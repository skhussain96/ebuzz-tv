package world.ebuzz.tv.core.playback.usecase

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import world.ebuzz.tv.core.playback.NowPlaying
import world.ebuzz.tv.core.playback.PlayerArgs
import world.ebuzz.tv.core.playback.PlayerKind
import world.ebuzz.tv.domain.usecase.StepTrack
import java.util.Locale

// Each class is one thing the player can be asked to do. They take a Player, so they work the same against
// the ExoPlayer in the service or the MediaController on a screen, and the screen itself stays thin.

/** Media ids carry the kind of thing being played, so a screen that re-attaches knows which mode to show. */
object MediaIds {
    fun live(number: Int) = "live:$number"
    fun film(id: Int) = "film:$id"
    fun albumTrack(albumId: Int, index: Int) = "album:$albumId:$index"
    fun isAlbum(item: MediaItem?, albumId: Int) = item?.mediaId?.startsWith("album:$albumId:") == true
    fun kindOf(item: MediaItem?) = when (item?.mediaId?.substringBefore(':')) {
        "album" -> PlayerKind.ALBUM; "film" -> PlayerKind.FILM; else -> PlayerKind.LIVE
    }
}

/** Replace whatever is playing with a single stream (a channel or a film), optionally resuming. */
class LoadStream {
    operator fun invoke(p: Player, item: NowPlaying) {
        p.stop(); p.clearMediaItems()
        // the start position travels with the item: across a MediaController a separate seekTo() can land before the
        // session has resolved the item, and is then thrown away when the item arrives
        p.setMediaItem(MediaItem.Builder().setMediaId(item.mediaId).setUri(item.streamUrl)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).build()).build(), item.startPositionMs)
        p.prepare()
        p.play()
    }
}

/** Hand an album to the session as a playlist — unless that album is already what's playing. */
class StartAlbum {
    operator fun invoke(p: Player, a: PlayerArgs.AlbumArgs) {
        if (MediaIds.isAlbum(p.currentMediaItem, a.id)) return
        val art = a.poster?.let(Uri::parse)
        p.setMediaItems(a.tracks.mapIndexed { i, t ->
            MediaItem.Builder().setMediaId(MediaIds.albumTrack(a.id, i)).setUri(t.streamUrl)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(t.name).setAlbumTitle(a.title).setArtist(a.title)
                    .setArtworkUri(art).setTrackNumber(i + 1).setTotalTrackCount(a.tracks.size).build()).build()
        }, 0, 0L)
        p.prepare(); p.play()
    }
}

/** Relative seek, clamped to the media. Returns false when the media isn't seekable (live). */
class SeekBy {
    operator fun invoke(p: Player, deltaMs: Long): Boolean {
        val d = p.duration.takeIf { it > 0 } ?: return false
        p.seekTo((p.currentPosition + deltaMs).coerceIn(0, d)); return true
    }
}

/** Jump to a fraction (0..1) of the media, as a scrub bar does. */
class SeekToFraction {
    operator fun invoke(p: Player, fraction: Float) { p.duration.takeIf { it > 0 }?.let { p.seekTo((it * fraction).toLong()) } }
}

class TogglePlay {
    operator fun invoke(p: Player) { if (p.isPlaying) p.pause() else { p.playWhenReady = true; p.play() } }
}

/** Previous / next track of the playlist; null when already at that end. */
class StepAlbumTrack(private val stepTrack: StepTrack) {
    operator fun invoke(p: Player, dir: Int): Int? =
        stepTrack(p.mediaItemCount, p.currentMediaItemIndex, dir)?.also { p.seekTo(it, 0L); p.play() }
}

class PlayTrackAt {
    operator fun invoke(p: Player, index: Int) { if (index in 0 until p.mediaItemCount) { p.seekTo(index, 0L); p.play() } }
}

class GetTrackTitles {
    operator fun invoke(p: Player): List<String> = List(p.mediaItemCount) { i -> "${i + 1}.  ${p.getMediaItemAt(i).mediaMetadata.title}" }
}

class HasAudioChoice {
    operator fun invoke(p: Player): Boolean = p.currentTracks.groups.count { it.type == C.TRACK_TYPE_AUDIO && it.isSupported } > 1
}

/** Switch to the next audio track of the stream. Returns its display name, or null when there is only one. */
class CycleAudioTrack {
    operator fun invoke(p: Player): String? {
        val groups = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
        if (groups.size < 2) return null
        val next = groups[(groups.indexOfFirst { it.isSelected } + 1) % groups.size]
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setOverrideForType(TrackSelectionOverride(next.mediaTrackGroup, 0)).build()
        val f = next.getTrackFormat(0)
        return f.label ?: f.language?.takeIf { it != "und" }?.let { Locale(it).displayLanguage.ifBlank { it } } ?: "Track ${groups.indexOf(next) + 1}"
    }
}

/** The one-line title the screen shows, read from the session so a re-attached screen tells the truth. */
class DescribeNowPlaying {
    operator fun invoke(p: Player, kind: PlayerKind): String {
        val md = p.mediaMetadata
        val title = md.title?.toString().orEmpty()
        return if (kind != PlayerKind.ALBUM) title
        else listOfNotNull(md.albumTitle?.toString(), "${p.currentMediaItemIndex + 1}/${p.mediaItemCount}  $title").joinToString("  ·  ")
    }
}

/** "12:03 / 1:41:20", or null when the media has no duration yet. */
class FormatProgress {
    operator fun invoke(positionMs: Long, durationMs: Long): String? = if (durationMs <= 0) null else "${fmt(positionMs)} / ${fmt(durationMs)}"

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }
}
