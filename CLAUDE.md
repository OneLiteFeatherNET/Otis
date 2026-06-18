# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Otis is a REST API service that stores Minecraft player profile data (UUID, name, skin textures, locale, join timestamps) in a database. It eliminates the need for runtime calls to Mojang/third-party services by caching player data locally. Built on the **Micronaut 4.6** framework with **Java 21**.

## Build & Development

```bash
# Build everything (backend + java-client + velocity-plugin)
./gradlew clean build

# Run tests (JUnit 5, uses JaCoCo for coverage)
./gradlew test

# Run only backend tests
./gradlew :backend:test

# Start the backend locally (requires MariaDB — see below)
./gradlew :backend:run

# Regenerate the java-client from the OpenAPI spec
./gradlew :java-client:openApiGenerate

# Build the velocity plugin fat JAR
./gradlew :velocity-plugin:shadowJar
```

### Local Database

Start MariaDB via Docker (port 3336):
```bash
docker compose -f docker/docker-compose.yml up -d
```
Default connection: `jdbc:mariadb://127.0.0.1:3336/otis`, user `root`, password `otis`. Configurable via env vars `JDBC_URL`, `JDBC_USER`, `JDBC_PASSWORD`, `JDBC_DRIVER`.

## Architecture

Three Gradle subprojects:

- **`backend`** — Micronaut HTTP server (Netty runtime). Contains the REST controllers, JPA entities, and repository layer. Entry point: `OtisApplication.java`. Uses Hibernate with auto-DDL (`hbm2ddl.auto=update`), Micronaut Data repositories, Caffeine caching, and Micrometer/Prometheus metrics. OpenAPI/Swagger UI is auto-generated at `/swagger-ui/`.

- **`java-client`** — Auto-generated Java HTTP client from the OpenAPI spec at `java-client/specs/otis-api-1.0.1.yml` using the `openapi-generator` Gradle plugin. Generated sources go to `build/generated/openapi/`. Published as `otis-client` to the OneLiteFeather Maven repository.

- **`velocity-plugin`** — Minecraft Velocity proxy plugin that uses `java-client` to sync player data to Otis on player join events. Built as a shadow JAR. Published as `otis-plugin`.

### Backend Layers

- **Controllers**: `OtisRequestsController` (`/otis`) for CRUD operations, `OtisSearchController` (`/search`) for lookups by UUID or name.
- **Entity**: `OtisPlayer` — JPA entity with custom converters for `Locale` and JSON map (`profileTexture`). Has a `toDto()`/`toEntity()` pattern for DTO conversion.
- **Repository**: `OtisPlayerRepository` extends `PageableRepository` with custom JPQL queries (`findByName`, `findByPlayerUuid`).
- **DTO**: `OtisPlayerDTO` — Java record used for API request/response serialization.

## Conventions

- Java 21 with UTF-8 encoding enforced in Gradle
- Micronaut Serde (Jackson-based) for serialization — use `@Serdeable` on data classes
- Micronaut dependency injection with `@Inject` constructor injection
- OpenAPI annotations (`@Operation`, `@ApiResponse`) on all controller methods
- Tests use JUnit 5 with REST Assured (`micronaut-test-rest-assured`)
- CI runs `./gradlew clean build test` on ubuntu, windows, and macos
- Releases via [Release Please](https://github.com/googleapis/release-please) (`release-please.yml` workflow), driven by Conventional Commits; the version lives in `gradle.properties` (marked `# x-release-please-version`) and `.release-please-manifest.json`. Default branch is `main`. Build/publish reuse the central workflows in `OneLiteFeatherNET/workflows`
