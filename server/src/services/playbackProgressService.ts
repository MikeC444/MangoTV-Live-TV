import type { PoolClient } from "pg";
import { pool } from "../db/pool.js";
import type { HistoryQuery, WatchProgressInput } from "../schemas/watchProgress.js";
import { mapContinueWatchingRow, type ContinueWatchingEntry, type ContinueWatchingRow, CONTINUE_WATCHING_COLUMNS } from "./continueWatchingService.js";

export interface WatchHistoryEntry {
  providerId: string;
  contentId: string;
  contentType: string;
  seasonNumber: number | null;
  episodeNumber: number | null;
  episodeTitle: string | null;
  title: string;
  posterUrl: string | null;
  positionMs: number;
  durationMs: number;
  completed: boolean;
  watchedAt: Date;
}

interface WatchHistoryRow {
  provider_id: string;
  content_id: string;
  content_type: string;
  season_number: number | null;
  episode_number: number | null;
  episode_title: string | null;
  title: string;
  poster_url: string | null;
  position_ms: string; // bigint comes back as string from pg
  duration_ms: string;
  completed: boolean;
  watched_at: Date;
}

const HISTORY_COLUMNS = `provider_id, content_id, content_type, season_number, episode_number, episode_title, title, poster_url, position_ms, duration_ms, completed, watched_at`;

function mapHistoryRow(row: WatchHistoryRow): WatchHistoryEntry {
  return {
    providerId: row.provider_id,
    contentId: row.content_id,
    contentType: row.content_type,
    seasonNumber: row.season_number,
    episodeNumber: row.episode_number,
    episodeTitle: row.episode_title,
    title: row.title,
    posterUrl: row.poster_url,
    positionMs: Number(row.position_ms),
    durationMs: Number(row.duration_ms),
    completed: row.completed,
    watchedAt: row.watched_at,
  };
}

async function getHistoryEntry(
  client: PoolClient,
  userId: string,
  providerId: string,
  contentId: string,
  contentType: string,
  seasonNumber: number | null,
  episodeNumber: number | null
): Promise<WatchHistoryEntry> {
  const result = await client.query<WatchHistoryRow>(
    `SELECT ${HISTORY_COLUMNS} FROM watch_history
     WHERE user_id = $1 AND provider_id = $2 AND content_id = $3 AND content_type = $4
       AND season_number IS NOT DISTINCT FROM $5 AND episode_number IS NOT DISTINCT FROM $6`,
    [userId, providerId, contentId, contentType, seasonNumber, episodeNumber]
  );
  // recordProgress always inserts before falling back to this lookup, so a
  // row is guaranteed to exist by the time this is called.
  return mapHistoryRow(result.rows[0]!);
}

/**
 * Records one playback-progress report against both watch_history (the
 * durable per-episode log) and continue_watching (the per-title resumable
 * pointer), in a single transaction -- they always derive from the same
 * playback event, and a client has no reason to risk them landing
 * inconsistently across two separate requests. Both writes are gated by
 * the same atomic "WHERE EXCLUDED.updated_at > current updated_at"
 * last-write-wins shape used throughout this codebase (settings, watchlist),
 * evaluated independently per table/row since a per-episode fact and a
 * per-title pointer are genuinely different things, even though both are
 * caused by the same report.
 *
 * completed=true clears (soft-deletes) the continue_watching row instead
 * of updating it -- a finished title isn't resumable -- matching
 * migration 0011's own header comment about how continue_watching is
 * maintained from application logic, not a trigger. completed=false
 * upserts it (creating it, or un-deleting/refreshing it if a later
 * re-watch follows an earlier completion).
 */
