# eBuzz TV (Android)

Live TV for Android TV and phones, plus a Movies tab on phones/tablets. Kotlin, Views + ViewBinding, Media3.
Logic is shared with the web player through a Kotlin Multiplatform module. No DI framework, no Compose:
APK ≈ 2 MB, low RAM is a requirement.

## Modules

- `shared/` – **Kotlin Multiplatform** (targets: Android, JS). Everything that is not UI or platform storage:
  - `domain/` – pure Kotlin `model`, `repository` interfaces, `usecase` (one small class each, `operator fun invoke`).
    Business rules live here: `ContentPolicy`, `GetMoviesPage` (skips pages the policy empties), `SaveMovieProgress`
    (keep only mid-movie positions), `GetChannels` (policy-filtered, numbers never renumbered), `FilterChannels`, `StepChannel`.
  - `data/` – `remote/EbuzzApi` (Ktor; Android passes the browser headers the API demands, the web passes its
    same-origin proxy path and no headers), `remote/Mappers` (kotlinx JSON tree → domain; the API is loosely typed),
    `repository/*Impl`.
  - `jsMain/js/EbuzzSdk` – `@JsExport` facade (Promises, arrays) for the web player. Built as one UMD file,
    `window.ebuzzShared`; the web repo copies it with its `sync-shared.sh`.
  - Tests: `shared/src/commonTest` (run on the JVM with `:shared:testReleaseUnitTest`).
- `app/` – Android only: `data/local/PrefsStores` (SharedPreferences behind `PlaybackStore` / `HomeStateStore`),
  `di/AppContainer` (the whole object graph, lazy; reached via `Context.container`), `presentation/`
  (`home`: HomeViewModel → `HomeUiState`, HomeActivity only renders it; `player`: PlayerViewModel decides what plays
  and what is persisted, PlayerActivity owns ExoPlayer and views, PlayerGestures is the touch vocabulary).

UI is deliberately NOT shared: the web's main target is old Android TV browsers (no WasmGC for Compose web), the
player is platform-specific anyway (Media3 vs <video>/hls.js/MSE), and Compose costs RAM. Views + ViewBinding here.

To add a feature: model → repository method → use case (all in `shared`) → expose from `AppContainer` and, if the
web needs it, from `EbuzzSdk` → call from a ViewModel.

## Content policy (do not weaken)

`ContentPolicy` is permanent and over-strict by design: no toggle, no allow-list, false positives accepted.
Blocks Romance/Erotic/Adult genres, a long term list in title/genre, and a longer one in descriptions, including
sexual-violence terms; also applied to channel titles. The web keeps a mirrored fallback of the same lists in
`ebuzz-web.html` (`FALLBACK_POLICY`) for browsers that can't load the bundle — change both together.

## Behaviour to preserve

- Channels are fetched in API id order so channel numbers are stable; digits jump to a number.
- Remote: LEFT/RIGHT prev/next channel (±10 s in a movie), UP/DOWN volume, OK pause, long-press OK cycles audio,
  BACK exits. Touch: swipe left/right = next/previous channel (±30 s in a movie), vertical drag = volume,
  tap = buttons, double-tap = pause (live) / ±10 s (movie). Pointer: wheel = volume. Buttons never show for the remote.
- TV is locked to landscape and hides the Movies tab; phones rotate freely.
- Movie progress is saved every 5 s and on stop; home tab + category are restored on launch; a Resume chip
  appears for an unfinished movie.
- Live buffer 4–12 s, movie buffer 15–30 s, no back buffer (RAM).
- Radii: `r_surface` 14dp, `r_media` 10dp, `r_badge` 8dp, pills fully round.

## Build / install

```bash
./gradlew :shared:testReleaseUnitTest :app:assembleRelease      # Android
./gradlew :shared:jsBrowserProductionWebpack                    # web bundle → shared/build/kotlin-webpack/js/productionExecutable
adb install -r app/build/outputs/apk/release/app-release.apk
```

Release is R8-minified and signed with the debug key (sideload only).
