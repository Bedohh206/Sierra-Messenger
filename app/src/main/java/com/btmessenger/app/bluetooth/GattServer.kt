package com.btmessenger.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.util.Log
import com.btmessenger.app.data.dao.FriendDao
import com.btmessenger.app.permission.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * GATT Server for receiving connections and messages
 *
 * Notes:
 * - Exposes CCCD and NOTIFY-capable characteristics (required for subscription).
 * - Uses API33+ notifyCharacteristicChanged(device, characteristic, confirm, value) when available.
 */
class GattServer(
    private val context: Context,
    private val friendDao: FriendDao? = null
) {
    private val tag = "GattServer"

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private var gattServer: BluetoothGattServer? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val _receivedMessages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val receivedMessages: SharedFlow<String> = _receivedMessages

    // Track connected + subscribed devices
    private val connectedDevices = mutableSetOf<BluetoothDevice>()
    private val subscribedDevices = mutableSetOf<BluetoothDevice>()
    private var lastConnKey: String? = null

    // Standard CCCD UUID
    private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Cache characteristics so you can notify later
    private var messageCharacteristic: BluetoothGattCharacteristic? = null
    private var transferCharacteristic: BluetoothGattCharacteristic? = null

    private val callback = object : BluetoothGattServerCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val key = "${device.address}|$status|$newState"
            if (key == lastConnKey) return
            lastConnKey = key

            Log.d(tag, "connState dev=${device.address} status=$status newState=$newState")

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevices.add(device)
                    Log.d(tag, "Device connected: ${device.address}")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevices.remove(device)
                    subscribedDevices.remove(device)
                    Log.d(tag, "Device disconnected: ${device.address}")
                }
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            Log.d(tag, "serviceAdded status=$status uuid=${service.uuid}")
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            Log.d(tag, "READ dev=${device.address} uuid=${characteristic.uuid} offset=$offset")

            if (characteristic.uuid == Protocol.MESSAGE_CHARACTERISTIC_UUID) {
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    "OK".toByteArray()
                )
            } else {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, 0, null)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            Log.d(
                tag,
                "WRITE dev=${device.address} uuid=${characteristic.uuid} offset=$offset len=${value.size} resp=$responseNeeded"
            )

            if (characteristic.uuid == Protocol.MESSAGE_CHARACTERISTIC_UUID) {
                val msg = value.toString(Charsets.UTF_8)
                val sender = device.address
                Log.d(tag, "Received message: $msg")

                if (friendDao != null && sender.isNotEmpty()) {
                    scope.launch {
                        try {
                            val friend = friendDao.getFriendByAddress(sender)
                            if (friend != null) _receivedMessages.tryEmit(msg)
                            else Log.d(tag, "Rejected message from non-friend: $sender")
                        } catch (e: Exception) {
                            Log.e(tag, "Error checking friend status", e)
                        }
                    }
                } else {
                    _receivedMessages.tryEmit(msg)
                }

                // Optional: notify subscribed clients that something changed (echo / ack)
                // notifyToSubscribers(Protocol.MESSAGE_CHARACTERISTIC_UUID, "ACK".toByteArray())
            }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            Log.d(tag, "DESC_WRITE dev=${device.address} uuid=${descriptor.uuid} len=${value.size} resp=$responseNeeded")

            // CCCD subscription handling
            if (descriptor.uuid == CCCD_UUID) {
                val enableNotify = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                val enableIndicate = value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                val disable = value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)

                when {
                    enableNotify || enableIndicate -> {
                        subscribedDevices.add(device)
                        Log.d(tag, "Subscribed: ${device.address} notify=$enableNotify indicate=$enableIndicate")
                    }
                    disable -> {
                        subscribedDevices.remove(device)
                        Log.d(tag, "Unsubscribed: ${device.address}")
                    }
                    else -> {
                        Log.d(tag, "Unknown CCCD value from ${device.address}: ${value.joinToString()}")
                    }
                }

                // Store descriptor value (some clients expect it)
                descriptor.value = value
            }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            Log.d(tag, "NOTIFY_SENT dev=${device.address} status=$status")
        }
    }

    @Synchronized
    @SuppressLint("MissingPermission")
    fun startServer(): Boolean {
        Log.d(tag, "startServer() called")

        if (scope.isActive.not()) {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        }

        if (_isRunning.value && gattServer != null) {
            Log.d(tag, "Already running; skipping start")
            return true
        }

        if (!isBluetoothEnabled()) {
            Log.e(tag, "Bluetooth adapter is null or disabled")
            return false
        }
        if (!hasRequiredPermissions()) {
            Log.e(tag, "Missing Bluetooth permissions")
            return false
        }

        return try {
            val server = bluetoothManager.openGattServer(context, callback)
            if (server == null) {
                Log.e(tag, "openGattServer returned null")
                return false
            }
            gattServer = server

            // Clear and rebuild services (prevents duplicates after process reuse)
            server.clearServices()
            Log.d(tag, "clearServices() done")

            val service = BluetoothGattService(Protocol.SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

            // MESSAGE characteristic: WRITE/READ + NOTIFY (so you can push events)
            messageCharacteristic = BluetoothGattCharacteristic(
                Protocol.MESSAGE_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_READ or
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_WRITE or
                        BluetoothGattCharacteristic.PERMISSION_READ
            ).also { it.addDescriptor(makeCccd()) }

            // TRANSFER characteristic: WRITE/READ + NOTIFY (optional)
            transferCharacteristic = BluetoothGattCharacteristic(
                Protocol.TRANSFER_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_READ or
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_WRITE or
                        BluetoothGattCharacteristic.PERMISSION_READ
            ).also { it.addDescriptor(makeCccd()) }

            service.addCharacteristic(requireNotNull(messageCharacteristic))
            service.addCharacteristic(requireNotNull(transferCharacteristic))

            val initiated = server.addService(service)
            Log.d(tag, "addService() initiated=$initiated uuid=${service.uuid}")

            // _isRunning becomes true after serviceAdded callback (best signal).
            // But many apps set it here; we'll keep it consistent:
            _isRunning.value = initiated
            initiated
        } catch (e: Exception) {
            Log.e(tag, "Failed to start GATT server", e)
            false
        }
    }

    @Synchronized
    @SuppressLint("MissingPermission")
    fun stopServer() {
        Log.d(tag, "stopServer() called")
        if (!hasRequiredPermissions()) return

        try {
            gattServer?.clearServices()
            gattServer?.close()
        } catch (e: Exception) {
            Log.e(tag, "Error stopping GATT server", e)
        } finally {
            gattServer = null
            _isRunning.value = false
            connectedDevices.clear()
            subscribedDevices.clear()
            scope.cancel()
            Log.d(tag, "GATT server stopped")
        }
    }

    /**
     * Call this when YOU want to push a notification to subscribed clients.
     */
    @SuppressLint("MissingPermission")
    fun notifyToSubscribers(characteristicUuid: UUID, value: ByteArray, confirm: Boolean = false) {
        val server = gattServer ?: run {
            Log.d(tag, "notifyToSubscribers: server is null")
            return
        }

        val ch = when (characteristicUuid) {
            Protocol.MESSAGE_CHARACTERISTIC_UUID -> messageCharacteristic
            Protocol.TRANSFER_CHARACTERISTIC_UUID -> transferCharacteristic
            else -> null
        } ?: run {
            Log.d(tag, "notifyToSubscribers: unknown characteristic $characteristicUuid")
            return
        }

        for (device in subscribedDevices.toList()) {
            val ok = notifyOne(server, device, ch, value, confirm)
            Log.d(tag, "notify dev=${device.address} ok=$ok len=${value.size}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyOne(
        server: BluetoothGattServer,
        device: BluetoothDevice,
        ch: BluetoothGattCharacteristic,
        value: ByteArray,
        confirm: Boolean
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            // New API returns a status code, not boolean. :contentReference[oaicite:1]{index=1}
            val code = server.notifyCharacteristicChanged(device, ch, confirm, value)
            code == BluetoothStatusCodes.SUCCESS
        } else {
            // Legacy (deprecated on newer Android)
            ch.value = value
            @Suppress("DEPRECATION")
            server.notifyCharacteristicChanged(device, ch, confirm)
        }
    }

    private fun makeCccd(): BluetoothGattDescriptor {
        return BluetoothGattDescriptor(
            CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
    }

    private fun hasRequiredPermissions(): Boolean =
        PermissionHelper.hasBluetoothPermissions(context)

    private fun isBluetoothEnabled(): Boolean {
        val adapter = bluetoothManager.adapter
        return adapter != null && adapter.isEnabled
    }
}
