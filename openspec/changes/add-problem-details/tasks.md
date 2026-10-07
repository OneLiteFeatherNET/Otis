# Tasks

## Execution Plan

Branch `feat/problem-details` from `origin/main`; all agents are Sonnet in their own worktree on that branch, sequential per wave. Every agent prompt restates: built-in first, Java 25 features (records, sealed types, pattern `switch`), no user-facing player text (problem texts are English developer text, no i18n bundles), SLF4J logging rules, OpenTelemetry API only (no SDK in main code), F.I.R.S.T. tests, test-first, Conventional Commits with the `Claude-Session` trailer.

| Wave | Agent | Task IDs | Model | May Touch | Must Not Touch |
| ---- | ----- | -------- | ----- | --------- | -------------- |
| 1 | backend-problems | 1.1-1.2, 2.1-2.6 | sonnet | `backend/**`, `settings.gradle.kts`, `openspec/**` | `java-client/**`, `velocity-plugin/**` |
| 2 | client-problems | 3.1-3.4, 4.1-4.2 | sonnet | `java-client/**`, `velocity-plugin/build.gradle.kts` (only if needed), `openspec/changes/add-problem-details/tasks.md` | `backend/src/main/**` |
| 3 | main session | 5.1-5.2 | - | PR metadata | source code |

## 1. Setup

- [x] 1.1 Copy `openspec/config.yaml`, `openspec/specs/.gitkeep`, `openspec/changes/archive/.gitkeep` and `openspec/changes/add-problem-details/` from `/mnt/projects/oss/onelitefeather/Otis` into the worktree and commit as `docs(openspec): add add-problem-details change`; verify `openspec validate add-problem-details --strict` passes in the worktree
- [x] 1.2 Add H2 test configuration `backend/src/test/resources/application-test.yml` (in-memory H2, `hbm2ddl=create-drop`) and verify an empty `@MicronautTest` context starts in `./gradlew :backend:test`

## 2. Backend

- [x] 2.1 (integration, characterization) Write REST Assured tests pinning current status codes: 404 for `GET /otis/byId|byName/{unknown}`, `GET /search/byId|byName/{unknown}`, `POST /otis/delete/{unknown}`; 400 for `POST /otis/update/{owner}` mismatch; 200 + exact body members for `GET /otis/byId/{known}`; each test creates its own fixtures; verify they pass on the unchanged code and commit as `test(api): pin status codes of existing endpoints`
- [x] 2.2 (integration, red first) Write failing tests for the "Errors use Problem Details format" and "Problem types are stable URIs" scenarios (unknown route, malformed JSON, 500 without internals, `about:blank` title); add `micronaut-problem-json` (platform BOM version) and `micronaut.problem.stack-trace: false`; verify the new tests and 2.1 pass
- [x] 2.3 (unit, red first) Write tests for the sealed `OtisProblemException` base and its `ExceptionHandler` mapping to `https://otis.onelitefeather.net/problems/<slug>` with extension members; implement; verify unit tests pass without a server
- [x] 2.4 (integration, red first) Write a test for a constraint violation on `POST /otis` asserting slug `constraint-violation` and `violations[{field,message}]`; implement via the module or a dedicated handler (D3); verify it passes
- [x] 2.5 (unit, red first) Add `opentelemetry-sdk-testing` (test scope); write `OpenTelemetryExtension` tests: `traceId` present with active span, absent without, 5xx sets ERROR + one exception event, 4xx not ERROR; implement D4 incl. one ERROR log for unhandled exceptions asserted via captured appender; verify tests pass
- [x] 2.6 Add `@ApiResponse` problem responses (`application/problem+json`, `ProblemDetail` schema) to all existing controller methods; verify `./gradlew :backend:build` succeeds and the generated OpenAPI YAML under `backend/build` contains `ProblemDetail`

## 3. Client

- [x] 3.1 Before changing the client, run `./gradlew :java-client:compileJava` and save `javap -public` output of generated `PlayerApi`, `SearchApi` and all models to the scratchpad as baseline
- [x] 3.2 Create `java-client/specs/otis-api-1.1.0.yml` (copy of 1.0.1, version 1.1.0) with the `ProblemDetail` schema and `application/problem+json` error responses for every operation; point `inputSpec` to it; leave 1.0.1 in place; verify `./gradlew :java-client:openApiGenerate` succeeds and a `ProblemDetail` model is generated
- [x] 3.3 (unit, red first) Write tests for `ProblemDetails.from(ApiException)`: problem body, extension members (`traceId`, `violations`), non-JSON body, empty body -> empty `Optional`; implement with the generated Jackson mapper; verify tests pass
- [x] 3.4 Compare `javap -public` against the 3.1 baseline and verify only additions; verify `./gradlew :velocity-plugin:shadowJar` builds without source changes

## 4. Docs and full build

- [x] 4.1 Correct `CLAUDE.md` (client serializes with Jackson, not GSON; error format is Problem Details) in a separate `docs(claude): ...` commit; verify the diff touches only `CLAUDE.md`
- [x] 4.2 Run `./gradlew clean build` and verify it is green; tick all completed boxes in this file

## 5. Pull Request

- [ ] 5.1 Main session reviews the branch diff and test results against `specs/api-error-responses/spec.md`; verify every scenario maps to a passing test
- [ ] 5.2 Push `feat/problem-details` and open the pull request against `main` titled `feat(api): return rfc 9457 problem details for errors`, body summarizing changes, the compatibility evidence (2.1, 3.4) and ending with `https://claude.ai/code/session_012BmpdMdQ5wwagagQvfcq8S`; verify `gh pr view` shows the PR
