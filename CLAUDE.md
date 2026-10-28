# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

`icar-api` is a backend API for a church ("ICAR") — it will provide an admin panel (donations, accounting,
membership, site content) plus public read endpoints for a companion public website. The domain model is built out:
JPA entities exist for the financial cluster (`AppUser`, `Category`, `Donation`, `Income`, `Expense`, `Budget`),
membership cluster (`Member`, `Family`, `Ministry`, `ServiceSchedule`, `MemberMinistry`), events
(`Event`, `EventPhoto`), and site content (`AboutUs`, `ContactInfo`), backed by Flyway migrations in
`src/main/resources/db/migration` (`V1`-`V16`) and a Postgres datasource configured per-profile (`dev`/`prod`) in
`application.yaml` via env vars (`hibernate.ddl-auto: validate`, so entities and migrations must stay in sync).
Every entity has a Spring Data repository. The modules with a controller/service layer so far are `category`
(list, create, update name, activate/deactivate) and `expense` (list with optional `categoryId` and `from`/`to`
date-range filters, get by id, create, update, void, and a `/summary` of totals per category for a required
`from`/`to` period) and `income` (a mirror of `expense` with the same endpoints, minus `receiptUrl`) and `budget` (list with optional
`categoryId` and `from`/`to` overlap filters, get by id, create, update, delete; every response compares the
planned amount against the actual totals) and `donation` (list with optional `status`, `method` and `from`/`to`
filters, get by id, register a cash/transfer donation, void), plus
`auth` (login, refresh, logout, me) and `user` (user management, own password change), plus the membership cluster:
`member` (list with optional `status`, `familyId`, `ministryId` and name `search` filters, get by id, create, update),
`family` (list, get by id, create, update, delete) and `ministry` (CRUD, plus `/ministries/{id}/members` to list,
assign and remove members and `/ministries/{id}/schedules` to add, update and delete service schedules), plus
`event` (admin `/events`: list with optional `active` and `from`/`to` overlap filters, get by id, create, update,
activate/deactivate, delete, and `/events/{id}/photos` to add, update and delete photos; public `GET /public/events`
and `/public/events/{id}`) and `sitecontent` (`PUT /site-content/about-us|contact-info` and their public
`GET /public/about-us|contact-info`). Do not assume any controllers/services exist beyond what's actually in `src/`; the ERD
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
  `INCOME`. `sourceDonation` is read-only in the API (not settable via `IncomeRequest`), and an income linked to a
  donation cannot be updated or voided through `/incomes` (409): it is managed from `/donations`.
- Donation rules: registering a donation (only `CASH`/`TRANSFER`; `PAYPAL` is reserved for the future checkout flow)
  creates it as `COMPLETED` (`completedAt` = the given date at 00:00) together with its `Income` (chosen `INCOME`
  category, same amount and date, `sourceDonation` set, `V5` keeps it 1:1) in one transaction. A blank `donorName` is
  stored as "Anónimo". Donations are never edited or deleted: voiding one (`V13` `chk_donation_void`, same rules as
  expenses) voids its income too, so totals and budgets stop counting it.
- Budget rules: a budget plans an amount for one category (`EXPENSE` or `INCOME`) over `periodStart`-`periodEnd`
  (inclusive; `V12` CHECKs keep the period ordered and the amount positive). A category cannot have two budgets
  with overlapping periods (checked in the service, 409). `actualAmount` is the sum of non-voided expenses or incomes
  (by category type) of that category in the period, and `remainingAmount = planned - actual` (negative = exceeded).
  Budgets are plans, not money movements, so they can be deleted. Inactive categories follow the income/expense rule.
- Membership rules: all membership endpoints use `MEMBERS_READ` / `MEMBERS_WRITE`. Members are never deleted (a member
  who leaves is marked `INACTIVE` or `DECEASED`); names are trimmed and blank email/phone stored as null. A family's
  responsible always belongs to that family: creating a family moves the responsible into it (409 if they already
  lead another family, `V2` unique), a new responsible must already be a member of it, and the responsible can't
  change family through `/members` (409). A family can only be deleted when the responsible is its only member (they
  are left without family). Members join families by setting `familyId` on the member. Ministry names are unique
  case-insensitively (service + `V14` index on `lower(name)`); deleting a ministry removes its schedules and
  assignments. `INACTIVE`/`DECEASED` members can't be assigned to a ministry; `joinedAt` defaults to now. Schedules
  use `java.time.DayOfWeek`, always belong to a ministry and can't repeat day + time within it (`V14`).
- Event rules: admin endpoints (reads included, there is no `CONTENT_READ`) use `CONTENT_WRITE`; public ones are
  `GET /public/events/**` with `@PreAuthorize("permitAll()")` and only show active events (inactive ones answer 404).
  `endAt >= startAt` (service + `V15` `chk_event_period`); title/description trimmed, blank location stored as null;
  events are created active and can be deleted (site content, not money; photos go with them). Without `from`/`to`
  the public list returns upcoming and in-progress events (`endAt >= now`), soonest first. Photos only store an
  `imageUrl` (no file storage yet); `displayOrder` is required in the DB (`V15`), defaults to last on add, is kept on
  update when omitted, and may repeat.
- Site content rules: `AboutUs` and `ContactInfo` are single records (`V16` unique index on `(true)`): `PUT` under
  `/site-content` (`CONTENT_WRITE`) creates them the first time and updates them afterwards; there are no admin GETs
  (the panel reads the public ones). Public GETs answer 404 until configured. All fields are required and trimmed.
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
