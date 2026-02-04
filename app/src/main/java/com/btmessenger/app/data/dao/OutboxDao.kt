package com.btmessenger.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.btmessenger.app.data.entities.OutboxMessage

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: OutboxMessage)

    @Query("SELECT * FROM outbox WHERE status != 'delivered' AND (nextAttemptAt IS NULL OR nextAttemptAt <= :now)")
    suspend fun getDue(now: Long): List<OutboxMessage>

    @Query("SELECT * FROM outbox WHERE msgId = :msgId LIMIT 1")
    suspend fun getById(msgId: String): OutboxMessage?

    @Query("UPDATE outbox SET status = :status, attempts = :attempts, lastAttemptAt = :lastAttemptAt, nextAttemptAt = :nextAttemptAt WHERE msgId = :msgId")
    suspend fun updateStatus(
        msgId: String,
        status: String,
        attempts: Int,
        lastAttemptAt: Long?,
        nextAttemptAt: Long?
    )

    @Query("DELETE FROM outbox WHERE msgId = :msgId")
    suspend fun deleteById(msgId: String)
}
