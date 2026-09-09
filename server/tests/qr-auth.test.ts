import { randomUUID } from "node:crypto";
import type { Express } from "express";
import request from "supertest";
import { beforeEach, describe, expect, it } from "vitest";
import { createApp } from "../src/app.js";
import { resetDatabase } from "./helpers/db.js";
import { createTestQrSession } from "./helpers/qr.js";

// Fresh app per test — see tests/me.test.ts's comment on why (rate
// limiter counters must not leak between tests).
let app: Express;

beforeEach(async () => {
  app = createApp();
  await resetDatabase();
});

describe("POST /auth/qr/create", () => {
  it("returns a token, an activation URL containing it, and a future expiry", async () => {
    const response = await request(app).post("/auth/qr/create").send({ deviceId: randomUUID(), deviceName: "Living Room" });

    expect(response.status).toBe(201);
    expect(response.body.token).toBeTruthy();
    expect(response.body.activationUrl).toContain(encodeURIComponent(response.body.token));
    expect(response.body.activationUrl).toMatch(/^http:\/\/localhost:3000\/activate\?token=/);
    expect(new Date(response.body.expiresAt).getTime()).toBeGreaterThan(Date.now());
  });

  it("rejects a non-UUID deviceId", async () => {
    const response = await request(app).post("/auth/qr/create").send({ deviceId: "not-a-uuid" });
    expect(response.status).toBe(400);
  });
});

describe("GET /auth/qr/resolve", () => {
  it("reports a freshly created session as pending", async () => {
    const created = await request(app).post("/auth/qr/create").send({ deviceId: randomUUID() });
    const resolved = await request(app).get("/auth/qr/resolve").query({ token: created.body.token });
    expect(resolved.body).toEqual({ status: "pending" });
  });

  it("reports an unknown token as not_found", async () => {
    const resolved = await request(app).get("/auth/qr/resolve").query({ token: "totally-made-up" });
    expect(resolved.body).toEqual({ status: "not_found" });
  });

  it("reports a timed-out session as expired without anything having to sweep it first", async () => {
    const qr = await createTestQrSession({ expiresInMs: -1000 });
    const resolved = await request(app).get("/auth/qr/resolve").query({ token: qr.token });
    expect(resolved.body).toEqual({ status: "expired" });
  });

  it("never mutates state — resolving a pending session repeatedly still leaves it pollable", async () => {
    const created = await request(app).post("/auth/qr/create").send({ deviceId: randomUUID() });
    await request(app).get("/auth/qr/resolve").query({ token: created.body.token });
    await request(app).get("/auth/qr/resolve").query({ token: created.body.token });
    const status = await request(app).get("/auth/qr/status").query({ token: created.body.token });
    expect(status.body).toEqual({ status: "pending" });
  });
});

describe("the full QR flow — account creation", () => {
  it("creates an account, and the TV's poll delivers working tokens exactly once", async () => {
    const deviceId = randomUUID();
    const created = await request(app).post("/auth/qr/create").send({ deviceId, deviceName: "Bedroom Fire TV" });
    const token = created.body.token;

    const complete = await request(app)
      .post("/auth/qr/complete")
      .send({ token, mode: "register", email: "newuser@example.com", password: "correct-horse-battery" });
    expect(complete.status).toBe(204);

    const firstPoll = await request(app).get("/auth/qr/status").query({ token });
    expect(firstPoll.body.status).toBe("completed");
    expect(firstPoll.body.user.email).toBe("newuser@example.com");
    expect(firstPoll.body.accessToken).toBeTruthy();

    const me = await request(app).get("/user/me").set("Authorization", `Bearer ${firstPoll.body.accessToken}`);
    expect(me.status).toBe(200);
    expect(me.body.email).toBe("newuser@example.com");

    const sessions = await request(app).get("/auth/sessions").set("Authorization", `Bearer ${firstPoll.body.accessToken}`);
    expect(sessions.body[0].deviceName).toBe("Bedroom Fire TV");

    // Single-use / replay prevention: the same token can never deliver
    // tokens a second time.
    const secondPoll = await request(app).get("/auth/qr/status").query({ token });
    expect(secondPoll.body).toEqual({ status: "expired" });
  });

  it("rejects creating an account with an email that's already registered", async () => {
    await request(app)
      .post("/auth/register")
      .send({ email: "taken@example.com", password: "correct-horse-battery", deviceId: randomUUID() });

    const created = await request(app).post("/auth/qr/create").send({ deviceId: randomUUID() });
    const complete = await request(app)
      .post("/auth/qr/complete")
      .send({ token: created.body.token, mode: "register", email: "taken@example.com", password: "another-password" });

    expect(complete.status).toBe(409);
  });
});

