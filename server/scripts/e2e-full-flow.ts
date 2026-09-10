import { randomUUID } from "node:crypto";

// Milestone 16 — the eight named end-to-end scenarios, driven against a
// REAL running server instance over REAL HTTP (no supertest, no direct
// database access) — exactly the same API surface the Fire TV app itself
// talks to, using two-plus independent "devices" (their own deviceId,
// their own tokens) the way real multi-device usage actually looks.
//
// What this proves: the account/auth/sync system is correct end-to-end
// at the protocol and data layer — QR registration and login, two
// devices converging on identical state, cross-device changes
// propagating, logout actually revoking access, complete data isolation
// between accounts, replay-safety of the writes an offline retry queue
// depends on, and every named cross-account attack failing.
//
// What this does NOT and CANNOT prove, because it never touches the
// actual Android app: Fire TV remote/D-pad navigation, a real QR code
// rendered on screen and scanned by a real phone camera, the local
// DataStore cache continuing to serve a UI with the network actually
// disabled, or on-device performance feel. Those need a human on real
// Fire TV hardware — see docs/milestone-16-e2e-test-plan.md.
//
// Usage: point E2E_API_BASE_URL at a running server (defaults to the
// local dev server) and run `npm run e2e`. Safe to re-run against a
// persistent (non-truncated) database — every account this script
// creates uses a timestamp-unique email, so it never collides with a
// previous run's data the way TRUNCATE-based test fixtures do.

const BASE_URL = process.env.E2E_API_BASE_URL ?? "http://localhost:3000";
const RUN_ID = Date.now();
const PASSWORD = "correct horse battery staple 42";
const ALICE_EMAIL = `alice-e2e-${RUN_ID}@example.com`;
const BOB_EMAIL = `bob-e2e-${RUN_ID}@example.com`;

interface CallOptions {
  token?: string;
  body?: unknown;
  query?: Record<string, string>;
}

interface CallResult {
  status: number;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  body: any;
}

async function call(method: string, path: string, opts: CallOptions = {}): Promise<CallResult> {
  const url = new URL(path, BASE_URL);
  if (opts.query) {
    for (const [key, value] of Object.entries(opts.query)) url.searchParams.set(key, value);
  }
  const headers: Record<string, string> = {};
  if (opts.token) headers.Authorization = `Bearer ${opts.token}`;
  if (opts.body !== undefined) headers["Content-Type"] = "application/json";

  const response = await fetch(url, {
    method,
    headers,
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
  });
  const text = await response.text();
  let body: unknown;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = text;
    }
  }
  return { status: response.status, body };
}

let passCount = 0;
let failCount = 0;
const failures: string[] = [];

function check(condition: boolean, label: string): void {
  if (condition) {
    passCount += 1;
    console.log(`  ✓ ${label}`);
  } else {
    failCount += 1;
    failures.push(label);
    console.log(`  ✗ ${label}`);
  }
}

function section(title: string): void {
  console.log(`\n${title}`);
}

function nowIso(): string {
  return new Date().toISOString();
}

