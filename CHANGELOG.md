# Changelog

Development log for the MangoTV account, authentication, and cloud
synchronization system. One entry per milestone.

## Milestone 0 — Codebase Audit & Implementation Plan

**Status:** Complete.

**Changes:** None to application code (audit-only milestone, as required).

**Files added:**
- `docs/milestone-0-audit-and-plan.md` — full architecture audit, data
  inventory, and implementation plan.
- `CHANGELOG.md` — this file.

**Tests performed:** N/A (no code changed). Verified findings by reading
every persistence/network/navigation/DI source file directly rather than
inferring from naming.

**Issues discovered:**
- The app has no backend, database, or authentication today — this is a
  greenfield build for all three, not a retrofit.
- Continue Watching / Watch History has UI plumbing (`WatchProgress`,
  `RowStyle.CONTINUE_WATCHING`) but zero writer anywhere in the app; it
  will be built new in Milestone 8, not migrated.
- A local-network-only QR pairing flow already exists for adding addons
  from a phone (`AddonPairingServer` + NanoHTTPD). It is unrelated to
  accounts and must not be confused with, or broken by, the new
  cloud-backed account QR-auth flow in Milestone 4.
- This execution sandbox has no Android SDK (Android build verification
  relies on the existing `build-apk.yml` GitHub Actions workflow) and can
  reach only HTTPS egress, not raw Postgres TCP to a remote Neon instance
  (local Postgres 16 is available and will be used to rehearse migrations).

**Issues fixed:** N/A (audit-only milestone).

## Milestone 1 — Neon Database Foundation

**Status:** Complete pending one external step (see below).

**Stack decisions (confirmed with the user before starting):** Node.js +
TypeScript + Express + `pg` (plain node-postgres, no ORM) for the backend;
hand-written SQL migrations with a small custom runner instead of a
migration-framework dependency.

**Changes:** None to the existing Android app. New `server/` directory
(backend foundation, not yet wired to any HTTP framework — that's
Milestone 2):

- `server/migrations/0001`–`0011` — 11 forward-only SQL migrations
  creating `users`, `devices`, `sessions`, `qr_auth_sessions`,
  `user_settings`, `user_addons`, `addon_settings`, `watchlist_items`,
  `watch_history`, `continue_watching`, each with UUID primary keys,
  foreign keys, indexes on every FK column, `created_at`/`updated_at`,
  `deleted_at` where soft-delete is meaningful, and unique constraints
  matching real product semantics (see each file's header comment for the
  reasoning, especially `watch_history`'s generated `episode_key` column,
  which works around a NULL-uniqueness edge case for movies).
- `server/src/db/migrate.ts` — the migration runner (`npm run migrate`):
  applies pending `.sql` files in order, each in its own transaction,
  tracked in a `schema_migrations` table. Idempotent — safe to re-run.
- `server/scripts/verify-schema.ts` — schema verification (`npm run
  verify-schema`): confirms every expected table/index exists, then
  proves foreign keys, unique constraints, and `ON DELETE CASCADE` behave
  correctly by actually attempting inserts/deletes that must succeed or
  fail. Runs inside one transaction that's always rolled back, so it's
  safe to run repeatedly against a real database.
- `server/.env.example` — placeholders only (`DATABASE_URL`, `JWT_SECRET`,
  `QR_AUTH_SECRET`, `API_BASE_URL`, `NODE_ENV`, `PORT`). No real secrets.
- `server/package.json`, `tsconfig.json`, `README.md`, `.gitignore`.

**Files changed:** None outside the new `server/` directory and this
CHANGELOG.

**Tests performed:**
- `npm run typecheck` — clean.
- `npm audit` — 0 vulnerabilities (bumped `vitest` to v5 after the initial
  install flagged moderate/high advisories in its transitive `esbuild`/
  `vite` dev-dependency tree).
- Dropped and recreated a local PostgreSQL 16 database from scratch, ran
  `npm run migrate` — all 11 migrations applied cleanly; ran it again —
  correctly reported "Already up to date" (idempotency confirmed).
