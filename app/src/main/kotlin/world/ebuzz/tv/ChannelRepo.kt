package world.ebuzz.tv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ChannelRepo {
    private const val BASE = "https://api.ebuzz.world/api/v1/"

    // The API 403s anything that doesn't look like a browser navigation.
    val browserHeaders = mapOf(
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
        "user-agent" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36",
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    @Volatile var cache: List<Channel> = emptyList()

    suspend fun channels(): List<Channel> = cache.ifEmpty {
        withContext(Dispatchers.IO) {
            val out = ArrayList<Channel>()
            var page = 1
            while (true) {
                val req = Request.Builder()
                    .url("${BASE}channels?includes=categories&limit=250&order_by=id,asc&page=$page")
                    .apply { browserHeaders.forEach { (k, v) -> header(k, v) } }.build()
                val body = client.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) error("HTTP ${r.code}")
                    r.body!!.string()
                }
                val json = JSONObject(body)
                val data = json.getJSONArray("data")
                for (i in 0 until data.length()) {
                    val o = data.getJSONObject(i)
                    val url = o.optJSONObject("links")?.let { l ->
                        listOf("android", "web", "ios").firstNotNullOfOrNull { l.optString(it).takeIf(String::isNotBlank) }
                    } ?: continue
                    out += Channel(o.getInt("id"), out.size + 1, o.optString("title", "Channel"), o.optString("poster").takeIf(String::isNotBlank), url)
                }
                val meta = json.optJSONObject("meta")
                if (meta == null || meta.optInt("current_page") >= meta.optInt("last_page") || data.length() == 0) break
                page++
            }
            out.also { cache = it }
        }
    }
}
