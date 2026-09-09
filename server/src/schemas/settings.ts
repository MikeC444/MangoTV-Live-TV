import { z } from "zod";

// Mirrors user_settings' columns exactly (see
// migrations/0006_user_settings.sql). updatedAt is the client's own local
// mutation timestamp, not server-assigned -- see settingsService's
// upsertUserSettings for why that's what last-write-wins compares against.
export const settingsBodySchema = z.object({
  homeRowOrder: z.array(z.string().min(1).max(200)).max(500),
  hiddenRowIds: z.array(z.string().min(1).max(200)).max(500),
  autoplayNextEpisode: z.boolean(),
  skipIntroEnabled: z.boolean(),
  updatedAt: z.iso.datetime("updatedAt must be an ISO-8601 UTC timestamp"),
});
export type SettingsInput = z.infer<typeof settingsBodySchema>;
