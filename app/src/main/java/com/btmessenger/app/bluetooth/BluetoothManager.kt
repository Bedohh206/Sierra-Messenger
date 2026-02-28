package com.btmessenger.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager as AndroidBluetoothManager
import android.content.Context
import android.util.Log
import com.btmessenger.app.data.AppDatabase
import com.btmessenger.app.permission.PermissionHelper
import com.btmessenger.app.util.DeviceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Unified manager for all Bluetooth operations:
 * - GATT Server (receiving connections)
 * - BLE Advertiser (making device discoverable)
 * - BLE Scanner (discovering nearby devices)
 * 
 * Lifecycle-safe with coroutine scope management and automatic retry logic
 */
class BluetoothManager(private val context: Context) {

    private val tag = "BluetoothManager"

    private val adapter: BluetoothAdapter? =
        context.getSystemService(AndroidBluetoothManager::class.java)?.adapter

    private val database = AppDatabase.getDatabase(context)
    private val deviceId = DeviceId.getOrCreate(context)

    val gattServer = BleGattServerManager(context)
    val advertiser = BleAdvertiser(context)
    val scanner = BleScanner(context)
    val classicServer = ClassicServer(
        context,
        hostId = deviceId,
        groupDao = database.groupDao(),
        peerDao = database.peerDao(),
        displayName = android.os.Build.MODEL
    )

    // UI state flows
    val isScanning = scanner.isScanning
    val isAdvertising = advertiser.isAdvertising
    val foundDevices = scanner.found
    val advertiseFailures = advertiser.failureEvents

    private var isStarted = false
    
    // Lifecycle-safe coroutine scope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Retry configuration
    private var retryCount = 0
    private val maxRetries = 3
    private val retryDelayMs = 2000L

    /**
     * Start all Bluetooth services asynchronously with retry logic
     */
    fun startAsync() {
        scope.launch {
            startWithRetry()
        }
    }

    /**
     * Start with automatic retry on failure
     */
    private suspend fun startWithRetry() {
        retryCount = 0
        while (retryCount <= maxRetries) {
            try {
                start()
                retryCount = 0 // Reset on success
                return
            } catch (t: Throwable) {
                retryCount++
                if (retryCount > maxRetries) {
                    Log.e(tag, "Start failed after $maxRetries retries", t)
                    return
                }
                val delayMs = retryDelayMs * retryCount
                Log.w(tag, "Start failed (attempt $retryCount/$maxRetries), retrying in ${delayMs}ms", t)
                delay(delayMs)
            }
        }
    }

    /**
     * Start all Bluetooth services (GATT server, advertiser, scanner)
     */
    @SuppressLint("MissingPermission")
    @Synchronized
    fun start() {
        Log.d(tag, "start() called")

        if (isStarted) {
            Log.d(tag, "Already started")
            return
        }

        if (!PermissionHelper.hasBluetoothPermissions(context)) {
            Log.e(tag, "Missing Bluetooth permissions")
            throw SecurityException("Missing Bluetooth permissions")
        }

        if (adapter == null) {
            Log.e(tag, "No BluetoothAdapter")
            throw IllegalStateException("No BluetoothAdapter")
        }

        if (!adapter.isEnabled) {
            Log.w(tag, "Bluetooth OFF")
            throw IllegalStateException("Bluetooth OFF")
        }

        try {
            gattServer.start()
            Log.d(tag, "GATT server started")
        } catch (t: Throwable) {
            Log.e(tag, "GATT server failed", t)
            throw t
        }

        try {
            classicServer.startServer()
            Log.d(tag, "Classic Bluetooth server started")
        } catch (t: Throwable) {
            Log.e(tag, "Classic Bluetooth server failed", t)
            // Don't throw - Classic BT is optional
        }

        try {
            val advertiseStarted = advertiser.startAdvertising()
            if (advertiseStarted) {
                Log.d(tag, "Advertiser started")
            } else {
                Log.w(tag, "Advertiser unavailable/unsupported; continuing with Classic + scanner fallback")
            }
        } catch (t: Throwable) {
            Log.e(tag, "Advertiser failed", t)
        }

        try {
            scanner.startScanFiltered()
            Log.d(tag, "Scanner started")
        } catch (t: Throwable) {
            Log.e(tag, "Scanner failed", t)
            throw t
        }

        isStarted = true
        Log.i(tag, "All services started successfully")
    }

    /**
     * Stop all Bluetooth services asynchronously
     */
    fun stopAsync() {
        scope.launch {
            stop()
        }
    }

    /**
     * Stop all Bluetooth services
     */
    @SuppressLint("MissingPermission")
    @Synchronized
    fun stop() {
        Log.d(tag, "stop() called")

        if (!isStarted) {
            Log.d(tag, "Not started")
            return
        }

        try {
            scanner.stopScan()
            Log.d(tag, "Scanner stopped")
        } catch (t: Throwable) {
            Log.e(tag, "Scanner stop failed", t)
        }

        try {
            advertiser.stopAdvertising()
            Log.d(tag, "Advertiser stopped")
        } catch (t: Throwable) {
            Log.e(tag, "Advertiser stop failed", t)
        }

        try {
            gattServer.stop()
            Log.d(tag, "GATT server stopped")
        } catch (t: Throwable) {
            Log.e(tag, "GATT server stop failed", t)
        }

        try {
            classicServer.stopServer()
            Log.d(tag, "Classic Bluetooth server stopped")
        } catch (t: Throwable) {
            Log.e(tag, "Classic Bluetooth server stop failed", t)
        }

        isStarted = false
        Log.d(tag, "Bluetooth services stopped")
    }

    /**
     * Clean up resources and cancel coroutines
     */
    fun cleanup() {
        Log.d(tag, "cleanup() called")
        scope.cancel()
    }

    /**
     * Check if Bluetooth is enabled and permissions are granted
     */
    fun isReady(): Boolean {
        return adapter != null &&
                adapter.isEnabled &&
                PermissionHelper.hasBluetoothPermissions(context)
    }

    /**
     * Get current Bluetooth status
     */
    fun getStatus(): String {
        return when {
            adapter == null -> "No Bluetooth adapter"
            !adapter.isEnabled -> "Bluetooth OFF"
            !PermissionHelper.hasBluetoothPermissions(context) -> "Missing permissions"
            isStarted -> "Running"
            else -> "Stopped"
        }
    }
}
