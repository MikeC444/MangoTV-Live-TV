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
export const pool = new Pool({
  connectionString: env.databaseUrl,
});
