package world.ebuzz.tv.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import world.ebuzz.tv.di.AppContainer
import world.ebuzz.tv.domain.model.Channel
import world.ebuzz.tv.domain.model.HomeState
import world.ebuzz.tv.domain.model.Movie
import world.ebuzz.tv.domain.model.MovieCategory
import world.ebuzz.tv.domain.model.MovieQuery
import world.ebuzz.tv.domain.model.ResumePoint

data class HomeUiState(
    val moviesTab: Boolean = false,
    val query: String = "",
    val channels: List<Channel> = emptyList(),          // already filtered by query
    val focusChannelNumber: Int? = null,                // one-shot: tile to focus after the first load
    val movies: List<Movie> = emptyList(),
    val categories: List<MovieCategory> = emptyList(),
    val categoryId: Int? = null,
    val resume: ResumePoint? = null,
    val loading: Boolean = false,
    val error: Boolean = false,
    val empty: Boolean = false,
)

class HomeViewModel(private val c: AppContainer, private val moviesAvailable: Boolean) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var allChannels: List<Channel> = emptyList()
    private var nextMoviePage: Int? = 1
    private var movieJob: Job? = null
    private var searchJob: Job? = null

    init {
        val saved = c.getHomeState()
        _state.update { it.copy(categories = c.getMovieCategories(), categoryId = saved.categoryId) }
        selectTab(moviesAvailable && saved.moviesTab)
    }

    fun selectTab(movies: Boolean) {
        _state.update { it.copy(moviesTab = movies, query = "", error = false, empty = false, resume = if (movies) c.getResumePoint() else null) }
        persist()
        if (movies) { if (_state.value.movies.isEmpty()) reloadMovies() else _state.update { it.copy(loading = false) } }
        else loadChannels()
    }

    fun setQuery(q: String) {
        if (q == _state.value.query) return
        _state.update { it.copy(query = q) }
        if (!_state.value.moviesTab) { _state.update { it.copy(channels = c.filterChannels(allChannels, q)) }; return }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { delay(SEARCH_DEBOUNCE_MS); reloadMovies() }
    }

    fun selectCategory(id: Int?) {
        if (id == _state.value.categoryId) return
        _state.update { it.copy(categoryId = id) }
        persist(); reloadMovies()
    }

    fun retry() = if (_state.value.moviesTab) loadMoreMovies() else loadChannels()

    fun refreshResume() = _state.update { it.copy(resume = if (it.moviesTab) c.getResumePoint() else null) }

    fun hasChannel(number: Int) = allChannels.any { it.number == number }

    fun consumeFocus() = _state.update { it.copy(focusChannelNumber = null) }

    private fun loadChannels() {
        if (allChannels.isNotEmpty()) { _state.update { it.copy(channels = c.filterChannels(allChannels, it.query), loading = false) }; return }
        _state.update { it.copy(loading = true, error = false) }
        viewModelScope.launch {
            runCatching { c.getChannels() }
                .onSuccess { list ->
                    allChannels = list
                    _state.update { it.copy(channels = c.filterChannels(list, it.query), loading = false, focusChannelNumber = c.getLastChannel()) }
                }
                .onFailure { _state.update { it.copy(loading = false, error = true) } }
        }
    }

    private fun reloadMovies() {
        movieJob?.cancel(); nextMoviePage = 1
        _state.update { it.copy(movies = emptyList(), empty = false) }
        loadMoreMovies()
    }

    fun loadMoreMovies() {
        val page = nextMoviePage ?: return
        if (movieJob?.isActive == true) return
        val s = _state.value
        _state.update { it.copy(loading = s.movies.isEmpty(), error = false) }
        movieJob = viewModelScope.launch {
            runCatching { c.getMoviesPage(MovieQuery(page, s.categoryId, s.query)) }
                .onSuccess { p ->
                    nextMoviePage = p.nextPage
                    _state.update { it.copy(movies = it.movies + p.items, loading = false, empty = it.movies.isEmpty() && p.items.isEmpty()) }
                }
                .onFailure { _state.update { it.copy(loading = false, error = it.movies.isEmpty()) } }
        }
    }

    private fun persist() = _state.value.let { c.saveHomeState(HomeState(it.moviesTab, it.categoryId)) }

    private companion object { const val SEARCH_DEBOUNCE_MS = 400L }
}
