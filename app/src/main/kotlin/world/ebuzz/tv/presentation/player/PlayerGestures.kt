package world.ebuzz.tv.presentation.player

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

    private val detector = GestureDetector(surface.context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean { axis = 0; dragY = 0f; return true }
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean { onTap(); return true }
        override fun onDoubleTap(e: MotionEvent): Boolean { onDoubleTap(e.x > surface.width / 2f); return true }

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
    fun attach(onAnyTouch: () -> Unit) = surface.setOnTouchListener { v, e ->
        onAnyTouch()
        val handled = detector.onTouchEvent(e)
        if (e.actionMasked == MotionEvent.ACTION_UP) { onRelease(); v.performClick() }
        handled
    }

    private companion object { const val SLOP_PX = 24; const val MIN_SWIPE_FRACTION = 0.12f }
}
