package com.btmessenger.app.transport

import com.btmessenger.app.bluetooth.ClassicClient
import com.btmessenger.app.bluetooth.GattClient

class BleSmallMessageTransport(
    private val gattClient: GattClient,
    private val classicClient: ClassicClient
) {
    suspend fun send(payload: String): Boolean {
        val bleSent = gattClient.sendMessage(payload)
        if (bleSent) return true
        return classicClient.sendMessage(payload)
    }
}
