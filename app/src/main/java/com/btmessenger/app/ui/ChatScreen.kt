package com.btmessenger.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.media.RingtoneManager
import android.webkit.MimeTypeMap
import android.util.Log
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.FileProvider
import com.btmessenger.app.audio.AudioPlayer
import com.btmessenger.app.audio.AudioRecorder
import com.btmessenger.app.bluetooth.BleViewModel
import com.btmessenger.app.bluetooth.ClassicClientPool
import com.btmessenger.app.bluetooth.ClassicClientPoolProvider
import com.btmessenger.app.bluetooth.GattClient
import com.btmessenger.app.bluetooth.Protocol
import com.btmessenger.app.data.AppDatabase
import com.btmessenger.app.data.entities.Message
import com.btmessenger.app.data.entities.Peer
import com.btmessenger.app.data.repository.MessengerRepository
import com.btmessenger.app.transport.BleSmallMessageTransport
import com.btmessenger.app.transport.MessageRouter
import com.btmessenger.app.util.AlertPreferences
import com.btmessenger.app.util.DeviceId
import kotlinx.coroutines.delay
import com.btmessenger.app.transport.MeshSdkFactory
import com.btmessenger.app.transport.TransportHint
import com.btmessenger.app.transport.TransportThresholds
import com.btmessenger.app.transport.TcpLanTransport
import com.btmessenger.app.transport.WifiDirectTransport
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(
    peer: Peer,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as ComponentActivity
    val bleViewModel: BleViewModel = viewModel(activity)
    val forceClassic by bleViewModel.forceClassic.collectAsState()
    val modeOverride by bleViewModel.modeOverride.collectAsState()
    val classicServerOnly by bleViewModel.classicServerOnly.collectAsState()
    val localId = remember { DeviceId.getOrCreate(context) }
    val localAddr = remember { DeviceId.getLocalBtAddress(context) }

    // ✅ Database / DAOs
    val database = remember { AppDatabase.getDatabase(context) }
    val friendDao = remember { database.friendDao() }
    // ✅ Repository (MUST include friendDao)
    val repository = remember {
        MessengerRepository(
            database.peerDao(),
            database.messageDao(),
            database.groupDao(),
            friendDao,
            database.outboxDao()
        )
    }

    // Bluetooth
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter = bluetoothManager.adapter
    val resolvedClassicAddress = remember(peer.address, peer.name) {
        val bonded = bluetoothAdapter?.bondedDevices
        val direct = bonded?.firstOrNull { it.address == peer.address }
        val byName = bonded?.firstOrNull { it.name == peer.name }
        val resolved = direct?.address ?: byName?.address ?: peer.address
        if (resolved != peer.address) {
            Log.d("ChatScreen", "Resolved classic address ${peer.address} -> $resolved")
        }
        resolved
    }
    val device = bluetoothAdapter?.getRemoteDevice(resolvedClassicAddress)

    val gattClient = remember { GattClient(context) }
    val classicPool = remember { ClassicClientPoolProvider.get(context) }
    val classicPoolInstanceId = remember { ClassicClientPoolProvider.getInstanceId(context) }
    val preferBlePeer = remember(peer.type) { peer.type.equals("BLE", ignoreCase = true) }
    val classicKey = remember(peer.id, resolvedClassicAddress) {
        if (resolvedClassicAddress.contains(":")) resolvedClassicAddress else peer.id
    }
    val tcpTransport = remember { TcpLanTransport() }
    val messageRouter = remember {
        MessageRouter(
            repository,
            BleSmallMessageTransport(gattClient, classicPool, bleViewModel.classicServer),
            WifiDirectTransport(context),
            tcpTransport,
            MeshSdkFactory.create(context)
        )
    }

    DisposableEffect(Unit) {
        Log.d("ClassicPoolCheck", "ChatScreen poolInstanceId=$classicPoolInstanceId peer=${peer.id}")
        messageRouter.start()
        onDispose {
            messageRouter.stop()
            tcpTransport.cleanup()
        }
    }

    // Audio
    val audioRecorder = remember { AudioRecorder(context) }
    val audioPlayer = remember { AudioPlayer(context) }
    val isRecording by audioRecorder.isRecording.collectAsState()
    var recordingDuration by remember { mutableStateOf(0) }

    val gattConnected by gattClient.isConnected.collectAsState()
    val classicConnected by classicPool.connectionState(classicKey).collectAsState()
    val isConnected = gattConnected || classicConnected
    val mappedPeer by repository.getPeerByAddressFlow(peer.address)
        .collectAsState(initial = null)
    val effectivePeer: Peer = remember(peer, mappedPeer) {
        when {
            peer.hasValidUuid() -> peer
            mappedPeer?.hasValidUuid() == true -> mappedPeer!!
            else -> mappedPeer ?: peer
        }
    }
    val peerIdCandidates = remember(peer.id, peer.address, mappedPeer?.id) {
        val ids = LinkedHashSet<String>()
        ids.add(peer.id)
        if (peer.address.isNotBlank()) {
            ids.add(peer.address)
        }
        mappedPeer?.id?.let { ids.add(it) }
        ids.toList()
    }
    val messages by repository.getMessagesForPeerIds(peerIdCandidates)
        .collectAsState(initial = emptyList())

    var messageText by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var connectionType by remember { mutableStateOf<String?>(null) }
    var showHandshakeBanner by remember { mutableStateOf(false) }
    var isRecordingMode by remember { mutableStateOf(false) }
    var showMediaSheet by remember { mutableStateOf(false) }
    var mediaTarget by remember { mutableStateOf(MediaTarget.IMAGE) }
    val mediaSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tcpHostInput by rememberSaveable(effectivePeer.id) { mutableStateOf(effectivePeer.tcpHost ?: "") }
    var tcpPortInput by rememberSaveable(effectivePeer.id) {
        mutableStateOf((effectivePeer.tcpPort ?: 8989).toString())
    }

    // ✅ Update recording duration while recording
    LaunchedEffect(isRecording) {
        if (!isRecording) {
            recordingDuration = 0
            return@LaunchedEffect
        }
        while (isRecording) {
            kotlinx.coroutines.delay(1000)
            recordingDuration += 1
        }
    }

    // Storage permissions
    val storagePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    } else {
        listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }
    val storagePermissionsState = rememberMultiplePermissionsState(storagePermissions)

    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingMediaUri by remember { mutableStateOf<Uri?>(null) }
    var pendingMediaFile by remember { mutableStateOf<File?>(null) }
    var pendingMediaMime by remember { mutableStateOf<String?>(null) }
    var pendingMediaTarget by remember { mutableStateOf<MediaTarget?>(null) }
    var pendingMediaName by remember { mutableStateOf<String?>(null) }

    suspend fun preparePendingMedia(uri: Uri, target: MediaTarget) {
        val (prefix, ext, fallbackMime) = when (target) {
            MediaTarget.IMAGE -> Triple("image", ".jpg", "image/jpeg")
            MediaTarget.VIDEO -> Triple("video", ".mp4", "video/mp4")
        }
        val file = withContext(Dispatchers.IO) {
            copyUriToCacheFile(context, uri, prefix, ext)
        }
        if (file == null) {
            Toast.makeText(context, "Unable to read attachment", Toast.LENGTH_SHORT).show()
            Log.e("ChatScreen", "preparePendingMedia failed for uri=$uri")
            return
        }
        val mime = context.contentResolver.getType(uri) ?: fallbackMime
        pendingMediaUri = uri
        pendingMediaFile = file
        pendingMediaMime = mime
        pendingMediaTarget = target
        pendingMediaName = queryDisplayName(context, uri)
            ?: file.name
    }

    suspend fun sendPendingMediaIfAny(): Boolean {
        val mediaFile = pendingMediaFile
        val mediaTarget = pendingMediaTarget
        val mediaMime = pendingMediaMime
        if (mediaTarget != null && mediaFile == null) {
            Toast.makeText(context, "Attachment not ready", Toast.LENGTH_SHORT).show()
            return false
        }
        if (mediaFile == null || mediaTarget == null) return false
        if (!isConnected) {
            Toast.makeText(context, "Not connected", Toast.LENGTH_SHORT).show()
            return false
        }
        val mime = mediaMime ?: if (mediaTarget == MediaTarget.IMAGE) "image/jpeg" else "video/mp4"
        sendMediaFile(
            context = context,
            file = mediaFile,
            peer = effectivePeer,
            mime = mime,
            type = if (mediaTarget == MediaTarget.IMAGE) {
                Protocol.TYPE_IMAGE_OFFER
            } else {
                Protocol.TYPE_VIDEO_OFFER
            },
            gattClient = gattClient,
            classicPool = classicPool,
            classicKey = classicKey,
            repository = repository
        )
        pendingMediaUri = null
        pendingMediaFile = null
        pendingMediaMime = null
        pendingMediaTarget = null
        pendingMediaName = null
        return true
    }

    // Image picker (gallery)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            scope.launch {
                preparePendingMedia(it, MediaTarget.IMAGE)
            }
        }
    }
    fun launchImagePicker() {
        imagePickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val photoCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingPhotoUri?.let { uri ->
                scope.launch {
                    preparePendingMedia(uri, MediaTarget.IMAGE)
                }
            }
        }
    }

    fun launchPhotoCapture() {
        if (!storagePermissionsState.allPermissionsGranted) {
            Toast.makeText(context, "Grant Camera permission to take a photo", Toast.LENGTH_SHORT).show()
            storagePermissionsState.launchMultiplePermissionRequest()
            return
        }
        val file = createTempMediaFile(context, "photo", ".jpg")
        val uri = file?.let { FileProvider.getUriForFile(context, fileProviderAuthority(context), it) }
        if (uri == null) {
            Toast.makeText(context, "Unable to create photo file", Toast.LENGTH_SHORT).show()
            return
        }
        pendingPhotoUri = uri
        photoCaptureLauncher.launch(uri)
    }

    // Video picker (gallery)
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            scope.launch {
                preparePendingMedia(it, MediaTarget.VIDEO)
            }
        }
    }
    fun launchVideoPicker() {
        videoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        )
    }

    val videoCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: pendingVideoUri
        uri?.let {
            scope.launch {
                preparePendingMedia(it, MediaTarget.VIDEO)
            }
        }
    }

    fun launchVideoCapture() {
        if (!storagePermissionsState.allPermissionsGranted) {
            Toast.makeText(context, "Grant Camera permission to record a video", Toast.LENGTH_SHORT).show()
            storagePermissionsState.launchMultiplePermissionRequest()
            return
        }
        val file = createTempMediaFile(context, "video", ".mp4")
        val uri = file?.let { FileProvider.getUriForFile(context, fileProviderAuthority(context), it) }
        if (uri == null) {
            Toast.makeText(context, "Unable to create video file", Toast.LENGTH_SHORT).show()
            return
        }
        pendingVideoUri = uri
        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            putExtra(MediaStore.EXTRA_DURATION_LIMIT, 60)
            putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        videoCaptureLauncher.launch(intent)
    }

    // Connect on first composition
    LaunchedEffect(Unit) {
        device?.let {
            val isSelfAddress = !DeviceId.isMaskedAddress(localAddr) && localAddr == it.address
            val isSelfId = peer.id == localId
            if (isSelfAddress || isSelfId) {
                Log.w("ChatScreen", "Skipping self-connection to ${it.address}")
                return@LaunchedEffect
            }
            if (forceClassic && classicServerOnly) {
                Log.d("ChatScreen", "Classic server-only enabled; skipping client connect")
                return@LaunchedEffect
            }
            isConnecting = true
            Log.d("ChatScreen", "Attempting connection to ${device.address}")
            if (forceClassic && !preferBlePeer) {
                Log.d("ChatScreen", "Force Classic enabled")
                val classicConnected = classicPool.connect(classicKey, it, localId, android.os.Build.MODEL)
                if (classicConnected) {
                    connectionType = "CLASSIC"
                    Log.d("ChatScreen", "Connected via Classic Bluetooth")
                } else {
                    Log.e("ChatScreen", "Classic Bluetooth connection failed")
                }
            } else {
                val bleConnected = gattClient.connect(it)
                if (bleConnected) {
                    connectionType = "BLE"
                    Log.d("ChatScreen", "Connected via BLE")
                    if (!preferBlePeer && modeOverride == BleViewModel.BleModeOverride.AUTO && !classicServerOnly) {
                        scope.launch {
                            Log.d("ChatScreen", "Auto mode: warming Classic connection")
                            classicPool.connect(classicKey, it, localId, android.os.Build.MODEL)
                        }
                    }
                } else {
                    if (preferBlePeer) {
                        Log.e("ChatScreen", "BLE connection failed for BLE peer")
                    } else {
                        Log.w("ChatScreen", "BLE connection failed, trying Classic Bluetooth...")
                        val classicConnected = classicPool.connect(classicKey, it, localId, android.os.Build.MODEL)
                        if (classicConnected) {
                            connectionType = "CLASSIC"
                            Log.d("ChatScreen", "Connected via Classic Bluetooth")
                        } else {
                            Log.e("ChatScreen", "Both BLE and Classic Bluetooth connection failed. Device may not have the app installed.")
                        }
                    }
                }
            }
            isConnecting = false
        }
    }

    // Send REGISTER message once connected to exchange UUIDs
    LaunchedEffect(isConnected) {
        if (isConnected && !effectivePeer.hasValidUuid()) {
            Log.d("ChatScreen", "Connected but peer lacks UUID, sending REGISTER message")
            delay(500) // Brief delay to ensure connection is stable
            val registerMsg = Protocol.createRegisterMessage(
                UUID.randomUUID().toString(),
                localId,
                android.os.Build.MODEL
            )
            val sent = when {
                classicConnected -> classicPool.sendMessage(classicKey, registerMsg)
                gattConnected -> gattClient.sendMessage(registerMsg)
                else -> false
            }
            if (sent) {
                Log.d("ChatScreen", "REGISTER message sent to initiate UUID exchange")
            } else {
                Log.w("ChatScreen", "Failed to send REGISTER message")
            }
        }
    }

    // Monitor connection state and auto-reconnect if disconnected
    LaunchedEffect(isConnected) {
        if (!isConnected && isConnecting) {
            Log.d("ChatScreen", "Connection lost, resetting isConnecting flag")
            isConnecting = false
        }
        
        // Auto-reconnect if connection drops
        if (!isConnected && !isConnecting && connectionType != null && !(forceClassic && classicServerOnly)) {
            Log.d("ChatScreen", "Connection dropped, attempting to reconnect...")
            delay(2000) // Wait 2 seconds before reconnecting
            
            device?.let {
                isConnecting = true
                val connected = if ((forceClassic && !preferBlePeer) || (connectionType == "CLASSIC" && !preferBlePeer)) {
                    classicPool.connect(classicKey, it, localId, android.os.Build.MODEL)
                } else {
                    gattClient.connect(it)
                }

                if (connected && connectionType == "BLE" && !preferBlePeer && modeOverride == BleViewModel.BleModeOverride.AUTO && !classicServerOnly) {
                    scope.launch {
                        Log.d("ChatScreen", "Auto mode: warming Classic connection (reconnect)")
                        classicPool.connect(classicKey, it, localId, android.os.Build.MODEL)
                    }
                }
                
                if (!connected) {
                    Log.e("ChatScreen", "Reconnection failed")
                    connectionType = null // Reset to allow retry button
                }
                isConnecting = false
            }
        }
    }

    LaunchedEffect(isConnected, effectivePeer.id, effectivePeer.hasValidUuid()) {
        if (!isConnected || effectivePeer.hasValidUuid()) {
            showHandshakeBanner = false
        } else {
            showHandshakeBanner = false
            delay(3000)
            if (isConnected && !effectivePeer.hasValidUuid()) {
                showHandshakeBanner = true
            }
        }
    }

    // Track connection type changes
    LaunchedEffect(gattConnected, classicConnected) {
        when {
            gattConnected && connectionType != "BLE" -> {
                connectionType = "BLE"
                Log.d("ChatScreen", "Connection type updated to BLE")
            }
            classicConnected && connectionType != "CLASSIC" -> {
                connectionType = "CLASSIC"
                Log.d("ChatScreen", "Connection type updated to CLASSIC")
            }
            !gattConnected && !classicConnected -> {
                if (connectionType != null) {
                    Log.d("ChatScreen", "All connections lost, resetting connection type")
                }
                connectionType = null
            }
        }
    }

    // Incoming messages
    LaunchedEffect(Unit) {
        bleViewModel.ensureClassicServerRunning()
        gattClient.receivedMessages.collect { json ->
            handleIncomingMessage(json, repository, context, messageRouter, peer)
        }
    }
    LaunchedEffect(Unit) {
        bleViewModel.gattServerMessages.collect { json ->
            handleIncomingMessage(json, repository, context, messageRouter, peer)
        }
    }
    LaunchedEffect(Unit) {
        bleViewModel.classicServerMessages.collect { json ->
            handleIncomingMessage(json, repository, context, messageRouter, peer)
        }
    }
    LaunchedEffect(Unit) {
        classicPool.receivedMessages.collect { json ->
            handleIncomingMessage(json, repository, context, messageRouter, peer)
        }
    }
    LaunchedEffect(Unit) {
        tcpTransport.receivedMessages.collect { json ->
            handleIncomingMessage(json, repository, context, messageRouter, peer)
        }
    }

    LaunchedEffect(modeOverride, tcpPortInput) {
        val port = tcpPortInput.toIntOrNull() ?: 0
        if (modeOverride == BleViewModel.BleModeOverride.AUTO && port > 0) {
            tcpTransport.startServer(port)
        } else {
            tcpTransport.stopServer()
        }
    }

    if (showMediaSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMediaSheet = false },
            sheetState = mediaSheetState,
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(8.dp)) {
                Text(
                    text = if (mediaTarget == MediaTarget.IMAGE) "Photo" else "Video",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
                ListItem(
                    headlineContent = { Text("Camera") },
                    leadingContent = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showMediaSheet = false
                        if (mediaTarget == MediaTarget.IMAGE) launchPhotoCapture() else launchVideoCapture()
                    }
                )
                ListItem(
                    headlineContent = { Text("Gallery") },
                    leadingContent = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showMediaSheet = false
                        if (mediaTarget == MediaTarget.IMAGE) launchImagePicker() else launchVideoPicker()
                    }
                )
                Spacer(Modifier.height(12.dp))
            }
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
                                connectionType == null && !isConnecting -> "Connection failed - Device needs app installed"
                                else -> "Disconnected"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                isConnected -> MaterialTheme.colorScheme.primary
                                connectionType == null && !isConnecting -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (!isConnected && device != null) {
                            scope.launch {
                                isConnecting = true
                                val connected = if (forceClassic && !preferBlePeer) {
                                    classicPool.connect(classicKey, device, localId, android.os.Build.MODEL)
                                } else {
                                    gattClient.connect(device)
                                }
                                if (!connected && !forceClassic && !preferBlePeer) {
                                    classicPool.connect(classicKey, device, localId, android.os.Build.MODEL)
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
            Surface(shadowElevation = 8.dp) {
                Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
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
                                                    sendAudio(
                                                        context,
                                                        audioFile,
                                                        peer,
                                                        gattClient,
                                                        classicPool,
                                                        classicKey,
                                                        repository
                                                    )
                                                }
                                            }
                                            isRecordingMode = false
                                        },
                                        enabled = recordingDuration >= 1
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                                    }
                                }
                            }
                        }
                    }

                    val hasPendingMedia = pendingMediaFile != null && pendingMediaTarget != null

                    if (hasPendingMedia) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (pendingMediaTarget == MediaTarget.IMAGE) {
                                            Icons.Default.Image
                                        } else {
                                            Icons.Default.Videocam
                                        },
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = pendingMediaName ?: "Attachment ready",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Row {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                sendPendingMediaIfAny()
                                            }
                                        },
                                        enabled = isConnected && pendingMediaFile != null && pendingMediaTarget != null
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send attachment")
                                    }
                                    IconButton(onClick = {
                                        pendingMediaUri = null
                                        pendingMediaFile = null
                                        pendingMediaMime = null
                                        pendingMediaTarget = null
                                        pendingMediaName = null
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove attachment")
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
                                mediaTarget = MediaTarget.IMAGE
                                showMediaSheet = true
                            }) {
                                Icon(Icons.Default.Image, contentDescription = "Attach Image")
                            }

                            IconButton(onClick = {
                                mediaTarget = MediaTarget.VIDEO
                                showMediaSheet = true
                            }) {
                                Icon(Icons.Default.Videocam, contentDescription = "Attach Video")
                            }

                            OutlinedTextField(
                                value = messageText,
                                onValueChange = { messageText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Type a message...") },
                                maxLines = 4,
                                trailingIcon = {
                                    if (messageText.isNotBlank()) {
                                        IconButton(onClick = { messageText = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear text")
                                        }
                                    }
                                }
                            )

                            if (messageText.isBlank() && !hasPendingMedia) {
                                IconButton(
                                    onClick = {
                                        if (storagePermissionsState.allPermissionsGranted) {
                                            isRecordingMode = true
                                            audioRecorder.startRecording()
                                        } else {
                                            storagePermissionsState.launchMultiplePermissionRequest()
                                        }
                                    },
                                    enabled = isConnected && effectivePeer.hasValidUuid()
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = "Voice Message")
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            val sendText = messageText.isNotBlank()
                                            sendPendingMediaIfAny()
                                            if (sendText) {
                                                sendTextMessage(
                                                    localId = DeviceId.getOrCreate(context),
                                                    text = messageText,
                                                    peer = effectivePeer,
                                                    gattClient = gattClient,
                                                    classicPool = classicPool,
                                                    classicKey = classicKey,
                                                    repository = repository,
                                                    connectionType = connectionType,
                                                    messageRouter = messageRouter,
                                                    forceClassic = forceClassic,
                                                    modeOverride = modeOverride
                                                )
                                                messageText = ""
                                            }
                                        }
                                    },
                                    enabled = (messageText.isNotBlank() || hasPendingMedia) && effectivePeer.hasValidUuid()
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Warning banner if peer doesn't have valid UUID yet
            if (showHandshakeBanner) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Connecting...",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Waiting for device handshake. Try closing and reopening chat in 10 seconds.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            
            if (modeOverride == BleViewModel.BleModeOverride.AUTO) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("TCP Peer (per chat)", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = tcpHostInput,
                            onValueChange = { tcpHostInput = it },
                            label = { Text("Peer IP / Host") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = tcpPortInput,
                            onValueChange = { tcpPortInput = it },
                            label = { Text("Port") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(onClick = {
                            val port = tcpPortInput.toIntOrNull()
                            if (port == null || port <= 0) {
                                Toast.makeText(context, "Invalid TCP port", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            tcpTransport.setPeer(tcpHostInput, port)
                            if (modeOverride == BleViewModel.BleModeOverride.AUTO) {
                                tcpTransport.startServer(port)
                            }
                            scope.launch {
                                repository.insertPeer(peer.copy(tcpHost = tcpHostInput, tcpPort = port))
                            }
                            Toast.makeText(context, "TCP peer saved for this chat", Toast.LENGTH_SHORT).show()
                        }) { Text("Apply TCP Peer") }
                        Text(
                            "Auto mode listens on this port while the chat is open.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            when {
                isConnecting -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Connecting to ${peer.name}...")
                        }
                    }
                }

                !isConnected -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
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
                                        val connected = if (forceClassic && !preferBlePeer) {
                                            classicPool.connect(classicKey, it, localId, android.os.Build.MODEL)
                                        } else {
                                            gattClient.connect(it)
                                        }
                                        if (!connected && !forceClassic && !preferBlePeer) {
                                            classicPool.connect(classicKey, it, localId, android.os.Build.MODEL)
                                        }
                                        isConnecting = false
                                    }
                                }
                            }) {
                                Text("Retry Connection")
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp)
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
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            gattClient.disconnect()
            classicPool.disconnect(classicKey)
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
                        Text(message.body ?: "", style = MaterialTheme.typography.bodyMedium)
                    }

                    Protocol.TYPE_IMAGE_OFFER -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(message.fileName ?: "Image", style = MaterialTheme.typography.bodyMedium)
                                Text("${(message.fileSize ?: 0) / 1024} KB", style = MaterialTheme.typography.bodySmall)
                                message.filePath?.let { path ->
                                    TextButton(onClick = {
                                        openAttachment(context, path, message.mime, message.type)
                                    }) {
                                        Text("Open")
                                    }
                                }
                            }
                        }
                    }

                    Protocol.TYPE_VIDEO_OFFER -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(message.fileName ?: "Video", style = MaterialTheme.typography.bodyMedium)
                                Text("${(message.fileSize ?: 0) / 1024} KB", style = MaterialTheme.typography.bodySmall)
                                message.filePath?.let { path ->
                                    TextButton(onClick = {
                                        openAttachment(context, path, message.mime, message.type)
                                    }) {
                                        Text("Open")
                                    }
                                }
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
                                        if (isPlayingThis && isPlaying) audioPlayer.pause()
                                        else if (isPlayingThis && !isPlaying) audioPlayer.resume()
                                        else audioPlayer.play(audioFile)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isPlayingThis && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Voice Message", style = MaterialTheme.typography.bodyMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(formatDuration(message.duration ?: 0), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    else -> {
                        Text(message.type, style = MaterialTheme.typography.bodySmall)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (message.status == "received-classic") {
                    Text(
                        text = "Accepted via Classic address fallback",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

suspend fun sendTextMessage(
    localId: String,
    text: String,
    peer: Peer,
    gattClient: GattClient,
    classicPool: ClassicClientPool,
    classicKey: String,
    repository: MessengerRepository,
    connectionType: String?,
    messageRouter: MessageRouter,
    forceClassic: Boolean,
    modeOverride: BleViewModel.BleModeOverride
) {
    val msgId = UUID.randomUUID().toString()
    val jsonMessage = Protocol.createTextMessage(msgId, localId, peer.id, text)
    val isBlePeer = peer.type.equals("BLE", ignoreCase = true)
    val routeToId = when {
        isBlePeer -> peer.id
        forceClassic && classicKey.contains(":") -> classicKey
        modeOverride == BleViewModel.BleModeOverride.AUTO && classicKey.contains(":") -> classicKey
        else -> peer.id
    }
    Log.d(
        "ChatScreen",
        "sendText msgId=$msgId to=${peer.id} routeTo=$routeToId localId=$localId bytes=${jsonMessage.toByteArray().size}"
    )

    // Always use messageRouter for intelligent transport selection
    // It will try: BLE GATT, ClassicServer, ClassicPool, WiFi, TCP, Mesh
    val sent = if (jsonMessage.toByteArray().size <= TransportThresholds.SMALL_MESSAGE_MAX_BYTES) {
        val hint = if (forceClassic) {
            TransportHint.BLE  // Will fall through to Classic in BleSmallMessageTransport
        } else if (modeOverride == BleViewModel.BleModeOverride.AUTO) {
            TransportHint.AUTO
        } else {
            TransportHint.BLE
        }
        Log.d("ChatScreen", "Sending via messageRouter with hint=${hint.name}")
        messageRouter.enqueueWithAck(msgId, routeToId, jsonMessage, hint)
    } else {
        Log.d("ChatScreen", "Sending large message via messageRouter with MESH hint")
        messageRouter.enqueueWithAck(msgId, peer.id, jsonMessage, TransportHint.MESH)
    }

    val message = Message(
        msgId = msgId,
        type = Protocol.TYPE_TEXT,
        fromId = localId,
        toId = peer.id,
        timestamp = System.currentTimeMillis(),
        body = text,
        status = if (sent) "sent" else "pending",
        isIncoming = false
    )
    repository.insertMessage(message)
}

suspend fun sendImage(
    context: Context,
    uri: Uri,
    peer: Peer,
    gattClient: GattClient,
    classicPool: ClassicClientPool,
    classicKey: String,
    repository: MessengerRepository,
    connectionType: String?,
    messageRouter: MessageRouter
) {
    try {
        val file = copyUriToCacheFile(context, uri, "image", ".jpg") ?: return
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        sendMediaFile(
            context = context,
            file = file,
            peer = peer,
            mime = mime,
            type = Protocol.TYPE_IMAGE_OFFER,
            gattClient = gattClient,
            classicPool = classicPool,
            classicKey = classicKey,
            repository = repository
        )
    } catch (e: Exception) {
        Log.e("ChatScreen", "Failed to send image", e)
    }
}

suspend fun sendVideo(
    context: Context,
    uri: Uri,
    peer: Peer,
    gattClient: GattClient,
    classicPool: ClassicClientPool,
    classicKey: String,
    repository: MessengerRepository,
    connectionType: String?,
    messageRouter: MessageRouter
) {
    try {
        val file = copyUriToCacheFile(context, uri, "video", ".mp4") ?: return
        val mime = context.contentResolver.getType(uri) ?: "video/mp4"
        sendMediaFile(
            context = context,
            file = file,
            peer = peer,
            mime = mime,
            type = Protocol.TYPE_VIDEO_OFFER,
            gattClient = gattClient,
            classicPool = classicPool,
            classicKey = classicKey,
            repository = repository
        )
    } catch (e: Exception) {
        Log.e("ChatScreen", "Failed to send video", e)
    }
}

private suspend fun sendMediaFile(
    context: Context,
    file: File,
    peer: Peer,
    mime: String,
    type: String,
    duration: Int? = null,
    gattClient: GattClient,
    classicPool: ClassicClientPool,
    classicKey: String,
    repository: MessengerRepository
) {
    suspend fun showToast(message: String, length: Int = Toast.LENGTH_SHORT) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, message, length).show()
        }
    }

    withContext(Dispatchers.IO) {
        Log.d("ChatScreen", "sendMediaFile: type=$type peer.id=${peer.id} peer.address=${peer.address} fileSize=${file.length()}")
        val isBlePeer = peer.type.equals("BLE", ignoreCase = true)
        val preferClassic = !isBlePeer
        val fileSize = file.length()
        val maxClassicBytes = if (type == Protocol.TYPE_VIDEO_OFFER) {
            TransportThresholds.MAX_CLASSIC_VIDEO_BYTES
        } else {
            TransportThresholds.MAX_CLASSIC_MEDIA_BYTES
        }
        if (fileSize > maxClassicBytes) {
            val maxMb = maxClassicBytes / (1024 * 1024)
            showToast("File too large for Bluetooth ($maxMb MB limit). Try Wi-Fi Direct.", Toast.LENGTH_LONG)
            return@withContext
        }

        val msgId = UUID.randomUUID().toString()
        val deviceId = DeviceId.getOrCreate(context)
        val fileName = file.name

        val offer = when (type) {
            Protocol.TYPE_IMAGE_OFFER -> Protocol.createImageOffer(msgId, deviceId, peer.id, fileName, fileSize, mime)
            Protocol.TYPE_VIDEO_OFFER -> Protocol.createVideoOffer(msgId, deviceId, peer.id, fileName, fileSize, mime)
            Protocol.TYPE_AUDIO_OFFER -> Protocol.createAudioOffer(
                msgId,
                deviceId,
                peer.id,
                fileName,
                fileSize,
                duration ?: 0
            )
            else -> Protocol.createImageOffer(msgId, deviceId, peer.id, fileName, fileSize, mime)
        }

        val handshake = MediaHandshake.register(msgId)

        val offerSent = sendPayloadWithRetry(
            context = context,
            payload = offer,
            gattClient = gattClient,
            classicPool = classicPool,
            classicKey = classicKey,
            preferClassic = preferClassic
        )
        val message = Message(
            msgId = msgId,
            type = type,
            fromId = deviceId,
            toId = peer.id,
            timestamp = System.currentTimeMillis(),
            fileName = fileName,
            fileSize = fileSize,
            mime = mime,
            filePath = file.absolutePath,
            duration = if (type == Protocol.TYPE_AUDIO_OFFER) duration else null,
            status = if (offerSent) "pending" else "failed",
            isIncoming = false
        )
        repository.insertMessage(message)

        if (!offerSent) {
            MediaHandshake.complete(msgId, false)
            showToast("Failed to send offer")
            return@withContext
        }

        val accepted = withTimeoutOrNull(12_000L) { handshake.await() } == true
        if (!accepted) {
            MediaHandshake.complete(msgId, false)
            repository.updateMessageStatus(msgId, "failed")
            showToast("Media transfer rejected or timed out")
            return@withContext
        }
        repository.updateMessageStatus(msgId, "sent")

        val totalChunks = ((fileSize + Protocol.CHUNK_SIZE - 1) / Protocol.CHUNK_SIZE).toInt()
        Log.d("ChatScreen", "sendMediaFile: Starting chunk transfer - $totalChunks chunks (~${fileSize/1024}KB)")

        file.inputStream().use { stream ->
            val buffer = ByteArray(Protocol.CHUNK_SIZE)
            var chunkIndex = 0
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break

                if (chunkIndex % 10 == 0 || totalChunks < 20) {
                    Log.d("ChatScreen", "sendMediaFile: Sending chunk $chunkIndex/$totalChunks (${(chunkIndex * 100) / totalChunks}%)")
                }

                val data = Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP)
                val chunkMsg = when (type) {
                    Protocol.TYPE_IMAGE_OFFER -> Protocol.createImageChunk(msgId, deviceId, peer.id, chunkIndex, totalChunks, data)
                    Protocol.TYPE_VIDEO_OFFER -> Protocol.createVideoChunk(msgId, deviceId, peer.id, chunkIndex,totalChunks, data)
                    Protocol.TYPE_AUDIO_OFFER -> Protocol.createAudioChunk(msgId, deviceId, peer.id, chunkIndex, totalChunks, data)
                    else -> Protocol.createImageChunk(msgId, deviceId, peer.id, chunkIndex, totalChunks, data)
                }
                val sent = sendPayloadWithRetry(
                    context = context,
                    payload = chunkMsg,
                    gattClient = gattClient,
                    classicPool = classicPool,
                    classicKey = classicKey,
                    preferClassic = preferClassic,
                    maxRetries = 2,
                    delayMs = 250L
                )
                if (!sent) {
                    Log.e("ChatScreen", "sendMediaFile: FAILED at chunk $chunkIndex/$totalChunks")
                    showToast("Transfer failed at ${(chunkIndex * 100) / totalChunks}%", Toast.LENGTH_LONG)
                    repository.updateMessageStatus(msgId, "failed")
                    return@withContext
                }
                chunkIndex += 1

                if (type == Protocol.TYPE_VIDEO_OFFER) {
                    delay(28L)
                }
            }
        }

        Log.d("ChatScreen", "sendMediaFile: All chunks sent successfully! Sending COMPLETE message...")

        val completeMsg = when (type) {
            Protocol.TYPE_IMAGE_OFFER -> Protocol.createImageComplete(UUID.randomUUID().toString(), deviceId, peer.id, msgId)
            Protocol.TYPE_VIDEO_OFFER -> Protocol.createVideoComplete(UUID.randomUUID().toString(), deviceId, peer.id, msgId)
            Protocol.TYPE_AUDIO_OFFER -> Protocol.createAudioComplete(UUID.randomUUID().toString(), deviceId, peer.id, msgId)
            else -> Protocol.createImageComplete(UUID.randomUUID().toString(), deviceId, peer.id, msgId)
        }
        val completeSent = sendPayloadWithRetry(
            context = context,
            payload = completeMsg,
            gattClient = gattClient,
            classicPool = classicPool,
            classicKey = classicKey,
            preferClassic = preferClassic,
            maxRetries = 1,
            delayMs = 250L
        )

        if (completeSent) {
            Log.d("ChatScreen", "sendMediaFile: Transfer COMPLETE!")
            showToast("Transfer complete!")
        } else {
            Log.w("ChatScreen", "sendMediaFile: COMPLETE message failed to send")
        }
    }
}

