import { pool } from "../db/pool.js";
import type { AddonDeleteInput, AddonInput } from "../schemas/addons.js";

export interface UserAddon {
  manifestUrl: string;
  addonId: string;
  name: string;
  manifestJson: Record<string, unknown>;
  enabled: boolean;
  sortOrder: number;
  updatedAt: Date;
  /** Non-null means this addon is currently removed -- only meaningful on POST/DELETE responses, same contract as watchlist's deletedAt. listActiveAddons never returns a row with this set. */
  deletedAt: Date | null;
}

interface AddonRow {
  manifest_url: string;
  addon_id: string;
  name: string;
  manifest_json: Record<string, unknown>;
  enabled: boolean;
  sort_order: number;
  updated_at: Date;
  deleted_at: Date | null;
}

const ROW_COLUMNS = `manifest_url, addon_id, name, manifest_json, enabled, sort_order, updated_at, deleted_at`;

function mapRow(row: AddonRow): UserAddon {
  return {
    manifestUrl: row.manifest_url,
    addonId: row.addon_id,
    name: row.name,
    manifestJson: row.manifest_json,
    enabled: row.enabled,
    sortOrder: row.sort_order,
    updatedAt: row.updated_at,
    deletedAt: row.deleted_at,
  };
}

/** This account's currently-installed addons, in display order -- what a fresh sign-in or app launch pulls down to seed/replace ProviderRegistry. Never includes soft-deleted rows. */
export async function listActiveAddons(userId: string): Promise<UserAddon[]> {
  const result = await pool.query<AddonRow>(
    `SELECT ${ROW_COLUMNS} FROM user_addons
     WHERE user_id = $1 AND deleted_at IS NULL
     ORDER BY sort_order ASC`,
    [userId]
  );
  return result.rows.map(mapRow);
}

/**
 * Installs (or un-removes/updates) one addon, keyed on the same
 * (user_id, manifest_url) natural key the table's unique constraint
 * enforces. Last-write-wins on the client's own updatedAt, in one atomic
 * statement -- the same shape as watchlistService.upsertWatchlistItem,
 * applied to a single-column natural key instead of a three-column one.
 * installed_at is deliberately left untouched by the DO UPDATE (only set
 * by the initial INSERT's DEFAULT now()) -- removing and re-adding the
 * same addon is a metadata update to one durable slot, not a fresh
 * install.
 */
export async function upsertAddon(userId: string, input: AddonInput): Promise<UserAddon> {
  const result = await pool.query<AddonRow>(
    `INSERT INTO user_addons (user_id, manifest_url, addon_id, name, manifest_json, enabled, sort_order, updated_at, deleted_at)
     VALUES ($1, $2, $3, $4, $5::jsonb, $6, $7, $8, NULL)
     ON CONFLICT (user_id, manifest_url) DO UPDATE SET
       addon_id = EXCLUDED.addon_id,
       name = EXCLUDED.name,
       manifest_json = EXCLUDED.manifest_json,
       enabled = EXCLUDED.enabled,
       sort_order = EXCLUDED.sort_order,
       updated_at = EXCLUDED.updated_at,
       deleted_at = NULL
     WHERE EXCLUDED.updated_at > user_addons.updated_at
     RETURNING ${ROW_COLUMNS}`,
    [
      userId,
      input.manifestUrl,
      input.addonId,
      input.name,
      JSON.stringify(input.manifestJson),
      input.enabled,
      input.sortOrder,
      new Date(input.updatedAt),
    ]
  );

  if (result.rows.length > 0) return mapRow(result.rows[0]!);
  // Lost the race (not newer than what's already stored) -- fetch and
  // return the still-current row instead.
  return getAddon(userId, input.manifestUrl) as Promise<UserAddon>;
}

/**
 * Soft-deletes one addon via the same atomic, LWW-gated shape as
 * upsertAddon. Returns null only when the addon never existed for this
 * user at all (e.g. a pre-Milestone-9 local-only install this device
 * never pushed) -- nothing server-side to reconcile in that case. When a
 * row does exist, always returns its current state, whether this call
 * actually removed it or lost the race to a newer write elsewhere.
 */
export async function removeAddon(userId: string, input: AddonDeleteInput): Promise<UserAddon | null> {
  const result = await pool.query<AddonRow>(
    `UPDATE user_addons
     SET deleted_at = $3, updated_at = $3
     WHERE user_id = $1 AND manifest_url = $2
       AND updated_at < $3
     RETURNING ${ROW_COLUMNS}`,
    [userId, input.manifestUrl, new Date(input.updatedAt)]
  );

  if (result.rows.length > 0) return mapRow(result.rows[0]!);
  return getAddon(userId, input.manifestUrl);
}

async function getAddon(userId: string, manifestUrl: string): Promise<UserAddon | null> {
  const result = await pool.query<AddonRow>(
    `SELECT ${ROW_COLUMNS} FROM user_addons WHERE user_id = $1 AND manifest_url = $2`,
    [userId, manifestUrl]
  );
  return result.rows[0] ? mapRow(result.rows[0]) : null;
}
