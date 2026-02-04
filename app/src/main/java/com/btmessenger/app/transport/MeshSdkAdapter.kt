package com.btmessenger.app.transport

interface MeshSdkAdapter {
    val isAvailable: Boolean
    suspend fun send(toId: String, payload: String): Boolean
}
