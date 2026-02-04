package com.btmessenger.app.data.repository

import com.btmessenger.app.data.dao.MessageDao
import com.btmessenger.app.data.dao.PeerDao
import com.btmessenger.app.data.dao.GroupDao
import com.btmessenger.app.data.dao.OutboxDao
import com.btmessenger.app.data.entities.Message
import com.btmessenger.app.data.entities.Peer
import com.btmessenger.app.data.entities.OutboxMessage
import kotlinx.coroutines.flow.Flow

class MessengerRepository(
    private val peerDao: PeerDao,
    private val messageDao: MessageDao,
    private val groupDao: GroupDao,
    private val friendDao: com.btmessenger.app.data.dao.FriendDao,
    private val outboxDao: OutboxDao
) {
    // Peer operations
    fun getAllPeers(): Flow<List<Peer>> = peerDao.getAllPeers()
    
    suspend fun getPeerById(peerId: String): Peer? = peerDao.getPeerById(peerId)
    
    suspend fun insertPeer(peer: Peer) = peerDao.insertPeer(peer)
    
    suspend fun insertPeers(peers: List<Peer>) = peerDao.insertPeers(peers)
    
    suspend fun updatePeer(peer: Peer) = peerDao.updatePeer(peer)
    
    suspend fun deletePeer(peer: Peer) = peerDao.deletePeer(peer)
    
    suspend fun deletePeerById(peerId: String) = peerDao.deletePeerById(peerId)
    
    // Message operations
    fun getMessagesForPeer(peerId: String): Flow<List<Message>> = 
        messageDao.getMessagesForPeer(peerId)
    
    suspend fun getMessageById(messageId: String): Message? = 
        messageDao.getMessageById(messageId)
    
    suspend fun insertMessage(message: Message) = messageDao.insertMessage(message)
    
    suspend fun insertMessages(messages: List<Message>) = messageDao.insertMessages(messages)
    
    suspend fun updateMessage(message: Message) = messageDao.updateMessage(message)

    suspend fun updateMessageStatus(messageId: String, status: String) =
        messageDao.updateMessageStatus(messageId, status)
    
    suspend fun deleteMessage(message: Message) = messageDao.deleteMessage(message)
    
    suspend fun deleteMessagesForPeer(peerId: String) = 
        messageDao.deleteMessagesForPeer(peerId)

    // Group operations
    fun getAllGroups() = groupDao.getAllGroups()

    suspend fun getGroupById(groupId: String) = groupDao.getGroupById(groupId)

    suspend fun insertGroup(group: com.btmessenger.app.data.entities.Group) = groupDao.insertGroup(group)

    suspend fun deleteGroup(group: com.btmessenger.app.data.entities.Group) = groupDao.deleteGroup(group)

    fun getMessagesForGroup(groupId: String) = messageDao.getMessagesForGroup(groupId)

    // Friend operations
    fun getAllFriends() = friendDao.getAllFriends()

    suspend fun getFriendById(friendId: String) = friendDao.getFriendById(friendId)

    suspend fun getFriendByAddress(address: String) = friendDao.getFriendByAddress(address)

    suspend fun insertFriend(friend: com.btmessenger.app.data.entities.Friend) = friendDao.insertFriend(friend)

    suspend fun deleteFriend(friend: com.btmessenger.app.data.entities.Friend) = friendDao.deleteFriend(friend)

    // Outbox operations (store-and-forward)
    suspend fun upsertOutbox(message: OutboxMessage) = outboxDao.upsert(message)

    suspend fun getOutboxDue(now: Long) = outboxDao.getDue(now)

    suspend fun getOutboxById(msgId: String) = outboxDao.getById(msgId)

    suspend fun updateOutboxStatus(
        msgId: String,
        status: String,
        attempts: Int,
        lastAttemptAt: Long?,
        nextAttemptAt: Long?
    ) = outboxDao.updateStatus(msgId, status, attempts, lastAttemptAt, nextAttemptAt)

    suspend fun deleteOutboxById(msgId: String) = outboxDao.deleteById(msgId)
}
