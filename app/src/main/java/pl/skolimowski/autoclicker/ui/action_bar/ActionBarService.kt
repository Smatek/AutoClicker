package pl.skolimowski.autoclicker.ui.action_bar

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.graphics.Path
import android.graphics.PixelFormat
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
    private val macroConfigWindowManager = MacroConfigWindowManager()
    private val clickPointConfigWindowManager = ClickPointConfigWindowManager()
    private val clickPointViewHolders = mutableListOf<ClickPointViewHolder>()

    lateinit var wm: WindowManager

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        initScreenSize()

        actionBarManager.createView()
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
        val width = windowMetrics.bounds.width()
        val height = windowMetrics.bounds.height()

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
                            macroConfigWindowManager.show(it.macroConfig)
                        }
                    }
                    is DismissConfigDialog -> {
                        withContext(Dispatchers.Main) {
                            macroConfigWindowManager.dismiss()
                        }
                    }
                    is UpdateConfigDialog -> {
                        withContext(Dispatchers.Main) {
                            macroConfigWindowManager.updateView(it.macroConfig)
                        }
                    }
                    is ShowClickPointConfigDialogAction -> {
                        withContext(Dispatchers.Main) {
                            clickPointConfigWindowManager.show(it.clickPointConfigState)
                        }
                    }
                    is DismissClickPointConfigDialogAction -> {
                        withContext(Dispatchers.Main) {
                            clickPointConfigWindowManager.dismiss()
                        }
                    }
                    is UpdateClickPointConfigDialogAction -> {
                        withContext(Dispatchers.Main) {
                            clickPointConfigWindowManager.updateView(it.clickPointConfigState)
                        }
                    }
                }
            }
        }
    }

    private fun createWindowLayoutParams(): WindowManager.LayoutParams {
        val lp = WindowManager.LayoutParams()

        lp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        lp.format = PixelFormat.TRANSLUCENT
        lp.flags = lp.flags or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
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

            setClickPointIndex(clickPointViewHolder)
            setUpClickPointDragTouchListener(clickPointViewHolder)

            return clickPointViewHolder
        }

        private fun setClickPointIndex(clickPointViewHolder: ClickPointViewHolder) {
            val textView = clickPointViewHolder.view.findViewById<TextView>(R.id.tv_click_point)
            textView.text = clickPointViewHolder.index.toString()
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

        private lateinit var ivClose: ImageView
        private lateinit var ivRemove: ImageView
        private lateinit var ivAdd: ImageView
        private lateinit var ivPlay: ImageView
        private lateinit var ivPause: ImageView
        private lateinit var ivConfig: ImageView

        fun createView() {
            viewsContainer = FrameLayout(this@ActionBarService)
            val inflater = LayoutInflater.from(this@ActionBarService)
            inflater.inflate(R.layout.action_bar, viewsContainer)

            params = createWindowLayoutParams()

            wm.addView(viewsContainer, params)

            findViews()
            setUpView()
        }

        private fun findViews() {
            ivClose = viewsContainer.findViewById(R.id.iv_close)
            ivAdd = viewsContainer.findViewById(R.id.iv_add)
            ivRemove = viewsContainer.findViewById(R.id.iv_remove)
            ivPlay = viewsContainer.findViewById(R.id.iv_play)
            ivPause = viewsContainer.findViewById(R.id.iv_pause)
            ivConfig = viewsContainer.findViewById(R.id.iv_config)
        }

        private fun setUpView() {
            ivClose.setOnClickListener { viewModel.onUiEvent(OnCloseImageClickedEvent) }
            ivAdd.setOnClickListener { viewModel.onUiEvent(OnAddImageClickedEvent) }
            ivRemove.setOnClickListener { viewModel.onUiEvent(OnRemoveImageClickedEvent) }
            ivPlay.setOnClickListener { viewModel.onUiEvent(OnPlayImageClickedEvent) }
            ivPause.setOnClickListener { viewModel.onUiEvent(OnPauseImageClickedEvent) }
            ivConfig.setOnClickListener { viewModel.onUiEvent(OnConfigImageClickedEvent) }

            setUpActionBarDragTouchListener()
            collectActionBarState()
        }

        private fun collectActionBarState() {
            myApp.applicationScope.launch {
                viewModel.actionBarStateFlow.collectLatest {
                    withContext(Dispatchers.Main) {
                        val dragState = it.dragState
                        params.x = dragState.x
                        params.y = dragState.y

                        if (it.isPlaying) {
                            ivPlay.visibility = View.GONE
                            ivPause.visibility = View.VISIBLE

                            ivAdd.visibility = View.GONE
                            ivRemove.visibility = View.GONE
                            ivConfig.visibility = View.GONE
                        } else {
                            ivPlay.visibility = View.VISIBLE
                            ivPause.visibility = View.GONE

                            ivAdd.visibility = View.VISIBLE
                            ivRemove.visibility = View.VISIBLE
                            ivConfig.visibility = View.VISIBLE
                        }

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

    inner class MacroConfigWindowManager : ConfigWindowManager(R.layout.dialog_config) {
        private lateinit var infiniteRadioButton: RadioButton
        private lateinit var cyclesRadioButton: RadioButton
        private lateinit var saveTextView: TextView
        private lateinit var cancelTextView: TextView
        private lateinit var cyclesEditText: TextInputEditText

        fun show(macroConfig: MacroConfig) {
            createDialog()

            updateView(macroConfig)
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
            val cyclesText = macroConfig.getCyclesText()
            cyclesEditText.setText(cyclesText)
            cyclesEditText.error = if (macroConfig.isValid()) null else "invalid" // todo resource

            if (cyclesText.length >= selection) {
                cyclesEditText.setSelection(selection)
            }

            infiniteRadioButton.setOnCheckedChangeListener(infiniteRadioButtonCheckListener)
            cyclesRadioButton.setOnCheckedChangeListener(cyclesRadioButtonCheckListener)
            saveTextView.setOnClickListener(saveClickListener)
            cancelTextView.setOnClickListener(cancelClickListener)
            cyclesEditText.addTextChangedListener(cyclesCountTextWatcher)
        }

        override fun findViews() {
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

        private val cyclesCountTextWatcher = object : SafeTextWatcher() {
            override fun onTextChanged(text: String) {
                viewModel.onUiEvent(OnCyclesCountTextChangedEvent(text))
            }
        }
    }

    inner class ClickPointConfigWindowManager :
        ConfigWindowManager(R.layout.dialog_click_point_config) {
        private lateinit var saveTextView: TextView
        private lateinit var cancelTextView: TextView
        private lateinit var delayEditText: TextInputEditText

        fun show(clickPointConfigState: ClickPointConfigState) {
            createDialog()

            updateView(clickPointConfigState)
        }

        fun updateView(clickPointConfigState: ClickPointConfigState) {
            // reset listeners so they are not affected by setting value here
            delayEditText.removeTextChangedListener(delayTextWatcher)

            val selection = delayEditText.selectionStart
            val delayText = clickPointConfigState.delay
            delayEditText.setText(delayText)
            delayEditText.error =
                if (clickPointConfigState.isValid()) null else "invalid" // todo resource

            if (delayText.length >= selection) {
                delayEditText.setSelection(selection)
            }

            saveTextView.setOnClickListener(saveClickListener)
            cancelTextView.setOnClickListener(cancelClickListener)
            delayEditText.addTextChangedListener(delayTextWatcher)
        }

        override fun findViews() {
            saveTextView = configWindow.findViewById(R.id.tv_save)
            cancelTextView = configWindow.findViewById(R.id.tv_cancel)
            delayEditText = configWindow.findViewById(R.id.et_delay)
        }

        private val saveClickListener = View.OnClickListener {
            viewModel.onUiEvent(OnSaveClickPointConfigClickEvent)
        }

        private val cancelClickListener = View.OnClickListener {
            viewModel.onUiEvent(OnCancelClickPointConfigClickEvent)
        }

        private val delayTextWatcher = object : SafeTextWatcher() {
            override fun onTextChanged(text: String) {
                viewModel.onUiEvent(OnDelayTextChangedEvent(text))
            }
        }
    }

    // AlertDialogs and dialogFragments needs activity to be shown. Other option was activity
    // displayed as an dialog, but the problem is that it is shown below action bar view.
    // That is why config window is created as a view of ActionBarService
    abstract inner class ConfigWindowManager(private val layoutResId: Int) {
        protected lateinit var configWindow: FrameLayout
        private lateinit var dialog: AlertDialog

        protected fun createDialog() {
            val context = ContextThemeWrapper(this@ActionBarService, R.style.Theme_AutoClicker)
            val inflater = LayoutInflater.from(context)
            configWindow = inflater.inflate(layoutResId, null) as FrameLayout
            dialog = Builder(context)
                .setView(configWindow)
                .setCancelable(false)
                .create()

            dialog.window?.let { window ->
                window.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
                dialog.show()
            }

            findViews()
        }

        fun dismiss() {
            dialog.dismiss()
        }

        abstract fun findViews()
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
    class OnDelayTextChangedEvent(val text: String) : ActionBarServiceEvents()
    object OnCancelClickPointConfigClickEvent : ActionBarServiceEvents()
    object OnSaveClickPointConfigClickEvent : ActionBarServiceEvents()
}

sealed class DragEvents {
    class ActionDown(val x: Int, val y: Int, val rawX: Float, val rawY: Float) : DragEvents()
    class ActionMove(val rawX: Float, val rawY: Float) : DragEvents()
}

// fixme add postfix "Action" to all object/classes
sealed class ActionBarServiceActions {
    object OnDisableSelfAction : ActionBarServiceActions()
    class ShowConfigDialog(val macroConfig: MacroConfig) : ActionBarServiceActions()
    class UpdateConfigDialog(val macroConfig: MacroConfig) : ActionBarServiceActions()
    object DismissConfigDialog : ActionBarServiceActions()
    class PerformClickAction(val x: Int, val y: Int) : ActionBarServiceActions()
    class ShowClickPointConfigDialogAction(val clickPointConfigState: ClickPointConfigState) :
        ActionBarServiceActions()

    class UpdateClickPointConfigDialogAction(val clickPointConfigState: ClickPointConfigState) :
        ActionBarServiceActions()

    object DismissClickPointConfigDialogAction : ActionBarServiceActions()
}