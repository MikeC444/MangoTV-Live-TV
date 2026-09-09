-- One row per logical login (one refresh-token lifetime), not one row per
-- request or per token refresh: /auth/refresh rotates both token hashes
-- and updates this same row in place, so a user's "active sessions" list
-- shows one entry per device login rather than growing on every refresh.
--
-- Both tokens are opaque, cryptographically random strings; only their
-- SHA-256 hashes are ever stored (same principle as password hashing —
-- a leaked database dump must not hand out usable tokens). Access tokens
-- are deliberately NOT self-contained JWTs: this app's scale doesn't need
-- stateless validation, and a DB-checked opaque token makes "revoked
-- session" and "expired session" take effect immediately and
-- deterministically instead of waiting out a JWT's remaining lifetime —
-- which matters directly for this project's own revocation test
-- requirements.
CREATE TABLE sessions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    device_id uuid NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    access_token_hash text NOT NULL,
    access_token_expires_at timestamptz NOT NULL,
    refresh_token_hash text NOT NULL,
    refresh_token_expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    last_used_at timestamptz NOT NULL DEFAULT now(),
    revoked_at timestamptz,
    CONSTRAINT sessions_access_token_hash_key UNIQUE (access_token_hash),
    CONSTRAINT sessions_refresh_token_hash_key UNIQUE (refresh_token_hash)
);

CREATE INDEX sessions_user_id_idx ON sessions (user_id);
CREATE INDEX sessions_device_id_idx ON sessions (device_id);
