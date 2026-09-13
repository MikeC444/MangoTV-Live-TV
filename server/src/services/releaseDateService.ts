import { getTmdbReadAccessToken } from "../config/env.js";

const TMDB_BASE_URL = "https://api.themoviedb.org/3";

// Same rationale as trailerService's own CACHE_TTL_MS: one TMDB key backs
// every account (see server/.env.example), so keeping actual TMDB traffic
// low matters here more than for a typical single-tenant integration.
const CACHE_TTL_MS = 24 * 60 * 60 * 1000;

export interface ReleaseDateResult {
  releaseDate: string;
}

interface TmdbSearchResult {
  release_date?: string;
}

interface TmdbSearchResponse {
  results?: TmdbSearchResult[];
}

// In-memory only, same tradeoff as trailerService's own cache: losing this
// on a restart just means the next request for that title re-asks TMDB
// once, a fine cost for a value this cheap to recompute.
const cache = new Map<string, { result: ReleaseDateResult | null; expiresAt: number }>();

function cacheKey(title: string, year: number | undefined): string {
  return `${title.trim().toLowerCase()}:${year ?? ""}`;
}

async function tmdbGet<T>(path: string, token: string): Promise<T | null> {
  const response = await fetch(`${TMDB_BASE_URL}${path}`, {
    headers: { Authorization: `Bearer ${token}`, Accept: "application/json" },
  });
  if (!response.ok) return null;
  return (await response.json()) as T;
}

async function lookUp(title: string, year: number | undefined): Promise<ReleaseDateResult | null> {
  const token = getTmdbReadAccessToken();
  if (!token) return null;

  try {
    const searchParams = new URLSearchParams({ query: title });
    if (year !== undefined) {
      searchParams.set("year", String(year));
    }
    const search = await tmdbGet<TmdbSearchResponse>(`/search/movie?${searchParams.toString()}`, token);
    const releaseDate = search?.results?.[0]?.release_date;
    return releaseDate ? { releaseDate } : null;
  } catch {
    // A release date is always a nice-to-have on top of the addon-supplied
    // year, never allowed to break loading a Detail page -- same reasoning
    // as trailerService's own lookUp.
    return null;
  }
}

/**
 * Looks up a movie's real release date via TMDB (addon catalogs only ever
 * supply a bare year), caching both hits and misses in memory for
 * CACHE_TTL_MS so the same title doesn't re-query TMDB every time any
 * user's Detail page opens it. Movies only, for now.
 *
 * Deliberately a separate lookup/cache from trailerService's findTrailer
 * rather than merged into it: a title can have a release date on TMDB with
 * no qualifying trailer (or vice versa -- see trailerService's own
 * pickBestVideo), so coupling the two would silently drop one whenever the
 * other comes back empty.
 */
export async function findReleaseDate(title: string, year: number | undefined): Promise<ReleaseDateResult | null> {
  const key = cacheKey(title, year);
  const cached = cache.get(key);
  if (cached && cached.expiresAt > Date.now()) return cached.result;

  const result = await lookUp(title, year);
  cache.set(key, { result, expiresAt: Date.now() + CACHE_TTL_MS });
  return result;
}