- `npm run verify-schema` against that freshly-migrated database — 56/56
  checks passed: every table and every index present; foreign keys reject
  orphaned rows; unique constraints reject duplicates (including the
  movie/null-season-episode edge case); `ON DELETE CASCADE` correctly
  removes every dependent row (devices, sessions, user_settings,
  user_addons, addon_settings, watchlist_items, watch_history,
  continue_watching) when a user is deleted; the verification's own use of
  a rolled-back transaction was confirmed to leave zero residual rows.
- Confirmed via `git status`/`git show` that only `.env.example`
  (placeholders) is tracked — `.env`, `node_modules/`, and `dist/` are
  git-ignored and were not staged.

**Issues discovered (self-review before marking complete):**
- `qr_auth_sessions.session_id` (a nullable FK with `ON DELETE SET NULL`)
  was missing its explicit index, inconsistent with every other FK column
  in the schema.
- Initial `vitest ^2.1.8` pulled in `esbuild`/`vite` versions with known
  moderate/high advisories (dev-tooling only, never shipped, but still
  worth clearing).

**Issues fixed:**
- Added `qr_auth_sessions_session_id_idx`; added a comment explaining why
  `device_identifier` on that table is intentionally *not* a foreign key
  (a QR session exists before any user — and therefore any `devices`
  row — does).
- Bumped `vitest` to `^5.0.0`; `npm audit` now reports 0 vulnerabilities.

**Neon connectivity — diagnosed and resolved via CI:** the user created a
Neon project and shared its pooled connection string. Confirmed
empirically (DNS resolves fine, TCP to the same IP on port 443 connects
instantly, TCP to port 5432 times out) that this development sandbox's
network policy blocks outbound non-443 TCP entirely — not a Neon,
credentials, or IPv6 issue. Rather than build a second, HTTP-driver-based
code path just to route around that restriction from inside the sandbox
(Neon's WebSocket driver is also blocked here — proxied WebSocket upgrades
aren't supported — and its plain HTTP driver doesn't support the
interactive multi-statement transactions `migrate`/`verify-schema` rely
on), added `.github/workflows/server-ci.yml`: it runs the exact same
`npm run migrate` / `npm run verify-schema` from GitHub's runners (normal,
unrestricted network access) against `secrets.DATABASE_URL`, gated so it
skips cleanly instead of failing red if that secret isn't configured yet.
This is a durable addition, not a one-off — it re-verifies every future
migration in Milestones 6-11 automatically on every push touching
`server/**`, using production's real code path rather than a sandbox
workaround.

Local `.env` holding the live credential was deleted after confirming it
couldn't be used from here — nothing sandbox-side retains it.

