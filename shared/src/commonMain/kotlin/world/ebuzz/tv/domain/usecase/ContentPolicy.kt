package world.ebuzz.tv.domain.usecase

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import world.ebuzz.filter.AdultFilter
import world.ebuzz.filter.EvidenceSource
import world.ebuzz.filter.Subject
import world.ebuzz.tv.domain.model.Album
import world.ebuzz.tv.domain.model.Channel
import world.ebuzz.tv.domain.model.Movie

// Maps the domain models onto the `:adultfilter` module, where every rule lives.
class ContentPolicy(source: EvidenceSource? = null) {
    private val filter = AdultFilter(source)

    fun allows(m: Movie): Boolean = filter.allows(m.subject())

    fun allows(c: Channel): Boolean = allows(c.title, "", "")

    fun allows(a: Album): Boolean = allows(a.title, "", a.description) && a.tracks.none { !allows(it.name, "", "") }

    fun allows(title: String, genre: String, description: String): Boolean = filter.allows(title, genre, description)

    fun allows(title: String, genre: String, description: String, rated: String): Boolean = filter.allows(Subject(title, genre, description, rated))

    suspend fun screen(s: Subject): Boolean = filter.screen(s)

    // Local rules first, then the evidence source for the survivors, a few lookups at a time (HttpURLConnection on old TVs).
    suspend fun screen(movies: List<Movie>): List<Movie> = coroutineScope {
        movies.filter(::allows).chunked(PARALLEL).flatMap { chunk ->
            chunk.map { m -> async { m.takeIf { filter.screen(it.subject()) } } }.awaitAll().filterNotNull()
        }
    }

    private fun Movie.subject() = Subject(title, genre, description, rated, imdbId)

    private companion object { const val PARALLEL = 8 }
}
