@file:OptIn(ExperimentalJsExport::class, DelicateCoroutinesApi::class)

package world.ebuzz.tv.js

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import world.ebuzz.filter.Subject
import world.ebuzz.tv.data.remote.EbuzzApi
import world.ebuzz.tv.data.remote.TmdbEvidenceSource
import world.ebuzz.tv.data.repository.ChannelRepositoryImpl
import world.ebuzz.tv.data.repository.MovieRepositoryImpl
import world.ebuzz.tv.domain.model.MovieQuery
import world.ebuzz.tv.domain.model.MovieSort
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
class EbuzzSdk(baseUrl: String, tmdbBaseUrl: String = "") {
    private val api = EbuzzApi(baseUrl, emptyMap())
    private val policy = ContentPolicy(tmdbBaseUrl.takeIf(String::isNotEmpty)?.let { TmdbEvidenceSource(it) }, LocalStorageVerdictStore)
    private val movieRepo = MovieRepositoryImpl(api)
    private val getChannels = GetChannels(ChannelRepositoryImpl(api), policy)
    private val getMoviesPage = GetMoviesPage(movieRepo, policy)
    private val getCategories = GetMovieCategories(movieRepo)

    /** The one content policy every client shares. */
    fun allows(title: String, genre: String, description: String, rated: String = ""): Boolean = policy.allows(title, genre, description, rated)

    // Local rules, then TMDB through [tmdbBaseUrl] (the server's /tmdb/ proxy, which holds the key).
    fun screen(title: String, genre: String, description: String, rated: String, imdbId: String): Promise<Boolean> =
        GlobalScope.promise { policy.screen(Subject(title, genre, description, rated, imdbId)) }

    fun categories(): Array<JsCategory> = getCategories().map { JsCategory(it.id, it.name) }.toTypedArray()

    fun channels(): Promise<Array<JsChannel>> = GlobalScope.promise {
        getChannels().map { JsChannel(it.id, it.number, it.title, it.poster, it.streamUrl) }.toTypedArray()
    }

    /** [sort] is a [MovieSort] name: ADDED, YEAR, TITLE, RATING, VIEWS, QUALITY. Search, category and sort combine. */
    fun moviesPage(page: Int, categoryId: Int?, text: String, sort: String = "ADDED"): Promise<JsMoviePage> = GlobalScope.promise {
        val p = getMoviesPage(MovieQuery(page, categoryId, text, MovieSort.entries.firstOrNull { it.name == sort } ?: MovieSort.ADDED))
        JsMoviePage(p.items.map { JsMovie(it.id, it.title, it.poster, it.year, it.rating, it.quality, it.streamUrl) }.toTypedArray(), p.nextPage)
    }
}

// Browser-side verdict cache; absent or full storage (private window, Node) just means no caching.
private object LocalStorageVerdictStore : world.ebuzz.filter.VerdictStore {
    private val storage: dynamic = js("(function(){try{return typeof localStorage==='undefined'?null:localStorage}catch(e){return null}})()")
    override fun get(key: String): Boolean? = try { when (storage?.getItem("vd:$key") as String?) { "1" -> true; "0" -> false; else -> null } } catch (e: Throwable) { null }
    override fun put(key: String, allowed: Boolean) { try { storage?.setItem("vd:$key", if (allowed) "1" else "0") } catch (e: Throwable) { } }
}
