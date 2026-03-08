package com.p2p.meshify.core.util

import android.util.Log

/**
 * Centralized Logger for Meshify.
 */
object Logger {
    private const val TAG = "Meshify_Log"

    fun d(message: String) {
        Log.d(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }

    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun w(message: String) {
        Log.w(TAG, message)
    }
}
