import { z } from "zod";

// Mirrors watchlist_items' columns (see migrations/0009_watchlist_items.sql).
// contentType matches the Postgres enum's values exactly (see
// migrations/0001_extensions_and_enums.sql). updatedAt is the client's own
// local mutation timestamp, not server-assigned -- same last-write-wins
// contract as settingsBodySchema, applied per item instead of per account.
const contentTypeSchema = z.enum(["MOVIE", "TV_SHOW"]);

export const watchlistItemBodySchema = z.object({
  providerId: z.string().min(1).max(200),
  contentId: z.string().min(1).max(500),
  contentType: contentTypeSchema,
  title: z.string().min(1).max(500),
  posterUrl: z.string().max(2000).nullable().optional(),
  backdropUrl: z.string().max(2000).nullable().optional(),
  year: z.number().int().min(0).max(9999).nullable().optional(),
  rating: z.number().min(0).max(100).nullable().optional(),
  updatedAt: z.iso.datetime("updatedAt must be an ISO-8601 UTC timestamp"),
});
export type WatchlistItemInput = z.infer<typeof watchlistItemBodySchema>;

// The natural key plus updatedAt -- everything DELETE needs to identify
// the row and apply the same LWW rule, carried as query params since a
// DELETE request has no conventional body across every HTTP client.
export const watchlistDeleteQuerySchema = z.object({
  providerId: z.string().min(1).max(200),
  contentId: z.string().min(1).max(500),
  contentType: contentTypeSchema,
  updatedAt: z.iso.datetime("updatedAt must be an ISO-8601 UTC timestamp"),
});
export type WatchlistDeleteInput = z.infer<typeof watchlistDeleteQuerySchema>;
