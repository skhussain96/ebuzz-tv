package world.ebuzz.tv.core.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import world.ebuzz.tv.core.data.PrefsCatalogStateStore
import world.ebuzz.tv.domain.model.MovieQuery
import world.ebuzz.tv.domain.model.MovieSort

data class CatalogUiState(
    val query: String = "",
    val tiles: List<PosterTile> = emptyList(),
    val categoryId: Int? = null,
    val sort: MovieSort = MovieSort.ADDED,
    val shortcut: CatalogShortcut? = null,
    val loading: Boolean = false,
    val error: Boolean = false,
    val empty: Boolean = false,
)

/** Paged list where search AND category AND sort always combine into one query. */
class CatalogViewModel(private val source: CatalogSource, private val store: PrefsCatalogStateStore) : ViewModel() {
    private val _state = MutableStateFlow(CatalogUiState(
        categoryId = store.category(source.key),
        sort = store.sort(source.key).takeIf { it in source.sorts() } ?: MovieSort.ADDED,
        shortcut = source.shortcut(),
    ))
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    private var nextPage: Int? = 1
    private var job: Job? = null
    private var searchJob: Job? = null

    init { loadMore() }

    fun setQuery(q: String) {
        if (q == _state.value.query) return
        _state.update { it.copy(query = q) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch { delay(SEARCH_DEBOUNCE_MS); reload() }
    }

    fun selectCategory(id: Int?) { if (id != _state.value.categoryId) { _state.update { it.copy(categoryId = id) }; persistAndReload() } }
    fun selectSort(sort: MovieSort) { if (sort != _state.value.sort) { _state.update { it.copy(sort = sort) }; persistAndReload() } }
    fun refreshShortcut() = _state.update { it.copy(shortcut = source.shortcut()) }
    fun retry() = loadMore()

    private fun persistAndReload() { _state.value.let { store.save(source.key, it.categoryId, it.sort) }; reload() }

    private fun reload() {
        job?.cancel(); nextPage = 1; source.reset()
        _state.update { it.copy(tiles = emptyList(), empty = false) }
        loadMore()
    }

    fun loadMore() {
        val page = nextPage ?: return
        if (job?.isActive == true) return
        val s = _state.value
        _state.update { it.copy(loading = s.tiles.isEmpty(), error = false) }
        job = viewModelScope.launch {
            runCatching { source.page(MovieQuery(page, s.categoryId, s.query, s.sort)) }
                .onSuccess { p ->
                    nextPage = p.nextPage
                    _state.update { val all = source.arrange(it.tiles + p.tiles, s.sort); it.copy(tiles = all, loading = false, empty = all.isEmpty()) }
                }
                .onFailure { _state.update { it.copy(loading = false, error = it.tiles.isEmpty()) } }
        }
    }

    private companion object { const val SEARCH_DEBOUNCE_MS = 400L }
}
