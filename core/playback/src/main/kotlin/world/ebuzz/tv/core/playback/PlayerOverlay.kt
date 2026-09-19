package world.ebuzz.tv.core.playback

import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import world.ebuzz.tv.core.playback.databinding.ActivityPlayerBinding
import world.ebuzz.tv.core.ui.R as UiR
import kotlin.math.roundToInt

/**
 * Everything drawn over the picture: the bottom bar, the touch buttons, the volume panel and the brief hints,
 * with their show / auto-hide timing. Keeps view bookkeeping out of the activity.
 */
internal class PlayerOverlay(private val b: ActivityPlayerBinding) {
    private val ui = Handler(Looper.getMainLooper())

    /** Buttons appear only after touch or pointer input; a TV remote keeps the screen clean. */
    var touchUi = false
    var scrubbing = false
    /** An album has no picture to uncover, so its bar never auto-hides. */
    var sticky = false

    val controlsVisible get() = b.controls.visibility == View.VISIBLE

    private val hideBar = Runnable { if (!scrubbing) hideNow() }
    private val hideVolume = Runnable { b.volBox.visibility = View.GONE }
    private val hideHint = Runnable { b.hint.visibility = View.GONE }

    fun applyMode(kind: PlayerKind) {
        val live = kind == PlayerKind.LIVE; val album = kind == PlayerKind.ALBUM
        sticky = album
        b.liveLine.visibility = if (live) View.VISIBLE else View.GONE
        b.seek.visibility = if (live) View.GONE else View.VISIBLE
        b.osdRight.setTextColor(b.root.context.getColor(if (live) UiR.color.bad else UiR.color.inkDim))
        b.osdRight.text = if (live) b.root.context.getString(UiR.string.live) else ""
        b.art.visibility = if (album) View.VISIBLE else View.GONE
        b.btnTracks.visibility = if (album) View.VISIBLE else View.GONE
    }

    // buttons come up only on an explicit tap / pointer move (or on an album, whose screen is its controls)
    fun reveal(playing: Boolean) {
        if (touchUi) { b.controls.visibility = View.VISIBLE; b.topBar.visibility = View.VISIBLE }
        show(playing)
    }

    fun show(playing: Boolean) {
        b.osd.visibility = View.VISIBLE
        if (touchUi && sticky) { b.controls.visibility = View.VISIBLE; b.topBar.visibility = View.VISIBLE }
        ui.removeCallbacks(hideBar)
        if (playing && !sticky) ui.postDelayed(hideBar, if (touchUi) 4000 else 3000)
    }

    /** Hold the bar up (buffering, scrubbing). */
    fun pin() { b.osd.visibility = View.VISIBLE; ui.removeCallbacks(hideBar) }

    fun hideNow() { if (sticky) return; b.osd.visibility = View.GONE; b.controls.visibility = View.GONE; b.topBar.visibility = View.GONE }

    fun hideButtons() { touchUi = false; b.controls.visibility = View.GONE; b.topBar.visibility = View.GONE }

    fun hint(text: String, side: Int = 0) {
        (b.hint.layoutParams as FrameLayout.LayoutParams).gravity =
            Gravity.CENTER_VERTICAL or (if (side < 0) Gravity.START else if (side > 0) Gravity.END else Gravity.CENTER_HORIZONTAL)
        b.hint.requestLayout()
        b.hint.text = text; b.hint.visibility = View.VISIBLE
        ui.removeCallbacks(hideHint); ui.postDelayed(hideHint, 700)
    }

    fun volume(level: Float) {
        val pct = (level * 100).roundToInt()
        b.volText.text = pct.toString()
        b.volIcon.setImageResource(if (pct == 0) android.R.drawable.ic_lock_silent_mode else android.R.drawable.ic_lock_silent_mode_off)
        (b.volFill.layoutParams as LinearLayout.LayoutParams).weight = level
        (b.volRest.layoutParams as LinearLayout.LayoutParams).weight = 1f - level
        b.volFill.requestLayout()
        b.volBox.visibility = View.VISIBLE
        ui.removeCallbacks(hideVolume); ui.postDelayed(hideVolume, 2000)
    }

    fun error(message: String?) {
        b.spinner.visibility = View.GONE
        b.error.text = message; b.error.visibility = if (message == null) View.GONE else View.VISIBLE
    }

    fun buffering(on: Boolean) { b.spinner.visibility = if (on) View.VISIBLE else View.GONE }

    fun playing(isPlaying: Boolean) =
        b.btnPlay.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)

    fun release() = ui.removeCallbacksAndMessages(null)
}
