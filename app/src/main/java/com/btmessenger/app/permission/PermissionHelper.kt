package com.btmessenger.app.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object PermissionHelper {

    fun hasBluetoothPermissions(context: Context): Boolean {
        Log.e("PERM_CHECK", "hasBluetoothPermissions called, SDK=${Build.VERSION.SDK_INT}")
        
        // Android 12+ requires SCAN + CONNECT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val required = listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
            val result = required.all {
                ContextCompat.checkSelfPermission(context, it) ==
                    PackageManager.PERMISSION_GRANTED
            }
            Log.e("PERM_CHECK", "Android 12+: result=$result")
            return result
        }
        
        // Pre-Android 12: only need BLUETOOTH + BLUETOOTH_ADMIN (location is nice-to-have for scanning)
        val btResult = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH)
        val btAdminResult = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN)
        val bluetoothGranted = (btResult == PackageManager.PERMISSION_GRANTED) && 
                               (btAdminResult == PackageManager.PERMISSION_GRANTED)
        
        Log.e("PERM_CHECK", "Pre-12: BT=$btResult BTAdmin=$btAdminResult bluetoothGranted=$bluetoothGranted")
        return bluetoothGranted
    }

    fun requestPre12LocationPermission(activity: Activity, requestCode: Int = 1001): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return true

        val fineGranted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val granted = fineGranted || coarseGranted

        if (!granted) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                requestCode
            )
        }

        return granted
    }

    fun canStartForegroundService(context: Context): Boolean {
        return true
    }
}
