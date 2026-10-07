# Design

## Context

See proposal.md - Why. Today the backend renders errors with Micronaut's default `JsonErrorResponseBodyProvider` (`vnd.error`). Controllers signal errors by returning `HttpResponse.notFound()` / `badRequest()` without a body, so Micronaut fills in the error body. Tracing is provided by `micronaut-tracing-opentelemetry-http` and is only exported when `OTEL_TRACES_EXPORTER=otlp`. The only known API consumer, `velocity-plugin` (`PlayerListener`), reacts to `ApiException.getCode()` (404, 400, 500) and never reads error bodies. The client is generated with `library=native`, which serializes with Jackson (the `serializationLibrary=gson` setting has no effect for this library).

Requirements: see `specs/api-error-responses/spec.md`.

## Goals / Non-Goals

**Goals:**
- One error format (`application/problem+json`) for all endpoints, produced by the framework, not per controller.
- Pin existing status codes before switching, so the switch is provably non-breaking for status-code based clients.
- Problem bodies correlate with traces.

**Non-Goals:**
- Changing status codes, paths or success bodies of `/otis` and `/search`.
- Restyling or versioning the existing endpoints.
- Localizing problem texts (developer-facing English API text, not player-facing; no i18n bundles).
- Generating the client spec from the backend build (separate `build(java-client)` change).

## Decisions

### D1 - Use Micronaut Problem JSON instead of an own ErrorResponseProcessor
Add `io.micronaut.problem:micronaut-problem-json` (version managed by the Micronaut platform BOM). It registers a `ProblemErrorResponseProcessor` that replaces the global JSON error processor, so every framework error (unknown route, unsupported media type, body binding, constraint violations) and every `HttpResponse.notFound()` without body becomes a problem.
- Built-in option: this *is* the framework's built-in module. Alternative considered: own `ErrorResponseProcessor`/`JsonErrorResponseBodyProvider` implementation - rejected, it duplicates the module (RFC mapping, constraint-violation handling) and must be maintained by us. During apply, the implementer verifies whether the module's 4.x line is based on `org.zalando:problem` or on Micronaut types and uses the module's own extension points (`ThrowableProblem`/`Problem` builder or its `ProblemErrorResponseProcessor` subclass hook) accordingly; this does not change the design.
- Configuration: `micronaut.problem.stack-trace: false` (no stack traces in bodies, explicit even though it is the default).
- Testing: integration tests (`@MicronautTest` against H2-free endpoints, REST Assured) per scenario of the "Errors use Problem Details format" requirement.
- SOLID: Open/Closed - errors are formatted at the framework seam; controllers stay unchanged.

### D2 - Domain problems via an own exception type mapped by one handler
Introduce a sealed `OtisProblemException` hierarchy carrying status, slug, title, detail and extension members, plus a single `ExceptionHandler` that turns it into a problem with `type = https://otis.onelitefeather.net/problems/<slug>`. Generic HTTP errors stay `about:blank`. This change defines the base type and `constraint-violation`; feature changes (e.g. `add-player-settings`) add their own subtypes without touching the handler.
- Built-in option: Micronaut's `ExceptionHandler` seam is used; alternative "return `HttpResponse.status(...).body(Problem)` from controllers" rejected - spreads formatting into every controller and is easy to forget.
- Testing: unit tests of the handler mapping (no server: construct exception, assert problem members); one integration test end to end.
- SOLID: Open/Closed (new problem kinds are new subtypes), Single Responsibility (handler only maps).

### D3 - Validation errors with `violations`
Use the module's constraint-violation support if it produces `violations[{field,message}]`; otherwise a dedicated `ExceptionHandler<ConstraintViolationException>` maps to that shape with slug `constraint-violation`.
- Built-in option: Micronaut validation already throws `ConstraintViolationException`; we only shape the output.
- Testing: integration test with an invalid `@Valid` body on `POST /otis`.
- SOLID: Single Responsibility.

