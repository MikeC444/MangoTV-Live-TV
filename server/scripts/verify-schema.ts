import type { PoolClient } from "pg";
import { pool } from "../src/db/pool.js";

// Exercises the migrated schema for real instead of only eyeballing the
// SQL: confirms every expected table/index exists, then proves foreign
// keys, unique constraints, and cascade deletes actually behave as
// designed by attempting operations that must succeed and operations that
// must fail. Everything runs inside one transaction that is always rolled
// back at the end, so this is safe to run repeatedly against a real
// database (including a shared dev/Neon instance) without leaving any
// residue behind.

const EXPECTED_TABLES = [
  "users",
  "devices",
  "sessions",
  "qr_auth_sessions",
  "user_settings",
  "user_addons",
  "addon_settings",
  "watchlist_items",
  "watch_history",
  "continue_watching",
  "schema_migrations",
];

const EXPECTED_INDEXES = [
  "users_email_key",
  "devices_user_id_idx",
  "devices_user_identifier_key",
  "sessions_user_id_idx",
  "sessions_device_id_idx",
  "sessions_access_token_hash_key",
  "sessions_refresh_token_hash_key",
  "qr_auth_sessions_device_identifier_idx",
  "qr_auth_sessions_session_id_idx",
  "qr_auth_sessions_status_expires_idx",
  "qr_auth_sessions_token_hash_key",
  "user_addons_user_id_idx",
  "user_addons_user_manifest_key",
  "watchlist_items_user_id_idx",
  "watchlist_items_active_idx",
  "watchlist_items_user_content_key",
  "watch_history_user_id_idx",
  "watch_history_user_watched_at_idx",
  "watch_history_user_episode_key",
  "continue_watching_user_id_idx",
  "continue_watching_active_idx",
  "continue_watching_user_content_key",
];

let passed = 0;
let failed = 0;

function pass(label: string): void {
  passed += 1;
  console.log(`  ok   ${label}`);
}

function fail(label: string, detail: string): void {
  failed += 1;
  console.error(`  FAIL ${label} — ${detail}`);
}

async function checkTablesExist(client: PoolClient): Promise<void> {
  const { rows } = await client.query<{ table_name: string }>(
    "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'"
  );
  const present = new Set(rows.map((r) => r.table_name));
  for (const table of EXPECTED_TABLES) {
    if (present.has(table)) pass(`table ${table} exists`);
    else fail(`table ${table} exists`, "not found in information_schema.tables");
  }
}

async function checkIndexesExist(client: PoolClient): Promise<void> {
  const { rows } = await client.query<{ indexname: string }>(
    "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'"
  );
  const present = new Set(rows.map((r) => r.indexname));
  for (const index of EXPECTED_INDEXES) {
    if (present.has(index)) pass(`index ${index} exists`);
    else fail(`index ${index} exists`, "not found in pg_indexes");
  }
}

/** Runs `fn` in a SAVEPOINT and asserts it throws a Postgres error with `expectedCode`. Always rolls back to the savepoint. */
async function expectError(client: PoolClient, label: string, expectedCode: string, fn: () => Promise<unknown>): Promise<void> {
  await client.query("SAVEPOINT check_point");
  try {
    await fn();
    fail(label, `expected Postgres error ${expectedCode} but the statement succeeded`);
  } catch (error) {
    const code = (error as { code?: string }).code;
    if (code === expectedCode) pass(label);
    else fail(label, `expected error ${expectedCode}, got ${code ?? (error as Error).message}`);
  } finally {
    await client.query("ROLLBACK TO SAVEPOINT check_point");
  }
}

async function expectSuccess(client: PoolClient, label: string, fn: () => Promise<unknown>): Promise<void> {
  try {
    await fn();
    pass(label);
  } catch (error) {
    fail(label, (error as Error).message);
  }
}

