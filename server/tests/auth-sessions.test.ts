import { randomUUID } from "node:crypto";
import type { Express } from "express";
import request from "supertest";
import { beforeEach, describe, expect, it } from "vitest";
import { createApp } from "../src/app.js";
import { resetDatabase } from "./helpers/db.js";

// Fresh app per test — see tests/me.test.ts's comment on why (the auth
// rate limiter's counters must not leak between tests).
let app: Express;

beforeEach(async () => {
  app = createApp();
  await resetDatabase();
});

async function register(email: string, deviceName?: string) {
  return request(app)
    .post("/auth/register")
    .send({ email, password: "correct-horse-battery", deviceId: randomUUID(), deviceName });
}

describe("POST /auth/logout", () => {
  it("rejects an unauthenticated request", async () => {
    const response = await request(app).post("/auth/logout");
    expect(response.status).toBe(401);
  });

  it("revokes the current session, and its access token stops working immediately afterward", async () => {
    const registered = await register("ivy@example.com");
    const accessToken = registered.body.accessToken;

    const logout = await request(app).post("/auth/logout").set("Authorization", `Bearer ${accessToken}`);
    expect(logout.status).toBe(204);

    const meAfter = await request(app).get("/user/me").set("Authorization", `Bearer ${accessToken}`);
    expect(meAfter.status).toBe(401);
  });

  it("also invalidates the refresh token for that session", async () => {
    const registered = await register("jack@example.com");
    await request(app).post("/auth/logout").set("Authorization", `Bearer ${registered.body.accessToken}`);

    const refresh = await request(app).post("/auth/refresh").send({ refreshToken: registered.body.refreshToken });
    expect(refresh.status).toBe(401);
  });
});

describe("GET /auth/sessions and DELETE /auth/sessions/:id", () => {
  it("rejects an unauthenticated request", async () => {
    const response = await request(app).get("/auth/sessions");
    expect(response.status).toBe(401);
  });

  it("lists only the requesting user's own sessions, marking the current one", async () => {
    const registered = await register("kim@example.com", "Kitchen");
    const list = await request(app).get("/auth/sessions").set("Authorization", `Bearer ${registered.body.accessToken}`);

    expect(list.status).toBe(200);
    expect(list.body).toHaveLength(1);
    expect(list.body[0].deviceName).toBe("Kitchen");
    expect(list.body[0].isCurrent).toBe(true);
  });

  it("never lists another user's sessions", async () => {
    const alice = await register("alice2@example.com", "Alice's TV");
    const bob = await register("bob2@example.com", "Bob's TV");

    const aliceList = await request(app).get("/auth/sessions").set("Authorization", `Bearer ${alice.body.accessToken}`);

    expect(aliceList.body).toHaveLength(1);
    expect(aliceList.body[0].deviceName).toBe("Alice's TV");
    expect(bob.body.user.id).not.toBe(alice.body.user.id);
  });

  it("lets a user revoke one of their own other sessions by id", async () => {
    const registered = await register("liam@example.com", "Bedroom");
    const secondLogin = await request(app)
      .post("/auth/login")
      .send({ email: "liam@example.com", password: "correct-horse-battery", deviceId: randomUUID(), deviceName: "Office" });

    const list = await request(app).get("/auth/sessions").set("Authorization", `Bearer ${registered.body.accessToken}`);
    const officeSession = list.body.find((s: { deviceName: string }) => s.deviceName === "Office");

    const revoke = await request(app)
      .delete(`/auth/sessions/${officeSession.id}`)
      .set("Authorization", `Bearer ${registered.body.accessToken}`);
    expect(revoke.status).toBe(204);

    const officeStillWorks = await request(app).get("/user/me").set("Authorization", `Bearer ${secondLogin.body.accessToken}`);
    expect(officeStillWorks.status).toBe(401);
  });

  it("returns 404 rather than revoking when asked to delete another user's session id", async () => {
    const alice = await register("alice3@example.com");
    const bob = await register("bob3@example.com");

    const aliceSessions = await request(app).get("/auth/sessions").set("Authorization", `Bearer ${alice.body.accessToken}`);
    const aliceSessionId = aliceSessions.body[0].id;

    const attempt = await request(app)
      .delete(`/auth/sessions/${aliceSessionId}`)
      .set("Authorization", `Bearer ${bob.body.accessToken}`);
    expect(attempt.status).toBe(404);

    // Alice's session must still work — Bob's attempt had no effect.
    const meStillWorks = await request(app).get("/user/me").set("Authorization", `Bearer ${alice.body.accessToken}`);
    expect(meStillWorks.status).toBe(200);
  });

  it("rejects a malformed session id", async () => {
    const registered = await register("mia@example.com");
    const response = await request(app)
      .delete("/auth/sessions/not-a-uuid")
      .set("Authorization", `Bearer ${registered.body.accessToken}`);
    expect(response.status).toBe(400);
  });
});
