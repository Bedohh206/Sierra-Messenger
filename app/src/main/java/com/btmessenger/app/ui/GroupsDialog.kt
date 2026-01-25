package com.btmessenger.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.btmessenger.app.data.entities.Group

@Composable
fun GroupsDialog(
    groups: List<Group>,
    onCreate: (String) -> Unit,
    onJoin: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }
    var joinId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Groups") },
        text = {
            Column {
                if (groups.isEmpty()) {
                    Text("No groups available")
                } else {
                    groups.forEach { g ->
                        Text("${g.name} (id: ${g.groupId})")
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (showCreate) {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Group name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = joinId,
                        onValueChange = { joinId = it },
                        label = { Text("Join Group ID") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    if (showCreate) {
                        if (groupName.isNotBlank()) onCreate(groupName)
                    } else {
                        if (joinId.isNotBlank()) onJoin(joinId)
                    }
                }) { Text(if (showCreate) "Create" else "Join") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { showCreate = !showCreate }) { Text(if (showCreate) "Switch to Join" else "Switch to Create") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