private suspend fun sendPayloadWithRetry(
    context: Context,
    payload: String,
    gattClient: GattClient,
    classicPool: ClassicClientPool,
    classicKey: String,
    preferClassic: Boolean,
    maxRetries: Int = 1,
    delayMs: Long = 200L
): Boolean {
    var attempt = 0
    while (attempt <= maxRetries) {
        Log.d(
            "ChatScreen",
            "sendPayloadWithRetry: classicKey=$classicKey preferClassic=$preferClassic isConnected=${classicPool.isConnected(classicKey)} attempt=$attempt/$maxRetries"
        )

        if (preferClassic && !classicPool.isConnected(classicKey) && classicKey.count { it == ':' } == 5) {
            Log.d("ChatScreen", "sendPayloadWithRetry: Attempting to establish connection to $classicKey")
            val adapter = BluetoothAdapter.getDefaultAdapter()
            val device = try {
                adapter?.getRemoteDevice(classicKey)
            } catch (_: IllegalArgumentException) {
                Log.e("ChatScreen", "sendPayloadWithRetry: Invalid Bluetooth address: $classicKey")
                null
            }
            if (device != null) {
                val localId = DeviceId.getOrCreate(context)
                val displayName = android.os.Build.MODEL
                Log.d("ChatScreen", "sendPayloadWithRetry: Calling classicPool.connect() for ${device.address} with localId=$localId")
                classicPool.connect(classicKey, device, localId, displayName)
                delay(900L)
            }
        }

        val sent = if (preferClassic && classicPool.isConnected(classicKey)) {
            classicPool.sendMessage(classicKey, payload)
        } else if (classicPool.isConnected(classicKey)) {
            classicPool.sendMessage(classicKey, payload)
        } else {
            gattClient.sendMessage(payload)
        }
        if (sent) return true

        if (preferClassic && classicKey.count { it == ':' } == 5) {
            classicPool.disconnect(classicKey)
        }

        attempt += 1
        if (attempt <= maxRetries) {
            val attemptBackoff = (delayMs * (attempt + 1)).coerceAtMost(1200L)
            delay(attemptBackoff)
        }
    }
    return false
}

