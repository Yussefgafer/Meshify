package com.p2p.meshify.core.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * MeshifyDatabaseTest — exercises the RoomDatabase subclass via an
 * in-memory database on Robolectric.
 *
 * What this covers:
 * - Successful creation + DAO accessor non-null
 * - Insert/query round-trip proving the DB is functional (not just a shell)
 * - Close / reopen lifecycle
 * - Migration objects exist and have correct start/end version
 * - runInTransaction executes block
 * - clearAllTables clears rows
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class MeshifyDatabaseTest {

    private var db: MeshifyDatabase? = null

    @After
    fun tearDown() {
        db?.close()
        db = null
    }

    private fun newInMemoryDb(): MeshifyDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, MeshifyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { db = it }
    }

    @Test
    fun `in-memory database creates successfully`() {
        val database = newInMemoryDb()
        assertNotNull(database)
        // Room opens lazily on first query; openHelper must be available immediately.
        assertNotNull(database.openHelper)
        assertNotNull(database.openHelper.writableDatabase)
        assertTrue(database.openHelper.writableDatabase.isOpen)
        assertTrue(database.isOpen)
    }

    @Test
    fun `DAO accessors are non-null`() {
        val database = newInMemoryDb()
        assertNotNull(database.chatDao())
        assertNotNull(database.messageDao())
        assertNotNull(database.pendingMessageDao())
    }

    @Test
    fun `database remains open after DAO creation`() {
        val database = newInMemoryDb()
        database.chatDao()
        database.messageDao()
        database.pendingMessageDao()
        // Trigger actual open via a query, then assert open.
        kotlinx.coroutines.runBlocking {
            database.chatDao().insertChat(
                com.p2p.meshify.core.data.local.entity.ChatEntity(
                    peerId = "probe", peerName = "Probe", lastMessage = null, lastTimestamp = 1L
                )
            )
        }
        assertTrue(database.isOpen)
        assertTrue(database.openHelper.writableDatabase.isOpen)
    }

    @Test
    fun `insert and query proves database is functional`() {
        val database = newInMemoryDb()
        val dao = database.chatDao()

        kotlinx.coroutines.runBlocking {
            dao.insertChat(
                com.p2p.meshify.core.data.local.entity.ChatEntity(
                    peerId = "peer-1",
                    peerName = "Alice",
                    lastMessage = "hello",
                    lastTimestamp = 123456L
                )
            )
            val fetched = dao.getChatById("peer-1")
            assertNotNull(fetched)
            assertEquals("peer-1", fetched!!.peerId)
            assertEquals("Alice", fetched.peerName)
        }
    }

    @Test
    fun `close makes database not open`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, MeshifyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        // Force open via a query so isOpen is meaningful.
        database.openHelper.writableDatabase
        assertTrue(database.isOpen)
        database.close()
        assertTrue(database.isOpen.not())
        // Don't let tearDown double-close: we handle it ourselves here.
        db = null
    }

    @Test
    fun `reopen after close creates new independent database`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db1 = Room.inMemoryDatabaseBuilder(context, MeshifyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        kotlinx.coroutines.runBlocking {
            db1.chatDao().insertChat(
                com.p2p.meshify.core.data.local.entity.ChatEntity(
                    peerId = "p1", peerName = "A", lastMessage = null, lastTimestamp = 1L
                )
            )
        }
        db1.close()
        assertTrue(db1.isOpen.not())

        // A fresh in-memory instance starts empty (different DB file).
        db = Room.inMemoryDatabaseBuilder(context, MeshifyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        kotlinx.coroutines.runBlocking {
            val found = db!!.chatDao().getChatById("p1")
            assertTrue("fresh in-memory DB must be empty", found == null)
        }
    }

    @Test
    fun `MIGRATION_6_7 has correct version bounds`() {
        assertEquals(6, MeshifyDatabase.MIGRATION_6_7.startVersion)
        assertEquals(7, MeshifyDatabase.MIGRATION_6_7.endVersion)
    }

    @Test
    fun `MIGRATION_7_8 has correct version bounds`() {
        assertEquals(7, MeshifyDatabase.MIGRATION_7_8.startVersion)
        assertEquals(8, MeshifyDatabase.MIGRATION_7_8.endVersion)
    }

    @Test
    fun `runInTransaction executes block`() {
        val database = newInMemoryDb()
        var executed = false
        database.runInTransaction {
            executed = true
        }
        assertTrue(executed)
    }

    @Test
    fun `clearAllTables removes all rows`() {
        val database = newInMemoryDb()
        kotlinx.coroutines.runBlocking {
            database.chatDao().insertChat(
                com.p2p.meshify.core.data.local.entity.ChatEntity(
                    peerId = "p-clear", peerName = "Bob", lastMessage = "hi", lastTimestamp = 9L
                )
            )
            assertNotNull(database.chatDao().getChatById("p-clear"))
            database.clearAllTables()
            assertTrue("chat should be gone after clearAllTables", database.chatDao().getChatById("p-clear") == null)
        }
    }

    @Test
    fun `open helper is non-null after build`() {
        val database = newInMemoryDb()
        assertNotNull(database.openHelper)
        assertNotNull(database.openHelper.writableDatabase)
    }
}
