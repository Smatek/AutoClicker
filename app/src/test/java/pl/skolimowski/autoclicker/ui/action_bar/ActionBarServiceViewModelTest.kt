package pl.skolimowski.autoclicker.ui.action_bar

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.*
import org.junit.Before
import org.junit.Test
import pl.skolimowski.autoclicker.MyApp
import pl.skolimowski.autoclicker.test_util.UnconfinedTestDispatcher
import pl.skolimowski.autoclicker.ui.action_bar.ActionBarServiceActions.*
import pl.skolimowski.autoclicker.ui.action_bar.ActionBarServiceEvents.*

class ActionBarServiceViewModelTest {
    private val applicationMock: MyApp = mockk(relaxed = true)

    lateinit var viewModel: ActionBarServiceViewModel

    @Before
    fun setUp() {
        every { applicationMock.applicationScope } returns TestScope()

        viewModel = createViewModel()
    }

    private fun createViewModel(): ActionBarServiceViewModel {
        return ActionBarServiceViewModel(
            dispatchers = UnconfinedTestDispatcher(),
            myApp = applicationMock
        )
    }

    @Test
    fun onUiEvent_OnCloseImageClickedEvent() = runTest {
        viewModel.actionsSharedFlow.test {
            viewModel.onUiEvent(OnCloseImageClickedEvent)

            val action = awaitItem()

            assertThat(action).isInstanceOf(OnDisableSelfAction::class.java)
        }
    }
}