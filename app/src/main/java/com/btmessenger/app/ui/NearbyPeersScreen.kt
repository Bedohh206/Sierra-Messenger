package com.btmessenger.app.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.btmessenger.app.bluetooth.*
import com.btmessenger.app.data.AppDatabase
import com.btmessenger.app.data.entities.Group
import com.btmessenger.app.data.entities.Peer
import com.btmessenger.app.data.entities.Friend
import com.btmessenger.app.data.repository.MessengerRepository
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NearbyPeersScreen(
    onPeerSelected: (Peer) -> Unit,
    onGroupSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ✅ Database FIRST (so everything else can use it safely)
    val database = remember { AppDatabase.getDatabase(context) }
    val friendDao = remember { database.friendDao() }
    val groupDao = remember { database.groupDao() }
    val repository = remember {
        MessengerRepository(
            database.peerDao(),
            database.messageDao(),
            database.groupDao(),
            database.friendDao()
        )
    }

    // ✅ Bluetooth components
    val bleScanner = remember { BleScanner(context) }
    val bleAdvertiser = remember { BleAdvertiser(context) }
    val gattServer = remember { GattServer(context, friendDao) }
    val classicServer = remember { ClassicServer(context, android.os.Build.MODEL, groupDao) }

    val classicClient = remember { ClassicClient(context) }
    val gattClient = remember { GattClient(context, friendDao = friendDao) }

    // ✅ UI state
    var showGroupsDialog by remember { mutableStateOf(false) }
    var showMessageForGroup by remember { mutableStateOf<String?>(null) }

    // ✅ Streams
    val isScanning by bleScanner.isScanning.collectAsState()
    val isAdvertising by bleAdvertiser.isAdvertising.collectAsState()
    val discoveredPeers by bleScanner.discoveredPeers.collectAsState()
    val groups by repository.getAllGroups().collectAsState(initial = emptyList())

    // ✅ Collect friends ONCE (not inside LazyColumn rows)
    val friends by repository.getAllFriends().collectAsState(initial = emptyList())

    // Permissions
    val permissionsList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
    val permissionsState = rememberMultiplePermissionsState(permissionsList)

    // Bluetooth enable launcher
    val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter = bluetoothManager.adapter
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { }

    val isBluetoothEnabled = bluetoothAdapter?.isEnabled == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nearby Devices") },
                actions = {
                    IconButton(onClick = {
                        if (isScanning) bleScanner.stopScanning() else bleScanner.startScanning()
                    }) {
                        Icon(
                            imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.Search,
                            contentDescription = if (isScanning) "Stop Scanning" else "Start Scanning"
                        )
                    }
                    IconButton(onClick = { showGroupsDialog = true }) {
                        Icon(Icons.Default.Group, contentDescription = "Groups")
                    }
                    IconButton(onClick = { bleScanner.clearPeers() }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (isAdvertising) {
                        bleAdvertiser.stopAdvertising()
                        gattServer.stopServer()
                        classicServer.stopServer()
                    } else {
                        bleAdvertiser.startAdvertising()
                        gattServer.startServer()
                        classicServer.startServer()
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (isAdvertising) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null
                    )
                },
                text = { Text(if (isAdvertising) "Visible" else "Hidden") }
            )
        }
    ) { paddingValues ->

        // ✅ Groups dialog (single source of truth)
        if (showGroupsDialog) {
            GroupsDialog(
                groups = groups,
                onCreate = { name ->
                    val gid = UUID.randomUUID().toString()
                    val hostIdVal = bluetoothAdapter?.address ?: android.os.Build.MODEL
                    val g = Group(
                        groupId = gid,
                        name = name,
                        hostId = hostIdVal,
                        createdAt = System.currentTimeMillis()
                    )
                    scope.launch { repository.insertGroup(g) }
                    showGroupsDialog = false
                },
                onJoin = { gid ->
                    scope.launch {
                        val existing = repository.getGroupById(gid)
                        if (existing == null) {
                            val g = Group(
                                groupId = gid,
                                name = gid,
                                hostId = "",
                                createdAt = System.currentTimeMillis(),
                                memberCount = 1
                            )
                            repository.insertGroup(g)
                        } else {
                            repository.insertGroup(existing.copy(memberCount = existing.memberCount + 1))
                        }
                    }
                    showGroupsDialog = false
                },
                onDismiss = { showGroupsDialog = false }
            )
        }

        // ✅ Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {

            // Group message dialog
            showMessageForGroup?.let { gid ->
                GroupMessageDialog(
                    groupId = gid,
                    onSend = { text ->
                        scope.launch {
                            val msgId = UUID.randomUUID().toString()
                            val deviceId = android.os.Build.MODEL

                            // Persist local; broadcast handled in your logic below/elsewhere
                            val json = Protocol.createGroupTextMessage(msgId, deviceId, gid, text)

                            // Try host if known, else broadcast to all
                            val hostId = repository.getGroupById(gid)?.hostId
                            val targetPeer =
                                if (!hostId.isNullOrEmpty())
                                    discoveredPeers.firstOrNull { it.address == hostId || it.id == hostId }
                                else null

                            suspend fun sendToPeer(p: Peer) {
                                bluetoothAdapter?.getRemoteDevice(p.address)?.let { device ->
                                    if (p.type == "BLE") {
                                        val ok = gattClient.connect(device)
                                        if (ok) {
                                            gattClient.sendMessage(json)
                                            gattClient.disconnect()
                                        }
                                    } else {
                                        val ok = classicClient.connect(device, deviceId, deviceId)
                                        if (ok) {
                                            classicClient.sendMessage(json)
                                            classicClient.disconnect()
                                        }
                                    }
                                }
                            }

                            try {
                                if (targetPeer != null) {
                                    sendToPeer(targetPeer)
                                } else {
                                    for (p in discoveredPeers) sendToPeer(p)
                                }
                            } catch (_: Exception) { }
                        }
                        showMessageForGroup = null
                    },
                    onDismiss = { showMessageForGroup = null }
                )
            }

            // Permission status
            if (!permissionsState.allPermissionsGranted) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Bluetooth Permissions Required", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "This app needs Bluetooth permissions to discover and connect to nearby devices.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                                Text("Grant Permissions")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            val activity = LocalContext.current as? android.app.Activity
                            OutlinedButton(onClick = {
                                activity?.let {
                                    com.btmessenger.app.permission.PermissionHelper.requestBluetoothPermissions(
                                        it,
                                        com.btmessenger.app.permission.PermissionHelper.REQUEST_BLUETOOTH_PERMS
                                    )
                                }
                            }) {
                                Text("Request via System")
                            }
                        }
                    }
                }
            }

            // Bluetooth disabled card
            if (!isBluetoothEnabled) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Bluetooth is Disabled", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Please enable Bluetooth to use this app.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            enableBluetoothLauncher.launch(intent)
                        }) {
                            Text("Enable Bluetooth")
                        }
                    }
                }
            }

            // Status chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatusChip("Scanning", isScanning, Icons.Default.Search)
                StatusChip("Visible", isAdvertising, Icons.Default.Visibility)
            }

            Divider()

            // ✅ Discovered peers list
            if (discoveredPeers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.BluetoothSearching,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isScanning) "Searching for devices..." else "No devices found",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (!isScanning) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Tap the search icon to start scanning",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(discoveredPeers) { peer ->
                        val isFriend = friends.any { it.id == peer.id || it.address == peer.address }

                        PeerItem(
                            peer = peer,
                            isFriend = isFriend,
                            onInvite = {
                                scope.launch {
                                    val f = Friend(id = peer.id, name = peer.name, address = peer.address)
                                    repository.insertFriend(f)
                                }
                            },
                            onClick = { onPeerSelected(peer) }
                        )
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            bleScanner.stopScanning()
            bleAdvertiser.stopAdvertising()
            gattServer.stopServer()
            classicServer.stopServer()
        }
    }
}

@Composable
fun StatusChip(
    label: String,
    isActive: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeerItem(
    peer: Peer,
    isFriend: Boolean = false,
    onInvite: () -> Unit = {},
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (peer.type == "BLE") Icons.Default.Bluetooth else Icons.Default.BluetoothConnected,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(peer.name, style = MaterialTheme.typography.titleMedium)
                Text(peer.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                peer.rssi?.let { rssi ->
                    Text("Signal: $rssi dBm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (isFriend) {
                Button(onClick = { }) { Text("Friend") }
            } else {
                OutlinedButton(onClick = onInvite) { Text("Invite") }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
