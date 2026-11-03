# Remediation Plan

- [Back to README](../README.md) · [Root report](../technical-debt-report.md)
- Siblings: [summary.md](summary.md) · [outdated-components.md](outdated-components.md) · [maintenance-burden.md](maintenance-burden.md)
- See also: [../migration/component-order.md](../migration/component-order.md) · [../migration/validation-criteria.md](../migration/validation-criteria.md)

## Table of Contents
1. [Recommended AWS Transformation](#recommended-aws-transformation)
2. [Prioritization Rules](#prioritization-rules)
3. [Phase 0 — Restore the Safety Net](#phase-0--restore-the-safety-net)
4. [Phase 1 — High Severity: Runtime and Framework](#phase-1--high-severity-runtime-and-framework)
5. [Phase 2 — Medium Severity: Dependencies and Security](#phase-2--medium-severity-dependencies-and-security)
6. [Phase 3 — Low Severity: Code Quality and Hygiene](#phase-3--low-severity-code-quality-and-hygiene)
7. [Optional — Functional Completion](#optional--functional-completion)
8. [Action Item Register](#action-item-register)

## Recommended AWS Transformation

**`AWS/java-version-upgrade`** — see the full justification at the top of [../technical-debt-report.md](../technical-debt-report.md). It addresses the only High-severity finding class (EOL Java 8 plus the Spring Boot 1.x/Spring 4.x ecosystem) and performs the `javax`→`jakarta` namespace migration that must land before anything else.

`AWS/spring-boot-version-upgrade` is deliberately **not** recommended yet: its scope starts at Spring Boot 3.x, and this project is on 1.2.3. It becomes applicable only after the Java version upgrade has brought the project onto Spring Boot 3.x.

Transformations assessed and found inapplicable:

| Transformation | Why not applicable |
|---|---|
| `AWS/spring-boot-version-upgrade` | Targets Spring Boot 3.x → 4.x; this project is on 1.2.3 |
| `AWS/java-aws-sdk-v1-to-v2` | No `com.amazonaws` dependency anywhere |
| `AWS/early-access-log4j-to-slf4j-migration` | No Log4j; already SLF4J + Logback |
| `AWS/JBoss-to-Spring-Boot`, `AWS/java-ee-to-quarkus` | No application server, no Java EE descriptors; already Spring Boot |
| `AWS/oracle-java-to-corretto` | No Dockerfile, no Oracle JDK pin, no `sun.*`/`com.sun.*` API usage |
| `AWS/java-x86-to-graviton`, `AWS/graviton-readiness-assessment` | No native libraries, no architecture-specific code; pure Java is already Arm64-portable |
| `AWS/java-performance-optimization` | No JFR recordings supplied |
| `AWS/security-issue-fixer` | Requires third-party scanner findings as input; none provided |
| `AWS/websphere-jaxrs-to-bedrock-agentcore` | Uses Spring MVC annotations, not JAX-RS |

**Do not apply any transformation as part of this analysis — these are recommendations only.**

## Prioritization Rules

Fixed order, applied throughout this plan:

1. 🔴 **High** — EOL/deprecated/outdated runtimes and frameworks
2. 🟠 **Medium** — outdated dependencies
3. 🟡 **Low** — code quality and architectural issues

Phase 0 is sequenced before Phase 1 not because of severity but because the High-severity work is unverifiable without it.

## Phase 0 — Restore the Safety Net

**Rationale.** The Spock specification is the only mechanism that can prove a framework upgrade preserved behavior, and it currently does not run. Fix that first.

| Action | Detail | Severity addressed |
|---|---|---|
| P0.1 | Register `src/test/groovy` as a test-source root by adding a Groovy compilation plugin (`gmavenplus-plugin` or `groovy-eclipse-compiler` wired into `maven-compiler-plugin`) | 🟠 Medium |
| P0.2 | Confirm all 12 feature methods in `DownloadControllerSpec` execute and pass against the **current** Java 8 / Boot 1.2.3 code, establishing the behavioral baseline | 🟠 Medium |
| P0.3 | Rename `DownloadControllerTest.groovy` → `DownloadControllerSpec.groovy` so the filename matches the class and surefire/Spock naming conventions align | 🟡 Low |
| P0.4 | Record the baseline HTTP contract (status codes, headers) from the passing run as the migration oracle | — |

**Exit condition.** A default Maven build compiles and runs the Spock specification, and all feature methods pass.

## Phase 1 — High Severity: Runtime and Framework

**Rationale.** Four High-severity findings, all unpatchable-platform risks, all interdependent. Do them as one staged migration, re-running the Phase 0 specification after every stage.

### P1.1 — Remove the explicit Spring Framework pins (do this first)
Delete the version elements from all eight `org.springframework:*` dependencies and delete the unused `<spring.version>` property. Let the `spring-boot-starter-parent` BOM manage Spring Framework versions.

**Why first:** while those pins exist, bumping the Boot parent has no effect on the Spring Framework version, producing an untested and potentially runtime-broken Boot/Spring combination. This single edit is what makes every subsequent parent bump actually take effect.

Most of these dependencies are also redundant — `spring-boot-starter-web` transitively supplies `spring-core`, `-beans`, `-context`, `-web`, `-webmvc`, `-aop` and `-expression`. Prefer deleting them outright over merely unpinning them.

### P1.2 — Stage the Spring Boot upgrade
| Stage | Target | Principal breaking changes to handle |
|---|---|---|
| a | Boot 1.5.x | Last 1.x release; surfaces deprecation warnings and property renames while still on Java 8 |
| b | Boot 2.0 → 2.7 | Actuator moves under `/actuator` with endpoints secured by default; `spring-boot-starter-test` adopts JUnit 5 + AssertJ (Spock 1.x engine stops being discovered — move to Spock 2.x here); relaxed binding rework; `ShallowEtagHeaderFilter.generateETagHeaderValue` signature change |
| c | Boot 3.x | Requires Java 17; `javax.*` → `jakarta.*` across the board; Spring Framework 6 `ResponseEntity`/`HttpHeaders` API refinements |

### P1.3 — Raise the Java language level
Progress 8 → 11 → 17 → 21 (LTS), replacing the `maven-compiler-plugin` `<source>`/`<target>` pair with `<maven.compiler.release>` once on Java 9+. Java 17 is the hard floor for Boot 3.x. Use Amazon Corretto for a no-cost long-term-supported build.

### P1.4 — Modernize the Spring MVC surface
With the framework current, replace `@RequestMapping(method = {GET, HEAD})` with `@GetMapping`/`@RequestMapping` combinations as appropriate, and migrate the `@RequestHeader Optional<Date>` parameters to `Instant`/`ZonedDateTime` so the HTTP boundary matches the `java.time` types already used in `FilePointer`.

**Exit condition.** Project builds on a current LTS JDK with a Spring Boot 3.x parent, no explicit Spring Framework pins remain, and the full Spock specification passes with unchanged observable HTTP behavior.

## Phase 2 — Medium Severity: Dependencies and Security

| Action | Detail |
|---|---|
| P2.1 | Upgrade **Guava** from 18.0 to a current release. Verify `RateLimiter.create`, `Hashing.sha512()`, `HashCode`, `MoreObjects.toStringHelper` and `com.google.common.net.MediaType` signatures — all are used by `ExistingFile`, `FileSystemPointer` and `ThrottlingInputStream`. Must follow the JDK upgrade, since current Guava requires Java 11+. |
| P2.2 | **Remove `commons-io`** entirely. It is used for the single constant `FileUtils.ONE_MB`; replace with a local `private static final long ONE_MB = 1024L * 1024L;` in `ExistingFile`. |
| P2.3 | **Remove `commons-codec`**. No source usage was found. |
| P2.4 | **Remove `cglib-nodep`** or move it to `test` scope. Spock 2.x uses ByteBuddy and does not need it declared. |
| P2.5 | Move `groovy-all` from `compile` to `test` scope, and update the coordinate to `org.apache.groovy` if moving to Groovy 4+. This keeps a large test-only library out of the production artifact. |
| P2.6 | **Delete the plain-HTTP `repo.spring.io/milestone` repository block.** Every declared dependency is a GA release available from Maven Central. This removes a man-in-the-middle supply-chain exposure and a stale repository. Do not substitute another custom repository. |
| P2.7 | Secure the actuator. Boot 2.x+ restricts sensitive endpoints by default, but make the intent explicit via `management.endpoints.web.exposure.include` and add Spring Security if any non-`/health` endpoint is to be exposed. |
| P2.8 | Decide and document the authorization model for `/download/**`. The endpoints are currently fully public; a UUID is an unguessable identifier but not an authorization control. If files are non-public, add authentication. |
| P2.9 | Remove the duplicate `spring-test` declaration (keep one). |
| P2.10 | Introduce automated dependency CVE scanning in CI so the post-upgrade dependency set does not drift back into this state. Verify every new or changed version against a vulnerability database before pinning it. |

**Exit condition.** No `compile`-scoped test-only libraries, no unused dependencies, no plain-HTTP repositories, actuator exposure explicitly configured, and the Spock specification still passing.

## Phase 3 — Low Severity: Code Quality and Hygiene

| Action | Detail |
|---|---|
| P3.1 | Delete `Integer temp = new Integer("1234");` from `MainApplication` — deprecated constructor and an unread local in one line. |
| P3.2 | Delete `src/test/java/org/springframework/web/filter/Sha512ShallowEtagHeaderFilter.java`. It is never registered, sits in a third-party package, and targets a changed Spring signature. Recreate against the current API only if the filter is genuinely wanted. |
| P3.3 | Externalize the throttle rate from `ExistingFile` into configuration (e.g. `download.throttle.bytes-per-second`), with the current 1 MB/s as the default so behavior is unchanged. |
| P3.4 | Add an `application.yml` so port, logging levels, actuator exposure and the throttle rate are configurable per environment instead of being framework defaults and source literals. |
| P3.5 | In `FileSystemPointer`, consider computing the SHA-512 ETag lazily or caching it by (path, size, lastModified) rather than hashing the entire file in the constructor. Today every request re-reads the whole file before a byte is served. Keep SHA-512 — do not weaken the hash. |
| P3.6 | In `FileSystemPointer`, stop rewrapping `IOException` as `IllegalArgumentException`; use a domain-specific exception (or `UncheckedIOException`) so callers can distinguish a bad argument from an I/O failure. |
| P3.7 | Add a `.gitignore` (at minimum `target/`, `.DS_Store`, IDE directories) and delete the committed `/repo/.DS_Store`. |
| P3.8 | Expand `README.md` to document the endpoints, how to run the application, and the stub-storage caveat. |
| P3.9 | Upgrade `maven-compiler-plugin` from 3.0 to a current release. |
| P3.10 | Add a CI pipeline that builds, runs the Spock specification, and runs dependency CVE scanning on every change. |
| P3.11 | Add a Dockerfile and JaCoCo coverage reporting if the application is to be deployed and measured. |

**Exit condition.** No deprecated APIs, no dead code, no committed OS artifacts, configuration externalized, and CI enforcing build plus tests plus vulnerability scanning.

## Optional — Functional Completion

Out of scope for modernization but required before this is a deployable service. Tracked here so it is not mistaken for technical debt that an upgrade will resolve.

| Action | Detail |
|---|---|
| F1 | Implement a real `FileStorage`. The interface is a single method — `Optional<FilePointer> findFile(UUID)` — so this is additive. The natural AWS shape is S3 for file bytes plus DynamoDB for the UUID→object metadata mapping, behind a new `S3FileStorage`/`S3FilePointer` adapter pair. |
| F2 | Add an upload/registration path that mints UUIDs and persists metadata; none exists today. |
| F3 | Remove the `@Profile("!test")` / `@Profile("test")` bean-selection trick once a real storage implementation exists, replacing it with conventional test configuration. |
| F4 | Reconsider content-hash ETags for large or remote objects — hashing full S3 object contents per request will not scale; prefer S3's own ETag or stored metadata. |

## Action Item Register

| ID | Action | Severity | Phase | Blocks |
|---|---|---|---|---|
| P0.1 | Register `src/test/groovy` as a test-source root | 🟠 Medium | 0 | All verification |
| P0.2 | Establish passing baseline for the Spock spec | 🟠 Medium | 0 | Phase 1 |
| P0.3 | Rename spec file to match class name | 🟡 Low | 0 | — |
| P1.1 | Remove explicit Spring Framework pins and unused property | 🔴 High | 1 | P1.2 |
| P1.2 | Stage Spring Boot 1.2.3 → 1.5 → 2.7 → 3.x | 🔴 High | 1 | P2.1 |
| P1.3 | Java 8 → 11 → 17 → 21 | 🔴 High | 1 | Boot 3.x, Guava upgrade |
| P1.4 | Modernize Spring MVC annotations and date binding | 🔴 High | 1 | — |
| P2.1 | Upgrade Guava | 🟠 Medium | 2 | — |
| P2.2 | Remove `commons-io` | 🟠 Medium | 2 | — |
| P2.3 | Remove `commons-codec` | 🟠 Medium | 2 | — |
| P2.4 | Remove or re-scope `cglib-nodep` | 🟠 Medium | 2 | — |
| P2.5 | Move `groovy-all` to `test` scope | 🟠 Medium | 2 | — |
| P2.6 | Delete plain-HTTP milestone repository | 🟠 Medium | 2 | — |
| P2.7 | Explicitly configure actuator exposure | 🟠 Medium | 2 | — |
| P2.8 | Decide the `/download/**` authorization model | 🟠 Medium | 2 | — |
| P2.9 | Remove duplicate `spring-test` declaration | 🟡 Low | 2 | — |
| P2.10 | Add dependency CVE scanning | 🟠 Medium | 2 | — |
| P3.1 | Delete `new Integer("1234")` line | 🟡 Low | 3 | — |
| P3.2 | Delete unused `Sha512ShallowEtagHeaderFilter` | 🟡 Low | 3 | — |
| P3.3 | Externalize throttle rate | 🟡 Low | 3 | P3.4 |
| P3.4 | Add `application.yml` | 🟡 Low | 3 | — |
| P3.5 | Make ETag hashing lazy or cached | 🟡 Low | 3 | — |
| P3.6 | Fix exception rewrapping in `FileSystemPointer` | 🟡 Low | 3 | — |
| P3.7 | Add `.gitignore`, delete `.DS_Store` | 🟡 Low | 3 | — |
| P3.8 | Expand `README.md` | 🟡 Low | 3 | — |
| P3.9 | Upgrade `maven-compiler-plugin` | 🟡 Low | 3 | — |
| P3.10 | Add CI pipeline | 🟡 Low | 3 | — |
| P3.11 | Add Dockerfile and coverage reporting | 🟡 Low | 3 | — |
| F1–F4 | Implement real storage, upload path, test config, ETag strategy | — | Optional | Production readiness |
