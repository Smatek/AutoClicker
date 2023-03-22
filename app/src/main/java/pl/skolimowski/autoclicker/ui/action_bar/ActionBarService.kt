package pl.skolimowski.autoclicker.ui.action_bar

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.graphics.Path
import android.graphics.PixelFormat
import android.view.*
import android.view.MotionEvent.*
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.skolimowski.autoclicker.MyApp
import pl.skolimowski.autoclicker.R
import pl.skolimowski.autoclicker.ui.UiEvent
import pl.skolimowski.autoclicker.ui.action_bar.ActionBarServiceEvents.*
import pl.skolimowski.autoclicker.ui.action_bar.DragEvents.*
import timber.log.Timber

// AFAIK there is no way to test UI of AccessibilityService.
// There is method to test callbacks like onAccessibilityEvent presented in google samples
// https://android.googlesource.com/platform/cts/+/27a84563508815d6d2652a0dfe17a118daccb2bd/tests/tests/accessibilityservice/src/android/accessibilityservice/cts/AccessibilityEndToEndTest.java
// It's quite troublesome to create additional apk etc. So because it is side project I decided to
// avoid writing this tests. However this class should be created as simple as possible and delegate
// more complex functionality to testable classes.
@AndroidEntryPoint
class ActionBarService : AccessibilityService() {
    @Inject
    lateinit var viewModel: ActionBarServiceViewModel

    @Inject
    lateinit var myApp: MyApp

    private val clickPointViewHolders = mutableListOf<ClickPointViewHolder>()
    private lateinit var viewsContainer: FrameLayout

