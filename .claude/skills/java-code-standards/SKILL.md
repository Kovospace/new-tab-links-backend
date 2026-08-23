---
name: java-code-standards
description: Java and Spring Boot code standards for the NewTabLinks backend — SOLID, MVC layering, Javadoc requirements, naming, method/class size limits, and the review checklist. Load before writing, refactoring or reviewing any Java, Spring configuration or OpenAPI annotation in this repository.
---

# Java code standards — NewTabLinks backend

The rules the author cares about, in priority order. When two collide, the one higher up wins.

## 1. A reader must guess what it does from the name alone

This is the project's defining rule and it outranks brevity every time.

- Prefer a long, explicit name over a short one that needs a comment.
  `findActiveLinksByEnvironmentIdOrderedByPosition` over `find`, `getLinks` or `query`.
- Names state *what* and *why*, never *how it is implemented*.
- Booleans read as predicates: `hasPendingChanges`, `isSyncEnabled`, `shouldRetryAfterConflict`.
- No vague names: `data`, `info`, `temp`, `obj`, `result`, `a`, `b`, `x`, `manager`, `helper`.
  Single letters are acceptable only as loop indices and lambda params with an obvious type.
- Abbreviations only where they are universal in the domain (`id`, `url`, `dto`, `api`, `http`).
- Class names carry their layer: `...Controller`, `...Service`, `...Repository`, `...Entity`,
  `...Dto`, `...Mapper`, `...Config`, `...Exception`, `...Util`.
- Test methods describe the scenario and expectation:
  `shouldRejectLinkCreationWhenEnvironmentDoesNotExist`.

## 2. Javadoc everywhere

Every type, every method (public, protected **and** package-private), every field that is not
self-evident, every constant.

- First sentence: what it does, in one line, ending with a period.
- `@param` for every parameter, `@return` unless `void`, `@throws` for every declared and every
  meaningful unchecked exception.
- Document the contract — nulls, empties, ordering guarantees, idempotency, transactional
  behaviour, side effects. Say what a caller cannot see from the signature.
- Do not restate the name (`/** Gets the id. */` on `getId()` is noise — but write it if the
  getter has any non-obvious behaviour).
- `{@link ...}` when referring to another type; `@since` on new public API.
- Private methods: Javadoc when the logic is non-obvious; a one-liner is fine when it is not.

## 3. SOLID

- **S** — one reason to change per class. A service doing HTTP mapping *and* persistence *and*
  validation is three classes. When a service grows a second responsibility, extract a second
  service rather than adding another region to the first.
- **O** — extend through new implementations and composition, not by editing a growing
  `switch`/`if-else` chain. A chain over a type is a signal for polymorphism or a strategy.
- **L** — a subtype must be usable wherever its supertype is. No strengthened preconditions,
  no `UnsupportedOperationException` in an override.
- **I** — narrow, purpose-shaped interfaces. Callers should not depend on methods they never
  call.
- **D** — depend on interfaces; inject them. **Constructor injection only** — never `@Autowired`
  on fields. Dependencies are `private final`.

## 4. MVC layering — one direction, no shortcuts

```
Controller  →  Service  →  Repository  →  Entity
  (DTOs)      (business)   (persistence)   (schema)
```

- **Controller**: HTTP only — routing, validation (`@Valid`), status codes, OpenAPI annotations.
  Thin. No business logic, no repository access, no entity ever crosses its boundary.
- **Service**: all business logic and transaction boundaries (`@Transactional`). Knows nothing
  about HTTP — no `HttpServletRequest`, no `ResponseEntity`, no status codes.
- **Repository**: persistence only. No business rules.
- **Entity**: schema shape. Never serialized to a client — map to a DTO.
- **Mapper**: entity ↔ DTO conversion, kept out of both controller and service bodies.
- Never let a lower layer call an upper one, and never let a controller reach past the service.

## 5. Size limits — the author dislikes long methods and long classes

- **Method:** aim ≤ 20 lines of body, hard smell at 40. One level of abstraction per method;
  do not mix orchestration with detail.
- **Class:** aim ≤ 200 lines, hard smell at 400. Split by responsibility, not by line count.
- **Parameters:** ≤ 4; beyond that pass a parameter object.
- **Nesting:** ≤ 2 levels. Use guard clauses and early returns instead of an `else` pyramid.
- When splitting, ask whether the extracted piece is reusable elsewhere — if so it belongs in a
  `util` class or its own service, not as a private method.
- Extracted methods must be genuinely meaningful units with real names, not `part1`/`doStuff`.

## 6. General

- Immutability by default: `final` fields, `record` for DTOs and value objects, unmodifiable
  collections out of getters.
- Return empty collections, never `null`. Use `Optional` for a genuinely absent single value —
  as a return type only, never as a field or parameter.
- Fail fast: validate at the boundary, throw a specific domain exception, handle it in a
  `@RestControllerAdvice`. Never swallow an exception; never `catch (Exception e) {}`.
- Log with SLF4J and parameterized messages (`log.debug("Synced {} links", count)`), never
  string concatenation. Never log secrets, tokens or full user payloads.
- No magic values — named constants or configuration properties.
- Comments explain *why*; the code already says *what*. Delete commented-out code.
- Use the Java 25 language level properly: records, sealed types, pattern matching for `switch`,
  text blocks, `var` only where the type is obvious from the right-hand side.

## 7. Spring & OpenAPI

- Configuration in `@ConfigurationProperties` classes, not scattered `@Value` fields.
- Every endpoint carries `@Operation` with a summary and `@ApiResponse` for each status it can
  return; every DTO field carries `@Schema` with a description and an example where it helps.
  Write these as you write the endpoint — a documented API is part of "done", not a follow-up.
- Keep the OpenAPI contract honest: if the response can be 409, document 409.

## Review checklist

Before calling any change done:

- [ ] Every new type and method has Javadoc with `@param`/`@return`/`@throws`.
- [ ] Names are long enough to guess the purpose without reading the body.
- [ ] No method over ~40 lines, no class over ~400, nesting ≤ 2.
- [ ] Layering respected; no entity leaked through a controller.
- [ ] Constructor injection, `private final` dependencies.
- [ ] New endpoints annotated for OpenAPI; new DTO fields have `@Schema`.
- [ ] No magic values, no swallowed exceptions, no `null` collections.
- [ ] Anything extracted-for-reuse lives where it can actually be reused.
- [ ] Build and tests actually run, and the result reported as it happened.
