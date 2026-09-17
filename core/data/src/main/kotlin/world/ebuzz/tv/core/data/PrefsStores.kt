package world.ebuzz.tv.core.data

import android.content.Context
import android.content.SharedPreferences
import world.ebuzz.tv.domain.model.MovieSort
import world.ebuzz.tv.domain.model.ResumePoint
import world.ebuzz.tv.domain.repository.PlaybackStore

private fun Context.ebuzzPrefs(): SharedPreferences = getSharedPreferences("ebuzz", Context.MODE_PRIVATE)

class PrefsPlaybackStore(context: Context) : PlaybackStore {
    private val prefs = context.ebuzzPrefs()

    override var lastChannelNumber: Int
        get() = prefs.getInt("last", 1)
        set(v) = prefs.edit().putInt("last", v).apply()

    override var volume: Float
        get() = prefs.getFloat("volume", 1f)
        set(v) = prefs.edit().putFloat("volume", v).apply()

    override fun progress(movieId: Int): Long = prefs.getLong("pos:$movieId", 0)

    override fun saveProgress(point: ResumePoint) = prefs.edit()
        .putLong("pos:${point.movieId}", point.positionMs)
        .putInt("lastMovieId", point.movieId).putString("lastMovieTitle", point.title).putString("lastMovieUrl", point.streamUrl)
        .apply()

    override fun clearProgress(movieId: Int) = prefs.edit().remove("pos:$movieId").apply()

    override fun resumePoint(): ResumePoint? {
        val id = prefs.getInt("lastMovieId", 0)
        val pos = prefs.getLong("pos:$id", 0)
        val url = prefs.getString("lastMovieUrl", null)
        return if (id == 0 || pos <= 0 || url == null) null else ResumePoint(id, prefs.getString("lastMovieTitle", "").orEmpty(), url, pos)
    }
}

/** Which home tab was open last. */
class PrefsSectionStore(context: Context) {
    private val prefs = context.ebuzzPrefs()
    var last: String?
        get() = prefs.getString("section", null)
        set(v) = prefs.edit().putString("section", v).apply()
}

/** Category and sort are remembered per catalog ("movies", "music"). */
class PrefsCatalogStateStore(context: Context) {
    private val prefs = context.ebuzzPrefs()
    fun category(key: String): Int? = prefs.getInt("$key.category", -1).takeIf { it >= 0 }
    fun sort(key: String): MovieSort = MovieSort.entries.firstOrNull { it.name == prefs.getString("$key.sort", null) } ?: MovieSort.ADDED
    fun save(key: String, categoryId: Int?, sort: MovieSort) =
        prefs.edit().putInt("$key.category", categoryId ?: -1).putString("$key.sort", sort.name).apply()
}
