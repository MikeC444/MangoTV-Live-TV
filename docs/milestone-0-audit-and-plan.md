# Milestone 0 — Codebase Audit & Implementation Plan

Status: **complete**. No application code was modified to produce this document.

## 1. Application architecture

- Native Android app, Kotlin 2.0.21 + Jetpack Compose, AGP 8.5.2, single Gradle
  module (`app`). `minSdk 23`, `target/compileSdk 34`.
- Targets Fire TV / Android TV specifically: `android.software.leanback`
  feature, `LEANBACK_LAUNCHER` intent filter, landscape-locked single
  `Activity`, a custom `TvFocusSurface` primitive used everywhere for D-pad
  focus/scale/glow states.
- No DI framework. `MangoTvApplication` builds one hand-rolled `AppContainer`
  holding the app's repositories as eager/lazy singletons
  (`app/src/main/java/com/mangotv/app/AppContainer.kt`). This is a deliberate
  existing choice (documented in its own kdoc) — the plan below keeps this
  pattern rather than introducing Hilt/Dagger.
- Single `MainActivity` hosts one Compose tree; navigation is Jetpack
  Navigation-Compose with string routes (`navigation/MangoRoutes.kt`,
  `navigation/MangoNavHost.kt`). Tab-root destinations (Home, Movies, TV
  Shows, Genres, Search, My List, Settings) preserve back-stack state on
  switch; everything else pushes normally.
- Content is addon-driven, Stremio-protocol-compatible
  (`data/provider/CatalogProvider.kt` interface, `ProviderRegistry`
  in-memory singleton, `StremioAddonProvider` + `StremioAddonClient` +
  `StremioMapper`/`StremioModels`). The UI never talks to a provider
  directly — only through the normalized `Content`/`HomeSection`/`Stream`
  models in `data/model/`.
- Playback is Media3/ExoPlayer (HLS + DASH + OkHttp datasource).
- Networking today is OkHttp + kotlinx.serialization, hand-written per
  client (no Retrofit, no generic API-client abstraction yet). This is the
  style the new backend API client should match.
- **No backend, no database, no authentication exist today.** This is
  greenfield for all of it — there is no legacy schema or legacy auth to
  preserve or migrate away from.

## 2. Startup flow

```
MangoTvApplication.onCreate()
  → new AppContainer(context)
      → AddonRepository constructed eagerly:
          init{} launches restoreFromDisk() on a background scope
            - reads installed addons from DataStore
            - registers each enabled one into ProviderRegistry
            - on true first-ever launch, auto-installs Cinemeta
MainActivity.onCreate()
  → setContent { MangoTvTheme { MangoNavHost() } }
      → NavHost(startDestination = MangoRoutes.HOME)   // unconditional
```

There is currently **no gate of any kind** before Home. Home renders
immediately and shows its own Loading/Empty state while
`ProviderRegistry.providers` is still empty. Milestone 5 will insert an
auth-check step between `AppContainer` construction and `NavHost`'s start
destination.

## 3. Persistence inventory

Full-project search for `SharedPreferences`, `Room`, `SQLite`,
`openOrCreateDatabase`, local files, and serialized objects turned up
**zero** matches outside the mechanism below. The entire app's persistence
is Jetpack **DataStore (Preferences)**, used four separate times, each as
one named store holding a single `stringPreferencesKey` with a
kotlinx.serialization JSON blob:

| DataStore file | Key | Shape | Owner |
|---|---|---|---|
| `mango_addons` | `installed_addons_json` | `List<InstalledAddon>` (manifestUrl, full `AddonManifest`, `enabled`); list order = row order | `AddonRepository` |
| `mango_addons` | `default_addon_bootstrapped` | `Boolean` | `AddonRepository` (internal one-shot flag, not user data) |
| `mango_my_list` | `my_list_json` | `List<SavedListItem>` (id, type, title, posterUrl, backdropUrl, year, rating, providerId, addedAtMillis) | `MyListRepository` |
| `mango_home_row_prefs` | `home_row_prefs_json` | `HomeRowPreferences` (order: List<rowId>, hiddenRowIds: Set<rowId>) | `HomeRowPreferencesRepository` |
| `mango_player_preferences` | `player_preferences_json` | `PlayerPreferences` (autoplayNextEpisode, skipIntroEnabled) — global, not per-title | `PlayerPreferencesRepository` |

Additionally:

- **Coil image disk cache** (`cacheDir/image_cache`, 50–250MB bounded) — pure
  image bytes, not user data.
- **In-memory only, rebuilt every process start:** `ProviderRegistry`
  (providers list, derived from the addons DataStore),
  `ui/detail/PendingDetailCache` (a self-clearing one-shot handoff map, not
  persistence).
- **A local-network-only pairing channel** (`data/addon/AddonPairingServer.kt`,
  NanoHTTPD + zxing QR) lets a phone on the same Wi-Fi push an addon
  manifest URL to the TV. This is **unrelated to accounts** — no cloud
  component, no auth — but it establishes the QR-pairing UX pattern
  (`QrCodeGenerator`/`QrCodeImage`) that Milestone 4's account QR flow will
  reuse visually while replacing the local-HTTP transport with the real
  backend.

