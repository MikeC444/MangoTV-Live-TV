import { pool } from "../db/pool.js";
import { HttpError } from "../lib/httpError.js";
import { generateToken, hashToken } from "../security/tokens.js";
import { createSessionForDevice, insertUser, verifyCredentials, type AuthResult } from "./authService.js";
import type { CompleteQrInput, CreateQrInput } from "../schemas/qr.js";

// Long enough that a user can pick up their phone and scan, short enough
// to bound how long a shown QR code stays a live credential.
const QR_SESSION_TTL_MS = 10 * 60 * 1000;

export interface QrSession {
  token: string;
  expiresAt: Date;
}

export async function createQrSession(input: CreateQrInput): Promise<QrSession> {
  const token = generateToken();
  const expiresAt = new Date(Date.now() + QR_SESSION_TTL_MS);

  await pool.query(
    `INSERT INTO qr_auth_sessions (token_hash, device_identifier, device_name, platform, expires_at)
     VALUES ($1, $2, $3, $4, $5)`,
    [hashToken(token), input.deviceId, input.deviceName ?? null, input.platform ?? null, expiresAt]
  );

  return { token, expiresAt };
}

export type QrStatus = "pending" | "expired" | "completed" | "consumed" | "not_found";

/**
 * Read-only status peek — used by the activation web page to check a
 * token before showing the sign-in form. Never mutates or issues
 * anything, unlike pollQrSession below, which is what actually hands out
 * real session tokens exactly once; if this function did that too, the
 * web page checking status on page load could accidentally be the one
 * that consumes the session, starving the TV's own poll of the tokens it
 * needs.
 */
export async function resolveQrSession(token: string): Promise<QrStatus> {
  const result = await pool.query<{ status: QrStatus; expires_at: Date }>(
    "SELECT status, expires_at FROM qr_auth_sessions WHERE token_hash = $1",
    [hashToken(token)]
  );
  const row = result.rows[0];
  if (!row) return "not_found";
  if (row.status === "pending" && row.expires_at.getTime() <= Date.now()) return "expired";
  return row.status;
}

/**
 * Called by the activation page once the user submits sign-in/create-
 * account credentials. Resolves the account (creating it for "register",
 * verifying it for "login") and marks the QR session completed — all in
 * one transaction, so a failure partway through (e.g. the qr_auth_sessions
 * update failing after a brand-new user was just inserted) rolls back the
 * account creation too rather than leaving an orphaned user nobody can
 * reach via this QR session.
 *
 * Deliberately does NOT create the actual device/session/tokens here —
 * that happens in pollQrSession, at the moment the TV actually claims
 * them, which is what lets tokens exist only as long as it takes to hand
 * them straight to their owner (see that function's own comment).
 */
export async function completeQrSession(token: string, input: CompleteQrInput): Promise<void> {
  const tokenHash = hashToken(token);
  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    const qrResult = await client.query<{ id: string; status: string; expires_at: Date }>(
      "SELECT id, status, expires_at FROM qr_auth_sessions WHERE token_hash = $1 FOR UPDATE",
      [tokenHash]
    );
    const qrRow = qrResult.rows[0];
    if (!qrRow || qrRow.status !== "pending" || qrRow.expires_at.getTime() <= Date.now()) {
      throw new HttpError(410, "Invalid or expired QR token");
    }

    const user =
      input.mode === "register"
        ? await insertUser(client, input.email, input.password, input.displayName)
        : await verifyCredentials(input.email, input.password);

    await client.query(
      "UPDATE qr_auth_sessions SET status = 'completed', user_id = $1, completed_at = now() WHERE id = $2",
      [user.id, qrRow.id]
    );

    await client.query("COMMIT");
  } catch (error) {
    await client.query("ROLLBACK");
    throw error;
  } finally {
    client.release();
  }
}

export type QrPollResult = { status: "pending" } | { status: "expired" } | ({ status: "completed" } & AuthResult);

/**
 * The TV's poll endpoint. Only this function ever hands out real session
 * tokens for a QR flow, and it does so exactly once: the UPDATE below can
 * match a given token_hash at most once (its own WHERE clause requires
 * status = 'completed', and the same statement flips it to 'consumed'),
 * so two overlapping polls can never both walk away with a usable
 * session — one wins the row, the other sees zero rows updated and falls
 * through to a plain status check.
 */
export async function pollQrSession(token: string): Promise<QrPollResult> {
  const claimed = await claimCompletedSession(hashToken(token));
  if (claimed) return claimed;

  const status = await resolveQrSession(token);
  // "pending" is the only state worth reporting distinctly here — a
  // consumed, already-expired, or never-valid token are all equally
  // "get a new QR code" from the TV's point of view, and collapsing them
  // avoids giving a poller any way to tell those cases apart.
  return status === "pending" ? { status: "pending" } : { status: "expired" };
}

interface ClaimRow {
  user_id: string;
  device_identifier: string;
  device_name: string | null;
  platform: string | null;
}

async function claimCompletedSession(tokenHash: string): Promise<({ status: "completed" } & AuthResult) | null> {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    const claimResult = await client.query<ClaimRow>(
      `UPDATE qr_auth_sessions
       SET status = 'consumed', consumed_at = now()
       WHERE token_hash = $1 AND status = 'completed'
       RETURNING user_id, device_identifier, device_name, platform`,
      [tokenHash]
    );
    const row = claimResult.rows[0];
    if (!row) {
      await client.query("ROLLBACK");
      return null;
    }

    const tokens = await createSessionForDevice(
      client,
      row.user_id,
      row.device_identifier,
      row.device_name ?? undefined,
      row.platform ?? undefined
    );
    const userResult = await client.query<{ id: string; email: string; display_name: string | null }>(
      "SELECT id, email, display_name FROM users WHERE id = $1",
      [row.user_id]
    );

    await client.query("COMMIT");

    const userRow = userResult.rows[0]!;
    return {
      status: "completed",
      ...tokens,
      user: { id: userRow.id, email: userRow.email, displayName: userRow.display_name },
    };
  } catch (error) {
    await client.query("ROLLBACK");
    throw error;
  } finally {
    client.release();
  }
}
