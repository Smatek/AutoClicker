package pl.skolimowski.autoclicker.ui.action_bar

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Before
import org.junit.Test
import pl.skolimowski.autoclicker.MyApp
import pl.skolimowski.autoclicker.test_util.StandardTestDispatcher
import pl.skolimowski.autoclicker.test_util.TestDispatchers
import pl.skolimowski.autoclicker.test_util.UnconfinedTestDispatcher
import pl.skolimowski.autoclicker.ui.action_bar.ActionBarServiceActions.*
import pl.skolimowski.autoclicker.ui.action_bar.ActionBarServiceEvents.*

@OptIn(ExperimentalCoroutinesApi::class)
class ActionBarServiceViewModelTest {
    private val applicationMock: MyApp = mockk(relaxed = true)

    lateinit var viewModel: ActionBarServiceViewModel

    @Before
    fun setUp() {
        every { applicationMock.applicationScope } returns TestScope()

        viewModel = createViewModel()
    }

    private fun createViewModel(
        testDispatchers: TestDispatchers = UnconfinedTestDispatcher()
    ): ActionBarServiceViewModel {
        return ActionBarServiceViewModel(
            dispatchers = testDispatchers,
            myApp = applicationMock
        )
    }

    @Test
    fun init_collectMacroStateChanges_isPlaying() = runTest {
        val scheduler = TestCoroutineScheduler()
        val testDispatchers = StandardTestDispatcher(scheduler = scheduler)

        every { applicationMock.applicationScope } returns TestScope(scheduler)

        viewModel = createViewModel(testDispatchers = testDispatchers)

        viewModel.onUiEvent(OnInitialScreenSizeEvent(width = 0, height = 0))
        viewModel.onUiEvent(OnConfigImageClickedEvent) // required to initialize tempConfig
        viewModel.onUiEvent(OnCyclesCountTextChangedEvent("1"))

        scheduler.advanceUntilIdle()

        (viewModel.clickPointsStateFlow as MutableStateFlow).value = ClickPointsState(
            list = listOf(
                ClickPoint(index = 0, dragState = DragState(x = 1, y = 2)),
                ClickPoint(index = 0, dragState = DragState(x = 3, y = 4)),
            )
        )

        viewModel.actionsSharedFlow.test {
            viewModel.onUiEvent(OnPlayImageClickedEvent)

            scheduler.runCurrent() // run first coroutine to change macroState
            assertThat(viewModel.actionBarStateFlow.value.isPlaying).isEqualTo(true)

            scheduler.advanceUntilIdle()

            val action1 = awaitItem() as PerformClickAction
            val action2 = awaitItem() as PerformClickAction

            assertThat(action1.x).isEqualTo(1)
            assertThat(action1.y).isEqualTo(2)
            assertThat(action2.x).isEqualTo(3)
            assertThat(action2.y).isEqualTo(4)

            assertThat(viewModel.actionBarStateFlow.value.isPlaying).isEqualTo(false)
        }
    }

    @Test
    fun init_collectMacroStateChanges_isPlaying_multipleCycleCounts() = runTest {
        val scheduler = TestCoroutineScheduler()
        val testDispatchers = StandardTestDispatcher(scheduler = scheduler)

        every { applicationMock.applicationScope } returns TestScope(scheduler)

        viewModel = createViewModel(testDispatchers = testDispatchers)

        viewModel.onUiEvent(OnInitialScreenSizeEvent(width = 0, height = 0))
        viewModel.onUiEvent(OnConfigImageClickedEvent) // required to initialize tempConfig
        viewModel.onUiEvent(OnCyclesCountTextChangedEvent("3"))
        viewModel.onUiEvent(OnSaveConfigClickEvent) // required to initialize tempConfig

        scheduler.advanceUntilIdle()

        (viewModel.clickPointsStateFlow as MutableStateFlow).value = ClickPointsState(
            list = listOf(
                ClickPoint(index = 0, dragState = DragState(x = 1, y = 2))
            )
        )

        viewModel.actionsSharedFlow.test {
            viewModel.onUiEvent(OnPlayImageClickedEvent)

            scheduler.advanceUntilIdle()

            val action1 = awaitItem() as PerformClickAction
            val action2 = awaitItem() as PerformClickAction
            val action3 = awaitItem() as PerformClickAction

            assertThat(action1.x).isEqualTo(1)
            assertThat(action1.y).isEqualTo(2)
            assertThat(action2.x).isEqualTo(1)
            assertThat(action2.y).isEqualTo(2)
            assertThat(action3.x).isEqualTo(1)
            assertThat(action3.y).isEqualTo(2)
        }
    }

