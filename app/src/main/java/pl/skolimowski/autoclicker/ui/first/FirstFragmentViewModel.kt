package pl.skolimowski.autoclicker.ui.first

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class FirstFragmentViewModel @Inject constructor() : ViewModel() {
    fun testMethod(): String {
        return "test"
    }
}