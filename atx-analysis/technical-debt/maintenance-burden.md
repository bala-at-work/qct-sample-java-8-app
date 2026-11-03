# Maintenance Burden

[← Back to index](../README.md) · [Summary](summary.md)

Areas of the codebase that demand disproportionate maintenance attention.

## Hotspot 1 — `pom.xml` (High burden)

**Why:** 20 of the 22 recorded debt items originate here. The file fights its own parent BOM: it inherits `spring-boot-starter-parent:1.2.3.RELEASE` but then re-declares explicit versions on every starter *and* pins nine Spring Framework artifacts to `4.1.6.RELEASE` — a version the Boot 1.2.3 BOM does not manage. It also declares an unused `<spring.version>` property set to a *third* value (`4.1.1.RELEASE`), and declares `spring-test` twice.

**Consequence:** Any version change must be made in several places consistently. A naive parent-version bump will not take effect, because explicit `<version>` elements override it. This is the single highest-value cleanup in the repository.

**Recommended attention:** Remove all explicit `<version>` elements from `spring-boot-starter-*` and `org.springframework:*` dependencies so the parent BOM governs them; delete the unused `<spring.version>` property; de-duplicate `spring-test`.

## Hotspot 2 — `FileSystemStorage` (High burden)

**Why:** The only production `FileStorage` implementation is a stub. `findFile(UUID)` discards its argument and returns a `FileSystemPointer` over the classpath resource `/logback.xml` for every call. Its Javadoc states: *"Stub implementation that for any UUID returns `logback.xml` file. Replace with real implementation."*

**Consequence:** The service cannot serve real content. Additionally, `new File(resource.getFile())` is unsafe — it breaks when the resource lives inside a JAR (the normal Spring Boot packaging) and does not URL-decode the path. The method will also throw `NullPointerException` if the resource is absent.

**Recommended attention:** Implement a real storage adapter (filesystem directory, S3, or database-backed) behind the existing `FileStorage` port. The port abstraction means no controller changes are needed.

## Hotspot 3 — `ExistingFile` (Medium burden)

**Why:** Highest-complexity class in the codebase (9 methods, ~130 LOC) and the convergence point for HTTP caching logic, redirect construction, response assembly, throttling, and Guava↔Spring `MediaType` conversion. Five distinct responsibilities in one class.

**Consequence:** Changes to any one concern risk the others. The hardcoded `RateLimiter.create(ONE_MB)` cannot be tuned without editing code. The `toMediaType` bridge relies on Guava's legacy `Optional.transform/or` API and will need rework on a Guava upgrade.

**Recommended attention:** Extract throttling configuration to a `@ConfigurationProperties` bean; isolate the `MediaType` conversion into a small dedicated converter.

## Hotspot 4 — `DownloadController` request-header binding (Medium burden)

**Why:** `@RequestHeader(IF_MODIFIED_SINCE) Optional<Date>` depends on Spring 4.1-era conversion of RFC-1123 header strings into `java.util.Date`, combined with `Optional` unwrapping. Both the `Optional` handling and the date conversion behavior have changed across Spring versions.

**Consequence:** This is the most likely place to break silently during a Spring upgrade — binding may yield empty `Optional`s instead of parsed dates, which would quietly disable `304` responses while all other tests still pass. The two conditional-request Spock specs are the guardrail here.

**Recommended attention:** During upgrade, prefer `@RequestHeader(value = IF_MODIFIED_SINCE, required = false) Instant` or read via `WebRequest.checkNotModified(...)`, and verify the `If-Modified-Since` specs explicitly.

## Hotspot 5 — Test infrastructure (Medium burden)

**Why:** Three compounding issues:
1. `DownloadControllerTest.groovy` declares `class DownloadControllerSpec` — the Groovy compiler requires public class names to match the filename, so this source does not compile as written.
2. Spock `1.0-groovy-2.4` is tied to JUnit 4 and Groovy 2.4; Spock 2.x requires the JUnit 5 platform, so a Groovy/JUnit upgrade is an all-or-nothing change.
3. `groovy-all` is at compile scope, leaking the Groovy runtime into the production classpath.

**Consequence:** Test modernization is coupled to three simultaneous version moves. The guardrail requirement to preserve all tests means this must be fixed rather than bypassed.

## Hotspot 6 — Orphaned test-scope filter (Low burden)

**Why:** `src/test/java/org/springframework/web/filter/Sha512ShallowEtagHeaderFilter.java` extends Spring's `ShallowEtagHeaderFilter` and sits in Spring's own package solely to override the `protected generateETagHeaderValue`. It is registered nowhere and referenced by nothing.

**Consequence:** Package squatting on a third-party namespace breaks under JPMS/module-path builds and is fragile against upstream signature changes. It is dead weight carried through every upgrade.

## Configuration Gaps (Low burden)

- No `application.properties` or `application.yml`. All behavior depends on Boot defaults plus the two `@Profile` annotations.
- Spring Boot 1.x exposes Actuator endpoints without authentication by default, and nothing in this repository restricts them. See [analysis/security-patterns.md](../analysis/security-patterns.md).
- No `Dockerfile`, no CI pipeline definition, no deployment manifests — runtime JDK selection is unpinned outside the compiler plugin.

## Burden Ranking

| Rank | Area | Burden | Primary driver |
|---|---|---|---|
| 1 | `pom.xml` | High | Version pinning conflicts with parent BOM |
| 2 | `FileSystemStorage` | High | Unimplemented production behavior |
| 3 | `ExistingFile` | Medium | Multiple responsibilities, highest complexity |
| 4 | `DownloadController` header binding | Medium | Upgrade-fragile framework behavior |
| 5 | Test infrastructure | Medium | Coupled Groovy/Spock/JUnit versions + non-compiling filename |
| 6 | `Sha512ShallowEtagHeaderFilter` | Low | Dead code in a squatted package |

Next: [remediation-plan.md](remediation-plan.md)
