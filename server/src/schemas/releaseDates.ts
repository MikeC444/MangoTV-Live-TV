import { z } from "zod";

// GET query params, not a body -- same shape/reasoning as trailers' own
// query schema (a pure read/lookup). Movies only, for now, so unlike
// trailerQuerySchema there's no `type` field.
export const releaseDateQuerySchema = z.object({
  title: z.string().min(1).max(500),
  year: z.coerce.number().int().min(1900).max(2100).optional(),
});
export type ReleaseDateQueryInput = z.infer<typeof releaseDateQuerySchema>;
