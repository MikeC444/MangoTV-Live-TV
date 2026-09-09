import { config as loadDotenv } from "dotenv";

// Runs as `pretest` (see package.json) so `npm test` always migrates the
// test database first. Static imports of anything under src/ are
// deliberately avoided at the top of this file — env.ts reads
// DATABASE_URL eagerly at import time, so TEST_DATABASE_URL has to be
// copied into it *before* that first import happens, via the dynamic
// import below.
loadDotenv({ path: ".env.test" });

const testUrl = process.env.TEST_DATABASE_URL;
if (!testUrl) {
  throw new Error(
    "TEST_DATABASE_URL is not set. Copy server/.env.test.example to server/.env.test and point it at a disposable database."
  );
}
process.env.DATABASE_URL = testUrl;

const { runMigrations } = await import("../src/db/migrate.js");
const { pool } = await import("../src/db/pool.js");

try {
  const { applied } = await runMigrations();
  console.log(applied.length > 0 ? `${applied.length} migration(s) applied to the test database.` : "Test database already up to date.");
} finally {
  await pool.end();
}
