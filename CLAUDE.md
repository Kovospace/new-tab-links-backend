# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

## What this project is

Backend service for **NewTabLinks** — the server side that stores and synchronizes user data
for the `NewTabGroupedLinks` Chrome extension (new tab page with grouped links).

**Status: running, authenticated application.** Module structure, JPA persistence, CRUD for the
whole domain, registration and sign-in, OpenAPI docs, the Dockerfile and the CI pipeline all
exist and are verified working.

**Two-way synchronization with the extension exists.** `POST /api/v1/sync/push` accepts a batch
of changes, and every mutation publishes a change event that `UserRefreshNotifier` delivers over
the websocket to the account's other browsers. The extension is the client of both.

**Every domain endpoint requires a bearer token, and the caller's identity comes only from that
token.** Nothing accepts an owner id from the caller. See the `authentication` skill.

## Paired repository — the Chrome extension

The client this backend serves lives in its own repository:

- **Remote:** `git@github.com:K0V0/NewTabGroupedLinks.git`
- **Local checkout:** `/home/kovo/IdeaProjects/NewTabGroupedLinks`
- Its `CLAUDE.md` is authoritative for the extension's architecture, data model and code style.
- It has a mirror-image `backend-sync` agent (`.claude/agents/backend-sync.md`) that does the
  extension-side half of any cross-repo change.

Note the owner mismatch: the extension is under **K0V0**, this backend under **Kovospace**.
Both remotes are SSH and `~/.ssh/id_ed25519` authenticates for both — no extra key or token.

Work with the extension checkout through `git -C /home/kovo/IdeaProjects/NewTabGroupedLinks <cmd>`
rather than `cd`. Reading it is allowed via `permissions.additionalDirectories` in the gitignored
`.claude/settings.local.json`; if Claude Code refuses to read that path, that entry is missing.

**Read the extension's data model before designing any API.** Its entities are flat
`Record<string, T>` maps keyed by UUID (`src/backend/entity/AppStateEntity.ts`): `environments`,
`groups`, `subgroups`, `links` — grouped, one level above, into `profiles`.

The extension has no `updatedAt`, no revision and no delete tombstones, and does not need them.
A client pulls whole profiles and takes the snapshot as authoritative, so a deleted record is
one that is simply absent; and it pushes by comparing its state against a stored baseline of
what the server last confirmed, so a record missing from the current state *is* the deletion.
Do not add tombstones on the assumption they are owed.

## How work arrives

Tasks come as assignment files in `.claude/assignments/*.md`. Each one is a numbered increment;
later assignments extend what earlier ones built. Read the referenced assignment in full before
starting, and do only what it asks — assignments deliberately defer work to later ones.

## Tech stack

Decided (verified 2026-08-23):

| Concern | Choice |
|---|---|
| Language | **Java 25** (latest LTS) — bytecode target; see the JDK caveat below |
| Framework | **Spring Boot 4.1.x** (latest stable; 4.1.1 released 2026-08-20) |
| API docs | **springdoc-openapi 3.1.0** — OpenAPI 3 + Swagger UI at `/swagger-ui.html` |
| Build tool | **Maven**, via the `./mvnw` wrapper (no `mvn` on this machine) |
| Mapping | **MapStruct 1.6.3**, compile-time, `unmappedTargetPolicy=ERROR` |
| Database | **PostgreSQL** (house standard, runs outside the cluster) |
| Security | Spring Security 7, JWT (HS256, Nimbus — no third-party JWT lib) |
| Provider sign-in | `oauth2-client`, Google; optional, enabled only by env vars |
| Mail | `spring-boot-starter-mail`, plain SMTP; provider is deployment config |
| Container | `Dockerfile`, multi-stage temurin 25, deployed by GitOps |

Caveats to keep in mind:

- The build targets **release 25** via `java.version` in `pom.xml`; never let it drift to 26.
- Spring Boot has **no LTS labels** — it ships a release train with ~1 year of OSS support.
  "Latest stable" (4.1.x) is the standing interpretation of the assignment's "latest stable
  (and LTS)". The 3.5.x line is the fallback if a dependency turns out not to support 4.x.
- springdoc **3.x** is the line that targets Spring Boot 4; 2.8.x is for Boot 3.
- **Spring Boot 4 renamed the starters.** It is `spring-boot-starter-webmvc` (not `-web`), and
  the old `spring-boot-starter-test` is split into one `-test` starter per slice
  (`spring-boot-starter-webmvc-test`, `-data-jpa-test`, …). Boot 3 names will not resolve.
