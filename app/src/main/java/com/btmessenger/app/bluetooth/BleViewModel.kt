package com.btmessenger.app.bluetooth

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class BleViewModel(app: Application) : AndroidViewModel(app) {
    private val server = BleGattServerManager(app.applicationContext)
    private val scanner = BleScanner(app.applicationContext)

    fun startServer() = server.start()
    fun stopServer() = server.stop()

    // Use filtered by default; add fallback if needed.
    fun rescan() = scanner.startScanFiltered()
    fun rescanUnfiltered() = scanner.startScanUnfiltered()
    fun stopScan() = scanner.stopScan()
    fun startDebugScan() = scanner.startDebugScan()
    fun stopDebugScan() = scanner.stopDebugScan()

    val isScanning = scanner.isScanning
    val found = scanner.found

    override fun onCleared() {
        scanner.stopScan()
        server.stop()
        super.onCleared()
    }
}
