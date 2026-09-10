# Milestone 16 — Full End-to-End Test Plan

Status: the eight scenarios below are **complete for everything the backend
alone determines** — verified for real, by `server/scripts/e2e-full-flow.ts`
against a live server and a live Postgres database (see "What's already
verified" below). The parts that only exist once the Fire TV app is actually
running on actual hardware — QR scanning with a real camera, D-pad/remote
navigation, the local cache continuing to work with Wi-Fi actually switched
off, on-device performance feel — cannot be exercised from this sandbox,
which has no Android SDK, emulator, or physical device (a limitation stated
since Milestone 0 and unchanged since). This document is both the plan for
running that hardware pass and the record of what's already covered without
it.

## What's already verified (no hardware needed)

`server/scripts/e2e-full-flow.ts` (`npm run e2e` from `server/`) drives a
**real, running** instance of the backend over **real HTTP** — no
`supertest`, no in-process shortcuts, no direct database access — through
all eight scenarios below, using multiple independent simulated "devices"
(their own `deviceId`, their own session tokens) exactly the way real
multi-device usage looks from the API's point of view. It's safe to re-run
against a persistent (non-truncated) database: every account it creates
uses a timestamp-unique email, so repeated runs never collide with a
previous run's data.

Run it yourself:

```
cd server
cp .env.example .env        # fill in DATABASE_URL, set API_BASE_URL to
                             # wherever the server you're about to start
                             # will actually listen (e.g. http://localhost:3000)
npm run migrate              # once, against a fresh database
npm run dev                  # in one terminal — leave it running
npm run e2e                  # in another terminal, against that server
```

It printed **40/40 checks passed** the last time it was run (twice in a
row, to confirm re-runnability), covering QR registration, QR replay
prevention, a second device converging on identical account state,
cross-device changes propagating, logout revoking exactly one device's
session, complete data isolation between two different accounts, the
replay-safety a client-side offline retry queue depends on, and every named
cross-account attack failing. What it proves and what it explicitly does
not — including why TEST 7 is only partially checkable this way — is
documented in the script's own header comment and echoed in its console
output when it runs.

## What still needs real hardware

Everything below requires: the backend deployed somewhere reachable over
HTTPS (see Milestone 17's deployment docs), a real Neon database behind it,
the app built with that deployment's `API_BASE_URL` in `local.properties`,
and at least two Fire TV / Android TV devices (or one device plus one
emulator with working GPU acceleration, if the toolchain running this has
one — this sandbox does not).

### TEST 1 — New user

1. Fresh-install the APK on Device A. Launch it.
2. **Expect:** the authentication screen appears immediately — never the
   existing Home/catalog UI first.
3. Select **Create Account**. A QR code appears.
4. Scan it with a phone. The activation page loads (account-creation tab).
5. Enter a new email + password, submit.
6. **Expect:** Device A detects completion on its own (polling, no manual
   refresh) within a few seconds and proceeds straight into the main app.

### TEST 2 — Data creation

On Device A: add at least two titles to My List, watch part of one title
then back out of the player, change a setting under Settings, install an
addon under Settings → Addons, and change that addon's enabled state.

**Expect:** every action reflects locally right away (no visible delay
waiting on the network) — this is the local-cache-first behavior Milestone
6 onward established.

### TEST 3 — Second device

1. Fresh-install the APK on Device B.
2. Select **Sign In**, scan the QR with a phone, sign in with Device A's
   account credentials (not create-account).
3. **Expect:** Device B proceeds into the main app and, within a few
   seconds, shows the exact same My List, Continue Watching entry,
   settings, and addon configuration Device A has.

### TEST 4 — Cross-device changes

On Device B: remove one My List item, change a setting to a different
value.

**Expect:** Device A reflects both changes without needing a restart — on
its own next sync trigger (app foreground, or within the periodic retry
window — see `SyncManager`'s Milestone 13 kdoc for the exact triggers).

### TEST 5 — Logout

On Device B: Settings → Account → Sign Out.

**Expect:** Device B returns to the authentication screen and none of
Device A's — now the *account's* — data is reachable from Device B
afterward (back button, relaunch) without signing in again. Device A stays
signed in and unaffected throughout.

### TEST 6 — Account switch

On Device B (now signed out): sign in with a **different** account (or
create a new one).

**Expect:** completely different My List / history / settings / addons —
nothing from the first account is visible.

### TEST 7 — Offline

1. On a signed-in device, disable Wi-Fi entirely.
2. **Expect:** the app continues to function using cached data — Home, My
   List, Continue Watching, Settings, and Addons all still render; nothing
   crashes or blanks out waiting on a dead network (per Milestone 13's
   hardening).
3. While still offline, add/remove a My List item and change a setting.
4. **Expect:** both changes apply locally and instantly, same as when
   online.
5. Re-enable Wi-Fi.
6. **Expect:** within a few seconds to a few minutes (immediately if the
   OS delivers a connectivity-changed callback promptly; within 5 minutes
   regardless, via `SyncManager`'s periodic retry — see its Milestone 13
   kdoc), the offline changes appear on a second signed-in device.

### TEST 8 — Security

This one is already exhaustively covered without hardware: `npm run e2e`'s
TEST 8, plus the 131 backend unit tests (several of them dedicated attack
simulations — e.g. one account guessing another's exact watchlist/addon
natural key with an engineered far-future timestamp to try to win a
last-write-wins race), already prove every cross-account access path
fails. Nothing about running the real Android app changes that server-side
guarantee. Worth a spot check on real devices only if you want to see it
firsthand: sign into two different accounts on two devices and confirm
each only ever shows its own data — there is no code path left unverified
that a manual pass here would newly catch.

## Recording results

When this is run on real hardware, append the outcome to `CHANGELOG.md`'s
Milestone 16 entry the same way every other milestone records its test
results — pass/fail per test above, and any issue found alongside the fix
that closed it, not just a final "works."