export async function recordProgress(
  userId: string,
  input: WatchProgressInput
): Promise<{ historyEntry: WatchHistoryEntry; continueWatching: ContinueWatchingEntry | null }> {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    const watchedAt = new Date(input.watchedAt);
    const seasonNumber = input.seasonNumber ?? null;
    const episodeNumber = input.episodeNumber ?? null;

    const historyResult = await client.query<WatchHistoryRow>(
      `INSERT INTO watch_history (user_id, provider_id, content_id, content_type, season_number, episode_number, episode_title, title, poster_url, position_ms, duration_ms, completed, watched_at, updated_at)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $13)
       ON CONFLICT (user_id, provider_id, content_id, content_type, episode_key) DO UPDATE SET
         episode_title = EXCLUDED.episode_title,
         title = EXCLUDED.title,
         poster_url = EXCLUDED.poster_url,
         position_ms = EXCLUDED.position_ms,
         duration_ms = EXCLUDED.duration_ms,
         completed = EXCLUDED.completed,
         watched_at = EXCLUDED.watched_at,
         updated_at = EXCLUDED.updated_at
       WHERE EXCLUDED.updated_at > watch_history.updated_at
       RETURNING ${HISTORY_COLUMNS}`,
      [
        userId,
        input.providerId,
        input.contentId,
        input.contentType,
        seasonNumber,
        episodeNumber,
        input.episodeTitle ?? null,
        input.title,
        input.posterUrl ?? null,
        input.positionMs,
        input.durationMs,
        input.completed,
        watchedAt,
      ]
    );
    const historyEntry = historyResult.rows[0]
      ? mapHistoryRow(historyResult.rows[0])
      : await getHistoryEntry(client, userId, input.providerId, input.contentId, input.contentType, seasonNumber, episodeNumber);

    let continueWatching: ContinueWatchingEntry | null;
    if (input.completed) {
      const deleteResult = await client.query<ContinueWatchingRow>(
        `UPDATE continue_watching
         SET deleted_at = $5, updated_at = $5
         WHERE user_id = $1 AND provider_id = $2 AND content_id = $3 AND content_type = $4
           AND updated_at < $5
         RETURNING ${CONTINUE_WATCHING_COLUMNS}`,
        [userId, input.providerId, input.contentId, input.contentType, watchedAt]
      );
      continueWatching = deleteResult.rows[0] ? mapContinueWatchingRow(deleteResult.rows[0]) : null;
    } else {
      const upsertResult = await client.query<ContinueWatchingRow>(
        `INSERT INTO continue_watching (user_id, provider_id, content_id, content_type, season_number, episode_number, episode_title, title, poster_url, backdrop_url, position_ms, duration_ms, last_watched_at, updated_at, deleted_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $13, NULL)
         ON CONFLICT (user_id, provider_id, content_id, content_type) DO UPDATE SET
           season_number = EXCLUDED.season_number,
           episode_number = EXCLUDED.episode_number,
           episode_title = EXCLUDED.episode_title,
           title = EXCLUDED.title,
           poster_url = EXCLUDED.poster_url,
           backdrop_url = EXCLUDED.backdrop_url,
           position_ms = EXCLUDED.position_ms,
           duration_ms = EXCLUDED.duration_ms,
           last_watched_at = EXCLUDED.last_watched_at,
           updated_at = EXCLUDED.updated_at,
           deleted_at = NULL
         WHERE EXCLUDED.updated_at > continue_watching.updated_at
         RETURNING ${CONTINUE_WATCHING_COLUMNS}`,
        [
          userId,
          input.providerId,
          input.contentId,
          input.contentType,
          seasonNumber,
          episodeNumber,
          input.episodeTitle ?? null,
          input.title,
          input.posterUrl ?? null,
          input.backdropUrl ?? null,
          input.positionMs,
          input.durationMs,
          watchedAt,
        ]
      );
      continueWatching = upsertResult.rows[0]
        ? mapContinueWatchingRow(upsertResult.rows[0])
        : await getContinueWatchingEntryForTransaction(client, userId, input.providerId, input.contentId, input.contentType);
    }

    await client.query("COMMIT");
    return { historyEntry, continueWatching };
  } catch (error) {
    await client.query("ROLLBACK");
    throw error;
  } finally {
    client.release();
  }
}

async function getContinueWatchingEntryForTransaction(
  client: PoolClient,
  userId: string,
  providerId: string,
  contentId: string,
  contentType: string
): Promise<ContinueWatchingEntry | null> {
  const result = await client.query<ContinueWatchingRow>(
    `SELECT ${CONTINUE_WATCHING_COLUMNS} FROM continue_watching
     WHERE user_id = $1 AND provider_id = $2 AND content_id = $3 AND content_type = $4`,
    [userId, providerId, contentId, contentType]
  );
  return result.rows[0] ? mapContinueWatchingRow(result.rows[0]) : null;
}

/**
 * Paginated watch history, newest first, keyset-paginated on watch_history's
 * own (user_id, watched_at DESC) index rather than OFFSET -- this table has
 * no upper bound on size for an active account, unlike watchlist/settings.
 */
export async function listWatchHistory(userId: string, query: HistoryQuery): Promise<WatchHistoryEntry[]> {
  const params: unknown[] = [userId];
  let whereBefore = "";
  if (query.before) {
    params.push(new Date(query.before));
    whereBefore = `AND watched_at < $${params.length}`;
  }
  params.push(query.limit);

  const result = await pool.query<WatchHistoryRow>(
    `SELECT ${HISTORY_COLUMNS} FROM watch_history
     WHERE user_id = $1 ${whereBefore}
     ORDER BY watched_at DESC
     LIMIT $${params.length}`,
    params
  );
  return result.rows.map(mapHistoryRow);
}
