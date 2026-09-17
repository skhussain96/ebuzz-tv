package world.ebuzz.tv.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import world.ebuzz.tv.core.data.AppContainer
import world.ebuzz.tv.domain.model.Channel

data class LiveUiState(
    val query: String = "",
    val channels: List<Channel> = emptyList(),          // already filtered by query
    val focusNumber: Int? = null,                       // one-shot: tile to focus after the first load
    val loading: Boolean = false,
    val error: Boolean = false,
)

class LiveViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(LiveUiState())
    val state: StateFlow<LiveUiState> = _state.asStateFlow()
    private var all: List<Channel> = emptyList()

    init { load() }

    fun load() {
        if (all.isNotEmpty() || _state.value.loading) return
        _state.update { it.copy(loading = true, error = false) }
        viewModelScope.launch {
            runCatching { c.getChannels() }
                .onSuccess { list -> all = list; _state.update { it.copy(channels = c.filterChannels(list, it.query), loading = false, focusNumber = c.getLastChannel()) } }
                .onFailure { _state.update { it.copy(loading = false, error = true) } }
        }
    }

    /** Filtering is local so channel numbers never shift. */
    fun setQuery(q: String) = _state.update { it.copy(query = q, channels = c.filterChannels(all, q)) }
    fun hasChannel(number: Int) = all.any { it.number == number }
    fun consumeFocus() = _state.update { it.copy(focusNumber = null) }
}
