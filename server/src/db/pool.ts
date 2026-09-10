import { Pool } from "pg";
import { env } from "../config/env.js";

// Neon requires TLS. `sslmode=verify-full` in DATABASE_URL (see
// .env.example) is what actually gets node-postgres to verify Neon's
// certificate against trusted CAs rather than merely encrypting the
// connection — deliberately spelled out rather than left as `require`,
// since node-postgres's own SSL modes deprecation warning says `require`
// is only a temporary alias for verify-full and will drop to weaker
// libpq-standard semantics in a future major version. Nothing here is
// Neon-specific, so this also works unchanged against the local Postgres
// instance used for Milestone 1's own rehearsal (see
// docs/milestone-0-audit-and-plan.md, "sandbox environment constraints")
// without needing an environment-specific branch in application code.
// Milestone 15: explicit pool sizing/timeouts rather than pg's own
// defaults. The one that actually matters is connectionTimeoutMillis --
// pg's default is 0 (wait forever for a free client), which under real
// saturation (a traffic spike, or Neon's own connection ceiling on a
// pooled/serverless plan) would hang a request indefinitely instead of
// failing fast with a clear error the client's own retry/offline handling
// (Milestone 13) already knows how to recover from. max/idleTimeoutMillis
// are pg's existing defaults, made explicit rather than left implicit —
// this deployment has no measured need to raise or lower them yet.
export const pool = new Pool({
  connectionString: env.databaseUrl,
  max: 10,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 5_000,
});
