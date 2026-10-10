# Spectra

A dark-themed Spotify companion for Android, built with Kotlin, Jetpack Compose and Material 3.
Browse your saved songs and playlists, control playback in the Spotify app, and watch a
real-time **circular spectrum visualizer** around the album art.

> **Status:** first working draft. It compiles, the unit tests pass, and GitHub Actions produces a
> debug APK. It has **not been run on a device yet**, so expect runtime rough edges (especially the
> Spotify connection and the live visualizer).

## Features

| Area | What it does |
|---|---|
| Login | Authorization Code + **PKCE** in a Custom Tab (no client secret, no implicit grant). Tokens are AES-256-GCM encrypted with an Android Keystore key. Automatic refresh on expiry / 401. |
| Playback | **Spotify App Remote SDK** drives the installed Spotify app: play, pause, skip, seek, shuffle, repeat, live player state and artwork. |
| Library | Saved songs and playlists via the Web API, infinite scroll, pull-to-refresh, playlist detail. |
| Premium + offline | Premium is detected from Web API `product` *or* App Remote `canPlayOnDemand`. An Offline tab shows plan and network state and a cached snapshot of your last synced songs. |
| UI | Strict Material 3 dark theme (no light scheme, no dynamic color), type-safe navigation, mini player. |
| Visualizer | Radial spectrum on a custom Compose `Canvas`: bars / wave / dots, bass-pulsing glow, rotating album disc, touch-reactive. Tap the ring to change style; drag to push bars near your finger. |

## Spotify platform limits (read this first)

