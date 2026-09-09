-- Durable per-(user, title, episode) progress record — one row per movie
-- or per TV episode a user has ever made progress on, UPSERTed on each
-- progress update (see Milestone 8's "sensible update strategy": periodic
-- during playback, on pause/stop/completion — never a row per tick).
-- This is a bounded table (one row per unique episode/movie ever
-- watched), not an unbounded append log.
--
-- episode_key normalizes the nullable (season_number, episode_number)
-- pair — always NULL for a movie — into a single non-null value so the
-- natural-key unique constraint behaves correctly. Plain SQL unique
-- constraints treat NULL as distinct from NULL (two "same movie, both
-- NULL season/episode" rows would NOT collide and the upsert would
-- silently insert duplicates instead of updating). `NULLS NOT DISTINCT`
-- would fix this directly but is PostgreSQL 15+ only; a generated column
-- keeps this schema portable to whatever Postgres version the target
-- Neon project runs.
CREATE TABLE watch_history (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider_id text NOT NULL,
    content_id text NOT NULL,
    content_type content_type NOT NULL,
    season_number integer,
    episode_number integer,
    episode_key text GENERATED ALWAYS AS (
        coalesce(season_number::text, 'x') || ':' || coalesce(episode_number::text, 'x')
    ) STORED,
    episode_title text,
    title text NOT NULL,
    poster_url text,
    position_ms bigint NOT NULL DEFAULT 0,
    duration_ms bigint NOT NULL DEFAULT 0,
    completed boolean NOT NULL DEFAULT false,
    watched_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT watch_history_position_non_negative CHECK (position_ms >= 0),
    CONSTRAINT watch_history_duration_non_negative CHECK (duration_ms >= 0),
    CONSTRAINT watch_history_user_episode_key UNIQUE (user_id, provider_id, content_id, content_type, episode_key)
);

CREATE INDEX watch_history_user_id_idx ON watch_history (user_id);
CREATE INDEX watch_history_user_watched_at_idx ON watch_history (user_id, watched_at DESC);
