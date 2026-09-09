import { Pool } from "pg";
import { env } from "../config/env.js";

// Neon requires TLS. `sslmode=require` in the connection string is enough
// for node-postgres to negotiate TLS; we don't pin/verify Neon's CA here
// (rejectUnauthorized stays at pg's default of true) so this also works
// unchanged against the local Postgres instance used for Milestone 1's
// own rehearsal (see docs/milestone-0-audit-and-plan.md, "sandbox
// environment constraints") without needing an environment-specific
// branch in application code.
export const pool = new Pool({
  connectionString: env.databaseUrl,
});
