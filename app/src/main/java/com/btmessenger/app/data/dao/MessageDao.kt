package com.btmessenger.app.data.dao

import androidx.room.*
import com.btmessenger.app.data.entities.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE (fromId = :peerId OR toId = :peerId) ORDER BY timestamp ASC")
    fun getMessagesForPeer(peerId: String): Flow<List<Message>>
    
    @Query("SELECT * FROM messages WHERE groupId = :groupId ORDER BY timestamp ASC")
    fun getMessagesForGroup(groupId: String): Flow<List<Message>>
    
    @Query("SELECT * FROM messages WHERE msgId = :messageId")
    suspend fun getMessageById(messageId: String): Message?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<Message>)
    
    @Update
    suspend fun updateMessage(message: Message)
    
    @Delete
    suspend fun deleteMessage(message: Message)
    
    @Query("DELETE FROM messages WHERE msgId = :messageId")
    suspend fun deleteMessageById(messageId: String)
    
    @Query("DELETE FROM messages WHERE fromId = :peerId OR toId = :peerId")
    suspend fun deleteMessagesForPeer(peerId: String)
    
    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
    
    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMessage(): Message?
}
