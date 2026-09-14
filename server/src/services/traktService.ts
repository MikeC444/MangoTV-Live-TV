import { pool } from "../db/pool.js";
import { getTraktCredentials } from "../config/env.js";
import { decryptSecret, encryptSecret } from "../security/crypto.js";
import { HttpError } from "../lib/httpError.js";

// All Trakt API traffic (both the OAuth device-code dance and the one
// plain API call this feature makes -- GET /users/settings, to learn the
// connected username) goes to this one host. Verified against Trakt's
// current OAuth client library implementations (a PHP League OAuth2
// provider and PyTrakt) rather than Trakt's own docs site, which this
// sandbox's network policy blocks outbound access to -- see this
// milestone's own writeup for why that verification path was needed.
const TRAKT_API_BASE = "https://api.trakt.tv";

// Trakt's device-code flow has no redirect back to the app at all (that's
// the entire point of the flow -- see startDeviceLink's own doc), but the
// *general* /oauth/token endpoint this app also uses for refreshing a
// token still expects a redirect_uri field to be present. This is the
// standard placeholder OAuth client libraries send when there's no real
// web redirect to give.
const OOB_REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob";

function requireCredentials(): { clientId: string; clientSecret: string } {
  const credentials = getTraktCredentials();
  if (!credentials) throw new HttpError(503, "Trakt integration is not configured on this server");
  return credentials;
}

export function isTraktConfigured(): boolean {
  return getTraktCredentials() !== null;
}

async function traktRequest(
  path: string,
  options: { method: string; body?: unknown; accessToken?: string }
): Promise<Response> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    "trakt-api-version": "2",
  };
  const credentials = getTraktCredentials();
  if (credentials) headers["trakt-api-key"] = credentials.clientId;
  if (options.accessToken) headers.Authorization = `Bearer ${options.accessToken}`;

  return fetch(`${TRAKT_API_BASE}${path}`, {
    method: options.method,
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });
}

interface TraktDeviceCodeResponse {
  device_code: string;
  user_code: string;
  verification_url: string;
  expires_in: number;
  interval: number;
}

interface TraktTokenResponse {
  access_token: string;
  refresh_token: string;
  expires_in: number;
  created_at: number;
  scope?: string;
}

interface TraktProfileResponse {
  user?: { username?: string; ids?: { slug?: string } };
}

export interface DeviceLinkInfo {
  userCode: string;
  verificationUrl: string;
  /** verificationUrl with the user_code appended as a path segment, e.g. https://trakt.tv/activate/ABCD1234 -- lets a QR code jump straight past manual code entry. Not part of Trakt's response; built here since it's a well-known convention several other Trakt-integrated apps already rely on. */
  directVerificationUrl: string;
  expiresInSeconds: number;
  intervalSeconds: number;
}

export type DevicePollResult =
  | { status: "pending" }
  | { status: "expired" }
  | { status: "denied" }
  | { status: "not_found" }
  | { status: "connected"; username: string | null };

export interface TraktConnectionStatus {
  /** False means this server has no TRAKT_CLIENT_ID/SECRET configured at all -- distinct from "configured but not connected", so the app can explain *why* Connect isn't available instead of offering a button that would just 503. */
  configured: boolean;
  connected: boolean;
  username: string | null;
  connectedAt: string | null;
}

interface DeviceLinkRow {
  device_code: string;
  user_code: string;
  verification_url: string;
  interval_seconds: number;
  expires_at: Date;
  last_polled_at: Date | null;
}

interface ConnectionRow {
  access_token: string;
  refresh_token: string;
  scope: string | null;
  expires_at: Date;
  trakt_username: string | null;
  connected_at: Date;
}

async function selectDeviceLink(userId: string): Promise<DeviceLinkRow | null> {
  const result = await pool.query<DeviceLinkRow>(
    `SELECT device_code, user_code, verification_url, interval_seconds, expires_at, last_polled_at
     FROM trakt_device_links WHERE user_id = $1`,
    [userId]
  );
  return result.rows[0] ?? null;
}

