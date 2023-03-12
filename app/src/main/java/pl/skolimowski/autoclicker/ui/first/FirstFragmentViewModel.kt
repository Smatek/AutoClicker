package pl.skolimowski.autoclicker.ui.first

import android.accessibilityservice.AccessibilityServiceInfo.*
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import pl.skolimowski.autoclicker.ui.DispatcherProvider
import pl.skolimowski.autoclicker.ui.UiEvent
import pl.skolimowski.autoclicker.ui.action_bar.ActionBarService
import pl.skolimowski.autoclicker.ui.first.FirstFragmentEvents.*

@HiltViewModel
class FirstFragmentViewModel @Inject constructor(
    @ApplicationContext val appContext: Context,
    private val dispatchers: DispatcherProvider
) : ViewModel() {
    private val _actionsSharedFlow = MutableSharedFlow<FirstFragmentActions>()
    val actionsSharedFlow: SharedFlow<FirstFragmentActions> = _actionsSharedFlow

    // todo move this to base ViewModel
    fun onUiEvent(uiEvent: UiEvent) {
        when (uiEvent) {
            is OnAccessibilitySettingsButtonClickEvent -> {
                checkAccessibilityServiceState()
            }
        }
    }

    private fun checkAccessibilityServiceState() {
        viewModelScope.launch(dispatchers.main) {
            _actionsSharedFlow.emit(FirstFragmentActions.OpenAccessibilitySettings)
        }
    }
}

data class AccessibilityServiceState(
    val isEnabled: Boolean = false,
)