### Important gap: Continue Watching / Watch History does not exist yet

`Content.watchProgress` / `Episode.watchProgress` (`data/model/Content.kt`)
and `RowStyle.CONTINUE_WATCHING` (rendered by `ContentCard`) are real,
wired-up **display** plumbing, but **nothing in the app ever constructs a
non-null `WatchProgress`**: `PlayerViewModel` never writes playback position
on pause/stop/tick, and `HomeViewModel` never builds a Continue Watching
section. This is not a case of "existing local data to migrate" — Milestone
8 is building this feature for the first time, with sync as a first-class
part of its design from the start, not bolted on after a local-only version
already shipped.

### Settings inventory

There is no dedicated "Settings" data domain today — what exists is spread
across the two repositories above plus the in-player settings panel:

- Settings screen (`ui/settings/SettingsScreen.kt`) currently exposes only
  **Addons** and **Home Rows** — no account/profile section exists yet.
- Player preferences (autoplay next episode, skip intro) are set from the
  in-player Settings overlay (`ui/player/overlay/SettingsPanel.kt`), backed
  by `PlayerPreferencesRepository`.
- Home Rows (manual order + hidden rows) are set from
  `ui/settings/HomeRowsScreen.kt`, backed by `HomeRowPreferencesRepository`.

## 4. Categorized data inventory

| # | Category | Items |
|---|---|---|
| 1 | **Must sync to account** | Installed addons + manifest cache + enabled flag + order (`InstalledAddon` list); My List / watchlist items (`SavedListItem` list); Home row order/hidden prefs; Player preferences (autoplay, skip‑intro); Watch history / continue‑watching / playback position (net‑new, built in Milestone 8) |
| 2 | **Device‑specific** | Generated device identifier/display name (Milestone 3); the device's own local session/refresh token (never itself synced — each device authenticates independently); the local‑LAN addon‑pairing server's ephemeral IP:port |
| 3 | **Cache only** | Coil image disk cache; `ProviderRegistry`'s in‑memory provider instances (rebuilt from synced addon config on every launch); post‑Milestone‑6+, the local DataStore copies of every item in row 1 become read‑through caches of the Neon‑owned values |
| 4 | **Temporary/session data** | `PendingDetailCache`; in‑flight QR‑auth session state; Compose/ViewModel UI state (search text, focus, loading/error flags); the `default_addon_bootstrapped` internal flag |

## 5. Sandbox environment constraints (affects how later milestones get verified)

Recorded here because they shape how "run the relevant tests/build checks"
gets satisfied at each milestone:

- **No Android SDK is installed in this execution environment** — only a
  JDK 21 and the project's own Gradle wrapper. `./gradlew assembleDebug`
  cannot be run here; the existing GitHub Actions workflow
  (`.github/workflows/build-apk.yml`, `android-actions/setup-android@v3`)
  is what actually compiles the APK on push. Android-side milestones will
  be verified by careful reading + Kotlin-level consistency checks locally,
  with the real compile signal coming from that CI workflow after push (an
  emulator smoke-test job was already removed from that workflow — see its
  inline comment — because GitHub-hosted runners don't reliably support
  hardware-accelerated emulation; runtime/UI behavior on real hardware is
  called out explicitly wherever this matters).
- **PostgreSQL 16 server + client are installed locally**, so Neon-targeting
  migrations can be rehearsed end-to-end against a real local Postgres
  before touching the user's actual Neon project (Neon is standard
  wire-compatible Postgres, so this is a faithful rehearsal, not a mock).
- **This sandbox's outbound network only proxies HTTPS**; raw-TCP database
  connections (the normal Postgres wire protocol Neon and `pg`/`node-postgres`
  use) are explicitly not tunnel-able from here. Practical effect: I can
  fully build, migrate, and test against **local** Postgres from this
  session, but connecting this session directly to a **remote** Neon
  instance over the standard port isn't possible — final verification
  against the user's real Neon project needs to happen where normal
  network egress exists (the deployed backend itself, or a session with
  unrestricted egress), not by this sandbox reaching Neon directly. This
  doesn't block building/testing the schema and API logic here; it only
  affects who runs the very last "does it work against my real Neon"
  check.
- No test directory exists anywhere in the repo today (`app/src/test`,
  `app/src/androidTest`) — zero automated tests currently. The new backend
  will carry its own test suite from the start (Milestone 2's completion
  criteria require it); Android-side testing stays manual/CI-build-level
  unless a milestone specifically calls for instrumented tests.

## 6. Implementation plan

### Files likely to change (Android app)

- `AppContainer.kt` — add auth/session/API-client/sync-manager wiring
  alongside the existing repositories (additive, not a rewrite).
- `MangoTvApplication.kt` — construct session state at startup.
- `MainActivity.kt`, `navigation/MangoNavHost.kt`, `navigation/MangoRoutes.kt`
  — insert the auth gate before `HOME`, add auth/QR routes.