async function checkConstraintsAndCascades(client: PoolClient): Promise<void> {
  const userId = "00000000-0000-4000-8000-000000000001";
  const deviceId = "00000000-0000-4000-8000-000000000002";
  const deviceIdentifier = "00000000-0000-4000-8000-000000000003";

  await expectSuccess(client, "insert a user", () =>
    client.query(
      "INSERT INTO users (id, email, password_hash) VALUES ($1, 'schema-check@example.com', 'not-a-real-hash')",
      [userId]
    )
  );

  await expectError(client, "FK rejects device with non-existent user_id", "23503", () =>
    client.query(
      "INSERT INTO devices (user_id, device_identifier) VALUES ('00000000-0000-4000-8000-0000000000ff', $1)",
      [deviceIdentifier]
    )
  );

  await expectSuccess(client, "insert a device for that user", () =>
    client.query("INSERT INTO devices (id, user_id, device_identifier) VALUES ($1, $2, $3)", [
      deviceId,
      userId,
      deviceIdentifier,
    ])
  );

  await expectError(client, "unique constraint rejects duplicate (user_id, device_identifier)", "23505", () =>
    client.query("INSERT INTO devices (user_id, device_identifier) VALUES ($1, $2)", [userId, deviceIdentifier])
  );

  await expectSuccess(client, "insert a session for that user+device", () =>
    client.query(
      `INSERT INTO sessions (user_id, device_id, access_token_hash, access_token_expires_at, refresh_token_hash, refresh_token_expires_at)
       VALUES ($1, $2, 'access-hash', now() + interval '1 hour', 'refresh-hash', now() + interval '30 days')`,
      [userId, deviceId]
    )
  );

  await expectSuccess(client, "insert user_settings", () =>
    client.query("INSERT INTO user_settings (user_id) VALUES ($1)", [userId])
  );

  await expectSuccess(client, "insert a watchlist item", () =>
    client.query(
      `INSERT INTO watchlist_items (user_id, provider_id, content_id, content_type, title)
       VALUES ($1, 'cinemeta', 'tt1234567', 'MOVIE', 'Test Movie')`,
      [userId]
    )
  );

  await expectError(client, "unique constraint rejects duplicate watchlist item", "23505", () =>
    client.query(
      `INSERT INTO watchlist_items (user_id, provider_id, content_id, content_type, title)
       VALUES ($1, 'cinemeta', 'tt1234567', 'MOVIE', 'Test Movie Again')`,
      [userId]
    )
  );

  await expectSuccess(client, "insert two distinct episodes into watch_history", () =>
    client.query(
      `INSERT INTO watch_history (user_id, provider_id, content_id, content_type, season_number, episode_number, title, position_ms, duration_ms)
       VALUES
        ($1, 'cinemeta', 'tt7654321', 'TV_SHOW', 1, 1, 'Test Show', 100, 1000),
        ($1, 'cinemeta', 'tt7654321', 'TV_SHOW', 1, 2, 'Test Show', 200, 1000)`,
      [userId]
    )
  );

  await expectSuccess(client, "insert a movie (null season/episode) into watch_history", () =>
    client.query(
      `INSERT INTO watch_history (user_id, provider_id, content_id, content_type, title, position_ms, duration_ms)
       VALUES ($1, 'cinemeta', 'tt1234567', 'MOVIE', 'Test Movie', 500, 1000)`,
      [userId]
    )
  );

  await expectError(
    client,
    "generated episode_key makes a second null/null movie row collide (proves the NULL-uniqueness workaround works)",
    "23505",
    () =>
      client.query(
        `INSERT INTO watch_history (user_id, provider_id, content_id, content_type, title, position_ms, duration_ms)
         VALUES ($1, 'cinemeta', 'tt1234567', 'MOVIE', 'Test Movie', 999, 1000)`,
        [userId]
      )
  );

  await expectSuccess(client, "insert a user_addon", () =>
    client.query(
      `INSERT INTO user_addons (id, user_id, manifest_url, addon_id, name, manifest_json)
       VALUES ('00000000-0000-4000-8000-000000000004', $1, 'https://example.com/manifest.json', 'example.addon', 'Example Addon', '{}'::jsonb)`,
      [userId]
    )
  );

  await expectSuccess(client, "insert addon_settings for that addon", () =>
    client.query(
      "INSERT INTO addon_settings (user_addon_id, config) VALUES ('00000000-0000-4000-8000-000000000004', '{}'::jsonb)"
    )
  );

  await expectSuccess(client, "insert continue_watching row", () =>
    client.query(
      `INSERT INTO continue_watching (user_id, provider_id, content_id, content_type, season_number, episode_number, title, position_ms, duration_ms)
       VALUES ($1, 'cinemeta', 'tt7654321', 'TV_SHOW', 1, 2, 'Test Show', 200, 1000)`,
      [userId]
    )
  );

  await expectSuccess(client, "delete the user", () => client.query("DELETE FROM users WHERE id = $1", [userId]));

  const dependentCounts = await client.query<{ table_name: string; remaining: string }>(
    `SELECT 'devices' AS table_name, count(*)::text AS remaining FROM devices WHERE user_id = $1
     UNION ALL SELECT 'sessions', count(*)::text FROM sessions WHERE user_id = $1
     UNION ALL SELECT 'user_settings', count(*)::text FROM user_settings WHERE user_id = $1
     UNION ALL SELECT 'user_addons', count(*)::text FROM user_addons WHERE user_id = $1
     UNION ALL SELECT 'addon_settings', count(*)::text FROM addon_settings WHERE user_addon_id = '00000000-0000-4000-8000-000000000004'
     UNION ALL SELECT 'watchlist_items', count(*)::text FROM watchlist_items WHERE user_id = $1
     UNION ALL SELECT 'watch_history', count(*)::text FROM watch_history WHERE user_id = $1
     UNION ALL SELECT 'continue_watching', count(*)::text FROM continue_watching WHERE user_id = $1`,
    [userId]
  );
  for (const row of dependentCounts.rows) {
    if (row.remaining === "0") pass(`ON DELETE CASCADE removed all ${row.table_name} rows for the deleted user`);
    else fail(`ON DELETE CASCADE removed all ${row.table_name} rows for the deleted user`, `${row.remaining} row(s) still present`);
  }
}

async function main(): Promise<void> {
  const client = await pool.connect();
  try {
    console.log("Checking tables...");
    await checkTablesExist(client);
    console.log("Checking indexes...");
    await checkIndexesExist(client);

    console.log("Exercising constraints (transaction will be rolled back, no data is kept)...");
    await client.query("BEGIN");
    try {
      await checkConstraintsAndCascades(client);
    } finally {
      await client.query("ROLLBACK");
    }
  } finally {
    client.release();
  }

  console.log(`\n${passed} passed, ${failed} failed.`);
  await pool.end();
  if (failed > 0) process.exit(1);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