private fun openAttachment(context: Context, filePath: String, mime: String?, messageType: String? = null) {
    val file = File(filePath)
    Log.d("ChatScreen", "openAttachment: path=$filePath mime=$mime exists=${file.exists()} size=${file.length()}")
    
    if (!file.exists()) {
        Toast.makeText(context, "File not found: ${file.name}", Toast.LENGTH_LONG).show()
        Log.e("ChatScreen", "File not found: $filePath")
        return
    }
    
    if (file.length() == 0L) {
        Toast.makeText(context, "File is empty", Toast.LENGTH_LONG).show()
        Log.e("ChatScreen", "File is empty: $filePath")
        return
    }
    
    val uri = FileProvider.getUriForFile(context, fileProviderAuthority(context), file)
    val ext = file.extension.lowercase()
    val guessed = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    val resolverType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
    val inferredByType = when (messageType) {
        Protocol.TYPE_VIDEO_OFFER -> "video/*"
        Protocol.TYPE_IMAGE_OFFER -> "image/*"
        Protocol.TYPE_AUDIO_OFFER -> "audio/*"
        else -> null
    }
    val type = when {
        !inferredByType.isNullOrBlank() -> inferredByType
        !mime.isNullOrBlank() && mime.startsWith("video") -> "video/*"
        !mime.isNullOrBlank() && mime.startsWith("image") -> "image/*"
        !mime.isNullOrBlank() && mime.startsWith("audio") -> "audio/*"
        !resolverType.isNullOrBlank() && resolverType.startsWith("video") -> "video/*"
        !resolverType.isNullOrBlank() && resolverType.startsWith("image") -> "image/*"
        !resolverType.isNullOrBlank() && resolverType.startsWith("audio") -> "audio/*"
        guessed?.startsWith("video") == true -> "video/*"
        guessed?.startsWith("image") == true -> "image/*"
        guessed?.startsWith("audio") == true -> "audio/*"
        !mime.isNullOrBlank() -> mime
        !resolverType.isNullOrBlank() -> resolverType
        !guessed.isNullOrBlank() -> guessed
        else -> "application/octet-stream"
    }
    Log.d("ChatScreen", "Opening with intent: uri=$uri type=$type ext=$ext mime=$mime resolverType=$resolverType messageType=$messageType")
    
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, type)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    intent.clipData = ClipData.newUri(context.contentResolver, file.name, uri)
    
    try {
        val resolved = context.packageManager.queryIntentActivities(intent, 0)
        Log.d("ChatScreen", "openAttachment: resolvedActivities=${resolved.size} for type=$type")
        if (resolved.isEmpty() && type != "*/*") {
            val fallback = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            fallback.clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            val fallbackResolved = context.packageManager.queryIntentActivities(fallback, 0)
            Log.d("ChatScreen", "openAttachment: fallback resolvedActivities=${fallbackResolved.size}")
            fallbackResolved.forEach { info ->
                context.grantUriPermission(
                    info.activityInfo.packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            if (fallbackResolved.isNotEmpty()) {
                context.startActivity(Intent.createChooser(fallback, "Open attachment"))
                return
            }
        }
        resolved.forEach { info ->
            context.grantUriPermission(
                info.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        context.startActivity(Intent.createChooser(intent, "Open attachment"))
    } catch (e: Exception) {
        val msg = "No app to open ${if (mime?.startsWith("video") == true) "video" else "this file"}"
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        Log.e("ChatScreen", "Failed to open attachment: path=$filePath mime=$mime", e)
    }
}

private fun copyUriToCacheFile(
    context: Context,
    uri: Uri,
    prefix: String,
    defaultExt: String
): File? {
    val name = queryDisplayName(context, uri)
    val safeName = if (!name.isNullOrBlank()) {
        name.replace(Regex("[^A-Za-z0-9._-]"), "_")
    } else {
        "${prefix}_${System.currentTimeMillis()}$defaultExt"
    }
    val dir = File(context.cacheDir, "media_outgoing")
    if (!dir.exists() && !dir.mkdirs()) return null
    val outFile = File(dir, safeName)

    context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(outFile).use { output ->
            input.copyTo(output)
        }
    } ?: return null

    return outFile
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) return it.getString(idx)
        }
    }
    return null
}

