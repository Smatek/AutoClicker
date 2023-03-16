package pl.skolimowski.autoclicker.ui.action_bar

import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pl.skolimowski.autoclicker.MyApp
import pl.skolimowski.autoclicker.ui.DispatcherProvider
import pl.skolimowski.autoclicker.ui.UiEvent
import pl.skolimowski.autoclicker.ui.action_bar.ActionBarServiceEvents.*
import timber.log.Timber

// This class is not a typical ViewModel that needs @HiltViewModel annotation or extend
// androidx.lifecycle.ViewModel class. It is created for purposes of Service that behaves
// differently than activities or fragments.
class ActionBarServiceViewModel @Inject constructor(
    private val dispatchers: DispatcherProvider,
    myApp: MyApp
) {
    private val applicationScope = myApp.applicationScope

    private val _dragStateFlow = MutableStateFlow(DragState())
    val dragStateFlow: StateFlow<DragState> = _dragStateFlow

    private val _actionsSharedFlow = MutableSharedFlow<ActionBarServiceActions>()
    val actionsSharedFlow: SharedFlow<ActionBarServiceActions> = _actionsSharedFlow

    fun onUiEvent(uiEvent: UiEvent) {
        Timber.i("uiEvent: $uiEvent")

        when (uiEvent) {
            is OnCloseImageClickedEvent -> {
                applicationScope.launch(dispatchers.io) {
                    _actionsSharedFlow.emit(ActionBarServiceActions.OnDisableSelfAction)
                }
            }
            is OnActionDownTouchEvent -> {
                Timber.i(
                    """onActionDown 
                    |uiEvent.x: ${uiEvent.x}, 
                    |uiEvent.y: ${uiEvent.y}, 
                    |uiEvent.rawX: ${uiEvent.rawX}, 
                    |uiEvent.rawY: ${uiEvent.rawY}""".trimMargin()
                )

                _dragStateFlow.value = dragStateFlow.value.copy(
                    initialX = uiEvent.x,
                    initialY = uiEvent.y,
                    initialTouchX = uiEvent.rawX,
                    initialTouchY = uiEvent.rawY
                )
            }
            is OnActionMoveTouchEvent -> {
                val dragState = dragStateFlow.value
                val newX = dragState.initialX + (uiEvent.rawX - dragState.initialTouchX).toInt()
                val newY = dragState.initialY + (uiEvent.rawY - dragState.initialTouchY).toInt()

                _dragStateFlow.value = dragState.copy(
                    x = newX,
                    y = newY
                )
            }
        }
    }
}

data class DragState(
    val initialX: Int = 0,
    val initialY: Int = 0,
    val initialTouchX: Float = 0f,
    val initialTouchY: Float = 0f,
    val x: Int = 0,
    val y: Int = 0
)
