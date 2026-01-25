package com.btmessenger.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "groups")
data class Group(
    @PrimaryKey
    val groupId: String,
    val name: String,
    val hostId: String, // peer id of host/relay
    val createdAt: Long,
    val memberCount: Int = 1
)
