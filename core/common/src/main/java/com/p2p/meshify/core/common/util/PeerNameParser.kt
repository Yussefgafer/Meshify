package com.p2p.meshify.core.common.util

/**
 * Utility for parsing peer display names from various transport formats.
 *
 * Handles common patterns:
 * - "name (device_id)" → "name"
 * - Standard name strings are returned as-is
 */
object PeerNameParser {

    /**
     * Extracts the clean display name from a raw peer name string.
     * Strips any trailing device identifier in parentheses.
     */
    fun parseName(raw: String): String {
        return raw.substringBefore(" (").trim()
    }
}
