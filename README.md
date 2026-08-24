# new-tab-links-backend

Backend service for the **NewTabLinks** Chrome extension
([`K0V0/NewTabGroupedLinks`](https://github.com/K0V0/NewTabGroupedLinks)) — stores and
synchronizes the user's environments, groups, subgroups and links, and handles registration
and sign-in.

Java 25 · Spring Boot 4.1 · PostgreSQL · Maven

---

## Running it locally

Nothing has to be configured. Every parameter has a working default, so the only real
requirement is a database:

```bash
docker run -d --name ntl-postgres \
  -e POSTGRES_DB=newtablinks -e POSTGRES_USER=newtablinks -e POSTGRES_PASSWORD=newtablinks \
  -p 5432:5432 postgres:17-alpine

./mvnw spring-boot:run
```

Then:

- API docs — <http://localhost:8080/swagger-ui.html>
- Health — <http://localhost:8080/actuator/health>

Mail is **disabled** by default, so activation links are written to the log instead of being
sent. That is how the registration flow is exercised without a mail provider:

```bash
grep -oP 'activate\?token=\K[^\s]+' app.log | tail -1
```

> **Note on this machine:** every JDK under `/usr/lib/jvm` is JRE-only (no `javac`). Maven
> needs a real JDK, so prefix commands with `JAVA_HOME=$HOME/.jdks/openjdk-26.0.2` — the build
> still targets release 25.

---

## Environment variables

Every value below is read as `${VARIABLE:default}`, so **the application starts with none of
them set**. A deployment overrides only what it needs, and never by editing
`application.properties`.

The **Required in deployment** column is what matters: ✅ means the default is wrong or unsafe
outside local development.

### Database

| Variable | Default | Required | Notes |
|---|---|:--:|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/newtablinks` | ✅ | In-cluster: `jdbc:postgresql://postgres:5432/newtablinks` |
| `SPRING_DATASOURCE_USERNAME` | `newtablinks` | ✅ | From the secret store |
| `SPRING_DATASOURCE_PASSWORD` | `newtablinks` | ✅ | **Secret.** From the secret store |
| `SPRING_DATASOURCE_DRIVER_CLASS_NAME` | `org.postgresql.Driver` | | |
| `SPRING_DATASOURCE_POOL_MAX_SIZE` | `10` | | Hikari maximum pool size |
| `SPRING_DATASOURCE_POOL_MIN_IDLE` | `2` | | Hikari minimum idle |

### JPA / schema

| Variable | Default | Required | Notes |
|---|---|:--:|---|
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `update` | ✅ | **Set to `validate`** once the Flyway migrations image exists. `update` lets Hibernate rewrite a schema it does not own |
| `SPRING_FLYWAY_ENABLED` | `false` | | Leave off — migrations run as an init container, not in the app |
| `SPRING_JPA_OPEN_IN_VIEW` | `false` | | |
| `SPRING_JPA_SHOW_SQL` | `false` | | Debugging only |
| `SPRING_JPA_FORMAT_SQL` | `false` | | Debugging only |
| `SPRING_JPA_TIME_ZONE` | `UTC` | | |

### Authentication

| Variable | Default | Required | Notes |
|---|---|:--:|---|
| `NEWTABLINKS_AUTH_JWT_SIGNING_SECRET` | *(development placeholder)* | ✅ | **Secret, and the single most important value here.** Anyone holding it can mint tokens for any account. The app refuses to start if it is under 32 bytes |
| `NEWTABLINKS_AUTH_JWT_ISSUER` | `https://newtablinks.local` | ✅ | The service's public base URL |
| `NEWTABLINKS_AUTH_ACCESS_TOKEN_LIFETIME` | `PT15M` | | ISO-8601 duration |
| `NEWTABLINKS_AUTH_REFRESH_TOKEN_LIFETIME` | `P30D` | | Rotated on every use |
| `NEWTABLINKS_AUTH_ACTIVATION_TOKEN_LIFETIME` | `PT24H` | | Emailed activation link |
| `NEWTABLINKS_AUTH_PASSWORD_RESET_TOKEN_LIFETIME` | `PT1H` | | Emailed reset link. Shorter than activation on purpose: a reset link takes over a *live* account, an activation link only finishes creating an empty one |
| `NEWTABLINKS_AUTH_WEB_HANDOFF_LIFETIME` | `PT2M` | | Code the website trades after a Google sign-in |
| `NEWTABLINKS_AUTH_EXTENSION_CONNECT_LIFETIME` | `PT10M` | | Code the user retypes into the extension |
| `NEWTABLINKS_AUTH_MAX_FAILED_LOGINS` | `10` | | Per-account lockout; there is no other rate limiting |

Generate a signing secret with:

```bash
openssl rand -base64 48
```

### Google sign-in (optional)

Not declared in `application.properties` on purpose — Spring Boot **validates client
registrations at startup and refuses to boot on an empty client id**, so declaring them with
empty defaults would make Google mandatory just to start. Set all three, or none.

| Variable | Required | Notes |
|---|:--:|---|
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID` | — | Enables the Google button |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET` | — | **Secret** |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_SCOPE` | — | `openid,email,profile` |

With none of them set the service starts normally and logs a warning; password sign-in still
works. With them set it logs `Provider sign-in is enabled`.

### The public website

The backend builds absolute links into the website (activation mail, OAuth redirect), so it
has to know where the site lives.

| Variable | Default | Required | Notes |
|---|---|:--:|---|
| `NEWTABLINKS_WEB_BASE_URL` | `http://localhost:5173` | ✅ | No trailing slash |
| `NEWTABLINKS_WEB_ACTIVATION_PATH` | `/activate` | | Receives `?token=…` |
| `NEWTABLINKS_WEB_OAUTH_CALLBACK_PATH` | `/auth/callback` | | Receives `?code=…` |
| `NEWTABLINKS_WEB_PASSWORD_RESET_PATH` | `/reset-password` | | Receives `?token=…` |

### CORS

| Variable | Default | Required | Notes |
|---|---|:--:|---|
| `NEWTABLINKS_SECURITY_ALLOWED_CORS_ORIGINS` | `http://localhost:5173,chrome-extension://*` | ✅ | Comma-separated. Tighten `chrome-extension://*` to the published extension id |

### Mail

| Variable | Default | Required | Notes |
|---|---|:--:|---|
| `NEWTABLINKS_MAIL_ENABLED` | `false` | ✅ | `false` logs messages instead of sending them |
| `NEWTABLINKS_MAIL_FROM_ADDRESS` | `no-reply@newtablinks.local` | ✅ | Must be an address the provider has authorised |
| `NEWTABLINKS_MAIL_FROM_NAME` | `NewTabLinks` | | |
| `SPRING_MAIL_HOST` | `localhost` | ✅ | Provider's SMTP host |
| `SPRING_MAIL_PORT` | `587` | | |
| `SPRING_MAIL_USERNAME` | *(empty)* | ✅ | |
| `SPRING_MAIL_PASSWORD` | *(empty)* | ✅ | **Secret** |
| `SPRING_MAIL_SMTP_AUTH` | `true` | | |
| `SPRING_MAIL_SMTP_STARTTLS` | `true` | | |

### HTTP, docs, websocket, operations

| Variable | Default | Notes |
|---|---|---|
| `SERVER_PORT` | `8080` | Matches the Helm chart's `containerPort` |
| `SPRING_APPLICATION_NAME` | `new-tab-links-backend` | |
| `SERVER_ERROR_WHITELABEL_ENABLED` | `false` | |
| `SPRING_WEB_RESOURCES_ADD_MAPPINGS` | `false` | |
| `NEWTABLINKS_OPENAPI_TITLE` | `NewTabLinks backend API` | |
| `NEWTABLINKS_OPENAPI_VERSION` | `v1` | |
| `NEWTABLINKS_OPENAPI_DESCRIPTION` | *(one-line description)* | |
| `SPRINGDOC_API_DOCS_PATH` | `/v3/api-docs` | |
| `SPRINGDOC_SWAGGER_UI_PATH` | `/swagger-ui.html` | |
| `SPRINGDOC_SWAGGER_UI_ENABLED` | `true` | Consider `false` in production |
| `SPRINGDOC_SWAGGER_UI_OPERATIONS_SORTER` | `alpha` | |
| `SPRINGDOC_SWAGGER_UI_TAGS_SORTER` | `alpha` | |
| `NEWTABLINKS_WEBSOCKET_ENDPOINT_PATH` | `/ws` | |
| `NEWTABLINKS_WEBSOCKET_USER_DESTINATION_PREFIX` | `/user` | |
| `NEWTABLINKS_WEBSOCKET_ALLOWED_ORIGIN_PATTERNS` | `chrome-extension://*` | |
| `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` | `health,info` | |
| `MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED` | `true` | |
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS` | `never` | `always` is useful when debugging a DOWN health |
| `MANAGEMENT_HEALTH_MAIL_ENABLED` | `false` | **Leave off.** Kubernetes probes `/actuator/health` for *liveness*, so an unreachable SMTP relay would crash-loop a healthy API |
| `MANAGEMENT_INFO_BUILD_ENABLED` | `true` | Publishes the expected Flyway schema version |
| `MANAGEMENT_INFO_JAVA_ENABLED` | `true` | |
| `LOGGING_LEVEL_ROOT` | `INFO` | |
| `LOGGING_LEVEL_APPLICATION` | `INFO` | Level for `com.kovospace.newtablinks` |

### Minimum viable deployment

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/newtablinks
SPRING_DATASOURCE_USERNAME=…
SPRING_DATASOURCE_PASSWORD=…            # secret
NEWTABLINKS_AUTH_JWT_SIGNING_SECRET=…   # secret, 32+ bytes
NEWTABLINKS_AUTH_JWT_ISSUER=https://api.newtablinks.example
NEWTABLINKS_WEB_BASE_URL=https://newtablinks.example
NEWTABLINKS_SECURITY_ALLOWED_CORS_ORIGINS=https://newtablinks.example,chrome-extension://<extension-id>
NEWTABLINKS_MAIL_ENABLED=true
NEWTABLINKS_MAIL_FROM_ADDRESS=no-reply@newtablinks.example
SPRING_MAIL_HOST=…
SPRING_MAIL_USERNAME=…
SPRING_MAIL_PASSWORD=…                  # secret
```

---

## What has to be set up elsewhere

None of the following lives in this repository.

### 1. Google Cloud Console — for "Sign in with Google"

<https://console.cloud.google.com/> → APIs & Services.

1. **OAuth consent screen** — app name, logo, support email, and a **privacy policy URL**
   (mandatory) plus a terms URL. Publish it, or add test users while it stays in testing.
2. **Credentials → Create OAuth client ID → Web application.**
3. **Authorised redirect URI** — exactly:

   ```
   https://<this service's public base url>/login/oauth2/code/google
   ```

   Spring Security builds this path itself; it is not configurable here. Locally it is
   `http://localhost:8080/login/oauth2/code/google`.
4. **Scopes: `openid`, `email`, `profile` only.** These are *not* sensitive or restricted, so
   **no Google verification review is required**. Asking for anything more (Gmail, Drive,
   contacts) triggers a review process that takes weeks.
5. Put the client id and secret into the secret store (below).

Adding Facebook or GitHub later is the same shape: they are built-in Spring Security
providers, so it is console setup plus three environment variables and one new enum constant —
no change to the extension.

### 2. A transactional email provider — for activation links

**Nothing in the cluster sends mail today.** Sending from a self-hosted relay on a home-lab IP
is reliably binned by Gmail and Outlook, so this needs a real provider (Brevo, Resend, Mailgun,
Postmark — all have a free tier well above what this needs).

1. Create the account and verify the sending **domain** (not just an address).
2. Add the DNS records the provider gives you — **SPF**, **DKIM**, and ideally **DMARC**.
   Without them activation mail lands in spam, which looks exactly like the feature being
   broken.
3. Take the SMTP host, username and password into the secret store.
4. Set `NEWTABLINKS_MAIL_ENABLED=true` and a `NEWTABLINKS_MAIL_FROM_ADDRESS` on the verified
   domain.

### 3. PostgreSQL

Runs **outside** the cluster on the VM, as the other Kovospace apps do. Needs a database and a
user:

```sql
CREATE DATABASE newtablinks;
CREATE USER newtablinks WITH ENCRYPTED PASSWORD '…';
GRANT ALL PRIVILEGES ON DATABASE newtablinks TO newtablinks;
```

The pod reaches it through the chart's `externalServices` block, which requires an **IP
address, not a hostname** (the other apps use `172.17.0.1`, the docker0 gateway).

### 4. Infisical — the secret store

The Helm chart syncs every key in the app's Infisical folder into one Kubernetes Secret and
injects it wholesale with `envFrom`, so **adding a secret needs no chart or manifest change**.

Create the folder `/new-tab-links-backend` and put in it:

- `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- `NEWTABLINKS_AUTH_JWT_SIGNING_SECRET`
- `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`
- `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID` / `..._CLIENT_SECRET`

Non-secret values belong in the GitOps `values.yaml` instead.

### 5. GitOps — how a build reaches the cluster

CI never talks to the cluster: it builds the image, pushes it, and commits the new tag into
[`Kovospace/kovostack-infra-gitops`](https://github.com/Kovospace/kovostack-infra-gitops), where
Argo CD picks it up. Two files have to be added there:

- `applications/new-tab-links-backend.yaml` — the Argo CD Application (copy
  `paster-backend.yaml`, change the names)
- `applications/new-tab-links-backend/values.yaml` — chart values

The values file needs at least:

```yaml
name: new-tab-links-backend
image: new-tab-links-backend
containerPort: 8080
host: api.newtablinks.example

# Required, or NO probes are rendered at all — an unset healthPath disables them
# rather than defaulting to "/".
healthPath: /actuator/health

externalServices:
  postgres:
    address: 172.17.0.1   # an IP, never a hostname
    port: 5432

env:
  SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/newtablinks
  NEWTABLINKS_AUTH_JWT_ISSUER: https://api.newtablinks.example
  NEWTABLINKS_WEB_BASE_URL: https://newtablinks.example
  NEWTABLINKS_MAIL_ENABLED: "true"
  # …the rest of the non-secret values
```

`versions/new-tab-links-backend.yaml` is written by CI — do not edit it by hand.

### 6. GitHub — already done

`REGISTRY_USER`, `REGISTRY_PASSWORD` and `GITOPS_DEPLOY_KEY` are **organization-level** secrets
and reach the pipeline through `secrets: inherit`. Nothing secret is configured in this
repository. The workflow is `workflow_dispatch` only, and builds from the default branch.

### 7. DNS and TLS

An A record for the API host pointing at the cluster. TLS is issued automatically by
cert-manager, so the record must exist **before** the app is deployed or the ACME challenge
fails.

### 8. Still to exist

- **Flyway migrations repository** — does not exist yet. Until it does,
  `SPRING_JPA_HIBERNATE_DDL_AUTO=update` maintains the schema. The version this build expects
  is the Maven property `flyway.migrations.schema.version`, published at `/actuator/info` as
  `build.flywayMigrationsSchemaVersion`.
- **Helm chart init-container support** — `chart-app-1.3.0` cannot render an init container at
  all, so the migrations image cannot be wired up until the chart gains that.
- **The website** — a separate project, not yet created. The backend already builds links into
  it, so it has to serve four things at the paths configured above:

  | Path | What it does |
  |---|---|
  | *(anywhere)* | Registration form → `POST /api/v1/auth/register`; Google button → link to `<api>/oauth2/authorization/google` |
  | `/activate?token=…` | Calls `GET /api/v1/auth/activate` |
  | `/auth/callback?code=…` | Calls `POST /api/v1/auth/session-handoff` and stores the returned tokens |
  | `/reset-password?token=…` | Calls `POST /api/v1/auth/password/reset-confirm` |

  Plus, for a signed-in user: `POST /api/v1/auth/extension-connect-codes` to show the connect
  code, and `GET /api/v1/users/me/devices` to list where the account has been used.

---

## Documentation

`CLAUDE.md` and `.claude/skills/` hold the working detail: architecture and code standards,
the deployment pipeline, and how authentication is put together.
