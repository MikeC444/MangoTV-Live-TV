import type { Express } from "express";
import request from "supertest";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createApp } from "../src/app.js";
import { pool } from "../src/db/pool.js";
import { resetDatabase } from "./helpers/db.js";
import { createTestSession } from "./helpers/auth.js";

let app: Express;
const fetchMock = vi.fn();

const TEST_ENCRYPTION_KEY = Buffer.alloc(32, 7).toString("base64");

const DEVICE_CODE_BODY = {
  device_code: "test-device-code",
  user_code: "TEST-CODE",
  verification_url: "https://trakt.tv/activate",
  expires_in: 600,
  interval: 5,
};

function tokenBody(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    access_token: "test-access-token",
    refresh_token: "test-refresh-token",
    expires_in: 7_776_000,
    created_at: Math.floor(Date.now() / 1000),
    scope: "public",
    ...overrides,
  };
}

const PROFILE_BODY = { user: { username: "testuser", ids: { slug: "testuser" } } };

function traktResponse(status: number, body: unknown) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  };
}

beforeEach(async () => {
  app = createApp();
  await resetDatabase();
  vi.stubGlobal("fetch", fetchMock);
  process.env.TRAKT_CLIENT_ID = "test-client-id";
  process.env.TRAKT_CLIENT_SECRET = "test-client-secret";
  process.env.TOKEN_ENCRYPTION_KEY = TEST_ENCRYPTION_KEY;
});

afterEach(() => {
  vi.unstubAllGlobals();
  fetchMock.mockReset();
  delete process.env.TRAKT_CLIENT_ID;
  delete process.env.TRAKT_CLIENT_SECRET;
  delete process.env.TOKEN_ENCRYPTION_KEY;
});

async function authHeader() {
  const session = await createTestSession();
  return { Authorization: `Bearer ${session.token}` };
}

describe("GET /user/trakt", () => {
  it("rejects a request with no Authorization header", async () => {
    const response = await request(app).get("/user/trakt");
    expect(response.status).toBe(401);
  });

  it("reports not configured, without ever calling Trakt, when no credentials are set", async () => {
    delete process.env.TRAKT_CLIENT_ID;
    delete process.env.TRAKT_CLIENT_SECRET;
    const auth = await authHeader();
    const response = await request(app).get("/user/trakt").set(auth);
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ configured: false, connected: false, username: null, connectedAt: null });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("reports disconnected for an account that has never linked Trakt", async () => {
    const auth = await authHeader();
    const response = await request(app).get("/user/trakt").set(auth);
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ configured: true, connected: false, username: null, connectedAt: null });
  });
});

