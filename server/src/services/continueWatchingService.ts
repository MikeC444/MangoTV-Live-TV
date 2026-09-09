import { pool } from "../db/pool.js";

export interface ContinueWatchingEntry {
  providerId: string;
  contentId: string;
  contentType: string;
  seasonNumber: number | null;
  episodeNumber: number | null;
  episodeTitle: string | null;
  title: string;
  posterUrl: string | null;
  backdropUrl: string | null;
  positionMs: number;
  durationMs: number;
  lastWatchedAt: Date;
  /** Non-null means this title is no longer resumable (completed, or removed) -- only meaningful on recordProgress's response, mirroring watchlist's deletedAt contract. listActiveContinueWatching never returns a row with this set. */
  deletedAt: Date | null;
}

export interface ContinueWatchingRow {
  provider_id: string;
  content_id: string;
  content_type: string;
  season_number: number | null;
  episode_number: number | null;
  episode_title: string | null;
  title: string;
  poster_url: string | null;
  backdrop_url: string | null;
  position_ms: string; // bigint comes back as string from pg
  duration_ms: string;
  last_watched_at: Date;
  deleted_at: Date | null;
}

export const CONTINUE_WATCHING_COLUMNS = `provider_id, content_id, content_type, season_number, episode_number, episode_title, title, poster_url, backdrop_url, position_ms, duration_ms, last_watched_at, deleted_at`;

export function mapContinueWatchingRow(row: ContinueWatchingRow): ContinueWatchingEntry {
  return {
    providerId: row.provider_id,
    contentId: row.content_id,
    contentType: row.content_type,
    seasonNumber: row.season_number,
    episodeNumber: row.episode_number,
    episodeTitle: row.episode_title,
    title: row.title,
    posterUrl: row.poster_url,
    backdropUrl: row.backdrop_url,
    positionMs: Number(row.position_ms),
    durationMs: Number(row.duration_ms),
    lastWatchedAt: row.last_watched_at,
    deletedAt: row.deleted_at,
  };
}

/** This account's currently-resumable titles, most recently watched first -- what a fresh sign-in or app launch pulls down to seed/replace Home's Continue Watching row. Never includes soft-deleted (completed/removed) rows. */
export async function listActiveContinueWatching(userId: string): Promise<ContinueWatchingEntry[]> {
  const result = await pool.query<ContinueWatchingRow>(
    `SELECT ${CONTINUE_WATCHING_COLUMNS} FROM continue_watching
     WHERE user_id = $1 AND deleted_at IS NULL
     ORDER BY last_watched_at DESC`,
    [userId]
  );
  return result.rows.map(mapContinueWatchingRow);
}
