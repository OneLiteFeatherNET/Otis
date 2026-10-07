# Proposal

Ships as: `feat(api): return rfc 9457 problem details for errors`

## Why

Otis answers errors in Micronaut's default `vnd.error` format, which carries no stable, machine-readable error type. The upcoming player settings API (`add-player-settings`) needs to tell callers precisely why a request failed (invalid key, reserved namespace, unknown player), and more services are expected to integrate with Otis. Adopting RFC 9457 Problem Details now gives every current and future consumer one standard error contract before new endpoints are built on top of it.

## What Changes

- All error responses of the backend use `application/problem+json` (RFC 9457) with `type`, `title`, `status`, `detail` and `instance`; this applies globally, including the existing `/otis` and `/search` endpoints.
- HTTP status codes of existing endpoints stay unchanged; only the error body format changes. The only known consumer (`velocity-plugin`) evaluates status codes only, so this is not treated as breaking. The changelog notes the format switch.
- Bean validation failures are reported as a problem with a `violations` extension member.
- Problem `type` values are stable URIs under a common Otis base (`https://otis.onelitefeather.net/problems/<slug>`); they identify the problem and need not resolve.
- Every problem response carries the current trace id as an extension member when a trace is active, so a reported error can be looked up in the tracing backend; server-side errors are recorded on the active span (error status plus the problem `type`).
- The OpenAPI spec describes a reusable `ProblemDetail` schema and declares `application/problem+json` error responses for every endpoint.
- The `java-client` exposes a `ProblemDetail` model and a helper that turns an `ApiException` into a `ProblemDetail`, so callers do not parse error bodies themselves.

## Capabilities

### New Capabilities
- `api-error-responses`: the error contract of the Otis HTTP API, i.e. Problem Details format, problem type identifiers, validation error reporting and the client-side access to problem details.

### Modified Capabilities

## Impact

- **Backend**: new dependency on Micronaut's problem-json support (`io.micronaut.problem:micronaut-problem-json`, version from the Micronaut platform catalog; replaces the global `ErrorResponseProcessor`); exception handlers mapping domain errors to problem types.
- **Observability**: uses the existing OpenTelemetry tracing setup (`otel.traces.exporter`, off unless `OTEL_TRACES_EXPORTER=otlp`); no new tracing dependency.
- **API**: error body format of every endpoint changes from `vnd.error` to `application/problem+json`; status codes unchanged.
- **java-client**: spec `java-client/specs/otis-api-1.0.1.yml` gains the `ProblemDetail` schema and error responses (maintained by hand, as today); new helper for reading problems from `ApiException`.
- **velocity-plugin**: no functional change expected (status-code based handling only).
- **User-facing text**: problem `title`/`detail` are English, developer-facing API texts consumed by services, not shown to players; no player-facing text and no translation keys are added.
- **Follow-up**: `add-player-settings` builds on this change and must land after it.
