package com.btmessenger.app.util

import android.content.Context
import android.bluetooth.BluetoothManager
import java.util.UUID

object DeviceId {
    private const val PREFS_NAME = "btm_device_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    private const val MASKED_BT_ADDRESS = "02:00:00:00:00:00"

    fun getOrCreate(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing

        val newId = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
        return newId
    }

    fun getLocalBtAddress(context: Context): String? {
        return try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            manager.adapter?.address
        } catch (_: SecurityException) {
            null
        }
    }

    fun isMaskedAddress(address: String?): Boolean {
        return address.isNullOrBlank() || address == MASKED_BT_ADDRESS
    }
}
