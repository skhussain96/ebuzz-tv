package world.ebuzz.tv.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import world.ebuzz.tv.di.AppContainer
import world.ebuzz.tv.domain.model.Channel
import world.ebuzz.tv.domain.model.ResumePoint

/** What the player should be showing right now. A new instance means "load this". */
data class NowPlaying(val title: String, val streamUrl: String, val isLive: Boolean, val startPositionMs: Long = 0)

sealed interface PlayerArgs {
    data class Live(val number: Int?) : PlayerArgs
    data class Film(val id: Int, val title: String, val streamUrl: String) : PlayerArgs
}

class PlayerViewModel(private val c: AppContainer, private val args: PlayerArgs) : ViewModel() {
    private val _now = MutableStateFlow<NowPlaying?>(null)
    val now: StateFlow<NowPlaying?> = _now.asStateFlow()

    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    val isMovie = args is PlayerArgs.Film
    var volume: Float = c.getVolume()
        private set

    private var channels: List<Channel> = emptyList()
    private var index = 0
    val channelCount get() = channels.size

    init {
        when (args) {
            is PlayerArgs.Film -> _now.value = NowPlaying(args.title, args.streamUrl, false, c.getMovieProgress(args.id))
            is PlayerArgs.Live -> viewModelScope.launch {
                channels = runCatching { c.getChannels() }.getOrDefault(emptyList())
                if (channels.isEmpty()) _failed.value = true
                else tune(indexOfNumber(args.number ?: c.getLastChannel()) ?: 0)
            }
        }
    }

    private fun tune(i: Int) {
        index = i
        val ch = channels[i]
        c.setLastChannel(ch.number)
        _now.value = NowPlaying("${ch.number} · ${ch.title}", ch.streamUrl, true)
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
