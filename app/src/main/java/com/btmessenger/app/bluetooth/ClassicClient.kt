package com.btmessenger.app.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Classic Bluetooth Client for connecting to other devices
 */
class ClassicClient(private val context: Context) {
    
    private val tag = "ClassicClient"
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var readJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected
    
    private val _receivedMessages = MutableSharedFlow<String>()
    val receivedMessages: SharedFlow<String> = _receivedMessages
    
    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice, localId: String? = null, displayName: String? = null): Boolean {
        if (!hasRequiredPermissions()) {
            Log.e(tag, "Missing Bluetooth permissions")
            return false
        }
        
        return withContext(Dispatchers.IO) {
            try {
                socket = device.createRfcommSocketToServiceRecord(Protocol.CLASSIC_UUID)
                socket?.connect()
                
                inputStream = socket?.inputStream
                outputStream = socket?.outputStream
                
                _isConnected.value = true
                
                // Start reading messages
                readJob = scope.launch {
                    readMessages()
                }
                
                Log.d(tag, "Connected to device: ${device.address}")

                // Optionally send a register announcement to host
                if (!localId.isNullOrEmpty()) {
                    val regMsg = Protocol.createRegisterMessage(UUID.randomUUID().toString(), localId, displayName ?: localId)
                    try {
                        sendMessage(regMsg)
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to send register message", e)
                    }
                }

                true
            } catch (e: IOException) {
                Log.e(tag, "Failed to connect to device", e)
                disconnect()
                false
            }
        }
    }

    suspend fun joinGroup(localId: String, groupId: String): Boolean {
        val msg = Protocol.createGroupJoinMessage(UUID.randomUUID().toString(), localId, groupId)
        return sendMessage(msg)
    }

    suspend fun leaveGroup(localId: String, groupId: String): Boolean {
        val msg = Protocol.createGroupLeaveMessage(UUID.randomUUID().toString(), localId, groupId)
        return sendMessage(msg)
    }

    suspend fun sendGroupText(localId: String, groupId: String, body: String): Boolean {
        val msg = Protocol.createGroupTextMessage(UUID.randomUUID().toString(), localId, groupId, body)
        return sendMessage(msg)
    }
    
    private suspend fun readMessages() {
        val buffer = ByteArray(4096)
        
        while (coroutineContext[Job]?.isActive == true && _isConnected.value) {
            try {
                val bytesRead = inputStream?.read(buffer) ?: -1
                if (bytesRead > 0) {
                    val message = String(buffer, 0, bytesRead, Charsets.UTF_8)
                    Log.d(tag, "Received message: ${message.take(100)}...")
                    _receivedMessages.emit(message)
                } else if (bytesRead == -1) {
                    // End of stream
                    break
                }
            } catch (e: IOException) {
                if (_isConnected.value) {
                    Log.e(tag, "Error reading message", e)
                }
                break
            }
        }
        
        disconnect()
    }
    
    suspend fun sendMessage(message: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val bytes = message.toByteArray(Charsets.UTF_8)
                outputStream?.write(bytes)
                outputStream?.flush()
                Log.d(tag, "Sent message: ${message.take(100)}...")
                true
            } catch (e: IOException) {
                Log.e(tag, "Failed to send message", e)
                false
            }
        }
    }
    
    fun disconnect() {
        _isConnected.value = false
        
        readJob?.cancel()
        readJob = null
        
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (e: IOException) {
            Log.e(tag, "Error closing connection", e)
        }
        
        inputStream = null
        outputStream = null
        socket = null
        
        Log.d(tag, "Disconnected from device")
    }
    
    fun cleanup() {
        disconnect()
        scope.cancel()
    }
    
    private fun hasRequiredPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }
}
