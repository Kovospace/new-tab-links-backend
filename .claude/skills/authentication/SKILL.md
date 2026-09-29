---
name: authentication
description: How identity works in the NewTabLinks backend - the account model, classic registration with email activation, Google sign-in, the extension connect code, JWT access and refresh tokens, password reset and change, the device list, authenticated websocket connections, and the ownership rules that protect every domain endpoint. Load before touching anything under auth/, user/, sync/, security or websocket configuration, or any endpoint that reads the current user.
---

# Authentication - NewTabLinks backend

## The shape of it

Three ways in, one kind of session out. Every path ends by issuing the same
`TokenPairDto`, so the rest of the application never knows how a client signed in.

```
CLASSIC                      GOOGLE                        EXTENSION
website form                 website button                extension form
   |                            |                             |
POST /auth/register       GET /oauth2/authorization/google    |
   |  status=PENDING           |  Google verifies             |
   |  activation mail          v                              |
   v                     success handler:                     |
GET /auth/activate       find/create account (ACTIVE)         |
   |  status=ACTIVE            |  mint WEB_SESSION_HANDOFF    |
   |                           v  redirect ?code=...          |
   |                     POST /auth/session-handoff           |
   |                           |                              |
   |                    POST /auth/extension-connect-codes    |
   |                           |  user reads "4F2K-9QX1" ---> |
   |                           |                    POST /auth/extension-connect
   v                           v                              v
              ----------  TokenPairDto  ----------
              access JWT (15 min)  +  refresh token (30 days)
```

`POST /auth/login` (username **or** email + password) is the extension's normal path.

Clients may send **`X-Device-Name`** on any endpoint that issues tokens. It is optional and never
trusted - a browser cannot read its host's name - it only labels a row in the user's device list.

## Why the connect code exists

A user who registered through Google **has no password**, so the extension's username/password
form can never sign them in. The connect code is the bridge: the website mints a short code for
an already-signed-in user, they retype it into the extension, and the extension trades it for a
normal token pair. The extension gains one paste box and no OAuth code at all.

## Passwords

One service, `PasswordService`, covers all three cases, and **all three sign every device out**.
That is the point: a password is usually changed precisely when the old one cannot be trusted, so
leaving the sessions it created alive would achieve nothing.

| Endpoint | Auth | Notes |
|---|---|---|
| `POST /auth/password/reset-request` | public | Always 202. Says nothing about whether the address exists |
| `POST /auth/password/reset-confirm` | public | Token single use, 1 hour |
| `POST /auth/password/change` | **bearer** | Sets *or* changes |

**"Set a password" is not a separate feature.** `POST /auth/password/change` requires
`currentPassword` only when the account already has one. An account created through Google has
none, so its owner supplies just `newPassword` - there is nothing to prove, because they are
already authenticated. That branch is three lines in `PasswordService.setOrChangePassword`.

A provider account whose address is the `…@no-address.invalid` placeholder **cannot** be reset by
mail - no link can reach it. Such an account can only gain a password while signed in. The reset
endpoint still answers 202, and logs why nothing was sent.

## Devices - where the account has been used

`GET /api/v1/users/me/devices`, `DELETE /api/v1/users/me/devices/{id}`.

A device is the pair **`(deviceName, browserName)`** within one account. One machine running three
browsers is three devices, because that is what the user experiences: each browser holds its own
tokens and is signed out separately. Signing in again from the same pair **updates** that row
rather than adding one.

**This is a history, not a live session list.** A device stays listed after its tokens are revoked
or expire, because the useful question is "where has my account been used". `signedIn` says which
still hold a usable session; `lastUsedAt` says when each was last seen.

There is also a mechanical reason the entity exists: refresh tokens **rotate on every use**, so a
client refreshing every fifteen minutes would otherwise leave thousands of unrelated token rows
per month. Tokens hang off a device; the device is what a user sees.

Two behaviours worth not breaking, both covered by the live checks:

- **Refreshing must not spawn a device.** `AuthenticationService.refresh` takes the device from the
  presented *token*, never from request headers - a client that changed its reported device name
  mid-session would otherwise silently create a second device on every refresh.
- **Signing a device out keeps the row** and only revokes its tokens.

There is **no device limit**, deliberately, and none should be added.

## Websockets