- **Local builds need an explicit `JAVA_HOME`.** Every JDK under `/usr/lib/jvm` on this machine
  is **JRE-only** — there is no `javac` in any of them. The only real JDK is
  `~/.jdks/openjdk-26.0.2`, so local Maven runs need
  `JAVA_HOME=$HOME/.jdks/openjdk-26.0.2 ./mvnw …`, which compiles with `--release 25`. The
  Docker build uses `eclipse-temurin:25-jdk` and is unaffected.

## Architecture

**Module-per-feature, each module holding all of its own layers** — the same layout as the
`paster-backend` project:

```
com.kovospace.newtablinks
├── common/        config (OpenAPI, WebSocket, security), exceptions, models, security, utils
├── auth/          registration, activation, sign-in, passwords, tokens, provider sign-in
├── user/          the account itself, its provider identities, and its devices
├── environment/   workspaces, owned by a user
├── group/         titled boxes of links, owned by an environment
├── subgroup/      collapsible sections, owned by a group
├── link/          the bookmarks themselves
├── profile/       named sets of environments, the top of the hierarchy and the extension's own
└── sync/          whole-account snapshot, the pushed change batch, and the change event that
                   drives websocket pushes

each feature module: controllers/ services/ repositories/ models/ dtos/ mappers/ utils/
```

**MVC, layered, one direction only:**

```
Controller (@RestController, DTOs only)
    → Service (business logic, transactions)
        → Repository (persistence)
            → Entity
```

- Controllers never touch entities or repositories; services never see HTTP types.
- Request DTOs go in, response DTOs come out — **entities never leave the service layer**.
- **Mappers are one-directional** (entity → DTO only). Building an entity from a request needs
  decisions a mapper should not make — resolving parents, assigning positions — so services do it.
- **Cross-module access goes service → service, never service → another module's repository.**
  Each service exposes a `getRequired<X>Entity(UUID)` method for its siblings; that is the only
  way an entity crosses a module boundary.
- A `utils/` package exists only where a module actually has a utility. Empty layer folders are
  not created for symmetry.
- Cross-cutting helpers go in `common/utils`; if a service grows past one clear responsibility,
  split it into a second service rather than letting it sprawl.

### Domain notes

- Every entity extends `common/models/AbstractAuditableEntity` — UUID id, `createdAt`,
  `updatedAt`, maintained by JPA lifecycle callbacks.
- `updatedAt` exists **specifically for sync**: it is what a client compares against to find what
  changed. The extension has no such field, which is the gap noted above.
- Ordering is a plain `position` int, appended via `common/utils/DisplayPositionCalculator`.
  Positions are *not* guaranteed dense — deletions leave gaps, and only relative order matters.
- A link's siblings are the other links of the same subgroup, or the group's other *direct*
  links when it has no subgroup. Positions are scoped to that sibling set.
- **`group` is a JPQL reserved word.** The fields are therefore named `parentGroup` /
  `parentSubgroup`, not `group` / `subgroup`. Do not rename them back.
- **Every domain lookup is ownership-scoped in the query** (`findByIdAndOwnerId`), and a row
  belonging to somebody else is reported as **404, not 403** — a 403 confirms the id is real.
  Never add an endpoint that resolves a domain object without the owner in the same query.
- **Every mutation calls `userDataChangePublisher.publishChangeFor(ownerId)`.** That is what
  pushes a refresh to the user's other browsers; a new mutating method without it leaves other
  devices silently stale. At most one announcement per account survives a transaction, so a push
  of four hundred operations sends one notification — and the overload taking an `originDeviceId`
  must be called *before* the work it describes, or the anonymous announcements behind it win
  and the pushing device cannot recognise its own echo.
- **The sync push accepts client-assigned identifiers**, the one place anything does. It is safe
  because every lookup is still ownership-scoped: an identifier already taken by another account
  is not an error but a remap, stored under a server-generated identifier and reported back.
  Nothing else in the application may take an identifier from a caller.

### Configuration

Every parameter in `application.properties` is declared as `${ENVIRONMENT_VARIABLE:default}`, so
the application starts with no configuration at all and a deployment overrides only what it needs
by setting environment variables. Never add a parameter without a default, and never let a
deployment edit this file.

## Skills

- **`java-code-standards`** — load before writing or reviewing any Java.
- **`deployment-pipeline`** — load before touching build, CI, Docker or deployment.
- **`authentication`** — load before touching `auth/`, `user/`, `sync/`, security or websocket
  config, or any endpoint that reads the current user.

## Code style

Full rules live in the **`java-code-standards` skill** (`.claude/skills/java-code-standards/`) —
load it before writing or reviewing Java. In short: SOLID, Javadoc on every type and method,
long descriptive names over short cryptic ones, short methods, small classes, no spaghetti.

## Git rules — hard, non-negotiable

