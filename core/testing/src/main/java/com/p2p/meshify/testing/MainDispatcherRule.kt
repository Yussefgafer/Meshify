package com.p2p.meshify.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * JUnit4 rule that swaps [Dispatchers.Main] for a [TestDispatcher] for the duration of a test and
 * restores it afterwards. Pure-JVM (no Android), so it is safe for both unit and Robolectric tests.
 *
 * Defaults to [StandardTestDispatcher] so coroutines are scheduled lazily and must be driven via
 * `dispatcher.scheduler.runCurrent()` / `advanceUntilIdle()`. Pass a [TestDispatcher] (e.g.
 * `UnconfinedTestDispatcher()`) to get eager execution instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                Dispatchers.setMain(dispatcher)
                try {
                    base.evaluate()
                } finally {
                    Dispatchers.resetMain()
                }
            }
        }
}
