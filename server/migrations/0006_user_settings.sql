-- One row per user (1:1), mirroring the two settings domains that exist
-- locally today: HomeRowPreferences and PlayerPreferences (see
-- docs/milestone-0-audit-and-plan.md). autoplay/skip-intro are plain
-- booleans (simple relational flags, not worth JSON). home_row_order and
-- hidden_row_ids stay JSONB: both are opaque, ordered/set-shaped lists of
-- addon-generated row ids with no independent identity of their own and
-- nothing ever queries into them individually — exactly the "flexible
-- settings" case the project's schema rules carve out for JSON/JSONB,
-- as opposed to core relational entities like watchlist items below.
CREATE TABLE user_settings (
    user_id uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    home_row_order jsonb NOT NULL DEFAULT '[]'::jsonb,
    hidden_row_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
    autoplay_next_episode boolean NOT NULL DEFAULT true,
    skip_intro_enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT user_settings_home_row_order_is_array CHECK (jsonb_typeof(home_row_order) = 'array'),
    CONSTRAINT user_settings_hidden_row_ids_is_array CHECK (jsonb_typeof(hidden_row_ids) = 'array')
);
