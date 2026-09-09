-- One row per (user, title) — a movie or an entire TV show, not per
-- episode — pointing at whichever episode is currently resumable. Moving
-- on to the next episode of a show updates this same row's
-- season/episode/position fields rather than inserting a new one, which
-- is exactly what backs a single "Continue Watching" card per title.
--
-- Kept as its own table rather than a view over watch_history because its
-- write pattern differs (removed once finished or dismissed by the user,
-- independent of watch_history which keeps every episode's record
-- indefinitely) and because it carries its own denormalized display
-- fields (backdrop_url) that watch_history has no need for.
--
-- Maintaining continue_watching from watch_history writes (e.g. clearing
-- it once an episode's completed flag flips true) is Milestone 8
-- application logic, not a database trigger — kept visible/testable in
-- code rather than hidden in SQL.
CREATE TABLE continue_watching (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider_id text NOT NULL,
    content_id text NOT NULL,
    content_type content_type NOT NULL,
    season_number integer,
    episode_number integer,
    episode_title text,
    title text NOT NULL,
    poster_url text,
    backdrop_url text,
    position_ms bigint NOT NULL DEFAULT 0,
    duration_ms bigint NOT NULL DEFAULT 0,
    last_watched_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    CONSTRAINT continue_watching_position_non_negative CHECK (position_ms >= 0),
    CONSTRAINT continue_watching_duration_non_negative CHECK (duration_ms >= 0),
    CONSTRAINT continue_watching_user_content_key UNIQUE (user_id, provider_id, content_id, content_type)
);

CREATE INDEX continue_watching_user_id_idx ON continue_watching (user_id);
CREATE INDEX continue_watching_active_idx ON continue_watching (user_id, last_watched_at DESC) WHERE deleted_at IS NULL;
