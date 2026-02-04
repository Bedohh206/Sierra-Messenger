package com.btmessenger.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.btmessenger.app.permission.PermissionHelper

class BleScanner(private val context: Context) {

    private val tag = "BleScanner"

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private val adapter = bluetoothManager.adapter
    private val scanner: BluetoothLeScanner?
        get() = adapter?.bluetoothLeScanner

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _found = MutableStateFlow<List<ScanResult>>(emptyList())
    val found: StateFlow<List<ScanResult>> = _found

    private val cb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.d(tag, "onScanResult name=${result.device.name} addr=${result.device.address} rssi=${result.rssi}")
            val list = _found.value.toMutableList()

            // de-dupe by address
            val idx = list.indexOfFirst { it.device.address == result.device.address }
            if (idx >= 0) list[idx] = result else list.add(result)

            _found.value = list
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(tag, "onScanFailed error=$errorCode")
            _isScanning.value = false
        }
    }

    private val debugScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.d("BLE_SCAN", "onScanResult device=${result.device.address} name=${result.device.name} rssi=${result.rssi}")
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { Log.d("BLE_SCAN", "onBatch device=${it.device.address} name=${it.device.name}") }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BLE_SCAN", "onScanFailed code=$errorCode")
        }
    }

    /**
     * Filtered scan by SERVICE_UUID.
     * Some OEM stacks can be picky; so we also support a fallback unfiltered scan.
     */
    @SuppressLint("MissingPermission")
    fun startScanFiltered() {
        Log.d(tag, "START_SCAN (filtered) entered")
        if (!PermissionHelper.hasBluetoothPermissions(context)) {
            Log.e(tag, "START_SCAN blocked: missing permissions")
            return
        }
        if (adapter == null || !adapter.isEnabled) {
            Log.e(tag, "START_SCAN blocked: bluetooth disabled")
            return
        }
        val sc = scanner ?: run {
            Log.e(tag, "scanner is null (bluetooth off?)")
            return
        }

        stopScan()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(Protocol.SERVICE_UUID))
            .build()

        _found.value = emptyList()
        _isScanning.value = true
        sc.startScan(listOf(filter), settings, cb)
    }

    @SuppressLint("MissingPermission")
    fun startScanUnfiltered() {
        Log.d(tag, "START_SCAN (unfiltered) entered")
        if (!PermissionHelper.hasBluetoothPermissions(context)) {
            Log.e(tag, "START_SCAN blocked: missing permissions")
            return
        }
        if (adapter == null || !adapter.isEnabled) {
            Log.e(tag, "START_SCAN blocked: bluetooth disabled")
            return
        }
        val sc = scanner ?: run {
            Log.e(tag, "scanner is null (bluetooth off?)")
            return
        }

        stopScan()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        _found.value = emptyList()
        _isScanning.value = true
        sc.startScan(null, settings, cb)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val sc = scanner ?: return
        try {
            sc.stopScan(cb)
        } catch (_: Throwable) {
        }
        _isScanning.value = false
        Log.d(tag, "STOP_SCAN")
    }

    fun clearFound() {
        _found.value = emptyList()
    }

    @SuppressLint("MissingPermission")
    fun startDebugScan() {
        if (!PermissionHelper.hasBluetoothPermissions(context)) {
            Log.e("BLE_SCAN", "START_SCAN blocked: missing permissions")
            return
        }
        if (adapter == null || !adapter.isEnabled) {
            Log.e("BLE_SCAN", "START_SCAN blocked: bluetooth disabled")
            return
        }
        val sc = scanner
        if (sc == null) {
            Log.e("BLE_SCAN", "No BLE scanner available")
            return
        }
        // Stop any existing scan first to avoid SCAN_FAILED_ALREADY_STARTED
        try {
            sc.stopScan(cb)
        } catch (_: Throwable) {
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()
        sc.startScan(emptyList(), settings, debugScanCallback)
        Log.d("BLE_SCAN", "startDebugScan called")
    }

    @SuppressLint("MissingPermission")
    fun stopDebugScan() {
        val sc = scanner
        sc?.stopScan(debugScanCallback)
        Log.d("BLE_SCAN", "stopDebugScan called")
    }
}