describe("POST /user/trakt/link", () => {
  it("rejects a request with no Authorization header", async () => {
    const response = await request(app).post("/user/trakt/link");
    expect(response.status).toBe(401);
  });

  it("returns 503 when Trakt isn't configured", async () => {
    delete process.env.TRAKT_CLIENT_ID;
    delete process.env.TRAKT_CLIENT_SECRET;
    const auth = await authHeader();
    const response = await request(app).post("/user/trakt/link").set(auth);
    expect(response.status).toBe(503);
  });

  it("starts a device link, sending only the client id (never the secret) to Trakt", async () => {
    fetchMock.mockResolvedValueOnce(traktResponse(200, DEVICE_CODE_BODY));
    const auth = await authHeader();
    const response = await request(app).post("/user/trakt/link").set(auth);

    expect(response.status).toBe(201);
    expect(response.body).toEqual({
      userCode: "TEST-CODE",
      verificationUrl: "https://trakt.tv/activate",
      directVerificationUrl: "https://trakt.tv/activate/TEST-CODE",
      expiresInSeconds: 600,
      intervalSeconds: 5,
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toBe("https://api.trakt.tv/oauth/device/code");
    const sentBody = JSON.parse(init.body as string);
    expect(sentBody).toEqual({ client_id: "test-client-id" });
  });

  it("propagates a 502 when Trakt rejects the device code request", async () => {
    fetchMock.mockResolvedValueOnce(traktResponse(400, { error: "invalid_client" }));
    const auth = await authHeader();
    const response = await request(app).post("/user/trakt/link").set(auth);
    expect(response.status).toBe(502);
  });
});

describe("GET /user/trakt/link", () => {
  it("rejects a request with no Authorization header", async () => {
    const response = await request(app).get("/user/trakt/link");
    expect(response.status).toBe(401);
  });

  it("reports not_found when nothing was ever started", async () => {
    const auth = await authHeader();
    const response = await request(app).get("/user/trakt/link").set(auth);
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ status: "not_found" });
  });

  it("reports pending while Trakt says the user hasn't approved it yet", async () => {
    const auth = await authHeader();
    fetchMock.mockResolvedValueOnce(traktResponse(200, DEVICE_CODE_BODY));
    await request(app).post("/user/trakt/link").set(auth);

    fetchMock.mockResolvedValueOnce(traktResponse(400, { error: "authorization_pending" }));
    const response = await request(app).get("/user/trakt/link").set(auth);
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ status: "pending" });
  });

  it("throttles a second immediate poll instead of re-calling Trakt within the interval", async () => {
    const auth = await authHeader();
    fetchMock.mockResolvedValueOnce(traktResponse(200, DEVICE_CODE_BODY));
    await request(app).post("/user/trakt/link").set(auth);

    fetchMock.mockResolvedValueOnce(traktResponse(400, { error: "authorization_pending" }));
    await request(app).get("/user/trakt/link").set(auth);
    expect(fetchMock).toHaveBeenCalledTimes(2); // device/code + first poll

    const secondPoll = await request(app).get("/user/trakt/link").set(auth);
    expect(secondPoll.body).toEqual({ status: "pending" });
    expect(fetchMock).toHaveBeenCalledTimes(2); // no additional Trakt call yet
  });

  it("maps a denied (418) response to status=denied and clears the pending link", async () => {
    const auth = await authHeader();
    fetchMock.mockResolvedValueOnce(traktResponse(200, DEVICE_CODE_BODY));
    await request(app).post("/user/trakt/link").set(auth);

    fetchMock.mockResolvedValueOnce(traktResponse(418, {}));
    const response = await request(app).get("/user/trakt/link").set(auth);
    expect(response.body).toEqual({ status: "denied" });

    const again = await request(app).get("/user/trakt/link").set(auth);
    expect(again.body).toEqual({ status: "not_found" });
  });

  it.each([404, 409, 410])("maps a %i response to status=expired and clears the pending link", async (status) => {
    const auth = await authHeader();
    fetchMock.mockResolvedValueOnce(traktResponse(200, DEVICE_CODE_BODY));
    await request(app).post("/user/trakt/link").set(auth);

    fetchMock.mockResolvedValueOnce(traktResponse(status, {}));
    const response = await request(app).get("/user/trakt/link").set(auth);
    expect(response.body).toEqual({ status: "expired" });
  });

  it("reports expired once this server's own expires_at has passed, without calling Trakt again", async () => {
    const auth = await authHeader();
    fetchMock.mockResolvedValueOnce(traktResponse(200, DEVICE_CODE_BODY));
    await request(app).post("/user/trakt/link").set(auth);

    await pool.query("UPDATE trakt_device_links SET expires_at = now() - interval '1 minute'");

    const response = await request(app).get("/user/trakt/link").set(auth);
    expect(response.body).toEqual({ status: "expired" });
    expect(fetchMock).toHaveBeenCalledTimes(1); // only the original device/code call
  });

  it("completes the flow: connected with username, and GET /user/trakt reflects it", async () => {
    const auth = await authHeader();
    fetchMock.mockResolvedValueOnce(traktResponse(200, DEVICE_CODE_BODY));
    await request(app).post("/user/trakt/link").set(auth);

    fetchMock.mockResolvedValueOnce(traktResponse(200, tokenBody()));
    fetchMock.mockResolvedValueOnce(traktResponse(200, PROFILE_BODY));
    const pollResponse = await request(app).get("/user/trakt/link").set(auth);
    expect(pollResponse.status).toBe(200);
    expect(pollResponse.body).toEqual({ status: "connected", username: "testuser" });

    const statusResponse = await request(app).get("/user/trakt").set(auth);
    expect(statusResponse.body).toEqual({
      configured: true,
      connected: true,
      username: "testuser",
      connectedAt: expect.any(String),
    });

    // The pending link is gone once connected.
    const again = await request(app).get("/user/trakt/link").set(auth);
    expect(again.body).toEqual({ status: "not_found" });
  });

  it("still reports connected (with a null username) if the post-connect profile lookup fails", async () => {
    const auth = await authHeader();
    fetchMock.mockResolvedValueOnce(traktResponse(200, DEVICE_CODE_BODY));
    await request(app).post("/user/trakt/link").set(auth);

    fetchMock.mockResolvedValueOnce(traktResponse(200, tokenBody()));
    fetchMock.mockRejectedValueOnce(new Error("network blip"));
    const pollResponse = await request(app).get("/user/trakt/link").set(auth);
    expect(pollResponse.body).toEqual({ status: "connected", username: null });
  });
});

