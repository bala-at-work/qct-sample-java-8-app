# Remediation Plan

[← Back to index](../README.md) · Related: [../technical-debt-report.md](../technical-debt-report.md) · [outdated-components.md](outdated-components.md) · [../migration/component-order.md](../migration/component-order.md)

Prioritized, ordered action items. Priorities use qualitative terms only.

---

## Priority: High — EOL/deprecated runtimes and frameworks

### R1. Remove the plaintext HTTP repository (do this first — it is a one-line, zero-risk change)
- **File**: `pom.xml` `<repositories>`
- **Action**: delete the `io.spring.repo.maven.milestone` repository entirely. No declared dependency requires a Spring milestone build. If a milestone artifact is genuinely needed later, add it back with `https://`.
- **Validates**: dependency resolution still succeeds from Maven Central.
- **Addresses**: H4, S3.

### R2. Upgrade the Java target to a supported LTS and modernize Spring accordingly
- **Recommended mechanism**: run `AWS/java-version-upgrade` targeting Java 21, which handles the JDK bump together with the Spring ecosystem and Jakarta EE namespace migration.
- **Changes expected**:
  1. `maven-compiler-plugin` → current version, using `<release>21</release>` instead of `<source>`/`<target>`.
  2. `spring-boot-starter-parent` → current supported Boot 3.x release.
  3. **Delete** all eight explicit `org.springframework:spring-*` version pins and the unused `<spring.version>` property; let the Boot BOM manage them.
  4. **Delete** the `spring-test` exclusion from `spring-boot-starter-test` and the duplicate `spring-test` declaration (the exclusion only existed to protect the explicit pin).
  5. Remove the redundant `<version>` on the Boot starters so they inherit from the parent.
  6. `javax.servlet` → `jakarta.servlet` wherever it surfaces — in this codebase only `Sha512ShallowEtagHeaderFilter`'s superclass is affected.
- **Code edit sites** (the complete list for this repository):
  | Site | Required change |
  |---|---|
  | `MainApplication.java:10` | delete the dead `new Integer("1234")` statement (deprecated for removal) |
  | `ExistingFile.java:115-119` (`toMediaType`) | rewrite without Guava's removed `Optional.transform/or`: read `input.charset()` and build the Spring `MediaType` with `java.util.Optional` semantics |
  | `FileSystemPointer.java:22` | replace deprecated `Files.hash(target, Hashing.sha512())` with `Files.asByteSource(target).hash(Hashing.sha512())` |
  | `DownloadController.java:38,50` + `ExistingFile.java:47,54,59` | optionally migrate `Optional<java.util.Date>` header binding to `Optional<ZonedDateTime>`/`Instant`, dropping the `Date::toInstant` hop |
  | `Sha512ShallowEtagHeaderFilter.java` | follow Spring's relocated/`jakarta`-based `ShallowEtagHeaderFilter`; consider moving the class out of the `org.springframework.web.filter` package |
- **Guardrails**: do not rename public types or methods; do not delete any test; do not downgrade any version.
- **Addresses**: H1, H2, H3, S4, M1 (partly), L5.

### R3. Secure or remove Spring Boot Actuator
- **Action**: no application code references Actuator. Either (a) remove `spring-boot-starter-actuator` from `pom.xml`, or (b) keep it and, after R2, restrict exposure explicitly (`management.endpoints.web.exposure.include=health,info`, a separate management port, and authentication).
- **Addresses**: M5, S2.

---

## Priority: Medium — Outdated dependencies

### R4. Upgrade or eliminate library dependencies
| Dependency | Action |
|---|---|
| `commons-codec` 1.10 | **Delete** — no source file imports it |
| `commons-io` 2.4 | Prefer deleting and inlining the single used constant (`ONE_MB`) as a private `static final long` in `ExistingFile`; otherwise upgrade to current |
| `guava` 18.0 | Upgrade to a current release together with the two call-site rewrites listed in R2 |
| `cglib-nodep` 3.1 | Move to `<scope>test</scope>`; upgrade or drop if the new Spock line supplies its own byte-code backend |
| `groovy-all` 2.4.3 | Move to `<scope>test</scope>`, switch to `org.apache.groovy` coordinates at a version matching the chosen Spock release |
| `spring-context-support` | Remove if nothing references it after R2 |
- **Before committing any version**: read the exact resolved coordinates from the dependency tree and check them against a vulnerability database; prefer the nearest non-vulnerable version. Never downgrade below the current version.
- **Addresses**: M1–M4.

