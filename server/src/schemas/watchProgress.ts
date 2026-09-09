import { z } from "zod";

// Mirrors watch_history/continue_watching's shared columns (see
// migrations/0010_watch_history.sql, 0011_continue_watching.sql). One
// client report updates both tables in a single request -- see
// services/playbackProgressService.ts's recordProgress -- since they
// always derive from the same playback event and the client has no
// reason to make two round trips (or risk them landing inconsistently)
// for one position update.
export const watchProgressBodySchema = z
  .object({
    providerId: z.string().min(1).max(200),
    contentId: z.string().min(1).max(500),
    contentType: z.enum(["MOVIE", "TV_SHOW"]),
    seasonNumber: z.number().int().min(0).max(9999).nullable().optional(),
    episodeNumber: z.number().int().min(0).max(9999).nullable().optional(),
    episodeTitle: z.string().max(500).nullable().optional(),
    title: z.string().min(1).max(500),
    posterUrl: z.string().max(2000).nullable().optional(),
    backdropUrl: z.string().max(2000).nullable().optional(),
    // Upper bound is generous (~31,000 years) purely to keep a malformed
    // client value from being handed to Postgres as-is and tripping a raw
    // bigint-overflow error instead of a clean 400 -- not a realistic
    // playback duration limit.
    positionMs: z.number().int().min(0).max(1_000_000_000_000),
    durationMs: z.number().int().min(0).max(1_000_000_000_000),
    completed: z.boolean(),
    // The client's own local mutation timestamp, same last-write-wins
    // contract as settings/watchlist's updatedAt -- named watchedAt here
    // to match watch_history's own column, since this value becomes both
    // watch_history.watched_at and the updated_at gate on both tables.
    watchedAt: z.iso.datetime("watchedAt must be an ISO-8601 UTC timestamp"),
  })
  // seasonNumber/episodeNumber travel as a pair -- watch_history's
  // generated episode_key coalesces each independently to 'x' when null,
  // so a mismatched half-null pair would silently produce a nonsensical
  // key (e.g. "3:x") instead of failing loudly here.
  .refine((body) => (body.seasonNumber == null) === (body.episodeNumber == null), {
    message: "seasonNumber and episodeNumber must both be present or both be absent",
    path: ["episodeNumber"],
  });
export type WatchProgressInput = z.infer<typeof watchProgressBodySchema>;

export const historyQuerySchema = z.object({
  limit: z.coerce.number().int().min(1).max(200).optional().default(50),
  // Keyset pagination on watch_history's own (user_id, watched_at DESC)
  // index: "give me rows watched before this timestamp" -- avoids an
  // OFFSET scan over a table that only grows for an active account.
  before: z.iso.datetime("before must be an ISO-8601 UTC timestamp").optional(),
});
export type HistoryQuery = z.infer<typeof historyQuerySchema>;
