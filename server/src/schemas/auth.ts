import { z } from "zod";

// Length requirement only, no forced complexity rules (symbols/digits/
// mixed case) — modern NIST guidance treats those as producing
// predictable, reused passwords rather than stronger ones. The max is a
// defensive cap on hashing cost for a pathological input, not a security
// boundary.
const password = z.string().min(8, "Password must be at least 8 characters").max(256);
const email = z.string().trim().toLowerCase().email("Must be a valid email address");
// The Fire TV app (or whatever else calls this API) generates and
// persists this UUID locally once, per Milestone 1's devices table design
// — it identifies the physical device/install, not a hardware serial.
const deviceId = z.uuid("deviceId must be a UUID");
const deviceName = z.string().trim().min(1).max(100).optional();
const platform = z.string().trim().min(1).max(50).optional();

export const registerSchema = z.object({
  email,
  password,
  displayName: z.string().trim().min(1).max(100).optional(),
  deviceId,
  deviceName,
  platform,
});
export type RegisterInput = z.infer<typeof registerSchema>;

export const loginSchema = z.object({
  email,
  password,
  deviceId,
  deviceName,
  platform,
});
export type LoginInput = z.infer<typeof loginSchema>;

export const refreshSchema = z.object({
  refreshToken: z.string().min(1),
});
export type RefreshInput = z.infer<typeof refreshSchema>;