async function selectConnection(userId: string): Promise<ConnectionRow | null> {
  const result = await pool.query<ConnectionRow>(
    `SELECT access_token, refresh_token, scope, expires_at, trakt_username, connected_at
     FROM trakt_connections WHERE user_id = $1`,
    [userId]
  );
  return result.rows[0] ?? null;
}

function buildDirectVerificationUrl(verificationUrl: string, userCode: string): string {
  return `${verificationUrl.replace(/\/+$/, "")}/${encodeURIComponent(userCode)}`;
}

/**
 * Starts (or restarts -- ON CONFLICT replaces any still-pending attempt)
 * the OAuth Device Code flow (RFC 8628): asks Trakt for a device_code/
 * user_code pair, persists the device_code server-side, and hands the app
 * back only what it needs to show the user (never the device_code itself
 * -- see the migration's own comment on why that's fine to keep
 * server-only). The app is expected to display userCode/verificationUrl
 * (or a QR code pointed at directVerificationUrl) and poll pollDeviceLink
 * roughly every intervalSeconds until it reports something other than
 * "pending".
 *
 * This flow is the one Trakt (and OAuth Device Authorization Grant more
 * generally) recommends specifically for TVs and other limited-input
 * devices: the user authorizes on a *separate* phone/computer, so nothing
 * here ever needs a redirect back into this app the way a browser-based
 * Authorization Code flow would -- "returning to the app" is trivial
 * because the app was never left in the first place, it just keeps
 * polling until this account shows up as authorized.
 */
export async function startDeviceLink(userId: string): Promise<DeviceLinkInfo> {
  const credentials = requireCredentials();

  const response = await traktRequest("/oauth/device/code", {
    method: "POST",
    body: { client_id: credentials.clientId },
  });
  if (!response.ok) {
    throw new HttpError(502, `Trakt rejected the device code request (HTTP ${response.status})`);
  }
  const body = (await response.json()) as TraktDeviceCodeResponse;
  const expiresAt = new Date(Date.now() + body.expires_in * 1000);

  await pool.query(
    `INSERT INTO trakt_device_links (user_id, device_code, user_code, verification_url, interval_seconds, expires_at, last_polled_at, created_at)
     VALUES ($1, $2, $3, $4, $5, $6, NULL, now())
     ON CONFLICT (user_id) DO UPDATE SET
       device_code = EXCLUDED.device_code,
       user_code = EXCLUDED.user_code,
       verification_url = EXCLUDED.verification_url,
       interval_seconds = EXCLUDED.interval_seconds,
       expires_at = EXCLUDED.expires_at,
       last_polled_at = NULL,
       created_at = now()`,
    [userId, body.device_code, body.user_code, body.verification_url, body.interval, expiresAt]
  );

  return {
    userCode: body.user_code,
    verificationUrl: body.verification_url,
    directVerificationUrl: buildDirectVerificationUrl(body.verification_url, body.user_code),
    expiresInSeconds: body.expires_in,
    intervalSeconds: body.interval,
  };
}

/** Best-effort: a failed profile lookup never fails the connection itself -- the tokens are already good at this point, a username is a display nicety on top. */
async function fetchProfile(accessToken: string): Promise<{ username: string | null; slug: string | null } | null> {
  try {
    const response = await traktRequest("/users/settings", { method: "GET", accessToken });
    if (!response.ok) return null;
    const body = (await response.json()) as TraktProfileResponse;
    return { username: body.user?.username ?? null, slug: body.user?.ids?.slug ?? null };
  } catch {
    return null;
  }
}

