@file:OptIn(ExperimentalCoroutinesApi::class)

package pl.skolimowski.autoclicker.test_util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import pl.skolimowski.autoclicker.ui.DispatcherProvider

// https://developer.android.com/kotlin/coroutines/test#testdispatchers
abstract class TestDispatchers(val testDispatcher: TestDispatcher) : DispatcherProvider {
    override val main: CoroutineDispatcher
        get() = testDispatcher
    override val io: CoroutineDispatcher
        get() = testDispatcher
    override val default: CoroutineDispatcher
        get() = testDispatcher
}

class StandardTestDispatcher(scheduler: TestCoroutineScheduler? = null) :
    TestDispatchers(StandardTestDispatcher(scheduler = scheduler))

class UnconfinedTestDispatcher(scheduler: TestCoroutineScheduler? = null) :
    TestDispatchers(UnconfinedTestDispatcher(scheduler = scheduler))