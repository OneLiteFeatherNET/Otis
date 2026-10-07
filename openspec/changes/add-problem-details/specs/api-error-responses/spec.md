# Spec Delta

## Purpose

Defines the error contract of the Otis HTTP API: every error is reported as an RFC 9457 Problem Details document with a stable, machine-readable problem type, so services can react to errors without parsing free text.

## ADDED Requirements

### Requirement: Errors use Problem Details format
The system SHALL answer every HTTP error response (status 4xx or 5xx) of every endpoint with a body of media type `application/problem+json` that conforms to RFC 9457 and contains at least the members `type`, `title` and `status`, where `status` equals the HTTP status code of the response.

#### Scenario: Unknown route
- **WHEN** a client sends `GET /does-not-exist`
- **THEN** the response status is 404
- **AND** the `Content-Type` is `application/problem+json`
- **AND** the body contains `type`, `title` and `status` with value 404

#### Scenario: Malformed request body
- **WHEN** a client sends `POST /otis` with a body that is not valid JSON
- **THEN** the response status is 400
- **AND** the body is a Problem Details document with `status` 400

#### Scenario: Unexpected server error
- **WHEN** handling a request fails with an unexpected exception
- **THEN** the response status is 500
- **AND** the body is a Problem Details document whose `detail` does not expose exception messages, class names or stack traces

### Requirement: Problem types are stable URIs
The system SHALL identify each kind of problem by a `type` URI of the form `https://otis.onelitefeather.net/problems/<slug>`, where `<slug>` is lowercase kebab-case and never changes once released. Problems without a more specific type SHALL use `about:blank` as defined by RFC 9457, with `title` set to the standard reason phrase of the status code.

#### Scenario: Specific problem type
- **WHEN** a request fails with a domain error that has a defined slug
- **THEN** the `type` member is `https://otis.onelitefeather.net/problems/<slug>`

#### Scenario: Generic problem
- **WHEN** a request fails with a plain HTTP error without a defined slug (e.g. unknown route)
- **THEN** the `type` member is `about:blank`
- **AND** the `title` member is the reason phrase of the status code

### Requirement: Validation errors list violations
The system SHALL report request validation failures with status 400, `type` `https://otis.onelitefeather.net/problems/constraint-violation` and an extension member `violations`, an array of objects with `field` (the path of the invalid input) and `message`.

#### Scenario: Constraint violation on input
- **WHEN** a request violates a declared validation constraint of an endpoint
- **THEN** the response status is 400
- **AND** `type` is `https://otis.onelitefeather.net/problems/constraint-violation`
- **AND** `violations` contains one entry per violated constraint naming the field

### Requirement: Problems carry the trace id
The system SHALL add an extension member `traceId` holding the W3C trace id of the active trace to every problem response while a trace is being recorded, and SHALL omit the member when no valid trace is active. Server errors (5xx) SHALL be recorded once on the active span with error status; client errors (4xx) SHALL NOT mark the span as error.

#### Scenario: Tracing enabled
- **WHEN** tracing is enabled and a request fails
- **THEN** the problem body contains `traceId` equal to the trace id of the server span of that request

#### Scenario: Tracing disabled
- **WHEN** tracing is disabled (no exporter configured) and a request fails
- **THEN** the problem body contains no `traceId` member

#### Scenario: Server error marks span
- **WHEN** a request fails with status 500 while tracing is enabled
- **THEN** the server span has status ERROR and records the exception exactly once

#### Scenario: Client error does not mark span
- **WHEN** a request fails with status 404 while tracing is enabled
- **THEN** the server span status is not ERROR

### Requirement: Existing endpoints stay compatible
The system SHALL keep paths, HTTP methods, success status codes, success response bodies and error status codes of all existing endpoints under `/otis` and `/search` unchanged; only the body and `Content-Type` of error responses change to Problem Details. Clients built against the previous API (including released `velocity-plugin` builds) SHALL keep working without modification.

#### Scenario: Player not found keeps status 404
- **WHEN** a client calls `GET /otis/byId/{owner}` for a player uuid that is not stored
- **THEN** the response status is 404 as before the change

#### Scenario: Search not found keeps status 404
- **WHEN** a client calls `GET /search/byName/{name}` for an unknown name
- **THEN** the response status is 404 as before the change

#### Scenario: Successful responses unchanged
- **WHEN** a client calls `GET /otis/byId/{owner}` for a stored player
- **THEN** the response status is 200 and the body has exactly the same members and values as before the change

#### Scenario: Update with mismatching owner keeps status 400
- **WHEN** a client calls `POST /otis/update/{owner}` with a request that the endpoint rejected with 400 before the change
- **THEN** the response status is still 400

### Requirement: Client exposes problem details
The Java client SHALL provide a `ProblemDetail` model and a way to obtain it from a failed API call, returning an empty result when the error body is not a Problem Details document. Existing public client methods and models SHALL keep their signatures.

#### Scenario: Read problem from failed call
- **WHEN** a client call fails with an error response carrying a Problem Details body
- **THEN** the caller can obtain a `ProblemDetail` with the same `type`, `title`, `status`, `detail` and extension members

#### Scenario: Non-problem error body
- **WHEN** a client call fails with an error body that is not Problem Details (e.g. from a proxy)
- **THEN** obtaining the problem yields an empty result instead of throwing

#### Scenario: Existing client API unchanged
- **WHEN** code compiled against the previous client version is compiled against the new client version
- **THEN** it compiles without changes
