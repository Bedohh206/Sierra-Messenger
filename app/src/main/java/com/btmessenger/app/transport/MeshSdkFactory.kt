package com.btmessenger.app.transport

import android.content.Context
import com.btmessenger.app.BuildConfig

object MeshSdkFactory {
    fun create(context: Context): MeshSdkAdapter {
        val adapter = BridgefyMeshSdkAdapter(context, BuildConfig.BRIDGEFY_API_KEY)
        return if (adapter.isAvailable) adapter else NoopMeshSdkAdapter()
    }
}
