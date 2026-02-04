package com.btmessenger.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
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
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.Manifest
import java.util.UUID

class BleGattServerManager(
    private val appContext: Context
) {
    companion object {
        private const val TAG = "GattServer"
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val btManager: BluetoothManager =
        appContext.getSystemService(BluetoothManager::class.java)

    private val btAdapter: BluetoothAdapter? get() = btManager.adapter

    // Use the single canonical SERVICE/CHAR UUIDs from Protocol
    private val SERVICE_UUID = Protocol.SERVICE_UUID
    private val CHAR_UUID = Protocol.MESSAGE_CHARACTERISTIC_UUID

    private var gattServer: BluetoothGattServer? = null
    private var messageCharacteristic: BluetoothGattCharacteristic? = null

    private val connectedDevices = mutableSetOf<BluetoothDevice>()
    private val subscribedDevices = mutableSetOf<BluetoothDevice>()
    private var lastConnKey: String? = null

    private val callback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val key = "${device.address}|$status|$newState"
            if (key == lastConnKey) return
            lastConnKey = key

            Log.d(TAG, "connState dev=${device.address} status=$status newState=$newState")

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevices.add(device)
                    Log.d(TAG, "Device connected: ${device.address}")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connectedDevices.remove(device)
                    subscribedDevices.remove(device)
                    Log.d(TAG, "Device disconnected: ${device.address}")
                }
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            Log.d(TAG, "serviceAdded status=$status uuid=${service.uuid}")
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            Log.d(TAG, "charRead dev=${device.address} uuid=${characteristic.uuid} offset=$offset")

            // Return something predictable; many clients probe reads
            val value = if (characteristic.uuid == CHAR_UUID) {
                "OK".toByteArray()
            } else null

            gattServer?.sendResponse(
                device,
                requestId,
                if (value != null) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE,
                0,
                value
            )
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            Log.d(TAG, "descRead dev=${device.address} uuid=${descriptor.uuid} offset=$offset")

            // If client reads CCCD, return current value (or DISABLE by default)
            val value = descriptor.value ?: BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE

            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value)
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
            Log.d(TAG, "descWrite dev=${device.address} uuid=${descriptor.uuid} len=${value.size} resp=$responseNeeded")

            if (descriptor.uuid == CCCD_UUID) {
                val enableNotify =
                    value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                val enableIndicate =
                    value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                val disable =
                    value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)

                // Persist descriptor value (some BLE stacks require this)
                descriptor.value = value

                when {
                    enableNotify || enableIndicate -> {
                        subscribedDevices.add(device)
                        Log.d(TAG, "Subscribed ${device.address} notify=$enableNotify indicate=$enableIndicate")
                    }
                    disable -> {
                        subscribedDevices.remove(device)
                        Log.d(TAG, "Unsubscribed ${device.address}")
                    }
                    else -> {
                        Log.d(TAG, "Unknown CCCD value from ${device.address}: ${value.joinToString()}")
                    }
                }
            }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
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
            Log.d(TAG, "charWrite dev=${device.address} uuid=${characteristic.uuid} len=${value.size} resp=$responseNeeded")

            if (characteristic.uuid == CHAR_UUID) {
                val msg = value.toString(Charsets.UTF_8)
                Log.d(TAG, "Received: $msg")

                // Optional: ACK via notify if they subscribed
                notifySubscribers("ACK".toByteArray())
            }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            Log.d(TAG, "notifySent dev=${device.address} status=$status")
        }
    }

    @Synchronized
    @SuppressLint("MissingPermission")
    fun start() {
        Log.d(TAG, "start() called")

        // Verify runtime permission on Android S+ before calling BluetoothManager.openGattServer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val has = ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT)
            if (has != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Missing BLUETOOTH_CONNECT runtime permission — not starting GATT server")
                return
            }
        }

        val adapter = btAdapter
        if (adapter == null) {
            Log.e(TAG, "No BluetoothAdapter on this device")
            return
        }
        if (!adapter.isEnabled) {
            Log.w(TAG, "Bluetooth is OFF; cannot start GATT server yet")
            return
        }

        if (gattServer != null) {
            Log.d(TAG, "Already started; skipping")
            return
        }

        val server = btManager.openGattServer(appContext, callback)
        if (server == null) {
            Log.e(TAG, "openGattServer returned null (permissions? BT off? stack busy?)")
            return
        }
        gattServer = server
        Log.d(TAG, "openGattServer OK")

        // Clear any previous services
        runCatching { server.clearServices() }
            .onSuccess { Log.d(TAG, "clearServices OK") }
            .onFailure { Log.w(TAG, "clearServices threw: ${it.message}") }

        // Build service + characteristic (NOTIFY requires CCCD)
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        messageCharacteristic = BluetoothGattCharacteristic(
            CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE or
                    BluetoothGattCharacteristic.PERMISSION_READ
        ).also { ch ->
            val cccd = BluetoothGattDescriptor(
                CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
            cccd.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            ch.addDescriptor(cccd)
            service.addCharacteristic(ch)
        }

        val initiated = server.addService(service)
        Log.d(TAG, "addService() initiated=$initiated uuid=${service.uuid}")
    }

    @Synchronized
    fun stop() {
        Log.d(TAG, "stop() called")
        try {
            gattServer?.clearServices()
        } catch (_: Throwable) {
        }
        gattServer?.close()
        gattServer = null
        connectedDevices.clear()
        subscribedDevices.clear()
        messageCharacteristic = null
    }

    /**
     * Call this to push data to subscribed clients.
     * Works on API 33+ using the new overload; falls back on older devices.
     */
    @SuppressLint("MissingPermission")
    fun notifySubscribers(value: ByteArray, confirm: Boolean = false) {
        val server = gattServer ?: run {
            Log.d(TAG, "notifySubscribers: server is null")
            return
        }
        val ch = messageCharacteristic ?: run {
            Log.d(TAG, "notifySubscribers: characteristic is null")
            return
        }

        val targets = subscribedDevices.toList()
        if (targets.isEmpty()) {
            Log.d(TAG, "notifySubscribers: nobody subscribed")
            return
        }

        for (device in targets) {
            val ok = notifyOne(server, device, ch, value, confirm)
            Log.d(TAG, "notify dev=${device.address} ok=$ok len=${value.size}")
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
            val code = server.notifyCharacteristicChanged(device, ch, confirm, value)
            code == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                ch.value = value
                server.notifyCharacteristicChanged(device, ch, confirm)
            }
        }
    }
}
