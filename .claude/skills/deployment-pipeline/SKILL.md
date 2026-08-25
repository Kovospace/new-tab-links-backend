---
name: deployment-pipeline
description: How this backend is built, shipped and deployed - the Dockerfile, the shared GitHub Actions pipeline in Kovospace/kovostack-github-workflows, the GitOps repository and Helm chart it deploys through, and the Flyway init-container schema contract. Load before touching the Dockerfile, .github/workflows, deployment configuration, or anything about how a change reaches the cluster.
---

# Deployment pipeline - NewTabLinks backend

Everything here was read out of the private Kovospace repositories on 2026-08-24. It is
recorded so nobody has to clone and re-read them to answer a routine deployment question.

## The shape of it

```
this repo
 └── .github/workflows/docker-build.yml   (thin caller, workflow_dispatch)
        │ uses:
        ▼
 Kovospace/kovostack-github-workflows@2.0.3
 └── .github/workflows/build-deploy.yml   (on: workflow_call - the real pipeline)
        │ 1. docker build . -> push
        ▼
 registry.matejkovac.sk/apps/new-tab-links-backend:sha-<7 char sha> (+ :latest)
        │ 2. writes imageTag into
        ▼
 Kovospace/kovostack-infra-gitops
 ├── versions/new-tab-links-backend.yaml       -> `imageTag: sha-abc1234`
 └── versions/new-tab-links-backend-init.yaml  -> `initImageTags.migrations: "0.0.1"`
        │ Argo CD reconciles
        ▼
 Kubernetes namespace new-tab-links-backend
```

**CI never talks to the cluster.** It builds, pushes, and commits a tag. Argo CD rolls it out.

## Facts that are easy to get wrong

- **The template repo is private.** HTTPS/GitHub API returns 404. It is reachable over SSH with
  `~/.ssh/id_ed25519`. To read it:
  `git clone --branch 2.0.3 --depth 1 git@github.com:Kovospace/kovostack-github-workflows.git`
- **Its tags have no `v` prefix** - the real tags are `2.0.2`, `2.0.1`, `2.0.0`, …, even though
  its README describes a `@v2` convention. Pin `@2.0.3`.
- **`image_name` and `app_namespace` both equal the repository name** (`new-tab-links-backend`),
  matching how `paster-backend` and the other projects are set up.
- **Only the default branch builds.** The pipeline guards on
  `github.ref_name == github.event.repository.default_branch`, so a feature branch cannot
  release. Work reaches `main` through a PR, and the workflow is then dispatched manually.
- **The trigger is `workflow_dispatch` only** - with `build` and `deploy` checkboxes. Unchecking
  `build` re-pins the GitOps tag for the checked-out commit, which is how a rollback is done.
- **Registry is plain HTTP** (no TLS); the shared pipeline configures the runner's daemon and
  buildkit for an insecure registry. Nothing to do on this side.
- **Old tags are never pruned** from the registry, deliberately - a GitOps rollback has to be
  able to pull them.
- Secrets (`REGISTRY_USER`, `REGISTRY_PASSWORD`, `GITOPS_DEPLOY_KEY`) are **organization level**
  and arrive via `secrets: inherit`. Never add them to this repository.

## The Helm chart the deployment uses

`Kovospace/kovostack-helm-charts`, chart `charts/app` at `chart-app-1.4.1`, wired up by
`applications/<name>.yaml` in the GitOps repo, with values from
`applications/<name>/values.yaml` plus `versions/<name>.yaml`.

Relevant chart behaviour:

- `containerPort` defaults to **8080** - which is why `server.port` defaults to 8080 here.
- Probes are rendered **only when `healthPath` is set**; an unset `healthPath` disables both the
  readiness and liveness probe rather than defaulting to `/`. Set it to `/actuator/health`.
- Plain `env` values come from the values file; anything secret is synced from Infisical into a
  Secret and injected wholesale with `envFrom`. Database credentials belong there, never in the
  values file.
- Postgres runs **outside** the cluster and is reached through the `externalServices` block,
  which needs an **IP address, not a hostname** (`172.17.0.1`, the docker0 gateway, for the
  other projects).
- **`initContainers` is a list in the app's values file** and `initImageTags` a *map* keyed by
  the container's resolved name, in `versions/<app>-init.yaml`. The split exists because Helm
  merges maps across values files but replaces lists wholesale. Setting an entry's own
  `imageTag` *and* `initImageTags.<name>` is refused by the chart, not resolved.

## The Flyway schema contract

