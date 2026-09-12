import { getTmdbReadAccessToken } from "../config/env.js";

const TMDB_BASE_URL = "https://api.themoviedb.org/3";

// Trailer availability for a given title essentially never changes day
// to day, so there's no reason to re-ask TMDB more often than this --
// keeping actual TMDB traffic low matters more here than for a typical
// single-tenant integration, since this is the one API key backing every
// account (see Settings' own "no per-user setup" design for this
// feature), not a key each user brings themselves.
const CACHE_TTL_MS = 24 * 60 * 60 * 1000;

export type TmdbMediaType = "movie" | "tv";

export interface TrailerResult {
  youtubeVideoId: string;
}

interface TmdbSearchResult {
  id: number;
}

interface TmdbSearchResponse {
  results?: TmdbSearchResult[];
}

interface TmdbVideo {
  key: string;
  site: string;
  type: string;
  official: boolean;
}

interface TmdbVideosResponse {
  results?: TmdbVideo[];
}

// In-memory only, not a database table: losing this on a restart just
// means the next request for that title re-asks TMDB once, a fine cost
// for a value this cheap to recompute and not worth a migration for.
const cache = new Map<string, { result: TrailerResult | null; expiresAt: number }>();

function cacheKey(title: string, year: number | undefined, mediaType: TmdbMediaType): string {
  return `${mediaType}:${title.trim().toLowerCase()}:${year ?? ""}`;
}

async function tmdbGet<T>(path: string, token: string): Promise<T | null> {
  const response = await fetch(`${TMDB_BASE_URL}${path}`, {
    headers: { Authorization: `Bearer ${token}`, Accept: "application/json" },
  });
  if (!response.ok) return null;
  return (await response.json()) as T;
}

/**
 * Only ever a YouTube-hosted Trailer or Teaser (preferring an official
 * full Trailer, then any Trailer, then a Teaser) -- never a Clip,
 * Featurette, Behind the Scenes, etc., even as a fallback when that's all
 * that exists: a button labeled "Trailer" that ends up playing a blooper
 * reel or a behind-the-scenes clip because that's the only video TMDB
 * had is a worse outcome than the button simply not appearing at all.
 */
function pickBestVideo(videos: TmdbVideo[]): TmdbVideo | null {
  const candidates = videos.filter(
    (video) => video.site === "YouTube" && (video.type === "Trailer" || video.type === "Teaser")
  );
  const priority = (video: TmdbVideo): number => {
    if (video.type === "Trailer" && video.official) return 0;
    if (video.type === "Trailer") return 1;
    return 2; // Teaser
  };
  return [...candidates].sort((a, b) => priority(a) - priority(b))[0] ?? null;
}

async function lookUp(title: string, year: number | undefined, mediaType: TmdbMediaType): Promise<TrailerResult | null> {
  const token = getTmdbReadAccessToken();
  if (!token) return null;

  try {
    const searchParams = new URLSearchParams({ query: title });
    if (year !== undefined) {
      // TMDB's movie search takes "year"; its TV search takes
      // "first_air_date_year" instead -- same underlying concept, two
      // different query param names depending on media type.
      searchParams.set(mediaType === "movie" ? "year" : "first_air_date_year", String(year));
    }
    const search = await tmdbGet<TmdbSearchResponse>(`/search/${mediaType}?${searchParams.toString()}`, token);
    const bestMatchId = search?.results?.[0]?.id;
    if (bestMatchId === undefined) return null;

    const videos = await tmdbGet<TmdbVideosResponse>(`/${mediaType}/${bestMatchId}/videos`, token);
    const best = pickBestVideo(videos?.results ?? []);
    return best ? { youtubeVideoId: best.key } : null;
  } catch {
    // Network failure, malformed JSON, an unexpected shape, etc. --
    // degrade the same way "nothing found" or "no token configured" does.
    // A trailer is always a nice-to-have; it must never be able to break
    // loading a Detail page.
    return null;
  }
}

/**
 * Looks up a YouTube trailer for a title via TMDB, caching both hits and
 * misses in memory for CACHE_TTL_MS so the same title doesn't re-query
 * TMDB every time any user of the app opens its Detail page. Never
 * throws -- see lookUp's own doc on why every failure mode degrades to
 * null instead.
 */
export async function findTrailer(
  title: string,
  year: number | undefined,
  mediaType: TmdbMediaType
): Promise<TrailerResult | null> {
  const key = cacheKey(title, year, mediaType);
  const cached = cache.get(key);
  if (cached && cached.expiresAt > Date.now()) return cached.result;

  const result = await lookUp(title, year, mediaType);
  cache.set(key, { result, expiresAt: Date.now() + CACHE_TTL_MS });
  return result;
}
