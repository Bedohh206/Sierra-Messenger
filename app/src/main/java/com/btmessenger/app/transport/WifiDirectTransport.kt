package com.btmessenger.app.transport

import android.content.Context
import android.util.Log
import java.io.File

class WifiDirectTransport(private val context: Context) {
    private val tag = "WifiDirectTransport"

    fun isAvailable(): Boolean = false

    suspend fun sendMessage(payload: String): Boolean {
        Log.w(tag, "Wi-Fi Direct transport not implemented yet")
        return false
    }

    suspend fun sendFile(file: File, toId: String): Boolean {
        Log.w(tag, "Wi-Fi Direct file transfer not implemented yet")
        return false
    }
}
