package com.btmessenger.app.bluetooth

import com.google.gson.Gson
import java.util.UUID

/**
 * Message Protocol for Bluetooth communication
 */
object Protocol {
    // Service UUIDs
    val SERVICE_UUID: UUID = UUID.fromString("00001234-0000-1000-8000-00805f9b34fb")
    val MESSAGE_CHARACTERISTIC_UUID: UUID = UUID.fromString("00001235-0000-1000-8000-00805f9b34fb")
    val TRANSFER_CHARACTERISTIC_UUID: UUID = UUID.fromString("00001236-0000-1000-8000-00805f9b34fb")
    
    // Classic Bluetooth UUID
    val CLASSIC_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    
    // Message types
    const val TYPE_TEXT = "TEXT"
    const val TYPE_IMAGE_OFFER = "IMAGE_OFFER"
    const val TYPE_IMAGE_ACCEPT = "IMAGE_ACCEPT"
    const val TYPE_IMAGE_REJECT = "IMAGE_REJECT"
    const val TYPE_IMAGE_CHUNK = "IMAGE_CHUNK"
    const val TYPE_IMAGE_COMPLETE = "IMAGE_COMPLETE"
    const val TYPE_AUDIO_OFFER = "AUDIO_OFFER"
    const val TYPE_AUDIO_ACCEPT = "AUDIO_ACCEPT"
    const val TYPE_AUDIO_REJECT = "AUDIO_REJECT"
    const val TYPE_AUDIO_CHUNK = "AUDIO_CHUNK"
    const val TYPE_AUDIO_COMPLETE = "AUDIO_COMPLETE"
    const val TYPE_PING = "PING"
    const val TYPE_PONG = "PONG"
    const val TYPE_REGISTER = "REGISTER"
    const val TYPE_GROUP_JOIN = "GROUP_JOIN"
    const val TYPE_GROUP_LEAVE = "GROUP_LEAVE"
    const val TYPE_GROUP_MESSAGE = "GROUP_MESSAGE"
    
    // Chunk size for file transfer (16KB)
    const val CHUNK_SIZE = 16384
    
    private val gson = Gson()
    
    /**
     * Base message structure
     */
    data class Message(
        val v: Int = 1,
        val type: String,
        val msgId: String,
        val from: String,
        val to: String? = null,
        val groupId: String? = null,
        val ts: Long,
        val body: String? = null,
        val fileName: String? = null,
        val fileSize: Long? = null,
        val mime: String? = null,
        val duration: Int? = null, // Audio duration in seconds
        val chunkIndex: Int? = null,
        val totalChunks: Int? = null,
        val data: String? = null // Base64 encoded chunk data
    )
    
    /**
     * Create a text message
     */
    fun createTextMessage(msgId: String, from: String, to: String, body: String): String {
        val message = Message(
            type = TYPE_TEXT,
            msgId = msgId,
            from = from,
            to = to,
            ts = System.currentTimeMillis(),
            body = body
        )
        return gson.toJson(message)
    }

    /**
     * Create a register message to announce a peer id / display name to the host
     */
    fun createRegisterMessage(msgId: String, from: String, displayName: String): String {
        val message = Message(
            type = TYPE_REGISTER,
            msgId = msgId,
            from = from,
            ts = System.currentTimeMillis(),
            body = displayName
        )
        return gson.toJson(message)
    }

    /**
     * Create a group join message
     */
    fun createGroupJoinMessage(msgId: String, from: String, groupId: String): String {
        val message = Message(
            type = TYPE_GROUP_JOIN,
            msgId = msgId,
            from = from,
            groupId = groupId,
            ts = System.currentTimeMillis()
        )
        return gson.toJson(message)
    }

    /**
     * Create a group leave message
     */
    fun createGroupLeaveMessage(msgId: String, from: String, groupId: String): String {
        val message = Message(
            type = TYPE_GROUP_LEAVE,
            msgId = msgId,
            from = from,
            groupId = groupId,
            ts = System.currentTimeMillis()
        )
        return gson.toJson(message)
    }

    /**
     * Create a group text message
     */
    fun createGroupTextMessage(msgId: String, from: String, groupId: String, body: String): String {
        val message = Message(
            type = TYPE_GROUP_MESSAGE,
            msgId = msgId,
            from = from,
            groupId = groupId,
            ts = System.currentTimeMillis(),
            body = body
        )
        return gson.toJson(message)
    }
    