- `ui/settings/SettingsScreen.kt` — add an Account section (sign out, switch
  account, sync/device status).
- `AddonRepository`, `MyListRepository`, `HomeRowPreferencesRepository`,
  `PlayerPreferencesRepository` — each gains a remote-sync path layered on
  top of its *existing* DataStore read/write, which becomes the local
  cache. None of these get rewritten from scratch.
- `data/model/Addon.kt`, `data/provider/MyListRepository.kt`'s
  `SavedListItem`, etc. — extend with sync metadata (`updatedAt`,
  soft-delete) where the sync design needs it.

### Files to create (Android app, introduced incrementally across milestones, not all at once)

- `data/network/` — `ApiClient` (OkHttp + kotlinx.serialization, matching
  the existing `StremioAddonClient` style), auth-header interceptor,
  refresh-on-401 interceptor.
- `data/auth/` — `AuthRepository`, `SessionManager` (secure local token
  storage), `DeviceIdentity` (a generated UUID persisted locally — **not**
  hardware fingerprinting per the spec), `QrAuthClient`.
- `data/sync/` — `SyncManager`, an upload/retry queue, per-domain sync
  repositories.
- `data/history/` — new `WatchHistoryRepository` / `ContinueWatchingRepository`
  (this domain doesn't exist yet at all — see §3).
- `ui/auth/` — the auth-gate screen, sign-in/create-account, QR display,
  `AuthViewModel`.
- `ui/settings/AccountScreen.kt` — account/device/session management, and
  Milestone 11's "Sync existing data / Start fresh" prompt.

### New backend service

The repo has no backend today, so this is a new top-level directory
(`server/`) alongside `app/` — its own `package.json`/test suite/deploy
unit, never bundled into the APK, never given Neon credentials on-device.
Exact layout proposed in the follow-up question to you before I start
writing it (stack choice affects this).

### Database tables (Neon Postgres) — adapted to what actually exists

Kept relational per the spec's constraint (no single giant JSON blob for
core entities); JSON/JSONB only for genuinely flexible bits (addon manifest
cache, per-addon config blob if one ever needs settings beyond
enabled/order):

`users`, `devices`, `sessions`, `qr_auth_sessions`, `user_settings`
(home-row prefs + player prefs), `watchlist_items` (item-level rows, not a
blob — mirrors `SavedListItem`'s fields plus `updated_at`/`deleted_at`),
`watch_history` + `continue_watching` (need to decide one table vs. two
during Milestone 1 against the real `WatchProgress` shape:
positionMs/durationMs/season/episode), `user_addons` (mirrors
`InstalledAddon`: manifest_url, cached manifest JSONB, enabled, sort order).
Whether `addon_settings` is its own table or folds into `user_addons` gets
decided in Milestone 1 — today's addons have no configurable state beyond
enabled/order, so a separate table only earns its keep if it's designed for
future per-addon config rather than speculative.

### API endpoints

The spec's example list, shaped around the real payloads above (e.g.
`/user/addons` carries `manifestUrl` + cached manifest + `enabled` + order,
not a placeholder shape; `/user/watchlist` mirrors `SavedListItem`). Every
handler resolves the user from the authenticated session — never from a
client-supplied user id.

### Authentication flow

QR-first (the existing addon-pairing screen already establishes that a TV
remote is the wrong input device for anything text-heavy, and Fire TV has
no reliable password-manager autofill) — TV requests a short-lived,
single-use activation token from the backend, renders it as a URL+QR
(reusing `QrCodeGenerator`/`QrCodeImage`), a phone/web page completes
sign-in or account creation against that token, and the TV picks up
completion via polling (simplest reliable option on Fire TV; SSE/WebSocket
considered but polling avoids keeping a long-lived connection alive across
Fire TV's aggressive process/network suspension behavior — this can be
revisited in Milestone 4 if polling proves too slow in practice).

### Sync architecture

Local DataStore stays the read path/cache for instant UI + offline (per the
spec's "local storage is a cache" rule); a sync layer pushes local
mutations to the API and pulls remote changes on login/interval/foreground;
`updated_at`-based last-write-wins to start, with item-level soft deletes
for watchlist/history so a remove on one device doesn't get resurrected by
a stale add from another.

### Migration strategy

Because Milestone 0 found no existing account system, "migration" in the
Milestone 11 sense (existing local data at first-login) applies to whatever
is already sitting in the four current DataStore files at the moment a user
first authenticates on an already-used device — there's no server-side data
to reconcile against for a brand-new account, so the SYNC/START FRESH
prompt mostly matters for re-installs and additional devices, exactly as
Milestone 11 describes.

## 7. Open items before Milestone 1 can start

Raised to the user directly (see chat) rather than assumed:

1. How to obtain a live Neon Postgres connection for real verification
   (this sandbox can rehearse against local Postgres 16 but cannot reach a
   remote Neon instance directly — see §5).
2. Backend language/framework choice (default proposed: Node.js +
   TypeScript + Express + `pg`, hand-written SQL migrations, no heavy ORM).
