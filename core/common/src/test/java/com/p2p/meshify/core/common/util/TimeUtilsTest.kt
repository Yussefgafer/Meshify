package com.p2p.meshify.core.common.util

import java.text.SimpleDateFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeUtilsTest {

    @Test
    fun formatMessageTime_afternoon() {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            .parse("2024-01-15 14:30:00")!!
            .time
        assertEquals("02:30 PM", formatMessageTime(timestamp))
    }

    @Test
    fun formatMessageTime_midnight() {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            .parse("2024-01-15 00:00:00")!!
            .time
        assertEquals("12:00 AM", formatMessageTime(timestamp))
    }
}
