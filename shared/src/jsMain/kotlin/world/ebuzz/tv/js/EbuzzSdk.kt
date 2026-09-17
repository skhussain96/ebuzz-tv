@file:OptIn(ExperimentalJsExport::class, DelicateCoroutinesApi::class)

package world.ebuzz.tv.js

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import world.ebuzz.tv.data.remote.EbuzzApi
import world.ebuzz.tv.data.repository.ChannelRepositoryImpl
import world.ebuzz.tv.data.repository.MovieRepositoryImpl
import world.ebuzz.tv.domain.model.MovieQuery
import world.ebuzz.tv.domain.usecase.ContentPolicy
import world.ebuzz.tv.domain.usecase.GetChannels
import world.ebuzz.tv.domain.usecase.GetMovieCategories
import world.ebuzz.tv.domain.usecase.GetMoviesPage
import kotlin.js.Promise

// Plain JS-friendly mirrors of the domain models: arrays instead of Lists, Promises instead of suspend.
@JsExport class JsChannel(val id: Int, val number: Int, val title: String, val poster: String?, val streamUrl: String)
@JsExport class JsMovie(val id: Int, val title: String, val poster: String?, val year: String, val rating: String, val quality: String, val streamUrl: String)
@JsExport class JsCategory(val id: Int, val name: String)
@JsExport class JsMoviePage(val items: Array<JsMovie>, val nextPage: Int?)

/**
 * Entry point for the web player: `new ebuzzShared.world.ebuzz.tv.js.EbuzzSdk("/api/v1/")`.
 * [baseUrl] is the same-origin proxy path; the browser can neither send the API's required headers nor pass CORS.
 */
@JsExport
class EbuzzSdk(baseUrl: String) {
    private val api = EbuzzApi(baseUrl, emptyMap())
    private val policy = ContentPolicy()
    private val movieRepo = MovieRepositoryImpl(api)
    private val getChannels = GetChannels(ChannelRepositoryImpl(api), policy)
    private val getMoviesPage = GetMoviesPage(movieRepo, policy)
    private val getCategories = GetMovieCategories(movieRepo)

    /** The one content policy every client shares. */
    fun allows(title: String, genre: String, description: String): Boolean = policy.allows(title, genre, description)

    fun categories(): Array<JsCategory> = getCategories().map { JsCategory(it.id, it.name) }.toTypedArray()

    fun channels(): Promise<Array<JsChannel>> = GlobalScope.promise {
        getChannels().map { JsChannel(it.id, it.number, it.title, it.poster, it.streamUrl) }.toTypedArray()
    }

    fun moviesPage(page: Int, categoryId: Int?, text: String): Promise<JsMoviePage> = GlobalScope.promise {
        val p = getMoviesPage(MovieQuery(page, categoryId, text))
        JsMoviePage(p.items.map { JsMovie(it.id, it.title, it.poster, it.year, it.rating, it.quality, it.streamUrl) }.toTypedArray(), p.nextPage)
    }
}
