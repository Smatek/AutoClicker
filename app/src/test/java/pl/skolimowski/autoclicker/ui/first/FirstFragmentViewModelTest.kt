package pl.skolimowski.autoclicker.ui.first

import android.accessibilityservice.AccessibilityServiceInfo.*
import android.content.Context
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import pl.skolimowski.autoclicker.test_util.UnconfinedTestDispatcher
import pl.skolimowski.autoclicker.ui.first.FirstFragmentActions.*
import pl.skolimowski.autoclicker.ui.first.FirstFragmentEvents.*

class FirstFragmentViewModelTest {
    private val appContextMock: Context = mockk(relaxed = true)

    lateinit var viewModel: FirstFragmentViewModel

    private fun createViewModel(): FirstFragmentViewModel {
        return FirstFragmentViewModel(
            appContext = appContextMock,
            dispatchers = UnconfinedTestDispatcher()
        )
    }

    @Test
    fun onUiEvent_OnActionButtonClickEvent_serviceDisabled() = runTest {
        viewModel = createViewModel()

        viewModel.actionsSharedFlow.test {
            viewModel.onUiEvent(OnAccessibilitySettingsButtonClickEvent)

            val action = awaitItem()

            assertThat(action).isInstanceOf(OpenAccessibilitySettings::class.java)
        }
    }
}