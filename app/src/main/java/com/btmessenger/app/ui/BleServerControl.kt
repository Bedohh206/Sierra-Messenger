package com.btmessenger.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.btmessenger.app.bluetooth.BleViewModel

@Composable
fun MainScreen() {
    val vm: BleViewModel = viewModel()

    DisposableEffect(Unit) {
        vm.startServer()
        onDispose { vm.stopServer() }
    }

    // ...rest of UI (Scan/Connect buttons etc.)
}
