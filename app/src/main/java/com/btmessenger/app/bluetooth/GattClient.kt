package com.btmessenger.app.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * GATT Client for connecting to other devices and sending messages
 */
class GattClient(private val context: Context) {
    
    private val tag = "GattClient"
    private var bluetoothGatt: BluetoothGatt? = null
    private var messageCharacteristic: BluetoothGattCharacteristic? = null
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected
    
    private val _receivedMessages = MutableSharedFlow<String>()
    val receivedMessages: SharedFlow<String> = _receivedMessages
    
    private var connectionContinuation: ((Boolean) -> Unit)? = null
    
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(tag, "Connected to GATT server")
                    _isConnected.value = true
                    // Discover services
                    gatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(tag, "Disconnected from GATT server")
                    _isConnected.value = false
                    connectionContinuation?.invoke(false)
                    connectionContinuation = null
                }
            }
        }
        
        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(tag, "Services discovered")
                
                val service = gatt?.getService(Protocol.SERVICE_UUID)
                messageCharacteristic = service?.getCharacteristic(Protocol.MESSAGE_CHARACTERISTIC_UUID)
                
                if (messageCharacteristic != null) {
                    Log.d(tag, "Message characteristic found")
                    connectionContinuation?.invoke(true)
                } else {
                    Log.e(tag, "Message characteristic not found")
                    connectionContinuation?.invoke(false)
                }
                connectionContinuation = null
            } else {
                Log.e(tag, "Service discovery failed with status: $status")
                connectionContinuation?.invoke(false)
                connectionContinuation = null
            }
        }
        
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(tag, "Message sent successfully")
            } else {
                Log.e(tag, "Failed to send message, status: $status")
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Boolean {
        if (!hasRequiredPermissions()) {
            Log.e(tag, "Missing Bluetooth permissions")
            return false
        }
        
        return suspendCancellableCoroutine { continuation ->
            connectionContinuation = { success ->
                continuation.resume(success)
            }
            
            try {
                bluetoothGatt = device.connectGatt(context, false, gattCallback)
                
                continuation.invokeOnCancellation {
                    disconnect()
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to connect", e)
                continuation.resume(false)
            }
        }
    }
    
    @SuppressLint("MissingPermission")
    fun sendMessage(message: String): Boolean {
        if (!hasRequiredPermissions()) {
            Log.e(tag, "Missing Bluetooth permissions")
            return false
        }
        
        val characteristic = messageCharacteristic
        if (characteristic == null) {
            Log.e(tag, "Message characteristic not available")
            return false
        }
        
        try {
            characteristic.value = message.toByteArray(Charsets.UTF_8)
            val success = bluetoothGatt?.writeCharacteristic(characteristic) ?: false
            
            if (success) {
                Log.d(tag, "Writing message: ${message.take(100)}...")
            } else {
                Log.e(tag, "Failed to write message")
            }
            
            return success
        } catch (e: Exception) {
            Log.e(tag, "Failed to send message", e)
            return false
        }
    }
    
    @SuppressLint("MissingPermission")
    fun disconnect() {
        if (!hasRequiredPermissions()) {
            return
        }
        
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
            messageCharacteristic = null
            _isConnected.value = false
            Log.d(tag, "Disconnected from device")
        } catch (e: Exception) {
            Log.e(tag, "Failed to disconnect", e)
        }
    }
    
    private fun hasRequiredPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }
}
