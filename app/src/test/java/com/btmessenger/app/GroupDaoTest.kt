package com.btmessenger.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.btmessenger.app.data.AppDatabase
import com.btmessenger.app.data.entities.Group
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GroupDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndGetGroup() = runBlocking {
        val g = Group(groupId = "g1", name = "Test Group", hostId = "host1", createdAt = System.currentTimeMillis(), memberCount = 2)
        db.groupDao().insertGroup(g)

        val fetched = db.groupDao().getGroupById("g1")
        assertNotNull(fetched)
        assertEquals("Test Group", fetched?.name)
        assertEquals(2, fetched?.memberCount)
    }

    @Test
    fun deleteGroup_removesIt() = runBlocking {
        val g = Group(groupId = "g2", name = "DeleteMe", hostId = "host2", createdAt = System.currentTimeMillis())
        db.groupDao().insertGroup(g)

        var fetched = db.groupDao().getGroupById("g2")
        assertNotNull(fetched)

        db.groupDao().deleteGroup(g)

        fetched = db.groupDao().getGroupById("g2")
        assertNull(fetched)
    }
}
