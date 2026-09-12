import { z } from "zod";

// GET query params, not a body -- this is a pure read/lookup, same
// reasoning as watchlist's own DELETE query schema. year is optional:
// addons don't always know a title's release year, and TMDB's search
// still works (just less precisely disambiguated) without one.
export const trailerQuerySchema = z.object({
  title: z.string().min(1).max(500),
  year: z.coerce.number().int().min(1900).max(2100).optional(),
  // Matches Content.ContentType on the client exactly (see
  // schemas/watchlist.ts's own contentTypeSchema) -- translated to
  // TMDB's "movie"/"tv" media-type strings inside trailerService.
  type: z.enum(["MOVIE", "TV_SHOW"]),
});
export type TrailerQueryInput = z.infer<typeof trailerQuerySchema>;