- Pull, fetch and checkout freely. **Push only to `feature/**` or `bugfix/**` branches.**
- **Never push to `main`/`master`** — not with `--force`, not via `git push origin HEAD:main`.
  `main` is reached through PRs only. Same rule applies in the extension repo.
- A `PreToolUse` hook (`.claude/hooks/block-push-to-main.sh`, wired in `.claude/settings.json`)
  denies any `git push` whose destination resolves to `main` or `master`. It is a backstop,
  not a substitute for following the rule.
- **Cross-repo branch naming:** when a change spans both repositories, use the *same* branch
  name in each so they stay in step. If the other repo is on `main` and there is no name to
  mirror, ask the user rather than inventing one.
- Never merge or rebase onto `main`/`master` locally; never delete remote branches.
- Commit only when asked. Confirm the branch with `git rev-parse --abbrev-ref HEAD` first.
- `gh` is installed and authenticated as **K0V0** over SSH, so a PR can be opened directly.
  Opening one is still the user's call to make, like any push.

## Deployment

CI builds the image and commits its tag to a GitOps repository; Argo CD rolls it out. Nothing in
CI talks to the cluster. Details — the shared pipeline, the Helm chart, the registry, and the
Flyway init-container contract — are in the **`deployment-pipeline` skill**
(`.claude/skills/deployment-pipeline/`). Load it before touching `Dockerfile`,
`.github/workflows/`, or anything about how a change reaches the cluster.

The GitOps repository is checked out locally and may be used from here:

- **Remote:** `git@github.com:Kovospace/kovostack-infra-gitops.git`
- **Local checkout:** `/home/kovo/IdeaProjects/kovostack-infra-gitops`, reachable via
  `permissions.additionalDirectories`. Work with it through `git -C … <cmd>`, not `cd`.
- Read it freely — deployed image tag, Helm values, the Flyway init-container contract.
- **Changes there are made by its `devops-engineer` agent** (`.claude/agents/devops-engineer.md`
  in that repo), not by editing cluster resources from this side. Argo CD reconciles that repo's
  `main`, so a commit there *is* a deployment.

## Known gaps and deferred decisions

Deliberately not built yet. Do not treat any of these as oversights to quietly fix:

- **Email change is refused by design**, not missing: users may not change their address.
- **No device limit, and no general rate limiting.** Two things exist and neither is that: the
  per-account failed-login counter (brute-force protection, unrelated to device counts — do not
  remove it), and the visitor token, which meters only registration and the username lookup.
  Deliberately **not** per IP address: carrier-grade NAT puts whole neighbourhoods behind one
  address, so counting by address would punish real users far more than anyone it aimed at. Any
  proposal to add IP-based limiting has to answer that first.
- **Spent tokens are never pruned.** `emailed_token`, `single_use_code` and revoked
  `refresh_token` rows accumulate forever; a cleanup job is still owed. `visitor_token` is the
  exception and the template — `VisitorTokenCleanupScheduler` sweeps it on a timer.
- **The websocket broker is in-memory, so single-replica only.** Scaling out silently stops
  delivering to clients on the other pod.
- **Conflicts are resolved by arrival order**, and nothing detects them. Two devices editing the
  same link means the later push wins and the earlier device is told to re-read. There is no
  merge, no version check and no report that it happened; that is a deliberate trade, not an
  oversight, because a push follows a change within a second.
- **A pull transfers the whole account.** There is no delta endpoint, so a very large account
  re-fetches everything whenever any device changes anything.
- **Reordering has no endpoint of its own**, but is no longer unreachable: the CRUD update
  methods still leave `position` alone deliberately, and the sync push is what sets it. A
  reorder made on the website would still need one.
- **`ddl-auto=update`** is a local-development convenience only. The migrations repository now
  exists and its image runs as an init container, so deployed environments must set
  `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`.

## Agents

- **`developer`** (`.claude/agents/developer.md`) — the working agent for this backend:
  risk/impact analysis, effort estimation, and implementation. It is the counterpart the
  extension's `backend-sync` agent talks to, and the one to invoke for backend feature work.
- **`devops-engineer`** (in the GitOps repo, `kovostack-infra-gitops`) — owns everything that
  changes the cluster: Argo CD Applications, app values, namespaces, umbrella charts. Hand it
  any deployment-side change this backend needs.

## Maintaining this setup

- Update this file when a recurring rule, decision or piece of context emerges.
- **Prefer offloading to skills over growing this file.** Everything here is loaded into every
  session; a skill is loaded only when relevant. Detailed, situational knowledge (conventions,
  procedures, checklists) belongs in a skill — keep `CLAUDE.md` to the map and the rules.
- Knowledge that cost real effort to obtain (a hard debug, a non-obvious constraint) goes into
  memory or a skill so it is never re-derived.
