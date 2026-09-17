# eBuzz TV (Android)

Live TV for Android TV and phones, plus a Movies tab on phones/tablets. Kotlin, Views + ViewBinding, Media3.
No DI framework, no Compose: APK ≈ 2 MB, low RAM is a requirement.

## Layers (package `world.ebuzz.tv`)

- `domain/` – pure Kotlin, no Android imports. `model`, `repository` (interfaces), `usecase` (one small class
  each, `operator fun invoke`). Business rules live here: `ContentPolicy` (permanent adult/Romance filter, no
  toggle by design), `GetMoviesPage` (skips pages the policy empties), `SaveMovieProgress` (keep only
  mid-movie positions), `FilterChannels`, `StepChannel`. Unit tests: `app/src/test/.../UseCaseTest.kt`.
- `data/` – `remote/EbuzzApi` (OkHttp + the browser headers the API demands), `remote/Mappers` (JSON → domain),
  `repository/*Impl`, `local/PrefsStores` (SharedPreferences behind `PlaybackStore` / `HomeStateStore`).
- `di/AppContainer` – the whole object graph, lazily built; reached via `Context.container`.
- `presentation/` – `home` (HomeViewModel → `HomeUiState`, HomeActivity only renders it), `player`
  (PlayerViewModel decides what plays and what is persisted; PlayerActivity owns ExoPlayer and views;
  PlayerGestures is the touch vocabulary), `common`.

To add a feature: model → repository method → use case → expose from `AppContainer` → call from a ViewModel.

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
./gradlew testReleaseUnitTest assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Release is R8-minified and signed with the debug key (sideload only).
