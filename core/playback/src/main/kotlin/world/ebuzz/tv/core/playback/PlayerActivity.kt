package world.ebuzz.tv.core.playback

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
import androidx.activity.viewModels
import android.app.AlertDialog
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.common.VideoSize
import world.ebuzz.tv.core.ui.loadUrl
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import world.ebuzz.tv.core.data.container
import world.ebuzz.tv.core.playback.databinding.ActivityPlayerBinding
import world.ebuzz.tv.core.playback.usecase.CycleAudioTrack
import world.ebuzz.tv.core.playback.usecase.DescribeNowPlaying
import world.ebuzz.tv.core.playback.usecase.FormatProgress
import world.ebuzz.tv.core.playback.usecase.GetTrackTitles
import world.ebuzz.tv.core.playback.usecase.HasAudioChoice
import world.ebuzz.tv.core.playback.usecase.LoadStream
import world.ebuzz.tv.core.playback.usecase.MediaIds
import world.ebuzz.tv.core.playback.usecase.PlayTrackAt
import world.ebuzz.tv.core.playback.usecase.SeekBy
import world.ebuzz.tv.core.playback.usecase.SeekToFraction
import world.ebuzz.tv.core.playback.usecase.StartAlbum
import world.ebuzz.tv.core.playback.usecase.StepAlbumTrack
import world.ebuzz.tv.core.playback.usecase.TogglePlay
import world.ebuzz.tv.core.ui.DigitEntry
import world.ebuzz.tv.core.ui.R as UiR
import world.ebuzz.tv.core.ui.applyOrientation
import world.ebuzz.tv.core.ui.factory
import world.ebuzz.tv.core.ui.isTv
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The player screen for Live TV, films and albums. It is deliberately thin: it turns input (remote, touch,
 * pointer) into calls on the playback use cases, and paints what [PlayerOverlay] and the session report.
 * The ExoPlayer lives in [PlaybackService]; this screen drives it through a MediaController.
 *
 * Remote:  LEFT/RIGHT previous/next channel or track (±10 s in a film), UP/DOWN volume, OK/space pause, BACK exit,
 *          digits jump to a channel, long-press OK = audio track (track list in an album).
 * Touch:   swipe left/right = next/previous channel or track (±30 s in a film), vertical drag = volume,
 *          tap = buttons, double-tap = pause (live) or ±10 s by side.
 * Pointer: click = tap, wheel = volume, movement reveals the buttons.
 */
class PlayerActivity : ComponentActivity() {
    private lateinit var b: ActivityPlayerBinding
    private lateinit var overlay: PlayerOverlay
    private lateinit var digits: DigitEntry
    private val vm: PlayerViewModel by viewModels { factory { PlayerViewModel(container, PlayerIntents.args(intent)) } }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var player: MediaController? = null
    private var kind = PlayerKind.LIVE
    private var okLongPressed = false
    private var dragStartVolume = 1f

    // use cases
    private val loadStream = LoadStream(); private val startAlbum = StartAlbum()
    private val seekBy = SeekBy(); private val seekToFraction = SeekToFraction(); private val togglePlay = TogglePlay()
    private val stepAlbumTrack by lazy { StepAlbumTrack(container.stepTrack) }
    private val playTrackAt = PlayTrackAt(); private val getTrackTitles = GetTrackTitles()
    private val hasAudioChoice = HasAudioChoice(); private val cycleAudioTrack = CycleAudioTrack()
    private val describe = DescribeNowPlaying(); private val formatProgress = FormatProgress()

