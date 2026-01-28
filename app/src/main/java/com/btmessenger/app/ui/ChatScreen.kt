package com.btmessenger.app.ui

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.btmessenger.app.audio.AudioPlayer
import com.btmessenger.app.audio.AudioRecorder
import com.btmessenger.app.bluetooth.*
import com.btmessenger.app.data.AppDatabase
import com.btmessenger.app.data.entities.Message
import com.btmessenger.app.data.entities.Peer
import com.btmessenger.app.data.repository.MessengerRepository
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(
    peer: Peer,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
// Database
val database = remember { AppDatabase.getDatabase(context) }
val friendDao = remember { database.friendDao() }

val repository = remember {
    MessengerRepository(
        database.peerDao(),
        database.messageDao(),
        database.groupDao(),
        friendDao
    )
}


val repository = remember {
    // IMPORTANT: use the same constructor signature you use elsewhere.
    // If your MessengerRepository takes friendDao as the 4th param, pass it here.
    MessengerRepository(
        database.peerDao(),
        database.messageDao(),
        database.groupDao(),
        friendDao
    )
}


    // Bluetooth
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter = bluetoothManager.adapter
    val device = bluetoothAdapter?.getRemoteDevice(peer.address)
    
    val gattClient = remember { GattClient(context, friendDao = friendDao) }
    val classicClient = remember { ClassicClient(context) }
    
    // Audio
    val audioRecorder = remember { AudioRecorder(context) }
    val audioPlayer = remember { AudioPlayer(context) }
    val isRecording by audioRecorder.isRecording.collectAsState()
    var recordingDuration by remember { mutableStateOf(0) }
    
    val isConnected by gattClient.isConnected.collectAsState()
    val messages by repository.getMessagesForPeer(peer.id).collectAsState(initial = emptyList())
    
    var messageText by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var connectionType by remember { mutableStateOf<String?>(null) }
    var showImagePicker by remember { mutableStateOf(false) }
    var isRecordingMode by remember { mutableStateOf(false) }
    
    // Update recording duration
    
    // Storage permissions for images
    val storagePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.RECORD_AUDIO)
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO)
    }
    
    val storagePermissionsState = rememberMultiplePermissionsState(storagePermissions)
    
    // Image picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                sendImage(context, it, peer, gattClient, classicClient, repository, connectionType)
            }
        }
    }
    
    // Connect on first composition
    LaunchedEffect(Unit) {
        device?.let {
            isConnecting = true
            // Try BLE first
            val bleConnected = gattClient.connect(it)
            if (bleConnected) {
                connectionType = "BLE"
                Log.d("ChatScreen", "Connected via BLE")
            } else {
                // Try Classic Bluetooth
                    val classicConnected = classicClient.connect(it, android.os.Build.MODEL, android.os.Build.MODEL)
                if (classicConnected) {
                    connectionType = "CLASSIC"
                    Log.d("ChatScreen", "Connected via Classic Bluetooth")
                }
            }
            isConnecting = false
        }
    }
    
    // Listen for incoming messages
    LaunchedEffect(Unit) {
        gattClient.receivedMessages.collect { jsonMessage ->
            handleIncomingMessage(jsonMessage, peer, repository, context)
        }
    }
    
    LaunchedEffect(Unit) {
        classicClient.receivedMessages.collect { jsonMessage ->
            handleIncomingMessage(jsonMessage, peer, repository, context)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(peer.name)
                        Text(
                            text = when {
                                isConnecting -> "Connecting..."
                                isConnected -> "Connected via $connectionType"
                                else -> "Disconnected"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (!isConnected && device != null) {
                            scope.launch {
                                isConnecting = true
                                val connected = gattClient.connect(device)
                                if (!connected) {
                                    classicClient.connect(device, android.os.Build.MODEL, android.os.Build.MODEL)
                                }
                                isConnecting = false
                            }
                        }
                    }) {
                        Icon(
                            imageVector = if (isConnected) Icons.Default.Link else Icons.Default.LinkOff,
                            contentDescription = if (isConnected) "Connected" else "Disconnected"
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp
            ) {
                Column {
                    // Recording indicator
                    if (isRecording) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Recording: ${formatDuration(recordingDuration)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Row {
                                    IconButton(onClick = {
                                        audioRecorder.cancelRecording()
                                        isRecordingMode = false
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                                    }
                                    IconButton(
                                        onClick = {
                                            val audioFile = audioRecorder.stopRecording()
                                            if (audioFile != null) {
                                                scope.launch {
                                                    sendAudio(context, audioFile, peer, gattClient, classicClient, repository, connectionType)
                                                }
                                            }
                                            isRecordingMode = false
                                        },
                                        enabled = recordingDuration >= 1
                                    ) {
                                        Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isRecordingMode) {
                            IconButton(onClick = {
                                if (storagePermissionsState.allPermissionsGranted) {
                                    imagePickerLauncher.launch("image/*")
                                } else {
                                    storagePermissionsState.launchMultiplePermissionRequest()
                                }
                            }) {
                                Icon(Icons.Default.Image, contentDescription = "Attach Image")
                            }
                            
                            OutlinedTextField(
                                value = messageText,
                                onValueChange = { messageText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Type a message...") },
                                maxLines = 4
                            )
                            
                            if (messageText.isBlank()) {
                                IconButton(
                                    onClick = {
                                        if (storagePermissionsState.allPermissionsGranted) {
                                            isRecordingMode = true
                                            audioRecorder.startRecording()
                                        } else {
                                            storagePermissionsState.launchMultiplePermissionRequest()
                                        }
                                    },
                                    enabled = isConnected
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = "Voice Message")
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        if (messageText.isNotBlank()) {
                                            scope.launch {
                                                sendTextMessage(messageText, peer, gattClient, classicClient, repository, connectionType)
                                                messageText = ""
                                            }
                                        }
                                    },
                                    enabled = messageText.isNotBlank() && isConnected
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = "Send")
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        if (isConnecting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Connecting to ${peer.name}...")
                }
            }
        } else if (!isConnected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.LinkOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Not Connected", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Unable to connect to ${peer.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        scope.launch {
                            device?.let {
                                isConnecting = true
                                val connected = gattClient.connect(it)
                                if (!connected) {
                                    classicClient.connect(it)
                                }
                                isConnecting = false
                            }
                        }
                    }) {
                        Text("Retry Connection")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 8.dp),
                reverseLayout = false
            ) {
                items(messages) { message ->
                    MessageBubble(
                        message = message,
                        audioPlayer = audioPlayer,
                        context = context
                    )
                }
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            gattClient.disconnect()
            classicClient.disconnect()
            audioRecorder.cleanup()
            audioPlayer.cleanup()
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    audioPlayer: AudioPlayer,
    context: Context
) {
    val isIncoming = message.isIncoming
    val isPlayingThis by remember(message.msgId) {
        derivedStateOf {
            message.filePath?.let { path ->
                val file = File(path)
                audioPlayer.isPlayingFile(file)
            } ?: false
        }
    }
    val isPlaying by audioPlayer.isPlaying.collectAsState()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isIncoming) Arrangement.Start else Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isIncoming) 4.dp else 16.dp,
                bottomEnd = if (isIncoming) 16.dp else 4.dp
            ),
            color = if (isIncoming) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                when (message.type) {
                    Protocol.TYPE_TEXT -> {
                        Text(
                            text = message.body ?: "",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Protocol.TYPE_IMAGE_OFFER -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = message.fileName ?: "Image",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${(message.fileSize ?: 0) / 1024} KB",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    Protocol.TYPE_AUDIO_OFFER -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                message.filePath?.let { path ->
                                    val audioFile = File(path)
                                    if (audioFile.exists()) {
                                        if (isPlayingThis && isPlaying) {
                                            audioPlayer.pause()
                                        } else if (isPlayingThis && !isPlaying) {
                                            audioPlayer.resume()
                                        } else {
                                            audioPlayer.play(audioFile)
                                        }
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isPlayingThis && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlayingThis && isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Voice Message",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = formatDuration(message.duration ?: 0),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        Text(
                            text = message.type,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

suspend fun sendTextMessage(
    text: String,
    peer: Peer,
    gattClient: GattClient,
    classicClient: ClassicClient,
    repository: MessengerRepository,
    connectionType: String?
) {
    val msgId = UUID.randomUUID().toString()
    val deviceId = android.os.Build.MODEL
    val jsonMessage = Protocol.createTextMessage(msgId, deviceId, peer.id, text)
    
    val sent = when (connectionType) {
        "BLE" -> gattClient.sendMessage(jsonMessage)
        "CLASSIC" -> classicClient.sendMessage(jsonMessage)
        else -> false
    }
    
    if (sent) {
        val message = Message(
            msgId = msgId,
            type = Protocol.TYPE_TEXT,
            fromId = deviceId,
            toId = peer.id,
            timestamp = System.currentTimeMillis(),
            body = text,
            status = "sent",
            isIncoming = false
        )
        repository.insertMessage(message)
    }
}

suspend fun sendImage(
    context: Context,
    uri: Uri,
    peer: Peer,
    gattClient: GattClient,
    classicClient: ClassicClient,
    repository: MessengerRepository,
    connectionType: String?
) {
    val msgId = UUID.randomUUID().toString()
    val deviceId = android.os.Build.MODEL
    
    try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes()
        inputStream?.close()
        
        if (bytes != null) {
            val fileName = "image_${System.currentTimeMillis()}.jpg"
            val fileSize = bytes.size.toLong()
            val mime = "image/jpeg"
            
            val jsonMessage = Protocol.createImageOffer(msgId, deviceId, peer.id, fileName, fileSize, mime)
            
            val sent = when (connectionType) {
                "BLE" -> gattClient.sendMessage(jsonMessage)
                "CLASSIC" -> classicClient.sendMessage(jsonMessage)
                else -> false
            }
            
            if (sent) {
                val message = Message(
                    msgId = msgId,
                    type = Protocol.TYPE_IMAGE_OFFER,
                    fromId = deviceId,
                    toId = peer.id,
                    timestamp = System.currentTimeMillis(),
                    fileName = fileName,
                    fileSize = fileSize,
                    mime = mime,
                    status = "sent",
                    isIncoming = false
                )
                repository.insertMessage(message)
            }
        }
    } catch (e: Exception) {
        Log.e("ChatScreen", "Failed to send image", e)
    }
}

suspend fun sendAudio(
    context: Context,
    audioFile: File,
    peer: Peer,
    gattClient: GattClient,
    classicClient: ClassicClient,
    repository: MessengerRepository,
    connectionType: String?
) {
    val msgId = UUID.randomUUID().toString()
    val deviceId = android.os.Build.MODEL
    
    try {
        val audioPlayer = AudioPlayer(context)
        val duration = audioPlayer.getAudioDuration(audioFile)
        audioPlayer.cleanup()
        
        val fileName = audioFile.name
        val fileSize = audioFile.length()
        
        val jsonMessage = Protocol.createAudioOffer(msgId, deviceId, peer.id, fileName, fileSize, duration)
        
        val sent = when (connectionType) {
            "BLE" -> gattClient.sendMessage(jsonMessage)
            "CLASSIC" -> classicClient.sendMessage(jsonMessage)
            else -> false
        }
        
        if (sent) {
            val message = Message(
                msgId = msgId,
                type = Protocol.TYPE_AUDIO_OFFER,
                fromId = deviceId,
                toId = peer.id,
                timestamp = System.currentTimeMillis(),
                fileName = fileName,
                fileSize = fileSize,
                filePath = audioFile.absolutePath,
                mime = "audio/3gpp",
                duration = duration,
                status = "sent",
                isIncoming = false
            )
            repository.insertMessage(message)
            Log.d("ChatScreen", "Audio message sent: $fileName, duration: ${duration}s")
        }
    } catch (e: Exception) {
        Log.e("ChatScreen", "Failed to send audio", e)
    }
}

suspend fun handleIncomingMessage(
    jsonMessage: String,
    peer: Peer,
    repository: MessengerRepository,
    context: Context
) {
    val parsedMessage = Protocol.parseMessage(jsonMessage) ?: return
    
    val message = Message(
        msgId = parsedMessage.msgId,
        type = parsedMessage.type,
        fromId = parsedMessage.from,
        toId = parsedMessage.to ?: android.os.Build.MODEL,
        timestamp = parsedMessage.ts,
        body = parsedMessage.body,
        fileName = parsedMessage.fileName,
        fileSize = parsedMessage.fileSize,
        mime = parsedMessage.mime,
        duration = parsedMessage.duration,
        status = "received",
        isIncoming = true
    )
    
    repository.insertMessage(message)
}

fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60000 -> "Just now"
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86400000 -> "${diff / 3600000}h ago"
        else -> "${diff / 86400000}d ago"
    }
}

fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return if (minutes > 0) {
        "%d:%02d".format(minutes, remainingSeconds)
    } else {
        "0:%02d".format(remainingSeconds)
    }
}
