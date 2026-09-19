package world.ebuzz.tv.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import world.ebuzz.filter.Certification
import world.ebuzz.filter.Evidence
import world.ebuzz.filter.EvidenceSource

// TMDB accepts an IMDb id in place of its own. Android calls it directly with a key; the web passes the
// same-origin proxy path and no key (the server adds it and caches the answer on disk).
class TmdbEvidenceSource internal constructor(
    private val baseUrl: String, private val apiKey: String, private val client: HttpClient,
) : EvidenceSource {
    constructor(baseUrl: String = DIRECT_BASE_URL, apiKey: String = "") : this(baseUrl, apiKey, HttpClient())

    override suspend fun lookup(imdbId: String): Evidence? {
        val key = if (apiKey.isEmpty()) "" else "&api_key=$apiKey"
        val response = client.get("${baseUrl}movie/$imdbId?append_to_response=keywords,release_dates$key")
        if (response.status == HttpStatusCode.NotFound) return null
        if (response.status.value !in 200..299) error("HTTP ${response.status.value}")
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject.toEvidence()
    }

    companion object { const val DIRECT_BASE_URL = "https://api.themoviedb.org/3/" }
}

internal fun JsonObject.toEvidence() = Evidence(
    adult = (this["adult"] as? JsonPrimitive)?.booleanOrNull == true,
    genres = arr("genres").orEmpty().mapNotNull { (it as? JsonObject)?.str("name") },
    keywords = obj("keywords")?.arr("keywords").orEmpty().mapNotNull { (it as? JsonObject)?.str("name") },
    overview = str("overview"),
    certifications = obj("release_dates")?.arr("results").orEmpty().flatMap { r ->
        val country = (r as? JsonObject)?.str("iso_3166_1").orEmpty()
        (r as? JsonObject)?.arr("release_dates").orEmpty().mapNotNull { d ->
            (d as? JsonObject)?.str("certification")?.takeIf(String::isNotBlank)?.let { Certification(country, it) }
        }
    },
)
