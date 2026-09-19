package world.ebuzz.tv.core.playback

import android.app.Activity
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import world.ebuzz.tv.domain.model.Album
import world.ebuzz.tv.domain.model.Track

// What this device is playing, in the form another device can pick up. Plain JSON so the web player can join later.
object HandOff {
    interface Source { fun snapshot(): JSONObject?; fun stop() }

    @Volatile var source: Source? = null
    // set by the link module; null means this build has no device linking and the player hides its button
    @Volatile var onSendClick: ((Activity, JSONObject) -> Unit)? = null

    fun live(number: Int, title: String) = JSONObject().put("kind", "live").put("number", number).put("title", title)
    fun film(id: Int, title: String, url: String, positionMs: Long) =
        JSONObject().put("kind", "film").put("id", id).put("title", title).put("url", url).put("pos", positionMs)
    fun album(a: PlayerArgs.AlbumArgs) = JSONObject().put("kind", "album").put("id", a.id).put("title", a.title).put("poster", a.poster ?: "")
        .put("names", JSONArray(a.tracks.map { it.name })).put("urls", JSONArray(a.tracks.map { it.streamUrl }))

    fun title(o: JSONObject): String = o.optString("title")

    fun intent(c: Context, o: JSONObject): Intent? = when (o.optString("kind")) {
        "live" -> o.optInt("number").takeIf { it > 0 }?.let { PlayerIntents.live(c, it) }
        "film" -> o.optString("url").takeIf { it.startsWith("http") }?.let { PlayerIntents.movie(c, o.optInt("id"), o.optString("title"), it) }
        "album" -> {
            val names = o.optJSONArray("names"); val urls = o.optJSONArray("urls")
            if (names == null || urls == null || urls.length() == 0 || names.length() != urls.length()) null
            else PlayerIntents.album(c, Album(o.optInt("id"), o.optString("title"), o.optString("poster").ifBlank { null },
                List(urls.length()) { Track(names.getString(it), urls.getString(it)) }))
        }
        else -> null
    }
}
