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
import com.btmessenger.app.data.repository.MessengerRepository
import com.btmessenger.app.data.entities.Group
import java.util.*
import com.btmessenger.app.data.entities.Peer
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NearbyPeersScreen(
    onPeerSelected: (Peer) -> Unit,
    onGroupSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Bluetooth components
    val bleScanner = remember { BleScanner(context) }
    val bleAdvertiser = remember { BleAdvertiser(context) }
    val gattServer = remember { GattServer(context) }
    val database = remember { com.btmessenger.app.data.AppDatabase.getDatabase(context) }
    val classicServer = remember { ClassicServer(context, android.os.Build.MODEL, database.groupDao()) }
    val repository = remember { MessengerRepository(database.peerDao(), database.messageDao(), database.groupDao()) }

    var showGroups by remember { mutableStateOf(false) }
    val classicClient = remember { ClassicClient(context) }
    val gattClient = remember { GattClient(context) }

    val isScanning by bleScanner.isScanning.collectAsState()
    val isAdvertising by bleAdvertiser.isAdvertising.collectAsState()
    val discoveredPeers by bleScanner.discoveredPeers.collectAsState()
    val groups by repository.getAllGroups().collectAsState(initial = emptyList())
    var showMessageForGroup by remember { mutableStateOf<String?>(null) }
    
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
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter = bluetoothManager.adapter
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { }
    
    // Check if Bluetooth is enabled
    val isBluetoothEnabled = bluetoothAdapter?.isEnabled == true
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nearby Devices") },
                actions = {
                    IconButton(onClick = {
                        if (isScanning) {
                            bleScanner.stopScanning()
                        } else {
                            bleScanner.startScanning()
                        }
                    }) {
                        Icon(
                            imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.Search,
                            contentDescription = if (isScanning) "Stop Scanning" else "Start Scanning"
                        )
                    }
                    IconButton(onClick = { showGroups = true }) {
                        Icon(Icons.Default.Group, contentDescription = "Groups")
                    }
                    
                    IconButton(onClick = {
                        bleScanner.clearPeers()
                    }) {
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
        var showGroups by remember { mutableStateOf(false) }

        if (showGroups) {
                GroupsDialog(
                groups = groups,
                onCreate = { name ->
                    val gid = UUID.randomUUID().toString()
                    val hostIdVal = bluetoothAdapter?.address ?: android.os.Build.MODEL
                    val g = Group(groupId = gid, name = name, hostId = hostIdVal, createdAt = System.currentTimeMillis())
                    scope.launch { repository.insertGroup(g) }
                    showGroups = false
                },
                onJoin = { gid ->
                    // Local join: insert or increment memberCount locally
                    scope.launch {
                        val existing = repository.getGroupById(gid)
                        if (existing == null) {
                            val g = Group(groupId = gid, name = gid, hostId = "", createdAt = System.currentTimeMillis(), memberCount = 1)
                            repository.insertGroup(g)
                        } else {
                            repository.insertGroup(existing.copy(memberCount = existing.memberCount + 1))
                        }
                    }
                    showGroups = false
                },
                onDismiss = { showGroups = false }
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
                // Groups header
                Text(
                    "Groups",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )

                if (groups.isEmpty()) {
                    Text(
                        "No groups",
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(groups) { group ->
                            Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                        .clickable { onGroupSelected(group.groupId) },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                    Column {
                                    Text(group.name)
                                    Text("Members: ${group.memberCount}", style = MaterialTheme.typography.bodySmall)
                                }
                                Row {
                                Button(onClick = {
                                    // local join action + broadcast GROUP_JOIN
                                    scope.launch {
                                        val existing = repository.getGroupById(group.groupId)
                                        if (existing == null) {
                                            repository.insertGroup(group.copy(memberCount = 1))
                                        } else {
                                            repository.insertGroup(existing.copy(memberCount = existing.memberCount + 1))
                                        }

                                        // Send GROUP_JOIN only to the group's host when known
                                        val deviceId = android.os.Build.MODEL
                                        val msg = Protocol.createGroupJoinMessage(UUID.randomUUID().toString(), deviceId, group.groupId)
                                        val hostId = repository.getGroupById(group.groupId)?.hostId
                                        val targetPeer = if (!hostId.isNullOrEmpty()) {
                                            // prefer discoveredPeers by address or id
                                            discoveredPeers.firstOrNull { it.address == hostId || it.id == hostId }
                                        } else null

                                        if (targetPeer != null) {
                                            bluetoothAdapter?.getRemoteDevice(targetPeer.address)?.let { device ->
                                                try {
                                                    if (targetPeer.type == "BLE") {
                                                        val ok = gattClient.connect(device)
                                                        if (ok) {
                                                            gattClient.sendMessage(msg)
                                                            gattClient.disconnect()
                                                        }
                                                    } else {
                                                        val ok = classicClient.connect(device, deviceId, deviceId)
                                                        if (ok) {
                                                            classicClient.sendMessage(msg)
                                                            classicClient.disconnect()
                                                        }
                                                    }
                                                } catch (_: Exception) { }
                                            }
                                        } else {
                                            // fallback: broadcast to all discovered peers
                                            val adapter = bluetoothAdapter
                                            for (peer in discoveredPeers) {
                                                try {
                                                    adapter?.getRemoteDevice(peer.address)?.let { device ->
                                                        if (peer.type == "BLE") {
                                                            val ok = gattClient.connect(device)
                                                            if (ok) {
                                                                gattClient.sendMessage(msg)
                                                                gattClient.disconnect()
                                                            }
                                                        } else {
                                                            val ok = classicClient.connect(device, deviceId, deviceId)
                                                            if (ok) {
                                                                classicClient.sendMessage(msg)
                                                                classicClient.disconnect()
                                                            }
                                                        }
                                                    }
                                                } catch (_: Exception) { }
                                            }
                                        }
                                    }
                                }) {
                                    Text("Join")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = {
                                    // local leave action + broadcast GROUP_LEAVE
                                    scope.launch {
                                        val existing = repository.getGroupById(group.groupId)
                                        if (existing != null) {
                                            val newCount = (existing.memberCount - 1).coerceAtLeast(0)
                                            if (newCount <= 0) {
                                                repository.deleteGroup(existing)
                                            } else {
                                                repository.insertGroup(existing.copy(memberCount = newCount))
                                            }
                                        }

                                        // Send GROUP_LEAVE to group's host when possible
                                        val deviceId = android.os.Build.MODEL
                                        val msg = Protocol.createGroupLeaveMessage(UUID.randomUUID().toString(), deviceId, group.groupId)
                                        val hostId = repository.getGroupById(group.groupId)?.hostId
                                        val targetPeer = if (!hostId.isNullOrEmpty()) discoveredPeers.firstOrNull { it.address == hostId || it.id == hostId } else null

                                        if (targetPeer != null) {
                                            bluetoothAdapter?.getRemoteDevice(targetPeer.address)?.let { device ->
                                                try {
                                                    if (targetPeer.type == "BLE") {
                                                        val ok = gattClient.connect(device)
                                                        if (ok) {
                                                            gattClient.sendMessage(msg)
                                                            gattClient.disconnect()
                                                        }
                                                    } else {
                                                        val ok = classicClient.connect(device, deviceId, deviceId)
                                                        if (ok) {
                                                            classicClient.sendMessage(msg)
                                                            classicClient.disconnect()
                                                        }
                                                    }
                                                } catch (_: Exception) {}
                                            }
                                        } else {
                                            val adapter = bluetoothAdapter
                                            for (peer in discoveredPeers) {
                                                try {
                                                    adapter?.getRemoteDevice(peer.address)?.let { device ->
                                                        if (peer.type == "BLE") {
                                                            val ok = gattClient.connect(device)
                                                            if (ok) {
                                                                gattClient.sendMessage(msg)
                                                                gattClient.disconnect()
                                                            }
                                                        } else {
                                                            val ok = classicClient.connect(device, deviceId, deviceId)
                                                            if (ok) {
                                                                classicClient.sendMessage(msg)
                                                                classicClient.disconnect()
                                                            }
                                                        }
                                                    }
                                                } catch (_: Exception) {}
                                            }
                                        }
                                    }
                                }) {
                                    Text("Leave")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = { showMessageForGroup = group.groupId }) {
                                    Text("Message")
                                }
                                }
                            }
                        }
                    }
                }

                // Group message dialog
                showMessageForGroup?.let { gid ->
                    GroupMessageDialog(
                        groupId = gid,
                        onSend = { text ->
                            scope.launch {
                                // Persist message locally
                                val msgId = UUID.randomUUID().toString()
                                val deviceId = android.os.Build.MODEL
                                val messageEntity = com.btmessenger.app.data.entities.Message(
                                    msgId = msgId,
                                    type = Protocol.TYPE_GROUP_MESSAGE,
                                    fromId = deviceId,
                                    toId = "",
                                    groupId = gid,
                                    timestamp = System.currentTimeMillis(),
                                    body = text,
                                    status = "sent",
                                    isIncoming = false
                                )
                                repository.insertMessage(messageEntity)

                                // Send GROUP_MESSAGE to group's host when possible
                                val json = Protocol.createGroupTextMessage(msgId, deviceId, gid, text)
                                val hostId = repository.getGroupById(gid)?.hostId
                                val targetPeer = if (!hostId.isNullOrEmpty()) discoveredPeers.firstOrNull { it.address == hostId || it.id == hostId } else null

                                if (targetPeer != null) {
                                    bluetoothAdapter?.getRemoteDevice(targetPeer.address)?.let { device ->
                                        try {
                                            if (targetPeer.type == "BLE") {
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
                                        } catch (_: Exception) {}
                                    }
                                } else {
                                    val adapter = bluetoothAdapter
                                    for (peer in discoveredPeers) {
                                        try {
                                            adapter?.getRemoteDevice(peer.address)?.let { device ->
                                                if (peer.type == "BLE") {
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
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                            showMessageForGroup = null
                        },
                        onDismiss = { showMessageForGroup = null }
                    )
                }
            // Permission and Bluetooth status
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
                        Text(
                            "Bluetooth Permissions Required",
                            style = MaterialTheme.typography.titleMedium
                        )
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
                                activity?.let { com.btmessenger.app.permission.PermissionHelper.requestBluetoothPermissions(it, com.btmessenger.app.permission.PermissionHelper.REQUEST_BLUETOOTH_PERMS) }
                            }) {
                                Text("Request via System")
                            }
                        }
                    }
                }
            }
            
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
                        Text(
                            "Bluetooth is Disabled",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Please enable Bluetooth to use this app.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            enableBluetoothLauncher.launch(enableBtIntent)
                        }) {
                            Text("Enable Bluetooth")
                        }
                    }
                }
            }
            
            // Status indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatusChip(
                    label = "Scanning",
                    isActive = isScanning,
                    icon = Icons.Default.Search
                )
                StatusChip(
                    label = "Visible",
                    isActive = isAdvertising,
                    icon = Icons.Default.Visibility
                )
            }
            
            Divider()
            
            // Discovered peers list
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
                                text = "Tap the search icon to start scanning",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(discoveredPeers) { peer ->
                        PeerItem(
                            peer = peer,
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
                Text(
                    text = peer.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = peer.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                peer.rssi?.let { rssi ->
                    Text(
                        text = "Signal: $rssi dBm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
