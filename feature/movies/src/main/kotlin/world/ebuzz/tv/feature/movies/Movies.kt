package world.ebuzz.tv.feature.movies

import android.content.Context
import androidx.fragment.app.Fragment
import world.ebuzz.tv.core.catalog.CatalogFragment
import world.ebuzz.tv.core.catalog.CatalogShortcut
import world.ebuzz.tv.core.catalog.CatalogSource
import world.ebuzz.tv.core.catalog.PosterTile
import world.ebuzz.tv.core.catalog.TilePage
import world.ebuzz.tv.core.data.AppContainer
import world.ebuzz.tv.core.data.container
import world.ebuzz.tv.core.playback.PlayerIntents
import world.ebuzz.tv.core.ui.HomeSection
import world.ebuzz.tv.data.repository.MovieRepositoryImpl
import world.ebuzz.tv.domain.model.Movie
import world.ebuzz.tv.domain.model.MovieQuery
import world.ebuzz.tv.domain.model.MovieSort
import world.ebuzz.tv.domain.usecase.GetMovieCategories
import world.ebuzz.tv.domain.usecase.GetMoviesPage
import world.ebuzz.tv.domain.usecase.RankByQuality

object MoviesSection : HomeSection {
    override val id = "movies"
    override val title = "Movies"
    override fun newFragment(): Fragment = MoviesFragment()
}

class MoviesFragment : CatalogFragment() {
    override fun source(context: Context): CatalogSource = MoviesSource(context.container)
}

/** The Movies feature's own object graph, built on the core container; editions without Movies never create it. */
internal class MoviesSource(private val c: AppContainer) : CatalogSource {
    private val repo = MovieRepositoryImpl(c.api)
    private val getCategories = GetMovieCategories(repo)
    private val getPage = GetMoviesPage(repo, c.policy)
    private val rankByQuality = RankByQuality()
    private val loaded = HashMap<Int, Movie>()

    override val key = "movies"
    override val searchHint = "Search movies"
    override fun categories() = getCategories()
    override fun sorts() = MovieSort.entries

    override suspend fun page(query: MovieQuery): TilePage = getPage(query).let { p ->
        p.items.forEach { loaded[it.id] = it }
        TilePage(p.items.map { it.toTile() }, p.nextPage)
    }

    /** Quality is free text upstream, so "Best quality" is ranked here; the badge of a tile is its quality. */
    override fun arrange(tiles: List<PosterTile>, sort: MovieSort) = if (sort.rankedOnClient) rankByQuality(tiles, PosterTile::badge) else tiles

    override fun open(context: Context, tileId: Int) {
        loaded[tileId]?.let { context.startActivity(PlayerIntents.movie(context, it.id, it.title, it.streamUrl)) }
    }

    override fun shortcut(): CatalogShortcut? = c.getResumePoint()?.let { r ->
        CatalogShortcut("▶  Resume · ${r.title}") { ctx -> ctx.startActivity(PlayerIntents.movie(ctx, r.movieId, r.title, r.streamUrl)) }
    }

    override fun reset() = loaded.clear()

    private fun Movie.toTile() = PosterTile(id, title, listOf(year, rating.takeIf(String::isNotEmpty)?.let { "★ $it" }.orEmpty()).filter(String::isNotEmpty).joinToString("  ·  "), quality, poster)
}
