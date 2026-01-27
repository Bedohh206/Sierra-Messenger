package com.btmessenger.app.data.dao

import androidx.room.*
import com.btmessenger.app.data.entities.Friend
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendDao {
    @Query("SELECT * FROM friends ORDER BY addedAt DESC")
    fun getAllFriends(): Flow<List<Friend>>

    @Query("SELECT * FROM friends WHERE id = :friendId")
    suspend fun getFriendById(friendId: String): Friend?

    @Query("SELECT * FROM friends WHERE address = :address LIMIT 1")
    suspend fun getFriendByAddress(address: String): Friend?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriend(friend: Friend)

    @Delete
    suspend fun deleteFriend(friend: Friend)

    @Query("DELETE FROM friends WHERE id = :friendId")
    suspend fun deleteFriendById(friendId: String)
}
