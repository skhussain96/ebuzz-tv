package world.ebuzz.tv.data.repository

import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.JsonObject
import world.ebuzz.tv.data.remote.EbuzzApi
import world.ebuzz.tv.data.remote.arr
import world.ebuzz.tv.data.remote.hasNextPage
import world.ebuzz.tv.data.remote.toMovie
import world.ebuzz.tv.domain.model.MovieCategory
import world.ebuzz.tv.domain.model.MoviePage
import world.ebuzz.tv.domain.model.MovieQuery
import world.ebuzz.tv.domain.repository.MovieRepository

class MovieRepositoryImpl(private val api: EbuzzApi) : MovieRepository {

    /** The API has no categories endpoint; ids and order come from the eBuzz app. */
    override fun categories() = listOf(
        MovieCategory(3, "Bollywood"), MovieCategory(25, "Kids Movies"), MovieCategory(16, "Wrestling"),
        MovieCategory(1, "Lollywood"), MovieCategory(13, "Stage Shows"), MovieCategory(26, "Dual Audio"),
        MovieCategory(4, "Hollywood"), MovieCategory(34, "Tamil Movies"), MovieCategory(36, "Punjabi Movies"),
        MovieCategory(37, "3D Movies"),
    )

    override suspend fun page(query: MovieQuery): MoviePage {
        val text = query.text.trim()
        val path = buildString {
            append("movies?includes=categories,mirrors&limit=$PAGE_SIZE&page=${query.page}&order_by=adate,desc|id,desc")
            query.categoryId?.let { append("&category=$it") }
            if (text.isNotEmpty()) append("&title=*").append(text.encodeURLParameter()).append("*")
        }
        val json = api.get(path)
        val items = json.arr("data").orEmpty().mapNotNull { (it as? JsonObject)?.toMovie() }
        return MoviePage(items, if (json.hasNextPage()) query.page + 1 else null)
    }

    private companion object { const val PAGE_SIZE = 60 }
}
