package com.btmessenger.app.transport

class NoopMeshSdkAdapter : MeshSdkAdapter {
    override val isAvailable: Boolean = false

    override suspend fun send(toId: String, payload: String): Boolean = false
}
