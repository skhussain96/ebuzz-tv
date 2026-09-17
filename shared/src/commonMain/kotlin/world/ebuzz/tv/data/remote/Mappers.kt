package world.ebuzz.tv.data.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import world.ebuzz.tv.domain.model.Album
import world.ebuzz.tv.domain.model.Channel
import world.ebuzz.tv.domain.model.Movie
import world.ebuzz.tv.domain.model.Track

// The API is loosely typed (ratings arrive as "7.1", "0" or false), so fields are read from the JSON tree
// rather than bound to strict DTOs that one odd value would break.
private val playable = Regex("\\.(mp4|mkv|m4v|webm|mov|m3u8)(\\?|$)", RegexOption.IGNORE_CASE)
private val yearRe = Regex("\\d{4}")

internal fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

private fun String.clean() = takeUnless { isBlank() || this == "N/A" || this == "false" || this == "0" }.orEmpty()

/** `links` is an object of identical URLs keyed by platform; prefer the Android one. */
fun JsonObject.toChannel(number: Int): Channel? {
    val links = obj("links") ?: return null
    val url = listOf("android", "web", "ios").firstNotNullOfOrNull { links.str(it).takeIf(String::isNotBlank) } ?: return null
    return Channel(int("id") ?: return null, number, str("title").ifBlank { "Channel" }, str("poster").takeIf(String::isNotBlank), url)
}

/** Movies list a .torrent and a direct file; only a direct, playable file makes a usable movie. */
fun JsonObject.toMovie(): Movie? {
    val url = arr("mirrors")?.mapNotNull { (it as? JsonObject)?.str("url") }?.firstOrNull(playable::containsMatchIn) ?: return null
    return Movie(
        id = int("id") ?: return null, title = str("title"), poster = str("poster").takeIf(String::isNotBlank),
        year = yearRe.find(str("release_date"))?.value.orEmpty(), rating = str("rating").clean(),
        quality = str("print").clean(), streamUrl = url, genre = str("genre"), description = str("description"),
    )
}

private val audio = Regex("\\.(mp3|m4a|aac|ogg|oga|opus|flac|wav)(\\?|$)", RegexOption.IGNORE_CASE)

/** Albums list their songs as `mirrors` with a `name`; only directly playable audio files count as tracks. */
fun JsonObject.toAlbum(): Album? {
    val tracks = arr("mirrors")?.mapNotNull { (it as? JsonObject)?.let { m ->
        val url = m.str("url"); if (!audio.containsMatchIn(url)) null
        else Track(m.str("name").replace(Regex("\\.(mp3|m4a)$", RegexOption.IGNORE_CASE), "").ifBlank { url.substringAfterLast('/') }, url)
    } }.orEmpty()
    return Album(int("id") ?: return null, str("title"), str("poster").takeIf(String::isNotBlank), tracks, str("description").let { if (it == "N/A") "" else it })
}

fun JsonObject.hasNextPage(): Boolean = obj("meta")?.let { (it.int("current_page") ?: 0) < (it.int("last_page") ?: 0) } ?: false
