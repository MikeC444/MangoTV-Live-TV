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

function cinemeta(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    manifestUrl: "https://v3-cinemeta.strem.io/manifest.json",
    addonId: "com.linvo.cinemeta",
    name: "Cinemeta",
    manifestJson: { id: "com.linvo.cinemeta", name: "Cinemeta", version: "3.0.0", types: ["movie", "series"] },
    enabled: true,
    sortOrder: 0,
    updatedAt: "2025-01-01T00:00:00.000Z",
    ...overrides,
  };
}

describe("GET /user/addons", () => {
  it("rejects a request with no Authorization header", async () => {
    const response = await request(app).get("/user/addons");
    expect(response.status).toBe(401);
  });

  it("returns an empty list for an account that has never synced addons", async () => {
    const session = await createTestSession();
    const response = await request(app).get("/user/addons").set("Authorization", `Bearer ${session.token}`);
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ items: [] });
  });
});

describe("POST /user/addons", () => {
  it("rejects a request with no Authorization header", async () => {
    const response = await request(app).post("/user/addons").send(cinemeta());
    expect(response.status).toBe(401);
  });

  it("rejects a body missing required fields", async () => {
    const session = await createTestSession();
    const response = await request(app)
      .post("/user/addons")
      .set("Authorization", `Bearer ${session.token}`)
      .send({ name: "Missing everything else" });
    expect(response.status).toBe(400);
  });

  it("rejects manifestJson that isn't a JSON object", async () => {
    const session = await createTestSession();
    const response = await request(app)
      .post("/user/addons")
      .set("Authorization", `Bearer ${session.token}`)
      .send(cinemeta({ manifestJson: "not-an-object" }));
    expect(response.status).toBe(400);
  });

  it("rejects a non-ISO-8601 updatedAt", async () => {
    const session = await createTestSession();
    const response = await request(app)
      .post("/user/addons")
      .set("Authorization", `Bearer ${session.token}`)
      .send(cinemeta({ updatedAt: "not-a-date" }));
    expect(response.status).toBe(400);
  });

  it("installs a new addon and echoes it back, manifestJson round-tripping intact", async () => {
    const session = await createTestSession();
    const body = cinemeta();
    const response = await request(app).post("/user/addons").set("Authorization", `Bearer ${session.token}`).send(body);
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ ...body, deletedAt: null });
  });

  it("a later GET reflects an installed addon", async () => {
    const session = await createTestSession();
    const body = cinemeta();
    await request(app).post("/user/addons").set("Authorization", `Bearer ${session.token}`).send(body);

    const response = await request(app).get("/user/addons").set("Authorization", `Bearer ${session.token}`);
    expect(response.status).toBe(200);
    expect(response.body.items).toEqual([{ ...body, deletedAt: null }]);
  });

  it("GET orders addons by sortOrder", async () => {
    const session = await createTestSession();
    const auth = { Authorization: `Bearer ${session.token}` };
    await request(app)
      .post("/user/addons")
      .set(auth)
      .send(cinemeta({ manifestUrl: "https://addon-b.example/manifest.json", addonId: "b", sortOrder: 1 }));
    await request(app)
      .post("/user/addons")
      .set(auth)
      .send(cinemeta({ manifestUrl: "https://addon-a.example/manifest.json", addonId: "a", sortOrder: 0 }));

    const response = await request(app).get("/user/addons").set(auth);
    expect(response.body.items.map((item: { addonId: string }) => item.addonId)).toEqual(["a", "b"]);
  });

  it("toggling enabled with a strictly newer updatedAt overwrites", async () => {
    const session = await createTestSession();
    const auth = { Authorization: `Bearer ${session.token}` };
    await request(app).post("/user/addons").set(auth).send(cinemeta({ updatedAt: "2025-01-01T00:00:00.000Z", enabled: true }));

    const disabled = cinemeta({ updatedAt: "2025-01-02T00:00:00.000Z", enabled: false });
    const response = await request(app).post("/user/addons").set(auth).send(disabled);
    expect(response.status).toBe(200);
    expect(response.body.enabled).toBe(false);
  });

  it("an older updatedAt does not overwrite: current (newer) state is returned", async () => {
    const session = await createTestSession();
    const auth = { Authorization: `Bearer ${session.token}` };
    const current = cinemeta({ updatedAt: "2025-01-02T00:00:00.000Z", enabled: false });
    await request(app).post("/user/addons").set(auth).send(current);

    const stale = cinemeta({ updatedAt: "2025-01-01T00:00:00.000Z", enabled: true });
    const response = await request(app).post("/user/addons").set(auth).send(stale);
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ ...current, deletedAt: null });
  });

  it("re-installing a removed addon (newer updatedAt) clears deletedAt and it reappears in GET", async () => {
    const session = await createTestSession();
    const auth = { Authorization: `Bearer ${session.token}` };
    await request(app).post("/user/addons").set(auth).send(cinemeta({ updatedAt: "2025-01-01T00:00:00.000Z" }));
    await request(app)
      .delete("/user/addons")
      .set(auth)
      .query({ manifestUrl: cinemeta().manifestUrl, updatedAt: "2025-01-02T00:00:00.000Z" });

    const reinstalled = cinemeta({ updatedAt: "2025-01-03T00:00:00.000Z" });
    const response = await request(app).post("/user/addons").set(auth).send(reinstalled);
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ ...reinstalled, deletedAt: null });

    const list = await request(app).get("/user/addons").set(auth);
    expect(list.body.items).toEqual([{ ...reinstalled, deletedAt: null }]);
  });
});

