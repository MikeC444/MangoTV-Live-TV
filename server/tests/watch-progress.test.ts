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

function movieProgress(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    providerId: "cinemeta",
    contentId: "tt1234567",
    contentType: "MOVIE",
    seasonNumber: null,
    episodeNumber: null,
    episodeTitle: null,
    title: "Test Movie",
    posterUrl: "https://example.com/poster.jpg",
    backdropUrl: "https://example.com/backdrop.jpg",
    positionMs: 60_000,
    durationMs: 6_000_000,
    completed: false,
    watchedAt: "2025-01-01T00:00:00.000Z",
    ...overrides,
  };
}

function episodeProgress(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    providerId: "cinemeta",
    contentId: "tt7654321",
    contentType: "TV_SHOW",
    seasonNumber: 1,
    episodeNumber: 1,
    episodeTitle: "Pilot",
    title: "Test Show",
    posterUrl: "https://example.com/poster.jpg",
    backdropUrl: "https://example.com/backdrop.jpg",
    positionMs: 120_000,
    durationMs: 1_500_000,
    completed: false,
    watchedAt: "2025-01-01T00:00:00.000Z",
    ...overrides,
  };
}

describe("POST /user/watch-progress", () => {
  it("rejects a request with no Authorization header", async () => {
    const response = await request(app).post("/user/watch-progress").send(movieProgress());
    expect(response.status).toBe(401);
  });

  it("rejects a body missing required fields", async () => {
    const session = await createTestSession();
    const response = await request(app)
      .post("/user/watch-progress")
      .set("Authorization", `Bearer ${session.token}`)
      .send({ title: "Missing everything else" });
    expect(response.status).toBe(400);
  });

  it("rejects a half-null season/episode pair", async () => {
    const session = await createTestSession();
    const response = await request(app)
      .post("/user/watch-progress")
      .set("Authorization", `Bearer ${session.token}`)
      .send(episodeProgress({ episodeNumber: null }));
    expect(response.status).toBe(400);
  });

  it("rejects an invalid contentType", async () => {
    const session = await createTestSession();
    const response = await request(app)
      .post("/user/watch-progress")
      .set("Authorization", `Bearer ${session.token}`)
      .send(movieProgress({ contentType: "DOCUMENTARY" }));
    expect(response.status).toBe(400);
  });

  it("rejects a negative positionMs", async () => {
    const session = await createTestSession();
    const response = await request(app)
      .post("/user/watch-progress")
      .set("Authorization", `Bearer ${session.token}`)
      .send(movieProgress({ positionMs: -1 }));
    expect(response.status).toBe(400);
  });

  it("records a movie's progress into both watch_history and continue_watching", async () => {
    const session = await createTestSession();
    const body = movieProgress();
    const response = await request(app).post("/user/watch-progress").set("Authorization", `Bearer ${session.token}`).send(body);

    expect(response.status).toBe(200);
    expect(response.body.historyEntry).toMatchObject({
      providerId: body.providerId,
      contentId: body.contentId,
      contentType: "MOVIE",
      positionMs: body.positionMs,
      durationMs: body.durationMs,
      completed: false,
    });
    expect(response.body.continueWatching).toMatchObject({
      providerId: body.providerId,
      contentId: body.contentId,
      positionMs: body.positionMs,
      deletedAt: null,
    });
  });

  it("a later GET /user/continue-watching reflects the report", async () => {
    const session = await createTestSession();
    const auth = { Authorization: `Bearer ${session.token}` };
    await request(app).post("/user/watch-progress").set(auth).send(movieProgress());

    const response = await request(app).get("/user/continue-watching").set(auth);
    expect(response.status).toBe(200);
    expect(response.body.items).toHaveLength(1);
    expect(response.body.items[0]).toMatchObject({ contentId: "tt1234567", positionMs: 60_000 });
  });

  it("completed=true clears continue_watching but keeps the watch_history row (completed=true)", async () => {
    const session = await createTestSession();
    const auth = { Authorization: `Bearer ${session.token}` };
    await request(app).post("/user/watch-progress").set(auth).send(movieProgress({ watchedAt: "2025-01-01T00:00:00.000Z" }));

    const finish = movieProgress({
      positionMs: 6_000_000,
      completed: true,
      watchedAt: "2025-01-01T02:00:00.000Z",
    });
    const response = await request(app).post("/user/watch-progress").set(auth).send(finish);
    expect(response.status).toBe(200);
    expect(response.body.historyEntry.completed).toBe(true);
    expect(response.body.continueWatching).not.toBeNull();
    expect(response.body.continueWatching.deletedAt).toBe("2025-01-01T02:00:00.000Z");

    const cwList = await request(app).get("/user/continue-watching").set(auth);
    expect(cwList.body.items).toEqual([]);

    const history = await request(app).get("/user/history").set(auth);
    expect(history.body.items).toHaveLength(1);
    expect(history.body.items[0].completed).toBe(true);
  });

  it("completing a title that never had a continue_watching row returns continueWatching: null", async () => {
    const session = await createTestSession();
    const response = await request(app)
      .post("/user/watch-progress")
      .set("Authorization", `Bearer ${session.token}`)
      .send(movieProgress({ completed: true, positionMs: 6_000_000 }));
    expect(response.status).toBe(200);
    expect(response.body.continueWatching).toBeNull();
  });

  it("re-watching after completion (newer watchedAt) recreates an active continue_watching row", async () => {
    const session = await createTestSession();
    const auth = { Authorization: `Bearer ${session.token}` };
    await request(app)
      .post("/user/watch-progress")
      .set(auth)
      .send(movieProgress({ completed: true, positionMs: 6_000_000, watchedAt: "2025-01-01T00:00:00.000Z" }));

    const rewatch = movieProgress({ positionMs: 30_000, completed: false, watchedAt: "2025-01-02T00:00:00.000Z" });
    const response = await request(app).post("/user/watch-progress").set(auth).send(rewatch);
    expect(response.status).toBe(200);
    expect(response.body.continueWatching).toMatchObject({ positionMs: 30_000, deletedAt: null });

    const cwList = await request(app).get("/user/continue-watching").set(auth);
    expect(cwList.body.items).toHaveLength(1);
  });

  it("switching episodes on the same show updates the one continue_watching row but creates a second watch_history row", async () => {
    const session = await createTestSession();
    const auth = { Authorization: `Bearer ${session.token}` };
    await request(app).post("/user/watch-progress").set(auth).send(episodeProgress({ watchedAt: "2025-01-01T00:00:00.000Z" }));
    await request(app)
      .post("/user/watch-progress")
      .set(auth)
      .send(episodeProgress({ seasonNumber: 1, episodeNumber: 2, episodeTitle: "Episode 2", watchedAt: "2025-01-01T01:00:00.000Z" }));

    const cwList = await request(app).get("/user/continue-watching").set(auth);
    expect(cwList.body.items).toHaveLength(1);
    expect(cwList.body.items[0].episodeNumber).toBe(2);

    const history = await request(app).get("/user/history").set(auth);
    expect(history.body.items).toHaveLength(2);
  });

  it("a strictly newer watchedAt overwrites; an older one loses to the current state", async () => {
    const session = await createTestSession();
    const auth = { Authorization: `Bearer ${session.token}` };
    await request(app)
      .post("/user/watch-progress")
      .set(auth)
      .send(movieProgress({ positionMs: 100_000, watchedAt: "2025-01-02T00:00:00.000Z" }));

    const stale = movieProgress({ positionMs: 1_000, watchedAt: "2025-01-01T00:00:00.000Z" });
    const response = await request(app).post("/user/watch-progress").set(auth).send(stale);
    expect(response.status).toBe(200);
    expect(response.body.historyEntry.positionMs).toBe(100_000);
    expect(response.body.continueWatching.positionMs).toBe(100_000);
  });
});