The extension is told its data changed so it can pull a fresh snapshot. Without this, a second
browser shows stale links until something makes it re-read.

**Authentication happens on the STOMP `CONNECT` frame, not at the HTTP handshake.** A browser's
`WebSocket` constructor takes a URL and nothing else, so no `Authorization` header can be set on
it, and putting the token in the query string would write it into every access log and proxy trace
on the way. STOMP's `CONNECT` is an application-level frame with its own headers, so the token
travels there and `StompAuthenticationInterceptor` checks it. The HTTP handshake at `/ws` is
therefore `permitAll` **on purpose** - it opens a socket that can do nothing until a valid
`CONNECT` arrives. Do not "fix" that by securing the handshake path.

The principal's name is set to the **user id**, which is what makes `convertAndSendToUser` reach
the right client.

```
client                                        server
  WebSocket ws://…/ws  (subprotocol v12.stomp)
  CONNECT  Authorization: Bearer <access token>   -> validated here, else ERROR
  SUBSCRIBE /user/queue/refresh
                    <- MESSAGE {"changedAt":…,"origin":null}
```

Sending is driven by an event, not by the websocket layer: services call
`UserDataChangePublisher.publishChangeFor(ownerId)` after a mutation, and `UserRefreshNotifier`
listens at **`TransactionPhase.AFTER_COMMIT`**. That phase is load-bearing - notifying inside the
transaction races the commit, and a browser told to re-read could fetch a snapshot taken before
the change was visible and then sit on stale data with no further signal coming. It also means a
rolled-back transaction sends nothing, which is correct.

The notification deliberately **carries no data**. Sending the change itself would mean a patch
format, ordering guarantees, and handling a client that missed one - all of which the snapshot
endpoint already solves.

**Every replica delivers, not just the one that committed.** The simple broker keeps
subscriptions in the pod's heap, so a browser is reachable only from the pod it connected to.
`UserRefreshNotifier` therefore does not send to the websocket: it publishes
`UserDataChangedMessage` through the messaging port (`common/messaging`, PostgreSQL
`NOTIFY`/`LISTEN` today), and `UserRefreshRelay` in every pod passes it to that pod's sessions.
Sticky sessions would not have helped: the browser that pushes and the browser that must hear
about it are different clients, pinned to different pods.

## Rules that must not be quietly undone

- **Identity is `(provider, provider_user_id)`, never the email address.** Addresses change and
  get reassigned. Google's stable key is the `sub` claim.
- **An account is linked to a provider on a verified address only.** Linking on an unverified one
  would let anyone who can claim an address at a provider take over the matching local account.
  There is a test for this (`ProviderSignInServiceTest`); it is not decoration.
- **`password_hash` is nullable.** Provider-only accounts have none. `UserDto.hasPassword` tells
  the website whether to offer "set" or "change".
- **Email is unique and not null**, so a provider that returns no address (Facebook routinely
  does) gets an unroutable `...@no-address.invalid` placeholder.
- **Only `ACTIVE` accounts authenticate.** That is what makes activation mean something.
- **Tokens are stored hashed** (SHA-256, `TokenHasher`) - activation tokens, refresh tokens and
  single-use codes alike. Plain SHA-256 is correct because these are full-entropy random values;
  passwords use `PasswordEncoder` instead because humans choose them.
- **Refresh rotates.** Using a refresh token revokes it and issues a new one.
- **No enumeration.** Duplicate *email* on registration returns the same 202 as success and mails
  a notice to the address instead. Duplicate *username* is refused openly (409) because a
  username is public. Login answers "Invalid credentials" for every cause, and verifies against a
  dummy hash when the account is missing so the timing matches.

## Ownership - how domain endpoints are protected

`AuthenticatedUserProvider.getAuthenticatedUserId()` reads the subject of the validated JWT. That
is the **only** source of caller identity. No endpoint accepts an owner id from the caller.

Every domain lookup is ownership-scoped **in the query**, not checked afterwards:

```java
// GroupRepository
@Query("select linkGroup from GroupEntity linkGroup "
     + "where linkGroup.id = :groupId and linkGroup.environment.owner.id = :ownerId")
Optional<GroupEntity> findByIdAndOwnerId(...);
```

Someone else's row is reported **404, never 403** - a 403 would confirm the identifier is real.
Verified end to end: a second user gets 404 on read, update, delete, and on creating a child
inside another user's environment.

