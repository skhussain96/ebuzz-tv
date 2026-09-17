package world.ebuzz.tv

import world.ebuzz.tv.core.ui.HomeSection
import world.ebuzz.tv.feature.live.LiveSection

/** eBuzz TV: Live TV only. The movies and music modules are not even on this edition's classpath. */
val homeSections: List<HomeSection> = listOf(LiveSection)
