package com.btmessenger.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.btmessenger.app.bluetooth.Protocol
import com.btmessenger.app.data.AppDatabase
import com.btmessenger.app.data.entities.Message
import com.btmessenger.app.data.repository.MessengerRepository
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    groupId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Database (remember so it doesn't recreate on recomposition)
    val database = remember { AppDatabase.getDatabase(context) }
    val friendDao = remember { database.friendDao() }

    // Repository (stable instance)
    val repository = remember {
        MessengerRepository(
            peerDao = database.peerDao(),
            messageDao = database.messageDao(),
            groupDao = database.groupDao(),
            friendDao = friendDao
        )
    }

    // Optional: GATT client (only if needed for sending messages)
    val gattClient = remember { GattClient(context, friendDao = friendDao) }

    // Messages stream (Compose-friendly)
    val messages by repository
        .getMessagesForGroup(groupId)
        .collectAsState(initial = emptyList())

    // Text field state
    var text by remember { mutableStateOf("") }

    // ---------------------------------------------------------
    // Your Scaffold / UI goes below this point
    // ---------------------------------------------------------
}
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group: ${groupId.take(12)}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },   // ✅ correct callback
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message") }
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (text.isBlank()) return@Button
                            scope.launch {
                                val msgId = UUID.randomUUID().toString()
                                val deviceId = android.os.Build.MODEL

                                val entity = Message(
                                    msgId = msgId,
                                    type = Protocol.TYPE_GROUP_MESSAGE,
                                    fromId = deviceId,
                                    toId = "",
                                    groupId = groupId,
                                    timestamp = System.currentTimeMillis(),
                                    body = text,
                                    status = "sent",
                                    isIncoming = false
                                )

                                repository.insertMessage(entity)

                                // Broadcasting handled elsewhere (keep as you said)
                                text = ""
                            }
                        }
                    ) { Text("Send") }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            items(messages) { m ->
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(m.fromId, style = MaterialTheme.typography.labelSmall)
                    Text(m.body ?: "", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        java.text.SimpleDateFormat.getDateTimeInstance()
                            .format(java.util.Date(m.timestamp)),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
