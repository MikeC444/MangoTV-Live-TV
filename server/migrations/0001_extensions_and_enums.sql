-- Shared enum types, used across multiple tables below.
--
-- content_type's values intentionally match the Android app's
-- ContentType enum names (MOVIE, TV_SHOW) exactly, so the API layer
-- never needs to translate between the two.
--
-- gen_random_uuid() is used throughout this schema for primary keys; it is
-- a built-in Postgres function since v13 and needs no extension.

CREATE TYPE content_type AS ENUM ('MOVIE', 'TV_SHOW');

CREATE TYPE qr_auth_status AS ENUM ('pending', 'completed', 'consumed', 'expired');
