import * as argon2 from "argon2";

// Argon2id specifically (not argon2's library default, in case that ever
// changes) — the OWASP-recommended password hash, resistant to both
// GPU-cracking (memory-hard) and side-channel attacks (the "id" variant
// mixes argon2i's side-channel resistance with argon2d's GPU resistance).
// Parameters are argon2's own recommended minimums for interactive login.
const HASH_OPTIONS: argon2.HashOptions = {
  type: argon2.argon2id,
  memoryCost: 19456, // 19 MiB
  timeCost: 2,
  parallelism: 1,
};

/** Hashes a plaintext password. The returned string is self-describing (algorithm+params+salt included) — nothing else needs to be stored alongside it. */
export function hashPassword(password: string): Promise<string> {
  return argon2.hash(password, HASH_OPTIONS);
}

/** Verifies a plaintext password against a stored hash produced by hashPassword. */
export function verifyPassword(hash: string, password: string): Promise<boolean> {
  return argon2.verify(hash, password);
}
