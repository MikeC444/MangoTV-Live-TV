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
