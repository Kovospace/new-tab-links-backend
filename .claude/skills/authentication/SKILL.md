---
name: authentication
description: How identity works in the NewTabLinks backend - the account model, classic registration with email activation, Google sign-in, the extension connect code, JWT access and refresh tokens, and the ownership rules that protect every domain endpoint. Load before touching anything under auth/, user/, security configuration, or any endpoint that reads the current user.
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

## Why the connect code exists

A user who registered through Google **has no password**, so the extension's username/password
form can never sign them in. The connect code is the bridge: the website mints a short code for
an already-signed-in user, they retype it into the extension, and the extension trades it for a
normal token pair. The extension gains one paste box and no OAuth code at all.

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
| max failed logins | 10 | per-account lockout; there is no rate-limiting infra |

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

## Setting Google up

1. Google Cloud console -> OAuth consent screen (name, logo, support email, **privacy policy URL**).
2. OAuth client ID, type *Web application*, redirect URI
   `<public base url>/login/oauth2/code/google`.
3. Scopes `openid email profile` only - these need **no Google review**; review is for sensitive
   and restricted scopes.
4. Set `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID` and `..._CLIENT_SECRET`
   from Infisical.

## Still missing

Password reset, changing your email address, setting a password on a provider-only account,
listing and revoking individual sessions, and rate limiting beyond the per-account counter.
The websocket handshake is still unauthenticated - it carries no user identity yet.
