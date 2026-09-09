import type { PoolClient } from "pg";
import { pool } from "../db/pool.js";
import { HttpError } from "../lib/httpError.js";
import { hashPassword, verifyPassword } from "../security/password.js";
import { generateToken, hashToken } from "../security/tokens.js";
import type { LoginInput, RegisterInput } from "../schemas/auth.js";

const ACCESS_TOKEN_TTL_MS = 60 * 60 * 1000; // 1 hour
const REFRESH_TOKEN_TTL_MS = 30 * 24 * 60 * 60 * 1000; // 30 days

const UNIQUE_VIOLATION = "23505";

// Computed once, lazily, on first use — an argon2 hash of a random value
// nobody could ever type as a real password. See its use in
// verifyCredentials() for why this exists: closing a timing side-channel,
// not a real credential.
let dummyHashPromise: Promise<string> | undefined;
function getDummyHash(): Promise<string> {
  dummyHashPromise ??= hashPassword(generateToken());
  return dummyHashPromise;
}

export interface AuthUser {
  id: string;
  email: string;
  displayName: string | null;
}

export interface TokenPair {
  accessToken: string;
  accessTokenExpiresAt: Date;
  refreshToken: string;
  refreshTokenExpiresAt: Date;
}

export interface AuthResult extends TokenPair {
  user: AuthUser;
}

export interface SessionSummary {
  id: string;
  deviceName: string;
  platform: string;
  createdAt: Date;
  lastUsedAt: Date;
  accessTokenExpiresAt: Date;
}

/**
 * Inserts a new user within an existing transaction — a building block
 * shared by register() and the QR "create account" flow
 * (qrAuthService.completeQrSession), both of which need it alongside
 * other statements in the same transaction rather than owning their own.
 */
export async function insertUser(
  client: PoolClient,
  email: string,
  password: string,
  displayName: string | undefined | null
): Promise<AuthUser> {
  const passwordHash = await hashPassword(password);
  try {
    const result = await client.query<{ id: string; email: string; display_name: string | null }>(
      "INSERT INTO users (email, password_hash, display_name) VALUES ($1, $2, $3) RETURNING id, email, display_name",
      [email, passwordHash, displayName ?? null]
    );
    const row = result.rows[0]!;
    return { id: row.id, email: row.email, displayName: row.display_name };
  } catch (error) {
    if ((error as { code?: string }).code === UNIQUE_VIOLATION) {
      throw new HttpError(409, "An account with that email already exists");
    }
    throw error;
  }
}

/**
 * Verifies an email+password pair, throwing the same generic error for
 * "no such account" and "wrong password" alike — shared by login() and
 * the QR "sign in" flow. Always runs exactly one argon2 verify, whether
 * or not the email matched a real account (against a dummy hash when it
 * doesn't): skipping straight to an error for an unknown email would make
 * that response measurably faster than a known-email-wrong-password one
 * (argon2 is deliberately slow), which is exactly the kind of timing
 * side-channel that lets a request enumerate which emails have accounts
 * even though the response body itself is identical either way.
 */
export async function verifyCredentials(email: string, password: string): Promise<AuthUser> {
  const userResult = await pool.query<{ id: string; email: string; password_hash: string; display_name: string | null }>(
    "SELECT id, email, password_hash, display_name FROM users WHERE email = $1 AND deleted_at IS NULL",
    [email]
  );
  const row = userResult.rows[0];

  const passwordValid = await verifyPassword(row?.password_hash ?? (await getDummyHash()), password);
  if (!row || !passwordValid) {
    throw new HttpError(401, "Invalid email or password");
  }

  return { id: row.id, email: row.email, displayName: row.display_name };
}

/**
 * Inserts or reactivates the (user, deviceId) device row, then creates a
 * fresh session for it — the combined "make this device logged in as
 * this user" step shared by register(), login(), and QR completion.
 * Reusing the device upsert for both password and QR sign-in means a
 * device that was previously remotely signed out (devices.revoked_at
 * set) becomes usable again on any fresh, legitimate authentication —
 * revocation is about killing existing sessions, not permanently banning
 * the hardware.
 */
export async function createSessionForDevice(
  client: PoolClient,
  userId: string,
  deviceId: string,
  deviceName: string | undefined,
  platform: string | undefined
): Promise<TokenPair> {
  const deviceResult = await client.query<{ id: string }>(
    `INSERT INTO devices (user_id, device_identifier, device_name, platform, last_seen_at)
     VALUES ($1, $2, COALESCE($3, 'Fire TV'), COALESCE($4, 'fire_tv'), now())
     ON CONFLICT (user_id, device_identifier)
     DO UPDATE SET
       device_name = COALESCE($3, devices.device_name),
       platform = COALESCE($4, devices.platform),
       last_seen_at = now(),
       updated_at = now(),
       revoked_at = NULL
     RETURNING id`,
    [userId, deviceId, deviceName ?? null, platform ?? null]
  );
  const internalDeviceId = deviceResult.rows[0]!.id;

  const accessToken = generateToken();
  const refreshToken = generateToken();
  const accessTokenExpiresAt = new Date(Date.now() + ACCESS_TOKEN_TTL_MS);
  const refreshTokenExpiresAt = new Date(Date.now() + REFRESH_TOKEN_TTL_MS);

  await client.query(
    `INSERT INTO sessions (user_id, device_id, access_token_hash, access_token_expires_at, refresh_token_hash, refresh_token_expires_at)
     VALUES ($1, $2, $3, $4, $5, $6)`,
    [userId, internalDeviceId, hashToken(accessToken), accessTokenExpiresAt, hashToken(refreshToken), refreshTokenExpiresAt]
  );

  return { accessToken, accessTokenExpiresAt, refreshToken, refreshTokenExpiresAt };
}

