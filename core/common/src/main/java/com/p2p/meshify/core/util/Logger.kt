package com.p2p.meshify.core.util

import android.util.Log

/**
 * Centralized Logger for Meshify.
 * 
 * Unified logging utility with configurable TAG.
 * Located in core:common to be accessible by all modules.
 */
object Logger {
    private const val DEFAULT_TAG = "Meshify"

    /**
     * Log debug message
     * @param message The message to log
     * @param tag Optional custom tag (uses default if not provided)
     */
    fun d(message: String, tag: String = DEFAULT_TAG) {
        Log.d(tag, message)
    }

    /**
     * Log error message with optional throwable
     * Overloaded for backward compatibility:
     * - e(message, throwable) - uses default tag
     * - e(message, tag, throwable) - custom tag
     */
    fun e(message: String, throwable: Throwable? = null, tag: String = DEFAULT_TAG) {
        Log.e(tag, message, throwable)
    }

    /**
     * Log info message
     * @param message The message to log
     * @param tag Optional custom tag (uses default if not provided)
     */
    fun i(message: String, tag: String = DEFAULT_TAG) {
        Log.i(tag, message)
    }

    /**
     * Log warning message
     * @param message The message to log
     * @param tag Optional custom tag (uses default if not provided)
     */
    fun w(message: String, tag: String = DEFAULT_TAG) {
        Log.w(tag, message)
    }

    /**
     * Create a logger with a custom tag
     * @param customTag The custom tag to use for logging
     * @return LoggerWrapper with the specified tag
     */
    fun withTag(customTag: String): LoggerWrapper = LoggerWrapper(customTag)
}

/**
 * Wrapper class for logging with a specific tag
 */
class LoggerWrapper(private val tag: String) {
    fun d(message: String) = Log.d(tag, message)
    fun e(message: String, throwable: Throwable? = null) = Log.e(tag, message, throwable)
    fun i(message: String) = Log.i(tag, message)
    fun w(message: String) = Log.w(tag, message)
}
