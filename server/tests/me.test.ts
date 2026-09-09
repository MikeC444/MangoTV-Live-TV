import type { Express } from "express";
import request from "supertest";
import { beforeEach, describe, expect, it } from "vitest";
import { createApp } from "../src/app.js";
import { resetDatabase } from "./helpers/db.js";
import { createTestSession } from "./helpers/auth.js";

// A fresh app per test, not one shared across the whole file — the auth
// rate limiter carries its own in-memory counters (see
// middleware/rateLimit.ts), and a single shared app would let one test's
// requests count against another's budget.
let app: Express;

beforeEach(async () => {
  app = createApp();
  await resetDatabase();
});

describe("auth middleware (requireAuth) via GET /user/me", () => {
  it("rejects a request with no Authorization header", async () => {
    const response = await request(app).get("/user/me");
    expect(response.status).toBe(401);
  });

  it("rejects a malformed Authorization header", async () => {
    const response = await request(app).get("/user/me").set("Authorization", "not-a-bearer-token");
    expect(response.status).toBe(401);
  });

  it("rejects an empty bearer token", async () => {
    const response = await request(app).get("/user/me").set("Authorization", "Bearer ");
    expect(response.status).toBe(401);
  });

  it("rejects a well-formed but unknown token", async () => {
    const response = await request(app).get("/user/me").set("Authorization", "Bearer " + "a".repeat(43));
    expect(response.status).toBe(401);
  });

  it("rejects an expired session's token", async () => {
    const session = await createTestSession({ expiresInMs: -1000 });
    const response = await request(app).get("/user/me").set("Authorization", `Bearer ${session.token}`);
    expect(response.status).toBe(401);
  });

  it("rejects a token whose device has been remotely revoked", async () => {
    const session = await createTestSession({ deviceRevoked: true });
    const response = await request(app).get("/user/me").set("Authorization", `Bearer ${session.token}`);
    expect(response.status).toBe(401);
  });

  it("rejects a revoked session's token", async () => {
    const session = await createTestSession({ revoked: true });
    const response = await request(app).get("/user/me").set("Authorization", `Bearer ${session.token}`);
    expect(response.status).toBe(401);
  });

  it("rejects a token belonging to a soft-deleted user", async () => {
    const session = await createTestSession({ userDeleted: true });
    const response = await request(app).get("/user/me").set("Authorization", `Bearer ${session.token}`);
    expect(response.status).toBe(401);
  });

  it("accepts a valid token and returns that session's own user", async () => {
    const session = await createTestSession({ email: "alice@example.com", displayName: "Alice" });
    const response = await request(app).get("/user/me").set("Authorization", `Bearer ${session.token}`);
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ id: session.userId, email: "alice@example.com", displayName: "Alice" });
  });

  it("every response carries an X-Request-Id header", async () => {
    const response = await request(app).get("/user/me");
    expect(response.headers["x-request-id"]).toBeTruthy();
  });
});

describe("cross-user data isolation", () => {
  it("never returns another user's data, even when the client asks for it by id", async () => {
    const alice = await createTestSession({ email: "alice@example.com", displayName: "Alice" });
    const bob = await createTestSession({ email: "bob@example.com", displayName: "Bob" });

    // Alice's token, but the request tries to name Bob's id explicitly —
    // requireAuth only ever trusts the token, so this must still come
    // back as Alice, never Bob.
    const response = await request(app)
      .get(`/user/me?id=${bob.userId}`)
      .set("Authorization", `Bearer ${alice.token}`);

    expect(response.status).toBe(200);
    expect(response.body.id).toBe(alice.userId);
    expect(response.body.id).not.toBe(bob.userId);
    expect(response.body.email).toBe("alice@example.com");
  });

  it("Bob's token can never authenticate as Alice, and vice versa", async () => {
    const alice = await createTestSession({ email: "alice@example.com" });
    const bob = await createTestSession({ email: "bob@example.com" });

    const asAlice = await request(app).get("/user/me").set("Authorization", `Bearer ${alice.token}`);
    const asBob = await request(app).get("/user/me").set("Authorization", `Bearer ${bob.token}`);

    expect(asAlice.body.id).toBe(alice.userId);
    expect(asBob.body.id).toBe(bob.userId);
    expect(asAlice.body.id).not.toBe(asBob.body.id);
  });
});

describe("unmatched routes and rate limiting", () => {
  it("returns a generic JSON 404 for unknown routes, not Express's default HTML page", async () => {
    const response = await request(app).get("/this-route-does-not-exist");
    expect(response.status).toBe(404);
    expect(response.body).toEqual({ error: "Not found" });
  });

  it("advertises rate limit headers", async () => {
    const response = await request(app).get("/health");
    expect(response.headers["ratelimit-limit"]).toBeTruthy();
  });
});
