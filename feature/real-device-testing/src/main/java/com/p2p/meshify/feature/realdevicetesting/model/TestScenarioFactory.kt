package com.p2p.meshify.feature.realdevicetesting.model

/**
 * Factory for creating default test scenarios.
 * Separated from the domain model to keep [TestScenario] framework-agnostic.
 */
object TestScenarioFactory {

    /** Returns the full list of built-in test scenarios. */
    fun createDefaults(): List<TestScenario> = listOf(
        TestScenario(
            id = "discovery",
            titleRes = com.p2p.meshify.feature.realdevicetesting.R.string.test_discovery_title,
            subtitleRes = com.p2p.meshify.feature.realdevicetesting.R.string.test_discovery_subtitle,
            icon = "search"
        ),
        TestScenario(
            id = "ping",
            titleRes = com.p2p.meshify.feature.realdevicetesting.R.string.test_ping_title,
            subtitleRes = com.p2p.meshify.feature.realdevicetesting.R.string.test_ping_subtitle,
            icon = "signal"
        ),
        TestScenario(
            id = "message",
            titleRes = com.p2p.meshify.feature.realdevicetesting.R.string.test_message_title,
            subtitleRes = com.p2p.meshify.feature.realdevicetesting.R.string.test_message_subtitle,
            icon = "chat"
        ),
        TestScenario(
            id = "file",
            titleRes = com.p2p.meshify.feature.realdevicetesting.R.string.test_file_title,
            subtitleRes = com.p2p.meshify.feature.realdevicetesting.R.string.test_file_subtitle,
            icon = "attachment"
        ),
        TestScenario(
            id = "latency",
            titleRes = com.p2p.meshify.feature.realdevicetesting.R.string.test_latency_title,
            subtitleRes = com.p2p.meshify.feature.realdevicetesting.R.string.test_latency_subtitle,
            icon = "timer"
        ),
        TestScenario(
            id = "roundtrip",
            titleRes = com.p2p.meshify.feature.realdevicetesting.R.string.test_roundtrip_title,
            subtitleRes = com.p2p.meshify.feature.realdevicetesting.R.string.test_roundtrip_subtitle,
            icon = "sync"
        ),
    )
}
