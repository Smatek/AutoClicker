package pl.skolimowski.autoclicker.ui.first

import android.content.Intent
import android.provider.Settings
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import pl.skolimowski.autoclicker.databinding.FragmentFirstBinding
import pl.skolimowski.autoclicker.ui.UiEvent
import pl.skolimowski.autoclicker.ui.base.BaseViewBindingFragment

@AndroidEntryPoint
class FirstFragment : BaseViewBindingFragment<FragmentFirstBinding>() {
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> FragmentFirstBinding
        get() = FragmentFirstBinding::inflate

    private val viewModel: FirstFragmentViewModel by viewModels()

    override fun setup() {
        binding.btnAccessibilitySettings.setOnClickListener {
            viewModel.onUiEvent(FirstFragmentEvents.OnAccessibilitySettingsButtonClickEvent)
        }

        collectLatestLifecycleFlow(viewModel.actionsSharedFlow, actionCollector)
    }

    private val actionCollector = { action: FirstFragmentActions ->
        when (action) {
            is FirstFragmentActions.OpenAccessibilitySettings -> {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            }
        }
    }
}
sealed class FirstFragmentActions {
    object OpenAccessibilitySettings : FirstFragmentActions()
}

sealed class FirstFragmentEvents : UiEvent() {
    object OnAccessibilitySettingsButtonClickEvent : FirstFragmentEvents()
}

// todo move to base fragment
fun <T> Fragment.collectLatestLifecycleFlow(flow: Flow<T>, collect: suspend (T) -> Unit) {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collectLatest(collect)
        }
    }
}