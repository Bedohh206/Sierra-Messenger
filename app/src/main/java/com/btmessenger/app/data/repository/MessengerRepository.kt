package com.btmessenger.app.data.repository

import com.btmessenger.app.data.dao.MessageDao
import com.btmessenger.app.data.dao.PeerDao
import com.btmessenger.app.data.dao.GroupDao
import com.btmessenger.app.data.entities.Message
import com.btmessenger.app.data.entities.Peer
import kotlinx.coroutines.flow.Flow

class MessengerRepository(
    private val peerDao: PeerDao,
    private val messageDao: MessageDao,
    private val groupDao: GroupDao
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
    
    suspend fun deleteMessage(message: Message) = messageDao.deleteMessage(message)
    
    suspend fun deleteMessagesForPeer(peerId: String) = 
        messageDao.deleteMessagesForPeer(peerId)

    // Group operations
    fun getAllGroups() = groupDao.getAllGroups()

    suspend fun getGroupById(groupId: String) = groupDao.getGroupById(groupId)

    suspend fun insertGroup(group: com.btmessenger.app.data.entities.Group) = groupDao.insertGroup(group)

    suspend fun deleteGroup(group: com.btmessenger.app.data.entities.Group) = groupDao.deleteGroup(group)

    fun getMessagesForGroup(groupId: String) = messageDao.getMessagesForGroup(groupId)
}
