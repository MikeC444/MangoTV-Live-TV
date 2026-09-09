-- Mirrors AddonRepository's local InstalledAddon list (see Milestone 0
-- audit): which Stremio-protocol addons a user has installed, their
-- cached manifest, whether they're enabled, and display order.
--
-- manifest_json caches the addon's own manifest.json verbatim — a
-- genuinely external, addon-controlled document (arbitrary optional
-- fields, addon-defined catalogs/resources) rather than an entity this
-- system models itself, so JSONB is the appropriate fit here, unlike the
-- relational columns beside it that this system does own and query by
-- (manifest_url, enabled, sort_order).
CREATE TABLE user_addons (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    manifest_url text NOT NULL,
    addon_id text NOT NULL,
    name text NOT NULL,
    manifest_json jsonb NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    sort_order integer NOT NULL DEFAULT 0,
    installed_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    CONSTRAINT user_addons_user_manifest_key UNIQUE (user_id, manifest_url)
);

CREATE INDEX user_addons_user_id_idx ON user_addons (user_id);
