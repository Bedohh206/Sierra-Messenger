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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * BLE Advertiser for making device discoverable
 */
class BleAdvertiser(private val context: Context) {
    
    private val tag = "BleAdvertiser"
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var advertiser: BluetoothLeAdvertiser? = null
    
    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising
    
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.d(tag, "BLE advertising started successfully")
            _isAdvertising.value = true
        }
        
        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e(tag, "BLE advertising failed with error: $errorCode")
            _isAdvertising.value = false
        }
    }
    
    @SuppressLint("MissingPermission")
    fun startAdvertising(): Boolean {
        if (!hasRequiredPermissions()) {
            Log.e(tag, "Missing Bluetooth permissions")
            return false
        }
        
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(tag, "Bluetooth is not available or not enabled")
            return false
        }
        
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.e(tag, "Device does not support BLE advertising")
            return false
        }
        
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()
        
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(Protocol.SERVICE_UUID))
            .build()
        
        try {
            advertiser?.startAdvertising(settings, data, advertiseCallback)
            return true
        } catch (e: Exception) {
            Log.e(tag, "Failed to start advertising", e)
            return false
        }
    }
    
    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        if (!hasRequiredPermissions()) {
            return
        }
        
        try {
            advertiser?.stopAdvertising(advertiseCallback)
            _isAdvertising.value = false
            Log.d(tag, "BLE advertising stopped")
        } catch (e: Exception) {
            Log.e(tag, "Failed to stop advertising", e)
        }
    }
    
    private fun hasRequiredPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_ADVERTISE
        ) == PackageManager.PERMISSION_GRANTED
    }
}
