package com.btmessenger.app.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
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

    // Database
    val database = remember { AppDatabase.getDatabase(context) }
    val friendDao = remember { database.friendDao() }

   val database = remember { AppDatabase.getDatabase(context) }
   val friendDao = remember { database.friendDao() }
   val repository = remember { MessengerRepository(database.peerDao(), database.messageDao(), database.groupDao(), friendDao) }

   val classicClient = remember { ClassicClient(context) }
val gattClient = remember { GattClient(context, friendDao = friendDao) }

    // If your project still uses/creates clients here, pass friendDao to GattClient.
    // (Even if unused, this prevents the "No value passed for parameter 'friendDao'" error.)
    remember { com.btmessenger.app.bluetooth.GattClient(context, friendDao = friendDao) }
    remember { com.btmessenger.app.bluetooth.ClassicClient(context) }

    val messages by repository.getMessagesForGroup(groupId)
        .collectAsState(initial = emptyList())

    var text by remember { mutableStateOf("") }

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
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = text,
                        onTextChange = { text = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message") }
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
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

                            // You said broadcasting happens elsewhere; keep it that way.
                            // val json = Protocol.createGroupTextMessage(msgId, deviceId, groupId, text)

                            text = ""
                        }
                    }) { Text("Send") }
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
