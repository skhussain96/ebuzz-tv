package world.ebuzz.tv.core.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import world.ebuzz.tv.core.data.AppContainer
import world.ebuzz.tv.core.playback.usecase.MediaIds
import world.ebuzz.tv.domain.model.Channel
import world.ebuzz.tv.domain.model.ResumePoint
import world.ebuzz.tv.domain.model.Track

enum class PlayerKind { LIVE, FILM, ALBUM, ATTACH }

/** A single stream to load (live channel or film). A new instance means "load this". */
data class NowPlaying(val mediaId: String, val title: String, val streamUrl: String, val startPositionMs: Long = 0)

sealed interface PlayerArgs {
    data class Live(val number: Int?) : PlayerArgs
    data class Film(val id: Int, val title: String, val streamUrl: String) : PlayerArgs
    data class AlbumArgs(val id: Int, val title: String, val poster: String?, val tracks: List<Track>) : PlayerArgs
    /** Opened from the media notification: show whatever the session is already playing. */
    data object Attach : PlayerArgs
}

class PlayerViewModel(private val c: AppContainer, val args: PlayerArgs) : ViewModel() {
    val kind = when (args) {
        is PlayerArgs.Live -> PlayerKind.LIVE; is PlayerArgs.Film -> PlayerKind.FILM
        is PlayerArgs.AlbumArgs -> PlayerKind.ALBUM; PlayerArgs.Attach -> PlayerKind.ATTACH
    }

    private val _now = MutableStateFlow<NowPlaying?>(null)
    val now: StateFlow<NowPlaying?> = _now.asStateFlow()

    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    var volume: Float = c.getVolume()
        private set

    private var channels: List<Channel> = emptyList()
    private var index = 0

    init {
        when (args) {
            is PlayerArgs.Film -> _now.value = NowPlaying(MediaIds.film(args.id), args.title, args.streamUrl, c.getMovieProgress(args.id))
            is PlayerArgs.Live -> viewModelScope.launch {
                channels = runCatching { c.getChannels() }.getOrDefault(emptyList())
                if (channels.isEmpty()) _failed.value = true else tune(indexOfNumber(args.number ?: c.getLastChannel()) ?: 0)
            }
            else -> Unit                                   // albums are a playlist; the screen hands it to the session
        }
    }

    private fun tune(i: Int) {
        index = i
        val ch = channels[i]
        c.setLastChannel(ch.number)
        _now.value = NowPlaying(MediaIds.live(ch.number), "${ch.number} · ${ch.title}", ch.streamUrl)
    }

    /** Live TV only: previous / next channel with wrap-around. */
    fun stepChannel(dir: Int) { if (channels.isNotEmpty()) tune(c.stepChannel(channels.size, index, dir)) }

    fun tuneNumber(n: Int) { indexOfNumber(n)?.let(::tune) }

    private fun indexOfNumber(n: Int): Int? = channels.indexOfFirst { it.number == n }.takeIf { it >= 0 }

    fun setVolume(v: Float, persist: Boolean) { volume = v.coerceIn(0f, 1f); if (persist) c.setVolume(volume) }

    fun saveProgress(positionMs: Long, durationMs: Long) {
        val film = args as? PlayerArgs.Film ?: return
        c.saveMovieProgress(ResumePoint(film.id, film.title, film.streamUrl, positionMs), durationMs)
    }

    fun onEnded() { (args as? PlayerArgs.Film)?.let { c.clearMovieProgress(it.id) } }
}
