# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

`icar-api` is a backend API for a church ("ICAR") — it will provide an admin panel (donations, accounting,
membership, site content) plus public read endpoints for a companion public website. The domain model is built out:
JPA entities exist for the financial cluster (`AppUser`, `Category`, `Donation`, `Income`, `Expense`, `Budget`),
membership cluster (`Member`, `Family`, `Ministry`, `ServiceSchedule`, `MemberMinistry`), events
(`Event`, `EventPhoto`), and site content (`AboutUs`, `ContactInfo`), backed by Flyway migrations in
`src/main/resources/db/migration` (`V1`-`V5`) and a Postgres datasource configured per-profile (`dev`/`prod`) in
`application.yaml` via env vars (`hibernate.ddl-auto: validate`, so entities and migrations must stay in sync).
There is still no Spring Security config and no controllers/services layer yet. Do not assume any
controllers/services exist beyond what's actually in `src/`; the ERD lives in the Obsidian vault, not this repo.

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
- No Spring Security and no external service SDKs (payments, cloud storage) are present yet — check `pom.xml`
  before assuming any of these are available.
