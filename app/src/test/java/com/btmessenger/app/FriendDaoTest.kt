package com.btmessenger.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.btmessenger.app.data.AppDatabase
import com.btmessenger.app.data.dao.FriendDao
import com.btmessenger.app.data.entities.Friend
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FriendDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var friendDao: FriendDao

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        friendDao = db.friendDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insertAndGetFriend() = runBlocking {
        val friend = Friend(id = "f1", name = "Alice", address = "AA:BB:CC:DD:EE:FF")
        friendDao.insertFriend(friend)

        val loaded = friendDao.getFriendById("f1")
        assertNotNull(loaded)
        assertEquals("Alice", loaded?.name)
        assertEquals("AA:BB:CC:DD:EE:FF", loaded?.address)
    }

    @Test
    fun deleteFriendById() = runBlocking {
        val friend = Friend(id = "f2", name = "Bob", address = "11:22:33:44:55:66")
        friendDao.insertFriend(friend)

        var loaded = friendDao.getFriendById("f2")
        assertNotNull(loaded)

        friendDao.deleteFriendById("f2")
        loaded = friendDao.getFriendById("f2")
        assertNull(loaded)
    }
}
