package com.btmessenger.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbox")
data class OutboxMessage(
    @PrimaryKey
    val msgId: String,
    val toId: String,
    val payload: String,
    val transportHint: String,
    val status: String,
    val attempts: Int,
    val lastAttemptAt: Long?,
    val nextAttemptAt: Long?,
    val createdAt: Long
)
