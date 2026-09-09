import { randomUUID } from "node:crypto";
import { pool } from "../../src/db/pool.js";
import { generateToken, hashToken } from "../../src/security/tokens.js";

export interface TestSession {
  token: string;
  userId: string;
  sessionId: string;
  email: string;
  displayName: string | null;
}

interface CreateTestSessionOptions {
  email?: string;
  displayName?: string | null;
  /** Milliseconds from now the access token expires. Negative creates an already-expired session. */
  expiresInMs?: number;
  revoked?: boolean;
  userDeleted?: boolean;
}

/**
 * Inserts a user, device, and session directly via the same pool the app
 * uses — bypassing the account-creation/login/QR endpoints, which don't
 * exist yet (Milestones 3-4). Hashes the generated token exactly the way
 * requireAuth verifies it, so tests exercise the real verification path,
 * not a shortcut around it. Returns the plaintext token to send as
 * `Authorization: Bearer <token>`.
 */
export async function createTestSession(options: CreateTestSessionOptions = {}): Promise<TestSession> {
  const email = options.email ?? `test-${randomUUID()}@example.com`;
  const displayName = options.displayName ?? null;

  const userResult = await pool.query<{ id: string }>(
    "INSERT INTO users (email, password_hash, display_name, deleted_at) VALUES ($1, 'unused-in-tests', $2, $3) RETURNING id",
    [email, displayName, options.userDeleted ? new Date() : null]
  );
  const userId = userResult.rows[0]!.id;

  const deviceResult = await pool.query<{ id: string }>(
    "INSERT INTO devices (user_id, device_identifier) VALUES ($1, $2) RETURNING id",
    [userId, randomUUID()]
  );
  const deviceId = deviceResult.rows[0]!.id;

  const token = generateToken();
  const expiresAt = new Date(Date.now() + (options.expiresInMs ?? 60 * 60 * 1000));

  const sessionResult = await pool.query<{ id: string }>(
    `INSERT INTO sessions (user_id, device_id, access_token_hash, access_token_expires_at, refresh_token_hash, refresh_token_expires_at, revoked_at)
     VALUES ($1, $2, $3, $4, $5, now() + interval '30 days', $6)
     RETURNING id`,
    [userId, deviceId, hashToken(token), expiresAt, hashToken(generateToken()), options.revoked ? new Date() : null]
  );

  return { token, userId, sessionId: sessionResult.rows[0]!.id, email, displayName };
}
