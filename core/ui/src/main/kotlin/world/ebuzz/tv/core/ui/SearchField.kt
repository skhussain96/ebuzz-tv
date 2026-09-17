package world.ebuzz.tv.core.ui

import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

/**
 * A search box that behaves on a TV: moving the D-pad focus across it must not pop the keyboard — only pressing
 * OK (a click) does. On touch devices it is an ordinary text field.
 */
fun EditText.keyboardOnlyOnClick() {
    if (!context.isTv) return
    showSoftInputOnFocus = false
    setOnClickListener {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }
}
