# eBuzz TV (Android)

Live TV for Android TV and phones, plus a Movies tab on phones/tablets. Kotlin, Views + ViewBinding, Media3.
Logic is shared with the web player through a Kotlin Multiplatform module. No DI framework, no Compose:
APK ≈ 2 MB, low RAM is a requirement.

## Editions (product flavors)

| Flavor | Contents | Application id | Label |
|---|---|---|---|
| `tv` | Live TV | `world.ebuzz.tv` | eBuzz TV |
| `entertainment` | Live TV + Movies + Music | `world.ebuzz.entertainment` | eBuzz Entertainment |

Editions differ in **which feature modules they link**, not in a flag. `app/build.gradle.kts` adds `:feature:movies`
and `:feature:music` with `entertainmentImplementation`, and each edition's tab list lives in its own source set:
`app/src/tv/kotlin/.../Sections.kt` and `app/src/entertainment/kotlin/.../Sections.kt`. The TV edition never has the
movies, music or catalog modules on its classpath. Both run on TVs and phones and install side by side.

## Modules

```
shared            Kotlin Multiplatform (Android + JS): domain + data. Shared with the web player.
core/ui           theme, drawables, styles, StateView (loading / empty / error + Retry), chips, DigitEntry,
                  HomeSection + KeyHandler contracts
core/data         AppContainer (core graph), EbuzzApp, SharedPreferences stores
core/playback     PlaybackService (owns ExoPlayer, MediaSession), PlayerActivity, PlayerOverlay, PlayerGestures,
                  PlayerViewModel, PlayerIntents, usecase/ (one class per player action)
core/catalog      the poster-grid screen: CatalogSource contract, CatalogFragment, CatalogViewModel, PosterAdapter
feature/live      LiveSection, LiveFragment, LiveViewModel, ChannelAdapter
feature/movies    MoviesSection + MoviesSource (its own small object graph)
feature/music     MusicSection + MusicSource
app               HomeActivity (tabs + fragment host), manifest, icons, per-edition Sections.kt
```

Dependency direction: `app → feature → core → shared`. Features never depend on each other or on `app`.

- **shared** – `domain/` is pure Kotlin: `model`, `repository` interfaces, `usecase` (one small class each,
  `operator fun invoke`). Rules live here: `ContentPolicy`, `GetMoviesPage` / `GetMusicPage` (skip pages the policy
  empties), `SaveMovieProgress`, `GetChannels` (policy-filtered, never renumbered), `RankByQuality`, `StepChannel`,
  `StepTrack`. `data/` is Ktor + kotlinx JSON tree. `jsMain/js/EbuzzSdk` is the web facade. Tests: `commonTest`.
- **A home tab** is a `HomeSection` (id, title, `newFragment()`). Fragments that want remote keys implement `KeyHandler`.
- **A catalog** (Movies, Music) is a `CatalogSource`: categories, sorts, `page(query)`, `open(tile)`, optional
  `arrange` (client-side order) and `shortcut` (e.g. Resume). The screen, paging and state are in `core:catalog`.
- **The player is thin.** `PlayerActivity` turns input into calls on `core/playback/usecase` classes (`LoadStream`,
  `StartAlbum`, `SeekBy`, `TogglePlay`, `StepAlbumTrack`, `CycleAudioTrack`, `DescribeNowPlaying`, …), which take a
  `Player` and so work on the service's ExoPlayer or a screen's MediaController alike. `PlayerOverlay` owns the
  on-screen views and their timing. Other modules open the player only through `PlayerIntents`.
- **Playback lives in a service.** Albums are handed to the session as a playlist and keep playing in the background
  with a media notification; channels and films pause when the screen is left and stop when it is closed. Media ids
  (`live:<n>`, `film:<id>`, `album:<id>:<i>`) tell a re-attached screen which mode to show.

UI is deliberately NOT shared with the web: its main target is old Android TV browsers, the player is
platform-specific anyway, and Compose costs RAM. Views + ViewBinding, no DI framework.

To add a feature: use case in `shared` → a `feature/<name>` module exposing a `HomeSection` → list it in the
edition's `Sections.kt` and add the `<edition>Implementation` dependency.

## Content policy (do not weaken)

`ContentPolicy` is permanent and over-strict by design: no toggle, no allow-list, false positives accepted.
Blocks Romance/Erotic/Adult genres, a long term list in title/genre, and a longer one in descriptions, including
sexual-violence terms; also applied to channel titles. The web keeps a mirrored fallback of the same lists in
`ebuzz-web.html` (`FALLBACK_POLICY`) for browsers that can't load the bundle — change both together.

## Behaviour to preserve

- Movie filters always combine: `MovieQuery` carries search text AND category AND `MovieSort` in one request, and the
  API applies all three across the whole catalogue. Only `MovieSort.QUALITY` is ranked on the client
  (`RankByQuality`), because quality is free text upstream. Sort and category are persisted with the home state.

- Channels are fetched in API id order so channel numbers are stable; digits jump to a number.
- Remote: LEFT/RIGHT prev/next channel (±10 s in a movie), UP/DOWN volume, OK pause, long-press OK cycles audio,
  BACK exits. Touch: swipe left/right = next/previous channel (±30 s in a movie), vertical drag = volume,
  tap = buttons, double-tap = pause (live) / ±10 s (movie). Pointer: wheel = volume. Buttons never show for the remote.
- TV is locked to landscape; phones rotate freely. Which tabs exist is decided by the edition, not the device.
- Every list screen uses `StateView`: the error state has a focusable Retry that takes focus (remote OK / tap),
  and a failed screen retries by itself when it is resumed.
- Movie progress is saved every 5 s and on stop; home tab + category are restored on launch; a Resume chip
  appears for an unfinished movie.
- Live buffer 4–12 s, movie buffer 15–30 s, no back buffer (RAM).
- Radii: `r_surface` 14dp, `r_media` 10dp, `r_badge` 8dp, pills fully round.

## Build / install

```bash
./gradlew :shared:testReleaseUnitTest :app:assembleTvRelease :app:assembleEntertainmentRelease   # both editions
./gradlew :app:dependencies --configuration tvReleaseRuntimeClasspath | grep 'project :'   # prove what an edition links
./gradlew :shared:jsBrowserProductionWebpack                    # web bundle → shared/build/kotlin-webpack/js/productionExecutable
adb install -r app/build/outputs/apk/tv/release/app-tv-release.apk     # or entertainment/release/app-entertainment-release.apk
```

Release is R8-minified and signed with the debug key (sideload only).
