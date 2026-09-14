import { createCipheriv, createDecipheriv, randomBytes } from "node:crypto";
import { getTokenEncryptionKey } from "../config/env.js";

const ALGORITHM = "aes-256-gcm";
// AES-GCM's recommended nonce size -- using the standard 12 bytes (rather
// than AES's own 16-byte block size) is what keeps the authentication tag
// cryptographically sound; this isn't an arbitrary choice.
const IV_LENGTH = 12;

/**
 * Reversible, authenticated encryption for secrets that -- unlike this
 * app's own bearer tokens (security/tokens.ts, one-way SHA-256 hashed) --
 * must be recoverable in plaintext to actually be used later: a Trakt
 * access/refresh token has to be sent back to Trakt's own API on every
 * call, so hashing it the way a session token is hashed would make it
 * permanently unusable. AES-256-GCM via TOKEN_ENCRYPTION_KEY, a key that
 * only this server process ever holds, so a leaked database dump alone
 * doesn't hand out usable Trakt tokens.
 *
 * Output is `<iv>.<authTag>.<ciphertext>`, each base64-encoded -- a fresh
 * random IV per call (never reused with the same key, which is what GCM's
 * security property depends on) plus the authentication tag GCM produces,
 * both needed again at decrypt time.
 */
export function encryptSecret(plaintext: string): string {
  const key = getTokenEncryptionKey();
  const iv = randomBytes(IV_LENGTH);
  const cipher = createCipheriv(ALGORITHM, key, iv);
  const ciphertext = Buffer.concat([cipher.update(plaintext, "utf8"), cipher.final()]);
  const authTag = cipher.getAuthTag();
  return [iv, authTag, ciphertext].map((buffer) => buffer.toString("base64")).join(".");
}

/** Inverse of encryptSecret. Throws if the value is malformed or the auth tag doesn't verify (wrong key, or tampered/corrupted ciphertext) -- never returns a "best effort" partial decrypt. */
export function decryptSecret(encoded: string): string {
  const [ivB64, authTagB64, ciphertextB64] = encoded.split(".");
  if (!ivB64 || !authTagB64 || !ciphertextB64) {
    throw new Error("Malformed encrypted secret");
  }

  const key = getTokenEncryptionKey();
  const decipher = createDecipheriv(ALGORITHM, key, Buffer.from(ivB64, "base64"));
  decipher.setAuthTag(Buffer.from(authTagB64, "base64"));
  const plaintext = Buffer.concat([decipher.update(Buffer.from(ciphertextB64, "base64")), decipher.final()]);
  return plaintext.toString("utf8");
}
