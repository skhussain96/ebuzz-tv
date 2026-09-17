package world.ebuzz.tv.feature.music

import android.content.Context
import androidx.fragment.app.Fragment
import world.ebuzz.tv.core.catalog.CatalogFragment
import world.ebuzz.tv.core.catalog.CatalogSource
import world.ebuzz.tv.core.catalog.PosterTile
import world.ebuzz.tv.core.catalog.TilePage
import world.ebuzz.tv.core.data.AppContainer
import world.ebuzz.tv.core.data.container
import world.ebuzz.tv.core.playback.PlayerIntents
import world.ebuzz.tv.core.ui.HomeSection
import world.ebuzz.tv.data.repository.MusicRepositoryImpl
import world.ebuzz.tv.domain.model.Album
import world.ebuzz.tv.domain.model.MovieQuery
import world.ebuzz.tv.domain.usecase.GetMusicCategories
import world.ebuzz.tv.domain.usecase.GetMusicPage
import world.ebuzz.tv.domain.usecase.GetMusicSorts

object MusicSection : HomeSection {
    override val id = "music"
    override val title = "Music"
    override fun newFragment(): Fragment = MusicFragment()
}

class MusicFragment : CatalogFragment() {
    override fun source(context: Context): CatalogSource = MusicSource(context.container)
}

/** The Music feature's own object graph, built on the core container. */
internal class MusicSource(c: AppContainer) : CatalogSource {
    private val repo = MusicRepositoryImpl(c.api)
    private val getCategories = GetMusicCategories(repo)
    private val getSorts = GetMusicSorts()
    private val getPage = GetMusicPage(repo, c.policy)
    private val loaded = HashMap<Int, Album>()

    override val key = "music"
    override val searchHint = "Search albums"
    override fun categories() = getCategories()
    override fun sorts() = getSorts()

    override suspend fun page(query: MovieQuery): TilePage = getPage(query).let { p ->
        p.items.forEach { loaded[it.id] = it }
        TilePage(p.items.map { PosterTile(it.id, it.title, "${it.tracks.size} ${if (it.tracks.size == 1) "song" else "songs"}", "", it.poster) }, p.nextPage)
    }

    /** An album plays as a playlist in the background-capable session. */
    override fun open(context: Context, tileId: Int) { loaded[tileId]?.let { context.startActivity(PlayerIntents.album(context, it)) } }

    override fun reset() = loaded.clear()
}
