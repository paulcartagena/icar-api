# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

`icar-api` is a backend API for a church ("ICAR") — it will provide an admin panel (donations, accounting,
membership, site content) plus public read endpoints for a companion public website. The domain model is built out:
JPA entities exist for the financial cluster (`AppUser`, `Category`, `Donation`, `Income`, `Expense`, `Budget`),
membership cluster (`Member`, `Family`, `Ministry`, `ServiceSchedule`, `MemberMinistry`), events
(`Event`, `EventPhoto`), and site content (`AboutUs`, `ContactInfo`), backed by Flyway migrations in
`src/main/resources/db/migration` (`V1`-`V11`) and a Postgres datasource configured per-profile (`dev`/`prod`) in
`application.yaml` via env vars (`hibernate.ddl-auto: validate`, so entities and migrations must stay in sync).
Every entity has a Spring Data repository. The modules with a controller/service layer so far are `category`
(list, create, update name, activate/deactivate) and `expense` (list with optional `categoryId` and `from`/`to`
date-range filters, get by id, create, update, void, and a `/summary` of totals per category for a required
`from`/`to` period) and `income` (a mirror of `expense` with the same endpoints, minus `receiptUrl`), plus
`auth` (login, refresh, logout, me) and `user` (user management, own password change); the rest are entities +
repositories only. Do not assume any controllers/services exist beyond what's actually in `src/`; the ERD
lives in the Obsidian vault, not this repo.

## Conventions

- Package-by-feature: `<feature>/{controller,dto,entity,enums,repository,service}` under `com.paulcartagena.icarapi`.
- DTOs are Java `record`s with Bean Validation annotations (`@NotBlank`, `@Size`, ...); entities stay as classes.
  Never expose entities from controllers.
- Errors: throw `ApiException` via its static factories (`resourceNotFound` -> 404, `badRequest` -> 400,
  `unauthorized` -> 401, `forbidden` -> 403, `duplicateResource` -> 409, `conflict` -> 409 for actions invalid in
  the current state). `exception/GlobalExceptionHandler` (`@RestControllerAdvice`) maps `ApiException`, `@Valid`
  failures (400), `DataIntegrityViolationException` (409) and Spring Security's `AuthenticationException` (401) /
  `AccessDeniedException` (403) to the `ErrorResponse` record; `config/SecurityConfig` forwards filter-level
  security errors to it through the `HandlerExceptionResolver`.
- Security: stateless JWT resource server (`config/SecurityConfig`, HS256 with `JWT_SECRET`). The access token's
  `permissions` claim maps 1:1 to authorities, so endpoints use `@PreAuthorize("hasAuthority('<Permission>')")` on
  every controller method — check permissions, never roles. Each `UserRole` declares its `Permission` set and a
  level (ADMIN > PASTOR > TREASURER). Public routes: `POST /auth/login|refresh|logout`, `GET /public/**`, the
  Scalar docs and `/error`. The current user's id is the JWT `sub` (`@AuthenticationPrincipal Jwt jwt`); use it for
  `registeredBy`, never a request field.
- Auth rules: the refresh token is an opaque random value stored only as SHA-256 in `refresh_token` (`V10`), sent
  in an `HttpOnly; SameSite=Strict; Path=/auth` cookie, rotated on every refresh; reusing a revoked one revokes all
  the user's sessions. The first ADMIN is created at startup by `InitialAdminCreator` from `ADMIN_USERNAME` /
  `ADMIN_PASSWORD` when none exists. Passwords are BCrypt, 12-72 characters.
- User rules: users are deactivated, never deleted; status is only `ACTIVE`/`INACTIVE` (`V11`). Anti-escalation:
  an actor can only manage users and assign roles below their own level unless they have `USERS_ADMIN`; nobody
  edits, deactivates or resets the password of their own account through `/users/{id}` (use `/users/me/password`).
  Deactivating a user or resetting their password revokes their sessions.
- Category rules: `type` is immutable after creation; name uniqueness is case-insensitive per type (enforced in
  the service with `...IgnoreCase...` queries and in the DB by the `V7` unique index on `lower(name), type`);
  names are trimmed; categories are deactivated, never deleted. Income/expense modules must reject inactive
  categories when assigning one (create, or an update that changes the category); a record keeping its current
  category can still be updated even if that category was deactivated.
- Expense rules: expenses are voided, never deleted. Voiding requires a reason, is final (cannot be repeated or
  reverted) and blocks updates; the `V8` `chk_expense_void` CHECK keeps `voided`, `voided_at` and `void_reason`
  consistent. Voided expenses still appear in the list but are excluded from totals.
- Income follows the same void rules as expenses (`V9` `chk_income_void`). Income categories must be of type
  `INCOME`. `sourceDonation` is read-only in the API (not settable via `IncomeRequest`).
- Never edit an already-applied Flyway migration; add a new `V<n>` file instead.

## Build, run, test

This project uses the Maven wrapper — always invoke `./mvnw`, not a system-installed `mvn`.

```bash
./mvnw clean install       # full build
./mvnw spring-boot:run     # run the app locally (default port 8080)
./mvnw test                # run all tests
./mvnw test -Dtest=IcarApiApplicationTests            # run a single test class
./mvnw test -Dtest=IcarApiApplicationTests#contextLoads  # run a single test method
```

There is no linter or formatter configured in `pom.xml`.

## Stack

- Java 21, Spring Boot 4.1.1 (parent POM), package base `com.paulcartagena.icarapi`.
- `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-webmvc` (each with matching
  `-test` starters), `postgresql` (runtime driver), `lombok` (annotation processor wired into both the
  `default-compile` and `default-testCompile` executions of `maven-compiler-plugin` — keep that wiring if you touch
  the compiler plugin config).
- `flyway-database-postgresql` (via `spring-boot-starter-flyway`) drives schema migrations from
  `src/main/resources/db/migration`.
- `springdoc-openapi-starter-webmvc-scalar` serves the API docs UI (Scalar).
- `spring-boot-starter-security-oauth2-resource-server` (Spring Boot 4's name for the resource server starter, with
  its `-test` companion) provides JWT validation and `JwtEncoder`/`JwtDecoder` via Nimbus.
- Required env vars: the datasource ones per profile, `JWT_SECRET` (at least 32 characters), and `ADMIN_USERNAME` /
  `ADMIN_PASSWORD` (optional `ADMIN_FULL_NAME`) only on the first start against an empty DB.
- No external service SDKs (payments, cloud storage) are present yet — check `pom.xml` before assuming any of these
  are available.
