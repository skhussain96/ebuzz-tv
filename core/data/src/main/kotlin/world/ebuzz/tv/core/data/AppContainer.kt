package world.ebuzz.tv.core.data

import android.content.Context
import world.ebuzz.tv.data.remote.EbuzzApi
import world.ebuzz.tv.data.repository.ChannelRepositoryImpl
import world.ebuzz.tv.domain.usecase.ClearMovieProgress
import world.ebuzz.tv.domain.usecase.ContentPolicy
import world.ebuzz.tv.domain.usecase.FilterChannels
import world.ebuzz.tv.domain.usecase.GetChannels
import world.ebuzz.tv.domain.usecase.GetLastChannel
import world.ebuzz.tv.domain.usecase.GetMovieProgress
import world.ebuzz.tv.domain.usecase.GetResumePoint
import world.ebuzz.tv.domain.usecase.GetVolume
import world.ebuzz.tv.domain.usecase.SaveMovieProgress
import world.ebuzz.tv.domain.usecase.SetLastChannel
import world.ebuzz.tv.domain.usecase.SetVolume
import world.ebuzz.tv.domain.usecase.StepChannel
import world.ebuzz.tv.domain.usecase.StepTrack

/**
 * The core object graph, lazily built: what every edition needs. Feature modules build their own small graphs
 * on top of [api] and [policy] (see MoviesGraph / MusicGraph), so an edition without a feature never wires it.
 */
class AppContainer(context: Context) {
    val api by lazy { EbuzzApi() }                                  // direct base URL + the browser headers the API demands
    val policy = ContentPolicy()
    private val playback by lazy { PrefsPlaybackStore(context.applicationContext) }
    val sections by lazy { PrefsSectionStore(context.applicationContext) }
    val catalogState by lazy { PrefsCatalogStateStore(context.applicationContext) }

    // live TV
    val getChannels by lazy { GetChannels(ChannelRepositoryImpl(api), policy) }
    val filterChannels = FilterChannels()
    val stepChannel = StepChannel()
    val getLastChannel by lazy { GetLastChannel(playback) }
    val setLastChannel by lazy { SetLastChannel(playback) }

    // playback (any kind of media)
    val stepTrack = StepTrack()
    val getVolume by lazy { GetVolume(playback) }
    val setVolume by lazy { SetVolume(playback) }
    val getMovieProgress by lazy { GetMovieProgress(playback) }
    val saveMovieProgress by lazy { SaveMovieProgress(playback) }
    val clearMovieProgress by lazy { ClearMovieProgress(playback) }
    val getResumePoint by lazy { GetResumePoint(playback) }
}
