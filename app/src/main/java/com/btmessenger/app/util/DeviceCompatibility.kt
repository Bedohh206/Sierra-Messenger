package com.btmessenger.app.util

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * Provides device capability detection and compatibility information
 */
object DeviceCompatibility {
    private const val TAG = "DeviceCompatibility"

    data class Capabilities(
        val hasBluetoothClassic: Boolean,
        val hasBle: Boolean,
        val hasBleAdvertising: Boolean,
        val hasCamera: Boolean,
        val hasMicrophone: Boolean,
        val androidVersion: Int,
        val androidVersionName: String,
        val supportsModernBlePermissions: Boolean,
        val recommendedTransport: String,
        val limitationsDescription: String?
    )

    /**
     * Detect all device capabilities
     */
    fun detectCapabilities(context: Context): Capabilities {
        val pm = context.packageManager
        val adapter = BluetoothAdapter.getDefaultAdapter()

        val hasBluetoothClassic = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        val hasBle = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val hasBleAdvertising = adapter?.isMultipleAdvertisementSupported == true
        val hasCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        val hasMicrophone = pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        
        val androidVersion = Build.VERSION.SDK_INT
        val androidVersionName = getAndroidVersionName(androidVersion)
        val supportsModernBlePermissions = androidVersion >= Build.VERSION_CODES.S

        val recommendedTransport = when {
            hasBle && hasBleAdvertising -> "BLE (Optimal)"
            hasBle -> "BLE + Classic Hybrid"
            hasBluetoothClassic -> "Classic Bluetooth"
            else -> "None Available"
        }

        val limitations = buildLimitationsDescription(
            hasBluetoothClassic,
            hasBle,
            hasBleAdvertising,
            androidVersion
        )

        return Capabilities(
            hasBluetoothClassic = hasBluetoothClassic,
            hasBle = hasBle,
            hasBleAdvertising = hasBleAdvertising,
            hasCamera = hasCamera,
            hasMicrophone = hasMicrophone,
            androidVersion = androidVersion,
            androidVersionName = androidVersionName,
            supportsModernBlePermissions = supportsModernBlePermissions,
            recommendedTransport = recommendedTransport,
            limitationsDescription = limitations
        )
    }

    /**
     * Get human-readable Android version name
     */
    private fun getAndroidVersionName(sdkInt: Int): String {
        return when (sdkInt) {
            Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> "Android 14 (Upside Down Cake)"
            34 -> "Android 14"
            Build.VERSION_CODES.TIRAMISU -> "Android 13 (Tiramisu)"
            Build.VERSION_CODES.S_V2 -> "Android 12L"
            Build.VERSION_CODES.S -> "Android 12"
            Build.VERSION_CODES.R -> "Android 11"
            Build.VERSION_CODES.Q -> "Android 10"
            Build.VERSION_CODES.P -> "Android 9 (Pie)"
            Build.VERSION_CODES.O_MR1 -> "Android 8.1 (Oreo)"
            Build.VERSION_CODES.O -> "Android 8.0 (Oreo)"
            else -> "Android API $sdkInt"
        }
    }

    /**
     * Build description of device limitations, if any
     */
    private fun buildLimitationsDescription(
        hasClassic: Boolean,
        hasBle: Boolean,
        hasAdvertising: Boolean,
        apiLevel: Int
    ): String? {
        val limitations = mutableListOf<String>()

        if (!hasClassic) {
            limitations.add("No Bluetooth Classic support")
        }

        if (!hasBle) {
            limitations.add("No BLE support - Classic only")
        } else if (!hasAdvertising) {
            limitations.add("BLE advertising not available - device cannot be discovered by others via BLE (use Classic pairing)")
        }

        if (apiLevel < Build.VERSION_CODES.S) {
            limitations.add("Location permission required for BLE scanning on Android ${apiLevel}")
        }

        return if (limitations.isEmpty()) null else limitations.joinToString("; ")
    }

    /**
     * Get user-friendly capability summary
     */
    fun getCapabilitySummary(context: Context): String {
        val caps = detectCapabilities(context)
        
        return buildString {
            appendLine("📱 Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("🤖 ${caps.androidVersionName}")
            appendLine()
            appendLine("✅ Bluetooth Classic: ${if (caps.hasBluetoothClassic) "Yes" else "No"}")
            appendLine("✅ BLE Support: ${if (caps.hasBle) "Yes" else "No"}")
            appendLine("✅ BLE Advertising: ${if (caps.hasBleAdvertising) "Yes" else "No (can't be discovered)"}")
            appendLine()
            appendLine("🔧 Recommended: ${caps.recommendedTransport}")
            
            caps.limitationsDescription?.let {
                appendLine()
                appendLine("⚠️ Limitations: $it")
            }
        }
    }

    /**
     * Log device capabilities for debugging
     */
    fun logCapabilities(context: Context) {
        val caps = detectCapabilities(context)
        Log.i(TAG, "=== Device Compatibility Report ===")
        Log.i(TAG, "Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        Log.i(TAG, "Android: ${caps.androidVersionName} (API ${caps.androidVersion})")
        Log.i(TAG, "Bluetooth Classic: ${caps.hasBluetoothClassic}")
        Log.i(TAG, "BLE: ${caps.hasBle}")
        Log.i(TAG, "BLE Advertising: ${caps.hasBleAdvertising}")
        Log.i(TAG, "Camera: ${caps.hasCamera}")
        Log.i(TAG, "Microphone: ${caps.hasMicrophone}")
        Log.i(TAG, "Modern BLE Permissions: ${caps.supportsModernBlePermissions}")
        Log.i(TAG, "Recommended Transport: ${caps.recommendedTransport}")
        caps.limitationsDescription?.let {
            Log.w(TAG, "Limitations: $it")
        }
        Log.i(TAG, "===================================")
    }

    /**
     * Check if device meets minimum requirements
     */
    fun meetsMinimumRequirements(context: Context): Boolean {
        val caps = detectCapabilities(context)
        return caps.hasBluetoothClassic || caps.hasBle
    }

    /**
     * Get compatibility mode description for UI
     */
    fun getCompatibilityMode(context: Context): String {
        val caps = detectCapabilities(context)
        
        return when {
            caps.hasBle && caps.hasBleAdvertising -> "Full BLE Mode"
            caps.hasBle && !caps.hasBleAdvertising -> "BLE Client Mode"
            caps.hasBluetoothClassic -> "Classic Mode"
            else -> "Limited"
        }
    }

    /**
     * Should show compatibility warning to user?
     */
    fun shouldShowCompatibilityWarning(context: Context): Boolean {
        val caps = detectCapabilities(context)
        return !caps.hasBleAdvertising || (!caps.hasBluetoothClassic && !caps.hasBle)
    }

    /**
     * Get actionable recommendation for user
     */
    fun getRecommendation(context: Context): String? {
        val caps = detectCapabilities(context)
        
        return when {
            !caps.hasBluetoothClassic && !caps.hasBle -> 
                "Device lacks Bluetooth support. App functionality severely limited."
            
            !caps.hasBleAdvertising && caps.androidVersion < Build.VERSION_CODES.S ->
                "Your device cannot advertise via BLE. Pair with others manually in Bluetooth settings, then connect via Classic Bluetooth."
            
            !caps.hasBleAdvertising ->
                "BLE advertising unavailable. Make device discoverable via system Bluetooth settings for others to find you."
            
            caps.androidVersion < Build.VERSION_CODES.S ->
                "Grant Location permission to enable BLE scanning on Android ${caps.androidVersion}."
            
            else -> null // No warnings needed
        }
    }
}
