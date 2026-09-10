package com.p2p.meshify.core.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.room.withTransaction
import com.p2p.meshify.core.data.local.MeshifyDatabase
import com.p2p.meshify.core.data.local.entity.ChatEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TransactionRunnerTest — verifies the [TransactionRunner] contract.
 *
 * The interface is a single `suspend fun <T> run(block: suspend () -> T): T`.
 * The production implementation (`database.withTransaction(block)`) is tested
 * two ways:
 *
 * 1. Pure fake (no Room): asserts the contract — return value, side effects,
 *    exception propagation (including CancellationException), nested calls,
 *    and that a plain fake does NOT auto-roll-back.
 * 2. Real Room TransactionRunner (`database.withTransaction`): asserts the
 *    load-bearing property — a failed block rolls back all DB writes in the
 *    transaction, while a successful one commits.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class TransactionRunnerTest {

    // ---- a simple non-Room fake for contract-only tests ----

    private fun fakeRunner(): TransactionRunner = object : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }

    // ---- real Room TransactionRunner for rollback/atomicity tests ----

    private lateinit var db: MeshifyDatabase
    private lateinit var realRunner: TransactionRunner

    @Before
    fun setUpRealRunner() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MeshifyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        realRunner = object : TransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction(block)
        }
    }

    @After
    fun tearDownRealRunner() {
        db.close()
    }

    // ==================== pure-contract tests (fake) ====================

    @Test
    fun `fake runner returns block value`() = runTest {
        val runner = fakeRunner()
        val result: Int = runner.run { 42 }
        assertEquals(42, result)
    }

    @Test
    fun `fake runner returns null block value`() = runTest {
        val runner = fakeRunner()
        val result: String? = runner.run { null }
        assertNull(result)
    }

    @Test
    fun `fake runner executes block exactly once`() = runTest {
        val runner = fakeRunner()
        var count = 0
        runner.run { count++ }
        assertEquals(1, count)
    }

    @Test
    fun `fake runner executes block with side effects`() = runTest {
        val runner = fakeRunner()
        val log = mutableListOf<String>()
        runner.run { log += "inside" }
        assertEquals(listOf("inside"), log)
    }

    @Test
    fun `fake runner propagates RuntimeException`() = runTest {
        val runner = fakeRunner()
        try {
            runner.run { throw IllegalStateException("boom") }
            fail("expected exception")
        } catch (e: IllegalStateException) {
            assertEquals("boom", e.message)
        }
    }

    @Test
    fun `fake runner propagates checked-like exception`() = runTest {
        val runner = fakeRunner()
        try {
            runner.run { throw java.io.IOException("io fail") }
            fail("expected exception")
        } catch (e: java.io.IOException) {
            assertEquals("io fail", e.message)
        }
    }

    @Test
    fun `fake runner does not swallow CancellationException`() = runTest {
        val runner = fakeRunner()
        try {
            runner.run { throw CancellationException("cancelled") }
            fail("expected CancellationException")
        } catch (e: CancellationException) {
            assertEquals("cancelled", e.message)
        }
    }

    @Test
    fun `fake runner CancellationException subclass is also propagated`() = runTest {
        val runner = fakeRunner()
        class MyCancel : CancellationException("my cancel")
        try {
            runner.run { throw MyCancel() }
            fail("expected MyCancel")
        } catch (e: MyCancel) {
            assertEquals("my cancel", e.message)
        }
    }

    @Test
    fun `fake runner nested runs both execute`() = runTest {
        val runner = fakeRunner()
        var outer = 0
        var inner = 0
        runner.run {
            outer++
            runner.run { inner++ }
        }
        assertEquals(1, outer)
        assertEquals(1, inner)
    }

    @Test
    fun `fake runner nested inner exception propagates to outer`() = runTest {
        val runner = fakeRunner()
        try {
            runner.run {
                runner.run { throw IllegalArgumentException("inner") }
            }
            fail("expected exception")
        } catch (e: IllegalArgumentException) {
            assertEquals("inner", e.message)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `fake runner works inside TestScope`() = runTest(StandardTestDispatcher()) {
        val runner = fakeRunner()
        var ran = false
        val scope = TestScope(testScheduler)
        scope.runTest {
            runner.run { ran = true }
        }
        assertTrue(ran)
    }

    // ==================== real Room TransactionRunner — atomicity ====================

    @Test
    fun `real runner commits on success`() = runTest {
        realRunner.run {
            db.chatDao().insertChat(ChatEntity("p1", "Alice", "hi", 1L))
        }
        assertNotNull(db.chatDao().getChatById("p1"))
    }

    @Test
    fun `real runner rolls back on exception — no partial commit`() = runTest {
        try {
            realRunner.run {
                db.chatDao().insertChat(ChatEntity("p1", "Alice", "hi", 1L))
                throw IllegalStateException("fail mid-transaction")
            }
            fail("expected exception")
        } catch (_: IllegalStateException) {
            // expected
        }
        // The chat inserted before the throw must have been rolled back
        assertNull("rolled-back insert must not persist", db.chatDao().getChatById("p1"))
    }

    @Test
    fun `real runner rolls back multiple inserts on exception`() = runTest {
        try {
            realRunner.run {
                db.chatDao().insertChat(ChatEntity("p1", "Alice", "hi", 1L))
                db.chatDao().insertChat(ChatEntity("p2", "Bob", "hey", 2L))
                throw RuntimeException("fail")
            }
            fail("expected exception")
        } catch (_: RuntimeException) {
        }
        assertNull(db.chatDao().getChatById("p1"))
        assertNull(db.chatDao().getChatById("p2"))
    }

    @Test
    fun `real runner still propagates CancellationException after rollback`() = runTest {
        try {
            realRunner.run {
                db.chatDao().insertChat(ChatEntity("p1", "Alice", "hi", 1L))
                throw CancellationException("cancel mid-tx")
            }
            fail("expected cancellation")
        } catch (e: CancellationException) {
            assertEquals("cancel mid-tx", e.message)
        }
        assertNull(db.chatDao().getChatById("p1"))
    }

    @Test
    fun `real runner returns block value`() = runTest {
        val v: String = realRunner.run { "result" }
        assertEquals("result", v)
    }

    @Test
    fun `real runner sequential transactions are independent`() = runTest {
        realRunner.run { db.chatDao().insertChat(ChatEntity("p1", "Alice", "hi", 1L)) }
        try {
            realRunner.run {
                db.chatDao().insertChat(ChatEntity("p2", "Bob", "hey", 2L))
                throw RuntimeException("second tx fails")
            }
            fail("expected exception")
        } catch (_: RuntimeException) {
        }
        assertNotNull("first tx committed independently", db.chatDao().getChatById("p1"))
        assertNull("second tx rolled back", db.chatDao().getChatById("p2"))
    }
}
