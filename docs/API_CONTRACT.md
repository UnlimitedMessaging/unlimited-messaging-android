# Backend contract

What the app expects from the Unlimited Messaging API. The app is written
against this document; the server side implements it. When one changes, the
other follows in the same release.

> **Status:** implemented in the API (module
> `apps/api/src/modules/messaging-account/channels/sms/` of the product
> repository, migration `sms-android-gateway`). Production needs that API
> version deployed before the release build of the app can sign in.

- [Conventions](#conventions)
- [Authentication](#authentication)
- [Device lifecycle](#device-lifecycle)
- [Endpoints](#endpoints)
  - [`POST /device/register`](#post-deviceregister)
  - [`GET /device/outbox`](#get-deviceoutbox)
  - [`POST /device/messages/{id}/status`](#post-devicemessagesidstatus)
  - [`POST /device/inbound`](#post-deviceinbound)
  - [`DELETE /device`](#delete-device)
- [Message statuses](#message-statuses)
- [Error codes](#error-codes)
- [Server-side notes](#server-side-notes)
- [Out of scope for v1](#out-of-scope-for-v1)

## Conventions

- JSON bodies, UTF-8. Timestamps are ISO-8601 instants in UTC
  (`2026-11-07T09:30:00.123Z`). Phone numbers are passed as the network gives
  them; E.164 when known.
- Every `/device/*` call carries `Authorization: Bearer <access token>`.
- Unknown fields in responses are ignored by the app, so the server may add
  fields freely. The app never sends a field the server must know about
  without a matching change here.
- Responses the app relies on:

  | Status | App behavior |
  | --- | --- |
  | `2xx` | Success. `204` (or an empty body) where no response body is documented. |
  | `401` | Refreshes the access token once and replays the request. If the refresh is refused, the session ends and the user is asked to sign in again. |
  | `410 Gone` | The device was removed (from the website, or by the server). The app signs out locally. |
  | `408`, `425`, `429`, `5xx`, network error | Retried with exponential backoff. |
  | other `4xx` on a report (status, inbound) | The report is dropped: the server would refuse it again identically. |

## Authentication

OAuth 2.1 authorization code flow with PKCE (RFC 7636, `S256` only), for a
**public client** (no secret, the APK can be unpacked).

1. The app reads `GET /.well-known/oauth-authorization-server` (RFC 8414) and
   uses `authorization_endpoint`, `token_endpoint` and `revocation_endpoint`
   from it. No path is hard-coded in the app.
2. It opens the authorization endpoint in a Custom Tab (the user's browser)
   with `response_type=code`, `client_id`, `redirect_uri`, `scope`, `state`,
   `code_challenge`, `code_challenge_method=S256`. The user signs in on the
   website's own page (email/password, magic link, Google, GitHub).
3. The server redirects to `redirect_uri?code=...&state=...`. The app checks
   `state` and exchanges the code at the token endpoint
   (`application/x-www-form-urlencoded`, `grant_type=authorization_code`,
   `code`, `code_verifier`, `client_id`, `redirect_uri`).
4. Expected response: `access_token`, `refresh_token`, `token_type`.
   `expires_in` is optional; the app relies on `401` rather than on a clock.
5. Refresh: `grant_type=refresh_token`, `refresh_token`, `client_id`. A new
   `refresh_token` in the response replaces the old one; when absent, the app
   keeps the one it has. `400`/`401` on refresh ends the session.
6. Sign-out: the app calls `DELETE /device`, then revokes the refresh token
   at the revocation endpoint (RFC 7009, `token`, `client_id`).

What the server needs for this:

| Item | Value |
| --- | --- |
| Client id | `um-android` (Gradle property `um.oauthClientId`) |
| Client type | Public, `token_endpoint_auth_method: none`, PKCE required |
| Redirect URIs | `app.unlimitedmessaging.android:/oauth2redirect` (release) and `app.unlimitedmessaging.android.debug:/oauth2redirect` (debug build) |
| Scope | `device` (Gradle property `um.oauthScope`) |
| Consent screen | Can be skipped: first-party client |
| Access token | Accepted on `/device/*` only. Today, tokens from this server (`mct_`) are only accepted under `/mcp`. |
| Lifetime | Short-lived access token (e.g. 1 h) plus a refresh token. |

## Device lifecycle

- A **device** is one installation of the app for one user, keyed by
  `(userId, installationId)`. `installationId` is a random UUID generated on
  first launch; reinstalling the app produces a new one, hence a new device.
- The server binds the OAuth grant to the device on the first
  `POST /device/register` made with it. Every other `/device/*` endpoint
  resolves the device from the access token.
- Each active **SIM** of the device is one **messaging account** with
  `channel = SMS`, keyed by `(deviceId, subscriptionId)`. A dual-SIM phone is
  two accounts.
- A SIM missing from a later registration has its account deactivated (not
  deleted: its message history stays). It is reactivated if the SIM comes
  back with the same `subscriptionId`.
- The device is **online** while it polls the outbox. Every poll updates its
  last-seen time. Suggested: an account goes `INACTIVE` after about 2 minutes
  without a poll, back to `ACTIVE` on the next one. That drives the existing
  account status webhooks.

## Endpoints

### `POST /device/register`

Creates or updates the device and its SIM accounts. **Idempotent**: the app
calls it every time the gateway starts, whenever the SIMs change, and after
any failed request.

Request:

```json
{
  "installationId": "6f1c2e0a-8d7b-4c1e-9a55-2b0f3e9c7d41",
  "name": "Pixel 9 Pro",
  "manufacturer": "Google",
  "model": "Pixel 9 Pro",
  "androidSdk": 35,
  "appVersion": "0.1.0",
  "sims": [
    {
      "subscriptionId": 1,
      "slotIndex": 0,
      "carrierName": "Orange F",
      "displayName": "Pro",
      "phoneNumber": "+33612345678",
      "countryIso": "FR"
    },
    {
      "subscriptionId": 3,
      "slotIndex": 1,
      "carrierName": "Free",
      "displayName": null,
      "phoneNumber": null,
      "countryIso": "FR"
    }
  ]
}
```

| Field | Notes |
| --- | --- |
| `name` | User-visible device name from system settings, else manufacturer and model. |
| `sims[].subscriptionId` | Android's id for the SIM on this phone. Stable when the same SIM is removed and reinserted; a new SIM gets a new id. |
| `sims[].slotIndex` | 0-based physical slot, `-1` for an eSIM not tied to one. |
| `sims[].phoneNumber` | **Often null**: many carriers do not expose the number to the phone. The account is then created without `externalIdentifier`, and the website asks the user for it. |

Response `200`:

```json
{
  "deviceId": "dev_01J...",
  "accounts": [
    { "subscriptionId": 1, "accountId": "acc_01J...", "status": "ACTIVE" },
    { "subscriptionId": 3, "accountId": "acc_01J...", "status": "ACTIVE" }
  ]
}
```

`accounts` lists one entry per SIM sent in the request. `status` is the
account's `AccountStatus`, shown as is in the app.

### `GET /device/outbox`

Claims the messages waiting for this device's SIM accounts. **Long poll.**

Query: `wait` (seconds, the app sends `30`; the server may cap it, e.g. at 60).

- When messages are claimable, answers immediately.
- Otherwise holds the request until one becomes claimable or `wait` elapses,
  then answers with an empty list. The server must answer before `wait`
  elapses: the app's read timeout is `wait + 15` seconds.
- Claiming is atomic: each returned message moves from `PENDING` to
  `SENDING` and records when it was claimed. A message is never returned to
  two concurrent polls.
- Batch size is up to the server (suggested: at most 10).

Response `200`:

```json
{
  "messages": [
    {
      "id": "msg_01J...",
      "accountId": "acc_01J...",
      "subscriptionId": 1,
      "to": "+33698765432",
      "text": "Your code is 482913\n\nvia unlimitedmessaging.app",
      "expiresAt": "2026-11-08T09:30:00Z"
    }
  ]
}
```

| Field | Notes |
| --- | --- |
| `subscriptionId` | Which SIM sends it (the app does not keep an account-to-SIM map). |
| `text` | **Final text, sent verbatim.** Anything the server adds, such as the free plan's mention, is already in it. |
| `expiresAt` | Optional. A message reaching the phone after it is failed with `EXPIRED` instead of sent. |

Delivery guarantees:

- If no status arrives for a claimed message within a timeout (suggested:
  10 minutes), the server may put it back to `PENDING`. The app remembers
  every message id it handled for 7 days and **never sends the same id
  twice**: a message handed out again only gets its last known status
  re-reported.
- A message still undelivered to the phone after its `expiresAt` is failed by
  the server.

### `POST /device/messages/{id}/status`

Reports a status change of a message this device claimed. **Idempotent**, and
reports for one message may arrive **out of order** (each is retried on its
own): the server only ever moves a message forward, see
[Message statuses](#message-statuses).

Request:

```json
{
  "status": "FAILED",
  "occurredAt": "2026-11-07T09:30:02.481Z",
  "errorCode": "NO_SERVICE",
  "parts": 2
}
```

| Field | Notes |
| --- | --- |
| `status` | `SENT`, `DELIVERED`, `UNDELIVERABLE` or `FAILED`. |
| `occurredAt` | When the phone observed it. |
| `errorCode` | Only with `FAILED` and `UNDELIVERABLE`. See [Error codes](#error-codes). |
| `parts` | How many SMS the text was split into (useful for billing-style stats). |

Response: `204`. `404` for a message that is not this device's (the app drops
the report).

### `POST /device/inbound`

An SMS this phone received on one of its SIMs. Only messages received
**after** sign-in are sent; existing history on the phone is never read.

Request:

```json
{
  "subscriptionId": 1,
  "from": "+33698765432",
  "text": "Yes, see you tomorrow",
  "sentAt": "2026-11-07T09:31:10Z",
  "receivedAt": "2026-11-07T09:31:12.004Z",
  "dedupKey": "0f8e...64 hex chars"
}
```

| Field | Notes |
| --- | --- |
| `from` | Sender as the network gives it: a number, or an alphanumeric sender id (`"BANK"`). |
| `text` | All parts of a long SMS, concatenated. |
| `sentAt` | Network (SMSC) timestamp. |
| `dedupKey` | SHA-256 of SIM, sender, network timestamp and text. **Idempotent on `(deviceId, dedupKey)`**: a duplicate is acknowledged and ignored. |

Response: `204`. The server stores it like any inbound message
(`direction = IN`, `channel = SMS`, `interlocutor = from`, `isGroup = false`,
`conversationId = null`) and emits `message.received`.

### `DELETE /device`

Sign-out. Removes the device bound to the token, deactivates its accounts,
and revokes the grant. Response: `204`. Pending outbox messages of those
accounts should be failed with a clear reason rather than left waiting.

## Message statuses

| Phone | `MessageStatus` | Meaning |
| --- | --- | --- |
| (claimed) | `SENDING` | Set by the server when the outbox hands the message out. |
| `SENT` | `SENT` | The radio accepted every part. |
| `DELIVERED` | `DELIVERED` | The recipient's network confirmed every part. Only if the carrier sends delivery reports; many messages stay `SENT` forever, which is normal. |
| `UNDELIVERABLE` | `UNDELIVERABLE` | The recipient's network gave up (permanent delivery failure). |
| `FAILED` | `FAILED` | The phone could not send it. |

There is no `READ`: SMS has no read receipts.

Ordering: `PENDING < SENDING < SENT < DELIVERED`. `FAILED` and
`UNDELIVERABLE` are terminal. Apply a report only if it moves the message
forward; ignore it otherwise (a late `SENT` after `DELIVERED`).

## Error codes

Radio errors (from Android's `SmsManager` result codes):

| Code | Meaning |
| --- | --- |
| `GENERIC_FAILURE` | Unspecified failure (often no credit, or the carrier refused). |
| `RADIO_OFF` | Airplane mode. |
| `NO_SERVICE` | No network. |
| `NULL_PDU` | Android could not encode the message. |
| `LIMIT_EXCEEDED` | Android's own sending limit hit (see docs/RELIABILITY.md). |
| `FDN_CHECK_FAILURE` | The SIM's fixed dialing numbers list blocks the recipient. |
| `SHORT_CODE_NOT_ALLOWED`, `SHORT_CODE_NEVER_ALLOWED` | Sending to a premium short code was refused. |
| `RADIO_ERROR_<n>` | Any other result code, numerically. |

App errors:

| Code | Meaning |
| --- | --- |
| `SIM_NOT_AVAILABLE` | The SIM the message was queued for is no longer in the phone. |
| `PERMISSION_DENIED` | The user revoked the SMS permission. |
| `EXPIRED` | Reached the phone after `expiresAt`. |
| `INVALID_MESSAGE` | Android refused the request (empty text, malformed number). |
| `DELIVERY_FAILED` | With `UNDELIVERABLE`: permanent failure reported by the recipient's network. |

## Server-side notes

Pointers for the implementation in the API repository. Not requirements of
the app, but what makes the contract above hold.

- **Channel-neutral layer.** `AccountChannel.SMS` and `MessageChannel.SMS`
  already exist. The SMS provider is a thin adapter like the others: the
  `POST /message` planning, validation (text only, no group, length limit),
  pacing and persistence stay in `MessageService`.
- **Waking a poll across instances.** The poll can land on any API instance.
  Have the queue worker, when a message becomes claimable, publish on a Redis
  channel (or push to a per-device list) that the waiting poll listens on,
  with the timeout as the fallback. Keeps the API stateless.
- **Pacing.** Android asks the user to confirm when an app sends more than 30
  SMS in 30 minutes. Default the SMS account cooldown accordingly (about 60 s)
  unless the user configured their own.
- **Free plan mention.** Appended server side, as for the other channels.
  Keep it short: past 160 GSM-7 characters (70 with any emoji), a message
  becomes two SMS, which costs the user twice on most plans.
- **Phone number unknown.** Planned: detect it by having the app send a
  verification SMS from each SIM to a number the platform owns, and read the
  sender. Until then the website asks the user.

## Out of scope for v1

- Reading or uploading SMS history already on the phone.
- SMS the user sends by hand from the phone's messaging app.
- MMS (pictures, group messages).
- Number detection by verification SMS (see above).
