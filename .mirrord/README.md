# Running this backend inside the cluster — mirrord

These configs run **this source tree, on this machine**, as though it were the
`new-tab-links-backend` Pod in the kovostack cluster. The local JVM gets the deployed Pod's
environment variables, DNS and outgoing network, which means:

- **The Infisical secrets arrive on their own** — the JWT signing key, the Google OAuth client
  id and secret, the database credentials, the Brevo SMTP credentials, `ADMIN_USERNAME` /
  `ADMIN_PASSWORD`. Nothing has to exist on this machine.
- **`SPRING_DATASOURCE_URL` resolves.** It says `jdbc:postgresql://postgres:5432/newtablinks`,
  and `postgres` is a selector-less Service in the `new-tab-links-backend` namespace pointing at
  `172.17.0.1` — the VM's docker0 gateway. That name means nothing on a laptop; under mirrord it
  is resolved and dialled in the cluster.
- **Mail really sends**, through Brevo, with the deployed credentials.

`docker-compose.yml` in the website repository remains the other way to work, and is the better
one for anything destructive. This one is for when you need the *real* data.

## The usual loop

```bash
# terminal 1 — the SSH tunnel to the cluster API, kept open
ssh -N -L 6443:127.0.0.1:6443 vm

# terminal 2 — this backend, inside the cluster
export KUBECONFIG=~/.kube/config-kovostack
mirrord exec -- ./mvnw spring-boot:run

# terminal 3 — the website, unchanged
cd ~/IdeaProjects/new-tab-links-frontend && npm start
```

`mirrord exec` with no `-f` picks up `.mirrord/mirrord.json` automatically, as do the JetBrains
and VS Code plugins. Setup for the tunnel and the kubeconfig is in
`~/IdeaProjects/kovostack-infra-gitops/docs/mirrord.md`.

The website reaches `http://localhost:8080` exactly as it does against the compose stack — its
`DEFAULT_RUNTIME_CONFIGURATION` already points there — so **nothing in the frontend repository
changes**.

## What the two configs do

### `mirrord.json` — mirror

Incoming production requests are **copied** to the local process; the deployed Pod still serves
every one of them. Nothing users do is affected.

The four `feature.env.override` entries are the whole reason a locally-served page works against
this process:

| Overridden | Deployed value | Why it has to change |
|---|---|---|
| `NEWTABLINKS_SECURITY_ALLOWED_CORS_ORIGINS` | `https://new-tab-links.matejkovac.sk,chrome-extension://*` | `http://localhost:5173` is not in the deployed list, so every call from the dev server would be blocked by CORS |
| `NEWTABLINKS_WEBSOCKET_ALLOWED_ORIGIN_PATTERNS` | same | the STOMP websocket checks its own list |
| `NEWTABLINKS_WEB_BASE_URL` | `https://new-tab-links.matejkovac.sk` | activation and password-reset mails build their links from it; without this they point at the deployed site, not the one you are editing |
| `SERVER_FORWARD_HEADERS_STRATEGY` | `FRAMEWORK` (the property default) | nothing proxies this process. Trusting `X-Forwarded-*` on a directly reachable application lets any caller forge its own scheme and host — the same reason `docker-compose.yml` sets `NONE` |

`NEWTABLINKS_AUTH_JWT_ISSUER` is deliberately **not** overridden: leaving it as the deployed
issuer means a token minted here is accepted there and vice versa, which is what makes it
possible to sign in on the real site and keep working locally.

> **Mirror mode re-executes the request.** A copied `POST` is genuinely handled by this process,
> against the production database — the response is discarded, the write is not. On a quiet
> personal API that is unlikely to bite, but if you are only developing the website you never
> need incoming traffic at all. Set `"mode": "off"` under `feature.network.incoming` and the
> hazard disappears with no other loss.

### `steal.json` — filtered steal

Requests arriving at `api.new-tab-links.matejkovac.sk` are **taken** and answered by this
process instead of the deployed Pod — but only those carrying the header

```
X-Mirrord-Steal: 1
```

Everything else still reaches the real Pod, so the deployed API stays up while you work. That is
what makes this safe to leave running, and it is the reason for the filter.

```bash
mirrord exec -f .mirrord/steal.json -- ./mvnw spring-boot:run
curl -H 'X-Mirrord-Steal: 1' https://api.new-tab-links.matejkovac.sk/actuator/health
```

Useful for pointing the **deployed** website or a **published** extension build at local server
code — neither of which can be made to talk to `localhost`. A browser will not add the header on
its own; use an extension such as ModHeader, or delete the `http_filter` block to steal
everything. Deleting it takes the production API offline for as long as the session runs, so do
it deliberately and say so out loud if anyone else is using it.

`SERVER_FORWARD_HEADERS_STRATEGY` is `FRAMEWORK` here, not `NONE`: stolen requests really do
arrive through Traefik, so `X-Forwarded-*` is genuine and must be honoured or every generated
URL comes out as `http://` on the wrong host.

## The part mirrord does not solve

This process is **inside** production, not beside it:

- every write lands in the production database — there is only one;
- every mail is really delivered, to a real address;
- the production signing key, OAuth secret and admin password are in this process's memory, and
  in its logs if it ever dumps its environment.

Nothing in these files changes that, and no flag would. Use `docker-compose.yml` in the website
repository when you need to break things.

## Requirements

- The mirrord CLI, a kubeconfig for the cluster, and the SSH tunnel — see
  `~/IdeaProjects/kovostack-infra-gitops/docs/mirrord.md`.
- Nothing is installed in the cluster. mirrord creates a short-lived privileged
  `mirrord-agent-*` Pod in `new-tab-links-backend` for the duration of the session; ArgoCD
  ignores it and it is cleaned up 30 seconds after the session ends.

## Why there is no `$schema` key

mirrord's config schema sets `additionalProperties: false`, so a `$schema` line at the top of
these files is an unknown field and is rejected rather than ignored. Editors that want
completion should be pointed at
`https://raw.githubusercontent.com/metalbear-co/mirrord/main/mirrord-schema.json` through their
own settings instead. These three files were validated against that schema on 2026-09-03.
