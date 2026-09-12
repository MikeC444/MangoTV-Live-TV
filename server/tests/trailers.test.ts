import type { Express } from "express";
import request from "supertest";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createApp } from "../src/app.js";
import { resetDatabase } from "./helpers/db.js";
import { createTestSession } from "./helpers/auth.js";

let app: Express;
const fetchMock = vi.fn();

// Every test below uses its own distinct title string -- trailerService's
// in-memory cache is a module-level Map that persists across `it()` blocks
// within this file (nothing here clears it between tests, the same way
// nothing clears TMDB's own real cache between requests), so a shared
// title across two tests expecting different outcomes would leak a
// cached result from one into the other.
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

describe("GET /user/trailer", () => {
  it("rejects a request with no Authorization header", async () => {
    const response = await request(app).get("/user/trailer").query({ title: "Anything", type: "MOVIE" });
    expect(response.status).toBe(401);
  });

  it("rejects a missing title", async () => {
    const session = await createTestSession();
    const response = await request(app)
      .get("/user/trailer")
      .set("Authorization", `Bearer ${session.token}`)
      .query({ type: "MOVIE" });
    expect(response.status).toBe(400);
  });

  it("rejects an invalid type", async () => {
    const session = await createTestSession();
    const response = await request(app)
      .get("/user/trailer")
      .set("Authorization", `Bearer ${session.token}`)
      .query({ title: "Anything", type: "PODCAST" });
    expect(response.status).toBe(400);
  });

  it("returns null without ever calling TMDB when no token is configured", async () => {
    delete process.env.TMDB_READ_ACCESS_TOKEN;
    const session = await createTestSession();
    const response = await request(app)
      .get("/user/trailer")
      .set("Authorization", `Bearer ${session.token}`)
      .query({ title: "Whatever Title Alpha", type: "MOVIE" });

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ youtubeVideoId: null });
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("resolves the best YouTube trailer for a matched movie", async () => {
    fetchMock
      .mockResolvedValueOnce(tmdbResponse({ results: [{ id: 27205 }] }))
      .mockResolvedValueOnce(
        tmdbResponse({
          results: [
            { key: "teaser123", site: "YouTube", type: "Teaser", official: true },
            { key: "trailer456", site: "YouTube", type: "Trailer", official: true },
            { key: "clip789", site: "YouTube", type: "Clip", official: true },
          ],
        })
      );

    const session = await createTestSession();
    const response = await request(app)
      .get("/user/trailer")
      .set("Authorization", `Bearer ${session.token}`)
      .query({ title: "Whatever Title Bravo", year: 2010, type: "MOVIE" });

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ youtubeVideoId: "trailer456" });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    const [searchUrl] = fetchMock.mock.calls[0]!;
    expect(String(searchUrl)).toContain("/search/movie");
    expect(String(searchUrl)).toContain("year=2010");
    const [videosUrl] = fetchMock.mock.calls[1]!;
    expect(String(videosUrl)).toContain("/movie/27205/videos");
  });

  it("prefers a TV show's first_air_date_year search param over year", async () => {
    fetchMock
      .mockResolvedValueOnce(tmdbResponse({ results: [{ id: 999 }] }))
      .mockResolvedValueOnce(tmdbResponse({ results: [{ key: "showtrailer", site: "YouTube", type: "Trailer", official: true }] }));

    const session = await createTestSession();
    const response = await request(app)
      .get("/user/trailer")
      .set("Authorization", `Bearer ${session.token}`)
      .query({ title: "Whatever Title Golf", year: 2015, type: "TV_SHOW" });

    expect(response.body).toEqual({ youtubeVideoId: "showtrailer" });
    const [searchUrl] = fetchMock.mock.calls[0]!;
    expect(String(searchUrl)).toContain("/search/tv");
    expect(String(searchUrl)).toContain("first_air_date_year=2015");
  });

  it("returns null when TMDB finds no matching title, without fetching videos", async () => {
    fetchMock.mockResolvedValueOnce(tmdbResponse({ results: [] }));

    const session = await createTestSession();
    const response = await request(app)
      .get("/user/trailer")
      .set("Authorization", `Bearer ${session.token}`)
      .query({ title: "Whatever Title Charlie", type: "MOVIE" });

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ youtubeVideoId: null });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("returns null when the matched title has no YouTube trailer/teaser", async () => {
    fetchMock
      .mockResolvedValueOnce(tmdbResponse({ results: [{ id: 42 }] }))
      .mockResolvedValueOnce(
        tmdbResponse({ results: [{ key: "clip1", site: "YouTube", type: "Clip", official: true }] })
      );

    const session = await createTestSession();
    const response = await request(app)
      .get("/user/trailer")
      .set("Authorization", `Bearer ${session.token}`)
      .query({ title: "Whatever Title Delta", type: "MOVIE" });

    expect(response.body).toEqual({ youtubeVideoId: null });
  });

  it("caches a result so a second lookup of the same title/year/type doesn't call TMDB again", async () => {
    fetchMock
      .mockResolvedValueOnce(tmdbResponse({ results: [{ id: 1 }] }))
      .mockResolvedValueOnce(
        tmdbResponse({ results: [{ key: "cached1", site: "YouTube", type: "Trailer", official: true }] })
      );

    const session = await createTestSession();
    const query = { title: "Whatever Title Echo", year: 1999, type: "MOVIE" as const };

    const first = await request(app).get("/user/trailer").set("Authorization", `Bearer ${session.token}`).query(query);
    expect(first.body).toEqual({ youtubeVideoId: "cached1" });

    const second = await request(app).get("/user/trailer").set("Authorization", `Bearer ${session.token}`).query(query);
    expect(second.body).toEqual({ youtubeVideoId: "cached1" });

    // Only the first request's two calls -- the second hit the cache.
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("degrades to null instead of throwing when TMDB itself errors", async () => {
    fetchMock.mockResolvedValueOnce({ ok: false, json: async () => ({}) });

    const session = await createTestSession();
    const response = await request(app)
      .get("/user/trailer")
      .set("Authorization", `Bearer ${session.token}`)
      .query({ title: "Whatever Title Foxtrot", type: "TV_SHOW" });

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ youtubeVideoId: null });
  });
});
