# Deployment

How to stand up a real instance of the backend against a real Neon
database, and wire the Fire TV app to it. See
[Architecture](ARCHITECTURE.md) for how the pieces fit together and
[Testing](TESTING.md) for how to verify a deployment once it's live.

## 1. Neon setup

1. Create a Neon project (any region). Neon is standard wire-compatible
   Postgres — nothing in this codebase is Neon-specific beyond the
   connection string itself.
2. From the Neon console, grab **two** connection strings:
   - The **pooled** connection string — use this for `DATABASE_URL` in
     the running server's environment (day-to-day request traffic).
   - The **direct/unpooled** connection string — use this specifically
     when *running migrations* (`npm run migrate`), so DDL runs on a
     single stable session rather than through a connection pooler that
     could route statements within one migration to different backend
     connections.
3. Append `?sslmode=verify-full` to whichever string you're using (see
   `server/.env.example`'s own comment for why `verify-full` is spelled
   out explicitly rather than left as `require` — `pg`'s own deprecation
   notice says `require` is only a temporary alias for it and a future
   major version will drop that alias to weaker semantics). Example
   shape:
   ```
   postgresql://<user>:<password>@<host>/<database>?sslmode=verify-full
   ```
4. Run migrations once against the fresh database:
   ```
   cd server
   DATABASE_URL="<direct connection string>" npm run migrate
   ```
   Then verify:
   ```
   DATABASE_URL="<direct connection string>" npm run verify-schema
   ```
   `verify-schema` confirms every table/index exists and proves foreign
   keys, unique constraints, and cascade deletes actually behave as
   designed — real inserts/deletes inside one transaction that's always
   rolled back, so it's safe to run again later against the same live
   database.
5. **Optional but recommended**: add `DATABASE_URL` (the direct
   connection string) as a GitHub repository secret named exactly
   `DATABASE_URL`. `.github/workflows/server-ci.yml`'s `db` job already
   runs `migrate` + `verify-schema` against it automatically on every
   push touching `server/**`, and skips cleanly (not a failure) when the
   secret isn't configured — so this is zero-effort continuous schema
   verification against your real database the moment the secret exists.

## 2. Backend deployment

The server (`server/`) is a plain Node.js/Express process — any platform
that runs a long-lived Node process works (Render, Railway, Fly, a plain
VM, etc.). It already handles what all of those expect:

- **Graceful shutdown**: `src/index.ts` catches `SIGTERM`/`SIGINT`, stops
  accepting new connections, lets in-flight requests finish, then closes
  the database pool before exiting — needed for a clean redeploy on any
  platform that sends `SIGTERM` on restart (all of the above do).
- **Build/start**: `npm run build` (plain `tsc`) then `npm start` (`node
  dist/index.js`) — no bundler, no framework-specific build step.
- **Health check**: `GET /health` is unauthenticated and checks DB
  connectivity — point your platform's health-check probe at it.

Minimal deployment steps:

```
cd server
npm ci
npm run build
# set environment variables (see §3) in the platform's dashboard/CLI
npm start
```

Run `npm run migrate` (with the *direct* Neon connection string, per §1)
as a one-off step before the first deploy, and again after pulling any
change that adds a new file under `server/migrations/`.

**`trust proxy` is set to trust exactly one hop** (`app.set("trust proxy",
1)` in `src/app.ts`) — correct for a single reverse proxy in front, which
is true of every platform named above. If a future deployment adds a
*second* hop in front of that (e.g. a separate CDN in front of the
platform's own proxy), revisit this deliberately rather than leaving it
as an unexamined default — over-trusting `X-Forwarded-For` through an
extra hop makes the per-IP rate limiter spoofable.

## 3. Environment variables

From `server/.env.example` — copy it to `server/.env` for local use, or
set these directly in your platform's environment configuration for a
real deployment. **Never commit `.env`** (already covered by
`server/.gitignore`).

| Variable | Required | Meaning |
|---|---|---|
| `DATABASE_URL` | Yes | Neon (or any Postgres 16+) connection string. Pooled for the running server; direct/unpooled specifically for `npm run migrate` (see §1). Must include `sslmode=verify-full` against Neon. |
| `API_BASE_URL` | Yes (for anything that builds a QR activation URL) | The public HTTPS URL this API is actually reachable at — used to build `<API_BASE_URL>/activate?token=...`. Local dev: `http://localhost:3000`. Real deployment: `https://api.yourdomain.com`. Read lazily (only by the QR routes), so running a migration never needs it set. |
| `NODE_ENV` | No (defaults to `development`) | `production` suppresses the extra `detail` field `errorHandler` otherwise includes on unexpected 500s for local debugging — never leak internals in production. |
| `PORT` | No (defaults to `3000`) | Port the HTTP server listens on. |

The **Android app** reads its own, separate configuration —
`API_BASE_URL` in `local.properties` (gitignored, developer/deployment-
specific, the same name deliberately, since it's the same logical value:
wherever the backend from §2 is actually reachable):

```
# app/local.properties (not committed)
API_BASE_URL=https://api.yourdomain.com
```

This becomes `BuildConfig.API_BASE_URL` at build time. `app/build.gradle.kts`
fails the build outright if this doesn't start with `https://` (Milestone
15) — there's no way to produce an APK that would send its bearer tokens
over plaintext HTTP because of a misconfigured value here. Leaving it
unset doesn't fail the build; it falls back to the deliberately-invalid
`https://not-configured.invalid`, so a forgotten config fails loudly at
runtime (DNS/connection error) instead of the app silently talking to
nothing in particular.

## 4. Domain configuration

Pick the public hostname the backend will live at (e.g.
`api.yourdomain.com`) before generating any build:

1. Point that hostname at your deployment platform, per that platform's
   own domain-configuration instructions.
2. Set `API_BASE_URL` (server-side, §3) to `https://<that hostname>`.
3. Set the **same** value in the Android app's `local.properties` before
   building the APK that will actually ship.
4. If either side is later repointed at a different domain, both need
   updating together — a mismatch means the app builds QR codes/activation
   URLs correctly server-side but talks to a different (or no) host for
   every other request, or vice versa.

## 5. HTTPS

This backend does **not** terminate TLS itself — no certificate
configuration exists anywhere in `server/`. It expects to sit behind a
platform that terminates HTTPS in front of it (every platform named in
§2 does this automatically for a custom domain) and forwards plain HTTP
internally, which is what `trust proxy` (§2) is configured for.

HTTPS here isn't optional hardening — it's load-bearing: every
authenticated request carries a bearer token in the `Authorization`
header, and `app/build.gradle.kts`'s build-time check (§3) exists
specifically because nothing else in the system stops that token from
leaving the device in the clear if the endpoint it's sent to isn't
actually HTTPS.

## 6. The QR activation URL

`GET /auth/qr/create`'s response includes `activationUrl`, built
server-side (`src/routes/qr.ts`'s `activationUrl()`) as
`<API_BASE_URL, trailing slash stripped>/activate?token=<the QR session's own token>`.
The Fire TV app renders this URL as a QR code (`QrCodeGenerator.kt` +
`QrCodeImage.kt`) — nothing about the activation URL's shape needs
configuring separately from `API_BASE_URL` itself (§3/§4). The page it
points at (`GET /activate`) is served by this same backend
(`server/public/activate.html` + `activate.js`), so once `API_BASE_URL`
is correct, scanning a real QR code on a real device takes you straight
to a working activation page with no separate frontend deployment or CORS
configuration needed — its own `fetch()` calls to `/auth/qr/*` are
same-origin by construction.
