# Technical Debt Assessment

[← Back to index](../README.md) · [Root report](../technical-debt-report.md) · [Detail](../technical-debt/summary.md)

Source-location-indexed view of the technical debt inventory. For the executive summary and the AWS transformation recommendation, see [../technical-debt-report.md](../technical-debt-report.md).

## Debt Profile

| Dimension | Rating | Basis |
|---|---|---|
| Platform currency | **Poor** | Java 8, Spring Boot 1.2.3, Spring Framework 4.1.6 — all EOL |
| Dependency currency | **Poor** | Guava 18.0, Commons IO 2.4, Commons Codec 1.10, Groovy 2.4.3, Spock 1.0, `maven-compiler-plugin` 3.0 |
| Build hygiene | **Poor** | 10 version pins overriding the parent BOM, duplicate declaration, unused property, plaintext HTTP repository, missing Boot repackage plugin, no Groovy compiler plugin |
| Structural quality | **Good** | Acyclic dependency graph, correct abstraction direction, immutability, constructor injection |
| Code-level complexity | **Good** | Average CC ~1.15, no method above CC 4, zero loops |
| Functional completeness | **Poor** | The only production storage implementation is a stub |
| Test integrity | **Mixed** | Strong HTTP behavioral coverage (12 specs), but the Groovy source does not compile as named and no unit tests exist for the throttling or hashing classes |
| Security posture | **Poor** | No auth, no TLS, unsecured Actuator, EOL stack |
| Observability | **Poor** | DEBUG/TRACE logging only; no metrics, tracing, or structured logging |
| Operational readiness | **Poor** | No externalized configuration, no container image, no deployment manifests, no CI |

## Findings Indexed by Source Location

### `pom.xml`

| Line region | Finding | ID | Severity |
|---|---|---|---|
| `<properties>` | `<spring.version>4.1.1.RELEASE</spring.version>` declared but never referenced; actual artifacts use `4.1.6.RELEASE` — a third value from the parent's managed version | TD-03 | High |
| `<parent>` | `spring-boot-starter-parent:1.2.3.RELEASE` — EOL | TD-02 | High |
| `maven-compiler-plugin` config | `<source>1.8</source><target>1.8</target>` — EOL Java target; also the plugin itself is pinned to `3.0` (2012) | TD-01, TD-11 | High / Low |
| `<repositories>` | `http://repo.spring.io/milestone/` over plaintext HTTP | TD-04 | High |
| `<dependencies>` | 3 `spring-boot-starter-*` artifacts with hardcoded `1.2.3.RELEASE` versions overriding the parent BOM | TD-02 | High |
| `<dependencies>` | 9 `org.springframework:*` artifacts hardcoded to `4.1.6.RELEASE`; 7 of them are already supplied transitively by `spring-boot-starter-web` | TD-03 | High |
| `<dependencies>` | `spring-test:4.1.6.RELEASE` declared twice | TD-13 | Low |
| `<dependencies>` | `guava:18.0`, `commons-io:2.4`, `commons-codec:1.10`, `cglib-nodep:3.1` outdated | TD-06…TD-10 | Medium |
| `<dependencies>` | `groovy-all:2.4.3` at **compile** scope though used only by tests; legacy `org.codehaus.groovy` coordinates | TD-09 | Medium |
| `<dependencies>` | `commons-codec` has no source usage anywhere | TD-08 | Medium |
| `<build>` | `spring-boot-maven-plugin` not declared — no executable JAR is repackaged | — | Medium |
| `<build>` | No Groovy compiler plugin, so `src/test/groovy` has no configured compiler | TD-18 | Low |
| — | SLF4J used directly in 3 classes but never declared | — | Low |

### `src/main/java/com/nurkiewicz/download/MainApplication.java`

| Line | Finding | ID | Severity |
|---|---|---|---|
| 7 | Class is package-private yet referenced as the `@ContextConfiguration` class by `DownloadControllerSpec` | TD-21 | Low |
| 10 | `Integer temp = new Integer("1234");` — deprecated constructor (since Java 9) assigned to an unused local. Dead code and a hard blocker for a clean build with escalated deprecation warnings. | TD-14 | Low |

### `src/main/java/com/nurkiewicz/download/FileSystemStorage.java`

| Line | Finding | ID | Severity |
|---|---|---|---|
| 23–30 | `findFile(UUID)` ignores `uuid` and always returns a pointer to classpath `/logback.xml`. Never returns `Optional.empty()`, so the `404` path is unreachable in production and the application's own configuration file is exposed to any caller. The 4-line Javadoc marks it a stub to be replaced. | TD-05 | High |
| 27 | `new File(resource.getFile())` — fails for resources inside a JAR (normal Boot packaging), does not URL-decode, and NPEs if the resource is absent | — | Low |

### `src/main/java/com/nurkiewicz/download/ExistingFile.java`

