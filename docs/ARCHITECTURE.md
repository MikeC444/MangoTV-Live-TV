# Architecture

Reference documentation for the MangoTV account, authentication, and cloud
synchronization system as it stands after Milestones 0–16. This describes
the finished system, not the order it was built in — see `CHANGELOG.md`
for the milestone-by-milestone history of *why* each piece looks the way
it does.

## 1. System overview

```
 Fire TV / Android TV app                 Backend (Node/Express)              Neon Postgres
┌───────────────────────┐               ┌──────────────────────┐           ┌──────────────────┐
│ Jetpack Compose UI     │               │ requireAuth           │           │ users             │
│ AppContainer (DI)      │   HTTPS       │ (session lookup,      │  TLS      │ devices           │
│ Repositories (local    │──────────────▶│  device revocation)   │──────────▶│ sessions          │
│  DataStore cache)      │  Bearer token │                        │  verify-  │ qr_auth_sessions  │
│ SyncManager +          │◀──────────────│ Zod-validated routes   │  full     │ user_settings     │
│  4 domain sync repos   │   JSON        │ → services (business   │◀──────────│ user_addons       │
│ AuthRepository         │               │   logic, atomic SQL)   │           │ addon_settings    │
│ (encrypted session,    │               │ → pg.Pool              │           │ watchlist_items   │
│  Tink/Keystore)        │               │                        │           │ watch_history     │
└───────────────────────┘               └──────────────────────┘           │ continue_watching │
                                                                              └──────────────────┘
```

**The Fire TV app never holds a database credential and never connects to
Postgres directly.** `BuildConfig.API_BASE_URL` (injected at build time
from the gitignored `local.properties`) is the *only* network destination
the account system's code knows about, and `app/build.gradle.kts` fails
the build outright if that value doesn't start with `https://` (Milestone
14/15) — there is no way to ship a build that would send account traffic,
including bearer tokens, anywhere but an HTTPS endpoint. The backend is
the only thing that holds `DATABASE_URL`, and it's read from environment
variables (`server/.env`, gitignored; a real deployment's secret store in
production) — never hardcoded, never bundled into anything shipped to a
device. See [Deployment](DEPLOYMENT.md) for how each half is actually run.

Three layers, three responsibilities:

- **Fire TV app** — Jetpack Compose UI, a hand-rolled `AppContainer`
  dependency graph (`app/src/main/java/com/mangotv/app/AppContainer.kt`),
  and a set of repositories that read/write a local DataStore cache first
  and push/pull to the backend asynchronously. The UI never blocks on the
  network — every screen renders from the local cache, which cloud sync
  keeps current in the background.
- **Backend** (`server/`) — Express + TypeScript + `pg` (no ORM). Owns
  every authentication and authorization decision; every route that
  touches user data resolves "who is asking" exclusively from the verified
  session (`req.user`, set by `requireAuth`), never from a client-supplied
  id in a param/body/query. See [API](../server/README.md) for the full
  endpoint reference.
- **Neon Postgres** — the durable source of truth for every account-owned
  entity. Local storage on the device is a cache; if local and cloud ever
  disagree, cloud wins except in the one deliberate case a user is asked
  about directly (first-login migration — §4.4).

## 2. Database

12 forward-only migrations (`server/migrations/0001`–`0012`), applied by
`server/src/db/migrate.ts` (`npm run migrate`) and verified against a real
database by `server/scripts/verify-schema.ts` (`npm run verify-schema`).
Every table uses a `uuid` primary key (`gen_random_uuid()`), every foreign
key column has an explicit index (Postgres doesn't create one
automatically for the referencing side), and every table has
`created_at`/`updated_at`; tables that support removal use `deleted_at`
(soft delete) rather than an actual `DELETE`, so sync can tell "removed"
apart from "never existed" on a later pull.

