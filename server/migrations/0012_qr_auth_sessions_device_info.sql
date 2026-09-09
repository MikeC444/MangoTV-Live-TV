-- Optional device metadata captured at QR-session creation time (Milestone
-- 4), carried through to the devices row created once the session is
-- consumed — without this, a device signing in via QR would always fall
-- back to devices' generic 'Fire TV' / 'fire_tv' defaults instead of a
-- real name. Nullable: a caller that doesn't supply them still works.
ALTER TABLE qr_auth_sessions ADD COLUMN device_name text;
ALTER TABLE qr_auth_sessions ADD COLUMN platform text;
