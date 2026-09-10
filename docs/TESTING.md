# Testing

Three layers of test coverage exist for this system, from fastest/most
automated to most realistic:

1. **Backend unit/integration tests** — 131 tests, each verifying one
   endpoint or one specific behavior in isolation, against a real
   (disposable) Postgres database.
2. **Backend end-to-end script** — one continuous script driving a real
   running server through all eight named multi-device scenarios in
   sequence, over real HTTP.
3. **Real hardware multi-device test** — the same eight scenarios, run by
   a human on real Fire TV devices against a real deployment. This is the
   only layer that exercises the actual Android app, QR camera scanning,
   remote/D-pad navigation, and genuine offline behavior.

Layers 1 and 2 can run entirely inside a plain dev environment (this
sandbox included) with no Android SDK, emulator, or physical device.
Layer 3 cannot — see its own section below for exactly why.

## 1. Backend unit/integration tests

```
cd server
cp .env.test.example .env.test
# edit .env.test: TEST_DATABASE_URL=postgresql://<user>:<password>@localhost:5432/<a throwaway database>
npm test
```

- Needs its own **disposable** database — the suite runs `TRUNCATE`
  between every test. `TEST_DATABASE_URL` is a completely separate
  variable from `.env`'s `DATABASE_URL`; the suite never reads
  `DATABASE_URL` at all, so a real (dev or production) database
  configured there is never at risk just because it happened to be
  sitting in `.env`.
- `npm test`'s `pretest` script always migrates the test database first,
  so it's always current.
- CI (`.github/workflows/server-ci.yml`, `test` job) runs the identical
  suite against a throwaway `postgres:16` GitHub Actions service
  container on every push — no secret needed, so this always runs.
- Coverage includes dedicated cross-account attack simulations, not just
  happy-path checks — e.g. one account guessing another's exact
  watchlist/addon natural key with a deliberately far-future `updatedAt`
  engineered to win any last-write-wins race, confirmed to affect nothing
  (every query is scoped to the authenticated session's own `user_id`,
  never a client-supplied value).

## 2. Automated end-to-end script

`server/scripts/e2e-full-flow.ts` (`npm run e2e`) drives a **real,
running** server instance over **real HTTP** — no test harness shortcuts,
no direct database access — through all eight scenarios below in one
continuous multi-device session, using several independent simulated
"devices" (their own device id, their own session tokens) the way real
multi-device usage actually looks to the API.

```
cd server
cp .env.example .env
# edit .env: DATABASE_URL to any database (fresh or persistent — see below),
# API_BASE_URL to wherever the server you're about to start will listen,
# e.g. http://localhost:3000
npm run migrate
npm run dev                 # in one terminal — leave it running
npm run e2e                 # in another terminal
```

Safe to run against a **persistent, non-truncated** database and to
re-run repeatedly — every account the script creates uses a
timestamp-unique email, so successive runs never collide with a previous
run's data. Point `E2E_API_BASE_URL` (env var, defaults to
`http://localhost:3000`) at any reachable deployment, including a real
one, to smoke-test it the same way.

Each of the eight scenarios below states exactly which parts this script
covers and — for TEST 7 specifically — which parts it honestly cannot.

## 3. Real hardware multi-device test

Requires: the backend deployed and reachable over HTTPS (see
[Deployment](DEPLOYMENT.md)), a real Neon database behind it, the APK
built with that deployment's `API_BASE_URL`, and at least two Fire TV /
Android TV devices (or one device plus a genuinely GPU-accelerated
emulator — this project's own development sandbox has neither).

### TEST 1 — New user

1. Fresh-install the APK on Device A. Launch it.
   **Expect:** the authentication screen appears immediately — never the
   existing Home/catalog UI first, and there is no way to reach it
   without authenticating.
2. Select **Create Account**. A QR code appears.
3. Scan it with a phone; the activation page's account-creation tab
   loads. Enter a new email + password, submit.
   **Expect:** Device A detects completion on its own (polling, no manual
   refresh) within a few seconds and proceeds straight into the main app.
   *(Covered by the automated script: QR create → resolve → complete →
   status delivers real tokens → a second status call proves the token
   can't be claimed twice.)*

### TEST 2 — Data creation

On Device A: add at least two titles to My List, watch part of one title
then back out of the player, change a setting under Settings, install an
addon and toggle its enabled state under Settings → Addons.
**Expect:** every action reflects locally right away — no visible delay
waiting on the network (local-cache-first, since Milestone 6).
*(Covered by the automated script for the data-layer writes themselves;
the on-device instant-feedback feel needs a human watching a real
screen.)*

### TEST 3 — Second device

1. Fresh-install the APK on Device B. Select **Sign In**, scan the QR,
   sign in with Device A's account.
   **Expect:** Device B proceeds into the main app and, within a few
   seconds, shows the exact same My List, Continue Watching entry,
   settings, and addon configuration Device A has.
   *(Covered by the automated script: a second device logging into the
   same account receives independent session tokens and an identical
   view of every domain's state.)*

### TEST 4 — Cross-device changes

On Device B: remove one My List item, change a setting.
**Expect:** Device A reflects both changes without a restart, on its own
next sync trigger.
*(Covered by the automated script for the propagation itself; the
"without needing a restart, on real hardware's own sync cadence" timing
needs a human watching both devices.)*

### TEST 5 — Logout

On Device B: Settings → Account → Sign Out.
**Expect:** Device B returns to the authentication screen; none of the
account's data is reachable afterward (back button, relaunch) without
signing in again. Device A stays signed in and unaffected throughout.
*(Covered by the automated script: the logged-out device's access token
is confirmed rejected immediately, while the other device's own session
is confirmed to remain valid.)*

### TEST 6 — Account switch

On Device B (now signed out): sign in with a different account.
**Expect:** completely different My List / history / settings / addons —
nothing from the first account is visible.
*(Covered by the automated script: a third account's every domain
confirmed empty/default, disjoint from the first.)*

### TEST 7 — Offline

1. On a signed-in device, disable Wi-Fi entirely.
   **Expect:** the app continues to function using cached data — Home, My
   List, Continue Watching, Settings, and Addons all still render;
   nothing crashes or blanks out waiting on a dead network.
2. While still offline, add/remove a My List item and change a setting.
   **Expect:** both apply locally and instantly, same as online.
3. Re-enable Wi-Fi.
   **Expect:** within a few seconds to at most five minutes, the offline
   changes appear on a second signed-in device.

**Not coverable by the automated script, by design** — local cache
continuity with the network genuinely disabled, and a queued change
automatically replaying once the OS reports connectivity restored, are
Android-side behaviors that only exist once the real app is running on a
real device. What the script *does* verify instead: the server-side
contract that behavior depends on — replaying an identical write after a
simulated reconnect is safe (no duplicate, no error), and a
stale-timestamped replay correctly loses to the already-applied newer
write (last-write-wins holds under replay).

### TEST 8 — Security

Already exhaustively covered without hardware: the automated script's own
final section, plus the 131 backend unit tests (several of them dedicated
attack simulations), already prove every cross-account access path fails.
Nothing about running the real Android app changes a server-side
authorization guarantee. Worth a spot check on real devices only to see
it firsthand: sign into two different accounts on two devices and confirm
each only ever shows its own data.

## Recording a hardware run

Append the result to `CHANGELOG.md`'s Milestone 16 entry — pass/fail per
test above, and any issue found alongside the fix that closed it — the
same way every other milestone in this project records its own test
results, rather than a bare "works."
