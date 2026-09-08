package com.p2p.meshify.receivers

import com.p2p.meshify.MeshifyApp

/**
 * Robolectric application for [ReplyReceiverTest]. Skips Hilt injection and
 * transport startup; the test assigns [MeshifyApp.chatRepository] /
 * [MeshifyApp.database] directly (they are public @Inject lateinit vars), so
 * onReceive can exercise the repository path without booting real transports.
 */
class TestReplyApp : MeshifyApp() {
    override fun onCreate() {
        // Intentionally empty: no Hilt, no transports, no crash handler.
    }
}