package pl.skolimowski.autoclicker

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import pl.skolimowski.autoclicker.base.BaseViewBindingFragment
import pl.skolimowski.autoclicker.databinding.FragmentFirstBinding

class FirstFragment : BaseViewBindingFragment<FragmentFirstBinding>() {
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> FragmentFirstBinding
        get() = FragmentFirstBinding::inflate

    override fun setup() {
        binding.buttonFirst.setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }
    }
}