| Table | Purpose | Key design points |
|---|---|---|
| `users` | One row per account. | `email` uniquely indexed, always lowercased (`CHECK` constraint); `password_hash` (Argon2id) is `NOT NULL` — this system has no other credential type yet; `deleted_at` reserved for account closure (unused today). |
| `devices` | One row per (account, physical device) pairing. | `device_identifier` is a locally-generated UUID the app persists once — never a hardware serial/ANDROID_ID/MAC address. Unique per `(user_id, device_identifier)`, **not** globally unique on `device_identifier` alone — the same physical TV can have a separate row under each account that's ever signed into it (Milestone 12). `revoked_at` lets a user remotely sign a device out; every session tied to it is invalidated the moment `requireAuth` next checks it. |
| `sessions` | One row per logical login (one refresh-token lifetime). | Both `access_token_hash`/`refresh_token_hash` are SHA-256 hashes — raw tokens are never persisted anywhere, even transiently. `/auth/refresh` rotates both hashes in place on the same row, so "list my sessions" shows one entry per device login, not one per refresh. Deliberately opaque, DB-checked tokens, not self-contained JWTs — revocation and expiry take effect immediately, not "whenever the JWT's own claim expires." |
| `qr_auth_sessions` | Backs the Fire TV QR sign-in/create-account flow. | `token_hash` only (never the raw activation token). `device_identifier` is **not** a foreign key to `devices` — a QR session exists before any account is signed in, so no `devices` row can exist yet to reference. `status` (`pending → completed → consumed`, or `expired`) enforces single-use at the data layer: the TV's poll claims a `completed` row via one atomic `UPDATE ... WHERE status = 'completed'`, which can match at most once. |
| `user_settings` | One row per account (1:1). | Mirrors the two local settings domains (Home Rows order/hidden state, Player autoplay/skip-intro). `home_row_order`/`hidden_row_ids` are JSONB (opaque, ordered lists with no independent identity); autoplay/skip-intro are plain booleans. |
| `user_addons` | One row per installed addon per account. | `manifest_json` caches the addon's own (externally-defined) manifest as JSONB — this system doesn't interpret it, just stores and echoes it back. Relational columns it *does* query by (`manifest_url`, `enabled`, `sort_order`) stay real columns, not buried in the JSON. Unique on `(user_id, manifest_url)`. |
| `addon_settings` | Reserved extensibility point. | Would hold per-addon configuration (Stremio's "configurable" addon protocol) if a future feature needs it. Confirmed still genuinely unused — no addon configurability beyond enabled/order exists in the app today. |
| `watchlist_items` | Item-level My List records. | Deliberately not a single blob replaced wholesale on every change. One row per `(user_id, provider_id, content_id, content_type)` slot, kept forever and toggled via `deleted_at` — removing and re-adding a title is an `UPDATE`, not a fresh row, which is what makes incremental sync (diffing on `updated_at`) meaningful. A partial index (`WHERE deleted_at IS NULL`) backs the "give me my current list" read path. |
| `watch_history` | Durable per-(user, title, episode) playback log. | Bounded, not an append-only log — one row per unique movie/episode ever watched, upserted on each report. A generated `episode_key` column (`coalesce(season::text,'x') \|\| ':' \|\| coalesce(episode::text,'x')`) works around plain SQL treating `NULL = NULL` as non-matching, which would otherwise let the natural-key unique constraint silently admit duplicate movie rows. Keyset-paginated via `(user_id, watched_at DESC)` — no Fire TV screen consumes this yet (no History browse UI exists), but the data is captured and ready. |
| `continue_watching` | One row per (user, title) — the resumable pointer. | Not a view over `watch_history`: written/removed independently (cleared once a title is completed or dismissed) and carries its own display fields (`backdrop_url`) history doesn't need. Maintained from application logic (`playbackProgressService.recordProgress`), not a DB trigger, so the "mark complete → clear continue_watching" behavior stays visible and testable in code. |

`content_type` (`MOVIE` | `TV_SHOW`) and `qr_auth_status` (`pending` |
`completed` | `consumed` | `expired`) are shared Postgres enums
(`0001_extensions_and_enums.sql`); `content_type`'s values intentionally
match the Android app's own `ContentType` enum names exactly, so the API
layer never translates between the two.

## 3. Authentication

Two independent paths reach the same backend account system: the original
QR/phone-assisted flow (3.1), and a direct on-device email/password flow
added later for a user who'd rather type on their remote than involve a
second device (3.2). Both produce an identical `Session` on the TV and are
indistinguishable to everything downstream (sync, token refresh, logout) —
see `AuthGateViewModel`, which doesn't know or care which path a session
came from.

### 3.1 QR flow (phone-assisted, no typing on the TV)

```
TV                          Backend                       Phone/web (public/activate.html)
│  POST /auth/qr/create      │                             │
│ {deviceId, deviceName,     │                             │
│  platform}                 │                             │
├────────────────────────────▶ generates a random 256-bit  │
│                             │ token, stores only its       │
│                             │ SHA-256 hash, 10-min expiry  │
│ ◀── {token, activationUrl,│                             │
│      expiresAt}            │                             │
│  (renders activationUrl    │                             │
│   as a QR code)             │                             │
│                             │  GET /activate?token=...   │
│                             │◀────────────────────────────┤ (scanned)
│                             │  GET /auth/qr/resolve       │
│                             │◀────────────────────────────┤ (status peek, read-only)
│                             │  POST /auth/qr/complete     │
│                             │  {token, mode, email,       │
│                             │   password, displayName?}   │
│                             │◀────────────────────────────┤
│                             │ creates/verifies the        │
│                             │ account; marks the QR       │
│                             │ session 'completed'         │
│  GET /auth/qr/status        │ (polled every 2.5s)         │
├────────────────────────────▶ status='completed' → creates │
│                             │ the real devices/sessions    │
│                             │ rows and issues real tokens  │
│                             │ atomically, exactly once,    │
│                             │ flips status to 'consumed'   │
│ ◀── real access+refresh   │                             │
│      tokens, user           │                             │
```

Why this shape, specifically:

- **The QR code/URL never carries anything sensitive** — just an opaque,
  random, single-use token. It cannot be decoded into a password or
  account information.
- **Resolve and status are deliberately separate endpoints.** If checking
  status also issued tokens, the web page loading (which calls resolve)
  could accidentally consume the token issuance meant for the TV.
- **Tokens are minted at the moment of consumption, not at completion.**
  `completeQrSession` (the web page's call) never creates a real
  device/session — only `pollQrSession` (the TV's own poll) does, via one
  atomic `UPDATE qr_auth_sessions SET status='consumed' ... WHERE
  status='completed'`. This is what guarantees a token can be claimed at
  most once even under concurrent pollers, and it's why a raw token is
  never persisted anywhere, even transiently — it's generated and handed
  to its owner in the same request that creates its (hashed) row.
- **The TV detects completion itself** by polling `/auth/qr/status` every
  2.5 seconds (`QrSignInViewModel`) — never a manual "refresh" action. A
  QR code that expires mid-wait is silently replaced with a fresh one,
  transparently to the user.
- **Along this path specifically, the Fire TV app never collects or
  transmits a password.** Account creation and sign-in happen exclusively
  on the phone/web activation page, which is served by the same backend
  (`GET /activate`) so its own calls are same-origin — no CORS
  configuration needed. (A second path that *does* type a password
  on-device exists alongside this one — see 3.2.)

### 3.2 Direct sign-in (typed on the Fire TV remote)

Reached by way of `AuthMethodScreen` (`auth/method/{intent}`) — the screen
`AuthStartScreen`'s Log In/Sign Up buttons both lead to now, asking
"Scan a QR Code" or "Type on My Remote" before committing to either path.
Choosing the latter takes a user who doesn't want to involve a phone at
all to `PasswordSignInScreen`/`PasswordSignInViewModel`, which
call the `/auth/register` and `/auth/login` endpoints directly — the same
endpoints `authService.register`/`.login` expose, built on the identical
`insertUser`/`verifyCredentials` helpers the activation page's own
`POST /auth/qr/complete` handler (`qrAuthService.completeQrSession`) uses
internally. Nothing new on the backend: this is a second caller of
endpoints Milestone 3 already built and tested, never invoked from the TV
until now.

```
TV                                Backend
│  POST /auth/register            │
│  {email, password,              │
│   displayName?, deviceId,       │
│   deviceName, platform}         │
├──────────────────────────────────▶ creates the user (Argon2id hash),
│                                   │ the devices row, and a real
│                                   │ session — all in one transaction
│ ◀── access+refresh tokens, user │
```

`POST /auth/login` is the same shape without `displayName`, verifying
credentials instead of creating an account. Both are one direct request/
response — no polling, no second device, no QR token in between.

Why this is additive rather than a replacement:

- **The QR flow stays exactly as it was.** `AuthStartScreen`'s "Log In"/
  "Sign Up" buttons still exist and still ultimately reach the exact same
  `QrSignInScreen`; `AuthMethodScreen` sits in front of both paths as a
  chooser, not a modification of either one.
- **The `intent` argument threads through unchanged.** `AuthMethodScreen`
  passes the same "login"/"register" string it received straight through
  to whichever path is chosen (`auth/qr/{intent}` or
  `auth/password/{intent}`) — it only ever decides which headline that
  next screen shows, never any actual behavior; see
  `MangoRoutes.authMethod`'s kdoc.
- **Client-side validation is deliberately loose.** `validateCredentials()`
  (a plain, unit-tested top-level function) only catches obvious typos —
  a missing `@`, no domain dot, a too-short password — before spending a
  network round trip. The server's own zod schema (`registerSchema`/
  `loginSchema`) remains the actual authority on what's valid; this is
  just to avoid an obviously-doomed request.
- **Same device identity either way.** Both paths call the same
  `DeviceIdentity.getOrCreate()` used by the QR flow, so a device that
  uses one path today and the other tomorrow is still recognized
  server-side as the same physical device (`devices` is unique on
  `(user_id, device_identifier)` — see 3.5).
- **Same post-auth migration handling.** Whether a session came from a QR
  scan or a typed password, `FirstLoginMigrationCoordinator.decide()`
  makes the identical sync-vs-start-fresh decision afterward — the two
  ViewModels deliberately duplicate this handling rather than share it
  (see `PasswordSignInViewModel`'s kdoc), since two call sites isn't yet
  enough to justify guessing at a shared abstraction's shape.

### 3.3 Session flow

- **Access token**: 1 hour. **Refresh token**: 30 days. Both are random
  256-bit values, SHA-256-hashed before they ever touch storage — raw
  values live only in memory on the device and in the one HTTP response
  that issues them.
- **`requireAuth`** (every authenticated route) hashes the presented
  bearer token and looks it up by hash — never by comparing a raw secret.
  It joins `devices` and rejects a session whose device has been remotely
  revoked. Every failure mode (missing header, malformed header, unknown
  token, expired token, revoked session, revoked device, a soft-deleted
  owning user) returns the identical generic 401, so a response can never
  be used to probe *which* of those states a token is in.
- **Refresh** (`POST /auth/refresh`) is one atomic `UPDATE ... WHERE
  refresh_token_hash = $1 AND ... RETURNING`, which both validates and
  rotates in the same statement — no separate check-then-act window a
  race could exploit. Rotating on every use means a stolen-and-reused
  *old* refresh token simply fails from then on. Rotation also
  invalidates the *previous* access token immediately (one
  `access_token_hash` column, overwritten), a stricter property than
  originally assumed, confirmed by a dedicated test.
- **On the Fire TV app**, `AuthRepository.ensureFreshSession()` treats
  only a *confirmed* 401 as proof the session is dead. A network failure,
  a 5xx, or any other unexpected error leaves the local session exactly
  as-is — a temporary outage is never a reason to sign someone out
  (Milestone 13).

### 3.4 Token expiration

| Token | Lifetime | What expiration means |
|---|---|---|
| Access token | 1 hour | Rejected by `requireAuth`; the app transparently calls `/auth/refresh` (serialized behind a `Mutex` so concurrent callers don't race the same refresh token) and retries. Invisible to the user in the common case. |
| Refresh token | 30 days | A confirmed 401 on refresh clears the local session and returns the user to the authentication screen. `AuthGateViewModel`'s own local check (`session.isRefreshTokenValid()`) also gates whether the app tries to proceed straight to Home at launch, without needing a network round trip to make that decision. |
| QR activation token | 10 minutes | Enforced both by `expires_at` and by `status` flipping away from `pending`/`completed` once consumed or timed out — either way, a stale token reports as `expired` to any poller, indistinguishable from a used or fabricated one. |

### 3.5 Device association

```
User
├── devices row (device_identifier = TV-1's UUID)
├── devices row (device_identifier = TV-2's UUID)
└── devices row (device_identifier = phone/other's UUID, if ever signed in)
```

- A device identifier is generated once on-device (`DeviceIdentity.kt`,
  a plain random UUID in unencrypted DataStore — not a hardware serial,
  `ANDROID_ID`, or MAC address, per the project's explicit no-invasive-
  fingerprinting requirement) and sent as `deviceId` on every QR-session
  creation.
- `devices` is unique on `(user_id, device_identifier)`, not on
  `device_identifier` alone — the same physical Fire TV signing into two
  different accounts over time gets two separate rows, so revoking one
  account's access to that hardware never touches the other's (Milestone
  12's account-switching design).
- `GET /auth/sessions` / `DELETE /auth/sessions/:id` (built, tested since
  Milestone 3) let an account list and remotely revoke its own other
  active sessions. No Fire TV screen calls these yet — device/session
  management UI is confirmed out of scope through Milestone 12 (see
  `AccountScreen.kt`'s own notes) and remains a clean extension point.

### 3.6 Logout

- **Local device** (`POST /auth/logout`, then `AccountSwitchCoordinator.signOut()`
  on the Fire TV app): revokes only the calling session
  (`req.session.id` — never a client-supplied id), then — regardless of
  whether that network call even succeeds — clears every local cache
  (Settings, My List, Continue Watching, Addons), drops every pending
  sync outbox entry, and resets the "first-login migration already
  resolved" flag, all in parallel (Milestone 12). This is what makes it
  safe for a *different* account to sign in on the same device
  immediately after: nothing from the previous account's session persists
  locally, visibly or in a queued write that could otherwise land in the
  new account's cloud data under the old account's stale credentials.
- **Remote** (`DELETE /auth/sessions/:id`): revokes a specific session by
  id, scoped to the caller's own `user_id` in the same `WHERE` clause —
  a session id that exists but belongs to someone else matches zero rows,
  returning 404, indistinguishable from an id that doesn't exist at all.
- Logout never deletes cloud data. The account continues to own
  everything server-side; only the device's own local copy and this one
  session are affected.

## 4. Synchronization

### 4.1 What syncs

Four domains, each with its own Android `*SyncRepository` and backend
route pair:

| Domain | Local cache | Backend table(s) | Sync repository |
|---|---|---|---|
| Settings | `HomeRowPreferencesRepository`, `PlayerPreferencesRepository` | `user_settings` | `SettingsSyncRepository` |
| Watchlist / My List | `MyListRepository` | `watchlist_items` | `WatchlistSyncRepository` |
| Continue Watching | `ContinueWatchingRepository` | `watch_history` + `continue_watching` | `ContinueWatchingSyncRepository` |
| Addons | `AddonRepository` | `user_addons` | `AddonSyncRepository` |

Watch History (the durable per-episode log, as opposed to the resumable
Continue Watching pointer) is captured server-side but has no Fire TV
screen consuming it yet — the paginated `GET /user/history` endpoint
exists and is tested, ready for a future History browse UI.

### 4.2 How often it syncs

- **Pull** (cloud → local): once at app launch, if the local session
  already looks usable (`AuthGateViewModel`), and once immediately after
  a fresh QR sign-in (`QrSignInViewModel`) — both via one
  `SyncManager.syncAll()` call that pulls all four domains in parallel.
- **Push** (local → cloud): immediately, fire-and-forget, the moment a
  genuine local mutation happens (an `onLocalChange` hook fired from the
  repository method that just persisted it — never from a pull applying
  remote state, which would otherwise trigger a redundant push right back
  up). Continue Watching's push is driven by player lifecycle events
  instead (periodically while playing, on pause/stop/completion, and once
  more on player disposal) rather than a UI-mutation hook, since it has
  exactly one writer.
- **Retry** (queued → cloud, once conditions allow): drained after every
  pull, the instant `ConnectivityManager` reports the network transitioned
  from unavailable to available, and — closing a gap Milestone 10 first
  flagged — every 5 minutes for the life of the process, covering a
  device that stayed online the whole time while the backend/database
  itself was unreachable for a stretch.
- None of this ever blocks the UI. Every entry point is fire-and-forget
  from its caller's perspective; the UI always reads from the local cache.

### 4.3 Conflict resolution

**Last-write-wins, keyed on the client's own local mutation timestamp**
(`updatedAt`), not receive order. Every write is one atomic SQL statement
— `INSERT ... ON CONFLICT DO UPDATE ... WHERE EXCLUDED.updated_at >
<table>.updated_at ... RETURNING` — so the comparison and the write happen
together; two concurrent pushes can't race each other into an
inconsistent result. The response always carries the row's current
authoritative state either way (the caller's own write if it won,
whatever was already there if it lost), so a client can persist the
response as its new local truth regardless of outcome. Comparing on the
client's own timestamp (not "whichever request the server saw first")
specifically protects a device that was briefly offline from having its
genuinely newer change overwritten just because it reconnected later than
another device's older one.

Item-level domains (Watchlist, Addons) apply this per natural key
(`(provider_id, content_id, content_type)`, `manifest_url`), not per
account — one item losing a race never affects any other item.

### 4.4 Offline behavior

- A device with a previously-established session continues to work fully
  from its local cache during a network or backend outage — Home, My
  List, Continue Watching, Settings, and Addons all render from local
  `StateFlow`s that never depend on a live network call.
- A local change made while offline (or while a push otherwise fails)
  persists to a small durable per-domain outbox (`PendingChangeStore`,
  Settings excepted since it only ever needs one queued entry for its
  single row) instead of being silently dropped.
- **First-login migration** (Milestone 11) is the one place offline/local
  state is handled as an explicit decision rather than silently resolved:
  if a device has real pre-existing local data *and* the account being
  signed into already has real cloud data too, the user is asked
  SYNC vs. START FRESH rather than either side being silently overwritten.
  Every other case (no local data worth protecting, or the cloud
  confirmed empty) resolves automatically without a prompt. A cloud check
  that can't complete (offline, server error) is never treated as "empty"
  — it falls back to proceeding normally and leaves the question open for
  a later, more informed sign-in, rather than risking a silent overwrite.
- A confirmed 401 is the *only* thing that signs a user out. Every other
  failure mode — no connectivity, a timeout, a 5xx, even a malformed
  response from a degraded backend — degrades to "leave local state as
  the last-known-good and let the next successful attempt recover"
  (Milestone 13).

### 4.5 Retry behavior

- Each domain's `retryPending()` walks its own outbox: a confirmed 401
  stops the whole batch immediately (the session is dead, every remaining
  item would fail identically); a non-401 rejection for one item leaves
  it queued and moves on to the rest of the batch (that item's own
  rejection doesn't predict the others'); a network-level failure leaves
  the entire remaining batch queued and stops (they'd fail identically
  right now).
- `SyncManager.syncAll()`/`retryPendingAll()` run all four domains inside
  a `supervisorScope`, not a plain `coroutineScope` — one domain's
  pull/retry failing can never cancel the other three's (Milestone 13;
  "partial sync" is a named requirement, not an incidental property).
- Every sync method's full body — including the local-outbox read/write
  steps that sit outside the network call itself — is wrapped so nothing
  but coroutine cancellation can escape uncaught. A malformed response, a
  local storage hiccup, or any other unexpected failure degrades the same
  way a plain network failure does, rather than crashing the app
  (Milestone 13's own motivating finding).

## 5. Further reading

- [API endpoint reference](../server/README.md) — every route, request/response shape, and local backend setup.
- [Deployment](DEPLOYMENT.md) — Neon, hosting, environment variables, domains, HTTPS, the QR activation URL.
- [Testing](TESTING.md) — reproducing the full multi-device test, running the unit suite, running the automated end-to-end script.
- `CHANGELOG.md` — the milestone-by-milestone development history: what changed, what was tested, what was found and fixed, in the order it actually happened.
- `docs/milestone-0-audit-and-plan.md` — the original codebase audit and implementation plan this system was built from.
