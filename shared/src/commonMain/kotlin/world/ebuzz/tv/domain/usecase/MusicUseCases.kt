package world.ebuzz.tv.domain.usecase

import world.ebuzz.tv.domain.model.AlbumPage
import world.ebuzz.tv.domain.model.MovieCategory
import world.ebuzz.tv.domain.model.MovieQuery
import world.ebuzz.tv.domain.model.MovieSort
import world.ebuzz.tv.domain.repository.MusicRepository

class GetMusicCategories(private val repo: MusicRepository) {
    operator fun invoke(): List<MovieCategory> = repo.categories()
}

class GetMusicSorts {
    operator fun invoke(): List<MovieSort> = MovieSort.entries.filter { it.forMusic }
}

/** Same contract as [GetMoviesPage]: search AND category AND sort combine, and policy-emptied pages are skipped. */
class GetMusicPage(private val repo: MusicRepository, private val policy: ContentPolicy) {
    suspend operator fun invoke(query: MovieQuery): AlbumPage {
        var q = if (query.sort.forMusic) query else query.copy(sort = MovieSort.ADDED)
        repeat(MAX_SKIPS) {
            val page = repo.page(q)
            val allowed = page.items.filter { it.tracks.isNotEmpty() && policy.allows(it) }.map { it.copy(description = "") }
            if (allowed.isNotEmpty() || page.nextPage == null) return AlbumPage(allowed, page.nextPage)
            q = q.copy(page = page.nextPage)
        }
        return AlbumPage(emptyList(), q.page)
    }
    private companion object { const val MAX_SKIPS = 4 }
}

/** Wrap-free stepping through an album: null at either end. */
class StepTrack {
    operator fun invoke(size: Int, index: Int, dir: Int): Int? = (index + dir).takeIf { it in 0 until size }
}
