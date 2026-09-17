package world.ebuzz.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import world.ebuzz.tv.domain.model.HomeState
import world.ebuzz.tv.domain.model.ResumePoint
import world.ebuzz.tv.domain.repository.HomeStateStore
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

class PrefsHomeStateStore(context: Context) : HomeStateStore {
    private val prefs = context.ebuzzPrefs()
    override fun load() = HomeState(prefs.getBoolean("tabMovies", false), prefs.getInt("category", -1).takeIf { it >= 0 })
    override fun save(state: HomeState) = prefs.edit().putBoolean("tabMovies", state.moviesTab).putInt("category", state.categoryId ?: -1).apply()
}
