package world.ebuzz.tv.domain.repository

import world.ebuzz.tv.domain.model.Channel
import world.ebuzz.tv.domain.model.HomeState
import world.ebuzz.tv.domain.model.MovieCategory
import world.ebuzz.tv.domain.model.MoviePage
import world.ebuzz.tv.domain.model.MovieQuery
import world.ebuzz.tv.domain.model.ResumePoint

interface ChannelRepository {
    /** Channels in stable API order; numbers are 1-based positions. */
    suspend fun channels(): List<Channel>
}

interface MovieRepository {
    fun categories(): List<MovieCategory>
    /** One unfiltered API page. Content policy is applied by the use case, not here. */
    suspend fun page(query: MovieQuery): MoviePage
}

interface PlaybackStore {
    var lastChannelNumber: Int
    var volume: Float
    fun progress(movieId: Int): Long
    fun saveProgress(point: ResumePoint)
    fun clearProgress(movieId: Int)
    fun resumePoint(): ResumePoint?
}

interface HomeStateStore {
    fun load(): HomeState
    fun save(state: HomeState)
}