private fun fileProviderAuthority(context: Context): String {
    return "${context.packageName}.fileprovider"
}

private fun createTempMediaFile(context: Context, prefix: String, ext: String): File? {
    val dir = File(context.cacheDir, "media_capture")
    if (!dir.exists() && !dir.mkdirs()) return null
    return File.createTempFile("${prefix}_", ext, dir)
}

private enum class MediaTarget {
    IMAGE,
    VIDEO
}

suspend fun sendAudio(
    context: Context,
    audioFile: File,
    peer: Peer,
    gattClient: GattClient,
    classicPool: ClassicClientPool,
    classicKey: String,
    repository: MessengerRepository
) {
    try {
        val tmpPlayer = AudioPlayer(context)
        val duration = tmpPlayer.getAudioDuration(audioFile)
        tmpPlayer.cleanup()
        sendMediaFile(
            context = context,
            file = audioFile,
            peer = peer,
            mime = "audio/3gpp",
            type = Protocol.TYPE_AUDIO_OFFER,
            duration = duration,
            gattClient = gattClient,
            classicPool = classicPool,
            classicKey = classicKey,
            repository = repository
        )
    } catch (e: Exception) {
        Log.e("ChatScreen", "Failed to send audio", e)
    }
}

private data class IncomingTransfer(
    val msgId: String,
    val type: String,
    val fileName: String,
    val fileSize: Long?,
    val mime: String?,
    val file: File,
    var receivedChunks: Int,
    var totalChunks: Int
)

