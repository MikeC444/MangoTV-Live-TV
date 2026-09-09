# MangoTV server

Backend API sitting between the Fire TV app and Neon Postgres. The Fire TV
app never receives database credentials or talks to Postgres directly —
see `docs/milestone-0-audit-and-plan.md` at the repo root for the full
architecture writeup.

Milestone 1 scope: database schema + migrations only. The HTTP API itself
starts in Milestone 2.

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
