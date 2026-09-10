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

**Superseded by [`docs/TESTING.md`](TESTING.md)**, written as part of
Milestone 17's final documentation pass — that file now holds the
maintained, canonical copy of the eight hardware-pass steps (§3) so they
exist in exactly one place going forward rather than drifting between a
milestone-numbered snapshot and a living doc. This file stays as the
Milestone 16 historical record of what was verified and when (see above);
go to `TESTING.md` to actually run the hardware pass.

## Recording results

When this is run on real hardware, append the outcome to `CHANGELOG.md`'s
Milestone 16 entry the same way every other milestone records its test
results — pass/fail per test above, and any issue found alongside the fix
that closed it, not just a final "works."
