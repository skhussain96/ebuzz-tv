package world.ebuzz.tv.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Thin HTTP client for the eBuzz API, shared by Android and the web.
 *
 * Android talks to the API directly and must send [BROWSER_HEADERS]: the API rejects anything that doesn't look
 * like a browser navigation. A browser can't set those headers and is blocked by CORS, so the web passes the
 * same-origin proxy path as [baseUrl] and no headers.
 */
class EbuzzApi internal constructor(
    private val baseUrl: String,
    private val requestHeaders: Map<String, String>,
    private val client: HttpClient,
) {
    /** Public entry point; keeps Ktor types out of the modules that depend on this one. */
    constructor(baseUrl: String = DIRECT_BASE_URL, requestHeaders: Map<String, String> = BROWSER_HEADERS) :
        this(baseUrl, requestHeaders, HttpClient())

    suspend fun get(path: String): JsonObject {
        val response = client.get(baseUrl + path) { requestHeaders.forEach { (k, v) -> header(k, v) } }
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}")
        return Json.parseToJsonElement(response.bodyAsText()).jsonObject
    }

    companion object {
        const val DIRECT_BASE_URL = "https://api.ebuzz.world/api/v1/"
        const val USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36"
        val BROWSER_HEADERS = mapOf(
            "accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            "accept-language" to "en-US,en;q=0.9",
            "sec-ch-ua" to "\"Chromium\";v=\"152\", \"Not?A_Brand\";v=\"24\", \"Google Chrome\";v=\"152\"",
            "sec-ch-ua-mobile" to "?0",
            "sec-ch-ua-platform" to "\"macOS\"",
            "sec-fetch-dest" to "document",
            "sec-fetch-mode" to "navigate",
            "sec-fetch-site" to "none",
            "sec-fetch-user" to "?1",
            "upgrade-insecure-requests" to "1",
            "user-agent" to USER_AGENT,
        )
    }
}
