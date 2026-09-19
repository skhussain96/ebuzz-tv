package world.ebuzz.tv

import world.ebuzz.tv.core.ui.HomeSection
import world.ebuzz.tv.feature.live.LiveSection
import world.ebuzz.tv.feature.movies.MoviesSection
import world.ebuzz.tv.feature.music.MusicSection

/** eBuzz Entertainment: Live TV, Movies and Music. */
val homeSections: List<HomeSection> = listOf(LiveSection, MoviesSection, MusicSection)

// what this edition sends to and accepts from other devices
val linkKinds = setOf("live", "film", "album")
