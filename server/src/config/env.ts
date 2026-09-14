import { config as loadDotenv } from "dotenv";

loadDotenv();

function required(name: string): string {
  const value = process.env[name];
  if (!value || value.trim() === "") {
    throw new Error(
      `Missing required environment variable ${name}. Copy server/.env.example to server/.env and fill it in.`
    );
  }
  return value;
}

function parsePort(raw: string | undefined): number {
  const port = Number(raw ?? 3000);
  if (!Number.isInteger(port) || port <= 0 || port > 65535) {
    throw new Error(`Invalid PORT: ${raw}`);
  }
  return port;
}

export const env = {
  databaseUrl: required("DATABASE_URL"),
  nodeEnv: process.env.NODE_ENV ?? "development",
  port: parsePort(process.env.PORT),
};

// A function, not a plain property on `env`: `env` is imported by
// db/pool.ts and therefore by scripts (migrate.ts, verify-schema.ts)
// that have nothing to do with QR auth and shouldn't need API_BASE_URL
// set just to run a migration. Evaluating this lazily, only when
// something actually builds an activation URL, keeps that requirement
// scoped to the code that needs it.
export function getApiBaseUrl(): string {
  return required("API_BASE_URL");
}

// Deliberately optional, unlike every required() value above: trailers
// are a nice-to-have on top of the core app, not something that should
// stop the whole server from booting in an environment where nobody's
// gotten around to configuring a TMDB key yet. trailerService.ts reads
// this per-lookup (not once at startup) and treats null the same as "no
// trailer found" -- the Detail screen just doesn't show a Trailer button.
export function getTmdbReadAccessToken(): string | null {
  const value = process.env.TMDB_READ_ACCESS_TOKEN;
  return value && value.trim() !== "" ? value : null;
}

// Also optional, same reasoning as TMDB above: Trakt account linking is an
// add-on feature, not something that should stop the server from booting
// just because nobody's registered a Trakt application yet. traktService.ts
// checks this per-request and returns a 503 ("Trakt integration is not
// configured") rather than ever throwing at startup. The two are read
// together, not as separate optionals -- a client id with no matching
// secret (or vice versa) is a misconfiguration, not "half enabled."
export function getTraktCredentials(): { clientId: string; clientSecret: string } | null {
  const clientId = process.env.TRAKT_CLIENT_ID;
  const clientSecret = process.env.TRAKT_CLIENT_SECRET;
  if (!clientId || !clientSecret || clientId.trim() === "" || clientSecret.trim() === "") return null;
  return { clientId, clientSecret };
}

// Required only by the code paths that actually encrypt/decrypt a stored
// third-party token (currently just traktService.ts) -- read lazily there,
// same as getApiBaseUrl(), so booting the server or running a migration
// never needs this set. Unlike hashToken() (security/tokens.ts, one-way,
// used for this app's own bearer tokens), a Trakt access/refresh token has
// to be recoverable in plaintext to ever be sent back to Trakt's API, so it
// can't just be hashed -- this key is what keeps it unreadable in a raw
// database dump anyway. Generate one with `openssl rand -base64 32`.
export function getTokenEncryptionKey(): Buffer {
  const raw = required("TOKEN_ENCRYPTION_KEY");
  const key = Buffer.from(raw, "base64");
  if (key.length !== 32) {
    throw new Error(
      "TOKEN_ENCRYPTION_KEY must decode (as base64) to exactly 32 bytes. Generate one with: openssl rand -base64 32"
    );
  }
  return key;
}