    /**
     * Create an image offer message
     */
    fun createImageOffer(msgId: String, from: String, to: String, fileName: String, fileSize: Long, mime: String): String {
        val message = Message(
            type = TYPE_IMAGE_OFFER,
            msgId = msgId,
            from = from,
            to = to,
            ts = System.currentTimeMillis(),
            fileName = fileName,
            fileSize = fileSize,
            mime = mime
        )
        return gson.toJson(message)
    }
    
    /**
     * Create an image accept message
     */
    fun createImageAccept(msgId: String, from: String, to: String, originalMsgId: String): String {
        val message = Message(
            type = TYPE_IMAGE_ACCEPT,
            msgId = msgId,
            from = from,
            to = to,
            ts = System.currentTimeMillis(),
            body = originalMsgId
        )
        return gson.toJson(message)
    }
    
    /**
     * Create an image reject message
     */
    fun createImageReject(msgId: String, from: String, to: String, originalMsgId: String): String {
        val message = Message(
            type = TYPE_IMAGE_REJECT,
            msgId = msgId,
            from = from,
            to = to,
            ts = System.currentTimeMillis(),
            body = originalMsgId
        )
        return gson.toJson(message)
    }
    
    /**
     * Create an image chunk message
     */
    fun createImageChunk(msgId: String, from: String, to: String, chunkIndex: Int, totalChunks: Int, data: String): String {
        val message = Message(
            type = TYPE_IMAGE_CHUNK,
            msgId = msgId,
            from = from,
            to = to,
            ts = System.currentTimeMillis(),
            chunkIndex = chunkIndex,
            totalChunks = totalChunks,
            data = data
        )
        return gson.toJson(message)
    }
    
    /**
     * Create an image complete message
     */
    fun createImageComplete(msgId: String, from: String, to: String, originalMsgId: String): String {
        val message = Message(
            type = TYPE_IMAGE_COMPLETE,
            msgId = msgId,
            from = from,
            to = to,
            ts = System.currentTimeMillis(),
            body = originalMsgId
        )
        return gson.toJson(message)
    }
    
    /**
     * Create an audio offer message
     */
    fun createAudioOffer(msgId: String, from: String, to: String, fileName: String, fileSize: Long, duration: Int): String {
        val message = Message(
            type = TYPE_AUDIO_OFFER,
            msgId = msgId,
            from = from,
            to = to,
            ts = System.currentTimeMillis(),
            fileName = fileName,
            fileSize = fileSize,
            mime = "audio/3gpp",
            duration = duration
        )
        return gson.toJson(message)
    }
    
    /**
     * Create an audio accept message
     */
    fun createAudioAccept(msgId: String, from: String, to: String, originalMsgId: String): String {
        val message = Message(
            type = TYPE_AUDIO_ACCEPT,
            msgId = msgId,
            from = from,
            to = to,
            ts = System.currentTimeMillis(),
            body = originalMsgId
        )
        return gson.toJson(message)
    }
    
    /**
     * Create an audio reject message
     */
    fun createAudioReject(msgId: String, from: String, to: String, originalMsgId: String): String {
        val message = Message(
            type = TYPE_AUDIO_REJECT,
            msgId = msgId,
            from = from,
            to = to,
            ts = System.currentTimeMillis(),
            body = originalMsgId
        )
        return gson.toJson(message)
    }
    
    /**
     * Create an audio chunk message
     */
    fun createAudioChunk(msgId: String, from: String, to: String, chunkIndex: Int, totalChunks: Int, data: String): String {
        val message = Message(
            type = TYPE_AUDIO_CHUNK,
            msgId = msgId,
            from = from,
            to = to,
            ts = System.currentTimeMillis(),
            chunkIndex = chunkIndex,
            totalChunks = totalChunks,
            data = data
        )
        return gson.toJson(message)
    }
    
    /**
     * Create an audio complete message
     */
    fun createAudioComplete(msgId: String, from: String, to: String, originalMsgId: String): String {
        val message = Message(
            type = TYPE_AUDIO_COMPLETE,
            msgId = msgId,
            from = from,
            to = to,
            ts = System.currentTimeMillis(),
            body = originalMsgId
        )
        return gson.toJson(message)
    }
    
    /**
     * Parse a JSON message
     */
    fun parseMessage(json: String): Message? {
        return try {
            gson.fromJson(json, Message::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
