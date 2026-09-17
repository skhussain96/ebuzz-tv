package world.ebuzz.tv.core.catalog

import android.content.Context
import world.ebuzz.tv.domain.model.MovieCategory
import world.ebuzz.tv.domain.model.MovieQuery
import world.ebuzz.tv.domain.model.MovieSort

/** What a poster card shows, whatever it stands for (a movie or an album). */
data class PosterTile(val id: Int, val title: String, val subtitle: String, val badge: String, val poster: String?)

data class TilePage(val tiles: List<PosterTile>, val nextPage: Int?)

/** A shortcut shown above the grid, e.g. "Resume · title". */
data class CatalogShortcut(val label: String, val open: (Context) -> Unit)

/**
 * What a feature (Movies, Music) plugs into the catalog screen. The screen owns search, sort, category, paging
 * and state; the source owns the data and what opening a tile means.
 */
interface CatalogSource {
    /** Persistence key for the remembered category and sort. */
    val key: String
    val searchHint: String
    fun categories(): List<MovieCategory>
    fun sorts(): List<MovieSort>
    /** Search text AND category AND sort arrive together in [query]. */
    suspend fun page(query: MovieQuery): TilePage
    /** Client-side ordering for sorts the API can't do; identity otherwise. */
    fun arrange(tiles: List<PosterTile>, sort: MovieSort): List<PosterTile> = tiles
    fun open(context: Context, tileId: Int)
    fun shortcut(): CatalogShortcut? = null
    /** Forget cached items (a new query starts). */
    fun reset() {}
}
