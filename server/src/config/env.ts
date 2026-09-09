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

// Only DATABASE_URL is read by anything in this milestone (the migration
// runner and schema verification script). JWT_SECRET/QR_AUTH_SECRET/
// API_BASE_URL are documented in .env.example but intentionally not
// validated here yet — Milestones 2-4 add them to this object once the
// HTTP server and auth flows exist to actually consume them.
export const env = {
  databaseUrl: required("DATABASE_URL"),
  nodeEnv: process.env.NODE_ENV ?? "development",
};
