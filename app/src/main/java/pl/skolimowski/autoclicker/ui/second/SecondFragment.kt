package pl.skolimowski.autoclicker.ui.second

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import pl.skolimowski.autoclicker.R
import pl.skolimowski.autoclicker.databinding.FragmentSecondBinding
import pl.skolimowski.autoclicker.ui.base.BaseViewBindingFragment

class SecondFragment : BaseViewBindingFragment<FragmentSecondBinding>() {
    override val bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> FragmentSecondBinding
        get() = FragmentSecondBinding::inflate

    override fun setup() {
        binding.buttonSecond.setOnClickListener {
            findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)
        }
    }
}