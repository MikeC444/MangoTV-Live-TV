import { z } from "zod";

// Mirrors user_addons' columns (see migrations/0007_user_addons.sql) --
// the cloud mirror of the local InstalledAddon list (manifestUrl,
// manifest, enabled). manifestJson caches the addon's own manifest.json
// verbatim, same as the column it lands in: validated only as "a JSON
// object" (its actual shape is entirely addon-defined, per the Stremio
// addon protocol -- this system doesn't need to know more about it than
// that to store and echo it back). addon_settings (per-addon config
// beyond enabled/order) stays unused here, same as in the app itself --
// see Milestone 0's audit and migration 0008's own header comment.
export const addonBodySchema = z.object({
  manifestUrl: z.string().min(1).max(2000),
  addonId: z.string().min(1).max(500),
  name: z.string().min(1).max(500),
  manifestJson: z.record(z.string(), z.unknown()),
  enabled: z.boolean(),
  sortOrder: z.number().int().min(0).max(100_000),
  updatedAt: z.iso.datetime("updatedAt must be an ISO-8601 UTC timestamp"),
});
export type AddonInput = z.infer<typeof addonBodySchema>;

// manifestUrl plus updatedAt -- everything DELETE needs to identify the
// row and apply the same LWW rule, carried as query params for the same
// reason watchlist's DELETE does (no conventional body on a DELETE
// request).
export const addonDeleteQuerySchema = z.object({
  manifestUrl: z.string().min(1).max(2000),
  updatedAt: z.iso.datetime("updatedAt must be an ISO-8601 UTC timestamp"),
});
export type AddonDeleteInput = z.infer<typeof addonDeleteQuerySchema>;
