package world.ebuzz.tv.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Thin HTTP client for api.ebuzz.world. The API rejects anything that doesn't look like a browser navigation. */
class EbuzzApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    /** Blocking; call from an IO dispatcher. */
    fun get(path: String): JSONObject {
        val req = Request.Builder().url(BASE + path).apply { BROWSER_HEADERS.forEach { (k, v) -> header(k, v) } }.build()
        return client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            JSONObject(r.body!!.string())
        }
    }

    companion object {
        private const val BASE = "https://api.ebuzz.world/api/v1/"
        const val USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36"
        private val BROWSER_HEADERS = mapOf(
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
