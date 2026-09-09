-- Backs the Fire TV QR sign-in/create-account flow (Milestone 4).
--
-- The activation token embedded in the QR/URL is never stored raw — only
-- its SHA-256 hash, looked up by re-hashing whatever token a request
-- presents (same at-rest principle as sessions' tokens above). It carries
-- no account information, matching the "cannot contain passwords or
-- sensitive account information" requirement: it is a random bearer value
-- that only means "the TV waiting on this token gets to claim whatever
-- session /auth/qr/complete produces".
--
-- Lifecycle: a TV creates a row (status='pending'). The phone/web
-- activation page resolves the token to find which device is waiting,
-- then either signs in or creates an account and calls complete, which
-- fills in user_id/session_id and flips status to 'completed'. The TV's
-- own poll of /auth/qr/status then reads the session's tokens exactly
-- once and the row flips to 'consumed' — enforcing single-use/replay
-- prevention at the data layer, not just in application logic. A
-- background sweep (Milestone 4) marks stale 'pending' rows 'expired'
-- past expires_at; the status+expires_at index below is for that sweep.
--
-- device_identifier is deliberately NOT a foreign key to devices: a QR
-- session is created before anyone has signed in, so no devices row
-- (which requires a user_id) exists yet to reference. It becomes a real
-- devices row only once /auth/qr/complete succeeds.
CREATE TABLE qr_auth_sessions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash text NOT NULL,
    device_identifier uuid NOT NULL,
    status qr_auth_status NOT NULL DEFAULT 'pending',
    user_id uuid REFERENCES users (id) ON DELETE CASCADE,
    session_id uuid REFERENCES sessions (id) ON DELETE SET NULL,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz,
    consumed_at timestamptz,
    CONSTRAINT qr_auth_sessions_token_hash_key UNIQUE (token_hash)
);

CREATE INDEX qr_auth_sessions_device_identifier_idx ON qr_auth_sessions (device_identifier);
CREATE INDEX qr_auth_sessions_user_id_idx ON qr_auth_sessions (user_id);
CREATE INDEX qr_auth_sessions_session_id_idx ON qr_auth_sessions (session_id);
CREATE INDEX qr_auth_sessions_status_expires_idx ON qr_auth_sessions (status, expires_at);
