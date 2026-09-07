# Live TV backend contract

The Android app never decides on its own whether a device is entitled to
Live TV — it only ever reflects what this backend says (see
`EntitlementRepository` in `app/src/main/java/com/mangotv/app/data/entitlement/`).
There is no bundled backend implementation in this repository; this
document is the exact contract the app expects so a real one can be built
and pointed at via `API_BASE_URL` (see "Configuring Live TV" in the root
`README.md`).

```
Android App  →  MangoTV API (this contract)  →  Entitlement Database  →  Payment Provider
```

## Why this exists

The app must never trust a local flag like `isPaid = true`. Every screen
that gates Live TV reads `EntitlementRepository.state`, which is only ever
set from an HTTP response described below. If `API_BASE_URL` is left
unconfigured, the app shows a distinct "not configured" state rather than
pretending to work — see `LiveTvConfig.isBackendConfigured`.

## Endpoints the app calls

### `POST {API_BASE_URL}/v1/devices/register`

Called once, best-effort, when Live TV is first opened without an active
entitlement. A failure here must never block the app from showing the QR
code (the QR already encodes everything needed — see below), so treat this
as a courtesy that lets your checkout page greet the user by pairing code.

Request body:

```json
{
  "device_id": "3fa1b9f0-...-uuid",
  "pairing_code": "MANGO-7K42",
  "platform": "android-tv"
}
```

Response: any `2xx` with an empty body is fine.

### `GET {API_BASE_URL}/v1/entitlements/{deviceId}`

Polled by the app every ~8 seconds while locked, and every ~5 minutes once
unlocked (to catch expiration without an app restart). Must always reflect
the backend's own record — never something the TV asserts.

Response body:

```json
{
  "status": "active",
  "expires_at": 1735689600000
}
```

- `status`: one of `"active"`, `"inactive"`, `"expired"`. Anything else is
  treated the same as an unreachable backend.
- `expires_at`: epoch milliseconds, or `null`/omitted for a non-expiring
  entitlement. If present and already in the past, the app treats the
  device as expired even if `status` says `"active"` (belt and braces
  against clock drift between systems).

A device with no record yet should return `{"status": "inactive"}` (HTTP
200), not a 404 — a 404/5xx is treated as "backend unreachable", which
shows different copy to the user than "you haven't paid yet".

## The checkout / payment flow

1. The app generates a device id (UUID) and a short pairing code (e.g.
   `MANGO-7K42`) once per install and persists them locally — see
   `DeviceIdentityRepository`. Neither is a secret; the pairing code is
   never typed anywhere, only scanned.
2. The QR code shown on the premium screen encodes:
   `{PREMIUM_CHECKOUT_URL}?device_id={deviceId}&pairing_code={pairingCode}`
   (see `LiveTvConfig.checkoutUrl`). This is **your** checkout page,
   hosted wherever you like — the app never talks to a payment provider
   directly.
3. Your checkout page reads `device_id`/`pairing_code` from its own query
   string and starts a real payment session with your payment provider
   (Stripe, etc.), tagging that session with the device id so you can
   reconcile it later.
4. When the payment provider confirms payment — via its webhook, not a
   redirect the user's phone might close before completing — your backend
   marks that device's entitlement `active` (with an `expires_at` if it's
   a subscription) in your entitlement database.
5. The next time the TV polls `GET /v1/entitlements/{deviceId}` (within a
   few seconds), it sees `"active"` and unlocks automatically — no app
   restart, no action on the TV.

**Never** have the checkout page or any redirect tell the TV "payment
succeeded" directly (e.g. via a deep link claiming success). The TV only
ever believes the entitlement endpoint above, which only your backend
(having independently verified the payment webhook) can flip to `active`.

## Expiration

When a previously active entitlement's next poll comes back
`"expired"` (or `"inactive"`, or an `active` record whose `expires_at` has
passed), the app shows *"Your Live TV access has expired."* and returns to
the purchase screen with a fresh QR code pointing at the same checkout
flow. Implement whatever expiration/renewal policy you like server-side
(subscription period, one-time unlock that never expires by omitting
`expires_at`, etc.) — the app only cares about the status this endpoint
reports.

## What the app never needs from you

- No payment provider API keys or webhook secrets ever go in the Android
  app or this repository — they belong entirely on your backend.
- No account/login system — entitlement is tied to the device id above.
  If MangoTV grows a real user/account system later, this is the one place
  to extend (associate the device id with an account id server-side; the
  app-side contract above doesn't need to change).
