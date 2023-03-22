package pl.skolimowski.autoclicker.ui.action_bar

import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import pl.skolimowski.autoclicker.MyApp
import pl.skolimowski.autoclicker.R
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

    private val _actionBarStateFlow = MutableStateFlow(ActionBarState())
    val actionBarStateFlow: StateFlow<ActionBarState> = _actionBarStateFlow

    private val _clickPointsStateFlow = MutableStateFlow(ClickPointsState())
    val clickPointsStateFlow: StateFlow<ClickPointsState> = _clickPointsStateFlow

    private val _macroStateFlow = MutableStateFlow(MacroState())
    val macroStateFlow: StateFlow<MacroState> = _macroStateFlow

    private val _actionsSharedFlow = MutableSharedFlow<ActionBarServiceActions>()
    val actionsSharedFlow: SharedFlow<ActionBarServiceActions> = _actionsSharedFlow

    private val macroPlayer = MacroPlayer()

    var viewSizes: ViewSizes = ViewSizes()

    init {
        viewSizes = viewSizes.copy(
            clickPointSize = myApp.resources.getDimensionPixelSize(R.dimen.click_point_size)
        )

        collectMacroStateChanges()
    }

    fun onUiEvent(uiEvent: UiEvent) {
        Timber.i("uiEvent: $uiEvent")

        when (uiEvent) {
            is OnInitialScreenSizeEvent -> {
                viewSizes = viewSizes.copy(
                    screenWidth = uiEvent.width, screenHeight = uiEvent.height
                )
            }
            is OnPlayImageClickedEvent -> {
                _macroStateFlow.value = macroStateFlow.value.copy(isPlaying = true)
            }
            is OnPauseImageClickedEvent -> {
                _macroStateFlow.value = macroStateFlow.value.copy(isPlaying = false)
            }
            is OnAddImageClickedEvent -> {
                val newClickPoint = clickPointsStateFlow.value.createNewClickPoint()
                val newList = clickPointsStateFlow.value.list.toMutableList()
                newList.add(newClickPoint)

                _clickPointsStateFlow.value = clickPointsStateFlow.value.copy(list = newList)
            }
            is OnRemoveImageClickedEvent -> {
                val newList = clickPointsStateFlow.value.list.toMutableList()
                newList.removeLastOrNull()

                _clickPointsStateFlow.value = clickPointsStateFlow.value.copy(list = newList)
            }
            is OnCloseImageClickedEvent -> {
                applicationScope.launch(dispatchers.io) {
                    _actionsSharedFlow.emit(ActionBarServiceActions.OnDisableSelfAction)
                }
            }
            is OnActionBarActionDownTouchEvent -> {
                val actionDown = uiEvent.actionDown

                _actionBarStateFlow.value = actionBarStateFlow.value.copy(
                    dragState = actionBarStateFlow.value.onActionDown(actionDown)
                )
            }
            is OnActionBarActionMoveTouchEvent -> {
                val actionMove = uiEvent.actionMove

                _actionBarStateFlow.value = actionBarStateFlow.value.copy(
                    dragState = actionBarStateFlow.value.onActionMove(actionMove, viewSizes)
                )
            }
            is OnClickPointClickEvent -> {
            }
            is OnClickPointActionDownTouchEvent -> {
                clickPointsStateFlow.value.list.find { it.index == uiEvent.index }
                    ?.let { clickPoint ->
                        val actionDown = uiEvent.actionDown
                        val updatedClickPoint = clickPoint.copy(
                            dragState = clickPoint.onActionDown(actionDown)
                        )

                        updateClickPoint(updatedClickPoint)
                    }
            }
            is OnClickPointActionMoveTouchEvent -> {
                clickPointsStateFlow.value.list.find { it.index == uiEvent.index }
                    ?.let { clickPoint ->
                        val actionMove = uiEvent.actionMove
                        val updatedClickPoint = clickPoint.copy(
                            dragState = clickPoint.onActionMove(actionMove, viewSizes)
                        )

                        updateClickPoint(updatedClickPoint)
                    }
            }
        }
    }

    private fun collectMacroStateChanges() {
        applicationScope.launch(dispatchers.io) {
            macroStateFlow.collectLatest {
                Timber.i("macroState changed to: $it")

                if (it.isPlaying) {
                    macroPlayer.play()
                } else {
                    macroPlayer.pause()
                }
            }
        }
    }

    private fun updateClickPoint(updatedClickPoint: ClickPoint) {
        Timber.i("updateClickPoint: $updatedClickPoint")
        val newList = clickPointsStateFlow.value.list.toMutableList()
        val indexOfFirst = newList.indexOfFirst { it.index == updatedClickPoint.index }
        newList[indexOfFirst] = updatedClickPoint

        _clickPointsStateFlow.value = clickPointsStateFlow.value.copy(
            list = newList
        )
    }

    private suspend fun performClick(clickPoint: ClickPoint) {
        val x = clickPoint.dragState.x + viewSizes.screenWidth / 2
        val y = clickPoint.dragState.y + viewSizes.screenHeight / 2

        Timber.i("perform click at $x:$y")

        _actionsSharedFlow.emit(ActionBarServiceActions.PerformClickAction(x, y))
    }

    inner class MacroPlayer {
        var job: Job? = null

        fun play() {
            job = applicationScope.launch(dispatchers.io) {
                val list = clickPointsStateFlow.value.list
                list.forEach {
                    performClick(it)

                    delay(3000L)
                }

                finish()
            }
        }

        fun pause() {
            job?.cancel()
        }

        private fun finish() {
            _macroStateFlow.value = macroStateFlow.value.copy(isPlaying = false)
        }
    }
}

data class MacroState(
    val isPlaying: Boolean = false
)

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
    override val dragState: DragState = DragState()
) : Draggable()

data class ActionBarState(
    override val dragState: DragState = DragState()
) : Draggable()

abstract class Draggable {
    abstract val dragState: DragState

    fun onActionDown(actionDown: DragEvents.ActionDown): DragState {
        return dragState.copy(
            initialX = actionDown.x,
            initialY = actionDown.y,
            initialTouchX = actionDown.rawX,
            initialTouchY = actionDown.rawY
        )
    }

    fun onActionMove(actionMove: DragEvents.ActionMove, viewSizes: ViewSizes): DragState {
        val newX = dragState.initialX + (actionMove.rawX - dragState.initialTouchX).toInt()
        val newY = dragState.initialY + (actionMove.rawY - dragState.initialTouchY).toInt()

        // fixme this method is used also on actionBar, so if its viewSize is different than clickPoint then this will be bugged.
        //   this needs to be fixed
        val halfOfScreenWidth = viewSizes.screenWidth / 2 - viewSizes.clickPointSize / 2
        val halfOfScreenHeight = viewSizes.screenHeight / 2 - viewSizes.clickPointSize / 2

        val newXAdjustedToBounds = getValueInsideBounds(newX, -halfOfScreenWidth, halfOfScreenWidth)
        val newYAdjustedToBounds =
            getValueInsideBounds(newY, -halfOfScreenHeight, halfOfScreenHeight)

        return dragState.copy(
            x = newXAdjustedToBounds,
            y = newYAdjustedToBounds
        )
    }

    private fun getValueInsideBounds(value: Int, min: Int, max: Int): Int {
        return when {
            value < min -> min
            value > max -> max
            else -> value
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

data class ViewSizes(
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    val clickPointSize: Int = 0
)