    lateinit var wm: WindowManager
    lateinit var params: WindowManager.LayoutParams

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        initScreenSize()
        createView()
        setUpView()
        setUpClickPoints()
    }

    override fun onCreate() {
        super.onCreate()

        collectActions()
    }

    override fun onAccessibilityEvent(p0: AccessibilityEvent?) {
        TODO("Not yet implemented")
    }

    override fun onInterrupt() {
        TODO("Not yet implemented")
    }

    private fun initScreenSize() {
        val windowMetrics = wm.currentWindowMetrics
        val windowInsets = windowMetrics.windowInsets
            .getInsetsIgnoringVisibility(0)
        val width = windowMetrics.bounds.width() - windowInsets.left - windowInsets.right
        val height = windowMetrics.bounds.height() - windowInsets.top - windowInsets.bottom
        viewModel.onUiEvent(OnInitialScreenSizeEvent(width = width, height = height))
    }

    private fun createView() {
        viewsContainer = FrameLayout(this)
        val inflater = LayoutInflater.from(this)
        inflater.inflate(R.layout.action_bar, viewsContainer)

        params = createWindowLayoutParams()

        wm.addView(viewsContainer, params)
    }

    private fun setUpClickPoints() {
        myApp.applicationScope.launch {
            viewModel.clickPointsStateFlow.collectLatest { clickPointsState ->
                val clickPointList = clickPointsState.list

                removeOldClickPoints(clickPointList, clickPointViewHolders)
                addNewClickPoints(clickPointList, clickPointViewHolders)
                updateChangedClickPoints(clickPointList, clickPointViewHolders)
            }
        }
    }

    private suspend fun updateChangedClickPoints(
        clickPointList: List<ClickPoint>,
        clickPointViewHolders: MutableList<ClickPointViewHolder>
    ) {
        clickPointList.forEach { clickPoint ->
            clickPointViewHolders.find { clickPointView -> clickPointView.index == clickPoint.index }
                ?.let { clickPointView ->
                    if (clickPoint.dragState.x != clickPointView.params.x || clickPoint.dragState.y != clickPointView.params.y) {
                        clickPointView.params.x = clickPoint.dragState.x
                        clickPointView.params.y = clickPoint.dragState.y

                        withContext(Dispatchers.Main) {
                            wm.updateViewLayout(clickPointView.view, clickPointView.params)
                        }
                    }
                }
                ?: throw IllegalStateException("no click point view with index ${clickPoint.index}")
        }
    }

    private suspend fun addNewClickPoints(
        clickPointList: List<ClickPoint>,
        clickPointViewHolders: MutableList<ClickPointViewHolder>
    ) {
        clickPointList.forEach { clickPoint ->
            val find =
                clickPointViewHolders.find { clickPointView -> clickPointView.index == clickPoint.index }

            if (find == null) {
                withContext(Dispatchers.Main) {
                    val clickPointView = createClickPointView(clickPoint.index)

                    wm.addView(clickPointView.view, clickPointView.params)

                    clickPointViewHolders.add(clickPointView)
                }
            }
        }
    }

    private suspend fun removeOldClickPoints(
        clickPointList: List<ClickPoint>,
        clickPointViewHolders: MutableList<ClickPointViewHolder>
    ) {
        clickPointViewHolders.forEach { clickPointView ->
            val find =
                clickPointList.find { clickPoint -> clickPoint.index == clickPointView.index }

            if (find == null) {
                withContext(Dispatchers.Main) {
                    wm.removeView(clickPointView.view)

                    clickPointViewHolders.remove(clickPointView)
                }
            }
        }
    }

    private fun createClickPointView(index: Int): ClickPointViewHolder {
        val view = FrameLayout(this)
        val inflater = LayoutInflater.from(this)
        inflater.inflate(R.layout.click_point, view)

        val params = createWindowLayoutParams()
        val clickPointViewHolder = ClickPointViewHolder(index = index, view = view, params = params)

        setUpClickPointDragTouchListener(clickPointViewHolder)

        return clickPointViewHolder
    }

    @SuppressLint("ClickableViewAccessibility") // todo check suppress
    // https://stackoverflow.com/a/51361730
    private fun setUpClickPointDragTouchListener(clickPointViewHolder: ClickPointViewHolder) {
        val root = clickPointViewHolder.view.findViewById<FrameLayout>(R.id.root)
        root.setOnTouchListener { _, event ->
            when (event.action) {
                ACTION_DOWN -> {
                    viewModel.onUiEvent(
                        OnClickPointActionDownTouchEvent(
                            index = clickPointViewHolder.index,
                            actionDown = ActionDown(
                                clickPointViewHolder.params.x,
                                clickPointViewHolder.params.y,
                                event.rawX,
                                event.rawY
                            )
                        )
                    )

                    return@setOnTouchListener true
                }
                ACTION_MOVE -> {
                    viewModel.onUiEvent(
                        OnClickPointActionMoveTouchEvent(
                            index = clickPointViewHolder.index,
                            actionMove = ActionMove(event.rawX, event.rawY)
                        )
                    )

                    return@setOnTouchListener true
                }
                else -> {
                    return@setOnTouchListener false
                }
            }
        }
    }

    private fun collectActions() {
        myApp.applicationScope.launch {
            viewModel.actionsSharedFlow.collectLatest {
                when (it) {
                    is ActionBarServiceActions.OnDisableSelfAction -> {
                        disableSelf()
                    }
                    is ActionBarServiceActions.PerformClickAction -> {
                        Timber.i("PerformClickAction x: ${it.x} y: ${it.y}")
                        Timber.i("PerformClickAction x: ${it.x.toFloat()} y: ${it.y.toFloat()}")

                        withContext(Dispatchers.Main) {
                            setClickPointsTouchable(false)

                            performClick(
                                it.x.toFloat(),
                                it.y.toFloat(),
                                addClickPointsGestureCallback
                            )
                        }
                    }
                }
            }
        }
    }

    private fun setClickPointsTouchable(isTouchable: Boolean) {
        clickPointViewHolders.forEach {
            it.setTouchable(isTouchable)

            wm.updateViewLayout(it.view, it.params)
        }
    }

    private fun performClick(x: Float, y: Float, callback: GestureResultCallback) {
        val path = Path()
        path.moveTo(x, y)
        val builder = GestureDescription.Builder()
        // start time is set to 100 so click point has time to change params, todo check minimal start time
        builder.addStroke(GestureDescription.StrokeDescription(path, 100, 1L))
        dispatchGesture(builder.build(), callback, null)
    }

    private fun setUpView() {
        viewsContainer.findViewById<ImageView>(R.id.iv_close).setOnClickListener {
            viewModel.onUiEvent(OnCloseImageClickedEvent)
        }

        viewsContainer.findViewById<ImageView>(R.id.iv_add).setOnClickListener {
            viewModel.onUiEvent(OnAddImageClickedEvent)
        }

        viewsContainer.findViewById<ImageView>(R.id.iv_remove).setOnClickListener {
            viewModel.onUiEvent(OnRemoveImageClickedEvent)
        }

        viewsContainer.findViewById<ImageView>(R.id.iv_play).setOnClickListener {
            viewModel.onUiEvent(OnPlayImageClickedEvent)
        }

        viewsContainer.findViewById<ImageView>(R.id.iv_pause).setOnClickListener {
            viewModel.onUiEvent(OnPauseImageClickedEvent)
        }

        setUpActionBarDrag()
        setUpMacroStateCollector()
    }

    private fun setUpMacroStateCollector() {
        myApp.applicationScope.launch {
            viewModel.macroStateFlow.collectLatest {
                withContext(Dispatchers.Main) {
                    val playImage = viewsContainer.findViewById<ImageView>(R.id.iv_play)
                    val pauseImage = viewsContainer.findViewById<ImageView>(R.id.iv_pause)

                    if (it.isPlaying) {
                        playImage.visibility = View.GONE
                        pauseImage.visibility = View.VISIBLE
                    } else {
                        playImage.visibility = View.VISIBLE
                        pauseImage.visibility = View.GONE
                    }

                    wm.updateViewLayout(viewsContainer, params)
                }
            }
        }
    }

    private fun setUpActionBarDrag() {
        collectActionBarDragState()
        setUpActionBarDragTouchListener()
    }

    @SuppressLint("ClickableViewAccessibility") // todo check suppress
    // https://stackoverflow.com/a/51361730
    private fun setUpActionBarDragTouchListener() {
        val root = viewsContainer.findViewById<LinearLayout>(R.id.root)
        root.setOnTouchListener { view, event ->
            when (event.action) {
                ACTION_DOWN -> {
                    viewModel.onUiEvent(
                        OnActionBarActionDownTouchEvent(
                            actionDown = ActionDown(params.x, params.y, event.rawX, event.rawY)
                        )
                    )

                    return@setOnTouchListener true
                }
                ACTION_MOVE -> {
                    viewModel.onUiEvent(
                        OnActionBarActionMoveTouchEvent(
                            actionMove = ActionMove(event.rawX, event.rawY)
                        )
                    )

                    return@setOnTouchListener true
                }
                else -> {
                    return@setOnTouchListener false
                }
            }
        }
    }

    private fun collectActionBarDragState() {
        myApp.applicationScope.launch {
            viewModel.actionBarStateFlow.collectLatest {
                withContext(Dispatchers.Main) {
                    val dragState = it.dragState
                    params.x = dragState.x
                    params.y = dragState.y

                    wm.updateViewLayout(viewsContainer, params)
                }
            }
        }
    }

    private fun createWindowLayoutParams(): WindowManager.LayoutParams {
        val lp = WindowManager.LayoutParams()

        lp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        lp.format = PixelFormat.TRANSLUCENT
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        lp.width = WindowManager.LayoutParams.WRAP_CONTENT
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT

        return lp
    }

    private val addClickPointsGestureCallback = object : GestureResultCallback() {
        override fun onCancelled(gestureDescription: GestureDescription?) {
            super.onCancelled(gestureDescription)

            setClickPointsTouchable(true)
        }

        override fun onCompleted(gestureDescription: GestureDescription?) {
            super.onCompleted(gestureDescription)

            setClickPointsTouchable(true)
        }
    }
}