async function establishConnection(userId: string, token: TraktTokenResponse): Promise<string | null> {
  const expiresAt = new Date((token.created_at + token.expires_in) * 1000);
  const profile = await fetchProfile(token.access_token);

  await pool.query(
    `INSERT INTO trakt_connections (user_id, access_token, refresh_token, scope, expires_at, trakt_slug, trakt_username, connected_at, updated_at)
     VALUES ($1, $2, $3, $4, $5, $6, $7, now(), now())
     ON CONFLICT (user_id) DO UPDATE SET
       access_token = EXCLUDED.access_token,
       refresh_token = EXCLUDED.refresh_token,
       scope = EXCLUDED.scope,
       expires_at = EXCLUDED.expires_at,
       trakt_slug = EXCLUDED.trakt_slug,
       trakt_username = EXCLUDED.trakt_username,
       connected_at = now(),
       updated_at = now()`,
    [
      userId,
      encryptSecret(token.access_token),
      encryptSecret(token.refresh_token),
      token.scope ?? null,
      expiresAt,
      profile?.slug ?? null,
      profile?.username ?? null,
    ]
  );

  return profile?.username ?? null;
}

/**
 * The TV's poll endpoint, called roughly every intervalSeconds while
 * DeviceLinkInfo is on screen. Trakt's device-token endpoint reports
 * "still waiting"/"denied"/"expired"/etc. as distinct raw HTTP status
 * codes rather than a shared JSON error field -- confirmed against a
 * community OAuth device-flow implementation's own status-code table
 * (see this milestone's writeup), since that specific shape isn't
 * something either OAuth client library consulted for the base URL/header
 * shape happened to exercise. 429 ("polling too fast") is folded into
 * "pending" rather than surfaced as its own state -- the interval
 * throttle below already keeps this app from tripping it under normal
 * polling, and if it ever does, the correct behavior from the caller's
 * point of view is identical to "not ready yet, keep waiting."
 */
export async function pollDeviceLink(userId: string): Promise<DevicePollResult> {
  const link = await selectDeviceLink(userId);
  if (!link) return { status: "not_found" };

  if (link.expires_at.getTime() <= Date.now()) {
    await pool.query("DELETE FROM trakt_device_links WHERE user_id = $1", [userId]);
    return { status: "expired" };
  }

  // Defensive backstop against 429s if this app's own poll cadence ever
  // drifts faster than the interval Trakt asked for -- just report
  // "pending" again without spending a real Trakt request.
  if (link.last_polled_at && Date.now() - link.last_polled_at.getTime() < link.interval_seconds * 1000) {
    return { status: "pending" };
  }

  const credentials = requireCredentials();
  await pool.query("UPDATE trakt_device_links SET last_polled_at = now() WHERE user_id = $1", [userId]);

  const response = await traktRequest("/oauth/device/token", {
    method: "POST",
    body: { code: link.device_code, client_id: credentials.clientId, client_secret: credentials.clientSecret },
  });

  if (response.status === 200) {
    const body = (await response.json()) as TraktTokenResponse;
    const username = await establishConnection(userId, body);
    await pool.query("DELETE FROM trakt_device_links WHERE user_id = $1", [userId]);
    return { status: "connected", username };
  }
  if (response.status === 400 || response.status === 429) {
    return { status: "pending" };
  }
  if (response.status === 418) {
    await pool.query("DELETE FROM trakt_device_links WHERE user_id = $1", [userId]);
    return { status: "denied" };
  }
  if (response.status === 404 || response.status === 409 || response.status === 410) {
    // Unrecognized, already-claimed, or expired -- all equally "this
    // attempt is dead, start a new one" from the caller's point of view.
    await pool.query("DELETE FROM trakt_device_links WHERE user_id = $1", [userId]);
    return { status: "expired" };
  }

  throw new HttpError(502, `Trakt device token poll failed unexpectedly (HTTP ${response.status})`);
}

type RefreshOutcome = "refreshed" | "invalid_grant" | "transient_error";

