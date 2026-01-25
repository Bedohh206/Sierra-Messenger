package com.btmessenger.app.data.dao

import androidx.room.*
import com.btmessenger.app.data.entities.Peer
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerDao {
    @Query("SELECT * FROM peers ORDER BY lastSeen DESC")
    fun getAllPeers(): Flow<List<Peer>>
    
    @Query("SELECT * FROM peers WHERE id = :peerId")
    suspend fun getPeerById(peerId: String): Peer?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeer(peer: Peer)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeers(peers: List<Peer>)
    
    @Update
    suspend fun updatePeer(peer: Peer)
    
    @Delete
    suspend fun deletePeer(peer: Peer)
    
    @Query("DELETE FROM peers WHERE id = :peerId")
    suspend fun deletePeerById(peerId: String)
    
    @Query("DELETE FROM peers")
    suspend fun deleteAllPeers()
}
