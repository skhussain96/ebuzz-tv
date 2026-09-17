package world.ebuzz.tv.core.ui

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.TextView

/** Collects typed digits for ~1.2 s, shows them, then hands the channel number to [onJump]. */
class DigitEntry(private val label: TextView, private val onJump: (Int) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var buf = ""
    private val fire = Runnable {
        label.visibility = View.GONE
        buf.toIntOrNull()?.let(onJump); buf = ""
    }

    fun onKey(keyCode: Int): Boolean {
        val d = when (keyCode) {
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> keyCode - KeyEvent.KEYCODE_0
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> keyCode - KeyEvent.KEYCODE_NUMPAD_0
            else -> return false
        }
        buf += d; label.text = buf; label.visibility = View.VISIBLE
        handler.removeCallbacks(fire); handler.postDelayed(fire, 1200)
        return true
    }
}