private object IncomingTransfers {
    private val transfers = mutableMapOf<String, IncomingTransfer>()

    fun get(id: String): IncomingTransfer? = synchronized(transfers) { transfers[id] }

    fun put(transfer: IncomingTransfer) {
        synchronized(transfers) { transfers[transfer.msgId] = transfer }
    }

    fun remove(id: String): IncomingTransfer? = synchronized(transfers) { transfers.remove(id) }
}

private object MediaHandshake {
    private val pending = mutableMapOf<String, CompletableDeferred<Boolean>>()

    fun register(msgId: String): CompletableDeferred<Boolean> {
        val deferred = CompletableDeferred<Boolean>()
        synchronized(pending) { pending[msgId] = deferred }
        return deferred
    }

    fun complete(msgId: String, accepted: Boolean) {
        synchronized(pending) { pending.remove(msgId) }?.complete(accepted)
    }
}

private fun buildMediaAccept(parsed: Protocol.Message, myId: String): String? {
    val originalId = parsed.msgId
    val to = parsed.from
    val replyId = UUID.randomUUID().toString()
    return when (parsed.type) {
        Protocol.TYPE_IMAGE_OFFER -> Protocol.createImageAccept(replyId, myId, to, originalId)
        Protocol.TYPE_VIDEO_OFFER -> Protocol.createVideoAccept(replyId, myId, to, originalId)
        Protocol.TYPE_AUDIO_OFFER -> Protocol.createAudioAccept(replyId, myId, to, originalId)
        else -> null
    }
}

