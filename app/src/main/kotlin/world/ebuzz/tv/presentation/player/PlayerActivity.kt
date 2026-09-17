package world.ebuzz.tv.presentation.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import world.ebuzz.tv.R
import world.ebuzz.tv.container
import world.ebuzz.tv.data.remote.EbuzzApi
import world.ebuzz.tv.databinding.ActivityPlayerBinding
import world.ebuzz.tv.presentation.common.DigitEntry
import world.ebuzz.tv.presentation.common.applyOrientation
import world.ebuzz.tv.presentation.common.factory
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One player for Live TV and movies. Owns the ExoPlayer and the views; what to play and what to persist
 * is decided by [PlayerViewModel].
 *
 * Remote:  LEFT/RIGHT previous/next channel (±10 s in a movie), UP/DOWN volume, OK/space pause, BACK exit,
 *          digits jump to a channel, long-press OK cycles audio tracks.
 * Touch:   swipe left/right = next/previous channel (±30 s in a movie), vertical drag = volume,
 *          tap = show buttons, double-tap = pause (live) or ±10 s by side (movie).
 * Pointer: click = tap, wheel = volume, moving the pointer reveals the buttons.
 */
class PlayerActivity : AppCompatActivity() {
    private lateinit var b: ActivityPlayerBinding
    private lateinit var player: ExoPlayer
    private lateinit var digits: DigitEntry
    private val ui = Handler(Looper.getMainLooper())

    private val vm: PlayerViewModel by viewModels {
        factory {
            val id = intent.getIntExtra(EXTRA_MOVIE_ID, 0)
            PlayerViewModel(container, if (id != 0) PlayerArgs.Film(id, intent.getStringExtra(EXTRA_TITLE).orEmpty(), intent.getStringExtra(EXTRA_URL)!!)
            else PlayerArgs.Live(intent.getIntExtra(EXTRA_NUMBER, 0).takeIf { it > 0 }))
        }
    }
    private val isMovie get() = vm.isMovie

    private var title = ""
    private var scrubbing = false
    private var touchUi = false                  // buttons appear only after touch / pointer input
    private var okLongPressed = false
    private var dragStartVolume = 1f
    private var ticks = 0