## Configuration

All of it under `newtablinks.auth.*`, bound to `AuthenticationProperties`.

| What | Default | Note |
|---|---|---|
| access token | 15 min | short; nothing can revoke one early |
| refresh token | 30 days | revocable, rotated on use |
| activation link | 24 h | single use |
| web handoff code | 2 min | exchanged immediately |
| extension connect code | 10 min | long enough to retype |
| provider sign-in | 10 min | redirect to Google and back; how long the sign-in cookie is honoured |
| password reset link | 1 h | single use; shorter than activation on purpose |
| max failed logins | 10 | per-account lockout; **not** a device limit |

**`newtablinks.auth.jwt-signing-secret` has a development default and MUST be overridden in every
deployment** - anyone holding it can mint tokens for any account. Startup fails if it is under
32 bytes.

## Two traps already hit here

- **Google credentials are validated at startup.** Declaring
  `spring.security.oauth2.client.registration.google.client-id` with an empty default makes the
  application *refuse to boot*, not run without Google. So the registration is not declared in
  `application.properties` at all, and `SecurityConfiguration` wires `oauth2Login` only when a
  `ClientRegistrationRepository` bean exists. Enable Google purely through environment variables.
- **`management.health.mail.enabled=false`.** The mail health indicator turns the aggregate
  `/actuator/health` DOWN when no SMTP relay answers, and the Helm chart probes that path for
  **liveness** - so an unreachable relay would crash-loop a healthy API. Mail failures are caught
  and logged instead.

## Mail

`ActivationEmailSender` is an interface; `SmtpActivationEmailSender` sends through plain SMTP
(`spring.mail.*`), so the transactional provider is deployment configuration, never code.
**`newtablinks.mail.enabled` defaults to `false`**, which logs the message - activation link
included - instead of sending it. That is how the whole flow is testable with no provider:

```bash
grep -oP 'activate\?token=\K[^\s]+' app.log | tail -1
```

Sending failures never fail the request: registration has already succeeded, and rolling it back
because a relay blinked would lose the account and leak that the address exists.

## Google sign-in keeps no session

The authorization request Spring saves before sending the browser to Google - state, PKCE
verifier, nonce - lives in a cookie, not the `HttpSession`
(`common/security/CookieOAuth2AuthorizationRequestRepository`). The session version tied the
sign-in to the pod that started it: with two replicas, Google's callback reached the other one
about half the time and failed with `authorization_request_not_found`.

- **Sealed with AES-GCM**, so the browser can neither read the verifier nor forge a request. The
  key is derived from `jwt-signing-secret` under a fixed label - every replica already shares
  that secret, and the label keeps the derived key useless for minting tokens. Rotating the
  signing secret therefore also fails any sign-in in flight at that moment, which is harmless.
- **JSON of named fields, never Java serialization**, although `OAuth2AuthorizationRequest`
  offers it: the cookie comes back from a client, and deserialising a client's bytes into
  arbitrary objects is a code-execution hazard however they are sealed.
- `HttpOnly`, `Secure`, `Path=/login/oauth2`, and **`SameSite=Lax`** - the callback is a
  top-level navigation from Google, which `Strict` would strip the cookie from.
- `Secure` is unconditional, so developing sign-in needs `https`, or `http://localhost`, which
  browsers treat as secure.

## Setting Google up

1. Google Cloud console -> OAuth consent screen (name, logo, support email, **privacy policy URL**).
2. OAuth client ID, type *Web application*, redirect URI
   `<public base url>/login/oauth2/code/google`.
3. Scopes `openid email profile` only - these need **no Google review**; review is for sensitive
   and restricted scopes.
4. Set `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID` and `..._CLIENT_SECRET`
   from Infisical.

## Deliberate omissions

Not oversights. Do not add them without being asked:

- **Changing your email address** - the owner decided users may not do this at all.
- **Rate limiting beyond the per-account failed-login counter.** That counter is brute-force
  protection and has nothing to do with device counts; there is no device limit and none is
  wanted. Removing the counter would let anyone grind passwords against an account forever.

Genuinely still missing: pruning spent tokens (`emailed_token`, `single_use_code` and revoked
`refresh_token` rows accumulate forever), and `origin` on the refresh notification is always
`null`, so a browser cannot yet recognise and skip the echo of its own change.
