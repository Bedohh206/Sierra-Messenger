package com.btmessenger.app.ui

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.lifecycle.viewmodel.compose.viewModel
import com.btmessenger.app.bluetooth.BleViewModel
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppRoot(
    onPeerSelected: (String) -> Unit,
    onGroupSelected: (String) -> Unit
) {
    val bleViewModel: BleViewModel = viewModel()
    val isScanningState by bleViewModel.isScanning.collectAsState()
    val found by bleViewModel.found.collectAsState()
    
    LaunchedEffect(Unit) {
        Log.d("UI_TEST", "AppRoot composed")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text("Bluetooth Messenger", style = MaterialTheme.typography.titleLarge)

        Spacer(Modifier.height(12.dp))

        // Scanning header with indicator so screen is never visually empty
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isScanningState) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp))
                Text("Scanning…", color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                // Non-functional affordance to indicate user can retry
                Button(onClick = {
                    Log.d("UI_INTERACT", "Rescan clicked")
                    bleViewModel.rescan()
                }) { Text("Rescan") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    Log.d("UI_INTERACT", "Debug scan clicked")
                    bleViewModel.startDebugScan()
                }) { Text("Debug Scan") }
            } else {
                Text("Scanner idle", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Button(onClick = { bleViewModel.rescan() }) { Text("Rescan") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { bleViewModel.startDebugScan() }) { Text("Debug Scan") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { bleViewModel.stopScan() }) { Text("Stop") }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (found.isEmpty()) {
            // Helpful, non-empty fallback content when there are no peers
            Text(
                "No devices found yet",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "• Make sure Bluetooth is ON\n" +
                        "• Grant location / nearby devices permission\n" +
                        "• Other device must be advertising\n" +
                        "• Try tapping ‘Rescan’",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(16.dp))

            // Lightweight visual placeholder to show activity
            Text(
                "Waiting for nearby devices to appear...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn {
                items(found) { result ->
                    val name = result.device.name ?: "Unknown"
                    val addr = result.device.address
                    Text(
                        text = "$name ($addr)",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPeerSelected(addr) }
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}
