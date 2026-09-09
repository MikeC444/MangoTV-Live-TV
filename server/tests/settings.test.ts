import type { Express } from "express";
import request from "supertest";
import { beforeEach, describe, expect, it } from "vitest";
import { createApp } from "../src/app.js";
import { resetDatabase } from "./helpers/db.js";
import { createTestSession } from "./helpers/auth.js";

let app: Express;

beforeEach(async () => {
  app = createApp();
  await resetDatabase();
});

function validBody(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    homeRowOrder: ["featured", "trending"],
    hiddenRowIds: ["horror"],
    autoplayNextEpisode: false,
    skipIntroEnabled: true,
    updatedAt: "2025-01-01T00:00:00.000Z",
    ...overrides,
  };
}

describe("GET /user/settings", () => {
  it("rejects a request with no Authorization header", async () => {
    const response = await request(app).get("/user/settings");
    expect(response.status).toBe(401);
  });

  it("returns column defaults for an account that has never synced settings", async () => {
    const session = await createTestSession();
    const response = await request(app).get("/user/settings").set("Authorization", `Bearer ${session.token}`);
    expect(response.status).toBe(200);
    expect(response.body).toEqual({
      homeRowOrder: [],
      hiddenRowIds: [],
      autoplayNextEpisode: true,
      skipIntroEnabled: true,
      updatedAt: null,
    });
  });
});

describe("PUT /user/settings", () => {
  it("rejects a request with no Authorization header", async () => {
    const response = await request(app).put("/user/settings").send(validBody());
    expect(response.status).toBe(401);
  });

  it("rejects a body missing required fields", async () => {
    const session = await createTestSession();
    const response = await request(app)
      .put("/user/settings")
      .set("Authorization", `Bearer ${session.token}`)
      .send({ autoplayNextEpisode: true });
    expect(response.status).toBe(400);
  });

  it("rejects a non-ISO-8601 updatedAt", async () => {
    const session = await createTestSession();
    const response = await request(app)
      .put("/user/settings")
      .set("Authorization", `Bearer ${session.token}`)
      .send(validBody({ updatedAt: "not-a-date" }));
    expect(response.status).toBe(400);
  });

  it("rejects wrong-typed fields (homeRowOrder as a string instead of an array)", async () => {
    const session = await createTestSession();
    const response = await request(app)
      .put("/user/settings")
      .set("Authorization", `Bearer ${session.token}`)
      .send(validBody({ homeRowOrder: "featured" }));
    expect(response.status).toBe(400);
  });

  it("creates the row on a brand new account's first push, and echoes it back", async () => {
    const session = await createTestSession();
    const body = validBody();
    const response = await request(app).put("/user/settings").set("Authorization", `Bearer ${session.token}`).send(body);
    expect(response.status).toBe(200);
    expect(response.body).toEqual(body);
  });

  it("a later GET reflects what was pushed", async () => {
    const session = await createTestSession();
    const body = validBody();
    await request(app).put("/user/settings").set("Authorization", `Bearer ${session.token}`).send(body);

    const response = await request(app).get("/user/settings").set("Authorization", `Bearer ${session.token}`);
    expect(response.status).toBe(200);
    expect(response.body).toEqual(body);
  });

  it("a strictly newer updatedAt overwrites the stored settings", async () => {
    const session = await createTestSession();
    await request(app)
      .put("/user/settings")
      .set("Authorization", `Bearer ${session.token}`)
      .send(validBody({ updatedAt: "2025-01-01T00:00:00.000Z", autoplayNextEpisode: false }));

    const newer = validBody({ updatedAt: "2025-01-02T00:00:00.000Z", autoplayNextEpisode: true });
    const response = await request(app).put("/user/settings").set("Authorization", `Bearer ${session.token}`).send(newer);

    expect(response.status).toBe(200);
    expect(response.body).toEqual(newer);
  });

  it("an older updatedAt is rejected: the still-current (newer) settings are returned, not the stale write", async () => {
    const session = await createTestSession();
    const current = validBody({ updatedAt: "2025-01-02T00:00:00.000Z", autoplayNextEpisode: true });
    await request(app).put("/user/settings").set("Authorization", `Bearer ${session.token}`).send(current);

    const stale = validBody({ updatedAt: "2025-01-01T00:00:00.000Z", autoplayNextEpisode: false });
    const response = await request(app).put("/user/settings").set("Authorization", `Bearer ${session.token}`).send(stale);

    expect(response.status).toBe(200);
    expect(response.body).toEqual(current);

    const getResponse = await request(app).get("/user/settings").set("Authorization", `Bearer ${session.token}`);
    expect(getResponse.body).toEqual(current);
  });

  it("an equal updatedAt does not overwrite (strictly-greater-than, not greater-or-equal)", async () => {
    const session = await createTestSession();
    const first = validBody({ updatedAt: "2025-01-01T00:00:00.000Z", autoplayNextEpisode: true });
    await request(app).put("/user/settings").set("Authorization", `Bearer ${session.token}`).send(first);

    const sameTimestamp = validBody({ updatedAt: "2025-01-01T00:00:00.000Z", autoplayNextEpisode: false });
    const response = await request(app)
      .put("/user/settings")
      .set("Authorization", `Bearer ${session.token}`)
      .send(sameTimestamp);

    expect(response.status).toBe(200);
    expect(response.body).toEqual(first);
  });
});

describe("cross-user isolation", () => {
  it("one account's settings are invisible to, and unaffected by, another's", async () => {
    const alice = await createTestSession({ email: "alice@example.com" });
    const bob = await createTestSession({ email: "bob@example.com" });

    const aliceSettings = validBody({ updatedAt: "2025-01-01T00:00:00.000Z", autoplayNextEpisode: false });
    await request(app).put("/user/settings").set("Authorization", `Bearer ${alice.token}`).send(aliceSettings);

    const bobGet = await request(app).get("/user/settings").set("Authorization", `Bearer ${bob.token}`);
    expect(bobGet.body).toEqual({
      homeRowOrder: [],
      hiddenRowIds: [],
      autoplayNextEpisode: true,
      skipIntroEnabled: true,
      updatedAt: null,
    });

    const bobSettings = validBody({ updatedAt: "2025-01-01T00:00:00.000Z", autoplayNextEpisode: true, homeRowOrder: ["comedy"] });
    await request(app).put("/user/settings").set("Authorization", `Bearer ${bob.token}`).send(bobSettings);

    const aliceGet = await request(app).get("/user/settings").set("Authorization", `Bearer ${alice.token}`);
    expect(aliceGet.body).toEqual(aliceSettings);
  });
});
