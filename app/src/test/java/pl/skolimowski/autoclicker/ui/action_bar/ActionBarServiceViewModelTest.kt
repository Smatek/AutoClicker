package pl.skolimowski.autoclicker.ui.action_bar

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Before
import org.junit.Test
import pl.skolimowski.autoclicker.MyApp
import pl.skolimowski.autoclicker.R
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

    @Test
    fun onUiEvent_OnActionBarActionDownTouchEvent() = runTest {
        viewModel.onUiEvent(
            OnActionBarActionDownTouchEvent(
                actionDown = DragEvents.ActionDown(
                    x = 1,
                    y = 2,
                    rawX = 3f,
                    rawY = 4f
                )
            )
        )

        viewModel.actionBarStateFlow.test {
            val state = awaitItem()

            assertThat(state.dragState.initialX).isEqualTo(1)
            assertThat(state.dragState.initialY).isEqualTo(2)
            assertThat(state.dragState.initialTouchX).isEqualTo(3f)
            assertThat(state.dragState.initialTouchY).isEqualTo(4f)
        }
    }

    @Test
    fun onUiEvent_OnActionBarActionMoveTouchEvent_x() = runTest {
        every { applicationMock.resources.getDimensionPixelSize(R.dimen.click_point_size) } returns 2

        (viewModel.actionBarStateFlow as MutableStateFlow).value = ActionBarState(
            dragState = DragState(
                initialX = 1,
                initialTouchX = 2f
            )
        )

        viewModel.onUiEvent(OnInitialScreenSizeEvent(10, 10))

        viewModel.onUiEvent(
            OnActionBarActionMoveTouchEvent(
                actionMove = DragEvents.ActionMove(
                    rawX = 3f,
                    rawY = 0f
                )
            )
        )

        viewModel.actionBarStateFlow.test {
            val state = awaitItem()

            assertThat(state.dragState.x).isEqualTo(2)
        }
    }

    @Test
    fun onUiEvent_OnActionBarActionMoveTouchEvent_x_outsideLeftBound() = runTest {
        every { applicationMock.resources.getDimensionPixelSize(R.dimen.click_point_size) } returns 2

        viewModel = createViewModel()

        (viewModel.actionBarStateFlow as MutableStateFlow).value = ActionBarState(
            dragState = DragState(
                initialX = 1,
                initialTouchX = 2f
            )
        )

        viewModel.onUiEvent(OnInitialScreenSizeEvent(10, 10))

        viewModel.onUiEvent(
            OnActionBarActionMoveTouchEvent(
                actionMove = DragEvents.ActionMove(
                    rawX = -4f,
                    rawY = 0f
                )
            )
        )

        viewModel.actionBarStateFlow.test {
            val state = awaitItem()

            assertThat(state.dragState.x).isEqualTo(-4)
        }
    }

    @Test
    fun onUiEvent_OnActionBarActionMoveTouchEvent_x_outsideRightBound() = runTest {
        every { applicationMock.resources.getDimensionPixelSize(R.dimen.click_point_size) } returns 2

        viewModel = createViewModel()

        (viewModel.actionBarStateFlow as MutableStateFlow).value = ActionBarState(
            dragState = DragState(
                initialX = 1,
                initialTouchX = 2f
            )
        )

        viewModel.onUiEvent(OnInitialScreenSizeEvent(10, 10))

        viewModel.onUiEvent(
            OnActionBarActionMoveTouchEvent(
                actionMove = DragEvents.ActionMove(
                    rawX = 7f,
                    rawY = 0f
                )
            )
        )

        viewModel.actionBarStateFlow.test {
            val state = awaitItem()

            assertThat(state.dragState.x).isEqualTo(4)
        }
    }

    @Test
    fun onUiEvent_OnActionBarActionMoveTouchEvent_y() = runTest {
        every { applicationMock.resources.getDimensionPixelSize(R.dimen.click_point_size) } returns 2

        (viewModel.actionBarStateFlow as MutableStateFlow).value = ActionBarState(
            dragState = DragState(
                initialY = 1,
                initialTouchY = 2f
            )
        )

        viewModel.onUiEvent(OnInitialScreenSizeEvent(10, 10))

        viewModel.onUiEvent(
            OnActionBarActionMoveTouchEvent(
                actionMove = DragEvents.ActionMove(
                    rawX = 0f,
                    rawY = 3f
                )
            )
        )

        viewModel.actionBarStateFlow.test {
            val state = awaitItem()

            assertThat(state.dragState.y).isEqualTo(2)
        }
    }

    @Test
    fun onUiEvent_OnActionBarActionMoveTouchEvent_y_outsideTopBound() = runTest {
        every { applicationMock.resources.getDimensionPixelSize(R.dimen.click_point_size) } returns 2

        viewModel = createViewModel()

        (viewModel.actionBarStateFlow as MutableStateFlow).value = ActionBarState(
            dragState = DragState(
                initialY = 1,
                initialTouchY = 2f
            )
        )

        viewModel.onUiEvent(OnInitialScreenSizeEvent(10, 10))

        viewModel.onUiEvent(
            OnActionBarActionMoveTouchEvent(
                actionMove = DragEvents.ActionMove(
                    rawX = 0f,
                    rawY = -4f
                )
            )
        )

        viewModel.actionBarStateFlow.test {
            val state = awaitItem()

            assertThat(state.dragState.y).isEqualTo(-4)
        }
    }

    @Test
    fun onUiEvent_OnActionBarActionMoveTouchEvent_y_outsideBottomBound() = runTest {
        every { applicationMock.resources.getDimensionPixelSize(R.dimen.click_point_size) } returns 2

        viewModel = createViewModel()

        (viewModel.actionBarStateFlow as MutableStateFlow).value = ActionBarState(
            dragState = DragState(
                initialY = 1,
                initialTouchY = 2f
            )
        )

        viewModel.onUiEvent(OnInitialScreenSizeEvent(10, 10))

        viewModel.onUiEvent(
            OnActionBarActionMoveTouchEvent(
                actionMove = DragEvents.ActionMove(
                    rawX = 0f,
                    rawY = 7f
                )
            )
        )

        viewModel.actionBarStateFlow.test {
            val state = awaitItem()

            assertThat(state.dragState.y).isEqualTo(4)
        }
    }

    @Test
    fun onUiEvent_OnClickPointActionDownTouchEvent() = runTest {
        // todo
    }

    @Test
    fun onUiEvent_OnClickPointActionMoveTouchEvent() = runTest {
        // todo
    }

    @Test
    fun onUiEvent_OnAddImageClickedEvent() = runTest {
        // todo
    }

    @Test
    fun onUiEvent_OnRemoveImageClickedEvent() = runTest {
        // todo
    }
}