import { randomUUID } from "node:crypto";
import type { Express } from "express";
import request from "supertest";
import { beforeEach, describe, expect, it } from "vitest";
import { createApp } from "../src/app.js";
import { pool } from "../src/db/pool.js";
import { resetDatabase } from "./helpers/db.js";

// Fresh app per test — see tests/me.test.ts's comment on why (the auth
// rate limiter's counters must not leak between tests).
let app: Express;

beforeEach(async () => {
  app = createApp();
  await resetDatabase();
});

describe("POST /auth/register", () => {
  it("creates an account and returns usable tokens", async () => {
    const deviceId = randomUUID();
    const response = await request(app)
      .post("/auth/register")
      .send({ email: "Alice@Example.com", password: "correct-horse-battery", deviceId, deviceName: "Living Room" });

    expect(response.status).toBe(201);
    expect(response.body.user.email).toBe("alice@example.com"); // normalized to lowercase
    expect(response.body.accessToken).toBeTruthy();
    expect(response.body.refreshToken).toBeTruthy();
    // Different secrets, not a mistaken copy of the same value.
    expect(response.body.accessToken).not.toBe(response.body.refreshToken);

    const me = await request(app).get("/user/me").set("Authorization", `Bearer ${response.body.accessToken}`);
    expect(me.status).toBe(200);
    expect(me.body.id).toBe(response.body.user.id);
  });

  it("never stores the plaintext password", async () => {
    const response = await request(app)
      .post("/auth/register")
      .send({ email: "bob@example.com", password: "correct-horse-battery", deviceId: randomUUID() });
    expect(response.status).toBe(201);

    const row = await pool.query("SELECT password_hash FROM users WHERE id = $1", [response.body.user.id]);
    const hash: string = row.rows[0].password_hash;
    expect(hash).not.toBe("correct-horse-battery");
    expect(hash.startsWith("$argon2id$")).toBe(true);
  });

  it("rejects a duplicate email with 409, without ever touching the first account", async () => {
    const deviceId = randomUUID();
    await request(app).post("/auth/register").send({ email: "dup@example.com", password: "correct-horse-battery", deviceId });

    const second = await request(app)
      .post("/auth/register")
      .send({ email: "dup@example.com", password: "a-different-password", deviceId: randomUUID() });

    expect(second.status).toBe(409);

    const count = await pool.query("SELECT count(*)::int AS n FROM users WHERE email = $1", ["dup@example.com"]);
    expect(count.rows[0].n).toBe(1);
  });

  it("rejects a password shorter than 8 characters", async () => {
    const response = await request(app)
      .post("/auth/register")
      .send({ email: "short@example.com", password: "short", deviceId: randomUUID() });
    expect(response.status).toBe(400);
  });

  it("rejects an invalid email", async () => {
    const response = await request(app)
      .post("/auth/register")
      .send({ email: "not-an-email", password: "correct-horse-battery", deviceId: randomUUID() });
    expect(response.status).toBe(400);
  });

  it("rejects a non-UUID deviceId", async () => {
    const response = await request(app)
      .post("/auth/register")
      .send({ email: "device@example.com", password: "correct-horse-battery", deviceId: "not-a-uuid" });
    expect(response.status).toBe(400);
  });
});
