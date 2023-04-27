package pl.skolimowski.autoclicker.ui.action_bar

import android.text.Editable
import android.text.TextWatcher

// this text watcher ensures that onTextChanged is not called multiple times in case that same text is set
abstract class SafeTextWatcher : TextWatcher {
    lateinit var beforeTextChangedValue: String

    override fun beforeTextChanged(text: CharSequence, p1: Int, p2: Int, p3: Int) {
        beforeTextChangedValue = text.toString()
    }

    override fun onTextChanged(text: CharSequence, p1: Int, p2: Int, p3: Int) {
        val textAsString = text.toString()

        if (textAsString != beforeTextChangedValue) {
            onTextChanged(textAsString)
        }
    }

    override fun afterTextChanged(p0: Editable?) {
        // empty
    }

    abstract fun onTextChanged(text: String)
}