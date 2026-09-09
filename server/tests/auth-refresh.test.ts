import { randomUUID } from "node:crypto";
import type { Express } from "express";
import request from "supertest";
import { beforeEach, describe, expect, it } from "vitest";
import { createApp } from "../src/app.js";
import { resetDatabase } from "./helpers/db.js";
import { createTestSession } from "./helpers/auth.js";

// Fresh app per test — see tests/me.test.ts's comment on why (the auth
// rate limiter's counters must not leak between tests).
let app: Express;

beforeEach(async () => {
  app = createApp();
  await resetDatabase();
});

describe("POST /auth/refresh", () => {
  it("rotates both tokens and the new access token works", async () => {
    const registered = await request(app)
      .post("/auth/register")
      .send({ email: "gina@example.com", password: "correct-horse-battery", deviceId: randomUUID() });

    const refreshed = await request(app).post("/auth/refresh").send({ refreshToken: registered.body.refreshToken });

    expect(refreshed.status).toBe(200);
    expect(refreshed.body.accessToken).not.toBe(registered.body.accessToken);
    expect(refreshed.body.refreshToken).not.toBe(registered.body.refreshToken);

    const me = await request(app).get("/user/me").set("Authorization", `Bearer ${refreshed.body.accessToken}`);
    expect(me.status).toBe(200);
  });

  it("invalidates the previous access token immediately, not just at its own natural expiry", async () => {
    const registered = await request(app)
      .post("/auth/register")
      .send({ email: "irene@example.com", password: "correct-horse-battery", deviceId: randomUUID() });

    await request(app).post("/auth/refresh").send({ refreshToken: registered.body.refreshToken });

    const meWithOldToken = await request(app)
      .get("/user/me")
      .set("Authorization", `Bearer ${registered.body.accessToken}`);
    expect(meWithOldToken.status).toBe(401);
  });

  it("rejects reuse of an already-rotated (old) refresh token — replay prevention", async () => {
    const registered = await request(app)
      .post("/auth/register")
      .send({ email: "harry@example.com", password: "correct-horse-battery", deviceId: randomUUID() });

    await request(app).post("/auth/refresh").send({ refreshToken: registered.body.refreshToken });
    const replay = await request(app).post("/auth/refresh").send({ refreshToken: registered.body.refreshToken });

    expect(replay.status).toBe(401);
  });

  it("rejects an unknown/garbage refresh token", async () => {
    const response = await request(app).post("/auth/refresh").send({ refreshToken: "not-a-real-token" });
    expect(response.status).toBe(401);
  });

  it("rejects an expired refresh token", async () => {
    const session = await createTestSession({ refreshExpiresInMs: -1000 });
    const response = await request(app).post("/auth/refresh").send({ refreshToken: session.refreshToken });
    expect(response.status).toBe(401);
  });

  it("rejects a revoked session's refresh token", async () => {
    const session = await createTestSession({ revoked: true });
    const response = await request(app).post("/auth/refresh").send({ refreshToken: session.refreshToken });
    expect(response.status).toBe(401);
  });

  it("rejects a refresh token whose device has been remotely revoked", async () => {
    const session = await createTestSession({ deviceRevoked: true });
    const response = await request(app).post("/auth/refresh").send({ refreshToken: session.refreshToken });
    expect(response.status).toBe(401);
  });
});
