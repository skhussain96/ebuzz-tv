package world.ebuzz.tv.domain.model

data class Channel(val id: Int, val number: Int, val title: String, val poster: String?, val streamUrl: String)

data class Movie(
    val id: Int, val title: String, val poster: String?, val year: String, val rating: String, val quality: String,
    val streamUrl: String, val genre: String = "", val description: String = "",
)

data class MovieCategory(val id: Int, val name: String)

data class Track(val name: String, val streamUrl: String)

/** A music album: the API calls its songs "mirrors". */
data class Album(val id: Int, val title: String, val poster: String?, val tracks: List<Track>, val description: String = "")

data class AlbumPage(val items: List<Album>, val nextPage: Int?)

/**
 * How a movie list is ordered. All but [QUALITY] are applied by the API across the whole catalogue;
 * quality has no meaningful server ordering (free text), so it is ranked on the client.
 */
enum class MovieSort(val label: String, val apiOrder: String, val rankedOnClient: Boolean = false, val forMusic: Boolean = false) {
    ADDED("Newest", "adate,desc|id,desc", forMusic = true),
    YEAR("Release year", "releasedate,desc|id,desc"),
    TITLE("Title A–Z", "title,asc", forMusic = true),
    RATING("Top rated", "rating,desc|id,desc"),
    VIEWS("Most viewed", "views,desc|id,desc", forMusic = true),
    QUALITY("Best quality", "adate,desc|id,desc", rankedOnClient = true),
}

/** Every filter travels together: search text AND category AND sort narrow the same result set. */
data class MovieQuery(val page: Int, val categoryId: Int?, val text: String, val sort: MovieSort = MovieSort.ADDED)

/** [nextPage] is null when the catalogue is exhausted. */
data class MoviePage(val items: List<Movie>, val nextPage: Int?)

data class ResumePoint(val movieId: Int, val title: String, val streamUrl: String, val positionMs: Long)