private fun buildMediaReject(parsed: Protocol.Message, myId: String): String? {
    val originalId = parsed.msgId
    val to = parsed.from
    val replyId = UUID.randomUUID().toString()
    return when (parsed.type) {
        Protocol.TYPE_IMAGE_OFFER -> Protocol.createImageReject(replyId, myId, to, originalId)
        Protocol.TYPE_VIDEO_OFFER -> Protocol.createVideoReject(replyId, myId, to, originalId)
        Protocol.TYPE_AUDIO_OFFER -> Protocol.createAudioReject(replyId, myId, to, originalId)
        else -> null
    }
}

private fun ensureIncomingTransfer(
    context: Context,
    parsed: Protocol.Message,
    type: String
): IncomingTransfer {
    val existing = IncomingTransfers.get(parsed.msgId)
    if (existing != null) return existing

    val dir = File(context.cacheDir, "media_incoming")
    if (!dir.exists()) dir.mkdirs()

    val baseNameRaw = parsed.fileName?.replace(Regex("[^A-Za-z0-9._-]"), "_")
        ?: "file"
    val extFromMime = extensionFromMime(parsed.mime)
    val hasExt = baseNameRaw.substringAfterLast('.', "").isNotBlank()
    val baseName = if (!hasExt && !extFromMime.isNullOrBlank()) {
        "$baseNameRaw.$extFromMime"
    } else {
        baseNameRaw
    }
    val name = "${parsed.msgId}_$baseName"
    val file = File(dir, name)
    if (file.exists()) {
        file.delete()
    }
    FileOutputStream(file, false).use { }
    val totalChunks = when {
        parsed.totalChunks != null && parsed.totalChunks > 0 -> parsed.totalChunks
        parsed.fileSize != null && parsed.fileSize > 0 ->
            ((parsed.fileSize + Protocol.CHUNK_SIZE - 1) / Protocol.CHUNK_SIZE).toInt()
        else -> 0
    }

    val transfer = IncomingTransfer(
        msgId = parsed.msgId,
        type = type,
        fileName = name,
        fileSize = parsed.fileSize,
        mime = parsed.mime,
        file = file,
        receivedChunks = 0,
        totalChunks = totalChunks
    )
    IncomingTransfers.put(transfer)
    return transfer
}

