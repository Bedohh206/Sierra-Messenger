package com.btmessenger.app.transport

import android.content.Context
import android.util.Log

class BridgefyMeshSdkAdapter(
    private val context: Context,
    private val apiKey: String
) : MeshSdkAdapter {
    private val tag = "BridgefyMeshSdk"

    override val isAvailable: Boolean
        get() = apiKey.isNotBlank() && isBridgefyOnClasspath()

    override suspend fun send(toId: String, payload: String): Boolean {
        if (!isAvailable) return false
        // TODO: Wire Bridgefy SDK send once dependency/version is confirmed.
        Log.w(tag, "Bridgefy SDK not wired yet; add SDK + API key to enable mesh send")
        return false
    }

    private fun isBridgefyOnClasspath(): Boolean {
        return try {
            Class.forName("me.bridgefy.sdk.client.Bridgefy")
            true
        } catch (_: Throwable) {
            false
        }
    }
}
