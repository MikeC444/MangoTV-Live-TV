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

// JWT_SECRET/QR_AUTH_SECRET/API_BASE_URL are documented in .env.example
// but intentionally not validated here yet. This server's session tokens
// are opaque, random, and validated against the sessions table (see
// migrations/0004_sessions.sql's header comment for why) rather than
// JWTs, so JWT_SECRET may end up serving a narrower purpose than its name
// suggests — or none — once Milestone 3 actually implements login and
// either finds it a real job or drops it. Not guessed at here.
export const env = {
  databaseUrl: required("DATABASE_URL"),
  nodeEnv: process.env.NODE_ENV ?? "development",
  port: parsePort(process.env.PORT),
};
