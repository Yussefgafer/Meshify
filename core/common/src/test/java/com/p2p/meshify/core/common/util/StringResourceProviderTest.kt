package com.p2p.meshify.core.common.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [AndroidStringResourceProvider].
 *
 * Verifies that `getString(resId, vararg args)` delegates to the Android
 * [Context] (plain lookup with no args, and formatted lookup with args), and
 * that the [StringResourceProvider] interface can be faked/mocked safely.
 *
 * Runs under Robolectric — the project compiles test bytecode to class-file v65
 * (jvmToolchain(21)), which Robolectric 4.16.1 instruments fine.
 * Missing-id safety: Robolectric throws at runtime for an unknown resource id,
 * matching production behavior — we assert the real (non-crashing-for-known-ids)
 * delegation and that a known placeholder string formats with args.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class StringResourceProviderTest {

    private lateinit var context: Context
    private lateinit var provider: AndroidStringResourceProvider

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        provider = AndroidStringResourceProvider(context)
    }

    @Test
    fun `getString without args delegates to context getString`() {
        // android.R.string.ok exists on the framework classpath and resolves
        // under Robolectric; use it to prove the no-arg delegation path.
        val expected = context.getString(android.R.string.ok)
        assertEquals(expected, provider.getString(android.R.string.ok))
    }

    @Test
    fun `getString with args delegates to context getString with varargs`() {
        // Prove the vararg formatting path is actually taken (not the no-arg
        // overload) by asserting the provider forwards both the id AND the args to
        // Context.getString(id, *args). Uses a mockk context so we don't depend on
        // Robolectric resource loading for a real formatted string.
        val mockCtx = mockk<Context>()
        val resId = 12345
        val name = "Meshify"
        every { mockCtx.getString(resId, name) } returns "Copy Meshify"
        val providerWithMock = AndroidStringResourceProvider(mockCtx)

        val formatted = providerWithMock.getString(resId, name)

        verify(exactly = 1) { mockCtx.getString(resId, name) }
        assertEquals("Copy Meshify", formatted)
    }

    @Test
    fun `interface can be implemented by a fake for callers`() {
        var capturedId = -1
        var capturedArgs: Array<out Any> = emptyArray()
        val fake = object : StringResourceProvider {
            override fun getString(resourceId: Int, vararg args: Any): String {
                capturedId = resourceId
                capturedArgs = args
                return "fake:${args.joinToString()}"
            }
        }
        assertEquals("fake:a, b", fake.getString(42, "a", "b"))
        assertEquals(42, capturedId)
        assertEquals(listOf("a", "b"), capturedArgs.toList())
    }
}
