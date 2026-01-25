package com.btmessenger.app.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import com.btmessenger.app.data.entities.Peer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * BLE Scanner for discovering nearby devices
 */
class BleScanner(private val context: Context) {
    
    private val tag = "BleScanner"
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var scanner: BluetoothLeScanner? = null
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning
    
    private val _discoveredPeers = MutableStateFlow<List<Peer>>(emptyList())
    val discoveredPeers: StateFlow<List<Peer>> = _discoveredPeers
    
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.let { scanResult ->
                val device = scanResult.device
                val deviceName = if (hasRequiredPermissions()) {
                    device.name ?: "Unknown Device"
                } else {
                    "Unknown Device"
                }
                val deviceAddress = device.address
                val rssi = scanResult.rssi
                
                Log.d(tag, "Found device: $deviceName ($deviceAddress) RSSI: $rssi")
                
                val peer = Peer(
                    id = deviceAddress,
                    name = deviceName,
                    address = deviceAddress,
                    type = "BLE",
                    lastSeen = System.currentTimeMillis(),
                    rssi = rssi
                )
                
                val currentPeers = _discoveredPeers.value.toMutableList()
                val existingIndex = currentPeers.indexOfFirst { it.address == deviceAddress }
                
                if (existingIndex != -1) {
                    currentPeers[existingIndex] = peer
                } else {
                    currentPeers.add(peer)
                }
                
                _discoveredPeers.value = currentPeers
            }
        }
        
        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            super.onBatchScanResults(results)
            results?.forEach { result ->
                onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(tag, "BLE scan failed with error: $errorCode")
            _isScanning.value = false
        }
    }
    
    @SuppressLint("MissingPermission")
    fun startScanning(): Boolean {
        if (!hasRequiredPermissions()) {
            Log.e(tag, "Missing Bluetooth permissions")
            return false
        }
        
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(tag, "Bluetooth is not available or not enabled")
            return false
        }
        
        scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e(tag, "Device does not support BLE scanning")
            return false
        }
        
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(Protocol.SERVICE_UUID))
            .build()
        
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        
        try {
            scanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
            _isScanning.value = true
            Log.d(tag, "BLE scanning started")
            return true
        } catch (e: Exception) {
            Log.e(tag, "Failed to start scanning", e)
            return false
        }
    }
    
    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (!hasRequiredPermissions()) {
            return
        }
        
        try {
            scanner?.stopScan(scanCallback)
            _isScanning.value = false
            Log.d(tag, "BLE scanning stopped")
        } catch (e: Exception) {
            Log.e(tag, "Failed to stop scanning", e)
        }
    }
    
    fun clearPeers() {
        _discoveredPeers.value = emptyList()
    }
    
    private fun hasRequiredPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
    }
}
