package com.btmessenger.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey
    val msgId: String,
    val type: String, // TEXT, IMAGE_OFFER, etc.
    val fromId: String,
    val toId: String,
    val groupId: String? = null,
    val timestamp: Long,
    val body: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val filePath: String? = null,
    val mime: String? = null,
    val duration: Int? = null, // Audio duration in seconds
    val status: String = "sent", // sent, delivered, read, failed
    val isIncoming: Boolean = false
)