    @Test
    fun init_collectMacroStateChanges_pauseDuringRun() = runTest {
        val scheduler = TestCoroutineScheduler()
        val testDispatchers = StandardTestDispatcher(scheduler = scheduler)

        every { applicationMock.applicationScope } returns TestScope(scheduler)

        viewModel = createViewModel(testDispatchers = testDispatchers)

        viewModel.onUiEvent(OnInitialScreenSizeEvent(width = 0, height = 0))
        (viewModel.clickPointsStateFlow as MutableStateFlow).value = ClickPointsState(
            list = listOf(
                ClickPoint(index = 0, dragState = DragState(x = 1, y = 2)),
                ClickPoint(index = 0, dragState = DragState(x = 3, y = 4)),
            )
        )

        viewModel.actionsSharedFlow.test {
            viewModel.onUiEvent(OnPlayImageClickedEvent)

            scheduler.runCurrent()

            val action1 = awaitItem() as PerformClickAction

            viewModel.onUiEvent(OnPauseImageClickedEvent)

            scheduler.runCurrent()

            assertThat(action1.x).isEqualTo(1)
            assertThat(action1.y).isEqualTo(2)
        }
    }

    @Test
    fun onUiEvent_OnInitialScreenSizeEvent() = runTest {
        viewModel.onUiEvent(OnInitialScreenSizeEvent(width = 1, height = 2))

        assertThat(viewModel.viewSizes.screenWidth).isEqualTo(1)
        assertThat(viewModel.viewSizes.screenHeight).isEqualTo(2)
    }

    @Test
    fun onUiEvent_OnPlayImageClickedEvent() = runTest {
        // use standard test dispatcher to prevent macro from actually running, finishing and
        // setting isPlaying to false
        val scheduler = TestCoroutineScheduler()
        val testDispatchers = StandardTestDispatcher(scheduler = scheduler)
        every { applicationMock.applicationScope } returns TestScope(scheduler)
        viewModel = createViewModel(testDispatchers = testDispatchers)

        viewModel.onUiEvent(OnPlayImageClickedEvent)

        viewModel.macroStateFlow.test {
            val state = awaitItem()

            assertThat(state.isPlaying).isEqualTo(true)
        }
    }

    @Test
    fun onUiEvent_OnPauseImageClickedEvent() = runTest {
        viewModel.onUiEvent(OnPauseImageClickedEvent)

        viewModel.macroStateFlow.test {
            val state = awaitItem()

            assertThat(state.isPlaying).isEqualTo(false)
        }
    }

    @Test
    fun onUiEvent_OnAddImageClickedEvent() = runTest {
        viewModel.onUiEvent(OnAddImageClickedEvent)

        viewModel.clickPointsStateFlow.test {
            val state = awaitItem()

            assertThat(state.list.size).isEqualTo(1)
            assertThat(state.list[0].index).isEqualTo(1)
        }
    }

    @Test
    fun onUiEvent_OnAddImageClickedEvent_alreadyAddedClickPoints() = runTest {
        (viewModel.clickPointsStateFlow as MutableStateFlow).value = ClickPointsState(
            listOf(
                ClickPoint(index = 1)
            )
        )

        viewModel.onUiEvent(OnAddImageClickedEvent)

        viewModel.clickPointsStateFlow.test {
            val state = awaitItem()

            assertThat(state.list.size).isEqualTo(2)
            assertThat(state.list[1].index).isEqualTo(2)
        }
    }

    @Test
    fun onUiEvent_OnConfigImageClickedEvent_initialMacroConfig() = runTest {
        viewModel.actionsSharedFlow.test {
            viewModel.onUiEvent(OnConfigImageClickedEvent)

            val action = awaitItem()

            assertThat(action).isInstanceOf(ShowConfigDialog::class.java)
            assertThat((action as ShowConfigDialog).macroConfig).isEqualTo(MacroConfig())
        }
    }