These shaped the design (based on Spotify's published API changes, including the Feb 2026 development-mode migration guide). Re-check them against the current docs before relying on any one of them.

- **No raw audio.** App Remote exposes metadata and controls, never PCM. The visualizer therefore uses the fallbacks below.
- **No downloaded-track list.** Neither the Web API nor App Remote reveals which tracks are downloaded for offline play, so the app cannot enumerate them. It reports plan + connectivity, caches library *metadata* for offline browsing, and leaves actual offline playback to the Spotify app. (If you ship the metadata cache, check Spotify's Developer Policy on caching.)
- **`product` removed from `GET /me`** for development-mode apps (Feb 2026 changes). `PremiumStatus.resolve()` falls back to App Remote `userApi.capabilities.canPlayOnDemand`, which is only true on Premium.
- **Audio Features / Analysis are deprecated** for apps created after 27 Nov 2024 (HTTP 403). The simulated visualizer uses them when available and otherwise a built-in default profile.
- **Playlist items** moved to `/playlists/{id}/items` and are only returned for playlists you own or collaborate on; other playlists show a friendly message and can still be played.
- **Development mode** requires the app owner to have Premium, and only users added under *User Management* can sign in.
- **Free accounts**: App Remote can only start shuffled playback, not on-demand tracks.

## Visualizer: where the spectrum comes from

`VisualizerController` produces one smoothed frame (~60 fps) and picks a source automatically:

1. **Live** — `android.media.audiofx.Visualizer` on the global output mix (audio session 0), FFT'd into 48 log-spaced bands (`NativeSpectrumTap` + `BandMapper`). Needs the `RECORD_AUDIO` runtime permission (the app only reads the FFT, nothing is recorded or stored). It hears *all* device audio, and some devices/Android versions refuse or return silence.
2. **Simulated** — `SimulatedSpectrum`: kick/snare/hat envelopes locked to the real playback position and the track's tempo, energy, loudness and danceability (Spotify Audio Features, else a default profile). It is synchronized to the music, but it does not analyse it.

If live capture fails, is denied, or stays silent for 1.5 s while playing, simulation takes over; when live audio returns, it takes control back. The Now Playing screen labels the active source.

## Architecture (MVVM, unidirectional data flow)

```
                 ┌──────────────────────────── UI (Jetpack Compose, Material 3 dark) ───────────────────────────┐
                 │ LoginScreen   LibraryScreen   PlaylistScreen   NowPlayingScreen ─ RadialSpectrumVisualizer   │
                 └───────▲───────────────▲──────────────▲────────────────▲───────────────────────────────────────┘
                         │ StateFlow      │              │                │            (spectrum read in draw phase only)
                 ┌───────┴───┐   ┌────────┴────┐  ┌──────┴───────┐  ┌─────┴──────────┐
                 │AuthViewModel│ │LibraryViewModel│ │PlaylistViewModel│ │NowPlayingViewModel│      ViewModels
                 └───────┬───┘   └────────┬────┘  └──────┬───────┘  └─────┬──────────┘
                         │                │              │                │
        ┌────────────────▼─┐   ┌──────────▼───────────┐  │    ┌───────────▼────────────┐   ┌────────────────────┐
        │SpotifyAuthManager│   │   LibraryRepository   │◄─┘    │ SpotifyRemoteManager    │   │VisualizerController│
        │ PKCE · refresh   │   │ Web API + LibraryCache│       │ App Remote: state/ctrl  │   │ live FFT ⇄ simulated│
        └────────┬─────────┘   └──────────┬───────────┘       └───────────┬────────────┘   └────────────────────┘
                 │ SecureTokenStore        │ Retrofit + OkHttp              │ Spotify app (IPC)
                 ▼ (Android Keystore)      ▼ (Bearer + 401 refresh + 429)   ▼
```

```
app/src/main/java/com/spectra/player/
├── AppContainer.kt, SpectraApp.kt, MainActivity.kt   manual DI, redirect handling
├── auth/        AuthConfig, Pkce, KeystoreCipher, SecureTokenStore, SpotifyAuthManager, SpotifyAccountsApi
├── data/        Models, Mappers, ErrorMessages
│   ├── remote/      SpotifyApi (Retrofit), Dto, Interceptors (Bearer, Authenticator, RateLimit)
│   └── repository/  LibraryRepository, LibraryCache, NetworkMonitor
├── player/      SpotifyRemoteManager (App Remote wrapper), PlaybackState
├── visualizer/  RadialVisualizer (Canvas), VisualizerController, NativeSpectrumTap,
│                BandMapper, SimulatedSpectrum, SpectrumSmoother
└── ui/          theme/, navigation/Route, login/, library/, nowplaying/, components/
```

## Setup

1. **Spotify dashboard** → create an app at <https://developer.spotify.com/dashboard>:
   - Redirect URI: `spectra://callback`
   - Add an *Android app*: package `com.spectra.player` and your debug **SHA-1** (`./gradlew signingReport`)
   - APIs used: Web API and Android (App Remote)
   - Under *User Management*, add the Spotify account(s) that will sign in (development mode)
2. **Client ID** → `cp local.properties.example local.properties` and set `spotify.clientId=...`
   (or export `SPOTIFY_CLIENT_ID`). No secret is needed or used.
3. Install the **Spotify app** on the device/emulator and log in.
4. Open in Android Studio (Ladybug or newer) and run, or `./gradlew installDebug`.
5. Unit tests: `./gradlew testDebugUnitTest`.

The Spotify App Remote SDK is vendored as `app/libs/spotify-app-remote-release-0.8.0.aar` (Apache-2.0,
from <https://github.com/spotify/android-sdk>); Spotify doesn't publish it to Maven Central. Login uses
our own PKCE flow, so Spotify's separate Auth library isn't needed.

## Build the APK on GitHub

`.github/workflows/android.yml` builds a debug APK on every push to `main` (and on demand via
**Actions → Build APK → Run workflow**), then runs the unit tests. Download the APK from the run's
**Artifacts** (`spectra-debug-apk`).

1. Repo **Settings → Secrets and variables → Actions → New repository secret**:
   - `SPOTIFY_CLIENT_ID` — your Spotify Client ID (required for sign-in to work).
2. Spotify App Remote only accepts an app whose signing **SHA-1** is registered in the dashboard,
   and a fresh runner makes a new random debug key every time. Create one stable key once:
   ```
   keytool -genkeypair -v -keystore ci.keystore -alias spectra -keyalg RSA -keysize 2048 \
     -validity 10000 -storepass <password> -keypass <password> -dname "CN=Spectra"
   base64 -w0 ci.keystore        # macOS: base64 -i ci.keystore
   keytool -list -v -keystore ci.keystore -alias spectra | grep SHA1   # register this in Spotify
   ```
   Add secrets `KEYSTORE_BASE64` (the base64 text), `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`spectra`),
   `KEY_PASSWORD`. Without them the APK still builds, but App Remote playback will be rejected.
3. Install the APK on a device with the Spotify app (allow "install unknown apps").

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| "Redirect URI not matching configuration" | The dashboard's Redirect URIs must contain exactly `spectra://callback` (no spaces, no trailing slash) and be saved. The login screen's *Sign-in problems? Show setup details* panel shows the exact values this build uses, including its SHA-1. |
| "Spotify didn't authorize this app" | Client ID, package name, SHA-1 or redirect URI don't match the dashboard. |
| Login page returns "Sign-in response could not be verified" | Redirect URI not exactly `spectra://callback`, or the sign-in was started twice. |
| 403 on library calls | Account not in *User Management*, or the app owner lacks Premium (dev mode). |
| Visualizer says "Simulated" | `RECORD_AUDIO` not granted, or the device blocks the global-mix `Visualizer`; this is the intended fallback. |
| Play fails with a Premium message | Free account; App Remote on-demand playback needs Premium. |

## Notes for extending

- `SpotifyRemoteManager.play()` starts a track by URI; to play *within* a playlist context, start the playlist URI and skip to the item.
- Swap `AppContainer` for Hilt/Koin if the graph grows.
- Dependency versions are conservative (AGP 8.7, Kotlin 2.0.21, Compose BOM 2024.12); bump them freely.
