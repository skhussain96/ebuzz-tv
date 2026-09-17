package world.ebuzz.tv.core.playback

import android.content.Context
import android.content.Intent
import world.ebuzz.tv.domain.model.Album
import world.ebuzz.tv.domain.model.Track

/** How other modules open the player. Features never reference PlayerActivity directly. */
object PlayerIntents {
    private const val NUMBER = "number"
    private const val MOVIE_ID = "movieId"
    private const val TITLE = "title"
    private const val URL = "url"
    private const val ALBUM_ID = "albumId"
    private const val POSTER = "poster"
    private const val TRACK_NAMES = "trackNames"
    private const val TRACK_URLS = "trackUrls"
    private const val ATTACH = "attach"

    fun live(c: Context, number: Int) = Intent(c, PlayerActivity::class.java).putExtra(NUMBER, number)

    fun movie(c: Context, id: Int, title: String, url: String) = Intent(c, PlayerActivity::class.java)
        .putExtra(MOVIE_ID, id).putExtra(TITLE, title).putExtra(URL, url)

    fun album(c: Context, a: Album) = Intent(c, PlayerActivity::class.java)
        .putExtra(ALBUM_ID, a.id).putExtra(TITLE, a.title).putExtra(POSTER, a.poster)
        .putStringArrayListExtra(TRACK_NAMES, ArrayList(a.tracks.map { it.name }))
        .putStringArrayListExtra(TRACK_URLS, ArrayList(a.tracks.map { it.streamUrl }))

    /** From the media notification: reopen the screen on whatever is playing. */
    fun attach(c: Context) = Intent(c, PlayerActivity::class.java).putExtra(ATTACH, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    internal fun args(i: Intent): PlayerArgs = when {
        i.getBooleanExtra(ATTACH, false) -> PlayerArgs.Attach
        i.getIntExtra(ALBUM_ID, 0) != 0 -> PlayerArgs.AlbumArgs(
            i.getIntExtra(ALBUM_ID, 0), i.getStringExtra(TITLE).orEmpty(), i.getStringExtra(POSTER),
            i.getStringArrayListExtra(TRACK_NAMES).orEmpty().zip(i.getStringArrayListExtra(TRACK_URLS).orEmpty()) { n, u -> Track(n, u) },
        )
        i.getIntExtra(MOVIE_ID, 0) != 0 -> PlayerArgs.Film(i.getIntExtra(MOVIE_ID, 0), i.getStringExtra(TITLE).orEmpty(), i.getStringExtra(URL)!!)
        else -> PlayerArgs.Live(i.getIntExtra(NUMBER, 0).takeIf { it > 0 })
    }
}
