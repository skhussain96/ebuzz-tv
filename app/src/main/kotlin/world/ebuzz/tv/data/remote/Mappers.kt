package world.ebuzz.tv.data.remote

import org.json.JSONObject
import world.ebuzz.tv.domain.model.Channel
import world.ebuzz.tv.domain.model.Movie

private val playable = Regex("\\.(mp4|mkv|m4v|webm|mov|m3u8)(\\?|$)", RegexOption.IGNORE_CASE)
private val yearRe = Regex("\\d{4}")

private fun String.clean() = takeUnless { isBlank() || this == "N/A" || this == "false" || this == "0" }.orEmpty()

/** `links` is an object of identical URLs keyed by platform; prefer the Android one. */
fun JSONObject.toChannel(number: Int): Channel? {
    val links = optJSONObject("links") ?: return null
    val url = listOf("android", "web", "ios").firstNotNullOfOrNull { links.optString(it).takeIf(String::isNotBlank) } ?: return null
    return Channel(getInt("id"), number, optString("title", "Channel"), optString("poster").takeIf(String::isNotBlank), url)
}

/** Movies list a .torrent and a direct file; only a direct, playable file makes a usable movie. */
fun JSONObject.toMovie(): Movie? {
    val mirrors = optJSONArray("mirrors") ?: return null
    val url = (0 until mirrors.length()).map { mirrors.getJSONObject(it).optString("url") }.firstOrNull(playable::containsMatchIn) ?: return null
    return Movie(
        id = getInt("id"), title = optString("title"), poster = optString("poster").takeIf(String::isNotBlank),
        year = yearRe.find(optString("release_date"))?.value.orEmpty(), rating = optString("rating").clean(),
        quality = optString("print").clean(), streamUrl = url, genre = optString("genre"), description = optString("description"),
    )
}

fun JSONObject.hasNextPage(): Boolean = optJSONObject("meta")?.let { it.optInt("current_page") < it.optInt("last_page") } ?: false
