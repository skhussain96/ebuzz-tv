package world.ebuzz.tv

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import kotlinx.coroutines.launch
import world.ebuzz.tv.databinding.ActivityPlayerBinding
import kotlin.math.roundToInt

/**
 * Five controls: LEFT/RIGHT previous/next channel, UP/DOWN volume, OK/space/play-pause toggle, BACK exits.
 * Digits jump to a channel number.
 */
class PlayerActivity : AppCompatActivity() {
    private lateinit var b: ActivityPlayerBinding
    private lateinit var player: ExoPlayer
    private lateinit var digits: DigitEntry
    private val ui = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("ebuzz", MODE_PRIVATE) }
    private var channels: List<Channel> = emptyList()
    private var index = 0
    private var volume = 1f

    private val hideOsd = Runnable { b.osd.visibility = View.GONE }
    private val hideVol = Runnable { b.volBox.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyOrientation()
        b = ActivityPlayerBinding.inflate(layoutInflater).also { setContentView(it.root) }
        volume = prefs.getFloat("volume", 1f)
        digits = DigitEntry(b.chNum) { n -> if (n in 1..channels.size) tune(n - 1) }

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(ChannelRepo.browserHeaders.getValue("user-agent"))
            .setAllowCrossProtocolRedirects(true)
        // Live TV needs only a few seconds of buffer; ExoPlayer's default keeps ~50 s in RAM.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(4_000, 12_000, 1_500, 2_500)
            .setBackBuffer(0, false)
            .build()
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(HlsMediaSource.Factory(http))
            .setLoadControl(loadControl)
            .build().also { p ->
                b.playerView.player = p
                p.playWhenReady = true
                p.volume = volume
                p.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        b.spinner.visibility = if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
                        if (state == Player.STATE_BUFFERING) { b.osd.visibility = View.VISIBLE; ui.removeCallbacks(hideOsd) } else showOsd()
                        if (state == Player.STATE_READY) b.error.visibility = View.GONE
                    }
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        b.pausedIcon.visibility = if (!isPlaying && player.playbackState == Player.STATE_READY && !player.playWhenReady) View.VISIBLE else View.GONE
                    }
                    override fun onRenderedFirstFrame() { b.playerView.visibility = View.VISIBLE }
                    override fun onPlayerError(error: PlaybackException) {
                        b.spinner.visibility = View.GONE
                        b.error.text = "Stream unavailable"; b.error.visibility = View.VISIBLE
                    }
                })
            }

        lifecycleScope.launch {
            channels = runCatching { ChannelRepo.channels() }.getOrDefault(emptyList())
            if (channels.isEmpty()) { b.error.text = getString(R.string.error); b.error.visibility = View.VISIBLE; return@launch }
            tune((intent.getIntExtra("number", prefs.getInt("last", 1)) - 1).coerceIn(0, channels.size - 1))
        }
    }

    private fun tune(i: Int) {
        index = i
        val c = channels[i]
        prefs.edit().putInt("last", c.number).apply()
        b.error.visibility = View.GONE
        b.osdTitle.text = "${c.number} · ${c.title}"
        b.playerView.visibility = View.INVISIBLE          // hide the old channel's last frame until the new one renders
        player.stop(); player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(c.url))
        player.prepare(); player.play()
        showOsd()
    }

    private fun showOsd() {
        b.osd.visibility = View.VISIBLE
        ui.removeCallbacks(hideOsd); ui.postDelayed(hideOsd, 3000)
    }

    private fun setVolume(v: Float) {
        volume = (v.coerceIn(0f, 1f) * 10).roundToInt() / 10f
        player.volume = volume
        prefs.edit().putFloat("volume", volume).apply()
        val pct = (volume * 100).roundToInt()
        b.volText.text = pct.toString()
        b.volIcon.setImageResource(if (pct == 0) android.R.drawable.ic_lock_silent_mode else android.R.drawable.ic_lock_silent_mode_off)
        (b.volFill.layoutParams as android.widget.LinearLayout.LayoutParams).weight = volume
        b.volFill.requestLayout()
        b.volBox.visibility = View.VISIBLE
        ui.removeCallbacks(hideVol); ui.postDelayed(hideVol, 2000)
    }

    /** Long-press OK: cycle through the stream's audio tracks (only when it carries more than one). */
    private fun cycleAudio() {
        val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
        if (groups.size < 2) { toast("Single audio track"); return }
        val cur = groups.indexOfFirst { it.isSelected }
        val next = groups[(cur + 1) % groups.size]
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(next.mediaTrackGroup, 0)).build()
        val f = next.getTrackFormat(0)
        toast("Audio: " + (f.label ?: f.language ?: "track ${(cur + 1) % groups.size + 1}"))
    }

    private fun toast(msg: String) {
        b.osdTitle.text = "${channels[index].number} · ${channels[index].title}  ·  $msg"; showOsd()
    }

    private fun togglePlay() {
        if (player.isPlaying) player.pause() else { player.playWhenReady = true; player.play() }
        showOsd()
    }

    private var okLongPressed = false

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) { okLongPressed = true; cycleAudio(); return true }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (!okLongPressed && channels.isNotEmpty()) togglePlay()
            okLongPressed = false; return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (digits.onKey(keyCode)) return true
        if (channels.isEmpty()) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { event.startTracking(); return true }
            KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK -> cycleAudio()
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_PAGE_UP ->
                tune((index + 1) % channels.size)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_PAGE_DOWN ->
                tune((index - 1 + channels.size) % channels.size)
            KeyEvent.KEYCODE_DPAD_UP -> setVolume(volume + 0.1f)
            KeyEvent.KEYCODE_DPAD_DOWN -> setVolume(volume - 0.1f)
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP ->
                togglePlay()
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> finish()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    override fun onStop() { super.onStop(); player.pause() }
    override fun onStart() { super.onStart(); if (channels.isNotEmpty()) player.play() }
    override fun onDestroy() { player.release(); super.onDestroy() }
}
