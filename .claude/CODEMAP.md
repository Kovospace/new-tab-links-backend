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
| Google sign-in state across replicas (sealed cookie, no session) | `common/security/CookieOAuth2AuthorizationRequestRepository.java`, `auth/utils/AuthorizationRequestCookieCipher.java`, `auth/utils/AuthorizationRequestCookieCodec.java` |
| Registration, activation, password reset | `auth/services/RegistrationService.java`, `auth/services/PasswordService.java` |
| Mail | `auth/services/SmtpAccountEmailSender.java` |
| Devices, installation id, take-over | `user/services/UserDeviceService.java` |
| Account read/update | `user/services/UserService.java` |
| Operator-granted pro (admin `premium` checkbox, `premiumGrantTerm` one year / lifetime) | `entitlement/services/EntitlementGrantService.java`, `entitlement/models/PremiumGrantTerm.java`, called from `user/services/UserAdministrationService.java`; shown as `premiumUntil` (`AdminUserDto`) and `grantedByOperator` (`payment/mappers/SubscriptionStatusMapper.java`) |
| Account deletion (user and admin paths) | `common/services/AccountDeletionService.java`, `user/services/UserAdministrationService.java` |
| Deleting a subtree of the hierarchy | `common/services/HierarchyDeletionService.java` |
| Operator sign-in and lockout (shared by every replica) | `admin/services/AdminSignInService.java`, `admin/services/AdminSignInAttemptTracker.java`, `admin/repositories/AdminSignInLockRepository.java` (the atomic SQL) |
| Sync snapshot (GET) | `sync/services/SyncSnapshotService.java`, `sync/services/EnvironmentSnapshotReader.java` |
| Sync push (POST) | `sync/services/SyncPushService.java` → one `*SyncOperationApplier.java` per entity kind |
| Why a pushed operation was refused | `sync/dtos/SyncRejectionReason.java` |
| Fair Use Policy caps (500 links per workspace, 50 workspaces, 50 profiles, 500 closed tabs; every plan) - refuse growth only, 409 `FAIR_USE_LIMIT_REACHED` with `code`/`limit`/`maximum` in `ApiErrorResponseDto`; account row locked per check | `common/services/FairUseLimitGuard.java` (called by `ProfileService`, `EnvironmentService`, `LinkService` creates, and by `SyncPushService` before/after the whole batch), `common/config/FairUseLimitProperties.java`, `common/models/FairUseLimit.java`; closed tabs trimmed, not refused: `ClosedTabSynchronizationService.trimHistoryToFairUseCap` |
| Free plan limits (2 workspaces, 1 profile, 5 synchronised installations = `user_device` rows with an `installation_id`) for an account not premium now - refuse growth only, 409 `FREE_PLAN_LIMIT_REACHED`, same body shape as fair use | `common/services/FreePlanLimitGuard.java` (profiles/workspaces asked from `FairUseLimitGuard`, devices from `user/services/UserDeviceService.java`), `common/config/FreePlanLimitProperties.java`, `common/models/FreePlanLimit.java` |
| Pushing a refresh to other devices | `sync/events/UserDataChangePublisher.java`, `sync/services/UserRefreshNotifier.java` (publishes), `sync/services/UserRefreshRelay.java` (delivers, in every pod), `sync/events/UserDataChangedMessage.java`, `common/config/WebSocketConfiguration.java`, `common/security/StompAuthenticationInterceptor.java` |
| Messaging between replicas - the port, and which transport carries it | `common/messaging/MessagePublisher.java`, `MessageSubscriber.java`, `MessageTopic.java`, `MessageHandlerRegistry.java`, `MessagingConfiguration.java` (the switch) |
| Messaging over PostgreSQL `NOTIFY`/`LISTEN` | `common/messaging/postgres/PostgresNotifyMessagePublisher.java`, `PostgresNotificationListener.java` |
| Client-assigned ids | `common/utils/ClientAssignedIdentifierPolicy.java`, `common/models/AssignedOrGeneratedUuid.java` |
| Ordering | `common/utils/DisplayPositionCalculator.java` |
| Usage statistics (new tabs, website visitors) | `statistics/services/NewTabReportService.java`, `statistics/services/WebsiteVisitService.java` (counts every report; the website dedupes per day) |
| Per-address rate limit (statistics only) | `statistics/services/UsageStatisticsRateLimiter.java` |
| Checkout: plan + currency -> Creem product | `payment/services/PaymentCheckoutService.java` (default currency), `payment/services/CreemCheckoutGateway.java`; unsupported currency -> `common/exceptions/PlanNotOfferedInCurrencyException.java` (400, field `currency`) |
| Product catalog per currency (`products.<ISO>.lifetime/subscription`) | `payment/config/CreemProperties.java` (`catalogProducts()`, `productIdFor(plan, currency)`) |
| Public price list: offers, prices from Creem, cache, country -> currency | `payment/services/PaymentOfferService.java`, `ProductPriceCache.java` (the only thing that reads prices; once per hour per pod), `CreemProductCatalog.java` (`GET /v1/products/{id}`), `CurrencySuggestionPolicy.java`; rules in `payment/config/PaymentPricingProperties.java` |
| Webhooks, entitlement signals, superseded-subscription cancel | `payment/controllers/CreemWebhookController.java`, `payment/services/CreemWebhookInterpreter.java`, `CreemEntitlementSignalTranslator.java`, `SupersededSubscriptionCancellation*.java` |
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
| `/api/v1/payments/offers` (GET) — public, bearer token ignored, reads `CF-IPCountry` | `payment/controllers/PaymentOfferController.java` |
| `/api/v1/payments/checkouts`, `/subscription`, `/webhooks/creem` | `payment/controllers/PaymentCheckoutController.java`, `PaymentSubscriptionController.java`, `CreemWebhookController.java` |
| `/api/v1/sync` (`/snapshot`, `/push`) | `sync/controllers/SyncController.java` |
| `/api/v1/profiles`, `/environments`, `/groups`, `/subgroups`, `/links` | `<module>/controllers/<Module>Controller.java` |

The five domain modules (`profile`, `environment`, `group`, `subgroup`, `link`) share one shape:
`controllers/ services/ repositories/ models/ dtos/ mappers/`, a `<Module>Service` and a
`<Module>SynchronizationService` used by sync. Learn one, and the others are the same.

## Files big enough to read by range

`grep -n` for the method first, then `sed -n 'a,bp'` around it:
`application.properties` (358 lines), `auth/controllers/AuthenticationController.java` (373),
`common/config/SecurityConfiguration.java` (455), `user/services/UserDeviceService.java` (363),
`common/exceptions/GlobalExceptionHandler.java` (481).

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
