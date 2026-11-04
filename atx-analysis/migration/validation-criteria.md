# Validation Criteria

- [Back to README](../README.md)
- Siblings: [component-order.md](component-order.md) · [test-specifications.md](test-specifications.md)
- See also: [../technical-debt/remediation-plan.md](../technical-debt/remediation-plan.md)

> These are the criteria by which a **future migration** of this codebase should be judged. They are not the validation criteria of this documentation transformation, which performs static analysis only and neither builds nor runs the code.

## Table of Contents
1. [How to Use This Document](#how-to-use-this-document)
2. [Gate 0 — Verification Capability](#gate-0--verification-capability)
3. [Gate 1 — Build Integrity](#gate-1--build-integrity)
4. [Gate 2 — Behavioral Equivalence](#gate-2--behavioral-equivalence)
5. [Gate 3 — Platform Currency](#gate-3--platform-currency)
6. [Gate 4 — Dependency Hygiene](#gate-4--dependency-hygiene)
7. [Gate 5 — Security Non-Regression](#gate-5--security-non-regression)
8. [Gate 6 — API Compatibility](#gate-6--api-compatibility)
9. [Gate 7 — Code Quality](#gate-7--code-quality)
10. [Gate 8 — Operational Readiness](#gate-8--operational-readiness)
11. [Guardrail Compliance Checklist](#guardrail-compliance-checklist)
12. [Definition of Done](#definition-of-done)
13. [Explicit Non-Criteria](#explicit-non-criteria)

## How to Use This Document

Each gate corresponds to a stage in [component-order.md](component-order.md). A gate is **passed** only when every criterion marked *required* is satisfied. Criteria are binary — satisfied or not — with no partial credit.

| Gate | Corresponds to stage | Blocking? |
|---|---|---|
| 0 — Verification capability | Stage 0 | **Blocks everything** |
| 1 — Build integrity | Stages 1, 2 | Blocks Gate 2 |
| 2 — Behavioral equivalence | After every stage | **Blocks progress at each checkpoint** |
| 3 — Platform currency | Stage 2 | Primary objective |
| 4 — Dependency hygiene | Stages 1, 3 | — |
| 5 — Security non-regression | Stage 4 | **Blocks release** |
| 6 — API compatibility | All stages | **Blocks release** |
| 7 — Code quality | Stage 5 | — |
| 8 — Operational readiness | Stage 6 | — |

## Gate 0 — Verification Capability

Nothing can be validated until the test suite runs. This gate exists because the suite currently does not.

| # | Criterion | Required | Verification |
|---|---|---|---|
| 0.1 | `src/test/groovy` is a registered test-source root | ✅ | A default build compiles `DownloadControllerSpec` |
| 0.2 | All 12 existing feature methods execute | ✅ | Test report shows exactly 12 (or more) tests run — **not zero** |
| 0.3 | All 12 pass against the **unmodified** Java 8 / Boot 1.2.3 code | ✅ | Green run before any migration change |
| 0.4 | The baseline HTTP contract is recorded | ✅ | Observed status codes and header values captured as the migration oracle |
| 0.5 | The build fails if the test count drops to zero | ✅ | CI asserts a non-zero test count, not merely build success |

**0.5 is not optional.** The project's current failure mode is precisely a green build with zero tests executed, and the same failure recurs at Boot 2.2+ when the Spock 1.x engine stops being discovered.

## Gate 1 — Build Integrity

| # | Criterion | Required | Verification |
|---|---|---|---|
| 1.1 | The project compiles with no errors | ✅ | Clean build |
| 1.2 | No new compiler warnings beyond the pre-existing baseline | — | Compare warning output |
| 1.3 | No `javax.*` imports remain in application source after reaching Boot 3.x | ✅ | Source grep. Note the baseline already has **zero** `javax.*` imports, so this must remain zero |
| 1.4 | `spring-boot-maven-plugin` is declared, producing an executable JAR | ✅ | `java -jar target/*.jar` starts the application |
| 1.5 | The Java language level is expressed via `maven.compiler.release` | ✅ | `pom.xml` inspection |
| 1.6 | No deprecated-for-removal API remains in use | ✅ | `new Integer(String)` deleted from `MainApplication` |
| 1.7 | The build runs offline against Maven Central only | ✅ | No custom repository declared |

## Gate 2 — Behavioral Equivalence

**The central criterion.** The externally observable HTTP contract must be byte-identical before and after migration. Evaluate this gate at **every** checkpoint, not only at the end.

| # | Criterion | Required | Reference |
|---|---|---|---|
| 2.1 | R1 — `GET` long URL ⇒ `200` + exact body bytes | ✅ | [test-specifications.md](test-specifications.md#migration-regression-suite) |
| 2.2 | R2 — unknown UUID ⇒ `404` + empty body | ✅ | " |
| 2.3 | R3 — matching `If-None-Match` ⇒ `304` + ETag echoed | ✅ | " |
| 2.4 | R4 — non-matching `If-None-Match` ⇒ `200` | ✅ | " |
| 2.5 | R5 — `If-Modified-Since` == lastModified ⇒ `304` | ✅ | " |
| 2.6 | R6 — `If-Modified-Since` > lastModified ⇒ `304` | ✅ | " |
| 2.7 | R7 — `If-Modified-Since` < lastModified ⇒ `200` | ✅ | " |
| 2.8 | R8 — `HEAD` ⇒ `200` + empty body + **true** `Content-Length` | ✅ | " |
| 2.9 | R9 — `HEAD` + matching ETag ⇒ `304` | ✅ | " |
| 2.10 | R10 — `Content-Type: text/plain` for the text fixture | ✅ | " |
| 2.11 | R11 — short URL ⇒ `301` + `Location: /download/{uuid}/{originalName}` | ✅ | Requires the new spec from Priority 1 |
| 2.12 | R12 — the ETag for identical content is **unchanged** from the pre-migration value | ✅ | Assert the literal SHA-512; guards against a silent hash change invalidating every client cache |
| 2.13 | R13 — throttle rate remains 1 MB/s | ✅ | Requires the new `ThrottlingInputStream` specs |
| 2.14 | Only `GET` and `HEAD` are accepted; other verbs ⇒ `405` | ✅ | — |
| 2.15 | A malformed UUID ⇒ `400` | ✅ | — |
| 2.16 | `Content-Type` is **omitted** (not defaulted) when undeterminable | ✅ | Preserves BR15 |
| 2.17 | `304`, `200`-GET and `200`-HEAD carry identical headers | ✅ | Preserves BR18 |
| 2.18 | A `301` carries `Location` and no caching headers | — | Preserves BR19; may legitimately be improved, but changing it is a behavior change that must be deliberate |

### Business rule preservation
All 20 business rules in [../diagrams/behavioral.md](../diagrams/behavioral.md#business-rules) must still hold. Any intentional deviation must be documented with a rationale; an undocumented deviation is a failure.

## Gate 3 — Platform Currency

The primary objective — resolving the four High-severity findings.

| # | Criterion | Required | Verification |
|---|---|---|---|
| 3.1 | The Java language level targets a supported LTS release | ✅ | `pom.xml`; the baseline is Java 8 |
| 3.2 | The JDK vendor is a long-term-supported, no-cost distribution | — | Amazon Corretto recommended |
| 3.3 | Spring Boot is on a supported release line | ✅ | Parent POM version; the baseline is 1.2.3.RELEASE |
| 3.4 | **No explicit `org.springframework:*` version pin remains** | ✅ | `pom.xml` grep — this is the finding that makes the parent bump effective |
| 3.5 | The unused `<spring.version>` property is removed | ✅ | `pom.xml` |
| 3.6 | Spring Framework versions are managed solely by the Boot BOM | ✅ | No version element on any Spring dependency |
| 3.7 | `maven-compiler-plugin` is on a current release | ✅ | `pom.xml`; the baseline is 3.0 |
| 3.8 | Spock and Groovy are on versions compatible with the target Boot line | ✅ | Tests actually execute — see 0.5 |
| 3.9 | No EOL transitive remains that the parent could have upgraded | ✅ | `jackson-databind`, `snakeyaml`, `hibernate-validator` move with the parent |

## Gate 4 — Dependency Hygiene

| # | Criterion | Required | Baseline |
|---|---|---|---|
| 4.1 | No dependency is declared at `compile` scope that is used only in tests | ✅ | `groovy-all` and `cglib-nodep` currently violate this |
| 4.2 | No declared dependency is entirely unused | ✅ | `commons-codec` currently violates this |
| 4.3 | No duplicate dependency declaration exists | ✅ | `spring-test` is currently declared twice |
| 4.4 | No plain-HTTP repository is declared | ✅ | `http://repo.spring.io/milestone/` must be removed |
| 4.5 | No new custom or third-party repository has been introduced | ✅ | Only standard public registries or repositories already present |
| 4.6 | **No dependency version has been downgraded below its original** | ✅ | Guardrail requirement |
| 4.7 | Every added or changed dependency version has been checked for known CVEs | ✅ | Verify the exact resolved version against a vulnerability database |
| 4.8 | Any dependency committed with a known CVE is explicitly disclosed | ✅ | Name the CVE, severity, and the suggested safer version |
| 4.9 | The declared dependency count is materially reduced | — | 19 declarations → roughly 6 is achievable with no behavior change |

## Gate 5 — Security Non-Regression

**Blocks release.** Per the modification guardrails, no existing security control may be removed or weakened.

| # | Criterion | Required | Control |
|---|---|---|---|
| 5.1 | UUID path variables remain typed as `UUID`, never `String` | ✅ | The only input validation in the system |
| 5.2 | Handlers remain restricted to `GET` and `HEAD` | ✅ | Read-only contract |
| 5.3 | No write, upload, or delete endpoint has been introduced | ✅ | — |
| 5.4 | The ETag hash algorithm is **not weakened** (SHA-512 retained) | ✅ | Must not be downgraded to MD5/SHA-1 for performance |
| 5.5 | Bandwidth throttling remains in place | ✅ | `ThrottlingInputStream` retained |
| 5.6 | `404` still does not distinguish "absent" from "forbidden" | ✅ | — |
| 5.7 | No user input participates in filesystem path or object-key construction | ✅ | Including `{filename}`, which must never reach storage |
| 5.8 | Actuator exposure is **explicitly configured**, not inherited | ✅ | `management.endpoints.web.exposure.include` set |
| 5.9 | Sensitive actuator endpoints are not anonymously reachable | ✅ | `/env`, `/configprops`, `/beans`, `/trace` |
| 5.10 | No permissive CORS configuration has been added | ✅ | — |
| 5.11 | No hardcoded secret, key, token, or credential has been introduced | ✅ | Baseline is clean; it must stay clean |
| 5.12 | No `eval`, `Runtime.exec`, `ProcessBuilder`, or untrusted deserialization has been introduced | ✅ | Baseline has none |
| 5.13 | Error responses do not disclose internal paths or exception detail | ✅ | Requires the Stage 4 `@ControllerAdvice` |
| 5.14 | Production log levels do not emit absolute paths and hashes at `DEBUG`/`ALL` | ✅ | Current `logback.xml` does |
| 5.15 | The access model for `/download/**` is documented | ✅ | Explicitly state whether content is public |

The authoritative control list is the inventory in [../analysis/security-patterns.md](../analysis/security-patterns.md#security-controls-inventory).

## Gate 6 — API Compatibility

**Blocks release.** Guardrail requirement — public names and signatures must not change.

| # | Criterion | Required | Detail |
|---|---|---|---|
| 6.1 | All public class names are unchanged | ✅ | `DownloadController`, `ExistingFile`, `FileStorage`, `FilePointer`, `FileSystemStorage`, `FileSystemPointer`, `ThrottlingInputStream`, `FileExamples`, `FileStorageStub`, `MainApplication` |
| 6.2 | All public method names are unchanged | ✅ | Including `modifiedAfter`, despite its misleading name — renaming it is a breaking API change |
| 6.3 | Every source file retains its primary type declaration | ✅ | No file may lose its main class/interface |
| 6.4 | Public method signatures are preserved, or changes are deliberate and documented | ✅ | Framework-forced changes (e.g. `Optional<Date>` → `Optional<Instant>`) must be justified |
| 6.5 | `FileStorage` remains a one-method interface | ✅ | `Optional<FilePointer> findFile(UUID)` |
| 6.6 | `FilePointer` retains all eight methods | ✅ | — |
| 6.7 | No method-signature duplicates were introduced within a scope | ✅ | — |
| 6.8 | HTTP URL paths are unchanged | ✅ | `/download/{uuid}` and `/download/{uuid}/{filename}` |
| 6.9 | Package names are unchanged | ✅ | `com.nurkiewicz.download` |
| 6.10 | Public visibility is not narrowed on existing types | ✅ | `FileSystemPointer` and `ExistingFile` are `public` today |

**Permitted exception.** Deleting `Sha512ShallowEtagHeaderFilter` is allowed even though it is `public`: it resides in the `org.springframework.web.filter` namespace (not a project package), is never registered, and targets a Spring signature that no longer exists. It is unreachable dead code rather than project API.

## Gate 7 — Code Quality

| # | Criterion | Required | Baseline |
|---|---|---|---|
| 7.1 | No deprecated-for-removal API in use | ✅ | `new Integer("1234")` removed |
| 7.2 | No unreachable or unregistered dead code | ✅ | `Sha512ShallowEtagHeaderFilter` deleted |
| 7.3 | Cyclomatic complexity has not increased | ✅ | Baseline maximum is 3 |
| 7.4 | Nesting depth has not increased | ✅ | Baseline maximum is 2 |
| 7.5 | Dependency cycles remain at zero | ✅ | Baseline is 0 |
| 7.6 | Field immutability is preserved | ✅ | Baseline is 11 of 11 `final` |
| 7.7 | Dependency inversion is preserved | ✅ | `DownloadController`/`ExistingFile` must not import `FileSystem*` types |
| 7.8 | I/O failures are not reported as `IllegalArgumentException` | — | Use `UncheckedIOException` or a domain exception |
| 7.9 | The throttle rate is externally configurable | — | Default must remain 1 MB/s |
| 7.10 | The spec filename matches its class name | — | `DownloadControllerSpec.groovy` |
| 7.11 | No license header or copyright notice was modified | ✅ | Guardrail requirement |
| 7.12 | The `FileSystemStorage` stub Javadoc is retained | ✅ | 5+ line comment blocks are preserved; this one is load-bearing documentation |

## Gate 8 — Operational Readiness

| # | Criterion | Required |
|---|---|---|
| 8.1 | An externalized configuration file exists (`application.yml` or equivalent) | ✅ |
| 8.2 | Port, log levels, actuator exposure and throttle rate are configurable | ✅ |
| 8.3 | Production log levels default to `INFO` or above | ✅ |
| 8.4 | A CI pipeline builds the project and runs the test suite | ✅ |
| 8.5 | CI asserts a non-zero test count | ✅ |
| 8.6 | CI runs dependency vulnerability scanning | ✅ |
| 8.7 | `.gitignore` exists and `.DS_Store` has been removed | — |
| 8.8 | `README.md` documents the endpoints, how to run the app, and the stub-storage caveat | — |
| 8.9 | Code coverage is measured | — |
| 8.10 | A container image definition exists (if containerized deployment is intended) | — |

## Guardrail Compliance Checklist

Verify before declaring any stage complete.

| Area | Check | Status to confirm |
|---|---|---|
| **Security** | No hardcoded secrets introduced | — |
| | No existing security control removed or weakened | — |
| | No dependency with known vulnerabilities introduced | — |
| | No `eval`/`exec` on untrusted input introduced | — |
| **Test integrity** | No test file deleted | `DownloadControllerTest.groovy`, `FileStorageStub.java`, `FileExamples.java`, `download.txt` all retained |
| | No test method removed or disabled | All 12 feature methods present and enabled |
| | Assertions updated for compatibility only, never weakened | — |
| **API compatibility** | Public class/module/function/variable names unchanged | — |
| | Every file retains its primary type declaration | — |
| | No duplicate method signatures in a scope | — |
| **Build & dependencies** | Only standard public repositories or ones already present | Maven Central only |
| | No dependency version downgraded below its original | — |
| **Filesystem safety** | Files deleted individually by name, never by recursive directory removal | — |
| **Legal & documentation** | Copyright notices and license headers unchanged | `LICENSE`, `CODE_OF_CONDUCT.md` untouched |
| | Comment blocks of 5+ lines preserved | `FileSystemStorage` Javadoc retained |

## Definition of Done

A migration of this codebase is complete when **all** of the following hold:

1. **Gate 0 passed** — the Spock specification compiles, runs, and the build fails if the test count reaches zero.
2. **Gate 2 passed at every checkpoint** — all 13 regression assertions pass after every stage, with the ETag value for identical content provably unchanged.
3. **Gate 3 passed** — the project builds on a supported LTS JDK with a supported Spring Boot line, and **no explicit Spring Framework version pin remains**.
4. **Gate 4 passed** — no mis-scoped or unused dependency, no duplicate declaration, no plain-HTTP repository, no downgraded version, and every version change CVE-checked.
5. **Gate 5 passed** — every security control in the inventory is intact, actuator exposure is explicit, and the access model is documented.
6. **Gate 6 passed** — no public name or signature changed except where framework-forced and documented.
7. **Gate 8.1–8.6 passed** — configuration externalized and CI enforcing build, tests, and CVE scanning.
8. **The guardrail checklist is fully confirmed.**

Gates 7 and the remainder of Gate 8 are quality improvements and do not block completion.

## Explicit Non-Criteria

Recorded so effort is not misdirected toward non-problems or scope creep.

| Not a criterion | Rationale |
|---|---|
| Reducing cyclomatic complexity | Baseline maximum is 3; there is nothing to reduce. Proposals to "simplify" this code should be declined |
| Restructuring the architecture | 0 cycles, correct dependency inversion, ideal stability distribution. No redesign is warranted |
| Introducing sub-packages | Optional cosmetic improvement; it changes no behavior and adds migration risk |
| Implementing real `FileStorage` | Feature work, not modernization. Must be scoped explicitly and separately |
| Adding `Range`/partial-content support | A genuine functional gap, but a new feature rather than a migration criterion |
| Converting to reactive/async handling | Would address the thread-per-download constraint, but is a redesign, not an upgrade |
| Renaming `modifiedAfter` | Blocked by Gate 6.2 — it is public API. Document the semantics instead |
| Removing the `{filename}` path segment | Would change the URL contract and break the existing specification |
| 100% code coverage | Coverage should increase, but a numeric target is not a migration criterion |
| Any time or effort estimate | This documentation states no timelines; priority and complexity are expressed qualitatively only |
