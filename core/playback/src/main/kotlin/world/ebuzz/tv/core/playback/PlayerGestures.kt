package world.ebuzz.tv.core.playback

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Touch vocabulary of the player, independent of what the gestures do:
 * tap, double-tap (with the side that was hit), horizontal swipe, vertical drag.
 */
class PlayerGestures(
    private val surface: View,
    private val onTap: () -> Unit,
    private val onDoubleTap: (rightHalf: Boolean) -> Unit,
    private val onSwipe: (dir: Int) -> Unit,                 // +1 = swipe left (next), -1 = swipe right (previous)
    private val onVerticalDrag: (fraction: Float) -> Unit,   // cumulative, + = upward, 1.0 ≈ 60 % of the screen height
    private val onRelease: () -> Unit,
) {
    private var axis = 0            // 0 undecided, 1 horizontal, 2 vertical
    private var dragY = 0f
    private var lastDouble = 0L
    private var lastRight = false
    private var swallowTap = false

    private val detector = GestureDetector(surface.context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean { axis = 0; dragY = 0f; return true }
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean { if (swallowTap) swallowTap = false else onTap(); return true }
        override fun onDoubleTap(e: MotionEvent): Boolean { lastRight = e.x > surface.width / 2f; lastDouble = e.eventTime; onDoubleTap(lastRight); return true }
        // keep tapping the same side after a double tap and every tap counts, like MX Player
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (e.eventTime - lastDouble > REPEAT_MS || (e.x > surface.width / 2f) != lastRight) return false
            lastDouble = e.eventTime; swallowTap = true; onDoubleTap(lastRight); return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (e1 == null) return false
            if (axis == 0) {
                val tx = abs(e2.x - e1.x); val ty = abs(e2.y - e1.y)
                if (maxOf(tx, ty) < SLOP_PX) return true
                axis = if (tx > ty) 1 else 2
            }
            if (axis == 2) { dragY += dy; onVerticalDrag(dragY / (surface.height * 0.6f)) }
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (e1 == null || axis != 1) return false
            val dx = e2.x - e1.x
            if (abs(dx) < surface.width * MIN_SWIPE_FRACTION) return false
            onSwipe(if (dx < 0) 1 else -1)
            return true
        }
    })

    @SuppressLint("ClickableViewAccessibility")
    fun attach(onAnyTouch: (MotionEvent) -> Unit) = surface.setOnTouchListener { v, e ->
        onAnyTouch(e)
        val handled = detector.onTouchEvent(e)
        if (e.actionMasked == MotionEvent.ACTION_UP) { onRelease(); v.performClick() }
        handled
    }

    private companion object { const val SLOP_PX = 24; const val REPEAT_MS = 700L; const val MIN_SWIPE_FRACTION = 0.12f }
}
