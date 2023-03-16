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

    private val _clickPointsStateFlow = MutableStateFlow(ClickPointsState())
    val clickPointsStateFlow: StateFlow<ClickPointsState> = _clickPointsStateFlow

    private val _actionsSharedFlow = MutableSharedFlow<ActionBarServiceActions>()
    val actionsSharedFlow: SharedFlow<ActionBarServiceActions> = _actionsSharedFlow

    fun onUiEvent(uiEvent: UiEvent) {
        Timber.i("uiEvent: $uiEvent")

        when (uiEvent) {
            is OnAddImageClickedEvent -> {
                val newClickPoint = clickPointsStateFlow.value.createNewClickPoint()
                val newList = clickPointsStateFlow.value.list.toMutableList()
                newList.add(newClickPoint)

                _clickPointsStateFlow.value = clickPointsStateFlow.value.copy(list = newList)
            }
            is OnCloseImageClickedEvent -> {
                applicationScope.launch(dispatchers.io) {
                    _actionsSharedFlow.emit(ActionBarServiceActions.OnDisableSelfAction)
                }
            }
            is OnActionBarActionDownTouchEvent -> {
                val actionDown = uiEvent.actionDown

                _dragStateFlow.value = dragStateFlow.value.copy(
                    initialX = actionDown.x,
                    initialY = actionDown.y,
                    initialTouchX = actionDown.rawX,
                    initialTouchY = actionDown.rawY
                )
            }
            is OnActionBarActionMoveTouchEvent -> {
                val actionMove = uiEvent.actionMove
                val dragState = dragStateFlow.value
                val newX = dragState.initialX + (actionMove.rawX - dragState.initialTouchX).toInt()
                val newY = dragState.initialY + (actionMove.rawY - dragState.initialTouchY).toInt()

                _dragStateFlow.value = dragState.copy(
                    x = newX,
                    y = newY
                )
            }
            is OnClickPointActionDownTouchEvent -> {
                val actionDown = uiEvent.actionDown

                clickPointsStateFlow.value.list.find { it.index == uiEvent.index }?.let { clickPoint ->
                    val updatedClickPoint = clickPoint.copy(
                        dragState = clickPoint.dragState.copy(
                            initialX = actionDown.x,
                            initialY = actionDown.y,
                            initialTouchX = actionDown.rawX,
                            initialTouchY = actionDown.rawY
                        )
                    )

                    updateClickPoint(updatedClickPoint)
                }
            }
            is OnClickPointActionMoveTouchEvent -> {
                clickPointsStateFlow.value.list.find { it.index == uiEvent.index }?.let { clickPoint ->
                    val actionMove = uiEvent.actionMove
                    val dragState = clickPoint.dragState
                    val newX = dragState.initialX + (actionMove.rawX - dragState.initialTouchX).toInt()
                    val newY = dragState.initialY + (actionMove.rawY - dragState.initialTouchY).toInt()

                    val updatedClickPoint = clickPoint.copy(
                        dragState = clickPoint.dragState.copy(
                            x = newX,
                            y = newY
                        )
                    )

                    updateClickPoint(updatedClickPoint)
                }
            }
        }
    }

    private fun updateClickPoint(updatedClickPoint: ClickPoint) {
        val newList = clickPointsStateFlow.value.list.toMutableList()
        val indexOfFirst = newList.indexOfFirst { it.index == updatedClickPoint.index }
        newList[indexOfFirst] = updatedClickPoint

        _clickPointsStateFlow.value = clickPointsStateFlow.value.copy(
            list = newList
        )
    }
}

data class ClickPointsState(
    val list: List<ClickPoint> = mutableListOf()
) {
    fun createNewClickPoint(): ClickPoint {
        val highestIdValue = list.maxOfOrNull { it.index } ?: 0

        return ClickPoint(highestIdValue + 1)
    }
}

data class ClickPoint(
    val index: Int = 0,
    val dragState: DragState = DragState()
)

data class DragState(
    val initialX: Int = 0,
    val initialY: Int = 0,
    val initialTouchX: Float = 0f,
    val initialTouchY: Float = 0f,
    val x: Int = 0,
    val y: Int = 0
)
