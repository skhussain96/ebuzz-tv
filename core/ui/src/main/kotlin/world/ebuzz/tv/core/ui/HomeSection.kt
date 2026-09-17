package world.ebuzz.tv.core.ui

import android.view.KeyEvent
import androidx.fragment.app.Fragment

/** A tab on the home screen. Feature modules provide these; the app edition decides which ones exist. */
interface HomeSection {
    val id: String
    val title: String
    fun newFragment(): Fragment
}

/** Implemented by section fragments that want remote keys (digits, OK-to-retry) before the default handling. */
interface KeyHandler {
    fun onKey(keyCode: Int, event: KeyEvent): Boolean
}
