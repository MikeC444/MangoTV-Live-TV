import { config as loadDotenv } from "dotenv";

// Runs before any test file (see vitest.config.ts's setupFiles) and
// before anything else in the app has a chance to import config/env.ts —
// which reads DATABASE_URL eagerly at module-load time. Repointing it
// here, to TEST_DATABASE_URL specifically, is what guarantees the test
// suite can never end up running its TRUNCATE-between-tests logic against
// whatever .env's real DATABASE_URL happens to be.
loadDotenv({ path: ".env.test" });

const testUrl = process.env.TEST_DATABASE_URL;
if (!testUrl) {
  throw new Error(
    "TEST_DATABASE_URL is not set. Copy server/.env.test.example to server/.env.test and point it at a disposable database — tests refuse to run without one."
  );
}
process.env.DATABASE_URL = testUrl;

// Not sensitive and doesn't vary per contributor (unlike the database
// URL), so a fixed default here beats asking everyone to add it to their
// own .env.test just to exercise the QR endpoints that build activation
// URLs from it.
process.env.API_BASE_URL ??= "http://localhost:3000";
