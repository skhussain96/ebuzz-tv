package world.ebuzz.tv

import kotlinx.coroutines.test.runTest
import world.ebuzz.tv.domain.model.Album
import world.ebuzz.tv.domain.model.AlbumPage
import world.ebuzz.tv.domain.model.Channel
import world.ebuzz.tv.domain.model.Movie
import world.ebuzz.tv.domain.model.MovieCategory
import world.ebuzz.tv.domain.model.MoviePage
import world.ebuzz.tv.domain.model.MovieQuery
import world.ebuzz.tv.domain.model.MovieSort
import world.ebuzz.tv.domain.model.ResumePoint
import world.ebuzz.tv.domain.model.Track
import world.ebuzz.tv.domain.repository.MovieRepository
import world.ebuzz.tv.domain.repository.MusicRepository
import world.ebuzz.tv.domain.repository.PlaybackStore
import world.ebuzz.tv.domain.usecase.ContentPolicy
import world.ebuzz.tv.domain.usecase.FilterChannels
import world.ebuzz.tv.domain.usecase.GetMovieProgress
import world.ebuzz.tv.domain.usecase.GetMoviesPage
import world.ebuzz.tv.domain.usecase.GetMusicPage
import world.ebuzz.tv.domain.usecase.GetMusicSorts
import world.ebuzz.tv.domain.usecase.StepTrack
import world.ebuzz.tv.domain.usecase.RankByQuality
import world.ebuzz.tv.domain.usecase.SaveMovieProgress
import world.ebuzz.tv.domain.usecase.StepChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UseCaseTest {
    private fun movie(id: Int, title: String = "T$id", genre: String = "Action", desc: String = "") =
        Movie(id, title, null, "2020", "7.0", "HDRip", "https://x/$id.mp4", genre, desc)

    private val policy = ContentPolicy()
    private fun blocked(title: String = "Plain", genre: String = "Action", desc: String = "") = !policy.allows(title, genre, desc)

    @Test fun policyBlocksGenres() {
        assertTrue(blocked(genre = "Drama,Romance")); assertTrue(blocked(genre = "Romantic Comedy"))
        assertTrue(blocked(genre = "Erotic Thriller")); assertTrue(blocked(genre = "Adult"))
    }

    @Test fun policyBlocksTitles() {
        listOf("Sex and the Teenage Mind", "XXX-mas", "Naked Weapon", "Hot Girls Wanted", "Secret Games: The Escort",
            "Strictly Sexual", "Kamasutra 3D", "Lust Stories", "The Seduction", "An Affair to Remember", "Bikini Beach",
            "Nymphomaniac: Vol. I", "Striptease", "Basic Instinct Unrated").forEach { assertTrue(blocked(title = it), it) }
    }

    @Test fun policyBlocksDescriptions() {
        listOf("A neglected housewife turns to prostitution to pass the time.", "She joins a local escort service.",
            "Two strangers begin a passionate affair.", "He sleeps with his best friend's wife.", "A steamy tale of forbidden love.",
            "The film contains nudity.", "A young woman is raped and seeks revenge.", "Her secret lover returns.",
            "A story of desire and betrayal.").forEach { assertTrue(blocked(desc = it), it) }
    }

    @Test fun policyKeepsOrdinaryTitles() {
        assertTrue(blocked(title = "xXx: Return of Xander Cage"))         // strict: "xxx" in any casing, accepted false positive
        assertFalse(blocked(title = "Jumanji", desc = "Four teenagers are sucked into a video game."))
        assertFalse(blocked(title = "PAW Patrol: The Dino Movie", genre = "Animation,Family"))
        assertFalse(blocked(title = "Essex Boys"))                       // word boundary, not substring
    }

    @Test fun policyAppliesToChannels() {
        assertFalse(policy.allows(Channel(1, 1, "Playboy TV", null, "u")))
        assertTrue(policy.allows(Channel(2, 2, "Geo News", null, "u")))
    }

    @Test fun channelsFilterByNameOrNumberPrefix() {
        val all = listOf(Channel(1, 1, "CNN", null, "u"), Channel(2, 12, "PTV Sports", null, "u"), Channel(3, 120, "Geo News", null, "u"))
        val f = FilterChannels()
        assertEquals(listOf(12, 120), f(all, "12").map { it.number })
        assertEquals(listOf(120), f(all, "geo").map { it.number })
        assertEquals(3, f(all, "  ").size)
    }

    @Test fun steppingWrapsBothWays() {
        val step = StepChannel()
        assertEquals(0, step(5, 4, 1)); assertEquals(4, step(5, 0, -1)); assertEquals(0, step(0, 0, 1))
    }

    @Test fun moviePageSkipsFullyFilteredPages() = runTest {
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

    @Test fun qualityRankingIsStableAndPutsUnknownLast() {
        val list = listOf(movie(1).copy(quality = "TS"), movie(2).copy(quality = "BRRip"), movie(3).copy(quality = "Weird"),
            movie(4).copy(quality = "HD Rip"), movie(5).copy(quality = "BRRip"))
        assertEquals(listOf(2, 5, 4, 1, 3), RankByQuality()(list).map { it.id })
    }

    @Test fun everySortHasAnApiOrderAndOnlyQualityIsClientRanked() {
        assertTrue(MovieSort.entries.all { it.apiOrder.isNotBlank() })
        assertEquals(listOf(MovieSort.QUALITY), MovieSort.entries.filter { it.rankedOnClient })
    }

    @Test fun albumsAreFilteredByTitleAndTrackNamesAndNeedTracks() = runTest {
        val ok = Album(1, "Sufi Hits", null, listOf(Track("Dam Mast Qalandar", "u/1.mp3")))
        val badTitle = Album(2, "Sexy Beats", null, listOf(Track("One", "u/2.mp3")))
        val badTrack = Album(3, "Party Mix", null, listOf(Track("Fine", "u/3.mp3"), Track("Naughty Girl", "u/4.mp3")))
        val empty = Album(4, "No Songs", null, emptyList())
        val repo = object : MusicRepository {
            override fun categories() = emptyList<MovieCategory>()
            override suspend fun page(query: MovieQuery) = AlbumPage(listOf(ok, badTitle, badTrack, empty), null)
        }
        assertEquals(listOf(1), GetMusicPage(repo, ContentPolicy())(MovieQuery(1, null, "")).items.map { it.id })
        assertEquals(listOf(MovieSort.ADDED, MovieSort.TITLE, MovieSort.VIEWS), GetMusicSorts()())
        assertEquals(1, StepTrack()(3, 0, 1)); assertNull(StepTrack()(3, 2, 1)); assertNull(StepTrack()(3, 0, -1))
    }

    @Test fun progressIsKeptOnlyMidMovie() {
        val store = object : PlaybackStore {
            val saved = HashMap<Int, Long>()
            override var lastChannelNumber = 1
            override var volume = 1f
            override fun progress(movieId: Int) = saved[movieId] ?: 0
            override fun saveProgress(point: ResumePoint) { saved[point.movieId] = point.positionMs }
            override fun clearProgress(movieId: Int) { saved.remove(movieId) }
            override fun resumePoint(): ResumePoint? = null
        }
        val save = SaveMovieProgress(store); val get = GetMovieProgress(store)
        val hour = 3_600_000L
        save(ResumePoint(1, "t", "u", 600_000), hour); assertEquals(600_000, get(1))
        save(ResumePoint(1, "t", "u", 5_000), hour); assertEquals(0, get(1))
        save(ResumePoint(1, "t", "u", hour - 10_000), hour); assertEquals(0, get(1))
    }
}
