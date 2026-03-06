package com.saltatoryimpulse.scrolliosis.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.saltatoryimpulse.scrolliosis.AppDatabase
import com.saltatoryimpulse.scrolliosis.BlockedApp
import com.saltatoryimpulse.scrolliosis.KnowledgeEntry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class KnowledgeRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: KnowledgeRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = KnowledgeRepository(db.knowledgeDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndRetrieveEntry() = runBlocking {
        val entry = KnowledgeEntry(title = "Test", summary = "Summary")
        repo.insertEntry(entry)

        val entries = repo.getAllEntries().first()
        assertEquals(1, entries.size)
        assertEquals("Test", entries[0].title)
    }

    @Test
    fun blockAndQueryApp() = runBlocking {
        val app = BlockedApp(packageName = "com.example.app", appName = "Example")
        repo.blockApp(app)

        val blocked = repo.getBlockedApps().first()
        assertEquals(1, blocked.size)
        assertEquals("com.example.app", blocked[0].packageName)

        val exists = repo.isAppBlocked("com.example.app")
        assertEquals(true, exists)
    }
}
