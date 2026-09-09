-- The account itself. Email is always stored lowercased by the
-- application before insert/lookup (enforced here as a defensive
-- invariant); we deliberately avoid the citext extension to keep the
-- schema free of extension-availability assumptions on the deployment
-- target.
--
-- password_hash is NOT NULL: the only credential type this system
-- currently supports is email+password (Argon2id/bcrypt, hashed
-- server-side — see Milestone 3). It is not made nullable "just in case"
-- a future OAuth flow needs it, per the project's own no-speculative-
-- flexibility rule; that would be a schema change made when that feature
-- actually exists.
--
-- deleted_at supports future account closure (soft delete). No feature
-- sets it yet in this milestone. The email uniqueness index below is
-- intentionally a plain (non-partial) unique index rather than
-- `WHERE deleted_at IS NULL`: no code path sets deleted_at today, so
-- scoping the index around it now would be speculative. If account
-- deletion + email reuse becomes a real feature later, revisit this
-- index alongside that work.

CREATE TABLE users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email text NOT NULL,
    password_hash text NOT NULL,
    display_name text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    CONSTRAINT users_email_lowercase CHECK (email = lower(email))
);

CREATE UNIQUE INDEX users_email_key ON users (email);
