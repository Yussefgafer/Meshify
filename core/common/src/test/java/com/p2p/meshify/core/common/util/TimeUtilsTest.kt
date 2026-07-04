package com.p2p.meshify.core.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Unit tests for TimeUtils (formatMessageTime).
 * Tests cover AM/PM formatting, known timestamps, and current time.
 */
class TimeUtilsTest {

    @Test
    fun `formatMessageTime formats morning timestamp correctly`() {
        // Given - January 1, 2024 at 10:30:00 AM
        val calendar = Calendar.getInstance(Locale.US).apply {
            set(2024, Calendar.JANUARY, 1, 10, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val timestamp = calendar.timeInMillis

        // When
        val result = formatMessageTime(timestamp)

        // Then
        assertEquals("10:30 AM", result)
    }

    @Test
    fun `formatMessageTime formats afternoon timestamp correctly`() {
        // Given - January 1, 2024 at 2:30:00 PM
        val calendar = Calendar.getInstance(Locale.US).apply {
            set(2024, Calendar.JANUARY, 1, 14, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val timestamp = calendar.timeInMillis

        // When
        val result = formatMessageTime(timestamp)

        // Then
        assertEquals("02:30 PM", result)
    }

    @Test
    fun `formatMessageTime formats midnight as 12 AM`() {
        // Given - January 1, 2024 at 12:00:00 AM (midnight)
        val calendar = Calendar.getInstance(Locale.US).apply {
            set(2024, Calendar.JANUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val timestamp = calendar.timeInMillis

        // When
        val result = formatMessageTime(timestamp)

        // Then
        assertEquals("12:00 AM", result)
    }

    @Test
    fun `formatMessageTime formats noon as 12 PM`() {
        // Given - January 1, 2024 at 12:00:00 PM (noon)
        val calendar = Calendar.getInstance(Locale.US).apply {
            set(2024, Calendar.JANUARY, 1, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val timestamp = calendar.timeInMillis

        // When
        val result = formatMessageTime(timestamp)

        // Then
        assertEquals("12:00 PM", result)
    }

    @Test
    fun `formatMessageTime handles late night PM correctly`() {
        // Given - January 1, 2024 at 11:59:00 PM
        val calendar = Calendar.getInstance(Locale.US).apply {
            set(2024, Calendar.JANUARY, 1, 23, 59, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val timestamp = calendar.timeInMillis

        // When
        val result = formatMessageTime(timestamp)

        // Then
        assertEquals("11:59 PM", result)
    }

    @Test
    fun `formatMessageTime handles early morning AM correctly`() {
        // Given - January 1, 2024 at 1:01:00 AM
        val calendar = Calendar.getInstance(Locale.US).apply {
            set(2024, Calendar.JANUARY, 1, 1, 1, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val timestamp = calendar.timeInMillis

        // When
        val result = formatMessageTime(timestamp)

        // Then
        assertEquals("01:01 AM", result)
    }

    @Test
    fun `formatMessageTime works with current timestamp`() {
        // Given
        val now = System.currentTimeMillis()
        val formatter = SimpleDateFormat("hh:mm a", Locale.US)

        // When
        val result = formatMessageTime(now)
        val expected = formatter.format(Date(now))

        // Then
        assertEquals(expected, result)
    }

    @Test
    fun `formatMessageTime output matches hh mm a pattern`() {
        // Given - multiple timestamps throughout the day
        val calendar = Calendar.getInstance(Locale.US).apply {
            set(2024, Calendar.JANUARY, 1, 0, 0, 0)
        }

        // When/Then - check every hour throughout the day
        val formatter = SimpleDateFormat("hh:mm a", Locale.US)
        repeat(24) { hour ->
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            val timestamp = calendar.timeInMillis

            val result = formatMessageTime(timestamp)
            val expected = formatter.format(Date(timestamp))

            assertEquals(
                "Failed for hour $hour:00",
                expected,
                result
            )
        }
    }

    @Test
    fun `formatMessageTime returns consistent formatting for same timestamp`() {
        // Given
        val calendar = Calendar.getInstance(Locale.US).apply {
            set(2024, Calendar.JUNE, 15, 8, 45, 30)
            set(Calendar.MILLISECOND, 0)
        }
        val timestamp = calendar.timeInMillis

        // When
        val result1 = formatMessageTime(timestamp)
        val result2 = formatMessageTime(timestamp)
        val result3 = formatMessageTime(timestamp)

        // Then
        assertEquals(result1, result2)
        assertEquals(result2, result3)
    }

    @Test
    fun `formatMessageTime uses US locale with AM and PM`() {
        // Given - timestamp at 3:00 PM
        val calendar = Calendar.getInstance(Locale.US).apply {
            set(2024, Calendar.JANUARY, 1, 15, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val timestamp = calendar.timeInMillis

        // When
        val result = formatMessageTime(timestamp)

        // Then - must contain "PM" (US locale), not "p.m." or "pm" in different locales
        assertTrue("Expected PM in result: $result", result.contains("PM"))
        assertEquals("03:00 PM", result)
    }

    @Test
    fun `formatMessageTime epoch timestamp`() {
        // Given - Unix epoch: January 1, 1970 00:00:00 UTC
        val timestamp = 0L

        // When
        val result = formatMessageTime(timestamp)

        // Then - depends on timezone, but should be a valid time string
        assertTrue("Expected non-empty result", result.isNotEmpty())
        // The formatter uses default timezone, so we just verify it produces a result
        assertTrue("Expected format like 'hh:mm AM/PM'", result.matches("\\d{2}:\\d{2} [AP]M".toRegex()))
    }
}
