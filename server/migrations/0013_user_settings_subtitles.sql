-- Adds subtitle defaults to the same one-row-per-user PlayerPreferences
-- mirror 0006_user_settings.sql created for autoplay/skip-intro.
-- subtitles_enabled is a plain boolean (same shape as those two).
-- default_subtitle_language is nullable text, not an enum/JSONB: it's an
-- opaque ISO 639-1 code (e.g. "en", "es") the client itself validates
-- against its own fixed language list (see SubtitleSettingsScreen.kt) --
-- nothing here queries into it individually, and null means "no
-- preference" (defer to the stream/system default), not "unset".
ALTER TABLE user_settings
    ADD COLUMN subtitles_enabled boolean NOT NULL DEFAULT true,
    ADD COLUMN default_subtitle_language text;