    private val hideOsd = Runnable {
        if (!scrubbing) { b.osd.visibility = View.GONE; b.controls.visibility = View.GONE; b.topBar.visibility = View.GONE }
    }
    private val hideVol = Runnable { b.volBox.visibility = View.GONE }
    private val hideHint = Runnable { b.hint.visibility = View.GONE }
    private val tick = object : Runnable {
        override fun run() {
            updateProgress()
            if (++ticks % 10 == 0) saveProgress()          // every 5 s, so a killed app still resumes
            ui.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyOrientation()
        b = ActivityPlayerBinding.inflate(layoutInflater).also { setContentView(it.root) }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, b.root).run {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        digits = DigitEntry(b.chNum) { n -> if (!isMovie) vm.tuneNumber(n) }

        player = buildPlayer()
        b.playerView.player = player
        if (isMovie) {
            b.liveLine.visibility = View.GONE; b.seek.visibility = View.VISIBLE
            b.osdRight.setTextColor(getColor(R.color.inkDim)); b.osdRight.text = ""
        }
        setUpTouch()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                launch { vm.now.filterNotNull().collect(::load) }
                launch { vm.failed.collect { if (it) { b.spinner.visibility = View.GONE; b.error.text = getString(R.string.error); b.error.visibility = View.VISIBLE } } }
            }
        }
    }

    private fun buildPlayer(): ExoPlayer {
        val http = DefaultHttpDataSource.Factory().setUserAgent(EbuzzApi.USER_AGENT).setAllowCrossProtocolRedirects(true)
        // Live needs only a few seconds buffered; movies get a moderate window. Both far below ExoPlayer's 50 s default.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(if (isMovie) 15_000 else 4_000, if (isMovie) 30_000 else 12_000, 1_500, 2_500)
            .setBackBuffer(0, false)
            .build()
        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .setLoadControl(loadControl)
            .build().apply { playWhenReady = true; volume = vm.volume; addListener(listener) }
    }

    private fun load(item: NowPlaying) {
        title = item.title
        b.error.visibility = View.GONE
        b.osdTitle.text = title
        b.playerView.visibility = View.INVISIBLE          // hide the previous stream's last frame until the new one renders
        player.stop(); player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(item.streamUrl))
        player.prepare()
        if (item.startPositionMs > 0) player.seekTo(item.startPositionMs)
        player.play()
        showOsd()
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            b.spinner.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
            if (state == Player.STATE_READY) b.error.visibility = View.GONE
            if (state == Player.STATE_BUFFERING) { b.osd.visibility = View.VISIBLE; ui.removeCallbacks(hideOsd) } else showOsd()
            if (state == Player.STATE_ENDED && isMovie) { vm.onEnded(); finish() }
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            b.btnPlay.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        }
        override fun onRenderedFirstFrame() { b.playerView.visibility = View.VISIBLE }
        override fun onTracksChanged(tracks: Tracks) { b.btnAudio.visibility = if (audioGroups().size > 1) View.VISIBLE else View.GONE }
        override fun onPlayerError(error: PlaybackException) {
            b.spinner.visibility = View.GONE
            b.error.text = "Stream unavailable"; b.error.visibility = View.VISIBLE
        }
    }

    // ---- transport ----
    /** Next / previous: another channel on Live TV, ±10 s in a movie. */
    private fun step(dir: Int) {
        if (isMovie) { seekBy(dir * 10_000L); return }
        hint(if (dir > 0) "Next  ›" else "‹  Previous")
        vm.stepChannel(dir)
    }

    private fun seekBy(ms: Long) {
        val d = player.duration.takeIf { it > 0 } ?: return
        player.seekTo((player.currentPosition + ms).coerceIn(0, d))
        hint((if (ms > 0) "+" else "−") + "${abs(ms) / 1000}s")
        updateProgress(); showOsd()
    }

    private fun togglePlay() {
        if (player.isPlaying) player.pause() else { player.playWhenReady = true; player.play() }
        showOsd()
    }

    private fun saveProgress() { if (isMovie) vm.saveProgress(player.currentPosition, player.duration) }

    // ---- overlays ----
    private fun showOsd() {
        b.osd.visibility = View.VISIBLE
        if (touchUi) { b.controls.visibility = View.VISIBLE; b.topBar.visibility = View.VISIBLE }
        ui.removeCallbacks(hideOsd)
        if (player.playWhenReady) ui.postDelayed(hideOsd, if (touchUi) 4000 else 3000)
    }

    private fun hint(text: String) {
        b.hint.text = text; b.hint.visibility = View.VISIBLE
        ui.removeCallbacks(hideHint); ui.postDelayed(hideHint, 700)
    }

    private fun updateProgress() {
        if (!isMovie || scrubbing) return
        val d = player.duration.takeIf { it > 0 } ?: return
        val p = player.currentPosition
        b.seek.progress = (p * 1000 / d).toInt(); b.osdRight.text = "${fmt(p)} / ${fmt(d)}"
    }

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }

    private fun setVolume(v: Float, persist: Boolean) {
        vm.setVolume(v, persist)
        player.volume = vm.volume
        val pct = (vm.volume * 100).roundToInt()
        b.volText.text = pct.toString()
        b.volIcon.setImageResource(if (pct == 0) android.R.drawable.ic_lock_silent_mode else android.R.drawable.ic_lock_silent_mode_off)
        (b.volFill.layoutParams as LinearLayout.LayoutParams).weight = vm.volume
        (b.volRest.layoutParams as LinearLayout.LayoutParams).weight = 1f - vm.volume
        b.volFill.requestLayout()
        b.volBox.visibility = View.VISIBLE
        ui.removeCallbacks(hideVol); ui.postDelayed(hideVol, 2000)
    }

    private fun stepVolume(dir: Int) = setVolume(((vm.volume * 10).roundToInt() + dir) / 10f, persist = true)

    // ---- audio tracks ----
    private fun audioGroups() = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }

    private fun cycleAudio() {
        val groups = audioGroups()
        if (groups.size < 2) { hint("Single audio track"); return }
        val next = groups[(groups.indexOfFirst { it.isSelected } + 1) % groups.size]
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(next.mediaTrackGroup, 0)).build()
        val f = next.getTrackFormat(0)
        val name = f.label ?: f.language?.takeIf { it != "und" }?.let { Locale(it).displayLanguage.ifBlank { it } } ?: "Track ${groups.indexOf(next) + 1}"
        b.btnAudio.text = "Audio · $name"
        hint("Audio: $name"); showOsd()
    }

    // ---- touch + pointer ----
    private fun setUpTouch() {
        b.btnPrev.setOnClickListener { step(-1) }
        b.btnNext.setOnClickListener { step(1) }
        b.btnPlay.setOnClickListener { togglePlay() }
        b.btnBack.setOnClickListener { finish() }
        b.btnAudio.setOnClickListener { cycleAudio() }
        b.seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) player.duration.takeIf { it > 0 }?.let { b.osdRight.text = "${fmt(it * progress / 1000)} / ${fmt(it)}" }
            }
            override fun onStartTrackingTouch(sb: SeekBar) { scrubbing = true; ui.removeCallbacks(hideOsd) }
            override fun onStopTrackingTouch(sb: SeekBar) {
                scrubbing = false
                player.duration.takeIf { it > 0 }?.let { player.seekTo(it * sb.progress / 1000) }
                showOsd()
            }
        })
        PlayerGestures(
            surface = b.root,
            onTap = { if (b.controls.visibility == View.VISIBLE) hideOsd.run() else showOsd() },
            onDoubleTap = { right -> if (isMovie) seekBy(if (right) 10_000 else -10_000) else togglePlay() },
            onSwipe = { dir -> if (isMovie) seekBy(dir * 30_000L) else step(dir) },
            onVerticalDrag = { f -> setVolume(dragStartVolume + f, persist = false) },
            onRelease = { vm.setVolume(vm.volume, persist = true); dragStartVolume = vm.volume },
        ).attach(onAnyTouch = { if (!touchUi) { touchUi = true; dragStartVolume = vm.volume } })
    }

    /** Mouse wheel = volume; pointer movement reveals the buttons. */
    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        if (e.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) when (e.actionMasked) {
            MotionEvent.ACTION_SCROLL -> { stepVolume(if (e.getAxisValue(MotionEvent.AXIS_VSCROLL) > 0) 1 else -1); return true }
            MotionEvent.ACTION_HOVER_MOVE -> if (b.controls.visibility != View.VISIBLE) { touchUi = true; showOsd() }
        }
        return super.onGenericMotionEvent(e)
    }

    // ---- remote ----
    private fun isOk(keyCode: Int) = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (isOk(keyCode)) { okLongPressed = true; cycleAudio(); return true }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isOk(keyCode)) { if (!okLongPressed) togglePlay(); okLongPressed = false; return true }
        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (digits.onKey(keyCode)) return true
        if (keyCode != KeyEvent.KEYCODE_BACK) { touchUi = false; b.controls.visibility = View.GONE; b.topBar.visibility = View.GONE }   // remote in use: keep the screen clean
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { event.startTracking(); return true }
            KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK -> cycleAudio()
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_PAGE_UP -> step(1)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_PAGE_DOWN -> step(-1)
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> seekBy(30_000)
            KeyEvent.KEYCODE_MEDIA_REWIND -> seekBy(-30_000)
            KeyEvent.KEYCODE_DPAD_UP -> stepVolume(1)
            KeyEvent.KEYCODE_DPAD_DOWN -> stepVolume(-1)
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> togglePlay()
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> finish()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    // ---- lifecycle ----
    override fun onStart() { super.onStart(); if (vm.now.value != null) player.play(); ui.post(tick) }
    override fun onStop() { super.onStop(); ui.removeCallbacks(tick); saveProgress(); player.pause() }
    override fun onDestroy() { ui.removeCallbacksAndMessages(null); player.release(); super.onDestroy() }

    companion object {
        private const val EXTRA_NUMBER = "number"
        private const val EXTRA_MOVIE_ID = "movieId"
        private const val EXTRA_TITLE = "movieTitle"
        private const val EXTRA_URL = "movieUrl"

        fun live(c: Context, number: Int) = Intent(c, PlayerActivity::class.java).putExtra(EXTRA_NUMBER, number)
        fun movie(c: Context, id: Int, title: String, url: String) = Intent(c, PlayerActivity::class.java)
            .putExtra(EXTRA_MOVIE_ID, id).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_URL, url)
    }
}
