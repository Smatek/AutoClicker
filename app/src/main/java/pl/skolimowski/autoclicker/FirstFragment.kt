package pl.skolimowski.autoclicker

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import pl.skolimowski.autoclicker.base.BaseViewBindingFragment
import pl.skolimowski.autoclicker.databinding.FragmentFirstBinding

@AndroidEntryPoint
class FirstFragment : BaseViewBindingFragment<FragmentFirstBinding>() {
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> FragmentFirstBinding
        get() = FragmentFirstBinding::inflate

    private val viewModel: FirstFragmentViewModel by viewModels()

    override fun setup() {
        binding.textviewFirst.text = viewModel.testMethod()

        binding.buttonFirst.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }
    }
}