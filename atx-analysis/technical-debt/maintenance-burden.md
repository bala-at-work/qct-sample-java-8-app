# Maintenance Burden

[← Back to index](../README.md) · Related: [summary.md](summary.md) · [outdated-components.md](outdated-components.md) · [../analysis/complexity-analysis.md](../analysis/complexity-analysis.md)

Where ongoing effort concentrates, and why.

## Burden Ranking

### 1. `pom.xml` — Highest burden
**Why**: 20 hand-pinned dependencies, 8 of which explicitly override the parent BOM; one unused property (`spring.version`) that disagrees with the pinned versions; one duplicated declaration (`spring-test`); one exclusion (`spring-boot-starter-test` → `spring-test`) that exists only to paper over the explicit pinning; two test-only artifacts at compile scope; one entirely unused artifact.

**Consequence**: any dependency change requires reasoning about four interacting mechanisms (parent BOM, explicit pins, exclusions, scopes). This single file holds the majority of the project's High and Medium debt.

**Relief**: delete the explicit Spring pins and the property, let the Boot parent manage versions, fix scopes, drop `commons-codec`. The file shrinks substantially and becomes self-consistent.

### 2. Platform upgrade path — High burden, one-time
**Why**: Java 8 → modern LTS and Spring Boot 1.x → 3.x is a multi-front change: `javax.*` → `jakarta.*`, Boot property and auto-configuration renames, Actuator endpoint model change, Spring Security absence to be decided, Guava API breakage, Spock 1 → 2 with a JUnit 5 provider, and a Groovy compiler plugin that must be *added* for the existing spec to compile at all.

**Consequence**: cannot be done incrementally per-file; it is a coordinated change validated by the test suite.

**Relief**: the application code is only 417 production lines and the surface touching framework internals is tiny — the handful of edit sites are enumerated in [remediation-plan.md](remediation-plan.md).

### 3. `FileSystemStorage` — High burden, by design
**Why**: it is a declared stub (`"Replace with real implementation"`) that returns `logback.xml` for every UUID. Any real use of this service requires writing a genuine backend, plus UUID→file metadata persistence that does not exist anywhere in the project.

**Consequence**: the service is not production-functional as written.

**Relief**: the `FileStorage`/`FilePointer` ports mean the replacement is additive — nothing in the web or caching layer changes. An object-store-backed `FilePointer` needs only to supply size, name, ETag, media type, last-modified, and a stream.

### 4. ETag/hashing cost — Medium burden
**Why**: `FileSystemPointer`'s constructor hashes the entire file with SHA-512 and probes its content type, and a new pointer is constructed on every request. There is no cache keyed by path+mtime.

**Consequence**: cost scales with file size × request rate, including for `HEAD` and `304` responses where no bytes are sent. Also an amplification vector (see S8 in [../analysis/security-patterns.md](../analysis/security-patterns.md)).

**Relief**: cache the hash (keyed by path + size + last-modified), or take the ETag from storage metadata (e.g. an object store's own checksum) rather than recomputing.

### 5. Threading model under throttling — Medium burden
**Why**: `RateLimiter.acquire` blocks the servlet worker thread for the full duration of a 1 MB/s transfer. Thread-pool capacity, not bandwidth, becomes the concurrency limit, and the limiter is per-request so aggregate egress is uncapped.

**Consequence**: capacity planning is non-obvious and load behaviour is easy to regress.

**Relief**: `StreamingResponseBody`/async dispatch, and/or a shared `RateLimiter` for a global cap.

### 6. Test stack and Groovy build wiring — Medium burden
**Why**: Spock 1.0 + Groovy 2.4 are tied to the old platform; no Groovy compiler plugin is configured, so `src/test/groovy` is not part of the declared build; the spec file is named `DownloadControllerTest.groovy` but declares `class DownloadControllerSpec`, which conflicts with conventional Surefire include patterns.

**Consequence**: the project's only meaningful test coverage is fragile and may not execute at all under the declared configuration — exactly the coverage needed to validate an upgrade.

**Relief**: add `gmavenplus-plugin`, align the class and file names, move to Spock 2.x on JUnit 5. (Guardrail: the specifications themselves must be preserved — adjust configuration and names, never delete tests.)

### 7. Error handling and diagnostics — Low burden
**Why**: three `catch` blocks all rethrow as `IllegalArgumentException`; `getResource()` results are dereferenced without null checks; there is no `@ControllerAdvice`; logging is TRACE/DEBUG only.

**Consequence**: operational failures (missing file, unreadable file, packaging error) all look alike and surface as generic `500`s.

**Relief**: narrow exception types, add a controller advice mapping I/O failure to `503`/`404` as appropriate, and null-check resource lookups.

### 8. Low-burden tidy-ups
- `MainApplication:10` dead `new Integer("1234")` statement.
- `Sha512ShallowEtagHeaderFilter` — unwired and squatting in `org.springframework.web.filter`. Either register it deliberately (while noting it buffers responses, defeating streaming) or move it out of the framework package.
- Missing `@Override` on `FileStorageStub.findFile`.
- `FilePointer.modifiedAfter` is named inversely to its behaviour; renaming it (an internal, non-public-API-breaking change within this project) would remove the single most likely source of a future logic regression.

## Knowledge-Concentration Risk

| Area | Risk |
|---|---|
| HTTP conditional-request semantics in `ExistingFile` + the inverted `modifiedAfter` | A maintainer who has not read [../reference/interfaces.md](../reference/interfaces.md) will likely invert a caching decision. The 12 Spock specifications are the safety net. |
| Guava-specific idioms (`transform/or`, `Files.hash`, `RateLimiter`) | These are the exact call sites that break on upgrade. |
| Profile-based bean substitution | Non-obvious that `@Profile("!test")`/`@Profile("test")` is the mocking mechanism; a new test that forgets `@ActiveProfiles("test")` silently gets `logback.xml` bytes. |

## No-Burden Areas

`ThrottlingInputStream` (pure delegation), `DownloadController` (no branching), both interfaces (minimal and stable), and `logback.xml` require essentially no ongoing maintenance.
