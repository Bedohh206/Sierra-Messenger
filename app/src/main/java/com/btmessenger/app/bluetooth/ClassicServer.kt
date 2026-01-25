package com.btmessenger.app.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import com.btmessenger.app.data.dao.GroupDao
import com.btmessenger.app.data.entities.Group
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Classic Bluetooth Server for accepting connections
 */
class ClassicServer(private val context: Context, private val hostId: String = android.os.Build.MODEL, private val groupDao: GroupDao? = null) {
    
    private val tag = "ClassicServer"
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    
    private var serverSocket: BluetoothServerSocket? = null
    private var acceptJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning
    
    private val _receivedMessages = MutableSharedFlow<String>()
    val receivedMessages: SharedFlow<String> = _receivedMessages
    
    private val connectedSockets = mutableListOf<BluetoothSocket>()
    private data class ConnectedPeer(
        val peerId: String,
        val socket: BluetoothSocket,
        val outputStream: OutputStream
    )

    // Map of peerId -> ConnectedPeer
    private val connectedPeers = mutableMapOf<String, ConnectedPeer>()

    // Group membership: groupId -> set of peerIds
    private val groups = mutableMapOf<String, MutableSet<String>>()
    
    @SuppressLint("MissingPermission")
    fun startServer(): Boolean {
        if (!hasRequiredPermissions()) {
            Log.e(tag, "Missing Bluetooth permissions")
            return false
        }
        
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(tag, "Bluetooth is not available or not enabled")
            return false
        }
        
        try {
            serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                "BTMessenger",
                Protocol.CLASSIC_UUID
            )
            
            _isRunning.value = true
            
            // Start accepting connections
            acceptJob = scope.launch {
                acceptConnections()
            }
            
