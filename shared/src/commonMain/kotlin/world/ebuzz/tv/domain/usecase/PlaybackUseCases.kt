package world.ebuzz.tv.domain.usecase

import world.ebuzz.tv.domain.model.ResumePoint
import world.ebuzz.tv.domain.repository.PlaybackStore

class GetLastChannel(private val store: PlaybackStore) { operator fun invoke(): Int = store.lastChannelNumber }
class SetLastChannel(private val store: PlaybackStore) { operator fun invoke(number: Int) { store.lastChannelNumber = number } }

class GetVolume(private val store: PlaybackStore) { operator fun invoke(): Float = store.volume }
class SetVolume(private val store: PlaybackStore) { operator fun invoke(v: Float) { store.volume = v.coerceIn(0f, 1f) } }

class GetMovieProgress(private val store: PlaybackStore) {
    /** Position worth resuming from, or 0. */
    operator fun invoke(movieId: Int): Long = store.progress(movieId).takeIf { it > MIN_RESUME_MS } ?: 0
}

/** Keeps progress only while the movie is genuinely in the middle; clears it near the start and the end. */
class SaveMovieProgress(private val store: PlaybackStore) {
    operator fun invoke(point: ResumePoint, durationMs: Long) {
        if (durationMs <= 0) return
        if (point.positionMs > MIN_RESUME_MS && point.positionMs < durationMs - END_MARGIN_MS) store.saveProgress(point)
        else store.clearProgress(point.movieId)
    }
}

class ClearMovieProgress(private val store: PlaybackStore) { operator fun invoke(movieId: Int) = store.clearProgress(movieId) }

class GetResumePoint(private val store: PlaybackStore) { operator fun invoke(): ResumePoint? = store.resumePoint() }

private const val MIN_RESUME_MS = 20_000L
private const val END_MARGIN_MS = 30_000L
