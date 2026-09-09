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

async function registerUser(email: string, password: string) {
  return request(app).post("/auth/register").send({ email, password, deviceId: randomUUID() });
}

describe("POST /auth/login", () => {
  it("logs in with correct credentials and returns fresh tokens for the requesting device", async () => {
    await registerUser("carol@example.com", "correct-horse-battery");

    const response = await request(app)
      .post("/auth/login")
      .send({ email: "carol@example.com", password: "correct-horse-battery", deviceId: randomUUID() });

    expect(response.status).toBe(200);
    expect(response.body.user.email).toBe("carol@example.com");
    expect(response.body.accessToken).toBeTruthy();
  });

  it("is case-insensitive on email", async () => {
    await registerUser("dana@example.com", "correct-horse-battery");
    const response = await request(app)
      .post("/auth/login")
      .send({ email: "DANA@EXAMPLE.COM", password: "correct-horse-battery", deviceId: randomUUID() });
    expect(response.status).toBe(200);
  });

  it("rejects an unknown email with the same message as a wrong password (no enumeration)", async () => {
    await registerUser("erin@example.com", "correct-horse-battery");

    const wrongPassword = await request(app)
      .post("/auth/login")
      .send({ email: "erin@example.com", password: "totally-wrong", deviceId: randomUUID() });
    const unknownEmail = await request(app)
      .post("/auth/login")
      .send({ email: "nobody@example.com", password: "totally-wrong", deviceId: randomUUID() });

    expect(wrongPassword.status).toBe(401);
    expect(unknownEmail.status).toBe(401);
    expect(wrongPassword.body.error).toBe(unknownEmail.body.error);
  });

  it("a second device gets its own independent session alongside the first", async () => {
    await registerUser("frank@example.com", "correct-horse-battery");

    const deviceA = await request(app)
      .post("/auth/login")
      .send({ email: "frank@example.com", password: "correct-horse-battery", deviceId: randomUUID(), deviceName: "Bedroom" });
    const deviceB = await request(app)
      .post("/auth/login")
      .send({ email: "frank@example.com", password: "correct-horse-battery", deviceId: randomUUID(), deviceName: "Living Room" });

    expect(deviceA.body.accessToken).not.toBe(deviceB.body.accessToken);

    const meA = await request(app).get("/user/me").set("Authorization", `Bearer ${deviceA.body.accessToken}`);
    const meB = await request(app).get("/user/me").set("Authorization", `Bearer ${deviceB.body.accessToken}`);
    expect(meA.status).toBe(200);
    expect(meB.status).toBe(200);
  });
});
