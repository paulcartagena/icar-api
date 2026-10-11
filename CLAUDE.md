# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

`icar-api` is a backend API for a church ("ICAR") — it will provide an admin panel (donations, accounting,
membership, site content) plus public read endpoints for a companion public website. The domain model is built out:
JPA entities exist for the financial cluster (`AppUser`, `Category`, `Donation`, `Income`, `Expense`, `Budget`),
membership cluster (`Member`, `Family`, `Ministry`, `ServiceSchedule`, `MemberMinistry`), events
(`Event`, `EventPhoto`), and site content (`AboutUs`, `ContactInfo`), backed by Flyway migrations in
`src/main/resources/db/migration` (`V1`-`V7`) and a Postgres datasource configured per-profile (`dev`/`prod`) in
`application.yaml` via env vars (`hibernate.ddl-auto: validate`, so entities and migrations must stay in sync).
Every entity has a Spring Data repository. The modules with a controller/service layer so far are `category`
(list, create, update name, activate/deactivate) and `expense` (list with optional `categoryId` and `from`/`to`
date-range filters, get by id, create, update); the rest are entities + repositories only. There is still no
Spring Security config. Do not assume any controllers/services exist beyond what's actually in `src/`; the ERD
lives in the Obsidian vault, not this repo.

## Conventions

- Package-by-feature: `<feature>/{controller,dto,entity,enums,repository,service}` under `com.paulcartagena.icarapi`.
- DTOs are Java `record`s with Bean Validation annotations (`@NotBlank`, `@Size`, ...); entities stay as classes.
  Never expose entities from controllers.
- Errors: throw `ApiException` via its static factories (`resourceNotFound` -> 404, `duplicateResource` -> 409).
  `exception/GlobalExceptionHandler` (`@RestControllerAdvice`) maps `ApiException`, `@Valid` failures (400) and
  `DataIntegrityViolationException` (409) to the `ErrorResponse` record.
- Category rules: `type` is immutable after creation; name uniqueness is case-insensitive per type (enforced in
  the service with `...IgnoreCase...` queries and in the DB by the `V7` unique index on `lower(name), type`);
  names are trimmed; categories are deactivated, never deleted. Income/expense modules must reject inactive
  categories when assigning one (create, or an update that changes the category); a record keeping its current
  category can still be updated even if that category was deactivated.
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
- No Spring Security and no external service SDKs (payments, cloud storage) are present yet — check `pom.xml`
  before assuming any of these are available.
