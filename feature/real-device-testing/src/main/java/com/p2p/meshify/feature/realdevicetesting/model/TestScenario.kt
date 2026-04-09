package com.p2p.meshify.feature.realdevicetesting.model

/**
 * Defines a single test scenario that can be selected and executed.
 *
 * @property id Unique identifier for the test (used for logging and state tracking).
 * @property titleRes String resource ID for the display name.
 * @property subtitleRes String resource ID for the description/subtitle.
 * @property icon String identifier for the icon (mapped to VectorDrawable at UI layer).
 * @property enabled Whether this test is currently available to run.
 */
data class TestScenario(
    val id: String,
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: String,
    val enabled: Boolean = true
)
