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

    private fun createView() {
        viewsContainer = FrameLayout(this)
        val inflater = LayoutInflater.from(this)
        inflater.inflate(R.layout.action_bar, viewsContainer)

        params = createWindowLayoutParams()

        wm.addView(viewsContainer, params)
    }

    override fun onInterrupt() {
        TODO("Not yet implemented")
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
                }

                clickPointViewHolders.remove(clickPointView)
            }
        }
    }

    private fun createClickPointView(index: Int): ClickPointViewHolder {
        val view = FrameLayout(this)
        val inflater = LayoutInflater.from(this)
        inflater.inflate(R.layout.action_bar, view) // todo create actual click point

        // todo setup drag listener

        val params = createWindowLayoutParams()

        return ClickPointViewHolder(index = index, view = view, params = params)
    }

    private fun collectActions() {
        myApp.applicationScope.launch {
            viewModel.actionsSharedFlow.collectLatest {
                when (it) {
                    is ActionBarServiceActions.OnDisableSelfAction -> {
                        disableSelf()
                    }
                    is ActionBarServiceActions.PerformClickAction -> {
                        val path = Path()
                        path.moveTo(it.x, it.y)
                        val builder = GestureDescription.Builder()
                        builder.addStroke(GestureDescription.StrokeDescription(path, 0, 1L))
                        dispatchGesture(builder.build(), null, null)
                    }
                }
            }
        }
    }

    private fun setUpView() {
        viewsContainer.findViewById<ImageView>(R.id.iv_close).setOnClickListener {
            viewModel.onUiEvent(OnCloseImageClickedEvent)
        }

        viewsContainer.findViewById<ImageView>(R.id.iv_add).setOnClickListener {
            viewModel.onUiEvent(OnAddImageClickedEvent)
        }

        setUpDrag()
    }

    private fun setUpDrag() {
        collectDragState()
        setUpDragTouchListener()
    }

    @SuppressLint("ClickableViewAccessibility") // todo check suppress
    // https://stackoverflow.com/a/51361730
    private fun setUpDragTouchListener() {
        val root = viewsContainer.findViewById<LinearLayout>(R.id.root)
        root.setOnTouchListener { view, event ->
            when (event.action) {
                ACTION_DOWN -> {
                    viewModel.onUiEvent(
                        OnActionDownTouchEvent(params.x, params.y, event.rawX, event.rawY)
                    )

                    return@setOnTouchListener true
                }
                ACTION_MOVE -> {
                    viewModel.onUiEvent(OnActionMoveTouchEvent(event.rawX, event.rawY))

                    return@setOnTouchListener true
                }
                else -> {
                    return@setOnTouchListener false
                }
            }
        }
    }

    private fun collectDragState() {
        myApp.applicationScope.launch {
            viewModel.dragStateFlow.collectLatest {
                withContext(Dispatchers.Main) {
                    params.x = it.x
                    params.y = it.y

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
}

class ClickPointViewHolder(
    val index: Int,
    val view: View,
    val params: WindowManager.LayoutParams
)

sealed class ActionBarServiceEvents : UiEvent() {
    object OnAddImageClickedEvent : ActionBarServiceEvents()
    object OnCloseImageClickedEvent : ActionBarServiceEvents()
    class OnActionDownTouchEvent(val x: Int, val y: Int, val rawX: Float, val rawY: Float) :
        ActionBarServiceEvents()

    class OnActionMoveTouchEvent(val rawX: Float, val rawY: Float) : ActionBarServiceEvents()
}

sealed class ActionBarServiceActions {
    object OnDisableSelfAction : ActionBarServiceActions()
    class PerformClickAction(val x: Float, val y: Float) : ActionBarServiceActions()
}