@file:OptIn(ExperimentalCoroutinesApi::class)

package pl.skolimowski.autoclicker.ui

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher

// https://developer.android.com/kotlin/coroutines/test#testdispatchers
abstract class TestDispatchers(private val testDispatcher: TestDispatcher) : DispatcherProvider {
    override val main: CoroutineDispatcher
        get() = testDispatcher
    override val io: CoroutineDispatcher
        get() = testDispatcher
    override val default: CoroutineDispatcher
        get() = testDispatcher
}

class StandardTestDispatcher() : TestDispatchers(StandardTestDispatcher())

class UnconfinedTestDispatcher() : TestDispatchers(UnconfinedTestDispatcher())