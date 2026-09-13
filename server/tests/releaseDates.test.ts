import type { Express } from "express";
import request from "supertest";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createApp } from "../src/app.js";
import { resetDatabase } from "./helpers/db.js";
import { createTestSession } from "./helpers/auth.js";

let app: Express;
const fetchMock = vi.fn();

// Same reasoning as trailers.test.ts: releaseDateService's in-memory cache
// is a module-level Map that persists across it() blocks in this file, so
// each test below uses its own distinct title.
beforeEach(async () => {
  app = createApp();
  await resetDatabase();
  vi.stubGlobal("fetch", fetchMock);
  process.env.TMDB_READ_ACCESS_TOKEN = "test-token";
});

afterEach(() => {
  vi.unstubAllGlobals();
  fetchMock.mockReset();
  delete process.env.TMDB_READ_ACCESS_TOKEN;
});

function tmdbResponse(body: unknown, ok = true) {
  return { ok, json: async () => body };
}

describe("GET /user/release-date", () => {
  it("rejects a request with no Authorization header", async () => {
    const response = await request(app).get("/user/release-date").query({ title: "Anything" });
    expect(response.status).toBe(401);
  });

  it("rejects a missing title", async () => {
    const session = await createTestSession();
    const response = await request(app)
      .get("/user/release-date")
      .set("Authorization", `Bearer ${session.token}`)
      .query({});
    expect(response.status).toBe(400);
  });

  it("returns null without ever calling TMDB when no token is configured", async () => {
    delete process.env.TMDB_READ_ACCESS_TOKEN;
    const session = await createTestSession();
    const response = await request(app)
      .get("/user/release-date")
      .set("Authorization", `Bearer ${session.token}`)
      .query({ title: "Whatever Title Alpha" });

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ releaseDate: null });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("resolves the release date for a matched movie", async () => {
    fetchMock.mockResolvedValueOnce(tmdbResponse({ results: [{ release_date: "2025-03-14" }] }));

    const session = await createTestSession();
    const response = await request(app)
      .get("/user/release-date")
      .set("Authorization", `Bearer ${session.token}`)
      .query({ title: "Whatever Title Bravo", year: 2025 });

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ releaseDate: "2025-03-14" });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [searchUrl] = fetchMock.mock.calls[0]!;
    expect(String(searchUrl)).toContain("/search/movie");
    expect(String(searchUrl)).toContain("year=2025");
  });

  it("returns null when TMDB finds no matching title", async () => {
    fetchMock.mockResolvedValueOnce(tmdbResponse({ results: [] }));

    const session = await createTestSession();
    const response = await request(app)
      .get("/user/release-date")
      .set("Authorization", `Bearer ${session.token}`)
      .query({ title: "Whatever Title Charlie" });

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ releaseDate: null });
  });

  it("caches a result so a second lookup of the same title/year doesn't call TMDB again", async () => {
    fetchMock.mockResolvedValueOnce(tmdbResponse({ results: [{ release_date: "1999-12-25" }] }));

    const session = await createTestSession();
    const query = { title: "Whatever Title Echo", year: 1999 };

    const first = await request(app).get("/user/release-date").set("Authorization", `Bearer ${session.token}`).query(query);
    expect(first.body).toEqual({ releaseDate: "1999-12-25" });

    const second = await request(app).get("/user/release-date").set("Authorization", `Bearer ${session.token}`).query(query);
    expect(second.body).toEqual({ releaseDate: "1999-12-25" });

    // Only the first request called TMDB -- the second hit the cache.
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("degrades to null instead of throwing when TMDB itself errors", async () => {
    fetchMock.mockResolvedValueOnce({ ok: false, json: async () => ({}) });

    const session = await createTestSession();
    const response = await request(app)
      .get("/user/release-date")
      .set("Authorization", `Bearer ${session.token}`)
      .query({ title: "Whatever Title Foxtrot" });

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ releaseDate: null });
  });
});
