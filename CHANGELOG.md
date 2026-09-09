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

**Outstanding:** the user needs to add `DATABASE_URL` as a GitHub Actions
repository secret (Settings > Secrets and variables > Actions); once
that's done this workflow gives the real "migrations work against a fresh
Neon database" proof this milestone's completion criteria call for.
