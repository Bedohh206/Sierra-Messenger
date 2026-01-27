package com.btmessenger.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.BluetoothGattCharacteristic.*
import android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
import android.content.Context
import android.util.Log
import com.btmessenger.app.permission.PermissionHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * GATT Server for receiving connections and messages
 */
class GattServer(private val context: Context) {
    
    private val tag = "GattServer"
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var gattServer: BluetoothGattServer? = null
    
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning
    
    private val _receivedMessages = MutableSharedFlow<String>()
    val receivedMessages: SharedFlow<String> = _receivedMessages
    
    private val connectedDevices = mutableSetOf<BluetoothDevice>()
    
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            device?.let {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        connectedDevices.add(it)
                        Log.d(tag, "Device connected: ${it.address}")
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        connectedDevices.remove(it)
                        Log.d(tag, "Device disconnected: ${it.address}")
                    }
                    else -> {
                        // Other states ignored
                    }
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
            
            if (characteristic?.uuid == Protocol.MESSAGE_CHARACTERISTIC_UUID) {
                value?.let {
                    val message = String(it, Charsets.UTF_8)
                    Log.d(tag, "Received message: $message")
                    
                    // Emit the received message without launching a coroutine (non-suspending)
                    _receivedMessages.tryEmit(message)
                }
                
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            
            if (characteristic?.uuid == Protocol.MESSAGE_CHARACTERISTIC_UUID) {
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    "OK".toByteArray()
                )
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    fun startServer(): Boolean {
        if (!isBluetoothEnabled()) {
            Log.e(tag, "Bluetooth adapter is null or disabled")
            return false
        }

        if (!hasRequiredPermissions()) {
            Log.e(tag, "Missing Bluetooth permissions")
            return false
        }
        
        try {
            gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
            
            // Create the service
            val service = BluetoothGattService(Protocol.SERVICE_UUID, SERVICE_TYPE_PRIMARY)
            
            // Add message characteristic (write)
            val messageCharacteristic = BluetoothGattCharacteristic(
                Protocol.MESSAGE_CHARACTERISTIC_UUID,
                PROPERTY_WRITE or PROPERTY_READ,
                PERMISSION_WRITE or PERMISSION_READ
            )
            service.addCharacteristic(messageCharacteristic)
            
            // Add transfer characteristic (for file chunks)
            val transferCharacteristic = BluetoothGattCharacteristic(
                Protocol.TRANSFER_CHARACTERISTIC_UUID,
                PROPERTY_WRITE or PROPERTY_READ,
                PERMISSION_WRITE or PERMISSION_READ
            )
            service.addCharacteristic(transferCharacteristic)
            
            // Add service to server
            val added = gattServer?.addService(service) ?: false
            if (added) {
                _isRunning.value = true
                Log.d(tag, "GATT server started successfully")
                return true
            } else {
                Log.e(tag, "Failed to add service to GATT server")
                return false
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to start GATT server", e)
            return false
        }
    }
    
    @SuppressLint("MissingPermission")
    fun stopServer() {
        if (!hasRequiredPermissions()) {
            return
        }
        
        try {
            gattServer?.close()
            gattServer = null
            _isRunning.value = false
            connectedDevices.clear()
            Log.d(tag, "GATT server stopped")
        } catch (e: Exception) {
            Log.e(tag, "Failed to stop GATT server", e)
        }
    }
    
    private fun hasRequiredPermissions(): Boolean {
        return PermissionHelper.hasBluetoothPermissions(context)
    }

    private fun isBluetoothEnabled(): Boolean {
        val adapter = bluetoothManager.adapter
        return adapter != null && adapter.isEnabled
    }
}
