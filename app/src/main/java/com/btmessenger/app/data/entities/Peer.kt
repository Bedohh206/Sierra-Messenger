package com.btmessenger.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "peers")
data class Peer(
    @PrimaryKey
    val id: String,
    val name: String,
    val address: String,
    val type: String, // "BLE" or "CLASSIC"
    val lastSeen: Long,
    val rssi: Int? = null
)
