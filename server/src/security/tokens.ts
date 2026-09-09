import { createHash, randomBytes } from "node:crypto";

/**
 * A cryptographically random, URL-safe bearer token (256 bits of entropy)
 * — used for session access/refresh tokens (Milestone 3) and QR
 * activation tokens (Milestone 4) alike. Random and unguessable is the
 * entire security property; no server secret is needed to make one safe.
 */
export function generateToken(): string {
  return randomBytes(32).toString("base64url");
}

/**
 * SHA-256 hash of a bearer token, hex-encoded. This is the only form of a
 * token ever persisted or compared against storage — a leaked database
 * dump must not hand out usable tokens, the same principle as password
 * hashing. Callers re-hash whatever token a request presents and look up
 * by the hash; the raw token itself is never stored anywhere.
 */
export function hashToken(token: string): string {
  return createHash("sha256").update(token).digest("hex");
}
