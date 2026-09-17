package world.ebuzz.tv.domain.usecase

import world.ebuzz.tv.domain.model.Movie
import world.ebuzz.tv.domain.model.MovieCategory
import world.ebuzz.tv.domain.model.MoviePage
import world.ebuzz.tv.domain.model.MovieQuery
import world.ebuzz.tv.domain.repository.MovieRepository

/** BluRay first, cam rips last; unknown labels sink to the end. Stable, so API order is kept within a rank. */
class RankByQuality {
    operator fun invoke(movies: List<Movie>): List<Movie> = movies.sortedBy(::rank)

    private fun rank(m: Movie): Int = LADDER.indexOf(m.quality.lowercase().filterNot(Char::isWhitespace)).let { if (it < 0) LADDER.size else it }

    private companion object {
        val LADDER = listOf("bluray", "brrip", "bdrip", "webdl", "web-dl", "webrip", "hdrip", "hdtv", "dvdrip", "master", "dvdscr", "hdcam", "hdts", "cam", "ts")
    }
}

class GetMovieCategories(private val repo: MovieRepository) {
    operator fun invoke(): List<MovieCategory> = repo.categories()
}

/**
 * Loads the next page of allowed movies. If the content policy empties a page, keeps going
 * (bounded) so the UI never receives an empty page while more exist.
 */
class GetMoviesPage(private val repo: MovieRepository, private val policy: ContentPolicy) {
    suspend operator fun invoke(query: MovieQuery): MoviePage {
        var q = query
        repeat(MAX_SKIPS) {
            val page = repo.page(q)
            // descriptions are only needed for filtering; drop them to keep the list light in memory
            val allowed = page.items.filter(policy::allows).map { it.copy(description = "", genre = "") }
            if (allowed.isNotEmpty() || page.nextPage == null) return MoviePage(allowed, page.nextPage)
            q = q.copy(page = page.nextPage)
        }
        return MoviePage(emptyList(), q.page)
    }
    private companion object { const val MAX_SKIPS = 4 }
}
