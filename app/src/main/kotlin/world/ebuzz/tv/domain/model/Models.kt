package world.ebuzz.tv.domain.model

data class Channel(val id: Int, val number: Int, val title: String, val poster: String?, val streamUrl: String)

data class Movie(
    val id: Int, val title: String, val poster: String?, val year: String, val rating: String, val quality: String,
    val streamUrl: String, val genre: String = "", val description: String = "",
)

data class MovieCategory(val id: Int, val name: String)

data class MovieQuery(val page: Int, val categoryId: Int?, val text: String)

/** [nextPage] is null when the catalogue is exhausted. */
data class MoviePage(val items: List<Movie>, val nextPage: Int?)

data class ResumePoint(val movieId: Int, val title: String, val streamUrl: String, val positionMs: Long)

data class HomeState(val moviesTab: Boolean, val categoryId: Int?)