/**
 * Only a confirmed `invalid_grant` from Trakt (the refresh token itself is
 * dead -- revoked on trakt.tv, or genuinely expired) is treated as this
 * connection actually ending. A network failure, a 5xx, or any other
 * unexpected shape degrades to "transient" and leaves the stored
 * connection exactly as-is -- the same "only a confirmed rejection clears
 * stored auth state" rule this codebase already applies to its own
 * session tokens (see AuthRepository.ensureFreshSession's kdoc). A
 * temporary Trakt outage must never look like "you got disconnected."
 */
async function tryRefresh(userId: string, row: ConnectionRow): Promise<RefreshOutcome> {
  const credentials = getTraktCredentials();
  if (!credentials) return "transient_error";

  let response: Response;
  try {
    response = await traktRequest("/oauth/token", {
      method: "POST",
      body: {
        refresh_token: decryptSecret(row.refresh_token),
        client_id: credentials.clientId,
        client_secret: credentials.clientSecret,
        redirect_uri: OOB_REDIRECT_URI,
        grant_type: "refresh_token",
      },
    });
  } catch {
    return "transient_error";
  }

  if (response.ok) {
    const body = (await response.json()) as TraktTokenResponse;
    const expiresAt = new Date((body.created_at + body.expires_in) * 1000);
    await pool.query(
      `UPDATE trakt_connections SET access_token = $2, refresh_token = $3, scope = $4, expires_at = $5, updated_at = now() WHERE user_id = $1`,
      [userId, encryptSecret(body.access_token), encryptSecret(body.refresh_token), body.scope ?? row.scope, expiresAt]
    );
    return "refreshed";
  }

  const errorBody = (await response.json().catch(() => null)) as { error?: string } | null;
  return errorBody?.error === "invalid_grant" ? "invalid_grant" : "transient_error";
}

/**
 * What Settings > Account renders. Refreshes just-in-time (never eagerly
 * on a schedule -- nothing else in this codebase runs background timers
 * for a single account's state either) when the stored access token looks
 * expired, so a merely-stale token is invisible to the caller: it comes
 * back either refreshed or, on a transient failure, reported as still
 * connected using its last-known-good display info.
 */
export async function getConnectionStatus(userId: string): Promise<TraktConnectionStatus> {
  if (!isTraktConfigured()) {
    return { configured: false, connected: false, username: null, connectedAt: null };
  }

  const row = await selectConnection(userId);
  if (!row) return { configured: true, connected: false, username: null, connectedAt: null };

  if (row.expires_at.getTime() <= Date.now()) {
    const outcome = await tryRefresh(userId, row);
    if (outcome === "invalid_grant") {
      await pool.query("DELETE FROM trakt_connections WHERE user_id = $1", [userId]);
      return { configured: true, connected: false, username: null, connectedAt: null };
    }
  }

  return { configured: true, connected: true, username: row.trakt_username, connectedAt: row.connected_at.toISOString() };
}

/**
 * Clears any in-progress pairing attempt and the established connection
 * alike, best-effort-revoking the access token with Trakt first. Revoke
 * failing (network hiccup, Trakt outage) never blocks the disconnect --
 * same "local state always clears regardless" rule as
 * AuthRepository.logout's own server-side counterpart (POST /auth/logout).
 * There's no separate sync/scrobble feature built on top of this
 * connection yet for disconnecting to also have to tear down -- the
 * stored tokens are the only Trakt-related state this app holds.
 */
export async function disconnect(userId: string): Promise<void> {
  await pool.query("DELETE FROM trakt_device_links WHERE user_id = $1", [userId]);

  const row = await selectConnection(userId);
  if (!row) return;

  const credentials = getTraktCredentials();
  if (credentials) {
    try {
      await traktRequest("/oauth/revoke", {
        method: "POST",
        body: {
          token: decryptSecret(row.access_token),
          client_id: credentials.clientId,
          client_secret: credentials.clientSecret,
        },
      });
    } catch {
      // Best-effort -- see this function's own doc.
    }
  }

  await pool.query("DELETE FROM trakt_connections WHERE user_id = $1", [userId]);
}
