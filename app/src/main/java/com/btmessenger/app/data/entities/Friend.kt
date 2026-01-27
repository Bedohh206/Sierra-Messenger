package com.btmessenger.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friends")
data class Friend(
    @PrimaryKey
    val id: String,
    val name: String,
    val address: String,
    val addedAt: Long = System.currentTimeMillis()
)
