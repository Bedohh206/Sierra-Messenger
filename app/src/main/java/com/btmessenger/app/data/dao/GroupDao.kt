package com.btmessenger.app.data.dao

import androidx.room.*
import com.btmessenger.app.data.entities.Group
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY createdAt DESC")
    fun getAllGroups(): Flow<List<Group>>

    @Query("SELECT * FROM groups WHERE groupId = :groupId")
    suspend fun getGroupById(groupId: String): Group?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: Group)

    @Delete
    suspend fun deleteGroup(group: Group)
}
