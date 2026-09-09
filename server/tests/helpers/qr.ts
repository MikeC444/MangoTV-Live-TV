import { randomUUID } from "node:crypto";
import { pool } from "../../src/db/pool.js";
import { generateToken, hashToken } from "../../src/security/tokens.js";

export interface TestQrSession {
  token: string;
  id: string;
  deviceIdentifier: string;
}

interface CreateTestQrSessionOptions {
  deviceIdentifier?: string;
  status?: "pending" | "expired" | "completed" | "consumed";
  /** Milliseconds from now this session expires. Negative creates an already-expired-but-still-"pending" row, matching what a real stale row looks like before anything notices. */
  expiresInMs?: number;
  userId?: string;
}

/**
 * Inserts a qr_auth_sessions row directly — for fixture states the real
 * create/complete/poll endpoints can't easily produce on demand (an
 * already-expired or already-consumed session). Hashes the token exactly
 * the way the service layer verifies it.
 */
export async function createTestQrSession(options: CreateTestQrSessionOptions = {}): Promise<TestQrSession> {
  const token = generateToken();
  const deviceIdentifier = options.deviceIdentifier ?? randomUUID();
  const expiresAt = new Date(Date.now() + (options.expiresInMs ?? 10 * 60 * 1000));
  const status = options.status ?? "pending";

  const result = await pool.query<{ id: string }>(
    `INSERT INTO qr_auth_sessions (token_hash, device_identifier, status, expires_at, user_id, completed_at, consumed_at)
     VALUES ($1, $2, $3, $4, $5, $6, $7)
     RETURNING id`,
    [
      hashToken(token),
      deviceIdentifier,
      status,
      expiresAt,
      options.userId ?? null,
      status === "completed" || status === "consumed" ? new Date() : null,
      status === "consumed" ? new Date() : null,
    ]
  );

  return { token, id: result.rows[0]!.id, deviceIdentifier };
}
