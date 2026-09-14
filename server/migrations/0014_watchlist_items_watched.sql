-- Tracks whether a My List slot got there (or was later flagged) because
-- the player reported it watched past the completion threshold, versus a
-- title the user only ever added manually — backs My List's "Watched"
-- filter (unselected mixes both; selected narrows to watched = true).
-- Not NULL with a false default so every pre-existing row (and any client
-- that doesn't yet send this field) is unambiguously "not watched" rather
-- than unknown.
ALTER TABLE watchlist_items ADD COLUMN watched boolean NOT NULL DEFAULT false;
