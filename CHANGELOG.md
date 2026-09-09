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
