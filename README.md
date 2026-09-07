# Mango TV

A premium, Netflix-inspired streaming app for Amazon Fire TV / Firestick, built with Kotlin and Jetpack Compose.

## Status

**Step 1 of the rebuild: Home screen.** This is a from-scratch rebuild — dark cinematic UI, a rotating hero banner, focus-driven content rows, and the data/provider architecture the rest of the app (movie/TV detail pages, search, My List, playback) will build on. Content is currently served by a built-in sample catalog provider (original fictional titles + placeholder artwork) so the UI can be reviewed before any real metadata/streaming provider is wired in.

## Project structure

```
app/src/main/java/com/mangotv/app/
  data/model/       Content, Genre, Episode, Season, WatchProgress — the shared metadata model
  data/provider/     CatalogProvider interface + ProviderRegistry (Stremio-style addon architecture) + sample catalog
  ui/theme/          Colors, typography, motion tokens, dimens — the design system
  ui/components/     Reusable focusable primitives: TvFocusSurface, ContentCard, ContentRow, MangoButton, MangoLogo, loading/error states
  ui/home/           HomeScreen, HeroSection, TopNavBar, HomeViewModel
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