describe("GET /user/history", () => {
  it("rejects a request with no Authorization header", async () => {
    const response = await request(app).get("/user/history");
    expect(response.status).toBe(401);
  });

  it("returns an empty list for an account with no history", async () => {
    const session = await createTestSession();
    const response = await request(app).get("/user/history").set("Authorization", `Bearer ${session.token}`);
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ items: [] });
  });

  it("rejects an out-of-range limit", async () => {
    const session = await createTestSession();
    const response = await request(app)
      .get("/user/history")
      .set("Authorization", `Bearer ${session.token}`)
      .query({ limit: "9999" });
    expect(response.status).toBe(400);
  });

  it("paginates with limit and before", async () => {
    const session = await createTestSession();
    const auth = { Authorization: `Bearer ${session.token}` };
    for (let i = 0; i < 5; i++) {
      await request(app)
        .post("/user/watch-progress")
        .set(auth)
        .send(
          movieProgress({
            contentId: `movie-${i}`,
            watchedAt: `2025-01-0${i + 1}T00:00:00.000Z`,
          })
        );
    }

    const firstPage = await request(app).get("/user/history").set(auth).query({ limit: 2 });
    expect(firstPage.body.items).toHaveLength(2);
    // Newest first.
    expect(firstPage.body.items[0].contentId).toBe("movie-4");
    expect(firstPage.body.items[1].contentId).toBe("movie-3");

    const secondPage = await request(app)
      .get("/user/history")
      .set(auth)
      .query({ limit: 2, before: firstPage.body.items[1].watchedAt });
    expect(secondPage.body.items).toHaveLength(2);
    expect(secondPage.body.items[0].contentId).toBe("movie-2");
    expect(secondPage.body.items[1].contentId).toBe("movie-1");
  });
});

