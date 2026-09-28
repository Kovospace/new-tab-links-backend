# Code map

Where things are, so that nobody lists the source tree to find out. Paths are relative to
`src/main/java/com/kovospace/newtablinks/`; tests mirror them under `src/test/java/…`.
Keep it current: a new module, controller or cross-cutting service adds a row here.

## Where to look for…

| Concern | File |
|---|---|
| Security filter chain, public paths, CORS | `common/config/SecurityConfiguration.java` (CORS test: `common/config/SecurityConfigurationCorsTest.java`) |
| Every endpoint path shared with another class | `common/config/ApiEndpointPaths.java` |
| `X-Api-Key` check | `common/security/FrontendApiKeyAuthenticationFilter.java` |
| Visitor token check (registration, username lookup) | `common/security/VisitorTokenAuthenticationFilter.java`, `auth/services/VisitorTokenService.java` |
| Who is signed in, inside a service | `common/security/AuthenticatedUserProvider.java` |
| Error → status code and body | `common/exceptions/GlobalExceptionHandler.java` |
| Password sign-in, refresh, logout | `auth/services/AuthenticationService.java`, `auth/services/TokenPairFactory.java`, `auth/services/AccessTokenIssuer.java` |
| Google sign-in and the handoff code | `auth/services/ProviderSignInService.java`, `auth/services/ProviderSignInSuccessHandler.java`, `auth/services/SingleUseCodeService.java` |
| Registration, activation, password reset | `auth/services/RegistrationService.java`, `auth/services/PasswordService.java` |
| Mail | `auth/services/SmtpAccountEmailSender.java` |
| Devices, installation id, take-over | `user/services/UserDeviceService.java` |
| Account read/update | `user/services/UserService.java` |
| Operator-granted pro (admin `premium` checkbox) | `entitlement/services/EntitlementGrantService.java`, called from `user/services/UserAdministrationService.java` |
| Account deletion (user and admin paths) | `common/services/AccountDeletionService.java`, `user/services/UserAdministrationService.java` |
| Deleting a subtree of the hierarchy | `common/services/HierarchyDeletionService.java` |
| Operator sign-in and lockout | `admin/services/AdminSignInService.java`, `admin/services/AdminSignInAttemptTracker.java` |
| Sync snapshot (GET) | `sync/services/SyncSnapshotService.java`, `sync/services/EnvironmentSnapshotReader.java` |
| Sync push (POST) | `sync/services/SyncPushService.java` → one `*SyncOperationApplier.java` per entity kind |
| Why a pushed operation was refused | `sync/dtos/SyncRejectionReason.java` |
| Pushing a refresh to other devices | `sync/events/UserDataChangePublisher.java`, `sync/services/UserRefreshNotifier.java`, `common/config/WebSocketConfiguration.java`, `common/security/StompAuthenticationInterceptor.java` |
| Client-assigned ids | `common/utils/ClientAssignedIdentifierPolicy.java`, `common/models/AssignedOrGeneratedUuid.java` |
| Ordering | `common/utils/DisplayPositionCalculator.java` |
| Usage statistics (new tabs, website visitors) | `statistics/services/NewTabReportService.java`, `statistics/services/WebsiteVisitService.java`, `statistics/services/WebsiteVisitorHasher.java` |
| Per-address rate limit (statistics only) | `statistics/services/UsageStatisticsRateLimiter.java` |
| Base entity (`id`, `createdAt`, `updatedAt`) | `common/models/AbstractAuditableEntity.java` |
| Every configuration parameter | `src/main/resources/application.properties` (each one `${ENV:default}`) |
| The schema | **not here** — `/home/kovo/IdeaProjects/new-tab-links-migrations`, whose `CLAUDE.md` indexes every table |

## Endpoints → controller

| Base path | Controller |
|---|---|
| `/api/v1/auth` (login, refresh, logout, register, activate, visitor token, …) | `auth/controllers/AuthenticationController.java`, `auth/controllers/VisitorTokenController.java` |
| `/api/v1/auth/password` | `auth/controllers/PasswordController.java` |
| `/api/v1/users` (`/me`) | `user/controllers/UserController.java` |
| `/api/v1/users/me/devices` | `user/controllers/UserDeviceController.java` |
| `/api/v1/admin` | `admin/controllers/AdminAuthenticationController.java` |
| `/api/v1/admin/users` | `user/controllers/AdminUserController.java` |
| `/api/v1/admin/metrics` | `statistics/controllers/AdminUsageMetricsController.java` |
| `/api/v1/stats` (`/new-tabs`, `/website-visit`) — public | `statistics/controllers/UsageStatisticsController.java` |
| `/api/v1/sync` (`/snapshot`, `/push`) | `sync/controllers/SyncController.java` |
| `/api/v1/profiles`, `/environments`, `/groups`, `/subgroups`, `/links` | `<module>/controllers/<Module>Controller.java` |

The five domain modules (`profile`, `environment`, `group`, `subgroup`, `link`) share one shape:
`controllers/ services/ repositories/ models/ dtos/ mappers/`, a `<Module>Service` and a
`<Module>SynchronizationService` used by sync. Learn one, and the others are the same.

## Files big enough to read by range

`grep -n` for the method first, then `sed -n 'a,bp'` around it:
`application.properties` (244 lines), `auth/controllers/AuthenticationController.java` (373),
`common/config/SecurityConfiguration.java` (368), `user/services/UserDeviceService.java` (363),
`common/exceptions/GlobalExceptionHandler.java` (301).

## CLAUDE.md, by section

Implementation work reads all of it. A question reads only the section it needs —
`grep -n '^##' CLAUDE.md` gives the line numbers:

| Section | Needed for |
|---|---|
| Paired repository — the Chrome extension | anything the extension consumes |
| Architecture, Domain notes | any code change; ownership scoping, `publishChangeFor`, closed tabs, client ids |
| Configuration | a new parameter |
| The schema is owned by another repository | any entity or table change |
| Deployment, Running against the cluster — mirrord | anything that reaches the cluster or needs real data |
| Known gaps and deferred decisions | before calling something a bug |
| Git rules | before any branch, commit or push |

## Skills — read the file, only when the topic matches

| Skill | When |
|---|---|
| `.claude/skills/java-code-standards/SKILL.md` | before writing or reviewing Java |
| `.claude/skills/authentication/SKILL.md` | anything under `auth/`, `admin/`, `common/security/`, or a sign-in path |
| `.claude/skills/deployment-pipeline/SKILL.md` | Dockerfile, CI, environment variables, the cluster |