Migrations live in **Kovospace/new-tab-links-migrations** (local checkout
`/home/kovo/IdeaProjects/new-tab-links-migrations`), ship as a Docker image whose tag *is* the
schema version, and run as a **Kubernetes init container** before this application starts. The
application must never migrate its own schema, which is why `spring.flyway.enabled` defaults to
`false` here.

The whole chain, end to end:

1. Maven property **`flyway.migrations.schema.version`** in `pom.xml` names the migration image
   tag whose schema this build expects. Bump it in the same commit that starts depending on a
   new migration.
2. It is baked into `META-INF/build-info.properties` by the `build-info` goal and published at
   **`/actuator/info` as `build.flywayMigrationsSchemaVersion`**, so a running pod can be
   compared against the migration image that actually ran.
3. `.github/workflows/docker-build.yml` reads that property out of the pom in its
   `schema-version` job (sed, not Maven) and passes it to the shared pipeline as
   `init_image_tags: migrations=<version>`.
4. The shared pipeline's deploy job writes `versions/new-tab-links-backend-init.yaml`:
   `initImageTags: { migrations: "<version>" }`, alongside the app's own `imageTag` file.
5. `applications/new-tab-links-backend/values.yaml` declares the init container itself
   (`initContainers: [{name: migrations, image: new-tab-links-migrations}]`); the chart joins
   the two by the name `migrations`.

Consequences worth remembering:

- **The migrations pipeline does not deploy** (`deploy: false`, no `app_namespace`). The image is
  not a workload of its own, and the version to pin is the one the *backend* was written
  against - so the backend's pipeline is what writes the init tag. Publishing a new migration
  version changes nothing in the cluster until this repository's pom is bumped and deployed.
- The schema-version job **fails the run** on a missing or non-`x.y.z` value, deliberately: a bad
  tag would leave the pod in `Init:ImagePullBackOff`.
- A **deploy-only run** (build unchecked) re-pins both files together, so a rollback of the app
  also rolls back the schema version it expects.

`spring.jpa.hibernate.ddl-auto` defaults to `update` for local development; deployed environments
**must** override it with `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`.

## Who owns which repository - use the agent, don't hand-edit

Three repositories around this one deploy it, and each has an agent that knows it. Delegate to
the agent rather than editing another repo's YAML from here: they hold the conventions,
versioning and PR rules of their repository, which this skill does not repeat.

| Repository | Local checkout | Agent | Use it for |
|---|---|---|---|
| `kovostack-infra-gitops` | `/home/kovo/IdeaProjects/kovostack-infra-gitops` | **`devops-engineer`** | Argo CD `Application`s, `applications/<app>/values.yaml`, `versions/*.yaml`, namespaces, "what breaks if we change X", and guidance on pipelines that deploy into this cluster. Asks before every push to `main`. |
| `kovostack-helm-charts` | `/home/kovo/IdeaProjects/kovostack-helm-charts` | **`helm-chart-devops`** | The `charts/app` chart itself - a new value, a new template, a fix. Bumps the chart version and delivers on an `upgrade/**` branch with a PR, never on `main`. |
| `kovostack-github-workflows` | `/home/kovo/IdeaProjects/kovostack-github-workflows` | *(none - edit directly)* | The shared `build-deploy.yml`. Change it on a `feature/**` branch, then cut the next `x.y.z` tag and bump the pin in every caller. |

A change here that needs a new chart value or a new deployment value is **two agents and this
repo**, in that order: chart first (it must be released before anything can use it), GitOps
second, caller last.

## Local verification recipe

The build image needs a real JDK. `/usr/lib/jvm/*` on this machine are **JRE-only** (no `javac`);
the only JDK is `~/.jdks/openjdk-26.0.2`, so local Maven runs need
`JAVA_HOME=$HOME/.jdks/openjdk-26.0.2` and compile with `--release 25`. The Docker build uses a
proper `eclipse-temurin:25-jdk`, so the image is unaffected.

```bash
docker run -d --name ntl-postgres -e POSTGRES_DB=newtablinks -e POSTGRES_USER=newtablinks \
  -e POSTGRES_PASSWORD=newtablinks -p 55432:5432 postgres:17-alpine

JAVA_HOME=$HOME/.jdks/openjdk-26.0.2 \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/newtablinks ./mvnw spring-boot:run

# the whole image, as the pipeline builds it
docker build -t new-tab-links-backend:verify .
docker run --rm --add-host=host.docker.internal:host-gateway -p 8081:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:55432/newtablinks \
  new-tab-links-backend:verify
```