### D4 - `traceId` extension and span status
Problem creation reads `Span.current().getSpanContext()`; if valid, it adds `traceId`. The handler for unexpected exceptions (5xx) calls `recordException` and `setStatus(ERROR)` on the current span exactly once; 4xx are left alone (expected outcomes per OLF tracing rules). No own spans are created - the server span comes from `micronaut-tracing-opentelemetry-http`.
- Built-in option: OpenTelemetry API `Span.current()` (already on the classpath through Micronaut tracing). Alternative: MDC lookup of `trace_id` - rejected, couples error output to the logging setup.
- Testing: unit test with `OpenTelemetryExtension` (`opentelemetry-sdk-testing`, test scope): start a span, make it current, build a problem, assert `traceId`; assert ERROR status and one exception event for 5xx and no ERROR for 4xx. Unit test without active span asserts no `traceId`.
- SOLID: Dependency Inversion - code depends on the OTel API only, never the SDK.
- Logging: unexpected exceptions are logged once at ERROR in the 5xx handler with the exception as last argument (`LOGGER.error("Unhandled request failure", ex)`); 4xx are not logged above DEBUG.
- Metrics: none added - HTTP server metrics (status codes) already exist via Micronaut Micrometer; no new operational question.

### D5 - Characterization tests first
Before adding the dependency, integration tests pin the status codes of existing endpoints: `GET /otis/byId/{unknown}` 404, `GET /otis/byName/{unknown}` 404, `GET /search/byId/{unknown}` 404, `GET /search/byName/{unknown}` 404, `POST /otis/delete/{unknown}` 404, `POST /otis/update/{owner}` mismatch 400, plus one success-body check for `GET /otis/byId/{known}`. They must pass before and after the switch.
- Test DB: in-memory H2 via `backend/src/test/resources/application-test.yml` (H2 is already a dependency; CI has no Docker on windows/macos). Each test class uses `@MicronautTest(transactional = true)` or deletes its own fixtures, so tests are independent of order. The currently disabled `OtisTest` is left untouched.
- SOLID: n/a (tests).

### D6 - Client: `ProblemDetail` model and helper
The spec `java-client/specs/otis-api-1.1.0.yml` (copy of `1.0.1` plus additions; `inputSpec` points to it, `1.0.1` stays in the repo) declares a `ProblemDetail` schema (`type`, `title`, `status`, `detail`, `instance`, `traceId`, `violations`, `additionalProperties: true`) and `application/problem+json` error responses for every operation. A hand-written `ProblemDetails.from(ApiException)` returns `Optional<ProblemDetail>` by parsing `getResponseBody()` with the client's Jackson `ObjectMapper` (`JSON.getDefault().getMapper()`), empty on parse failure or non-problem content type.
- Built-in option: the generated model + generated `ObjectMapper`; no new dependency.
- Testing: unit tests (no network) - valid problem body, extension members, non-JSON body, empty body. Compatibility: `javap -public` of generated `PlayerApi`, `SearchApi` and models before/after must only differ by additions; `velocity-plugin` compiles unchanged.
- SOLID: Open/Closed - generated API untouched; helper is additive.

## Risks / Trade-offs

- [Body of empty-body 404/400 responses changes from `vnd.error` to problem] -> No known consumer reads it; characterization tests guarantee status codes; changelog note.
- [Module behavior differs from assumption (e.g. `about:blank` vs. own type for 404)] -> Integration tests per scenario fail visibly; adapt via the module's hooks.
- [Error responses with `Content-Type: application/problem+json` handled differently by the native client] -> The native client throws `ApiException` for every status >= 400 regardless of content type; covered by the client helper tests.
- [Spec drift between backend and hand-maintained client spec] -> Accepted for this change; follow-up `build(java-client)` change.

## Migration Plan

- No data migration. Deploy as a normal release; rollback = previous image (error format reverts, no persistent state involved).
