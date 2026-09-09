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

function validItem(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    providerId: "cinemeta",
    contentId: "tt1234567",
    contentType: "MOVIE",
    title: "Test Movie",
    posterUrl: "https://example.com/poster.jpg",
    backdropUrl: "https://example.com/backdrop.jpg",
    year: 2024,
    rating: 8.1,
    updatedAt: "2025-01-01T00:00:00.000Z",
    ...overrides,
  };
}

describe("GET /user/watchlist", () => {
  it("rejects a request with no Authorization header", async () => {
    const response = await request(app).get("/user/watchlist");
    expect(response.status).toBe(401);
  });

  it("returns an empty list for an account that has never synced a watchlist", async () => {
    const session = await createTestSession();
    const response = await request(app).get("/user/watchlist").set("Authorization", `Bearer ${session.token}`);
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ items: [] });
  });
});

describe("POST /user/watchlist", () => {
  it("rejects a request with no Authorization header", async () => {
    const response = await request(app).post("/user/watchlist").send(validItem());
    expect(response.status).toBe(401);
  });

  it("rejects a body missing required fields", async () => {
    const session = await createTestSession();
    const response = await request(app)
      .post("/user/watchlist")
      .set("Authorization", `Bearer ${session.token}`)
      .send({ title: "Missing everything else" });
    expect(response.status).toBe(400);
  });

  it("rejects an invalid contentType", async () => {
    const session = await createTestSession();
    const response = await request(app)
      .post("/user/watchlist")
      .set("Authorization", `Bearer ${session.token}`)
      .send(validItem({ contentType: "DOCUMENTARY" }));
    expect(response.status).toBe(400);
  });

  it("rejects a non-ISO-8601 updatedAt", async () => {
    const session = await createTestSession();
    const response = await request(app)
      .post("/user/watchlist")
      .set("Authorization", `Bearer ${session.token}`)
      .send(validItem({ updatedAt: "not-a-date" }));
    expect(response.status).toBe(400);
  });

  it("adds a new item and echoes it back with deletedAt null", async () => {
    const session = await createTestSession();
    const item = validItem();
    const response = await request(app).post("/user/watchlist").set("Authorization", `Bearer ${session.token}`).send(item);
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ ...item, deletedAt: null });
  });

  it("a later GET reflects an added item", async () => {
    const session = await createTestSession();
    const item = validItem();
    await request(app).post("/user/watchlist").set("Authorization", `Bearer ${session.token}`).send(item);

    const response = await request(app).get("/user/watchlist").set("Authorization", `Bearer ${session.token}`);
    expect(response.status).toBe(200);
    expect(response.body.items).toEqual([{ ...item, deletedAt: null }]);
  });

  it("adding the same item twice with a strictly newer updatedAt overwrites its metadata", async () => {
    const session = await createTestSession();
    await request(app)
      .post("/user/watchlist")
      .set("Authorization", `Bearer ${session.token}`)
      .send(validItem({ updatedAt: "2025-01-01T00:00:00.000Z", rating: 5 }));

    const newer = validItem({ updatedAt: "2025-01-02T00:00:00.000Z", rating: 9 });
    const response = await request(app).post("/user/watchlist").set("Authorization", `Bearer ${session.token}`).send(newer);

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ ...newer, deletedAt: null });
  });

  it("adding with an older updatedAt does not overwrite: current (newer) state is returned", async () => {
    const session = await createTestSession();
    const current = validItem({ updatedAt: "2025-01-02T00:00:00.000Z", rating: 9 });
    await request(app).post("/user/watchlist").set("Authorization", `Bearer ${session.token}`).send(current);

    const stale = validItem({ updatedAt: "2025-01-01T00:00:00.000Z", rating: 1 });
    const response = await request(app).post("/user/watchlist").set("Authorization", `Bearer ${session.token}`).send(stale);

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ ...current, deletedAt: null });
  });

  it("re-adding a removed item (newer updatedAt) clears deletedAt and it reappears in GET", async () => {
    const session = await createTestSession();
    const auth = { Authorization: `Bearer ${session.token}` };
    await request(app).post("/user/watchlist").set(auth).send(validItem({ updatedAt: "2025-01-01T00:00:00.000Z" }));
    await request(app)
      .delete("/user/watchlist")
      .set(auth)
      .query({ providerId: "cinemeta", contentId: "tt1234567", contentType: "MOVIE", updatedAt: "2025-01-02T00:00:00.000Z" });

    const readded = validItem({ updatedAt: "2025-01-03T00:00:00.000Z", rating: 7 });
    const response = await request(app).post("/user/watchlist").set(auth).send(readded);
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ ...readded, deletedAt: null });

    const list = await request(app).get("/user/watchlist").set(auth);
    expect(list.body.items).toEqual([{ ...readded, deletedAt: null }]);
  });

  it("different providerId + same contentId is a distinct item (natural key includes providerId)", async () => {
    const session = await createTestSession();
    const auth = { Authorization: `Bearer ${session.token}` };
    await request(app).post("/user/watchlist").set(auth).send(validItem({ providerId: "cinemeta" }));
    await request(app).post("/user/watchlist").set(auth).send(validItem({ providerId: "other-addon" }));

    const list = await request(app).get("/user/watchlist").set(auth);
    expect(list.body.items).toHaveLength(2);
  });
});

