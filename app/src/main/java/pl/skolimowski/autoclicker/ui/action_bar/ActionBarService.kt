package pl.skolimowski.autoclicker.ui.action_bar

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.view.*
import android.view.MotionEvent.*
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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

    private lateinit var viewsContainer: FrameLayout

    lateinit var wm: WindowManager
    lateinit var params: WindowManager.LayoutParams

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        viewsContainer = FrameLayout(this)
        val inflater = LayoutInflater.from(this)
        inflater.inflate(R.layout.action_bar, viewsContainer)

        params = createWindowLayoutParams()

        wm.addView(viewsContainer, params)

        setUpView()
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

    private fun collectActions() {
        myApp.applicationScope.launch {
            viewModel.actionsSharedFlow.collectLatest {
                when (it) {
                    is ActionBarServiceActions.OnDisableSelfAction -> {
                        disableSelf()
                    }
                }
            }
        }
    }

    private fun setUpView() {
        viewsContainer.findViewById<ImageView>(R.id.iv_close).setOnClickListener {
            viewModel.onUiEvent(OnCloseImageClickedEvent)
        }

        setUpDrag()
    }

    // todo move logic to view model
    // https://stackoverflow.com/a/51361730
    @SuppressLint("ClickableViewAccessibility") // todo check suppress
    private fun setUpDrag() {
        val root = viewsContainer.findViewById<LinearLayout>(R.id.root)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        root.setOnTouchListener { view, event ->
            when (event.action) {
                ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y

                    initialTouchX = event.rawX
                    initialTouchY = event.rawY

                    return@setOnTouchListener true
                }
                ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()

                    wm.updateViewLayout(viewsContainer, params)

                    return@setOnTouchListener true
                }
            }

            return@setOnTouchListener false
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

sealed class ActionBarServiceEvents : UiEvent() {
    object OnCloseImageClickedEvent : ActionBarServiceEvents()
}

sealed class ActionBarServiceActions {
    object OnDisableSelfAction : ActionBarServiceActions()
}