private fun extensionFromMime(mime: String?): String? {
    if (mime.isNullOrBlank()) return null
    val fromMap = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
    if (!fromMap.isNullOrBlank()) return fromMap
    return when {
        mime.startsWith("video/") -> "mp4"
        mime.startsWith("image/") -> "jpg"
        mime.startsWith("audio/") -> "3gp"
        else -> null
    }
}

private fun appendIncomingChunk(transfer: IncomingTransfer, data: String, totalChunks: Int?) {
    val bytes = Base64.decode(data, Base64.NO_WRAP)
    FileOutputStream(transfer.file, true).use { out ->
        out.write(bytes)
    }
    transfer.receivedChunks += 1
    if (totalChunks != null && totalChunks > 0) {
        transfer.totalChunks = totalChunks
    }
}

suspend fun handleIncomingMessage(
    jsonMessage: String,
    repository: MessengerRepository,
    context: Context,
    messageRouter: MessageRouter?,
    chatPeer: Peer?
) {
    var payload = jsonMessage
    var sourceAddr: String? = null
    if (payload.startsWith("BTM_ADDR=")) {
        val newline = payload.indexOf('\n')
        if (newline > 0) {
            sourceAddr = payload.substring("BTM_ADDR=".length, newline).trim()
            payload = payload.substring(newline + 1)
        }
    }

    val parsed = Protocol.parseMessage(payload) ?: return
    Log.d(
        "ChatScreen",
        "handleIncomingMessage type=${parsed.type} from=${parsed.from} to=${parsed.to} sourceAddr=${sourceAddr ?: ""}"
    )

    val myId = DeviceId.getOrCreate(context)
    val localAddr = DeviceId.getLocalBtAddress(context)
    val ackClassicKey = sourceAddr?.takeIf { it.contains(":") }

    if (parsed.type == Protocol.TYPE_ACK) {
        parsed.ackFor?.let { ackFor ->
            repository.updateMessageStatus(ackFor, "delivered")
            messageRouter?.markDelivered(ackFor)
        }
        return
    }

    if (parsed.type == Protocol.TYPE_IMAGE_ACCEPT ||
        parsed.type == Protocol.TYPE_VIDEO_ACCEPT ||
        parsed.type == Protocol.TYPE_AUDIO_ACCEPT
    ) {
        val originalId = parsed.body ?: return
        MediaHandshake.complete(originalId, true)
        repository.updateMessageStatus(originalId, "sent")
        return
    }

    if (parsed.type == Protocol.TYPE_IMAGE_REJECT ||
        parsed.type == Protocol.TYPE_VIDEO_REJECT ||
        parsed.type == Protocol.TYPE_AUDIO_REJECT
    ) {
        val originalId = parsed.body ?: return
        MediaHandshake.complete(originalId, false)
        repository.updateMessageStatus(originalId, "failed")
        return
    }

    // Store-and-forward: if the message isn't for us, try mesh forwarding
    val acceptById = parsed.to == null || parsed.to == myId
    val acceptByAddr = !DeviceId.isMaskedAddress(localAddr) && parsed.to == localAddr
    val acceptWhenMasked = DeviceId.isMaskedAddress(localAddr) && parsed.to?.contains(":") == true
    // Classic server messages should be accepted even if the target is a MAC we cannot verify.
    val acceptByClassicAddr = sourceAddr != null && parsed.to?.contains(":") == true
    val classicFallback = acceptByClassicAddr && !acceptById && !acceptByAddr && !acceptWhenMasked
    if (!acceptById && !acceptByAddr && !acceptWhenMasked && !acceptByClassicAddr) {
        Log.d(
            "ChatScreen",
            "Forwarding msgId=${parsed.msgId} to=${parsed.to} myId=$myId sourceAddr=${sourceAddr ?: ""}"
        )
        messageRouter?.forwardViaMesh(parsed.to, jsonMessage)
        return
    }

    Log.d(
        "ChatScreen",
        "Accepting msgId=${parsed.msgId} to=${parsed.to} myId=$myId sourceAddr=${sourceAddr ?: ""}"
    )

    val incomingStatus = if (classicFallback) "received-classic" else "received"

    when (parsed.type) {
        Protocol.TYPE_IMAGE_OFFER,
        Protocol.TYPE_AUDIO_OFFER,
        Protocol.TYPE_VIDEO_OFFER -> {
            val fileSize = parsed.fileSize ?: 0L
            val maxClassicBytes = if (parsed.type == Protocol.TYPE_VIDEO_OFFER) {
                TransportThresholds.MAX_CLASSIC_VIDEO_BYTES
            } else {
                TransportThresholds.MAX_CLASSIC_MEDIA_BYTES
            }
            if (fileSize > maxClassicBytes) {
                buildMediaReject(parsed, myId)?.let { reject ->
                    messageRouter?.sendAck(parsed.from, reject, ackClassicKey)
                }
                repository.insertMessage(
                    Message(
                        msgId = parsed.msgId,
                        type = parsed.type,
                        fromId = parsed.from,
                        toId = parsed.to ?: myId,
                        timestamp = parsed.ts,
                        fileName = parsed.fileName,
                        fileSize = parsed.fileSize,
                        mime = parsed.mime,
                        status = "rejected",
                        isIncoming = true
                    )
                )
                return
            }

            buildMediaAccept(parsed, myId)?.let { accept ->
                messageRouter?.sendAck(parsed.from, accept, ackClassicKey)
            }
            val transfer = ensureIncomingTransfer(context, parsed, parsed.type)
            val normalizedToId = if (acceptByAddr || acceptWhenMasked) myId else (parsed.to ?: myId)
            val message = Message(
                msgId = parsed.msgId,
                type = parsed.type,
                fromId = parsed.from,
                toId = normalizedToId,
                timestamp = parsed.ts,
                fileName = transfer.fileName,
                fileSize = parsed.fileSize,
                mime = parsed.mime,
                status = "receiving",
                isIncoming = true
            )
            repository.insertMessage(message)
            IncomingMessageAlerts.play(context, parsed.type)
            return
        }
        Protocol.TYPE_IMAGE_CHUNK,
        Protocol.TYPE_AUDIO_CHUNK,
        Protocol.TYPE_VIDEO_CHUNK -> {
            val transfer = IncomingTransfers.get(parsed.msgId)
            if (transfer == null) {
                Log.w("ChatScreen", "Received chunk for unknown transfer msgId=${parsed.msgId}, ignoring")
                return
            }
            val data = parsed.data ?: return
            appendIncomingChunk(transfer, data, parsed.totalChunks)
            return
        }
        Protocol.TYPE_IMAGE_COMPLETE,
        Protocol.TYPE_AUDIO_COMPLETE,
        Protocol.TYPE_VIDEO_COMPLETE -> {
            val originalId = parsed.body ?: parsed.msgId
            val transfer = IncomingTransfers.remove(originalId)
            if (transfer != null) {
                Log.d("ChatScreen", "Media COMPLETE: type=${transfer.type} file=${transfer.file.absolutePath} exists=${transfer.file.exists()} size=${transfer.file.length()} receivedChunks=${transfer.receivedChunks}/${transfer.totalChunks}")
                val actualSize = transfer.file.length()
                val expectedSize = transfer.fileSize
                val chunkMismatch = transfer.totalChunks > 0 && transfer.receivedChunks < transfer.totalChunks
                val sizeMismatch = expectedSize != null && expectedSize > 0L && actualSize != expectedSize
                if (chunkMismatch || sizeMismatch) {
                    Log.e(
                        "ChatScreen",
                        "Media integrity check failed msgId=$originalId chunkMismatch=$chunkMismatch sizeMismatch=$sizeMismatch actualSize=$actualSize expectedSize=$expectedSize receivedChunks=${transfer.receivedChunks} totalChunks=${transfer.totalChunks}"
                    )
                    repository.updateMessageStatus(originalId, "failed")
                    return
                }
                val existing = repository.getMessageById(originalId)
                val normalizedToId = if (acceptByAddr || acceptWhenMasked) myId else (parsed.to ?: myId)
                val updated = (existing ?: Message(
                    msgId = originalId,
                    type = transfer.type,
                    fromId = parsed.from,
                    toId = normalizedToId,
                    timestamp = parsed.ts,
                    fileName = transfer.fileName,
                    fileSize = transfer.fileSize,
                    mime = transfer.mime,
                    status = incomingStatus,
                    isIncoming = true
                )).copy(filePath = transfer.file.absolutePath, status = incomingStatus)
                if (existing == null) {
                    repository.insertMessage(updated)
                } else {
                    repository.updateMessage(updated)
                }
            } else {
                Log.w("ChatScreen", "Media COMPLETE but no transfer found for msgId=$originalId")
            }
            return
        }
    }

    if (chatPeer != null && chatPeer.address.isNotBlank() && parsed.from.isNotBlank()) {
        val isAddressChat = chatPeer.id.contains(":")
        if (isAddressChat) {
            val existing = repository.getPeerByAddress(chatPeer.address)
            if (existing == null || existing.id != parsed.from) {
                val existingById = repository.getPeerById(parsed.from)
                val tcpHost = existingById?.tcpHost ?: existing?.tcpHost
                val tcpPort = existingById?.tcpPort ?: existing?.tcpPort
                repository.insertPeer(
                    Peer(
                        id = parsed.from,
                        name = chatPeer.name,
                        address = chatPeer.address,
                        type = chatPeer.type,
                        lastSeen = System.currentTimeMillis(),
                        rssi = 0,
                        tcpHost = tcpHost,
                        tcpPort = tcpPort
                    )
                )
            }
        }
    }

    if (!sourceAddr.isNullOrBlank() && parsed.from.isNotBlank()) {
        val existing = repository.getPeerById(parsed.from)
        if (existing == null) {
            val existingByAddress = repository.getPeerByAddress(sourceAddr)
            if (existingByAddress != null && existingByAddress.id != parsed.from) {
                repository.deletePeerById(existingByAddress.id)
            }
            val name = if (parsed.type == Protocol.TYPE_REGISTER && !parsed.body.isNullOrBlank()) {
                parsed.body
            } else {
                sourceAddr
            }
            repository.insertPeer(
                Peer(
                    id = parsed.from,
                    name = name ?: sourceAddr,
                    address = sourceAddr,
                    type = "CLASSIC",
                    lastSeen = System.currentTimeMillis(),
                    rssi = 0,
                    tcpHost = existing?.tcpHost,
                    tcpPort = existing?.tcpPort
                )
            )
        } else if (existing.address != sourceAddr) {
            repository.updatePeer(existing.copy(address = sourceAddr))
        }
    }

    val normalizedToId = if (chatPeer != null && chatPeer.id.contains(":")) {
        chatPeer.id
    } else if (acceptByAddr || acceptWhenMasked) {
        myId
    } else {
        parsed.to ?: myId
    }
    val message = Message(
        msgId = parsed.msgId,
        type = parsed.type,
        fromId = parsed.from,
        toId = normalizedToId,
        timestamp = parsed.ts,
        body = parsed.body,
        fileName = parsed.fileName,
        fileSize = parsed.fileSize,
        mime = parsed.mime,
        duration = parsed.duration,
        status = incomingStatus,
        isIncoming = true
    )

    repository.insertMessage(message)
    IncomingMessageAlerts.play(context, parsed.type)

    // Send ACK back for reliability
    val ackPayload = Protocol.createAckMessage(
        msgId = UUID.randomUUID().toString(),
        from = myId,
        to = parsed.from,
        ackFor = parsed.msgId
    )
    messageRouter?.sendAck(parsed.from, ackPayload, ackClassicKey)
}

fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> "${diff / 86_400_000}d ago"
    }
}

fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return if (minutes > 0) "%d:%02d".format(minutes, remainingSeconds) else "0:%02d".format(remainingSeconds)
}

private object IncomingMessageAlerts {
    private const val MIN_ALERT_INTERVAL_MS = 800L
    private var lastAlertAtMs: Long = 0L

    fun play(context: Context, type: String) {
        if (!shouldAlert(type)) return

        val now = System.currentTimeMillis()
        synchronized(this) {
            if (now - lastAlertAtMs < MIN_ALERT_INTERVAL_MS) return
            lastAlertAtMs = now
        }

        if (AlertPreferences.isSoundEnabled(context)) {
            runCatching {
                val toneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(context, toneUri)
                ringtone?.play()
            }.onFailure {
                Log.w("ChatScreen", "Incoming alert tone failed", it)
            }
        }

        if (AlertPreferences.isVibrationEnabled(context)) {
            runCatching {
                val vibrator = context.getSystemService(Vibrator::class.java)
                if (vibrator?.hasVibrator() == true) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(120L, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(120L)
                    }
                }
            }.onFailure {
                Log.w("ChatScreen", "Incoming alert vibration failed", it)
            }
        }
    }

    private fun shouldAlert(type: String): Boolean {
        return when (type) {
            Protocol.TYPE_TEXT,
            Protocol.TYPE_IMAGE_OFFER,
            Protocol.TYPE_VIDEO_OFFER,
            Protocol.TYPE_AUDIO_OFFER -> true
            else -> false
        }
    }
}