**Confirmed against the real Neon database.** The user added the
`DATABASE_URL` repository secret. Run #1 (the automatic push trigger, before
the secret existed) correctly skipped both DB steps with conclusion
`skipped`, not a failure — proving the graceful-skip path works. Run #2
(manually dispatched after the secret was added,
[run 34388148221](https://github.com/MikeC444/MangoTV-Live-TV/actions/runs/34388148221))
ran for real:

- `npm run migrate` applied all 11 migrations to the live Neon database in
  ~9 seconds.
- `npm run verify-schema` reported **56 passed, 0 failed** — every table,
  every index, every FK/unique constraint, and every cascade delete,
  exercised against the actual Neon Postgres instance, not just the local
  rehearsal.

This satisfies Milestone 1's "verify against a fresh Neon database"
criterion for real.

**One more issue caught from the CI log itself:** node-postgres emitted a
deprecation warning — `sslmode=require`/`prefer`/`verify-ca` are currently
treated as aliases for `verify-full` (full certificate verification, the
secure behavior already in effect), but a future major version of
`pg`/`pg-connection-string` will drop `require` to weaker libpq-standard
semantics. Updated `.env.example`'s example connection string and
`pool.ts`'s comment to recommend `sslmode=verify-full` explicitly, so this
connection's security guarantee doesn't silently change on a future
dependency bump. Not urgent (current behavior is already secure) — the
user's existing secret value doesn't need to change today, just next time
it's convenient to touch it.

**Milestone 1 is complete.**

## Milestone 2 — Backend/API Foundation

**Status:** Complete.

**Changes:** No changes to the existing Android app or Milestone 1's
schema/migrations. Adds the Express application itself on top of
Milestone 1's database layer:

- `src/app.ts` / `src/index.ts` — `createApp()` assembles the Express app
  (exported unbound for tests); `index.ts` is the real process entrypoint,
  including SIGTERM/SIGINT handling that stops accepting new connections,
  lets in-flight ones finish, then closes the DB pool before exiting —
  needed for clean deploys on any platform that sends SIGTERM (Render,
  Railway, Fly, etc. all do on every restart/redeploy).
- `src/middleware/auth.ts` (`requireAuth`) — the authentication layer.
  Verifies `Authorization: Bearer <token>` against `sessions` (hashing the
  presented token and looking up by hash, never comparing raw secrets),
  rejecting missing/malformed/unknown/expired/revoked tokens and tokens
  belonging to a soft-deleted user with the same generic 401 in every
  case, so a response can't be used to distinguish *why* a token failed.
  Attaches `req.user`/`req.session` — the only source of "who is making
  this request" anywhere in the API; no route reads an id from a
  param/query/body.
- `src/middleware/errorHandler.ts` — centralized error handling. Anything
  that isn't a deliberately-thrown `HttpError` (`src/lib/httpError.ts`) is
  treated as unexpected and never exposes its real message to the client
  (full detail goes to the server log only; a short `detail` field is
  added back only outside production, for local debugging).
- `src/middleware/rateLimit.ts` — one global limiter (120 req/min/IP) for
  now; login/QR endpoints get their own, stricter one once Milestones 3-4
  add them.
- `src/middleware/validate.ts` — a generic Zod-based validation middleware
  factory. No route needs it yet (`/health` and `/user/me` take no client
  input) — covered by its own unit test in the meantime; its first real
  caller is Milestone 3's account creation/login bodies.
- `src/middleware/requestLogger.ts` — structured JSON request logs
  (method/path/status/duration/user id, no bodies/headers/query strings)
  with a per-request id echoed as `X-Request-Id` and included in error
  responses for support correlation.
- `src/routes/health.ts` (`GET /health`, unauthenticated) and
  `src/routes/me.ts` (`GET /user/me`, authenticated) — the first two real
  endpoints, chosen specifically to exercise the auth middleware and
  prove cross-user isolation without depending on any feature from a
  later milestone.
- `src/security/tokens.ts` — shared `generateToken`/`hashToken` (random
  256-bit tokens, SHA-256 hashed at rest), used by both the auth
  middleware and the test fixtures; will be reused by Milestone 3's
  login/refresh and Milestone 4's QR tokens.
- Test infrastructure: `vitest.config.ts`, `tests/setup.ts`,
  `tests/helpers/{db,auth}.ts`, and three test files (16 tests). Tests
  require a dedicated `TEST_DATABASE_URL` (never `DATABASE_URL`) — see
  `.env.test.example` — so the suite's between-test `TRUNCATE` can never
  land on a real database by accident.
- `.github/workflows/server-ci.yml` — added a second job, `test`, running
  the suite against a throwaway `postgres:16` service container (separate
  from the `db` job's real-Neon migration check). Needs no secret at all,
  so full test coverage runs the same way on every push.

**Tests performed:**
- `npm run typecheck` — clean throughout.
- `npm audit` — 0 vulnerabilities after adding express/helmet/
  express-rate-limit/zod/supertest.
- `npm test` locally against a dedicated local `mangotv_test` database —
  **16/16 passed**, covering: no/malformed/empty/unknown/expired/revoked
  token, a soft-deleted user's token, a valid token, cross-user isolation
  (a spoofed `?id=` query param has no effect; two users' tokens never
  cross), the 404 handler, rate-limit headers, and the validate()
  middleware's accept/reject paths.
- Manual smoke test against a *real* running server (not just supertest's
  in-process simulation): started `npm run dev`, confirmed `GET /health`,
  an unauthenticated `GET /user/me` (401), an unknown route (404), and —
  after seeding a real session directly into the local dev database — a
  valid token successfully authenticating over actual HTTP, all with
  Helmet's security headers present. Seeded row deleted immediately after.

**Issues discovered (self-review before marking complete):**
- No graceful shutdown handling in `index.ts` — a SIGTERM (sent by every
  major deploy platform on restart/redeploy) would have killed in-flight
  requests and left DB connections to time out on their own instead of
  closing cleanly.
- `trust proxy` is set to trust exactly one hop, which is correct for a
  single reverse proxy in front (true of every deployment target being
  considered) but would need revisiting — not tightening blindly, actually
  reconsidering — if a future deployment adds a second hop (e.g. a
  separate CDN) in front of that, since over-trusting `X-Forwarded-For`
  makes the rate limiter spoofable.

**Issues fixed:**
- Added the SIGTERM/SIGINT handler described above.
- Documented the `trust proxy` assumption explicitly in `app.ts` as
  something to re-check at actual deployment time (Milestone 17), rather
  than leaving it an unstated assumption.

**Deliberately not built yet (belongs to later milestones, not scope creep):**
account creation/login endpoints (Milestone 3), QR auth (Milestone 4),
CORS (no browser-facing page exists until Milestone 4's activation page,
and no CORS policy is the secure default until then), and the
settings/watchlist/history/addon endpoints (Milestones 6-9).

**Milestone 2 is complete.**

## Milestone 3 — User Account System

**Status:** Complete.

**Changes:** No changes to the Android app or Milestone 1's schema. Adds
real account creation, login, logout, refresh, and session management on
top of Milestone 2's foundation:

- `src/security/password.ts` — Argon2id hashing (`argon2` package), with
  OWASP/RFC 9106's recommended interactive-login parameters (19 MiB
  memory, t=2, p=1) pinned explicitly rather than relying on the
  library's own default in case it ever changes.
- `src/schemas/auth.ts` — Zod schemas for register/login/refresh bodies:
  email normalized (trimmed, lowercased) to match the `users` table's
  `email = lower(email)` constraint, password length-only requirements
  (min 8, no forced complexity — modern NIST guidance), `deviceId`
  required as a UUID. This is validate()'s first real caller since
  Milestone 2 built it ahead of having one.
- `src/services/authService.ts` — the actual account logic:
  - `register` — hashes the password, then inserts the user + device +
    session as one transaction (all-or-nothing).
  - `login` — verifies the password and returns the same generic
    "Invalid email or password" whether the email doesn't exist or the
    password is wrong. Also closes a timing side-channel found during
    self-review: an unknown email used to skip the argon2 verify
    entirely, making that response measurably faster than a
    known-email-wrong-password one — now it always runs exactly one
    verify (against a dummy hash when there's no real user), so response
    timing can't be used to enumerate which emails have accounts.
  - `refresh` — a single atomic `UPDATE ... FROM ... WHERE ... RETURNING`
    that validates (unexpired/unrevoked session, non-deleted user,
    non-revoked device) and rotates both tokens in one statement, so
    there's no separate check-then-act race window. Rotating the access
    token turned out to invalidate the *previous* access token
    immediately too (one `access_token_hash` column per session, simply
    overwritten) — confirmed via a manual smoke test, then locked in with
    its own automated test, since it's a stricter and better property
    than what the original design comment assumed.
  - `logout` — revokes only `req.session.id` (never a client-supplied
    session id).
  - `listSessions` / `revokeSession` — list/revoke a user's own sessions;
    revoking a session id that exists but belongs to someone else returns
    404 (not 403), so the response can't confirm another user's session
    id is real. `listSessions` also excludes sessions on a
    remotely-revoked device, matching `requireAuth`'s own check
    (self-review catch — it hadn't originally).
- `src/middleware/auth.ts` — `requireAuth` now also joins `devices` and
  rejects a session whose device has been remotely revoked (the
  `devices.revoked_at` column existed since Milestone 1 but nothing read
  it until now).
- `src/middleware/rateLimit.ts` / `src/routes/auth.ts` — a stricter
  10-req/min-per-IP limiter on the whole `/auth` router (on top of the
  general 120/min one). Refactored both rate limiters and `authRouter`
  from module-level singletons into factories constructed fresh inside
  `createApp()` — the limiters carry in-memory counters, and a shared
  singleton meant one test's auth calls silently ate into another test's
  budget (found by running the test suite, not by inspection).
- New endpoints: `POST /auth/register`, `POST /auth/login`,
  `POST /auth/refresh`, `POST /auth/logout` (authenticated),
  `GET /auth/sessions` (authenticated), `DELETE /auth/sessions/:id`
  (authenticated).

**Tests performed:**
- `npm run typecheck` — clean throughout, including working around two
  real TS friction points: `argon2`'s named (not default) exports needing
  a namespace import, and its `HashOptions` type (not `Options`).
- `npm audit` — 0 vulnerabilities after adding `argon2`.
- `npm test` locally against `mangotv_test` — **43/43 passed**: account
  creation (including password never stored in plaintext, duplicate-email
  rejection, weak-password/bad-email/bad-deviceId rejection), login
  (correct credentials, case-insensitive email, wrong password, unknown
  email producing an identical response, independent sessions per
  device), refresh (rotation, the new access token working, the *old*
  access token immediately failing, replay of an already-used refresh
  token failing, expired refresh token, revoked session, revoked device),
  logout (revokes the current session, that session's access *and*
  refresh tokens both stop working immediately after), session listing
  and revocation (never shows or revokes another user's sessions, 404 —
  not a leak — on someone else's session id, 400 on a malformed one).
- Manual smoke test against a real running server: register → /user/me →
  /auth/sessions → /auth/refresh → /auth/logout → confirmed the
  now-revoked session's access token stops working, all over actual HTTP.
  This smoke test is what surfaced the "refresh invalidates the old
  access token too" behavior in the first place, before it became an
  automated test. Seeded data deleted immediately after.

**Issues discovered (self-review before marking complete):**
- The login timing side-channel described above.
- `listSessions` not excluding a revoked device's session.
- The rate-limiter/router singleton-sharing bug the test suite itself
  surfaced (test isolation issue, not a production bug, but the
  underlying "shared mutable state across app instances" pattern was a
  real architectural smell worth fixing rather than working around).

**Issues fixed:** all three, described above alongside where they were found.

**Milestone 3 is complete.**

## Milestone 4 — QR Authentication

**Status:** Complete.

**Changes:** No changes to the Android app yet (that's Milestone 5 —
this milestone is the backend flow and the phone/web side of it only).

- `migrations/0012_qr_auth_sessions_device_info.sql` — adds
  `device_name`/`platform` to `qr_auth_sessions`, captured at creation
  time so the eventual `devices` row gets a real name instead of falling
  back to generic defaults. Added as a new migration rather than editing
  0005, since 0005 is already applied to the real Neon project.
- `src/services/authService.ts` refactored to export three reusable
  pieces — `insertUser`, `verifyCredentials` (including the timing-safe
  dummy-hash check), `createSessionForDevice` — so register/login and
  the new QR flow share the same account/session logic instead of
  duplicating it.
- `src/services/qrAuthService.ts` — the actual flow:
  - `createQrSession` — random 256-bit token, hashed at rest, 10-minute
    expiry.
  - `resolveQrSession` — a read-only status peek for the activation page.
    Deliberately separate from the TV's poll endpoint: if the same
    endpoint both checked status *and* issued tokens, the web page
    loading and checking status could itself consume the one-time token
    issuance meant for the TV.
  - `completeQrSession` — called once the activation page submits
    credentials. Resolves the account (create or verify) and marks the
    QR session completed, all in one transaction — a failure partway
    through (e.g. a duplicate-email conflict) rolls back cleanly and
    leaves the QR session still usable for another attempt (tested).
    Deliberately does **not** create the real device/session/tokens at
    this point.
  - `pollQrSession` — the TV's endpoint, and the *only* place that ever
    creates the real device/session and issues tokens: it does so at the
    moment of consumption via one atomic `UPDATE ... WHERE status =
    'completed' ... RETURNING`, which can match a given token at most
    once. This is also why raw tokens are never persisted anywhere even
    transiently — they're generated and handed to the caller in the same
    request that creates their (hashed-at-rest) session row.
- `src/routes/qr.ts` mounted at `/auth/qr` as its **own** router, not
  nested under the existing auth router — that router's blanket 10/min
  limiter is right for register/login but would break legitimate TV
  polling (a TV checking every 2-3s would exhaust it in seconds).
  `/create` and `/complete` share the strict limiter (they're exactly as
  much a credential-guessing/account-spam surface as register/login);
  `/status` gets its own 40/min limiter sized for polling;
  `/resolve` relies on the general limiter (called once per page load).
- `public/activate.html` + `activate.js` — the activation page, served by
  this same backend (co-hosting means its `fetch()` calls to `/auth/qr/*`
  are same-origin, so no CORS configuration was needed at all). No
  external scripts/styles/fonts, so Helmet's default CSP needed no
  changes. Sign-in/create-account tabs, a pre-check against `/resolve` so
  an expired/used code shows a clear message instead of a live form,
  `.textContent` everywhere a message is rendered (no `innerHTML`, so no
  reflected-content XSS surface).
- `.env.example` / `config/env.ts` cleanup: removed `JWT_SECRET` and
  `QR_AUTH_SECRET`, both listed in the original spec as expected
  placeholders but never actually needed — this design uses opaque,
  random, hashed-at-rest bearer tokens throughout (sessions *and* QR
  alike), never JWTs or HMAC-signed values, so neither secret ever found
  a real job. `API_BASE_URL` is now genuinely required, but scoped as a
  lazily-evaluated `getApiBaseUrl()` rather than a field on the shared
  `env` object — that object is imported by `db/pool.ts`, and therefore
  by `migrate.ts`/`verify-schema.ts`, which have nothing to do with QR
  auth and shouldn't need it set just to run a migration.

**Tests performed:**
- `npm run typecheck` / `npm audit` — clean.
- `npm test` locally against `mangotv_test` — **60/60 passed** (up from
  43): QR generation, resolve status transitions (pending/expired/
  not_found, and confirmed non-mutating), the full create→complete→poll
  flow for both account creation and existing-account sign-in, wrong
  password leaving the QR session still usable, expiration, replay
  prevention (a second poll after consumption reports expired, never
  hands out tokens twice), completing an already-completed or expired
  session failing cleanly (410), a duplicate-email QR registration
  failing the same way direct registration does, two fully independent
  simultaneous QR sessions on different devices never cross-contaminating
  tokens or accounts, and the activation page itself being served at the
  exact URL the QR code encodes.
- Manual smoke test against a real running server, over actual HTTP:
  create → resolve (pending) → status (pending) → complete (204) →
  status (delivers real tokens) → second status (expired) → the returned
  access token working on `/user/me`. This is what caught a real gap
  before it reached CI: the new migration had only been applied to the
  test database automatically (via `pretest`), not to the local dev
  database, so the first smoke-test attempt failed with a clear "column
  does not exist" error — not a code bug, but a reminder that automated
  tests and manual smoke tests exercise different databases and both are
  worth running.

**Issues discovered (self-review before marking complete):** none required
a code fix beyond one minor robustness gap — `activationUrl()` didn't
strip a trailing slash from `API_BASE_URL`, which would have produced a
broken double-slash URL for anyone who configured it with one.

**Issues fixed:** added the trailing-slash strip.

**Deliberately not built yet:** a periodic cleanup job for expired/consumed
`qr_auth_sessions` rows — they're small, inert, and harmless to accumulate
at this scale, and a scheduled job is real infrastructure this milestone
doesn't otherwise need. The actual Fire TV screens that call these
endpoints (QR display, polling from the app, navigation gating) are
Milestone 5, not this one.

**Milestone 4 is complete.**