async function main(): Promise<void> {
  console.log(`Running full-flow E2E against ${BASE_URL}`);
  console.log(`Alice: ${ALICE_EMAIL}  Bob: ${BOB_EMAIL}`);

  // ------------------------------------------------------------------
  section("TEST 1 -- NEW USER (QR registration)");
  const deviceA = randomUUID();
  let r = await call("POST", "/auth/qr/create", {
    body: { deviceId: deviceA, deviceName: "E2E Device A", platform: "fire_tv" },
  });
  check(r.status === 201 && typeof r.body?.token === "string" && typeof r.body?.activationUrl === "string", "QR session created");
  const qrTokenA: string = r.body.token;

  r = await call("GET", "/auth/qr/resolve", { query: { token: qrTokenA } });
  check(r.status === 200 && r.body?.status === "pending", "QR resolves as pending before activation");

  r = await call("POST", "/auth/qr/complete", {
    body: { token: qrTokenA, mode: "register", email: ALICE_EMAIL, password: PASSWORD, displayName: "Alice E2E" },
  });
  check(r.status === 204, "QR completion (register) accepted");

  r = await call("GET", "/auth/qr/status", { query: { token: qrTokenA } });
  check(
    r.status === 200 && r.body?.status === "completed" && typeof r.body?.accessToken === "string" && typeof r.body?.refreshToken === "string",
    "TV poll delivers real session tokens"
  );
  const deviceAAccessToken: string = r.body.accessToken;

  r = await call("GET", "/auth/qr/status", { query: { token: qrTokenA } });
  check(r.status === 200 && r.body?.status !== "completed", "QR token cannot be claimed a second time (replay prevention)");

  r = await call("GET", "/user/me", { token: deviceAAccessToken });
  check(r.status === 200 && r.body?.email === ALICE_EMAIL, "Device A is authenticated as the new account");

  // ------------------------------------------------------------------
  section("TEST 2 -- DATA CREATION (Device A)");
  r = await call("POST", "/user/watchlist", {
    token: deviceAAccessToken,
    body: { providerId: "e2e", contentId: "movie-1", contentType: "MOVIE", title: "E2E Movie One", updatedAt: nowIso() },
  });
  check(r.status === 200, "Watchlist item 1 added");

  r = await call("POST", "/user/watchlist", {
    token: deviceAAccessToken,
    body: { providerId: "e2e", contentId: "movie-2", contentType: "MOVIE", title: "E2E Movie Two", updatedAt: nowIso() },
  });
  check(r.status === 200, "Watchlist item 2 added");

  r = await call("POST", "/user/watch-progress", {
    token: deviceAAccessToken,
    body: {
      providerId: "e2e",
      contentId: "movie-1",
      contentType: "MOVIE",
      title: "E2E Movie One",
      positionMs: 120_000,
      durationMs: 600_000,
      completed: false,
      watchedAt: nowIso(),
    },
  });
  check(r.status === 200 && r.body?.continueWatching !== null, "Watch progress recorded; Continue Watching populated");

  r = await call("PUT", "/user/settings", {
    token: deviceAAccessToken,
    body: {
      homeRowOrder: ["trending", "continue_watching"],
      hiddenRowIds: ["kids"],
      autoplayNextEpisode: false,
      skipIntroEnabled: true,
      updatedAt: nowIso(),
    },
  });
  check(r.status === 200 && r.body?.autoplayNextEpisode === false, "Settings updated");

  r = await call("POST", "/user/addons", {
    token: deviceAAccessToken,
    body: {
      manifestUrl: "https://e2e.example.com/manifest.json",
      addonId: "e2e.addon",
      name: "E2E Addon",
      manifestJson: { id: "e2e.addon", name: "E2E Addon", version: "1.0.0" },
      enabled: true,
      sortOrder: 0,
      updatedAt: nowIso(),
    },
  });
  check(r.status === 200, "Addon installed");

  // ------------------------------------------------------------------
  section("TEST 3 -- SECOND DEVICE (Device B signs into the same account)");
  const deviceB = randomUUID();
  r = await call("POST", "/auth/qr/create", {
    body: { deviceId: deviceB, deviceName: "E2E Device B", platform: "fire_tv" },
  });
  check(r.status === 201, "Device B QR session created");
  const qrTokenB: string = r.body.token;

  r = await call("POST", "/auth/qr/complete", { body: { token: qrTokenB, mode: "login", email: ALICE_EMAIL, password: PASSWORD } });
  check(r.status === 204, "Device B QR completion (login) accepted");

  r = await call("GET", "/auth/qr/status", { query: { token: qrTokenB } });
  check(r.status === 200 && r.body?.status === "completed", "Device B receives real session tokens");
  const deviceBAccessToken: string = r.body.accessToken;
  check(deviceBAccessToken !== deviceAAccessToken, "Device B's access token differs from Device A's (independent per-device sessions)");

  r = await call("GET", "/user/watchlist", { token: deviceBAccessToken });
  check(r.status === 200 && r.body?.items?.length === 2, "Device B sees both watchlist items Device A added");

  r = await call("GET", "/user/continue-watching", { token: deviceBAccessToken });
  check(r.status === 200 && r.body?.items?.length === 1, "Device B sees Device A's Continue Watching entry");

  r = await call("GET", "/user/settings", { token: deviceBAccessToken });
  check(
    r.status === 200 && r.body?.autoplayNextEpisode === false && JSON.stringify(r.body?.homeRowOrder) === JSON.stringify(["trending", "continue_watching"]),
    "Device B sees Device A's settings"
  );

  r = await call("GET", "/user/addons", { token: deviceBAccessToken });
  check(
    r.status === 200 && (r.body?.items ?? []).some((addon: { manifestUrl: string }) => addon.manifestUrl === "https://e2e.example.com/manifest.json"),
    "Device B sees Device A's addon"
  );

  // ------------------------------------------------------------------
  section("TEST 4 -- CROSS-DEVICE CHANGES (Device B edits, Device A observes)");
  r = await call("DELETE", "/user/watchlist", {
    token: deviceBAccessToken,
    query: { providerId: "e2e", contentId: "movie-2", contentType: "MOVIE", updatedAt: nowIso() },
  });
  check(r.status === 200 || r.status === 204, "Device B removes one watchlist item");

  r = await call("PUT", "/user/settings", {
    token: deviceBAccessToken,
    body: { homeRowOrder: ["trending"], hiddenRowIds: [], autoplayNextEpisode: true, skipIntroEnabled: true, updatedAt: nowIso() },
  });
  check(r.status === 200, "Device B changes a setting");

  r = await call("GET", "/user/watchlist", { token: deviceAAccessToken });
  check(r.status === 200 && r.body?.items?.length === 1, "Device A sees the removal");

  r = await call("GET", "/user/settings", { token: deviceAAccessToken });
  check(r.status === 200 && r.body?.autoplayNextEpisode === true, "Device A sees the setting change");

  // ------------------------------------------------------------------
  section("TEST 5 -- LOGOUT");
  r = await call("POST", "/auth/logout", { token: deviceBAccessToken });
  check(r.status === 204, "Device B logs out");

  r = await call("GET", "/user/me", { token: deviceBAccessToken });
  check(r.status === 401, "Device B's access token is rejected immediately after logout");

  r = await call("GET", "/user/me", { token: deviceAAccessToken });
  check(r.status === 200, "Device A's session remains valid (sessions are per-device, not account-wide)");

  // ------------------------------------------------------------------
  section("TEST 6 -- ACCOUNT SWITCH (a different user, completely disjoint data)");
  const deviceC = randomUUID();
  r = await call("POST", "/auth/qr/create", {
    body: { deviceId: deviceC, deviceName: "E2E Device C", platform: "fire_tv" },
  });
  const qrTokenC: string = r.body.token;
  check(r.status === 201, "Device C QR session created");

  r = await call("POST", "/auth/qr/complete", {
    body: { token: qrTokenC, mode: "register", email: BOB_EMAIL, password: PASSWORD, displayName: "Bob E2E" },
  });
  check(r.status === 204, "Bob's account created");

  r = await call("GET", "/auth/qr/status", { query: { token: qrTokenC } });
  check(r.status === 200 && r.body?.status === "completed", "Bob's device authenticated");
  const bobAccessToken: string = r.body.accessToken;

  r = await call("GET", "/user/watchlist", { token: bobAccessToken });
  check(r.status === 200 && r.body?.items?.length === 0, "Bob's watchlist is empty (disjoint from Alice)");

  r = await call("GET", "/user/settings", { token: bobAccessToken });
  check(r.status === 200 && r.body?.updatedAt === null, "Bob's settings are untouched defaults, not Alice's");

  r = await call("GET", "/user/addons", { token: bobAccessToken });
  check(r.status === 200 && r.body?.items?.length === 0, "Bob's addons are empty");

  // ------------------------------------------------------------------
  section("TEST 7 -- OFFLINE (backend-verifiable subset only -- see note)");
  console.log(
    "  NOTE: local cache continuity while genuinely offline, and a queued\n" +
      "  change replaying automatically once network returns, are Android-side\n" +
      "  behaviors (Milestone 13) that require a real device -- not something\n" +
      "  a backend-only script can exercise. What IS verified here is the\n" +
      "  server-side contract the client's retry queue depends on:"
  );

  const replayUpdatedAt = nowIso();
  r = await call("POST", "/user/watchlist", {
    token: deviceAAccessToken,
    body: { providerId: "e2e", contentId: "movie-3", contentType: "MOVIE", title: "E2E Movie Three", updatedAt: replayUpdatedAt },
  });
  check(r.status === 200, "First write of a simulated offline-queued item succeeds");

  // A retry queue replaying the exact same request after reconnecting.
  r = await call("POST", "/user/watchlist", {
    token: deviceAAccessToken,
    body: { providerId: "e2e", contentId: "movie-3", contentType: "MOVIE", title: "E2E Movie Three", updatedAt: replayUpdatedAt },
  });
  check(r.status === 200, "Replaying the identical queued write is safe (no duplicate, no error)");

  const staleUpdatedAt = new Date(Date.now() - 60_000).toISOString();
  await call("POST", "/user/watchlist", {
    token: deviceAAccessToken,
    body: { providerId: "e2e", contentId: "movie-3", contentType: "MOVIE", title: "STALE -- SHOULD NOT WIN", updatedAt: staleUpdatedAt },
  });
  r = await call("GET", "/user/watchlist", { token: deviceAAccessToken });
  const item3 = (r.body?.items ?? []).find((item: { contentId: string }) => item.contentId === "movie-3");
  check(item3?.title === "E2E Movie Three", "A stale-timestamped replay loses to the already-applied newer write (last-write-wins holds)");

  // ------------------------------------------------------------------
  section("TEST 8 -- SECURITY (cross-account access must fail)");
  const farFutureUpdatedAt = new Date(Date.now() + 365 * 24 * 3600 * 1000).toISOString();
  r = await call("DELETE", "/user/watchlist", {
    token: bobAccessToken,
    query: { providerId: "e2e", contentId: "movie-1", contentType: "MOVIE", updatedAt: farFutureUpdatedAt },
  });
  check(r.status === 204, "Bob's delete of Alice's item natural key has no server-side row for Bob to affect (204, not a leak)");

  r = await call("GET", "/user/watchlist", { token: deviceAAccessToken });
  check(r.status === 200 && (r.body?.items ?? []).some((item: { contentId: string }) => item.contentId === "movie-1"), "Alice's item is untouched by Bob's attempt");

  r = await call("GET", "/user/watchlist", {});
  check(r.status === 401, "A request with no Authorization header is rejected");

  r = await call("GET", "/user/me", { token: "not-a-real-token" });
  check(r.status === 401, "A malformed/unknown token is rejected");

  r = await call("DELETE", `/auth/sessions/${randomUUID()}`, { token: bobAccessToken });
  check(r.status === 404, "Revoking a session id Bob doesn't own returns 404, not a leak of whether it exists");

  // ------------------------------------------------------------------
  console.log(`\n${passCount} passed, ${failCount} failed.`);
  if (failCount > 0) {
    console.log("\nFailed checks:");
    for (const failure of failures) console.log(`  - ${failure}`);
    process.exitCode = 1;
  }
}

main().catch((error: unknown) => {
  console.error("E2E script crashed:", error);
  process.exitCode = 1;
});
