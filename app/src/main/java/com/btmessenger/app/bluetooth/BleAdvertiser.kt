package com.btmessenger.app.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class AdvertiseFailure(
    val errorCode: Int,
    val errorName: String,
    val minimalAttempted: Boolean
)

/**
 * BLE Advertiser for making device discoverable
 */
class BleAdvertiser(private val context: Context) {
    
    private val tag = "BleAdvertiser"
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var advertiser: BluetoothLeAdvertiser? = null
    private var lastStartMinimal = false
    private var fallbackAttempted = false
    
    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising
    private val _failureEvents = MutableSharedFlow<AdvertiseFailure>(extraBufferCapacity = 1)
    val failureEvents: SharedFlow<AdvertiseFailure> = _failureEvents
    
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.d("ADV", "Advertising SUCCESS: $settingsInEffect")
            Log.d(tag, "BluetoothLeAdvertiser onStartSuccess")
            Log.d(tag, "BLE advertising started successfully")
            fallbackAttempted = false
            _isAdvertising.value = true
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e("ADV", "Advertising FAILED: error=$errorCode")
            Log.e(tag, "BluetoothLeAdvertiser onStartFailure error=$errorCode")
            val name = errorName(errorCode)
            Log.e(tag, "BLE advertising failed with error: $name")
            if ((errorCode == AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS ||
                        errorCode == AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE) &&
                !lastStartMinimal && !fallbackAttempted
            ) {
                fallbackAttempted = true
                Log.w(tag, "Advertise failed with $name -> retrying minimal payload")
                stopAdvertising()
                startAdvertisingInternal(minimal = true)
                return
            }
            _failureEvents.tryEmit(AdvertiseFailure(errorCode, name, lastStartMinimal))
            _isAdvertising.value = false
        }
    }
    
    @SuppressLint("MissingPermission")
    fun startAdvertising(): Boolean {
        return startAdvertisingInternal(minimal = false)
    }

    @SuppressLint("MissingPermission")
    fun startAdvertisingMinimal(): Boolean {
        return startAdvertisingInternal(minimal = true)
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertisingInternal(minimal: Boolean): Boolean {
        Log.d(tag, "startAdvertisingInternal minimal=$minimal adapter=${bluetoothAdapter != null}")
        lastStartMinimal = minimal
        if (!minimal) {
            fallbackAttempted = false
        }
        if (!hasRequiredPermissions()) {
            Log.e(tag, "Missing Bluetooth advertise permission")
            _failureEvents.tryEmit(
                AdvertiseFailure(
                    AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED,
                    "MISSING_PERMISSION",
                    minimal
                )
            )
            _isAdvertising.value = false
            return false
        }

        if (bluetoothAdapter?.isMultipleAdvertisementSupported != true) {
            Log.e(tag, "BLE advertising not supported on this device")
            _failureEvents.tryEmit(
                AdvertiseFailure(
                    AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED,
                    "FEATURE_UNSUPPORTED",
                    minimal
                )
            )
            _isAdvertising.value = false
            return false
        }

        val advertiserLocal = bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiserLocal == null) {
            Log.e(tag, "No BLE advertiser available")
            _failureEvents.tryEmit(
                AdvertiseFailure(
                    AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED,
                    "NO_ADVERTISER",
                    minimal
                )
            )
            _isAdvertising.value = false
            return false
        }

        advertiser = advertiserLocal

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(0) // 0 = no timeout
            .build()

        val serviceUuid = ParcelUuid(Protocol.SERVICE_UUID)

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(serviceUuid)
            .build()

        val scanResponse = if (minimal) {
            null
        } else {
            AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .build()
        }

        return try {
            Log.d(tag, "startAdvertising called minimal=$minimal")
            advertiserLocal.startAdvertising(settings, data, scanResponse, advertiseCallback)
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to start advertising", e)
            false
        }
    }
    
    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        val advertiserLocal = advertiser ?: bluetoothAdapter?.bluetoothLeAdvertiser
        advertiserLocal?.stopAdvertising(advertiseCallback)
        advertiser = null
        _isAdvertising.value = false
        Log.d(tag, "stopAdvertising called")
    }
    
    private fun hasRequiredPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_ADVERTISE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun errorName(errorCode: Int): String {
        return when (errorCode) {
            AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE ($errorCode)"
            AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS ($errorCode)"
            AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED ($errorCode)"
            AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR ($errorCode)"
            AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED ($errorCode)"
            else -> "UNKNOWN ($errorCode)"
        }
    }
}
