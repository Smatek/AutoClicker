package pl.skolimowski.autoclicker.ui.action_bar

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.graphics.Path
import android.graphics.PixelFormat
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.MotionEvent.*
import android.view.accessibility.AccessibilityEvent
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AlertDialog.Builder
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.skolimowski.autoclicker.MyApp
import pl.skolimowski.autoclicker.R
import pl.skolimowski.autoclicker.ui.UiEvent
import pl.skolimowski.autoclicker.ui.action_bar.ActionBarServiceActions.*
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

    private val actionBarManager = ActionBarManager()
    private val clickPointsManager = ClickPointsManager()
    private val configWindowManager = ConfigWindowManager()
    private val clickPointViewHolders = mutableListOf<ClickPointViewHolder>()

    lateinit var wm: WindowManager

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        initScreenSize()

        actionBarManager.createView()
        actionBarManager.setUpView()
        clickPointsManager.setUp()
    }

    override fun onCreate() {
        super.onCreate()

        collectActions()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Timber.i("onAccessibilityEvent: $event")
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

    private fun collectActions() {
        myApp.applicationScope.launch {
            viewModel.actionsSharedFlow.collectLatest {
                when (it) {
                    is OnDisableSelfAction -> {
                        disableSelf()
                    }
                    is PerformClickAction -> {
                        withContext(Dispatchers.Main) {
                            clickPointsManager.performClick(it)
                        }
                    }
                    is ShowConfigDialog -> {
                        withContext(Dispatchers.Main) {
                            configWindowManager.show(it.macroConfig)
                        }
                    }
                    is DismissConfigDialog -> {
                        withContext(Dispatchers.Main) {
                            configWindowManager.dismiss()
                        }
                    }
                    is UpdateConfigDialog -> {
                        withContext(Dispatchers.Main) {
                            configWindowManager.updateView(it.macroConfig)
                        }
                    }
                }
            }
        }
    }

    private fun setUpMacroStateCollector() {
        myApp.applicationScope.launch {
            viewModel.macroStateFlow.collectLatest {
                withContext(Dispatchers.Main) {
                    actionBarManager.macroStateChanged(it)
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

    inner class ClickPointsManager {
        fun setUp() {
            myApp.applicationScope.launch {
                viewModel.clickPointsStateFlow.collectLatest { clickPointsState ->
                    val clickPointList = clickPointsState.list

                    removeOldClickPoints(clickPointList, clickPointViewHolders)
                    addNewClickPoints(clickPointList, clickPointViewHolders)
                    updateChangedClickPoints(clickPointList, clickPointViewHolders)
                }
            }
        }

        fun performClick(clickAction: PerformClickAction) {
            setClickPointsTouchable(false)

            performClick(
                clickAction.x.toFloat(),
                clickAction.y.toFloat(),
                addClickPointsGestureCallback
            )
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
            val view = FrameLayout(this@ActionBarService)
            val inflater = LayoutInflater.from(this@ActionBarService)
            inflater.inflate(R.layout.click_point, view)

            val params = createWindowLayoutParams()
            val clickPointViewHolder =
                ClickPointViewHolder(index = index, view = view, params = params)

            setUpClickPointDragTouchListener(clickPointViewHolder)

            return clickPointViewHolder
        }

        @SuppressLint("ClickableViewAccessibility") // todo check suppress
        // https://stackoverflow.com/a/51361730
        private fun setUpClickPointDragTouchListener(clickPointViewHolder: ClickPointViewHolder) {
            val root = clickPointViewHolder.view.findViewById<FrameLayout>(R.id.root)

            var moveActionHappened = false

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

                        moveActionHappened = true

                        return@setOnTouchListener true
                    }
                    ACTION_UP -> {
                        // trigger OnClick event only if ClickPoint was not moved
                        if (!moveActionHappened) {
                            viewModel.onUiEvent(OnClickPointClickEvent(clickPointViewHolder.index))
                        }

                        // reset the move Action happened flag
                        moveActionHappened = false

                        return@setOnTouchListener true
                    }
                    else -> {
                        return@setOnTouchListener false
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

    inner class ActionBarManager {
        lateinit var params: WindowManager.LayoutParams
        private lateinit var viewsContainer: FrameLayout

        fun createView() {
            viewsContainer = FrameLayout(this@ActionBarService)
            val inflater = LayoutInflater.from(this@ActionBarService)
            inflater.inflate(R.layout.action_bar, viewsContainer)

            params = createWindowLayoutParams()

            wm.addView(viewsContainer, params)
        }

        fun setUpView() {
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

            viewsContainer.findViewById<ImageView>(R.id.iv_config).setOnClickListener {
                viewModel.onUiEvent(OnConfigImageClickedEvent)
            }

            setUpActionBarDrag()
            setUpMacroStateCollector()
        }

        fun macroStateChanged(macroState: MacroState) {
            val playImage = viewsContainer.findViewById<ImageView>(R.id.iv_play)
            val pauseImage = viewsContainer.findViewById<ImageView>(R.id.iv_pause)

            if (macroState.isPlaying) {
                playImage.visibility = View.GONE
                pauseImage.visibility = View.VISIBLE
            } else {
                playImage.visibility = View.VISIBLE
                pauseImage.visibility = View.GONE
            }

            wm.updateViewLayout(viewsContainer, params)
        }

        private fun setUpActionBarDrag() {
            collectActionBarDragState()
            setUpActionBarDragTouchListener()
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
    }

    // AlertDialogs and dialogFragments needs activity to be shown. Other option was activity
    // displayed as an dialog, but the problem is that it is shown below action bar view.
    // That is why config window is created as a view of ActionBarService
    inner class ConfigWindowManager {
        private lateinit var params: WindowManager.LayoutParams
        private lateinit var configWindow: FrameLayout
        private lateinit var dialog: AlertDialog

        private lateinit var infiniteRadioButton: RadioButton
        private lateinit var cyclesRadioButton: RadioButton
        private lateinit var saveTextView: TextView
        private lateinit var cancelTextView: TextView
        private lateinit var cyclesEditText: TextInputEditText

        fun show(macroConfig: MacroConfig) {
            val context = ContextThemeWrapper(this@ActionBarService, R.style.Theme_AutoClicker)
            val inflater = LayoutInflater.from(context)
            configWindow = inflater.inflate(R.layout.dialog_config, null) as FrameLayout
            dialog = Builder(context)
                .setView(configWindow)
                .setCancelable(false)
                .create()

            dialog.window?.let { window ->
                window.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
                dialog.show()
            }

            findViews()
            updateView(macroConfig)
        }

        fun dismiss() {
            dialog.dismiss()
        }

        fun updateView(macroConfig: MacroConfig) {
            // reset listeners so they are not affected by setting value here
            infiniteRadioButton.setOnCheckedChangeListener(null)
            cyclesRadioButton.setOnCheckedChangeListener(null)
            cyclesEditText.removeTextChangedListener(cyclesCountTextWatcher)

            val isInfinite = macroConfig.cycleMode == CycleMode.INFINITE
            infiniteRadioButton.isChecked = isInfinite
            cyclesRadioButton.isChecked = !isInfinite

            val selection = cyclesEditText.selectionStart
            val cyclesText = macroConfig.cyclesText
            cyclesEditText.setText(cyclesText)
            cyclesEditText.error = if (macroConfig.cyclesValid) null else "invalid" // todo resource

            if (cyclesText.length >= selection) {
                cyclesEditText.setSelection(selection)
            }

            infiniteRadioButton.setOnCheckedChangeListener(infiniteRadioButtonCheckListener)
            cyclesRadioButton.setOnCheckedChangeListener(cyclesRadioButtonCheckListener)
            saveTextView.setOnClickListener(saveClickListener)
            cancelTextView.setOnClickListener(cancelClickListener)
            cyclesEditText.addTextChangedListener(cyclesCountTextWatcher)
        }

        private fun findViews() {
            infiniteRadioButton = configWindow.findViewById(R.id.rb_infinite)
            cyclesRadioButton = configWindow.findViewById(R.id.rb_cycles)
            saveTextView = configWindow.findViewById(R.id.tv_save)
            cancelTextView = configWindow.findViewById(R.id.tv_cancel)
            cyclesEditText = configWindow.findViewById(R.id.et_cycles)
        }

        private val saveClickListener = View.OnClickListener {
            viewModel.onUiEvent(OnSaveConfigClickEvent)
        }

        private val cancelClickListener = View.OnClickListener {
            viewModel.onUiEvent(OnCancelConfigClickEvent)
        }

        private val infiniteRadioButtonCheckListener: (CompoundButton, Boolean) -> Unit = { _, _ ->
            viewModel.onUiEvent(OnInfiniteRadioButtonCheckedEvent)
        }

        private val cyclesRadioButtonCheckListener: (CompoundButton, Boolean) -> Unit = { _, _ ->
            viewModel.onUiEvent(OnCyclesCountRadioButtonCheckedEvent)
        }

        private val cyclesCountTextWatcher = object : TextWatcher {
            lateinit var beforeTextChangedValue: String

            override fun beforeTextChanged(text: CharSequence, p1: Int, p2: Int, p3: Int) {
                beforeTextChangedValue = text.toString()
            }

            override fun onTextChanged(text: CharSequence, p1: Int, p2: Int, p3: Int) {
                val textAsString = text.toString()

                if (textAsString != beforeTextChangedValue) {
                    Timber.i("$textAsString - textAsString")
                    Timber.i("$beforeTextChangedValue - beforeTextChangedValue")
                    viewModel.onUiEvent(OnCyclesCountTextChangedEvent(textAsString))
                }
            }

            override fun afterTextChanged(p0: Editable?) {
                // empty
            }
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
    object OnConfigImageClickedEvent : ActionBarServiceEvents()
    object OnAddImageClickedEvent : ActionBarServiceEvents()
    object OnRemoveImageClickedEvent : ActionBarServiceEvents()
    object OnCloseImageClickedEvent : ActionBarServiceEvents()
    class OnClickPointClickEvent(val index: Int) : ActionBarServiceEvents()
    class OnInitialScreenSizeEvent(val width: Int, val height: Int) : ActionBarServiceEvents()
    class OnActionBarActionDownTouchEvent(val actionDown: ActionDown) : ActionBarServiceEvents()
    class OnActionBarActionMoveTouchEvent(val actionMove: ActionMove) : ActionBarServiceEvents()
    class OnClickPointActionDownTouchEvent(val index: Int, val actionDown: ActionDown) :
        ActionBarServiceEvents()

    class OnClickPointActionMoveTouchEvent(val index: Int, val actionMove: ActionMove) :
        ActionBarServiceEvents()

    object OnInfiniteRadioButtonCheckedEvent : ActionBarServiceEvents()
    object OnCyclesCountRadioButtonCheckedEvent : ActionBarServiceEvents()
    class OnCyclesCountTextChangedEvent(val text: String) : ActionBarServiceEvents()
    object OnCancelConfigClickEvent : ActionBarServiceEvents()
    object OnSaveConfigClickEvent : ActionBarServiceEvents()
}

sealed class DragEvents {
    class ActionDown(val x: Int, val y: Int, val rawX: Float, val rawY: Float) : DragEvents()
    class ActionMove(val rawX: Float, val rawY: Float) : DragEvents()
}

sealed class ActionBarServiceActions {
    object OnDisableSelfAction : ActionBarServiceActions()
    class ShowConfigDialog(val macroConfig: MacroConfig) : ActionBarServiceActions()
    class UpdateConfigDialog(val macroConfig: MacroConfig) : ActionBarServiceActions()
    object DismissConfigDialog : ActionBarServiceActions()
    class PerformClickAction(val x: Int, val y: Int) : ActionBarServiceActions()
}