# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

## What this project is

Backend service for **NewTabLinks** — the server side that stores and synchronizes user data
for the `NewTabGroupedLinks` Chrome extension (new tab page with grouped links).

**Status: skeleton only.** There is no build file, no source tree and no application yet —
only this Claude setup. Creating the Spring Boot project structure is a follow-up assignment.
Do not assume any class, package or endpoint exists; check first.

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
`groups`, `subgroups`, `links`. They carry `createdAt` and ordering fields but **no `updatedAt`,
no revision, no delete tombstones** — that gap is the central sync design problem and must be
solved explicitly, not assumed away.

## How work arrives

Tasks come as assignment files in `.claude/assignments/*.md`. Each one is a numbered increment;
later assignments extend what earlier ones built. Read the referenced assignment in full before
starting, and do only what it asks — assignments deliberately defer work to later ones.

## Tech stack

Decided (verified 2026-08-23):

| Concern | Choice |
|---|---|
| Language | **Java 25** (latest LTS) — `/usr/lib/jvm/java-25-openjdk-amd64` |
| Framework | **Spring Boot 4.1.x** (latest stable; 4.1.1 released 2026-08-20) |
| API docs | **springdoc-openapi 3.1.0** — OpenAPI 3 + Swagger UI at `/swagger-ui.html` |
| Container | `Dockerfile`, multi-stage, built by a GitHub Actions pipeline |

Caveats to keep in mind:

- The JDK on `PATH` is **26** (non-LTS). Pin **25** through the build file's toolchain /
  `JAVA_HOME`; never let the build silently target 26.
- Spring Boot has **no LTS labels** — it ships a release train with ~1 year of OSS support.
  "Latest stable" (4.1.x) is the standing interpretation of the assignment's "latest stable
  (and LTS)". The 3.5.x line is the fallback if a dependency turns out not to support 4.x.
- springdoc **3.x** is the line that targets Spring Boot 4; 2.8.x is for Boot 3.
- **Build tool (Maven vs Gradle) is not yet decided** — it belongs to the project-structure
  assignment. Neither `mvn` nor `gradle` is installed, so whichever is picked must be used
  through its wrapper (`./mvnw` / `./gradlew`).

## Architecture

**MVC, layered, one direction only:**

```
Controller (@RestController, DTOs only)
    → Service (business logic, transactions)
        → Repository (persistence)
            → Entity
```

- Controllers never touch entities or repositories; services never see HTTP types.
- DTOs cross the controller boundary in both directions — entities never leave the service layer.
- Cross-cutting helpers go in `util` classes; if a service grows past one clear responsibility,
  split it into a second service rather than letting it sprawl.

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
- `gh` is **not installed** — for a PR, either ask the user to install it or hand them the
  GitHub compare URL.

## Agents

- **`developer`** (`.claude/agents/developer.md`) — the working agent for this backend:
  risk/impact analysis, effort estimation, and implementation. It is the counterpart the
  extension's `backend-sync` agent talks to, and the one to invoke for backend feature work.

## Maintaining this setup

- Update this file when a recurring rule, decision or piece of context emerges.
- **Prefer offloading to skills over growing this file.** Everything here is loaded into every
  session; a skill is loaded only when relevant. Detailed, situational knowledge (conventions,
  procedures, checklists) belongs in a skill — keep `CLAUDE.md` to the map and the rules.
- Knowledge that cost real effort to obtain (a hard debug, a non-obvious constraint) goes into
  memory or a skill so it is never re-derived.
