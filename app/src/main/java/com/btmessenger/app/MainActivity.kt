package com.btmessenger.app

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.util.Log
import android.Manifest

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
import com.btmessenger.app.bluetooth.BleGattServerManager
import com.btmessenger.app.ui.AppRoot

class MainActivity : ComponentActivity() {

    private lateinit var gattServerManager: BleGattServerManager
    private lateinit var bleAdvertiser: com.btmessenger.app.bluetooth.BleAdvertiser

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms.entries.all { it.value == true }
        if (granted) {
            Log.d("MainActivity", "permissions granted")
            // Permissions granted — lifecycle onStart will start GATT/advertising.
        } else {
            Log.w("MainActivity", "permissions denied: $perms")
        }
    }

    private fun ensurePermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += listOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            perms += listOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        requestPermissions.launch(perms.toTypedArray())
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

        // initialize BLE manager and request required permissions
        gattServerManager = BleGattServerManager(applicationContext)
        bleAdvertiser = com.btmessenger.app.bluetooth.BleAdvertiser(applicationContext)
        ensurePermissions()

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

            val gatt = gattServerManager

            DisposableEffect(Unit) {
                    onDispose {
                        Log.d("GattServer", "MainActivity -> stop()")
                        // Ensure GATT is stopped when the composition goes away.
                        lifecycleScope.launch(Dispatchers.IO) { gatt.stop() }
                    }
            }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
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

    override fun onStart() {
        super.onStart()
        Log.d("MainActivity","onStart")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                gattServerManager.start()
            } catch (t: Throwable) {
                Log.w("MainActivity", "gatt start failed", t)
            }

            try {
                bleAdvertiser.startAdvertising()
            } catch (t: Throwable) {
                Log.w("MainActivity", "advertiser start failed", t)
            }
        }
    }

    override fun onStop() {
        Log.d("MainActivity","onStop")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                bleAdvertiser.stopAdvertising()
            } catch (t: Throwable) {
                Log.w("MainActivity", "advertiser stop failed", t)
            }

            try {
                gattServerManager.stop()
            } catch (t: Throwable) {
                Log.w("MainActivity", "gatt stop failed", t)
            }
        }
        super.onStop()
    }

    override fun onPause() {
        Log.d("MainActivity","onPause")
        super.onPause()
        try {
            Handler(Looper.getMainLooper()).removeCallbacksAndMessages(null)
        } catch (_: Throwable) {
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity","onResume")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.d("MainActivity","onWindowFocusChanged hasFocus=$hasFocus")
    }
}