            Log.d(tag, "Classic Bluetooth server started")
            return true
        } catch (e: Exception) {
            Log.e(tag, "Failed to start server", e)
            return false
        }
    }
    
    private suspend fun acceptConnections() {
        while (coroutineContext[Job]?.isActive == true && _isRunning.value) {
            try {
                val socket = serverSocket?.accept()
                socket?.let {
                    Log.d(tag, "Client connected: ${it.remoteDevice?.address}")

                    // Handle this connection in a separate coroutine
                    scope.launch {
                        handleConnection(it)
                    }
                }
            } catch (e: IOException) {
                if (_isRunning.value) {
                    Log.e(tag, "Error accepting connection", e)
                }
                break
            }
        }
    }
    
    private suspend fun handleConnection(socket: BluetoothSocket) {
        try {
            val inputStream = socket.inputStream
            val buffer = ByteArray(4096)
            var peerId: String? = null

            while (coroutineContext[Job]?.isActive == true && socket.isConnected) {
                try {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        val message = String(buffer, 0, bytesRead, Charsets.UTF_8)
                        Log.d(tag, "Received message: ${message.take(100)}...")

                        // Try to parse and handle known protocol messages for relay
                        try {
                            val parsed = Protocol.parseMessage(message)
                            if (parsed != null) {
                                when (parsed.type) {
                                    Protocol.TYPE_REGISTER -> {
                                        // register this socket under parsed.from
                                        peerId = parsed.from
                                        try {
                                            val out = socket.outputStream
                                            val cp = ConnectedPeer(peerId!!, socket, out)
                                            synchronized(connectedPeers) {
                                                connectedPeers[peerId!!] = cp
                                            }
                                            Log.d(tag, "Registered peer: $peerId")
                                        } catch (e: Exception) {
                                            Log.e(tag, "Error registering peer", e)
                                        }
                                    }
                                    Protocol.TYPE_GROUP_JOIN -> {
                                        val gid = parsed.groupId
                                        val pid = parsed.from
                                        if (gid != null && pid != null) {
                                            synchronized(groups) {
                                                val set = groups.getOrPut(gid) { mutableSetOf() }
                                                set.add(pid)
                                            }
                                            Log.d(tag, "Peer $pid joined group $gid")

                                            // Persist group (create or update member count) using coroutine
                                            groupDao?.let { dao ->
                                                scope.launch {
                                                    try {
                                                        val existing = dao.getGroupById(gid)
                                                        if (existing == null) {
                                                            val g = Group(
                                                                groupId = gid,
                                                                name = gid,
                                                                hostId = hostId,
                                                                createdAt = System.currentTimeMillis(),
                                                                memberCount = 1
                                                            )
                                                            dao.insertGroup(g)
                                                            Log.d(tag, "Created group persisted: $gid")
                                                        } else {
                                                            val updated = existing.copy(memberCount = existing.memberCount + 1)
                                                            dao.insertGroup(updated)
                                                            Log.d(tag, "Updated group memberCount: $gid -> ${updated.memberCount}")
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e(tag, "Error persisting group join", e)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Protocol.TYPE_GROUP_LEAVE -> {
                                        val gid = parsed.groupId
                                        val pid = parsed.from
                                        if (gid != null && pid != null) {
                                            synchronized(groups) {
                                                groups[gid]?.remove(pid)
                                                if (groups[gid]?.isEmpty() == true) groups.remove(gid)
                                            }
                                            Log.d(tag, "Peer $pid left group $gid")

                                            // Update persisted group memberCount or delete if empty using coroutine
                                            groupDao?.let { dao ->
                                                scope.launch {
                                                    try {
                                                        val existing = dao.getGroupById(gid)
                                                        if (existing != null) {
                                                            val newCount = existing.memberCount - 1
                                                            if (newCount <= 0) {
                                                                dao.deleteGroup(existing)
                                                                Log.d(tag, "Deleted group persisted: $gid")
                                                            } else {
                                                                val updated = existing.copy(memberCount = newCount)
                                                                dao.insertGroup(updated)
                                                                Log.d(tag, "Decremented group memberCount: $gid -> ${updated.memberCount}")
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        Log.e(tag, "Error persisting group leave", e)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Protocol.TYPE_GROUP_MESSAGE -> {
                                        val gid = parsed.groupId
                                        val sender = parsed.from
                                        if (gid != null && sender != null) {
                                            // Relay to group members excluding sender
                                            val targets: Set<String> = synchronized(groups) {
                                                groups[gid]?.toSet() ?: emptySet()
                                            }
                                            targets.forEach { targetId ->
                                                if (targetId != sender) {
                                                    sendToPeer(targetId, message)
                                                }
                                            }
                                        }
                                    }
                                    else -> {
                                        // If this is a direct message with 'to' set, attempt to forward
                                        val to = parsed.to
                                        if (!to.isNullOrEmpty()) {
                                            sendToPeer(to, message)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(tag, "Error parsing/handling message", e)
                        }

                        _receivedMessages.emit(message)
                    }
                } catch (e: IOException) {
                    Log.d(tag, "Connection closed")
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error handling connection", e)
        } finally {
            try {
                socket.close()
                // cleanup peer mappings
                synchronized(connectedPeers) {
                    val entry = connectedPeers.entries.find { it.value.socket == socket }
                    val pid = entry?.key
                    if (pid != null) {
                        connectedPeers.remove(pid)
                        // remove from groups
                        synchronized(groups) {
                            groups.forEach { (_, set) -> set.remove(pid) }
                            groups.entries.removeIf { it.value.isEmpty() }
                        }
                        Log.d(tag, "Peer disconnected and cleaned: $pid")
                    }
                }
            } catch (e: IOException) {
                Log.e(tag, "Error closing socket", e)
            }
        }
    }

    private fun sendToPeer(peerId: String, message: String) {
        val cp = synchronized(connectedPeers) { connectedPeers[peerId] }
        if (cp != null) {
            try {
                cp.outputStream.write(message.toByteArray(Charsets.UTF_8))
                cp.outputStream.flush()
                Log.d(tag, "Relayed message to $peerId")
            } catch (e: IOException) {
                Log.e(tag, "Failed to send to $peerId", e)
            }
        } else {
            Log.d(tag, "No connected peer with id $peerId to relay message")
        }
    }
    
    fun stopServer() {
        _isRunning.value = false
        
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: IOException) {
            Log.e(tag, "Error closing server socket", e)
        }
        
        connectedSockets.forEach { socket ->
            try {
                socket.close()
            } catch (e: IOException) {
                Log.e(tag, "Error closing client socket", e)
            }
        }
        connectedSockets.clear()
        
        acceptJob?.cancel()
        acceptJob = null
        
        Log.d(tag, "Classic Bluetooth server stopped")
    }
    
    fun cleanup() {
        stopServer()
        scope.cancel()
    }
    
    private fun hasRequiredPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }
}
