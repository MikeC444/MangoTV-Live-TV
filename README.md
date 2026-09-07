# Mango TV

A premium, Netflix-inspired streaming app for Amazon Fire TV / Firestick, built with Kotlin and Jetpack Compose.

## Status

**Step 1 of the rebuild: Home screen.** This is a from-scratch rebuild — dark cinematic UI, a rotating hero banner, focus-driven content rows, and the data/provider architecture the rest of the app (movie/TV detail pages, search, My List, playback) will build on. Content is currently served by a built-in sample catalog provider (original fictional titles + placeholder artwork) so the UI can be reviewed before any real metadata/streaming provider is wired in.

## Project structure

```
app/src/main/java/com/mangotv/app/
  config/            LiveTvConfig — every externally-configurable Live TV URL, read from BuildConfig
  data/model/        Content, Genre, Episode, Season, WatchProgress — the shared metadata model
  data/provider/     CatalogProvider interface + ProviderRegistry (Stremio-style addon architecture) + sample catalog
  data/entitlement/  Device identity, entitlement polling/state, backend API client (see docs/LIVE_TV_BACKEND.md)
  data/livetv/       M3U playlist + XMLTV EPG fetching, parsing, and disk caching
  ui/theme/          Colors, typography, motion tokens, dimens — the design system
  ui/components/     Reusable focusable primitives: TvFocusSurface, ContentCard, ContentRow, MangoButton, MangoLogo, loading/error states
  ui/home/           HomeScreen, HeroSection, TopNavBar, HomeViewModel
  ui/livetv/         Live TV tab: premium/paywall screen, channel browser, and its own lightweight live player
```

## Live TV

The **Live TV** tab sits in the top nav between TV Shows and Genres. It's a
premium feature gated by a real backend-verified entitlement — the app
never trusts a local "paid" flag (see `docs/LIVE_TV_BACKEND.md` for the
full contract).

**Right now the paywall is bypassed for testing** — `LIVE_TV_SKIP_PAYWALL`
defaults to `true`, so Live TV unlocks straight to the channel browser for
everyone, with a visible "TESTING — PAYWALL BYPASSED" badge on the browse
screen as a reminder. Once you're done testing channel browsing/playback
and are ready to test or ship the real entitlement flow, set
`LIVE_TV_SKIP_PAYWALL=false` in `local.properties` — with a backend
configured (`API_BASE_URL`), the real paywall takes over; without one,
Live TV instead shows a "not configured yet" screen rather than pretending
payments work.

### Configuring Live TV

Four URLs drive the whole feature, all read via `LiveTvConfig` from
`BuildConfig` fields set in `app/build.gradle.kts`. None are secrets, but
none belong in a committed file with real per-deployment values either —
set them in a `local.properties` at the repo root (already gitignored) to
override the defaults baked into `gradle.properties`/`build.gradle.kts`:

```properties
# local.properties (never committed)
IPTV_PLAYLIST_URL=https://iptv-org.github.io/iptv/index.m3u
LIVE_TV_ALLOWED_GROUPS=
EPG_URL=
API_BASE_URL=https://your-backend.example.com
PREMIUM_CHECKOUT_URL=https://your-checkout.example.com/live-tv
LIVE_TV_SKIP_PAYWALL=true
```

- **`IPTV_PLAYLIST_URL`** — the M3U playlist to load channels from.
  Defaults to iptv-org's public aggregate index. **Read the legal note
  below before shipping this to real users.**
- **`LIVE_TV_ALLOWED_GROUPS`** — optional comma-separated allow-list of
  M3U `group-title` values (e.g. `News,Entertainment`). Empty means every
  group in the configured playlist is shown.
- **`EPG_URL`** — optional XMLTV guide URL (plain or `.gz`) for now/next
  programme info. Leave blank to skip EPG entirely — channels just show
  "Live" instead.
- **`API_BASE_URL`** — your entitlement backend. Leave blank and Live TV
  shows a developer-facing "not configured" screen instead of a paywall
  (unless `LIVE_TV_SKIP_PAYWALL` is bypassing the check entirely).
- **`PREMIUM_CHECKOUT_URL`** — your payment checkout page, encoded into the
  QR code shown on the paywall along with a device id and pairing code.
- **`LIVE_TV_SKIP_PAYWALL`** — **defaults to `true`.** Skips entitlement
  verification so Live TV unlocks for everyone, for testing channel
  browsing/playback before a real backend exists. Set to `false` once
  ready to test/ship the real paywall.

**Legal note on the default playlist:** iptv-org's aggregate index mixes
streams of unverified rights status. `IPTV_PLAYLIST_URL` and
`LIVE_TV_ALLOWED_GROUPS` exist so you can point this at a playlist you
actually have the rights to redistribute (or narrow the default down to
specific categories) before shipping to real users — this app makes no
attempt to vet the legal status of any given stream for you.

### Testing on a Fire TV / Firestick

1. Build and install as below.
2. Without `API_BASE_URL` set: opening Live TV shows the "backend isn't
   configured yet" screen — this is expected, not a bug.
3. With a real backend implementing `docs/LIVE_TV_BACKEND.md`: opening Live
   TV shows a QR code; scanning it and completing checkout on your backend
   should unlock the channel browser within a few seconds, with no app
   restart.
4. D-pad LEFT/RIGHT should move between nav items including Live TV, OK
   should open it, and BACK should return to the previous screen from any
   depth (paywall, channel browser, or the live player).

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
