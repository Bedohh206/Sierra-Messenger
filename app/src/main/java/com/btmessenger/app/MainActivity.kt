package com.btmessenger.app

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.util.Log
import android.Manifest
import android.bluetooth.BluetoothManager as AndroidBluetoothManager
import android.content.Intent
import android.content.pm.PackageManager

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.btmessenger.app.bluetooth.BluetoothService
import com.btmessenger.app.data.AppDatabase
import com.btmessenger.app.ui.AppRoot
import com.btmessenger.app.ui.ChatScreen
import com.btmessenger.app.data.entities.Peer
import com.btmessenger.app.permission.PermissionHelper
import com.btmessenger.app.util.DeviceCompatibility

class MainActivity : ComponentActivity() {

    private val serviceStartHandler = Handler(Looper.getMainLooper())
    private var serviceRetryAttempt = 0
    private var hasWindowFocusNow = false

    private companion object {
        const val MAX_SERVICE_START_RETRIES = 10
        const val SERVICE_START_RETRY_DELAY_MS = 1200L
    }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val bluetoothGranted = PermissionHelper.hasBluetoothPermissions(this)
        if (bluetoothGranted) {
            Log.d("MainActivity", "Bluetooth permissions granted")
            ensureBluetoothEnabled()
            requestBluetoothServiceStart("permission-result")
        } else {
            Log.w("MainActivity", "Bluetooth permissions denied: $perms")
        }
    }

    private val requestEnableBluetooth = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d("MainActivity", "Bluetooth enabled by user")
        } else {
            Log.w("MainActivity", "Bluetooth enable request denied")
        }
    }

    private fun ensurePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val perms = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.POST_NOTIFICATIONS
            )
            requestPermissions.launch(perms)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val perms = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            requestPermissions.launch(perms)
            return
        }

        PermissionHelper.requestPre12LocationPermission(this)
    }

    private fun ensureBluetoothEnabled() {
        val adapter = getSystemService(AndroidBluetoothManager::class.java)?.adapter
        if (adapter == null) {
            Log.w("MainActivity", "No BluetoothAdapter available")
            return
        }
        if (!adapter.isEnabled) {
            Log.w("MainActivity", "Bluetooth is OFF; requesting enable")
            val intent = Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestEnableBluetooth.launch(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("UI_ALIVE", "MainActivity.onCreate() reached")

        // Guard native library load: optional native features may be absent on some builds/devices.
        try {
            System.loadLibrary("dolphin")
            Log.d("Native", "libdolphin loaded")
        } catch (t: Throwable) {
            Log.w("Native", "libdolphin not found; continuing without native features", t)
        }

        // Log device compatibility info for debugging
        DeviceCompatibility.logCapabilities(this)

        // Don't initialize BluetoothManager here - it blocks onCreate for 1+ seconds
        // The foreground service will handle Bluetooth initialization
        ensurePermissions()
        ensureBluetoothEnabled()

        // main-thread heartbeat to detect UI thread stalls
        val heartbeatHandler = Handler(Looper.getMainLooper())
        val heartbeat = object : Runnable {
            override fun run() {
                Log.d("MAIN_HEARTBEAT", "tick")
                heartbeatHandler.postDelayed(this, 2000)
            }
        }

        // Start heartbeat and ensure it's cleaned up on pause
        heartbeatHandler.postDelayed(heartbeat, 2000)

        setContent {
            LaunchedEffect(Unit) {
                Log.d("UI_ALIVE", "setContent composed")
            }

            // BluetoothManager is now handled by BluetoothService, not MainActivity

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var selectedPeer by remember { mutableStateOf<Peer?>(null) }

                    if (selectedPeer != null) {
                        ChatScreen(
                            peer = selectedPeer!!,
                            onBack = { selectedPeer = null }
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "UI is alive ✅",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Spacer(Modifier.height(12.dp))

                            // If AppRoot is the problem, you'll still see the banner above.
                            AppRoot(
                                onPeerSelected = { peer ->
                                    Log.d("UI_ALIVE", "peer selected: $peer")
                                    // Store peer in database before opening chat
                                    lifecycleScope.launch {
                                        try {
                                            val db = AppDatabase.getDatabase(this@MainActivity)
                                            db.peerDao().insertPeer(peer)
                                            Log.d("MainActivity", "Stored peer in database: ${peer.id}")
                                            selectedPeer = peer
                                        } catch (e: Exception) {
                                            Log.e("MainActivity", "Failed to store peer", e)
                                            selectedPeer = peer // Still open chat even if DB insert fails
                                        }
                                    }
                                },
                                onGroupSelected = { group ->
                                    Log.d("UI_ALIVE", "group selected: $group")
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d("MainActivity","onStart")
        ensureBluetoothEnabled()
        requestBluetoothServiceStart("onStart")
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity","onResume")
    }

    override fun onStop() {
        Log.d("MainActivity","onStop")
        cancelBluetoothServiceStartRetries()
        super.onStop()
    }

    private fun requestBluetoothServiceStart(source: String) {
        if (!PermissionHelper.hasBluetoothPermissions(this)) {
            Log.w("MainActivity", "Bluetooth permissions missing; skipping service start ($source)")
            return
        }

        if (tryStartBluetoothService(source)) {
            cancelBluetoothServiceStartRetries()
            return
        }

        scheduleBluetoothServiceStartRetry(source)
    }

    private fun tryStartBluetoothService(source: String): Boolean {
        try {
            startService(Intent(this, BluetoothService::class.java))
            Log.d("MainActivity", "Started service with startService() from $source")
            return true
        } catch (e: IllegalStateException) {
            Log.w("MainActivity", "startService() blocked in $source: ${e.message}")
            return false
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start service in $source", e)
            return false
        }
    }

    private fun scheduleBluetoothServiceStartRetry(source: String) {
        if (serviceRetryAttempt >= MAX_SERVICE_START_RETRIES) {
            Log.w("MainActivity", "Giving up service start retries after $serviceRetryAttempt attempts")
            return
        }

        serviceRetryAttempt += 1
        val attempt = serviceRetryAttempt
        serviceStartHandler.postDelayed({
            if (isFinishing || isDestroyed) {
                return@postDelayed
            }

            val isForeground = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) && hasWindowFocusNow
            if (!isForeground) {
                Log.d("MainActivity", "Skipping retry #$attempt: app not fully foreground yet")
                scheduleBluetoothServiceStartRetry(source)
                return@postDelayed
            }

            if (tryStartBluetoothService("retry#$attempt/$source")) {
                cancelBluetoothServiceStartRetries()
            } else {
                scheduleBluetoothServiceStartRetry(source)
            }
        }, SERVICE_START_RETRY_DELAY_MS)
    }

    private fun cancelBluetoothServiceStartRetries() {
        serviceRetryAttempt = 0
        serviceStartHandler.removeCallbacksAndMessages(null)
    }

    private fun startBluetoothServiceFromBackground() {
        // Only use this from receivers or background entry points
        // Use startForegroundService() when not called from visible Activity
        if (!PermissionHelper.hasBluetoothPermissions(this)) {
            Log.w("MainActivity", "Bluetooth permissions missing; skipping service start")
            return
        }
        if (!PermissionHelper.canStartForegroundService(this)) {
            Log.w("MainActivity", "Notifications disabled; skipping service start")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.w("MainActivity", "POST_NOTIFICATIONS not granted; skipping service start")
                return
            }
        }
        val intent = Intent(this, BluetoothService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
                Log.d("MainActivity", "Started service with startForegroundService() (background entry point)")
            } else {
                startService(intent)
            }
        } catch (e: IllegalStateException) {
            Log.e("MainActivity", "Failed to start service: ${e.message}")
        }
    }

    private fun stopBluetoothService() {
        stopService(Intent(this, BluetoothService::class.java))
    }

    override fun onPause() {
        Log.d("MainActivity","onPause")
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        hasWindowFocusNow = hasFocus
        Log.d("MainActivity","onWindowFocusChanged hasFocus=$hasFocus")
    }
}