export async function register(input: RegisterInput): Promise<AuthResult> {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    const user = await insertUser(client, input.email, input.password, input.displayName);
    const tokens = await createSessionForDevice(client, user.id, input.deviceId, input.deviceName, input.platform);
    await client.query("COMMIT");
    return { ...tokens, user };
  } catch (error) {
    await client.query("ROLLBACK");
    throw error;
  } finally {
    client.release();
  }
}

export async function login(input: LoginInput): Promise<AuthResult> {
  const user = await verifyCredentials(input.email, input.password);

  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    const tokens = await createSessionForDevice(client, user.id, input.deviceId, input.deviceName, input.platform);
    await client.query("COMMIT");
    return { ...tokens, user };
  } catch (error) {
    await client.query("ROLLBACK");
    throw error;
  } finally {
    client.release();
  }
}

export async function refresh(refreshToken: string): Promise<TokenPair> {
  const newAccessToken = generateToken();
  const newRefreshToken = generateToken();
  const accessTokenExpiresAt = new Date(Date.now() + ACCESS_TOKEN_TTL_MS);
  const refreshTokenExpiresAt = new Date(Date.now() + REFRESH_TOKEN_TTL_MS);

  // Single atomic UPDATE: the WHERE clause both validates (unexpired,
  // unrevoked session; non-deleted user; non-revoked device) and performs
  // the rotation in one statement, so there's no separate check-then-act
  // race window between confirming the token is valid and replacing it.
  // Rotating on every use means a stolen-and-reused *old* refresh token
  // simply fails from then on, rather than continuing to work forever.
  const result = await pool.query<{ id: string }>(
    `UPDATE sessions s
     SET access_token_hash = $2,
         access_token_expires_at = $3,
         refresh_token_hash = $4,
         refresh_token_expires_at = $5,
         updated_at = now(),
         last_used_at = now()
     FROM users u, devices d
     WHERE s.refresh_token_hash = $1
       AND s.revoked_at IS NULL
       AND s.refresh_token_expires_at > now()
       AND u.id = s.user_id AND u.deleted_at IS NULL
       AND d.id = s.device_id AND d.revoked_at IS NULL
     RETURNING s.id`,
    [hashToken(refreshToken), hashToken(newAccessToken), accessTokenExpiresAt, hashToken(newRefreshToken), refreshTokenExpiresAt]
  );

  if (result.rows.length === 0) {
    throw new HttpError(401, "Invalid or expired refresh token");
  }

  return { accessToken: newAccessToken, accessTokenExpiresAt, refreshToken: newRefreshToken, refreshTokenExpiresAt };
}

export async function logout(sessionId: string): Promise<void> {
  await pool.query("UPDATE sessions SET revoked_at = now() WHERE id = $1 AND revoked_at IS NULL", [sessionId]);
}

export async function listSessions(userId: string): Promise<SessionSummary[]> {
  const result = await pool.query<{
    id: string;
    device_name: string;
    platform: string;
    created_at: Date;
    last_used_at: Date;
    access_token_expires_at: Date;
  }>(
    `SELECT s.id, d.device_name, d.platform, s.created_at, s.last_used_at, s.access_token_expires_at
     FROM sessions s
     JOIN devices d ON d.id = s.device_id
     WHERE s.user_id = $1
       AND s.revoked_at IS NULL
       AND s.refresh_token_expires_at > now()
       AND d.revoked_at IS NULL
     ORDER BY s.last_used_at DESC`,
    [userId]
  );
  return result.rows.map((row) => ({
    id: row.id,
    deviceName: row.device_name,
    platform: row.platform,
    createdAt: row.created_at,
    lastUsedAt: row.last_used_at,
    accessTokenExpiresAt: row.access_token_expires_at,
  }));
}

export async function revokeSession(userId: string, sessionId: string): Promise<void> {
  // Scoped to userId in the WHERE clause, not checked afterward — a
  // session id that exists but belongs to someone else matches zero rows
  // here, indistinguishable from an id that doesn't exist at all, which
  // is exactly what stops this from confirming another user's session id
  // is real.
  const result = await pool.query(
    "UPDATE sessions SET revoked_at = now() WHERE id = $1 AND user_id = $2 AND revoked_at IS NULL RETURNING id",
    [sessionId, userId]
  );
  if (result.rowCount === 0) {
    throw new HttpError(404, "Session not found");
  }
}