    private val ticker = Handler(Looper.getMainLooper())
    private var ticks = 0
    private val tick = object : Runnable {
        override fun run() {
            paintProgress()
            if (++ticks % 10 == 0) saveProgress()          // every 5 s, so a killed app still resumes
            ticker.postDelayed(this, 500)
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
        overlay = PlayerOverlay(b)
        kind = vm.kind
        digits = DigitEntry(b.chNum) { n -> if (kind == PlayerKind.LIVE) vm.tuneNumber(n) }
        bindInput()
        if (kind == PlayerKind.ALBUM) askForNotifications()

        controllerFuture = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync().also { f ->
            f.addListener({ runCatching { f.get() }.onSuccess(::onConnected).onFailure { overlay.error(getString(UiR.string.error_title)) } }, MoreExecutors.directExecutor())
        }
    }

    private fun onConnected(c: MediaController) {
        player = c
        c.setVideoSurfaceView(b.surface)
        c.addListener(listener)
        c.volume = vm.volume
        when (val a = vm.args) {
            is PlayerArgs.AlbumArgs -> startAlbum(c, a)
            PlayerArgs.Attach -> if (c.mediaItemCount == 0) { finish(); return } else kind = MediaIds.kindOf(c.currentMediaItem)
            else -> lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.CREATED) {
                    launch { vm.now.filterNotNull().collect { overlay.error(null); b.shutter.visibility = View.VISIBLE; loadStream(c, it); paint() } }
                    launch { vm.failed.collect { if (it) overlay.error(getString(UiR.string.error_title)) } }
                }
            }
        }
        overlay.applyMode(kind)
        if (kind == PlayerKind.ALBUM && !isTv) overlay.touchUi = true          // an album screen is its controls
        paint()
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            overlay.buffering(state == Player.STATE_BUFFERING)
            if (state == Player.STATE_READY) overlay.error(null)
            if (state == Player.STATE_BUFFERING) overlay.pin() else overlay.show(player?.playWhenReady == true)
            if (state == Player.STATE_ENDED && kind == PlayerKind.FILM) { vm.onEnded(); finish() }
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) = overlay.playing(isPlaying)
        override fun onMediaItemTransition(item: MediaItem?, reason: Int) = paint()
        override fun onMediaMetadataChanged(metadata: MediaMetadata) = paint()      // a controller learns titles and art a beat late
        override fun onRenderedFirstFrame() { if (kind != PlayerKind.ALBUM) b.shutter.visibility = View.GONE }
        override fun onVideoSizeChanged(size: VideoSize) { if (size.height > 0) b.surface.aspect = size.width * size.pixelWidthHeightRatio / size.height }
        override fun onTracksChanged(tracks: Tracks) { b.btnAudio.visibility = if (player?.let(hasAudioChoice::invoke) == true) View.VISIBLE else View.GONE }
        override fun onPlayerError(error: PlaybackException) = overlay.error("Stream unavailable")
    }

    /** Title, artwork and button state all come from the session, so a re-attached screen shows the truth. */
    private fun paint() {
        val p = player ?: return
        b.osdTitle.text = describe(p, kind).ifBlank { (vm.args as? PlayerArgs.AlbumArgs)?.title.orEmpty() }
        if (kind == PlayerKind.ALBUM) {
            b.shutter.visibility = View.VISIBLE                      // audio only: black behind the artwork
            b.art.loadUrl(p.mediaMetadata.artworkUri?.toString() ?: (vm.args as? PlayerArgs.AlbumArgs)?.poster)
        }
        overlay.playing(p.isPlaying); paintProgress(); overlay.show(p.playWhenReady)
    }

    private fun paintProgress() {
        val p = player ?: return
        if (kind == PlayerKind.LIVE || overlay.scrubbing) return
        val text = formatProgress(p.currentPosition, p.duration) ?: return
        b.seek.progress = (p.currentPosition * 1000 / p.duration).toInt(); b.osdRight.text = text
    }

    // ---- actions: each one is a use case plus feedback ----
    /** Next / previous: a channel on Live TV, a track in an album, ±10 s in a film. */
    private var seekSum = 0L
    private var seekAt = 0L

    private fun step(dir: Int) {
        val p = player ?: return
        when (kind) {
            PlayerKind.FILM -> seek(dir * 10_000L)
            PlayerKind.ALBUM -> overlay.hint(if (stepAlbumTrack(p, dir) == null) (if (dir > 0) "Last track" else "First track") else if (dir > 0) "Next  ›" else "‹  Previous")
            else -> vm.stepChannel(dir)
        }
    }

    private fun seek(ms: Long) {
        val p = player ?: return
        if (!seekBy(p, ms)) return
        val now = SystemClock.uptimeMillis()
        seekSum = if (now - seekAt < 900 && (seekSum > 0) == (ms > 0)) seekSum + ms else ms
        seekAt = now
        overlay.hint((if (seekSum > 0) "+" else "−") + "${abs(seekSum) / 1000}s", if (ms > 0) 1 else -1)
        paintProgress(); if (overlay.controlsVisible) overlay.show(p.playWhenReady)
    }

    private fun playPause() { val p = player ?: return; togglePlay(p); overlay.show(p.playWhenReady) }

    private fun nextAudio() { val p = player ?: return
        val name = cycleAudioTrack(p)
        if (name == null) overlay.hint("Single audio track") else { b.btnAudio.text = "Audio · $name"; overlay.hint("Audio: $name"); overlay.show(p.playWhenReady) }
    }

    private fun pickTrack() { val p = player ?: return
        AlertDialog.Builder(this).setTitle(p.mediaMetadata.albumTitle)
            .setSingleChoiceItems(getTrackTitles(p).toTypedArray(), p.currentMediaItemIndex) { d, i -> playTrackAt(p, i); d.dismiss() }
            .show()
    }

    private fun setVolume(v: Float, persist: Boolean) { vm.setVolume(v, persist); player?.volume = vm.volume; overlay.volume(vm.volume) }
    private fun stepVolume(dir: Int) = setVolume(((vm.volume * 10).roundToInt() + dir) / 10f, persist = true)
    private fun saveProgress() { val p = player ?: return; if (kind == PlayerKind.FILM) vm.saveProgress(p.currentPosition, p.duration) }

    // ---- input ----
    private fun bindInput() {
        b.btnPrev.setOnClickListener { step(-1) }
        b.btnNext.setOnClickListener { step(1) }
        b.btnPlay.setOnClickListener { playPause() }
        b.btnBack.setOnClickListener { finish() }
        b.btnAudio.setOnClickListener { nextAudio() }
        b.btnTracks.setOnClickListener { pickTrack() }
        b.seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) player?.let { p -> formatProgress(p.duration * progress / 1000, p.duration)?.let { b.osdRight.text = it } }
            }
            override fun onStartTrackingTouch(sb: SeekBar) { overlay.scrubbing = true; overlay.pin() }
            override fun onStopTrackingTouch(sb: SeekBar) {
                overlay.scrubbing = false
                player?.let { seekToFraction(it, sb.progress / 1000f); overlay.show(it.playWhenReady) }
            }
        })
        PlayerGestures(
            surface = b.root,
            onTap = { if (overlay.controlsVisible) overlay.hideNow() else overlay.reveal(player?.playWhenReady == true) },
            onDoubleTap = { right -> if (kind == PlayerKind.FILM) seek(if (right) 10_000 else -10_000) else step(if (right) 1 else -1) },
            onSwipe = { dir -> if (kind == PlayerKind.FILM) seek(dir * 30_000L) else step(dir) },
            onVerticalDrag = { f -> setVolume(dragStartVolume + f, persist = false) },
            onRelease = { vm.setVolume(vm.volume, persist = true); dragStartVolume = vm.volume },
        ).attach(onAnyTouch = { if (!overlay.touchUi) { overlay.touchUi = true; dragStartVolume = vm.volume } })
    }

    /** Mouse wheel = volume; pointer movement reveals the buttons. */
    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        if (e.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) when (e.actionMasked) {
            MotionEvent.ACTION_SCROLL -> { stepVolume(if (e.getAxisValue(MotionEvent.AXIS_VSCROLL) > 0) 1 else -1); return true }
            MotionEvent.ACTION_HOVER_MOVE -> if (!overlay.controlsVisible) { overlay.touchUi = true; overlay.reveal(player?.playWhenReady == true) }
        }
        return super.onGenericMotionEvent(e)
    }

    private fun isOk(keyCode: Int) = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (isOk(keyCode)) { okLongPressed = true; if (kind == PlayerKind.ALBUM) pickTrack() else nextAudio(); return true }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isOk(keyCode)) { if (!okLongPressed) playPause(); okLongPressed = false; return true }
        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (digits.onKey(keyCode)) return true
        if (keyCode != KeyEvent.KEYCODE_BACK) overlay.hideButtons()              // remote in use: keep the screen clean
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { event.startTracking(); return true }
            KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK -> nextAudio()
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_PAGE_UP -> step(1)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_PAGE_DOWN -> step(-1)
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> seek(30_000)
            KeyEvent.KEYCODE_MEDIA_REWIND -> seek(-30_000)
            KeyEvent.KEYCODE_DPAD_UP -> stepVolume(1)
            KeyEvent.KEYCODE_DPAD_DOWN -> stepVolume(-1)
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> playPause()
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> finish()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    // ---- lifecycle ----
    /** The media notification is how background music is controlled; Android 13+ needs consent to show it. */
    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    override fun onStart() { super.onStart(); ticker.post(tick); if (kind != PlayerKind.ALBUM) player?.takeIf { it.mediaItemCount > 0 }?.play() }

    /** Video has no business playing unseen; an album carries on in the background. */
    override fun onStop() { super.onStop(); ticker.removeCallbacks(tick); saveProgress(); if (kind != PlayerKind.ALBUM) player?.pause() }

    override fun onDestroy() {
        ticker.removeCallbacksAndMessages(null); overlay.release()
        player?.let { p ->
            p.removeListener(listener)
            if (isFinishing && kind != PlayerKind.ALBUM) { p.stop(); p.clearMediaItems() }        // leaving a channel or film ends it
        }
        player?.clearVideoSurfaceView(b.surface)
        controllerFuture?.let(MediaController::releaseFuture)
        super.onDestroy()
    }
}