describe("DELETE /user/watchlist", () => {
  it("rejects a request with no Authorization header", async () => {
    const response = await request(app)
      .delete("/user/watchlist")
      .query({ providerId: "cinemeta", contentId: "tt1234567", contentType: "MOVIE", updatedAt: "2025-01-01T00:00:00.000Z" });
    expect(response.status).toBe(401);
  });

  it("rejects missing/invalid query params", async () => {
    const session = await createTestSession();
    const response = await request(app)
      .delete("/user/watchlist")
      .set("Authorization", `Bearer ${session.token}`)
      .query({ providerId: "cinemeta" });
    expect(response.status).toBe(400);
  });

  it("returns 204 for an item that was never synced (nothing to reconcile)", async () => {
    const session = await createTestSession();
    const response = await request(app)
      .delete("/user/watchlist")
      .set("Authorization", `Bearer ${session.token}`)
      .query({ providerId: "cinemeta", contentId: "never-existed", contentType: "MOVIE", updatedAt: "2025-01-01T00:00:00.000Z" });
    expect(response.status).toBe(204);
    expect(response.body).toEqual({});
  });

  it("removes an existing item: it disappears from a later GET", async () => {
    const session = await createTestSession();
    const auth = { Authorization: `Bearer ${session.token}` };
    await request(app).post("/user/watchlist").set(auth).send(validItem({ updatedAt: "2025-01-01T00:00:00.000Z" }));

    const response = await request(app)
      .delete("/user/watchlist")
      .set(auth)
      .query({ providerId: "cinemeta", contentId: "tt1234567", contentType: "MOVIE", updatedAt: "2025-01-02T00:00:00.000Z" });
    expect(response.status).toBe(200);
    expect(response.body.deletedAt).toBe("2025-01-02T00:00:00.000Z");

    const list = await request(app).get("/user/watchlist").set(auth);
    expect(list.body.items).toEqual([]);
  });

  it("a stale-timestamped remove loses to a newer add: item stays active, response reflects it", async () => {
    const session = await createTestSession();
    const auth = { Authorization: `Bearer ${session.token}` };
    const current = validItem({ updatedAt: "2025-01-02T00:00:00.000Z" });
    await request(app).post("/user/watchlist").set(auth).send(current);

    const response = await request(app)
      .delete("/user/watchlist")
      .set(auth)
      .query({ providerId: "cinemeta", contentId: "tt1234567", contentType: "MOVIE", updatedAt: "2025-01-01T00:00:00.000Z" });
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ ...current, deletedAt: null });

    const list = await request(app).get("/user/watchlist").set(auth);
    expect(list.body.items).toEqual([{ ...current, deletedAt: null }]);
  });

  it("an equal updatedAt does not remove (strictly-greater-than, not greater-or-equal)", async () => {
    const session = await createTestSession();
    const auth = { Authorization: `Bearer ${session.token}` };
    const current = validItem({ updatedAt: "2025-01-01T00:00:00.000Z" });
    await request(app).post("/user/watchlist").set(auth).send(current);

    const response = await request(app)
      .delete("/user/watchlist")
      .set(auth)
      .query({ providerId: "cinemeta", contentId: "tt1234567", contentType: "MOVIE", updatedAt: "2025-01-01T00:00:00.000Z" });
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ ...current, deletedAt: null });
  });
});

describe("cross-user isolation", () => {
  it("one account's watchlist is invisible to, and unaffected by, another's", async () => {
    const alice = await createTestSession({ email: "alice@example.com" });
    const bob = await createTestSession({ email: "bob@example.com" });

    const aliceItem = validItem({ contentId: "alice-movie" });
    await request(app).post("/user/watchlist").set("Authorization", `Bearer ${alice.token}`).send(aliceItem);

    const bobGet = await request(app).get("/user/watchlist").set("Authorization", `Bearer ${bob.token}`);
    expect(bobGet.body).toEqual({ items: [] });

    const bobItem = validItem({ contentId: "bob-movie" });
    await request(app).post("/user/watchlist").set("Authorization", `Bearer ${bob.token}`).send(bobItem);

    const aliceGet = await request(app).get("/user/watchlist").set("Authorization", `Bearer ${alice.token}`);
    expect(aliceGet.body.items).toEqual([{ ...aliceItem, deletedAt: null }]);
  });

  it("bob cannot remove alice's watchlist item by guessing her content id", async () => {
    const alice = await createTestSession({ email: "alice2@example.com" });
    const bob = await createTestSession({ email: "bob2@example.com" });

    const aliceItem = validItem({ contentId: "alice-secret-movie", updatedAt: "2025-01-01T00:00:00.000Z" });
    await request(app).post("/user/watchlist").set("Authorization", `Bearer ${alice.token}`).send(aliceItem);

    // Bob attempts to remove the exact same (providerId, contentId,
    // contentType) with a far-future updatedAt that would win any LWW race.
    const response = await request(app)
      .delete("/user/watchlist")
      .set("Authorization", `Bearer ${bob.token}`)
      .query({ providerId: "cinemeta", contentId: "alice-secret-movie", contentType: "MOVIE", updatedAt: "2099-01-01T00:00:00.000Z" });
    // Scoped to bob's own rows -- no such row exists for bob, so this is
    // the "never existed for this user" case, not a leak of alice's item.
    expect(response.status).toBe(204);

    const aliceGet = await request(app).get("/user/watchlist").set("Authorization", `Bearer ${alice.token}`);
    expect(aliceGet.body.items).toEqual([{ ...aliceItem, deletedAt: null }]);
  });
});
