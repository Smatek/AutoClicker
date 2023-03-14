package pl.skolimowski.autoclicker.ui.action_bar

import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import pl.skolimowski.autoclicker.MyApp
import pl.skolimowski.autoclicker.ui.DispatcherProvider
import pl.skolimowski.autoclicker.ui.UiEvent

// This class is not a typical ViewModel that needs @HiltViewModel annotation or extend
// androidx.lifecycle.ViewModel class. It is created for purposes of Service that behaves
// differently than activities or fragments.
class ActionBarServiceViewModel @Inject constructor(
    private val dispatchers: DispatcherProvider,
    myApp: MyApp
) {
    private val applicationScope = myApp.applicationScope

    private val _actionsSharedFlow = MutableSharedFlow<ActionBarServiceActions>()
    val actionsSharedFlow: SharedFlow<ActionBarServiceActions> = _actionsSharedFlow

    fun onUiEvent(uiEvent: UiEvent) {
        when (uiEvent) {
            is ActionBarServiceEvents.OnCloseImageClickedEvent -> {
                applicationScope.launch(dispatchers.io) {
                    _actionsSharedFlow.emit(ActionBarServiceActions.OnDisableSelfAction)
                }
            }
        }
    }
}
