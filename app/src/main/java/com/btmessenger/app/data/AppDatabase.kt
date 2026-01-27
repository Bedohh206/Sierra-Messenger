package com.btmessenger.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.btmessenger.app.data.dao.MessageDao
import com.btmessenger.app.data.dao.PeerDao
import com.btmessenger.app.data.dao.GroupDao
import com.btmessenger.app.data.entities.Message
import com.btmessenger.app.data.entities.Peer
import com.btmessenger.app.data.entities.Group

@Database(
    entities = [Peer::class, Message::class, Group::class, com.btmessenger.app.data.entities.Friend::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun peerDao(): PeerDao
    abstract fun messageDao(): MessageDao
    abstract fun groupDao(): GroupDao
    abstract fun friendDao(): com.btmessenger.app.data.dao.FriendDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bluetooth_messenger_db"
                )
                    .fallbackToDestructiveMigration() // For development - recreate DB on schema change
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
