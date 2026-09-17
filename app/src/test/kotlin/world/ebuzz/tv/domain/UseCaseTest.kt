package world.ebuzz.tv.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import world.ebuzz.tv.domain.model.Channel
import world.ebuzz.tv.domain.model.Movie
import world.ebuzz.tv.domain.model.MovieCategory
import world.ebuzz.tv.domain.model.MoviePage
import world.ebuzz.tv.domain.model.MovieQuery
import world.ebuzz.tv.domain.model.ResumePoint
import world.ebuzz.tv.domain.repository.MovieRepository
import world.ebuzz.tv.domain.repository.PlaybackStore
import world.ebuzz.tv.domain.usecase.ContentPolicy
import world.ebuzz.tv.domain.usecase.FilterChannels
import world.ebuzz.tv.domain.usecase.GetMovieProgress
import world.ebuzz.tv.domain.usecase.GetMoviesPage
import world.ebuzz.tv.domain.usecase.SaveMovieProgress
import world.ebuzz.tv.domain.usecase.StepChannel

class UseCaseTest {
    private fun movie(id: Int, title: String = "T$id", genre: String = "Action", desc: String = "") =
        Movie(id, title, null, "2020", "7.0", "HDRip", "https://x/$id.mp4", genre, desc)

    @Test fun `content policy blocks romance and adult keywords but keeps xXx`() {
        val p = ContentPolicy()
        assertFalse(p.allows(movie(1, genre = "Drama,Romance")))
        assertFalse(p.allows(movie(2, title = "Sex and the Teenage Mind")))
        assertFalse(p.allows(movie(3, desc = "A housewife turns to prostitution to pass the time.")))
        assertFalse(p.allows(movie(4, title = "XXX-mas")))
        assertTrue(p.allows(movie(5, title = "xXx: Return of Xander Cage")))
        assertTrue(p.allows(movie(6, title = "Jumanji", desc = "Four adults are sucked into a game.")))
    }

    @Test fun `channels filter by name or number prefix`() {
        val all = listOf(Channel(1, 1, "CNN", null, "u"), Channel(2, 12, "PTV Sports", null, "u"), Channel(3, 120, "Geo News", null, "u"))
        val f = FilterChannels()
        assertEquals(listOf(12, 120), f(all, "12").map { it.number })
        assertEquals(listOf(120), f(all, "geo").map { it.number })
        assertEquals(3, f(all, "  ").size)
    }

    @Test fun `stepping wraps both ways`() {
        val step = StepChannel()
        assertEquals(0, step(5, 4, 1)); assertEquals(4, step(5, 0, -1)); assertEquals(0, step(0, 0, 1))
    }

    @Test fun `movie page skips fully filtered pages`() = runTest {
        val repo = object : MovieRepository {
            override fun categories() = emptyList<MovieCategory>()
            override suspend fun page(query: MovieQuery) = when (query.page) {
                1 -> MoviePage(listOf(movie(1, genre = "Romance")), 2)
                else -> MoviePage(listOf(movie(2)), null)
            }
        }
        val page = GetMoviesPage(repo, ContentPolicy())(MovieQuery(1, null, ""))
        assertEquals(listOf(2), page.items.map { it.id }); assertNull(page.nextPage)
        assertEquals("", page.items[0].description)
    }

    @Test fun `progress is kept only mid-movie`() {
        val store = object : PlaybackStore {
            val saved = HashMap<Int, Long>()
            override var lastChannelNumber = 1; override var volume = 1f
            override fun progress(movieId: Int) = saved[movieId] ?: 0
            override fun saveProgress(point: ResumePoint) { saved[point.movieId] = point.positionMs }
            override fun clearProgress(movieId: Int) { saved.remove(movieId) }
            override fun resumePoint(): ResumePoint? = null
        }
        val save = SaveMovieProgress(store); val get = GetMovieProgress(store)
        val hour = 3_600_000L
        save(ResumePoint(1, "t", "u", 600_000), hour); assertEquals(600_000, get(1))
        save(ResumePoint(1, "t", "u", 5_000), hour); assertEquals(0, get(1))                 // too early to bother
        save(ResumePoint(1, "t", "u", hour - 10_000), hour); assertEquals(0, get(1))        // credits: start over next time
    }
}
