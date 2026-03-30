package com.p2p.meshify.core.util

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log

/**
 * Centralized Logger for Meshify.
 *
 * Unified logging utility with configurable TAG.
 * Located in core:common to be accessible by all modules.
 * 
 * ✅ SEC-03: Debug logging disabled in production builds
 * Prevents sensitive data exposure in release builds
 */
object Logger {
    private const val DEFAULT_TAG = "Meshify"
    
    // ✅ SEC-03: Only log debug/info in debug builds (using debuggable flag)
    private var isDebug: Boolean = false
    
    // Public getter for LoggerWrapper
    val isDebugEnabled: Boolean
        get() = isDebug
    
    /**
     * Initialize logger with application context.
     * Call this once at app startup.
     */
    fun init(app: Application) {
        isDebug = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    /**
     * Log debug message
     * @param message The message to log
     * @param tag Optional custom tag (uses default if not provided)
     */
    fun d(message: String, tag: String = DEFAULT_TAG) {
        if (isDebugEnabled) Log.d(tag, message)
    }

    /**
     * Log error message with optional throwable
     * Overloaded for backward compatibility:
     * - e(message, throwable) - uses default tag
     * - e(message, tag, throwable) - custom tag
     * 
     * ✅ Always logs errors (needed for production debugging)
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
        if (isDebugEnabled) Log.i(tag, message)
    }

    /**
     * Log warning message
     * @param message The message to log
     * @param tag Optional custom tag (uses default if not provided)
     */
    fun w(message: String, tag: String = DEFAULT_TAG) {
        if (isDebugEnabled) Log.w(tag, message)
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
    fun d(message: String) {
        if (Logger.isDebugEnabled) Log.d(tag, message)
    }
    fun e(message: String, throwable: Throwable? = null) = Log.e(tag, message, throwable)
    fun i(message: String) {
        if (Logger.isDebugEnabled) Log.i(tag, message)
    }
    fun w(message: String) {
        if (Logger.isDebugEnabled) Log.w(tag, message)
    }
}