### R5. Repair the test build wiring (configuration only — no test is removed or disabled)
1. Add a Groovy compiler plugin (`gmavenplus-plugin`) so `src/test/groovy` is actually compiled — today it is not part of the declared build.
2. Rename `DownloadControllerTest.groovy` to `DownloadControllerSpec.groovy` to match the declared class, and configure Surefire/Failsafe includes for `*Spec` — or keep the file name and rename nothing if Surefire is configured for both patterns. Either way, the 12 feature methods must be preserved verbatim.
3. Upgrade to Spock 2.x on the JUnit 5 platform with a matching Groovy line.
4. De-duplicate `spring-test` (also covered by R2).
- **Addresses**: L3, L4, A10.

### R6. Eliminate per-request full-file hashing
- **Action**: cache the computed ETag keyed by (path, size, last-modified), or source the validator from storage metadata instead of recomputing. Keep the ETag format (quoted strong validator) unchanged so the existing specs still pass.
- **Addresses**: P1, P2, S8.

---

## Priority: Low — Code quality and architectural issues

### R7. Implement a real storage backend
- Replace the `FileSystemStorage` stub (which returns `logback.xml` for every UUID) with a genuine adapter — for example an Amazon S3-backed `FileStorage` plus an S3-backed `FilePointer` using the object's own checksum as the ETag and its metadata for size/content type. The existing ports mean no change to `DownloadController` or `ExistingFile`.
- Keep the `FileSystemStorage` class present (guardrail: primary type declarations are retained) and keep its profile semantics; add the new adapter alongside.
- **Addresses**: L6, and makes R6 natural.

### R8. Tighten error handling
- Narrow the three `IllegalArgumentException` rethrows (`FileSystemPointer:27,36`, `ExistingFile:72`) to meaningful types (for example an `UncheckedIOException` or a domain `FileAccessException`).
- Null-check `getClass().getResource(...)` in `FileSystemStorage:26` and `FileExamples:14` before constructing a `File`.
- Add a `@ControllerAdvice` mapping storage failures to appropriate statuses so I/O errors do not all appear as generic `500`s.
- **Addresses**: A4, A5, S9, S10.

### R9. Clarify the inverted caching predicate
- `FilePointer.modifiedAfter(Instant)` returns `true` when the *client's* copy is current. Either rename it (e.g. `clientCopyIsCurrent`) across the interface and its implementation, or add a short contract comment. This is an internal interface of this project; the existing specs pin the behaviour, so the change is verifiable.
- **Addresses**: the top cognitive-complexity trap in [../analysis/complexity-analysis.md](../analysis/complexity-analysis.md).

### R10. Decide the fate of `Sha512ShallowEtagHeaderFilter`
- It is unwired and sits in a squatted framework package. Either register it intentionally (noting that `ShallowEtagHeaderFilter` buffers whole responses, which conflicts with this service's streaming design) or relocate it into `com.nurkiewicz.download`. Do not simply delete it without confirming it is unused by downstream consumers.
- **Addresses**: A1, L7.

### R11. Harden the HTTP response
- Add `Content-Disposition: attachment; filename="..."` and `X-Content-Type-Options: nosniff`, and constrain served `Content-Type` values, so that probed content types cannot be leveraged for stored XSS once a real (user-supplied) storage backend exists.
- Consider `StreamingResponseBody`/async dispatch and a shared `RateLimiter` to decouple throttled transfers from container threads and to cap aggregate egress.
- Consider `Range`/`Content-Range` support for resumable downloads.
- **Addresses**: S6, S7, P3, P4, P6.

### R12. Add supply-chain and quality automation
- `versions-maven-plugin` or Dependabot for dependency currency; a vulnerability scanner; SBOM generation; a CI definition (none exists in the repository).

---

## Suggested Execution Order

```
R1 (repository, trivial)
 └─> R2 (Java + Spring Boot upgrade)   ← the gate for everything else
       ├─> R3 (Actuator decision)
       ├─> R4 (library versions/scopes)
       ├─> R5 (test build wiring)      ← restores the validation safety net
       │     └─> R6 (ETag caching)
       │           └─> R7 (real storage backend)
       └─> R8, R9, R10, R11, R12 (opportunistic, independent)
```

Rationale: R1 is risk-free and removes a build-time attack surface immediately. R2 unblocks every dependency upgrade. R5 restores confidence in the test suite before behavioural changes (R6–R11) are attempted. R7 onwards are product decisions rather than debt repayment.

## Validation Approach

No build or test execution is performed by this analysis transformation. For the remediation work itself, validate with the criteria in [../migration/validation-criteria.md](../migration/validation-criteria.md) and the behavioural contract in [../migration/test-specifications.md](../migration/test-specifications.md).
