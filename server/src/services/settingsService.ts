import { pool } from "../db/pool.js";
import type { SettingsInput } from "../schemas/settings.js";

export interface UserSettings {
  homeRowOrder: string[];
  hiddenRowIds: string[];
  autoplayNextEpisode: boolean;
  skipIntroEnabled: boolean;
  /** null only for an account that has never pushed settings from any device. */
  updatedAt: Date | null;
}

const DEFAULT_SETTINGS: UserSettings = {
  homeRowOrder: [],
  hiddenRowIds: [],
  autoplayNextEpisode: true,
  skipIntroEnabled: true,
  updatedAt: null,
};

interface SettingsRow {
  home_row_order: string[];
  hidden_row_ids: string[];
  autoplay_next_episode: boolean;
  skip_intro_enabled: boolean;
  updated_at: Date;
}

function mapRow(row: SettingsRow): UserSettings {
  return {
    homeRowOrder: row.home_row_order,
    hiddenRowIds: row.hidden_row_ids,
    autoplayNextEpisode: row.autoplay_next_episode,
    skipIntroEnabled: row.skip_intro_enabled,
    updatedAt: row.updated_at,
  };
}

/** No row yet (brand new account, never synced from any device) reads as the same defaults the column definitions themselves use, not an error. */
export async function getUserSettings(userId: string): Promise<UserSettings> {
  const result = await pool.query<SettingsRow>(
    `SELECT home_row_order, hidden_row_ids, autoplay_next_episode, skip_intro_enabled, updated_at
     FROM user_settings WHERE user_id = $1`,
    [userId]
  );
  const row = result.rows[0];
  return row ? mapRow(row) : DEFAULT_SETTINGS;
}

/**
 * Upserts settings using last-write-wins keyed on the client-supplied
 * updatedAt (when that device made the change locally, not when this
 * request happened to reach the server) -- comparing by receive order
 * instead would let a device that was offline for a while overwrite a
 * genuinely newer change from elsewhere just by reconnecting later.
 *
 * The comparison and the write happen in one atomic statement (the same
 * "UPDATE ... WHERE ... RETURNING" shape used throughout this codebase's
 * auth/QR flows) rather than a separate read-compare-then-write, so two
 * concurrent pushes from different devices can't race each other into an
 * inconsistent result.
 *
 * Always returns the row's current authoritative state afterward -- this
 * write's own values if it won the race, or whatever was already there if
 * it lost -- so a caller can simply persist the response as its new local
 * cache either way, without needing to know which one happened.
 */
export async function upsertUserSettings(userId: string, input: SettingsInput): Promise<UserSettings> {
  const result = await pool.query<SettingsRow>(
    `INSERT INTO user_settings (user_id, home_row_order, hidden_row_ids, autoplay_next_episode, skip_intro_enabled, updated_at)
     VALUES ($1, $2::jsonb, $3::jsonb, $4, $5, $6)
     ON CONFLICT (user_id) DO UPDATE SET
       home_row_order = EXCLUDED.home_row_order,
       hidden_row_ids = EXCLUDED.hidden_row_ids,
       autoplay_next_episode = EXCLUDED.autoplay_next_episode,
       skip_intro_enabled = EXCLUDED.skip_intro_enabled,
       updated_at = EXCLUDED.updated_at
     WHERE EXCLUDED.updated_at > user_settings.updated_at
     RETURNING home_row_order, hidden_row_ids, autoplay_next_episode, skip_intro_enabled, updated_at`,
    [
      userId,
      JSON.stringify(input.homeRowOrder),
      JSON.stringify(input.hiddenRowIds),
      input.autoplayNextEpisode,
      input.skipIntroEnabled,
      new Date(input.updatedAt),
    ]
  );

  if (result.rows.length > 0) return mapRow(result.rows[0]!);
  // The incoming write lost the race (not newer than what's already
  // stored) -- the INSERT/DO UPDATE above is a no-op in that case, so
  // there's nothing to map; fetch and return the still-current row.
  return getUserSettings(userId);
}