describe("the full QR flow — existing account sign-in", () => {
  it("signs in to an existing account via QR", async () => {
    await request(app)
      .post("/auth/register")
      .send({ email: "existing@example.com", password: "correct-horse-battery", deviceId: randomUUID() });

    const created = await request(app).post("/auth/qr/create").send({ deviceId: randomUUID(), deviceName: "Office" });
    const complete = await request(app)
      .post("/auth/qr/complete")
      .send({ token: created.body.token, mode: "login", email: "existing@example.com", password: "correct-horse-battery" });
    expect(complete.status).toBe(204);

    const poll = await request(app).get("/auth/qr/status").query({ token: created.body.token });
    expect(poll.body.status).toBe("completed");
    expect(poll.body.user.email).toBe("existing@example.com");
  });

  it("rejects the wrong password", async () => {
    await request(app)
      .post("/auth/register")
      .send({ email: "haswrongpw@example.com", password: "correct-horse-battery", deviceId: randomUUID() });

    const created = await request(app).post("/auth/qr/create").send({ deviceId: randomUUID() });
    const complete = await request(app)
      .post("/auth/qr/complete")
      .send({ token: created.body.token, mode: "login", email: "haswrongpw@example.com", password: "totally-wrong" });

    expect(complete.status).toBe(401);

    // The session must still be usable afterward — a failed attempt
    // shouldn't burn the QR code.
    const status = await request(app).get("/auth/qr/status").query({ token: created.body.token });
    expect(status.body).toEqual({ status: "pending" });
  });
});

describe("QR expiration, replay, and invalid tokens", () => {
  it("rejects completing an expired session", async () => {
    const qr = await createTestQrSession({ expiresInMs: -1000 });
    const response = await request(app)
      .post("/auth/qr/complete")
      .send({ token: qr.token, mode: "register", email: "toolate@example.com", password: "correct-horse-battery" });
    expect(response.status).toBe(410);
  });

  it("rejects completing an already-completed session", async () => {
    const qr = await createTestQrSession({ status: "completed" });
    const response = await request(app)
      .post("/auth/qr/complete")
      .send({ token: qr.token, mode: "register", email: "toolate2@example.com", password: "correct-horse-battery" });
    expect(response.status).toBe(410);
  });

  it("reports an already-consumed session's status as expired, not pending or completed", async () => {
    const qr = await createTestQrSession({ status: "consumed" });
    const status = await request(app).get("/auth/qr/status").query({ token: qr.token });
    expect(status.body).toEqual({ status: "expired" });
  });

  it("rejects a garbage/invalid token on every endpoint", async () => {
    const resolve = await request(app).get("/auth/qr/resolve").query({ token: "garbage" });
    const status = await request(app).get("/auth/qr/status").query({ token: "garbage" });
    const complete = await request(app)
      .post("/auth/qr/complete")
      .send({ token: "garbage", mode: "login", email: "a@example.com", password: "correct-horse-battery" });

    expect(resolve.body).toEqual({ status: "not_found" });
    expect(status.body).toEqual({ status: "expired" });
    expect(complete.status).toBe(410);
  });
});

describe("multiple simultaneous devices", () => {
  it("keeps two independent QR sessions from interfering with each other", async () => {
    const deviceA = randomUUID();
    const deviceB = randomUUID();

    const createdA = await request(app).post("/auth/qr/create").send({ deviceId: deviceA, deviceName: "Kitchen" });
    const createdB = await request(app).post("/auth/qr/create").send({ deviceId: deviceB, deviceName: "Garage" });

    expect(createdA.body.token).not.toBe(createdB.body.token);

    await request(app)
      .post("/auth/qr/complete")
      .send({ token: createdA.body.token, mode: "register", email: "devicea@example.com", password: "correct-horse-battery" });
    await request(app)
      .post("/auth/qr/complete")
      .send({ token: createdB.body.token, mode: "register", email: "deviceb@example.com", password: "correct-horse-battery" });

    const pollA = await request(app).get("/auth/qr/status").query({ token: createdA.body.token });
    const pollB = await request(app).get("/auth/qr/status").query({ token: createdB.body.token });

    expect(pollA.body.user.email).toBe("devicea@example.com");
    expect(pollB.body.user.email).toBe("deviceb@example.com");
    expect(pollA.body.accessToken).not.toBe(pollB.body.accessToken);

    // Each device's token only ever proves that device's identity.
    const meA = await request(app).get("/user/me").set("Authorization", `Bearer ${pollA.body.accessToken}`);
    const meB = await request(app).get("/user/me").set("Authorization", `Bearer ${pollB.body.accessToken}`);
    expect(meA.body.email).toBe("devicea@example.com");
    expect(meB.body.email).toBe("deviceb@example.com");
  });
});

describe("the activation page itself", () => {
  it("is served at the exact URL shape the QR code encodes", async () => {
    const response = await request(app).get("/activate").query({ token: "whatever" });
    expect(response.status).toBe(200);
    expect(response.headers["content-type"]).toContain("text/html");
    expect(response.text).toContain("MangoTV");
  });

  it("serves its own script same-origin, so no CORS configuration is needed", async () => {
    const response = await request(app).get("/activate.js");
    expect(response.status).toBe(200);
    expect(response.headers["content-type"]).toContain("javascript");
  });
});
