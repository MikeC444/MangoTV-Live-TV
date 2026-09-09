-- Item-level watchlist/"My List" records — deliberately NOT a single
-- blob column replaced wholesale on every change (the project's own
-- Milestone 7 requirement). Mirrors the local SavedListItem shape.
--
-- One row per (user, title) slot, kept forever and toggled via
-- deleted_at rather than deleted-and-reinserted: removing and re-adding
-- the same title is an UPDATE that clears deleted_at, not a fresh INSERT,
-- which is what makes the unique constraint below meaningful (it
-- identifies the slot, independent of its current add/removed state) and
-- is what a later device's incremental sync diffs against via
-- updated_at.
--
-- content_id is only unique within one addon's own namespace, not
-- globally — the natural key includes provider_id for exactly that
-- reason (two different addons could otherwise coincidentally reuse the
-- same id for unrelated titles).
CREATE TABLE watchlist_items (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider_id text NOT NULL,
    content_id text NOT NULL,
    content_type content_type NOT NULL,
    title text NOT NULL,
    poster_url text,
    backdrop_url text,
    year integer,
    rating double precision,
    added_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    CONSTRAINT watchlist_items_user_content_key UNIQUE (user_id, provider_id, content_id, content_type)
);

CREATE INDEX watchlist_items_user_id_idx ON watchlist_items (user_id);

-- Backs "give me this user's current list" (Milestone 7's actual read
-- path), which always filters to still-active items — a partial index
-- keeps that lookup from scanning rows the user has removed.
CREATE INDEX watchlist_items_active_idx ON watchlist_items (user_id, updated_at) WHERE deleted_at IS NULL;
