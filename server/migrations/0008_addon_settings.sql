-- Reserved extensibility point, unused by any application code as of
-- Milestone 1: today's addons (see Milestone 0 audit) have no
-- configurable state beyond what already lives on user_addons
-- (enabled, sort_order). The Stremio addon protocol supports
-- "configurable" addons that accept user-supplied config values; if
-- Milestone 9 wires that up, per-addon config lands here as a JSONB blob
-- (config's shape is entirely addon-defined) rather than as new columns
-- on user_addons, keeping that table's own always-present fields clean.
-- One row per installed addon, 1:1 with user_addons.
CREATE TABLE addon_settings (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_addon_id uuid NOT NULL REFERENCES user_addons (id) ON DELETE CASCADE,
    config jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT addon_settings_user_addon_key UNIQUE (user_addon_id),
    CONSTRAINT addon_settings_config_is_object CHECK (jsonb_typeof(config) = 'object')
);