describe("token refresh via GET /user/trakt", () => {
  async function connectAccount(auth: Record<string, string>) {
    fetchMock.mockResolvedValueOnce(traktResponse(200, DEVICE_CODE_BODY));
    await request(app).post("/user/trakt/link").set(auth);
    fetchMock.mockResolvedValueOnce(traktResponse(200, tokenBody()));
    fetchMock.mockResolvedValueOnce(traktResponse(200, PROFILE_BODY));
    await request(app).get("/user/trakt/link").set(auth);
    await pool.query("UPDATE trakt_connections SET expires_at = now() - interval '1 minute'");
  }

  it("silently refreshes an expired access token and stays connected", async () => {
    const auth = await authHeader();
    await connectAccount(auth);

    fetchMock.mockResolvedValueOnce(traktResponse(200, tokenBody({ access_token: "refreshed-token" })));
    const response = await request(app).get("/user/trakt").set(auth);
    expect(response.body).toEqual({
      configured: true,
      connected: true,
      username: "testuser",
      connectedAt: expect.any(String),
    });
    const [, refreshInit] = fetchMock.mock.calls.at(-1)!;
    expect(JSON.parse(refreshInit.body as string)).toMatchObject({ grant_type: "refresh_token" });
  });

  it("a confirmed invalid_grant clears the connection", async () => {
    const auth = await authHeader();
    await connectAccount(auth);

    fetchMock.mockResolvedValueOnce(traktResponse(400, { error: "invalid_grant" }));
    const response = await request(app).get("/user/trakt").set(auth);
    expect(response.body).toEqual({ configured: true, connected: false, username: null, connectedAt: null });
  });

  it("a network failure during refresh leaves the account looking connected", async () => {
    const auth = await authHeader();
    await connectAccount(auth);

    fetchMock.mockRejectedValueOnce(new Error("network blip"));
    const response = await request(app).get("/user/trakt").set(auth);
    expect(response.body.connected).toBe(true);
    expect(response.body.username).toBe("testuser");
  });
});

describe("DELETE /user/trakt", () => {
  it("rejects a request with no Authorization header", async () => {
    const response = await request(app).delete("/user/trakt");
    expect(response.status).toBe(401);
  });

  it("is a no-op 204 for an account with nothing connected", async () => {
    const auth = await authHeader();
    const response = await request(app).delete("/user/trakt").set(auth);
    expect(response.status).toBe(204);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("revokes with Trakt and clears the stored connection", async () => {
    const auth = await authHeader();
    fetchMock.mockResolvedValueOnce(traktResponse(200, DEVICE_CODE_BODY));
    await request(app).post("/user/trakt/link").set(auth);
    fetchMock.mockResolvedValueOnce(traktResponse(200, tokenBody()));
    fetchMock.mockResolvedValueOnce(traktResponse(200, PROFILE_BODY));
    await request(app).get("/user/trakt/link").set(auth);

    fetchMock.mockResolvedValueOnce(traktResponse(200, {}));
    const response = await request(app).delete("/user/trakt").set(auth);
    expect(response.status).toBe(204);
    const [revokeUrl] = fetchMock.mock.calls.at(-1)!;
    expect(revokeUrl).toBe("https://api.trakt.tv/oauth/revoke");

    const status = await request(app).get("/user/trakt").set(auth);
    expect(status.body).toEqual({ configured: true, connected: false, username: null, connectedAt: null });
  });

  it("still clears the local connection even if the Trakt revoke call fails", async () => {
    const auth = await authHeader();
    fetchMock.mockResolvedValueOnce(traktResponse(200, DEVICE_CODE_BODY));
    await request(app).post("/user/trakt/link").set(auth);
    fetchMock.mockResolvedValueOnce(traktResponse(200, tokenBody()));
    fetchMock.mockResolvedValueOnce(traktResponse(200, PROFILE_BODY));
    await request(app).get("/user/trakt/link").set(auth);

    fetchMock.mockRejectedValueOnce(new Error("Trakt is down"));
    const response = await request(app).delete("/user/trakt").set(auth);
    expect(response.status).toBe(204);

    const status = await request(app).get("/user/trakt").set(auth);
    expect(status.body.connected).toBe(false);
  });
});

describe("cross-user isolation", () => {
  it("one account's Trakt connection is invisible to another", async () => {
    const alice = await createTestSession({ email: "alice-trakt@example.com" });
    const bob = await createTestSession({ email: "bob-trakt@example.com" });
    const aliceAuth = { Authorization: `Bearer ${alice.token}` };
    const bobAuth = { Authorization: `Bearer ${bob.token}` };

    fetchMock.mockResolvedValueOnce(traktResponse(200, DEVICE_CODE_BODY));
    await request(app).post("/user/trakt/link").set(aliceAuth);
    fetchMock.mockResolvedValueOnce(traktResponse(200, tokenBody()));
    fetchMock.mockResolvedValueOnce(traktResponse(200, PROFILE_BODY));
    await request(app).get("/user/trakt/link").set(aliceAuth);

    const bobStatus = await request(app).get("/user/trakt").set(bobAuth);
    expect(bobStatus.body).toEqual({ configured: true, connected: false, username: null, connectedAt: null });

    const bobPoll = await request(app).get("/user/trakt/link").set(bobAuth);
    expect(bobPoll.body).toEqual({ status: "not_found" });
  });
});
