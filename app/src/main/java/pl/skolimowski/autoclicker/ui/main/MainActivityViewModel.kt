package pl.skolimowski.autoclicker.ui.main

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor() : ViewModel() {
    // fixme remove this method when there is some real use of ViewModel in MainActivity.
    //  for now it is only used to check if testing works correctly - running from AndroidStudio,
    //  with coverage, with jacoco report
    fun testMethod(): String {
        return "test"
    }
}