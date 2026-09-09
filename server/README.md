# MangoTV server

Backend API sitting between the Fire TV app and Neon Postgres. The Fire TV
app never receives database credentials or talks to Postgres directly —
see `docs/milestone-0-audit-and-plan.md` at the repo root for the full
architecture writeup.

Milestone 1: database schema + migrations. Milestone 2: the Express API
foundation (server config, error handling, request validation,
auth/rate-limit middleware). Milestone 3 (current): real accounts.

Endpoints so far:

- `GET /health` — unauthenticated liveness/DB-connectivity check.
- `POST /auth/register`, `POST /auth/login` — `{ email, password,
  deviceId, deviceName?, platform? }` → `{ accessToken,
  accessTokenExpiresAt, refreshToken, refreshTokenExpiresAt, user }`.
- `POST /auth/refresh` — `{ refreshToken }` → a fresh token pair; rotates
  and invalidates the one it was given.
- `POST /auth/logout` (authenticated) — revokes the calling session.
- `GET /auth/sessions` (authenticated) — the caller's own active sessions.
- `DELETE /auth/sessions/:id` (authenticated) — revoke one of them.
- `GET /user/me` (authenticated) — the caller's own profile.

## Setup

```
cd server
npm install
cp .env.example .env
# edit .env: set DATABASE_URL to your Neon (or local) Postgres connection string
```

## Running migrations

```
npm run migrate
```

Applies every file in `migrations/` that hasn't already been recorded in
the `schema_migrations` table, in filename order, each inside its own
transaction. Safe to re-run — already-applied migrations are skipped.

## Verifying the schema

```
npm run verify-schema
```

Confirms every expected table/index exists, then proves foreign keys,
unique constraints, and `ON DELETE CASCADE` actually behave as designed by
attempting real inserts/deletes that must succeed or fail. Everything runs
inside one transaction that's rolled back at the end, so this is safe to
run against a real (even shared) database — it never leaves data behind.

## Local development database

If you don't want to point at Neon while iterating, any local Postgres 16+
works identically (Neon is standard wire-compatible Postgres) — just point
`DATABASE_URL` at it instead, e.g.:

```
DATABASE_URL=postgresql://<user>:<password>@localhost:5432/<database>
```

## Running the API locally

```
npm run dev
```

Starts the server on `PORT` (default 3000) with auto-restart on file
changes. `npm run build && npm start` runs the compiled, non-watching
version the same way a deployment would.

## Running the automated API tests

Tests need their own disposable database — **never** point them at the
same database as `DATABASE_URL`, since the suite runs `TRUNCATE` between
every test:

```
cp .env.test.example .env.test
# edit .env.test: TEST_DATABASE_URL=postgresql://<user>:<password>@localhost:5432/<a throwaway database>
npm test
```

`npm test` always migrates that test database first (the `pretest`
script), then runs the suite against it. `TEST_DATABASE_URL` is
completely separate from `.env`'s `DATABASE_URL` — the test suite never
reads `DATABASE_URL` at all, so a real database configured there is never
at risk just because it was sitting in `.env` when tests ran. CI runs the
same suite against a throwaway `postgres:16` service container instead of
a local database, and needs no secret to do it.
