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

## Size budget (keep it ~1.3 MB)

No AppCompat, no media3-ui, no Coil, no OkHttp. Theme is platform Material; activities are `FragmentActivity` /
`ComponentActivity`; video renders into `AspectSurfaceView` with a black `shutter` view; images go through
`core/ui/ImageLoader.kt` (`ImageView.loadUrl`); Ktor uses the `android` engine; `PlaybackService` names only the
MP4/MKV/MP3/ADTS extractors. English resources only. Icons are vectors - do not add PNGs or icon libraries.
On TV the search field never opens the keyboard on focus, only on OK (`keyboardOnlyOnClick`).

## Device link (`core:link`) - play on / continue from another device

Every running copy (phone or TV, either edition) advertises `_ebuzz._tcp` over NSD as `<device name>~<6-char id>` on a
random port and browses for the others, only while the app is on screen. Assume many phones and many TVs on one Wi-Fi:
peers are a list, the user always picks from `DevicePicker`, a device filters itself out by id, resolves are queued.
Protocol is one JSON line each way: `{"cmd":"query"}` -> `{"now":item|null}`, `{"cmd":"play","item":…}`, `{"cmd":"stop"}`.
Items (`HandOff`): live `{number,title}`, film `{id,title,url,pos}`, album `{id,title,poster,names[],urls[]}`.
Received items pass `ContentPolicy` before anything opens. Player "Play on" pushes and stops locally; the cast button
on the home row pulls ("Continue from") and stops the source. `core:playback` knows nothing of `core:link` except the
`HandOff` hooks.

Editions differ in what they link: `linkKinds` in each flavor's `Sections.kt` (TV = `live` only). It is advertised as the
NSD `kinds` attribute and enforced on both ends: the TV edition never offers, accepts or lists films/albums, its
"Continue from" list shows a device only while that device is playing a channel, and "Play on" lists only peers that
accept the item's kind. A `query` reply also carries `resume` (the film a device stopped part-way) so it can be finished
elsewhere at the same position.

## Behaviours that were bugs once (do not regress)

- Start position travels with the media item (`setMediaItem(item, startPositionMs)`). A separate `seekTo()` after
  `setMediaItem()` is dropped across the MediaController, which silently broke Resume and film hand-off.
- Volume is the device media stream (`AudioManager.STREAM_MUSIC`) and always snaps to 10 % steps, D-pad and swipe alike:
  the stream is set to the index at or above the level and player gain trims the remainder (a 15-step stream has no
  slot for every 10 %). Player gain alone is used only when `isVolumeFixed`. Opening the player never changes the volume.
- Vertical D-pad focus is row-based: screens and the home shell are `FocusColumn`s. Android's FocusFinder measures from
  an EditText's caret and scores by centre distance, so it skips full-width rows (the Resume pill). Any new vertical
  stack of rows must sit in a `FocusColumn`.
- A selected tab still shows a focus ring (`tab_bg.xml` has a selected+focused state).

Casting is one-way: phone/tablet (either edition) -> TV device (either edition). NSD attribute `tv` = 1 on a TV.
Phones show "Play on" in the player and list only TVs that accept the item's kind; TVs show the home cast button
("Continue from") and list only phones. Enforced on the receiving end too: a phone refuses `play`, a TV answers
`query` with nothing. Phone-to-phone and TV-to-phone are deliberately impossible.
- A `play` intent reaching an already-open player (`singleTop` -> `onNewIntent`: a cast, or another tile) replaces the
  screen. The old screen must release its controller (listener, video surface, stop + clear) *before* the new one starts;
  a late `clearVideoSurface`/`pause` from it lands on the new stream (film audio over a frozen frame of the old channel).
- `PlaybackService` stops itself once the player is idle and empty, so closing a channel or film releases ExoPlayer
  (measured: service gone and thread count back to baseline 5 s after closing). Albums keep playing by design.
- The link server prefers ports 47811/47812 (random only if both are taken) and senders fall back to them: a restarted
  app otherwise returns on a new port while peers still hold the old one from the mDNS cache ("did not respond").

## Launcher identity

Each edition owns its icon and TV banner (`app/src/<flavor>/res/drawable/{ic_launcher_fg,ic_launcher_bg,banner}.xml`;
nothing in `main`). TV: dark tile, honey TV set with a red on-air dot, "eBuzz TV" + LIVE pill. Entertainment: honey tile,
dark clapperboard, "eBuzz Entertainment". A TV launcher shows the banner and no label, so the name is drawn into the
banner as vector paths (Roboto outlines) - keep both editions visually different in colour *and* shape.