data class ClickPointViewHolder(
    val index: Int,
    val view: View,
    val params: WindowManager.LayoutParams
) {
    fun setTouchable(touchable: Boolean) {
        if (touchable) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
    }
}

sealed class ActionBarServiceEvents : UiEvent() {
    object OnPlayImageClickedEvent : ActionBarServiceEvents()
    object OnPauseImageClickedEvent : ActionBarServiceEvents()
    object OnAddImageClickedEvent : ActionBarServiceEvents()
    object OnRemoveImageClickedEvent : ActionBarServiceEvents()
    object OnCloseImageClickedEvent : ActionBarServiceEvents()
    class OnInitialScreenSizeEvent(val width: Int, val height: Int) : ActionBarServiceEvents()
    class OnActionBarActionDownTouchEvent(val actionDown: ActionDown) : ActionBarServiceEvents()
    class OnActionBarActionMoveTouchEvent(val actionMove: ActionMove) : ActionBarServiceEvents()
    class OnClickPointActionDownTouchEvent(val index: Int, val actionDown: ActionDown) :
        ActionBarServiceEvents()

    class OnClickPointActionMoveTouchEvent(val index: Int, val actionMove: ActionMove) :
        ActionBarServiceEvents()
}

sealed class DragEvents {
    class ActionDown(val x: Int, val y: Int, val rawX: Float, val rawY: Float) : DragEvents()
    class ActionMove(val rawX: Float, val rawY: Float) : DragEvents()
}

sealed class ActionBarServiceActions {
    object OnDisableSelfAction : ActionBarServiceActions()
    class PerformClickAction(val x: Int, val y: Int) : ActionBarServiceActions()
}