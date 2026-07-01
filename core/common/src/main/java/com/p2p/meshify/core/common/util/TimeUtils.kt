package com.p2p.meshify.core.common.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Thread-safe lazy-initialized date formatter for message timestamps.
 * Format: "hh:mm a" (e.g., "02:30 PM") using US locale.
 */
private val messageTimeFormatter by lazy {
    SimpleDateFormat("hh:mm a", Locale.US)
}

/**
 * Formats a Unix timestamp (in milliseconds) to a human-readable time string.
 *
 * @param timestamp Unix timestamp in milliseconds.
 * @return Formatted time string in "hh:mm a" format, e.g., "02:30 PM".
 */
fun formatMessageTime(timestamp: Long): String {
    return messageTimeFormatter.format(Date(timestamp))
}