| Line | Finding | ID | Severity |
|---|---|---|---|
| ~46–52 | `serveWithCaching` higher-order indirection — justified, but the reader must visit two call sites to see what executes | — | Informational |
| ~55–65 | `cached()` — the densest logic in the repository: two `Optional` pipelines, a `Date`→`Instant` hop, and `modifiedAfter`, whose name reads as the inverse of its return value | — | Low |
| ~97–101 | `buildResource` hardcodes `RateLimiter.create(ONE_MB)` — a policy decision with no configuration hook | TD-16 | Low |
| ~97–101 | The stream from `FilePointer.open()` is handed to `InputStreamResource` with no explicit close guarantee on error paths | TD-20 | Low |
| ~124–127 | `toMediaType` uses Guava's legacy `Optional.transform(...).or(...)` and shadows two different `MediaType` types | — | Low |
| whole class | Five responsibilities: cache decision, redirect construction, response assembly, throttling setup, media-type conversion | — | Low |
| ~72–74 | `URISyntaxException` wrapped in `IllegalArgumentException`; the `301` response omits `ETag`/`Content-Length`/`Last-Modified` that the `200`/`304` paths include | — | Informational |

### `src/main/java/com/nurkiewicz/download/FileSystemPointer.java`

| Line | Finding | ID | Severity |
|---|---|---|---|
| 19–29 | Constructor eagerly computes a full SHA-512 over the file — **O(file size) on every request**, including `HEAD` and `304` responses that transfer nothing. Dominant runtime cost and an unauthenticated DoS lever. | — | Medium |
| 19–29 | `IOException` wrapped in `IllegalArgumentException`, preserving a message that typically contains the absolute path | — | Low |
| 38–44 | `FileNotFoundException` wrapped in `IllegalArgumentException` | — | Low |
| — | `tag` is cached from construction while `getSize()`/`getLastModified()` read live from the filesystem — the ETag can disagree with the size/timestamp if the file changes mid-flight | — | Low |

### `src/main/java/com/nurkiewicz/download/DownloadController.java`

| Line | Finding | ID | Severity |
|---|---|---|---|
| 38, 50 | `@RequestHeader(IF_MODIFIED_SINCE) Optional<Date>` depends on Spring 4.1-era RFC-1123→`Date` conversion combined with `Optional` unwrapping — the most upgrade-fragile binding in the codebase. A silent regression here would disable `304` responses while most tests still pass. | TD-19 | Low |

### `src/main/java/com/nurkiewicz/download/ThrottlingInputStream.java`

| Line | Finding | ID | Severity |
|---|---|---|---|
| 19–35 | `RateLimiter.acquire(n)` blocks the servlet thread; with a per-request limiter and no concurrency cap, concurrent large transfers can exhaust the container thread pool | — | Medium |
| ~40 | `skip` is **not** throttled, unlike the three `read` overloads — an uncommented asymmetry | — | Informational |

### `src/main/resources/`

| Finding | ID | Severity |
|---|---|---|
| No `application.properties` or `application.yml` exists. Actuator exposure, server settings, and the throttle rate all rely on defaults or hardcoded constants. | TD-22 | Low |

### `src/test/`

| File | Finding | ID | Severity |
|---|---|---|---|
| `DownloadControllerTest.groovy:25` | File declares `class DownloadControllerSpec` — Groovy requires the filename to match the public class name, so this source does not compile as written | TD-18 | Low |
| `FileStorageStub.java:20` | `findFile` is missing `@Override` | TD-15 | Low |
| `org/springframework/web/filter/Sha512ShallowEtagHeaderFilter.java:1` | Project class placed inside Spring's own package to reach a `protected` hook; never registered or referenced; illegal on a module path | TD-17 | Low |
| `FileExamples.java:15` | `new File(resource.getFile())` — same classpath-URL issue as `FileSystemStorage` (tolerable in a test fixture) | — | Informational |
| — | No unit tests for `ThrottlingInputStream`, `FileSystemPointer`, or `FileSystemStorage` — the three most algorithmically interesting production classes | — | Low |

## Debt by Category

| Category | High | Medium | Low | Total |
|---|---|---|---|---|
| EOL runtimes and frameworks | 3 | 0 | 0 | 3 |
| Supply chain / build security | 1 | 0 | 0 | 1 |
| Functional completeness | 1 | 0 | 0 | 1 |
| Outdated dependencies | 0 | 5 | 0 | 5 |
| Performance / scalability | 0 | 2 | 0 | 2 |
| Build hygiene | 0 | 1 | 3 | 4 |
| Code quality | 0 | 0 | 7 | 7 |
| Test integrity | 0 | 0 | 3 | 3 |
| Configuration / operability | 0 | 0 | 1 | 1 |

## What Makes This Debt Tractable

The debt is concentrated in **configuration rather than code**. Of the 22 catalogued items, 12 live in `pom.xml`. The production source itself is small, acyclic, immutable, well-factored, and free of the patterns that normally obstruct a JDK upgrade:

- No `javax.*` imports in any source file — the Jakarta migration is confined to the dependency graph.
- No reflection, no dynamic proxies, no bytecode manipulation, no `sun.misc`/`com.sun.*` internal APIs.
- No AWS SDK, no Log4j, no native libraries, no architecture-specific code.
- No persistence layer, ORM, or database driver to migrate.
- Exactly one deprecated API call (`new Integer(String)`), which is dead code and can simply be deleted.
- Single Maven module — no inter-module ordering to plan.

The practical consequence: the high-severity platform debt is addressable primarily through build-file changes plus one deleted line, with the test suite as the behavioral guardrail. The functional debt (the storage stub) is independent of the upgrade path and has zero blast radius on tests thanks to the `@Profile` split.

See [../technical-debt/remediation-plan.md](../technical-debt/remediation-plan.md) for the prioritized action items and [../migration/component-order.md](../migration/component-order.md) for sequencing.
