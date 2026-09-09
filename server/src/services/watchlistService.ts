import { pool } from "../db/pool.js";
import type { WatchlistDeleteInput, WatchlistItemInput } from "../schemas/watchlist.js";

export interface WatchlistItem {
  providerId: string;
  contentId: string;
  contentType: string;
  title: string;
  posterUrl: string | null;
  backdropUrl: string | null;
  year: number | null;
  rating: number | null;
  updatedAt: Date;
  /** Non-null means this slot is currently removed -- present so a client can tell "still active" apart from "was removed since I last knew about it" on the same response shape POST and DELETE both return. */
  deletedAt: Date | null;
}

interface WatchlistRow {
  provider_id: string;
  content_id: string;
  content_type: string;
  title: string;
  poster_url: string | null;
  backdrop_url: string | null;
  year: number | null;
  rating: number | null;
  updated_at: Date;
  deleted_at: Date | null;
}

function mapRow(row: WatchlistRow): WatchlistItem {
  return {
    providerId: row.provider_id,
    contentId: row.content_id,
    contentType: row.content_type,
    title: row.title,
    posterUrl: row.poster_url,
    backdropUrl: row.backdrop_url,
    year: row.year,
    rating: row.rating,
    updatedAt: row.updated_at,
    deletedAt: row.deleted_at,
  };
}

const ROW_COLUMNS = `provider_id, content_id, content_type, title, poster_url, backdrop_url, year, rating, updated_at, deleted_at`;

/** This account's currently-active watchlist -- what a fresh sign-in or app launch pulls down to seed/replace the local cache. Never includes soft-deleted rows. */
export async function listActiveWatchlist(userId: string): Promise<WatchlistItem[]> {
  const result = await pool.query<WatchlistRow>(
    `SELECT ${ROW_COLUMNS} FROM watchlist_items
     WHERE user_id = $1 AND deleted_at IS NULL
     ORDER BY added_at ASC`,
    [userId]
  );
  return result.rows.map(mapRow);
}

/**
 * Adds (or "un-removes") one item, keyed on the same (user_id, provider_id,
 * content_id, content_type) natural key the table's unique constraint
 * enforces. Last-write-wins on the client's own updatedAt, in one atomic
 * statement, exactly like settingsService.upsertUserSettings -- two
 * concurrent pushes from different devices can't race each other into an
 * inconsistent result. added_at is deliberately left untouched by the
 * DO UPDATE (only set by the initial INSERT's DEFAULT now()): removing and
 * re-adding the same title is a metadata update to one durable slot, not a
 * fresh add, per migration 0009's own header comment.
 *
 * Always returns the row's current authoritative state afterward -- this
 * write's own values if it won the race, or whatever is already there
 * (including a possibly-still-deleted state, if a later remove elsewhere
 * beat this add) if it lost -- so the caller can reconcile its local cache
 * from the response either way without needing to know which happened.
 */
export async function upsertWatchlistItem(userId: string, input: WatchlistItemInput): Promise<WatchlistItem> {
  const result = await pool.query<WatchlistRow>(
    `INSERT INTO watchlist_items (user_id, provider_id, content_id, content_type, title, poster_url, backdrop_url, year, rating, updated_at, deleted_at)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, NULL)
     ON CONFLICT (user_id, provider_id, content_id, content_type) DO UPDATE SET
       title = EXCLUDED.title,
       poster_url = EXCLUDED.poster_url,
       backdrop_url = EXCLUDED.backdrop_url,
       year = EXCLUDED.year,
       rating = EXCLUDED.rating,
       updated_at = EXCLUDED.updated_at,
       deleted_at = NULL
     WHERE EXCLUDED.updated_at > watchlist_items.updated_at
     RETURNING ${ROW_COLUMNS}`,
    [
      userId,
      input.providerId,
      input.contentId,
      input.contentType,
      input.title,
      input.posterUrl ?? null,
      input.backdropUrl ?? null,
      input.year ?? null,
      input.rating ?? null,
      new Date(input.updatedAt),
    ]
  );

  if (result.rows.length > 0) return mapRow(result.rows[0]!);
  // Lost the race (not newer than what's already stored) -- the INSERT/DO
  // UPDATE above is a no-op in that case, so fetch and return the
  // still-current row instead.
  return getWatchlistItem(userId, input.providerId, input.contentId, input.contentType) as Promise<WatchlistItem>;
}

/**
 * Soft-deletes one item via the same atomic, LWW-gated shape as
 * upsertWatchlistItem. Returns null only when the row never existed for
 * this user at all (e.g. a pre-Milestone-7 local-only item this device
 * never pushed) -- there is nothing server-side to reconcile in that case,
 * so the caller's local removal simply stands. When a row does exist,
 * always returns its current state, whether this call actually removed it
 * or lost the race to a newer write elsewhere (in which case the returned
 * deletedAt is null, telling the caller to keep the item active locally).
 */
export async function removeWatchlistItem(userId: string, input: WatchlistDeleteInput): Promise<WatchlistItem | null> {
  const result = await pool.query<WatchlistRow>(
    `UPDATE watchlist_items
     SET deleted_at = $5, updated_at = $5
     WHERE user_id = $1 AND provider_id = $2 AND content_id = $3 AND content_type = $4
       AND updated_at < $5
     RETURNING ${ROW_COLUMNS}`,
    [userId, input.providerId, input.contentId, input.contentType, new Date(input.updatedAt)]
  );

  if (result.rows.length > 0) return mapRow(result.rows[0]!);
  const current = await getWatchlistItem(userId, input.providerId, input.contentId, input.contentType);
  return current;
}

async function getWatchlistItem(
  userId: string,
  providerId: string,
  contentId: string,
  contentType: string
): Promise<WatchlistItem | null> {
  const result = await pool.query<WatchlistRow>(
    `SELECT ${ROW_COLUMNS} FROM watchlist_items
     WHERE user_id = $1 AND provider_id = $2 AND content_id = $3 AND content_type = $4`,
    [userId, providerId, contentId, contentType]
  );
  return result.rows[0] ? mapRow(result.rows[0]) : null;
}
