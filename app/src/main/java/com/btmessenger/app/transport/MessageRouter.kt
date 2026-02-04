package com.btmessenger.app.transport

import android.util.Log
import com.btmessenger.app.data.entities.OutboxMessage
import com.btmessenger.app.data.repository.MessengerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MessageRouter(
    private val repository: MessengerRepository,
    private val bleTransport: BleSmallMessageTransport,
    private val wifiDirectTransport: WifiDirectTransport,
    private val meshAdapter: MeshSdkAdapter
) {
    private val tag = "MessageRouter"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var loopJob: Job? = null

    private val ackTimeoutMs = 15_000L
    private val retryDelayMs = 10_000L

    fun start() {
        if (loopJob != null) return
        loopJob = scope.launch {
            while (isActive) {
                try {
                    drainOutbox()
                } catch (e: Exception) {
                    Log.e(tag, "Outbox drain failed", e)
                }
                delay(2_000L)
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }

    suspend fun enqueueWithAck(
        msgId: String,
        toId: String,
        payload: String,
        hint: TransportHint
    ): Boolean {
        val now = System.currentTimeMillis()
        repository.upsertOutbox(
            OutboxMessage(
                msgId = msgId,
                toId = toId,
                payload = payload,
                transportHint = hint.name,
                status = "pending",
                attempts = 0,
                lastAttemptAt = null,
                nextAttemptAt = now,
                createdAt = now
            )
        )
        return attemptSend(msgId, toId, payload, hint, 0)
    }

    suspend fun sendAck(toId: String, payload: String) {
        val sent = bleTransport.send(payload)
        if (!sent && meshAdapter.isAvailable) {
            meshAdapter.send(toId, payload)
        }
    }

    suspend fun markDelivered(msgId: String) {
        val existing = repository.getOutboxById(msgId) ?: return
        repository.updateOutboxStatus(
            msgId = msgId,
            status = "delivered",
            attempts = existing.attempts,
            lastAttemptAt = existing.lastAttemptAt,
            nextAttemptAt = null
        )
    }

    suspend fun forwardViaMesh(toId: String, payload: String): Boolean {
        if (!meshAdapter.isAvailable) return false
        return meshAdapter.send(toId, payload)
    }

    private suspend fun drainOutbox() {
        val now = System.currentTimeMillis()
        val due = repository.getOutboxDue(now)
        for (entry in due) {
            val hint = TransportHint.valueOf(entry.transportHint)
            if (entry.status == "awaiting_ack" && entry.nextAttemptAt != null && entry.nextAttemptAt > now) {
                continue
            }
            attemptSend(entry.msgId, entry.toId, entry.payload, hint, entry.attempts)
        }
    }

    private suspend fun attemptSend(
        msgId: String,
        toId: String,
        payload: String,
        hint: TransportHint,
        attempts: Int
    ): Boolean {
        val now = System.currentTimeMillis()
        val sent = when (hint) {
            TransportHint.BLE -> bleTransport.send(payload)
            TransportHint.WIFI_DIRECT -> wifiDirectTransport.sendMessage(payload)
            TransportHint.MESH -> if (meshAdapter.isAvailable) meshAdapter.send(toId, payload) else false
        }

        val nextStatus = if (sent) "awaiting_ack" else "pending"
        val nextAttemptAt = if (sent) now + ackTimeoutMs else now + retryDelayMs
        repository.updateOutboxStatus(
            msgId = msgId,
            status = nextStatus,
            attempts = attempts + 1,
            lastAttemptAt = now,
            nextAttemptAt = nextAttemptAt
        )
        return sent
    }
}
