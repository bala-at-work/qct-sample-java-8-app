# Component Migration Order

- [Back to README](../README.md)
- Siblings: [test-specifications.md](test-specifications.md) · [validation-criteria.md](validation-criteria.md)
- See also: [../technical-debt/remediation-plan.md](../technical-debt/remediation-plan.md) · [../analysis/dependency-analysis.md](../analysis/dependency-analysis.md#upgrade-blocking-order)

> This document orders the work by the project's actual dependency graph. It is a sequencing guide for the remediation plan, not a schedule — no time estimates are given anywhere in this documentation.

## Table of Contents
1. [Ordering Principles](#ordering-principles)
2. [Dependency-Derived Order](#dependency-derived-order)
3. [Stage 0 — Build and Test Wiring](#stage-0--build-and-test-wiring)
4. [Stage 1 — POM Normalization](#stage-1--pom-normalization)
5. [Stage 2 — Runtime and Framework](#stage-2--runtime-and-framework)
6. [Stage 3 — Library Dependencies](#stage-3--library-dependencies)
7. [Stage 4 — Configuration and Security](#stage-4--configuration-and-security)
8. [Stage 5 — Code Quality](#stage-5--code-quality)
9. [Stage 6 — Operations](#stage-6--operations)
10. [Optional — Storage Implementation](#optional--storage-implementation)
11. [Per-File Migration Order](#per-file-migration-order)
12. [Checkpoint Matrix](#checkpoint-matrix)
13. [Rollback Points](#rollback-points)

## Ordering Principles

| # | Principle | Applied here as |
|---|---|---|
| 1 | **Verification capability comes first** | The Spock specification does not run; activate it before changing anything else |
| 2 | **Unblock before upgrading** | The explicit Spring version pins make a Boot parent bump a silent no-op — remove them first |
| 3 | **Leaf dependencies before dependents** | `FilePointer`/`FileStorage` (fan-out 0–1) are reviewed before `ExistingFile`/`DownloadController` (fan-out 2–3) |
| 4 | **Platform before libraries** | Current Guava requires Java 11+; Boot 3.x requires Java 17 |
| 5 | **Severity order within a stage** | High (runtime/framework) → Medium (dependencies) → Low (code quality) |
| 6 | **One verifiable change set at a time** | Re-run the specification after each stage, not after each file edit |

## Dependency-Derived Order

```
   STAGE 0  Build & test wiring            ← gates everything
       │    register src/test/groovy; establish passing baseline
       ▼
   STAGE 1  POM normalization              ← gates Stage 2
       │    remove Spring pins, unused property, duplicate, dead deps
       ▼
   STAGE 2  Runtime & framework   🔴 High
       │    Java 8→11→17→21 ; Boot 1.2.3→1.5→2.x→3.x
       │    (Spock 1.x→2.x forced at Boot 2.2+)
       ├─────────────────────────┐
       ▼                         ▼
   STAGE 3  Libraries  🟠 Medium   STAGE 4  Config & security  🟠 Medium
       │    Guava (needs JDK 11+)     externalize config; secure actuator
       └─────────────┬───────────┘
                     ▼
   STAGE 5  Code quality  🟡 Low
       │    dead code, deprecated APIs, lazy ETag, exceptions
       ▼
   STAGE 6  Operations  🟡 Low
            CI, container, coverage, .gitignore, README

   OPTIONAL  Storage implementation (feature work, not modernization)
```

## Stage 0 — Build and Test Wiring

**Why first.** The project's entire behavioral safety net — 12 Spock feature methods covering the full HTTP contract — is neither compiled nor executed by a default Maven build, because `src/test/groovy` is not a registered test-source root. Every subsequent stage is unverifiable until this is fixed. Tests that exist but never run are worse than no tests, because they imply assurance that is not being delivered.

| Order | Action | Files touched | Severity |
|---|---|---|---|
| 0.1 | Add a Groovy compilation plugin (`gmavenplus-plugin` or `groovy-eclipse-compiler`) so `src/test/groovy` is a test-source root | `pom.xml` | 🟠 Medium |
| 0.2 | Confirm all 12 feature methods compile and pass **against the unmodified Java 8 / Boot 1.2.3 code** | — | 🟠 Medium |
| 0.3 | Record the observed status codes and headers as the behavioral baseline (the migration oracle) | — | — |
| 0.4 | Rename `DownloadControllerTest.groovy` → `DownloadControllerSpec.groovy` to match the class name | test source | 🟡 Low |

**Do not proceed past Stage 0 until the specification passes.** If it cannot be made to pass on the current code, the baseline is unknown and every later verification is meaningless.

## Stage 1 — POM Normalization

**Why before Stage 2.** The POM declares a Spring Boot parent (whose BOM manages Spring Framework versions) and then overrides those versions with explicit `4.1.6.RELEASE` pins on eight artifacts. **The pins win.** Bumping the parent while they remain produces a Boot/Spring combination that upstream never tested, and the failure mode is a runtime `NoSuchMethodError` rather than a compile error — it would escape a build-only check.

| Order | Action | Rationale | Severity |
|---|---|---|---|
| 1.1 | Delete the version elements from all eight `org.springframework:*` dependencies — or better, delete the declarations entirely, since `spring-boot-starter-web` supplies `spring-core`, `-beans`, `-context`, `-web`, `-webmvc`, `-aop` and `-expression` transitively | Makes every later parent bump actually take effect | 🔴 High |
| 1.2 | Delete the unused `<spring.version>4.1.1.RELEASE</spring.version>` property | It contradicts the 4.1.6 pins actually in use | 🟡 Low |
| 1.3 | Delete the duplicate `spring-test` declaration (keep one) | Prevents the two copies drifting apart | 🟡 Low |
| 1.4 | Delete `commons-codec` (zero imports) and `spring-context-support` (no feature used) | Dead dependencies | 🟠 Medium / 🟡 Low |
| 1.5 | Move `groovy-all` to `test` scope; remove or re-scope `cglib-nodep` | Keeps test-only libraries out of the production artifact | 🟠 Medium |
| 1.6 | Delete the `http://repo.spring.io/milestone/` repository block | Plain-HTTP supply-chain exposure; every dependency is a Maven Central GA release. Do **not** substitute another custom repository | 🟠 Medium |
| 1.7 | Declare `spring-boot-maven-plugin` in `<build><plugins>` | Without it `mvn package` produces a plain JAR, not an executable fat JAR | 🟠 Medium |

**Checkpoint.** 19 declarations reduce to roughly 6. Re-run the Stage 0 specification — behavior must be byte-identical. No source file changes in this stage.

## Stage 2 — Runtime and Framework

🔴 **High severity — the core of the modernization.** This is the work that `AWS/java-version-upgrade` is recommended for.

| Order | Action | Prerequisite | Expected breakage |
|---|---|---|---|
| 2.1 | Upgrade `maven-compiler-plugin` 3.0 → current | Stage 1 | None |
| 2.2 | Java 8 → 11; replace `<source>`/`<target>` with `<maven.compiler.release>` | 2.1 | Minimal — no `sun.*`/`com.sun.*` usage exists, so no strong-encapsulation breakage |
| 2.3 | Boot 1.2.3 → 1.5.x | Stage 1 (pins removed) | Deprecation warnings; property renames |
| 2.4 | Boot 1.5 → 2.0 → 2.7 | 2.3 | Actuator moves under `/actuator` with endpoints secured by default; `spring-boot-starter-test` adopts JUnit 5 + AssertJ; relaxed-binding rework |
| 2.5 | **Spock 1.0 → 2.x** (forced at Boot 2.2+, when starter-test adopts the JUnit Platform) | 2.4 | The Spock 1.x engine stops being discovered; surefire configuration required |
| 2.6 | Groovy 2.4 → 4.x (coordinate moves `org.codehaus.groovy` → `org.apache.groovy`) | 2.5 | Spec syntax is largely unaffected |
| 2.7 | Java 11 → 17 | 2.2 | Hard floor for Boot 3.x |
| 2.8 | Boot 2.7 → 3.x | 2.7 | `javax.*` → `jakarta.*`; Spring Framework 6 `ResponseEntity`/`HttpHeaders` refinements; `HttpMethod` becomes a class rather than an enum |
| 2.9 | Java 17 → 21 (LTS) | 2.8 | Minimal |

### Source files requiring attention in this stage, in dependency order
| Order | File | Reason | Risk |
|---|---|---|---|
| a | `MainApplication.java` | Delete `new Integer("1234")` (deprecated for removal); verify `@SpringBootApplication` | 🟡 Low |
| b | `FilePointer.java` | Port signature review — Guava `MediaType` return type | 🟡 Low |
| c | `FileStorage.java` | No change expected | 🟡 Low |
| d | `ThrottlingInputStream.java` | No framework coupling; only Guava `RateLimiter`. Safest file in the project | 🟡 Low |
| e | `FileSystemPointer.java` | Guava `Files.hash`, `Hashing.sha512()`, `HashCode`, `MoreObjects` signatures | 🟠 Medium |
| f | `FileSystemStorage.java` | `@Component`/`@Profile` are stable; `new File(resource.getFile())` fragility unchanged | 🟡 Low |
| g | **`ExistingFile.java`** | **Highest risk.** `ResponseEntity.BodyBuilder` (`eTag`, `contentLength`, `lastModified`, `contentType`) all evolved in Spring 5/6; `method == HttpMethod.GET` comparison; Guava `Optional.transform`/`or` in `toMediaType` | 🔴 **High** |
| h | **`DownloadController.java`** | `@RequestHeader Optional<Date>` binding and RFC 1123 conversion. **This fails silently** — `cached()` simply returns `false` and the server serves the full file every time, with no exception and no log entry | 🔴 **High** |
| i | `Sha512ShallowEtagHeaderFilter.java` | Will **not compile** — the protected hook now takes an `InputStream`. **Delete it** (unregistered dead code) rather than migrating | 🟠 Medium |
| j | `DownloadControllerTest.groovy` | Spock 2.x migration; `MockMvc` builder API; static header imports | 🟠 Medium |
| k | `FileExamples.java`, `FileStorageStub.java` | Minimal — standard JDK and `@Profile` only | 🟡 Low |

**Critical verification.** After each sub-step, re-run the full Spock specification. Pay particular attention to the four `If-Modified-Since` / `If-None-Match` feature methods — they are the only guard against the silent failure mode in (h).

## Stage 3 — Library Dependencies

🟠 **Medium severity.** Must follow Stage 2 because current Guava requires Java 11+.

| Order | Action | Files touched | Verification focus |
|---|---|---|---|
| 3.1 | Upgrade Guava 18.0 → current | `pom.xml`, possibly `ExistingFile`, `FileSystemPointer`, `FilePointer` | `RateLimiter.create`, `Files.hash`, `Hashing.sha512()`, `HashCode`, `MoreObjects.toStringHelper`, `com.google.common.net.MediaType`, and the `Optional.transform`/`or` idiom in `toMediaType` |
| 3.2 | Remove `commons-io`; inline `private static final long ONE_MB = 1024L * 1024L;` in `ExistingFile` | `pom.xml`, `ExistingFile.java` | Throttle rate unchanged at 1 MB/s |
| 3.3 | Review whether Guava types should remain on the `FilePointer` port signature | `FilePointer.java` | Any change here is a **public API change** — weigh against downstream compatibility |

**Before finalizing any version in this stage, verify the exact target version against a vulnerability database.** Never downgrade below the current declared version.

## Stage 4 — Configuration and Security

🟠 **Medium severity.** Independent of Stage 3; can proceed in parallel.

| Order | Action | Files touched |
|---|---|---|
| 4.1 | Add `src/main/resources/application.yml` | new file |
| 4.2 | Externalize the throttle rate (`download.throttle.bytes-per-second`), defaulting to 1 MB/s so behavior is unchanged | `application.yml`, `ExistingFile.java` |
| 4.3 | Externalize log levels; set production defaults to `INFO` (currently root `DEBUG`, `com.nurkiewicz` at `ALL`) | `application.yml`, `logback.xml` |
| 4.4 | Explicitly configure actuator exposure via `management.endpoints.web.exposure.include`; add Spring Security if any endpoint beyond `/health` is exposed. Alternatively remove the actuator dependency — no application code uses it | `pom.xml`, `application.yml` |
| 4.5 | Decide and **document** the `/download/**` authorization model. A UUID is an unguessable identifier, not an authorization control — it cannot be revoked and does not expire | `application.yml`, documentation |
| 4.6 | Add a `@ControllerAdvice` mapping storage I/O failures to a generic `500` without exception detail | new file |
| 4.7 | Add security response headers (`X-Content-Type-Options: nosniff`, `Content-Disposition`, appropriate `Cache-Control`) | `ExistingFile.java` or a filter |

**Preserve every existing control.** Per the modification guardrails, do not weaken UUID type validation, the GET/HEAD-only restriction, the read-only API, the SHA-512 ETag algorithm, or the throttle. See the controls inventory in [../analysis/security-patterns.md](../analysis/security-patterns.md#security-controls-inventory).

## Stage 5 — Code Quality

🟡 **Low severity.** Deliberately last — none of these findings compounds with time, and none blocks anything.

| Order | Action | File |
|---|---|---|
| 5.1 | Delete `Sha512ShallowEtagHeaderFilter` (if not already removed in Stage 2) | `src/test/java/org/springframework/web/filter/` |
| 5.2 | Make the SHA-512 ETag lazy, or cache it keyed by (path, size, lastModified). **Keep SHA-512** — do not weaken the hash for performance | `FileSystemPointer.java` |
| 5.3 | Capture `size`, `lastModified` and `tag` consistently so the ETag and `Content-Length` cannot describe different file versions | `FileSystemPointer.java` |
| 5.4 | Replace `IllegalArgumentException` rewrapping with `UncheckedIOException` or a domain exception | `FileSystemPointer.java`, `ExistingFile.java` |
| 5.5 | Replace `new File(resource.getFile())` with `Paths.get(resource.toURI())` or direct resource streaming | `FileSystemStorage.java`, `FileExamples.java` |
| 5.6 | Either validate `{filename}` against `getOriginalName()` (redirecting on mismatch) or document it as decorative. **Note:** the existing spec requests `/download/{uuid}/file.txt` against a fixture named `download.txt`, so adding strict validation requires updating the specification | `DownloadController.java`, spec |
| 5.7 | Document the `getEtag()` pre-quoting obligation in `FilePointer` Javadoc | `FilePointer.java` |
| 5.8 | Consider `@GetMapping` in place of `@RequestMapping(method = {GET, HEAD})` — optional modernization | `DownloadController.java` |

**Do not rename `modifiedAfter`.** Although its name reads as the inverse of its semantics, it is a **public interface method**; renaming it breaks API compatibility for any downstream consumer. Document the semantics instead.

## Stage 6 — Operations

🟡 **Low severity**, but 6.3 is what prevents the repaid dependency debt from returning — git history shows Dependabot upgrades were previously reverted.

| Order | Action |
|---|---|
| 6.1 | Add `.gitignore` (`target/`, `.DS_Store`, IDE directories) and delete the committed `/repo/.DS_Store` |
| 6.2 | Expand `README.md` to document the two endpoints, how to run the application, and the stub-storage caveat |
| 6.3 | Add a CI pipeline running build + the Spock specification + dependency CVE scanning |
| 6.4 | Add JaCoCo coverage reporting |
| 6.5 | Add a Dockerfile if container deployment is intended |
| 6.6 | Add the missing test coverage identified in [test-specifications.md](test-specifications.md): the `301` redirect path, `ThrottlingInputStream` unit tests, `400`/`405` paths |

## Optional — Storage Implementation

**Feature work, not modernization.** Listed separately so it is not mistaken for debt an upgrade will resolve.

| Order | Action | Note |
|---|---|---|
| S1 | Implement a real `FileStorage` + `FilePointer` adapter pair | The interfaces are 1 and 8 methods; this is purely additive. No change to `DownloadController`, `ExistingFile`, or `ThrottlingInputStream` |
| S2 | Add a metadata store for the UUID → file mapping (none exists today) | Natural AWS shape: object store for bytes, DynamoDB for metadata |
| S3 | Add an upload/registration path that mints cryptographically random UUIDs | A predictable UUID scheme would eliminate the only access control the system has |
| S4 | **Mandatory:** resolve paths/keys via metadata lookup, canonicalize, and assert containment within the storage root. Never incorporate `{filename}` into a path or object key | Path traversal becomes a first-class risk the moment real storage exists |
| S5 | Use stored metadata or the object store's own ETag rather than hashing full contents per request | Hashing large remote objects per request will not scale |
| S6 | Replace the `@Profile("!test")` / `@Profile("test")` bean-selection trick with conventional test configuration | Removes production config expressed in terms of a test concept |
| S7 | Consider `Range` / partial-content support so throttled downloads can be resumed | Currently unsupported — a notable gap for a deliberately throttled download server |

## Per-File Migration Order

Derived strictly from the dependency graph: zero-fan-out leaves first, highest-risk framework-coupled files last.

| # | File | Fan-out | Framework coupling | Risk | Stage |
|---|---|---|---|---|---|
| 1 | `pom.xml` | — | — | 🔴 High (gates everything) | 1 |
| 2 | `FileStorage.java` | 1 | None | 🟡 Low | 2 |
| 3 | `FilePointer.java` | 0 | Guava only (port signature) | 🟡 Low | 2, 3 |
| 4 | `ThrottlingInputStream.java` | 0 | Guava only | 🟡 Low | 2, 3 |
| 5 | `MainApplication.java` | 0 | Boot annotation | 🟡 Low | 2 |
| 6 | `FileSystemPointer.java` | 1 | Guava hashing, JDK I/O | 🟠 Medium | 2, 3, 5 |
| 7 | `FileSystemStorage.java` | 2 | Spring Context annotations | 🟡 Low | 2, 5 |
| 8 | `DownloadController.java` | 3 | **Spring MVC binding** | 🔴 **High — silent failure mode** | 2 |
| 9 | `ExistingFile.java` | 3 | **Spring Web response API** | 🔴 **High** | 2, 3, 4 |
| 10 | `Sha512ShallowEtagHeaderFilter.java` | 1 | Spring internal (protected hook) | 🟠 Medium — **delete** | 2/5 |
| 11 | `FileExamples.java` | 2 | None | 🟡 Low | 2, 5 |
| 12 | `FileStorageStub.java` | 2 | Spring Context annotations | 🟡 Low | 2 |
| 13 | `DownloadControllerTest.groovy` | 3 | Spock + Spring Test | 🟠 Medium | 0, 2 |
| 14 | `logback.xml` | — | Logback | 🟡 Low | 4 |

## Checkpoint Matrix

Verify at each boundary. All verification is behavioral — run the specification; never assume.

| After stage | Verify | Pass condition |
|---|---|---|
| 0 | Spock specification compiles and runs | All 12 feature methods pass on the **unmodified** code |
| 1 | Dependency set normalized | Specification still passes; no Spring version pins remain; no plain-HTTP repository; `spring-boot-maven-plugin` declared |
| 2 (each sub-step) | Framework behavior preserved | All 12 features pass; **specifically** the four conditional-request features, which guard the silent `If-Modified-Since` failure |
| 2 (complete) | Platform current | Builds on a current LTS JDK with a Boot 3.x parent; no `javax.*` residue; specification passes |
| 3 | Library behavior preserved | Throttle still 1 MB/s; ETag still SHA-512; `Content-Type` still correct; specification passes |
| 4 | Security not weakened | Every control in the inventory still present; actuator exposure explicit; specification passes |
| 5 | Code quality improvements behavior-neutral | Specification passes; ETag values unchanged for identical content |
| 6 | Automation in place | CI builds, runs the specification, and scans dependencies for CVEs |

Detailed criteria: [validation-criteria.md](validation-criteria.md).

## Rollback Points

Each stage is an independently revertible commit boundary.

| Rollback point | Safe to revert independently? | Note |
|---|---|---|
| After Stage 0 | Yes | Build-only change; no source semantics altered |
| After Stage 1 | Yes | POM-only change (plus the `ONE_MB` inline if done here) |
| After each Stage 2 sub-step | Yes | Keep each JDK bump and each Boot bump as a separate commit — this is what makes a regression bisectable |
| After Stage 3 | Yes | Guava upgrade is isolable |
| After Stage 4 | Yes | Additive configuration |
| After Stage 5 | Yes | Independent small fixes |
| After Stage 6 | Yes | Tooling only |

**Highest-risk boundary:** Stage 2.4 (Boot 1.5 → 2.x), which simultaneously changes the actuator model, the test framework, and property binding. Split it into the smallest commits the build tolerates.
