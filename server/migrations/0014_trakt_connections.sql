-- Trakt account linking. Two tables, both keyed 1:1 on user_id (same shape
-- as user_settings) rather than user_addons' many-per-user shape: an
-- account has at most one Trakt connection, and at most one in-progress
-- device-code pairing attempt at a time.
--
-- trakt_connections holds the established connection. access_token/
-- refresh_token are encrypted at rest (see src/security/crypto.ts) --
-- unlike this app's own session tokens (security/tokens.ts), these have to
-- be recoverable in plaintext to ever call the Trakt API again, so a
-- one-way hash (this system's usual pattern) doesn't apply here.
CREATE TABLE trakt_connections (
    user_id uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    access_token text NOT NULL,
    refresh_token text NOT NULL,
    scope text,
    expires_at timestamptz NOT NULL,
    trakt_slug text,
    trakt_username text,
    connected_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- trakt_device_links is ephemeral pairing state for the OAuth Device Code
-- flow (RFC 8628) -- the app displays user_code/verification_url and this
-- backend polls Trakt on its behalf until the user approves it elsewhere
-- (phone/computer) or it expires. Kept in Postgres rather than an
-- in-process Map so pairing survives this process restarting mid-flow and
-- works the same if this backend ever runs as more than one instance.
-- device_code is short-lived (Trakt's own expiry is ~10 minutes) and
-- useless without this app's client_secret, so it's stored as plain text
-- rather than encrypted like the long-lived tokens above.
CREATE TABLE trakt_device_links (
    user_id uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    device_code text NOT NULL,
    user_code text NOT NULL,
    verification_url text NOT NULL,
    interval_seconds integer NOT NULL,
    expires_at timestamptz NOT NULL,
    last_polled_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