describe("DELETE /user/addons", () => {
  it("rejects a request with no Authorization header", async () => {
    const response = await request(app)
      .delete("/user/addons")
      .query({ manifestUrl: cinemeta().manifestUrl, updatedAt: "2025-01-01T00:00:00.000Z" });
    expect(response.status).toBe(401);
  });

  it("rejects missing/invalid query params", async () => {
    const session = await createTestSession();
    const response = await request(app)
      .delete("/user/addons")
      .set("Authorization", `Bearer ${session.token}`)
      .query({});
    expect(response.status).toBe(400);
  });

  it("returns 204 for an addon that was never synced (nothing to reconcile)", async () => {
    const session = await createTestSession();
    const response = await request(app)
      .delete("/user/addons")
      .set("Authorization", `Bearer ${session.token}`)
      .query({ manifestUrl: "https://never-installed.example/manifest.json", updatedAt: "2025-01-01T00:00:00.000Z" });
    expect(response.status).toBe(204);
    expect(response.body).toEqual({});
  });

  it("removes an installed addon: it disappears from a later GET", async () => {
    const session = await createTestSession();
    const auth = { Authorization: `Bearer ${session.token}` };
    await request(app).post("/user/addons").set(auth).send(cinemeta({ updatedAt: "2025-01-01T00:00:00.000Z" }));

    const response = await request(app)
      .delete("/user/addons")
      .set(auth)
      .query({ manifestUrl: cinemeta().manifestUrl, updatedAt: "2025-01-02T00:00:00.000Z" });
    expect(response.status).toBe(200);
    expect(response.body.deletedAt).toBe("2025-01-02T00:00:00.000Z");

    const list = await request(app).get("/user/addons").set(auth);
    expect(list.body.items).toEqual([]);
  });

  it("a stale-timestamped remove loses to a newer install: addon stays active", async () => {
    const session = await createTestSession();
    const auth = { Authorization: `Bearer ${session.token}` };
    const current = cinemeta({ updatedAt: "2025-01-02T00:00:00.000Z" });
    await request(app).post("/user/addons").set(auth).send(current);

    const response = await request(app)
      .delete("/user/addons")
      .set(auth)
      .query({ manifestUrl: cinemeta().manifestUrl, updatedAt: "2025-01-01T00:00:00.000Z" });
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ ...current, deletedAt: null });

    const list = await request(app).get("/user/addons").set(auth);
    expect(list.body.items).toEqual([{ ...current, deletedAt: null }]);
  });
});

describe("cross-user isolation", () => {
  it("one account's addons are invisible to, and unaffected by, another's", async () => {
    const alice = await createTestSession({ email: "alice@example.com" });
    const bob = await createTestSession({ email: "bob@example.com" });

    const aliceAddon = cinemeta({ manifestUrl: "https://alice-addon.example/manifest.json" });
    await request(app).post("/user/addons").set("Authorization", `Bearer ${alice.token}`).send(aliceAddon);

    const bobGet = await request(app).get("/user/addons").set("Authorization", `Bearer ${bob.token}`);
    expect(bobGet.body).toEqual({ items: [] });

    const bobAddon = cinemeta({ manifestUrl: "https://bob-addon.example/manifest.json" });
    await request(app).post("/user/addons").set("Authorization", `Bearer ${bob.token}`).send(bobAddon);

    const aliceGet = await request(app).get("/user/addons").set("Authorization", `Bearer ${alice.token}`);
    expect(aliceGet.body.items).toEqual([{ ...aliceAddon, deletedAt: null }]);
  });

  it("bob cannot remove alice's addon by guessing her exact manifestUrl", async () => {
    const alice = await createTestSession({ email: "alice2@example.com" });
    const bob = await createTestSession({ email: "bob2@example.com" });

    const aliceAddon = cinemeta({ updatedAt: "2025-01-01T00:00:00.000Z" });
    await request(app).post("/user/addons").set("Authorization", `Bearer ${alice.token}`).send(aliceAddon);

    const response = await request(app)
      .delete("/user/addons")
      .set("Authorization", `Bearer ${bob.token}`)
      .query({ manifestUrl: cinemeta().manifestUrl, updatedAt: "2099-01-01T00:00:00.000Z" });
    // Scoped to bob's own rows -- no such row exists for bob.
    expect(response.status).toBe(204);

    const aliceGet = await request(app).get("/user/addons").set("Authorization", `Bearer ${alice.token}`);
    expect(aliceGet.body.items).toEqual([{ ...aliceAddon, deletedAt: null }]);
  });
});
