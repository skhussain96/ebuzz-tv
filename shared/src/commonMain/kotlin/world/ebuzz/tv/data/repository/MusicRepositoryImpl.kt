package world.ebuzz.tv.data.repository

import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.JsonObject
import world.ebuzz.tv.data.remote.EbuzzApi
import world.ebuzz.tv.data.remote.arr
import world.ebuzz.tv.data.remote.hasNextPage
import world.ebuzz.tv.data.remote.toAlbum
import world.ebuzz.tv.domain.model.AlbumPage
import world.ebuzz.tv.domain.model.MovieCategory
import world.ebuzz.tv.domain.model.MovieQuery
import world.ebuzz.tv.domain.repository.MusicRepository

class MusicRepositoryImpl(private val api: EbuzzApi) : MusicRepository {

    /** No categories endpoint upstream; ids and order come from the eBuzz app. */
    override fun categories() = listOf(
        MovieCategory(4, "Bollywood Movies Songs"), MovieCategory(5, "Pakistani Songs"), MovieCategory(6, "Indian Pop and Remix"),
        MovieCategory(9, "Old Is Gold"), MovieCategory(10, "Turkish Musics"), MovieCategory(11, "Rap Songs"),
        MovieCategory(12, "Arabic Musics"), MovieCategory(13, "Russian Musics"), MovieCategory(3, "Hollywood Songs"),
        MovieCategory(15, "Breakup Mashup"), MovieCategory(14, "Sufi"), MovieCategory(16, "Uzbekistan"),
        MovieCategory(17, "Party Mashup"), MovieCategory(18, "Ultra Beats"), MovieCategory(22, "Punjabi Music"),
        MovieCategory(23, "Mili Naghame"),
    )

    override suspend fun page(query: MovieQuery): AlbumPage {
        val text = query.text.trim()
        val path = buildString {
            append("music?includes=categories,mirrors&limit=$PAGE_SIZE&page=${query.page}&order_by=${query.sort.apiOrder}")
            query.categoryId?.let { append("&category=$it") }
            if (text.isNotEmpty()) append("&title=*").append(text.encodeURLParameter()).append("*")
        }
        val json = api.get(path)
        val items = json.arr("data").orEmpty().mapNotNull { (it as? JsonObject)?.toAlbum() }
        return AlbumPage(items, if (json.hasNextPage()) query.page + 1 else null)
    }

    private companion object { const val PAGE_SIZE = 40 }
}
