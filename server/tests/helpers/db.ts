import { pool } from "../../src/db/pool.js";

/**
 * Wipes every table that hangs off `users` via ON DELETE CASCADE. Postgres
 * follows FK dependencies for TRUNCATE just like it does for DELETE, so
 * truncating `users` alone clears devices, sessions, qr_auth_sessions,
 * user_settings, user_addons (and addon_settings beneath it),
 * watchlist_items, watch_history, and continue_watching in one statement.
 * Call this in a beforeEach so every test starts from an empty database
 * regardless of what earlier tests inserted.
 */
export async function resetDatabase(): Promise<void> {
  await pool.query("TRUNCATE TABLE users CASCADE");
}
