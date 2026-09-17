package world.ebuzz.tv.di

import android.content.Context
import world.ebuzz.tv.data.local.PrefsHomeStateStore
import world.ebuzz.tv.data.local.PrefsPlaybackStore
import world.ebuzz.tv.data.remote.EbuzzApi
import world.ebuzz.tv.data.repository.ChannelRepositoryImpl
import world.ebuzz.tv.data.repository.MovieRepositoryImpl
import world.ebuzz.tv.domain.usecase.ClearMovieProgress
import world.ebuzz.tv.domain.usecase.ContentPolicy
import world.ebuzz.tv.domain.usecase.FilterChannels
import world.ebuzz.tv.domain.usecase.GetChannels
import world.ebuzz.tv.domain.usecase.GetHomeState
import world.ebuzz.tv.domain.usecase.GetLastChannel
import world.ebuzz.tv.domain.usecase.GetMovieCategories
import world.ebuzz.tv.domain.usecase.GetMovieProgress
import world.ebuzz.tv.domain.usecase.GetMoviesPage
import world.ebuzz.tv.domain.usecase.GetResumePoint
import world.ebuzz.tv.domain.usecase.GetVolume
import world.ebuzz.tv.domain.usecase.SaveHomeState
import world.ebuzz.tv.domain.usecase.SaveMovieProgress
import world.ebuzz.tv.domain.usecase.SetLastChannel
import world.ebuzz.tv.domain.usecase.SetVolume
import world.ebuzz.tv.domain.usecase.StepChannel

/** Manual dependency graph: one place to see how everything is wired. Lazy, so nothing is built until used. */
class AppContainer(context: Context) {
    private val api by lazy { EbuzzApi() }                 // direct base URL + browser headers
    private val policy = ContentPolicy()
    private val channelRepo by lazy { ChannelRepositoryImpl(api) }
    private val movieRepo by lazy { MovieRepositoryImpl(api) }
    private val playback by lazy { PrefsPlaybackStore(context.applicationContext) }
    private val homeState by lazy { PrefsHomeStateStore(context.applicationContext) }

    val getChannels by lazy { GetChannels(channelRepo, policy) }
    val filterChannels = FilterChannels()
    val stepChannel = StepChannel()
    val getMovieCategories by lazy { GetMovieCategories(movieRepo) }
    val getMoviesPage by lazy { GetMoviesPage(movieRepo, policy) }

    val getLastChannel by lazy { GetLastChannel(playback) }
    val setLastChannel by lazy { SetLastChannel(playback) }
    val getVolume by lazy { GetVolume(playback) }
    val setVolume by lazy { SetVolume(playback) }
    val getMovieProgress by lazy { GetMovieProgress(playback) }
    val saveMovieProgress by lazy { SaveMovieProgress(playback) }
    val clearMovieProgress by lazy { ClearMovieProgress(playback) }
    val getResumePoint by lazy { GetResumePoint(playback) }
    val getHomeState by lazy { GetHomeState(homeState) }
    val saveHomeState by lazy { SaveHomeState(homeState) }
}
