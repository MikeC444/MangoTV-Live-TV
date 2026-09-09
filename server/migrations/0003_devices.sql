-- One row per (account, physical device) pairing.
--
-- device_identifier is a UUID the Fire TV app generates once and persists
-- locally (not derived from hardware serials/ANDROID_ID/MAC address — see
-- the project's "no invasive hardware fingerprinting" requirement). The
-- same physical device can appear under multiple accounts (Milestone 12's
-- account switching): each account that has ever signed into that TV gets
-- its own devices row sharing the same device_identifier, which is why
-- device_identifier is NOT globally unique on its own — only unique per
-- user. This is also what lets a user's "manage devices" screen show
-- distinct entries and revoke one without affecting another account that
-- happens to share the same physical hardware.
--
-- revoked_at lets a user remotely sign a device out; sessions.device_id
-- cascades so revoking here (or deleting the row) invalidates any
-- sessions tied to it.

CREATE TABLE devices (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    device_identifier uuid NOT NULL,
    device_name text NOT NULL DEFAULT 'Fire TV',
    platform text NOT NULL DEFAULT 'fire_tv',
    app_version text,
    last_seen_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    revoked_at timestamptz,
    CONSTRAINT devices_user_identifier_key UNIQUE (user_id, device_identifier)
);

-- Postgres does not automatically index the referencing side of a foreign
-- key (only the referenced parent's key is auto-indexed) — every FK column
-- below gets an explicit index to keep joins, "list my devices", and
-- ON DELETE CASCADE fast.
CREATE INDEX devices_user_id_idx ON devices (user_id);
