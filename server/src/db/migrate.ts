import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { pool } from "./pool.js";

// Deliberately hand-written rather than a migration-framework dependency:
// the full behavior fits in one readable file, and every step here is
// something Milestone 1's own completion criteria need to be able to
// point at directly ("migrations work", "no destructive surprises").
//
// Each .sql file is applied at most once, in filename order, inside its
// own transaction (Postgres DDL is transactional, so a failing migration
// rolls back cleanly instead of leaving the schema half-applied), and
// recorded in schema_migrations so re-running this script is a no-op for
// anything already applied.

const migrationsDir = path.join(path.dirname(fileURLToPath(import.meta.url)), "..", "..", "migrations");

async function ensureMigrationsTable(): Promise<void> {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS schema_migrations (
      id text PRIMARY KEY,
      applied_at timestamptz NOT NULL DEFAULT now()
    )
  `);
}

async function appliedMigrations(): Promise<Set<string>> {
  const result = await pool.query<{ id: string }>("SELECT id FROM schema_migrations");
  return new Set(result.rows.map((row) => row.id));
}

export async function runMigrations(): Promise<{ applied: string[] }> {
  await ensureMigrationsTable();
  const already = await appliedMigrations();

  const files = (await readdir(migrationsDir)).filter((file) => file.endsWith(".sql")).sort();
  const applied: string[] = [];

  for (const file of files) {
    if (already.has(file)) continue;

    const sql = await readFile(path.join(migrationsDir, file), "utf8");
    const client = await pool.connect();
    try {
      await client.query("BEGIN");
      await client.query(sql);
      await client.query("INSERT INTO schema_migrations (id) VALUES ($1)", [file]);
      await client.query("COMMIT");
      applied.push(file);
      console.log(`applied ${file}`);
    } catch (error) {
      await client.query("ROLLBACK");
      throw new Error(`migration ${file} failed, rolled back: ${(error as Error).message}`, { cause: error });
    } finally {
      client.release();
    }
  }

  return { applied };
}

const isMainModule = process.argv[1] === fileURLToPath(import.meta.url);
if (isMainModule) {
  runMigrations()
    .then(({ applied }) => {
      console.log(applied.length > 0 ? `${applied.length} migration(s) applied.` : "Already up to date.");
      return pool.end();
    })
    .catch((error) => {
      console.error(error);
      return pool.end().finally(() => process.exit(1));
    });
}