    @Test
    fun onUiEvent_OnConfigImageClickedEvent_alreadyChangedMacro() = runTest {
        viewModel.onUiEvent(OnConfigImageClickedEvent)
        viewModel.onUiEvent(OnInfiniteRadioButtonCheckedEvent)
        viewModel.onUiEvent(OnSaveConfigClickEvent)

        viewModel.actionsSharedFlow.test {
            viewModel.onUiEvent(OnConfigImageClickedEvent)

            val action = awaitItem()

            assertThat(action).isInstanceOf(ShowConfigDialog::class.java)
            assertThat((action as ShowConfigDialog).macroConfig).isEqualTo(MacroConfig(cycleMode = CycleMode.INFINITE))
        }
    }

    @Test
    fun onUiEvent_OnRemoveImageClickedEvent() = runTest {
        (viewModel.clickPointsStateFlow as MutableStateFlow).value = ClickPointsState(
            listOf(
                ClickPoint(index = 1)
            )
        )

        viewModel.onUiEvent(OnRemoveImageClickedEvent)

        viewModel.clickPointsStateFlow.test {
            val state = awaitItem()

            assertThat(state.list.size).isEqualTo(0)
        }
    }

    @Test
    fun onUiEvent_OnRemoveImageClickedEvent_emptyList() = runTest {
        (viewModel.clickPointsStateFlow as MutableStateFlow).value = ClickPointsState(emptyList())

        viewModel.onUiEvent(OnRemoveImageClickedEvent)

        viewModel.clickPointsStateFlow.test {
            val state = awaitItem()

            assertThat(state.list.size).isEqualTo(0)
        }
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
                    rawX = -8f,
                    rawY = 0f
                )
            )
        )

        viewModel.actionBarStateFlow.test {
            val state = awaitItem()

            assertThat(state.dragState.x).isEqualTo(-5)
        }
    }

    @Test
    fun onUiEvent_OnActionBarActionMoveTouchEvent_x_outsideRightBound() = runTest {
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

            assertThat(state.dragState.x).isEqualTo(5)
        }
    }

    @Test
    fun onUiEvent_OnActionBarActionMoveTouchEvent_y() = runTest {
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
                    rawY = -8f
                )
            )
        )

        viewModel.actionBarStateFlow.test {
            val state = awaitItem()

            assertThat(state.dragState.y).isEqualTo(-5)
        }
    }

    @Test
    fun onUiEvent_OnActionBarActionMoveTouchEvent_y_outsideBottomBound() = runTest {
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

            assertThat(state.dragState.y).isEqualTo(5)
        }
    }

    @Test
    fun onUiEvent_OnClickPointClickEvent() = runTest {
        viewModel.onUiEvent(OnAddImageClickedEvent)

        viewModel.actionsSharedFlow.test {
            viewModel.onUiEvent(OnClickPointClickEvent(index = 1))

            val action = awaitItem()

            assertThat(action).isInstanceOf(ShowClickPointConfigDialogAction::class.java)
            assertThat((action as ShowClickPointConfigDialogAction).clickPointConfigState)
                .isEqualTo(
                    ClickPointConfigState(
                        delay = "1000",
                        clickPoint = ClickPoint(index = 1)
                    )
                )
        }
    }

    @Test
    fun onUiEvent_OnDelayTextChangedEvent() = runTest {
        viewModel.onUiEvent(OnAddImageClickedEvent)
        viewModel.onUiEvent(OnClickPointClickEvent(index = 1))

        viewModel.actionsSharedFlow.test {
            viewModel.onUiEvent(OnDelayTextChangedEvent(text = "100"))

            val action = awaitItem()

            assertThat(action).isInstanceOf(UpdateClickPointConfigDialogAction::class.java)
            assertThat((action as UpdateClickPointConfigDialogAction).clickPointConfigState.delay)
                .isEqualTo("100")
        }
    }

    @Test
    fun onUiEvent_OnCancelClickPointConfigClickEvent() = runTest {
        viewModel.actionsSharedFlow.test {
            viewModel.onUiEvent(OnCancelClickPointConfigClickEvent)

            val action = awaitItem()

            assertThat(action).isInstanceOf(DismissClickPointConfigDialogAction::class.java)
        }
    }

    @Test
    fun onUiEvent_OnSaveClickPointConfigClickEvent_isValid_true() = runTest {
        viewModel.onUiEvent(OnAddImageClickedEvent)
        viewModel.onUiEvent(OnClickPointClickEvent(index = 1))
        viewModel.onUiEvent(OnDelayTextChangedEvent(text = "100"))

        viewModel.actionsSharedFlow.test {
            viewModel.onUiEvent(OnSaveClickPointConfigClickEvent)

            viewModel.clickPointsStateFlow.test {
                val clickPointsState = awaitItem()

                assertThat(clickPointsState.list[0].delay).isEqualTo(100L)
            }

            val action = awaitItem()

            assertThat(action).isInstanceOf(DismissClickPointConfigDialogAction::class.java)
        }
    }

    @Test
    fun onUiEvent_OnSaveClickPointConfigClickEvent_isValid_false() = runTest {
        viewModel.onUiEvent(OnAddImageClickedEvent)
        viewModel.onUiEvent(OnClickPointClickEvent(index = 1))
        viewModel.onUiEvent(OnDelayTextChangedEvent(text = "0"))

        viewModel.actionsSharedFlow.test {
            viewModel.onUiEvent(OnSaveClickPointConfigClickEvent)

            expectNoEvents()
        }
    }

    @Test
    fun onUiEvent_OnClickPointActionDownTouchEvent() = runTest {
        (viewModel.clickPointsStateFlow as MutableStateFlow).value = ClickPointsState(
            list = listOf(ClickPoint(index = 1))
        )

        viewModel.onUiEvent(
            OnClickPointActionDownTouchEvent(
                index = 1,
                actionDown = DragEvents.ActionDown(
                    x = 1,
                    y = 2,
                    rawX = 3f,
                    rawY = 4f
                )
            )
        )

        viewModel.clickPointsStateFlow.test {
            val state = awaitItem()
            val dragState = state.list[0].dragState

            assertThat(dragState.initialX).isEqualTo(1)
            assertThat(dragState.initialY).isEqualTo(2)
            assertThat(dragState.initialTouchX).isEqualTo(3f)
            assertThat(dragState.initialTouchY).isEqualTo(4f)
        }
    }

    @Test
    fun onUiEvent_OnClickPointActionMoveTouchEvent_x() = runTest {
        (viewModel.clickPointsStateFlow as MutableStateFlow).value = ClickPointsState(
            listOf(
                ClickPoint(
                    index = 1,
                    dragState = DragState(
                        initialX = 1,
                        initialTouchX = 2f
                    )
                )
            )
        )

        viewModel.onUiEvent(OnInitialScreenSizeEvent(10, 10))

        viewModel.onUiEvent(
            OnClickPointActionMoveTouchEvent(
                index = 1,
                actionMove = DragEvents.ActionMove(
                    rawX = 3f,
                    rawY = 0f
                )
            )
        )

        viewModel.clickPointsStateFlow.test {
            val state = awaitItem()

            assertThat(state.list[0].dragState.x).isEqualTo(2)
        }
    }

    @Test
    fun onUiEvent_OnClickPointActionMoveTouchEvent_x_outsideLeftBound() = runTest {
        viewModel = createViewModel()

        (viewModel.clickPointsStateFlow as MutableStateFlow).value = ClickPointsState(
            listOf(
                ClickPoint(
                    index = 1,
                    dragState = DragState(
                        initialX = 1,
                        initialTouchX = 2f
                    )
                )
            )
        )

        viewModel.onUiEvent(OnInitialScreenSizeEvent(10, 10))

        viewModel.onUiEvent(
            OnClickPointActionMoveTouchEvent(
                index = 1,
                actionMove = DragEvents.ActionMove(
                    rawX = -8f,
                    rawY = 0f
                )
            )
        )

        viewModel.clickPointsStateFlow.test {
            val state = awaitItem()

            assertThat(state.list[0].dragState.x).isEqualTo(-5)
        }
    }

    @Test
    fun onUiEvent_OnClickPointActionMoveTouchEvent_x_outsideRightBound() = runTest {
        viewModel = createViewModel()

        (viewModel.clickPointsStateFlow as MutableStateFlow).value = ClickPointsState(
            listOf(
                ClickPoint(
                    index = 1,
                    dragState = DragState(
                        initialX = 1,
                        initialTouchX = 2f
                    )
                )
            )
        )

        viewModel.onUiEvent(OnInitialScreenSizeEvent(10, 10))

        viewModel.onUiEvent(
            OnClickPointActionMoveTouchEvent(
                index = 1,
                actionMove = DragEvents.ActionMove(
                    rawX = 7f,
                    rawY = 0f
                )
            )
        )

        viewModel.clickPointsStateFlow.test {
            val state = awaitItem()

            assertThat(state.list[0].dragState.x).isEqualTo(5)
        }
    }

    @Test
    fun onUiEvent_OnClickPointActionMoveTouchEvent_y() = runTest {
        (viewModel.clickPointsStateFlow as MutableStateFlow).value = ClickPointsState(
            listOf(
                ClickPoint(
                    index = 1,
                    dragState = DragState(
                        initialY = 1,
                        initialTouchY = 2f
                    )
                )
            )
        )

        viewModel.onUiEvent(OnInitialScreenSizeEvent(10, 10))

        viewModel.onUiEvent(
            OnClickPointActionMoveTouchEvent(
                index = 1,
                actionMove = DragEvents.ActionMove(
                    rawX = 0f,
                    rawY = 3f
                )
            )
        )

        viewModel.clickPointsStateFlow.test {
            val state = awaitItem()

            assertThat(state.list[0].dragState.y).isEqualTo(2)
        }
    }

    @Test
    fun onUiEvent_OnClickPointActionMoveTouchEvent_y_outsideTopBound() = runTest {
        viewModel = createViewModel()

        (viewModel.clickPointsStateFlow as MutableStateFlow).value = ClickPointsState(
            listOf(
                ClickPoint(
                    index = 1,
                    dragState = DragState(
                        initialY = 1,
                        initialTouchY = 2f
                    )
                )
            )
        )

        viewModel.onUiEvent(OnInitialScreenSizeEvent(10, 10))

        viewModel.onUiEvent(
            OnClickPointActionMoveTouchEvent(
                index = 1,
                actionMove = DragEvents.ActionMove(
                    rawX = 0f,
                    rawY = -8f
                )
            )
        )

        viewModel.clickPointsStateFlow.test {
            val state = awaitItem()

            assertThat(state.list[0].dragState.y).isEqualTo(-5)
        }
    }

    @Test
    fun onUiEvent_OnClickPointActionMoveTouchEvent_y_outsideUpperBound() = runTest {
        viewModel = createViewModel()

        (viewModel.clickPointsStateFlow as MutableStateFlow).value = ClickPointsState(
            listOf(
                ClickPoint(
                    index = 1,
                    dragState = DragState(
                        initialY = 1,
                        initialTouchY = 2f
                    )
                )
            )
        )

        viewModel.onUiEvent(OnInitialScreenSizeEvent(10, 10))

        viewModel.onUiEvent(
            OnClickPointActionMoveTouchEvent(
                index = 1,
                actionMove = DragEvents.ActionMove(
                    rawX = 0f,
                    rawY = 7f
                )
            )
        )

        viewModel.clickPointsStateFlow.test {
            val state = awaitItem()

            assertThat(state.list[0].dragState.y).isEqualTo(5)
        }
    }

    @Test
    fun onUiEvent_OnCyclesCountTextChangedEvent() = runTest {
        viewModel.onUiEvent(OnConfigImageClickedEvent) // required to initialize tempConfig

        viewModel.actionsSharedFlow.test {
            viewModel.onUiEvent(OnCyclesCountTextChangedEvent("123"))

            val action = awaitItem()

            assertThat(action).isInstanceOf(UpdateConfigDialog::class.java)
            assertThat((action as UpdateConfigDialog).macroConfig.cycles).isEqualTo(123)
        }
    }

    @Test
    fun onUiEvent_OnInfiniteRadioButtonCheckedEvent() = runTest {
        viewModel.onUiEvent(OnConfigImageClickedEvent) // required to initialize tempConfig

        viewModel.actionsSharedFlow.test {
            viewModel.onUiEvent(OnInfiniteRadioButtonCheckedEvent)

            val action = awaitItem()

            assertThat(action).isInstanceOf(UpdateConfigDialog::class.java)
            assertThat((action as UpdateConfigDialog).macroConfig.cycleMode).isEqualTo(CycleMode.INFINITE)
        }
    }

    @Test
    fun onUiEvent_OnCyclesCountRadioButtonCheckedEvent() = runTest {
        viewModel.onUiEvent(OnConfigImageClickedEvent) // required to initialize tempConfig

        viewModel.actionsSharedFlow.test {
            viewModel.onUiEvent(OnCyclesCountRadioButtonCheckedEvent)

            val action = awaitItem()

            assertThat(action).isInstanceOf(UpdateConfigDialog::class.java)
            assertThat((action as UpdateConfigDialog).macroConfig.cycleMode).isEqualTo(CycleMode.CYCLES_COUNT)
        }
    }

    @Test
    fun onUiEvent_OnSaveConfigClickEvent_isValid_true_infiniteMode() = runTest {
        // fixme below test could be better by adding spy and checking if macroConfig changed but
        //  there is problem with mockk library https://github.com/mockk/mockk/issues/564
//        val viewModelSpy = spyk(viewModel, recordPrivateCalls = true)

        viewModel.onUiEvent(OnConfigImageClickedEvent)
        viewModel.onUiEvent(OnInfiniteRadioButtonCheckedEvent)

//        viewModelSpy.actionsSharedFlow.test {
        viewModel.actionsSharedFlow.test {
//            every { viewModelSpy getProperty "macroConfig" } returns MacroConfig(cycleMode = CycleMode.INFINITE)

//            viewModelSpy.onUiEvent(OnSaveConfigClickEvent)
            viewModel.onUiEvent(OnSaveConfigClickEvent)

            val action = awaitItem()

            assertThat(action).isInstanceOf(DismissConfigDialog::class.java)
        }
    }

    @Test
    fun onUiEvent_OnSaveConfigClickEvent_isValid_true_cyclesCountMode() = runTest {
        // fixme below test could be better by adding spy and checking if macroConfig changed but
        //  there is problem with mockk library https://github.com/mockk/mockk/issues/564
//        val viewModelSpy = spyk(viewModel, recordPrivateCalls = true)

        viewModel.onUiEvent(OnConfigImageClickedEvent)

//        viewModelSpy.actionsSharedFlow.test {
        viewModel.actionsSharedFlow.test {
//            every { viewModelSpy getProperty "macroConfig" } returns MacroConfig(
//                cycleMode = CycleMode.CYCLES_COUNT,
//                cyclesText = "1"
//            )

//            viewModelSpy.onUiEvent(OnSaveConfigClickEvent)
            viewModel.onUiEvent(OnSaveConfigClickEvent)

            val action = awaitItem()

            assertThat(action).isInstanceOf(DismissConfigDialog::class.java)
        }
    }

    @Test
    fun onUiEvent_OnSaveConfigClickEvent_isValid_false() = runTest {
        // fixme below test could be better by adding spy and checking if macroConfig changed but
        //  there is problem with mockk library https://github.com/mockk/mockk/issues/564
//        val viewModelSpy = spyk(viewModel, recordPrivateCalls = true)

        viewModel.onUiEvent(OnConfigImageClickedEvent)
        viewModel.onUiEvent(OnCyclesCountTextChangedEvent("0"))

//        viewModelSpy.actionsSharedFlow.test {
        viewModel.actionsSharedFlow.test {
//            every { viewModelSpy getProperty "macroConfig" } returns MacroConfig(
//                cycleMode = CycleMode.CYCLES_COUNT,
//                cyclesText = "0"
//            )

//            viewModelSpy.onUiEvent(OnSaveConfigClickEvent)
            viewModel.onUiEvent(OnSaveConfigClickEvent)

            expectNoEvents()
        }
    }

    @Test
    fun onUiEvent_OnCancelConfigClickEvent() = runTest {
        viewModel.actionsSharedFlow.test {
            viewModel.onUiEvent(OnCancelConfigClickEvent)

            val action = awaitItem()

            assertThat(action).isInstanceOf(DismissConfigDialog::class.java)
        }
    }
}