describe("GET /user/continue-watching", () => {
  it("rejects a request with no Authorization header", async () => {
    const response = await request(app).get("/user/continue-watching");
    expect(response.status).toBe(401);
  });

  it("returns an empty list for an account with nothing in progress", async () => {
    const session = await createTestSession();
    const response = await request(app).get("/user/continue-watching").set("Authorization", `Bearer ${session.token}`);
    expect(response.status).toBe(200);
    expect(response.body).toEqual({ items: [] });
  });
});

describe("cross-user isolation", () => {
  it("one account's watch progress is invisible to, and unaffected by, another's", async () => {
    const alice = await createTestSession({ email: "alice@example.com" });
    const bob = await createTestSession({ email: "bob@example.com" });

    await request(app)
      .post("/user/watch-progress")
      .set("Authorization", `Bearer ${alice.token}`)
      .send(movieProgress({ contentId: "alice-movie" }));

    const bobHistory = await request(app).get("/user/history").set("Authorization", `Bearer ${bob.token}`);
    expect(bobHistory.body.items).toEqual([]);
    const bobContinueWatching = await request(app).get("/user/continue-watching").set("Authorization", `Bearer ${bob.token}`);
    expect(bobContinueWatching.body.items).toEqual([]);

    const aliceHistory = await request(app).get("/user/history").set("Authorization", `Bearer ${alice.token}`);
    expect(aliceHistory.body.items).toHaveLength(1);
  });

  it("bob cannot mark alice's title completed by guessing her exact content id", async () => {
    const alice = await createTestSession({ email: "alice2@example.com" });
    const bob = await createTestSession({ email: "bob2@example.com" });

    await request(app)
      .post("/user/watch-progress")
      .set("Authorization", `Bearer ${alice.token}`)
      .send(movieProgress({ contentId: "alice-secret-movie", watchedAt: "2025-01-01T00:00:00.000Z" }));

    await request(app)
      .post("/user/watch-progress")
      .set("Authorization", `Bearer ${bob.token}`)
      .send(
        movieProgress({
          contentId: "alice-secret-movie",
          completed: true,
          positionMs: 6_000_000,
          watchedAt: "2099-01-01T00:00:00.000Z",
        })
      );

    // Bob's own report created his own row (scoped to his user_id) --
    // alice's is untouched.
    const aliceCw = await request(app).get("/user/continue-watching").set("Authorization", `Bearer ${alice.token}`);
    expect(aliceCw.body.items).toHaveLength(1);
    expect(aliceCw.body.items[0].contentId).toBe("alice-secret-movie");
  });
});
