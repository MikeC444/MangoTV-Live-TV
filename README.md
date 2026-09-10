# Mango TV

A premium, Netflix-inspired streaming app for Amazon Fire TV / Firestick, built with Kotlin and Jetpack Compose. Content comes from Stremio-protocol addons (`data/provider`, `data/addon`) rather than a fixed built-in catalog — a user installs whichever addons they want, the same way Stremio itself works.

## Status

Full-featured: Home, Movies/TV Shows/Genres browsing, Search, a Detail page, playback (Media3/ExoPlayer, HLS/DASH), My List, and an addon-driven catalog, all built for D-pad/remote navigation. On top of that, the app has a complete **account, authentication, and cloud synchronization system** — an account signed into on one Fire TV follows to any other, with My List, watch history/Continue Watching, settings, and addon configuration all kept in sync. See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for how that system works, [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) for running your own backend instance, and [`docs/TESTING.md`](docs/TESTING.md) for how to verify it end-to-end (including reproducing the full multi-device test). `CHANGELOG.md` has the milestone-by-milestone development history.

## Project structure

```
app/src/main/java/com/mangotv/app/
  data/model/        Content, Genre, Episode, Season, WatchProgress — the shared metadata model
  data/provider/      CatalogProvider interface + ProviderRegistry (Stremio-style addon architecture)
  data/addon/         Stremio addon client/mapper + local-network addon pairing
  data/auth/           Session/device identity, encrypted-at-rest session storage (Tink/Android Keystore)
  data/network/        OkHttp + kotlinx.serialization API clients talking to the backend (server/)
  data/sync/           Per-domain cloud sync (settings, watchlist, continue watching, addons), retry queues, account switching
  data/history/        Local Continue Watching cache
  data/player/         Local player-preferences cache
  ui/theme/            Colors, typography, motion tokens, dimens — the design system
  ui/components/       Reusable focusable primitives: TvFocusSurface, ContentCard, ContentRow, MangoButton, MangoLogo, loading/error states
  ui/home/ ui/browse/ ui/detail/ ui/genres/ ui/search/ ui/mylist/ ui/player/  The main app screens
  ui/auth/             Authentication gate, sign-in start screen, QR sign-in flow
  ui/settings/         Settings, Home Rows, Addons, Account
  navigation/          Jetpack Navigation-Compose routes/nav host

server/                Backend API (Node/Express/TypeScript) sitting between the app and Neon Postgres —
                        see docs/ARCHITECTURE.md and server/README.md
```

## Building

Requires Android Studio (or the command line with an Android SDK installed):

```
./gradlew assembleDebug
```

The debug APK is also built automatically by GitHub Actions on every push (`.github/workflows/build-apk.yml`) and uploaded as a workflow artifact, so a build is available for download without needing a local Android SDK.

## Installing on a Fire TV / Firestick

1. Enable **Settings → My Fire TV → Developer Options → Apps from Unknown Sources** and **ADB Debugging**.
2. `adb connect <firestick-ip>:5555`
3. `adb install app-debug.apk`
