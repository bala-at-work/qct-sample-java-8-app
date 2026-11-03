# Outdated Components

- [Back to README](../README.md) · [Root report](../technical-debt-report.md)
- Siblings: [summary.md](summary.md) · [maintenance-burden.md](maintenance-burden.md) · [remediation-plan.md](remediation-plan.md)

## Table of Contents
1. [Runtime and Framework](#runtime-and-framework)
2. [Runtime Dependencies](#runtime-dependencies)
3. [Developer and Build Dependencies](#developer-and-build-dependencies)
4. [Scope Defects](#scope-defects)
5. [Repository Configuration](#repository-configuration)
6. [Deprecated APIs in Source](#deprecated-apis-in-source)
7. [Full Dependency Inventory](#full-dependency-inventory)

Severity scheme: **High** = EOL/deprecated/outdated runtimes and frameworks · **Medium** = outdated runtime/production dependencies · **Low** = outdated developer/build dependencies.

## Runtime and Framework

### 1. Java/runtime version
| Field | Value |
|---|---|
| Declared | `source` = `1.8`, `target` = `1.8` (via `maven-compiler-plugin` 3.0) |
| Severity | 🔴 **High** |
| Status | Java 8 no longer receives free public updates from Oracle; it is three LTS generations behind |
| Evidence | `/repo/pom.xml` build/plugins section |
| Impact | No access to modern language features, modern GCs, container-awareness improvements, or current security patches on a free support track. Also prevents adoption of any dependency that now requires Java 17+. |
| Upgrade path | Java 8 → 11 → 17 → 21 (LTS). Consume Amazon Corretto for a long-term supported, no-cost build. Prefer `<maven.compiler.release>` over the `source`/`target` pair once on Java 9+. |
| Note | No `<java.version>` property is set; the language level lives only in the compiler-plugin configuration, so an upgrade must edit the plugin block. |

### 2. Spring Boot
| Field | Value |
|---|---|
| Declared | parent `spring-boot-starter-parent:1.2.3.RELEASE`; `spring-boot-starter-web`, `-actuator`, `-test` all `1.2.3.RELEASE` |
| Severity | 🔴 **High** |
| Status | Released 2015; the 1.x line is fully end-of-life and receives no security fixes |
| Impact | Unpatchable framework CVEs. Boot 1.2.x also predates the Boot 2.x configuration/actuator model, the relaxed-binding rework, and the Jakarta namespace, so the gap to current Boot is large. |
| Upgrade path | Staged: 1.2.3 → 1.5.x (last 1.x, eases property migration) → 2.0 → 2.7 → 3.x. The `javax`→`jakarta` rename lands at Boot 3.0 and requires Java 17. |
| Breaking changes to expect | Actuator endpoint paths move under `/actuator`; `spring-boot-starter-test` switches from JUnit 4 + Hamcrest to JUnit 5 + AssertJ; `@RequestMapping(method=...)` is superseded by `@GetMapping`; `Optional<Date>` `@RequestHeader` binding and date-header conversion behavior change. |

### 3. Spring Framework
| Field | Value |
|---|---|
| Declared | `spring-core`, `-beans`, `-context`, `-context-support`, `-web`, `-webmvc`, `-aop`, `-expression`, `-test` all `4.1.6.RELEASE` |
| Severity | 🔴 **High** |
| Status | End-of-life; Spring 4.x receives no open-source updates |
| Impact | Nine artifacts pinned to an unsupported minor line. `ShallowEtagHeaderFilter.generateETagHeaderValue(byte[])` — the protected method overridden by `Sha512ShallowEtagHeaderFilter` — changed signature in later Spring versions, so that class will not compile unchanged after the upgrade. |
| Upgrade path | Remove the explicit pins entirely and let the Spring Boot parent BOM manage Spring Framework versions. |

### 4. Explicit Spring version pinning
| Field | Value |
|---|---|
| Problem | The POM declares a Spring Boot parent (which manages Spring Framework versions via its BOM) and then **overrides** those versions with explicit `4.1.6.RELEASE` pins on eight Spring artifacts |
| Severity | 🔴 **High** |
| Impact | Upgrading the Boot parent will silently have **no effect** on the Spring Framework version — the pins win. A naive parent bump produces a Boot/Spring combination that was never tested together and can fail at runtime with `NoSuchMethodError`. |
| Aggravating factor | A `<spring.version>4.1.1.RELEASE</spring.version>` property is declared but never referenced, so the POM documents one version and uses another. |
| Remediation | Delete all eight explicit `org.springframework:*` version pins and the unused property; depend on the Boot starters only. |

## Runtime Dependencies

All severity 🟠 **Medium** (runtime/production libraries).

| Dependency | Declared version | Age | Actually used for | Notes |
|---|---|---|---|---|
| `com.google.guava:guava` | 18.0 | 2014 | `RateLimiter` (throttling), `Hashing.sha512` (ETag), `com.google.common.net.MediaType` (content type), `MoreObjects` (toString) | Many major releases behind. `RateLimiter` and `Hashing` remain `@Beta`-adjacent APIs; verify signatures on upgrade. Guava dropped Java 8 support in recent releases — upgrade the JDK first. |
| `commons-io:commons-io` | 2.4 | 2012 | `FileUtils.ONE_MB` constant only | Used for a single constant; this dependency could be removed outright in favour of a local constant. |
| `commons-codec:commons-codec` | 1.10 | 2014 | No direct source usage found | Appears to be a leftover; a candidate for removal. |
| `cglib:cglib-nodep` | 3.1 | 2014 | Spock mock proxying | Declared at **compile** scope but needed only for tests. Modern Spock bundles ByteBuddy and no longer needs cglib declared. |

## Developer and Build Dependencies

All severity 🟡 **Low** (developer/build tooling).

| Component | Declared version | Notes |
|---|---|---|
| `org.apache.maven.plugins:maven-compiler-plugin` | 3.0 | Released 2012; the only plugin declared in the POM. Later versions are required for `release`-based compilation and modern JDK support. |
| `org.spockframework:spock-core` / `spock-spring` | 1.0-groovy-2.4 | Spock 1.x predates the JUnit 5 platform. Spock 2.x is required for JUnit Platform integration and modern Groovy. |
| `org.codehaus.groovy:groovy-all` | 2.4.3 | Groovy 2.4 is superseded; the coordinate also moved from `org.codehaus.groovy` to `org.apache.groovy` in Groovy 4. |
| Maven wrapper | 3.9.8 (`wrapperVersion=3.3.2`, `distributionType=only-script`) | This is the one genuinely current piece of tooling in the project. |
| Surefire / Groovy compilation plugin | **absent** | No test-execution or Groovy-compilation plugin is declared; see [maintenance-burden.md](maintenance-burden.md#build-configuration-gaps). |

## Scope Defects

| Dependency | Declared scope | Correct scope | Severity | Impact |
|---|---|---|---|---|
| `org.codehaus.groovy:groovy-all` | `compile` | `test` | 🟠 Medium | Ships a large test-only runtime into the production artifact, enlarging the deployable and widening the attack surface. |
| `cglib:cglib-nodep` | `compile` | `test` (or removed) | 🟠 Medium | Same class of problem; cglib is needed only by Spock mocks. |
| `org.springframework:spring-test` | `test` (declared **twice**) | `test`, declared once | 🟡 Low | Duplicate declaration of the same GAV; harmless at resolution time but a maintenance trap. Note `spring-boot-starter-test` already excludes `spring-test`, so the explicit declaration is intentional — but only one copy is needed. |

## Repository Configuration

| Item | Value | Severity | Issue |
|---|---|---|---|
| `io.spring.repo.maven.milestone` | `http://repo.spring.io/milestone/` | 🟠 Medium | Declared over **plain HTTP**, exposing dependency resolution to tampering on the wire. Maven Central has also rejected plain-HTTP resolution for years, and this milestone repository no longer serves the artifacts this build needs. Since every declared dependency is a GA release available on Maven Central, this repository can simply be deleted. |

## Deprecated APIs in Source

| API | Location | Severity | Replacement |
|---|---|---|---|
| `new Integer(String)` | `MainApplication.java` — `Integer temp = new Integer("1234");` | 🟡 Low | Deprecated for removal since Java 9. The local variable is never read, so the correct fix is to **delete the line**. |
| `ShallowEtagHeaderFilter.generateETagHeaderValue(byte[])` | `Sha512ShallowEtagHeaderFilter.java` | 🟡 Low | The protected hook's signature changed in later Spring versions (it now accepts an `InputStream`). The class is unused and should be deleted, or rewritten against the current signature if the SHA-512 ETag filter is wanted. |
| `java.util.Date` in `@RequestHeader Optional<Date>` | `DownloadController.java` | 🟡 Low | Legacy date type at the HTTP boundary while the domain already uses `java.time.Instant`. Prefer `ZonedDateTime`/`Instant` binding on upgrade. |

## Full Dependency Inventory

Exactly as declared in `/repo/pom.xml` (19 declarations, 18 distinct GAVs):

| # | groupId:artifactId | Version | Scope | Exclusions | Currency |
|---|---|---|---|---|---|
| 1 | `org.springframework:spring-test` | 4.1.6.RELEASE | test | — | 🔴 EOL |
| 2 | `org.spockframework:spock-spring` | 1.0-groovy-2.4 | test | `org.codehaus.groovy:groovy-all` | 🟡 Outdated |
| 3 | `commons-io:commons-io` | 2.4 | compile | — | 🟠 Outdated |
| 4 | `commons-codec:commons-codec` | 1.10 | compile | — | 🟠 Outdated |
| 5 | `org.springframework.boot:spring-boot-starter-web` | 1.2.3.RELEASE | compile | — | 🔴 EOL |
| 6 | `org.springframework.boot:spring-boot-starter-actuator` | 1.2.3.RELEASE | compile | — | 🔴 EOL |
| 7 | `org.springframework.boot:spring-boot-starter-test` | 1.2.3.RELEASE | test | `org.springframework:spring-test` | 🔴 EOL |
| 8 | `org.spockframework:spock-core` | 1.0-groovy-2.4 | test | — | 🟡 Outdated |
| 9 | `com.google.guava:guava` | 18.0 | compile | — | 🟠 Outdated |
| 10 | `cglib:cglib-nodep` | 3.1 | compile | — | 🟠 Outdated + wrong scope |
| 11 | `org.springframework:spring-beans` | 4.1.6.RELEASE | compile | — | 🔴 EOL |
| 12 | `org.springframework:spring-context` | 4.1.6.RELEASE | compile | — | 🔴 EOL |
| 13 | `org.springframework:spring-context-support` | 4.1.6.RELEASE | compile | — | 🔴 EOL |
| 14 | `org.springframework:spring-webmvc` | 4.1.6.RELEASE | compile | — | 🔴 EOL |
| 15 | `org.springframework:spring-web` | 4.1.6.RELEASE | compile | — | 🔴 EOL |
| 16 | `org.springframework:spring-aop` | 4.1.6.RELEASE | compile | — | 🔴 EOL |
| 17 | `org.springframework:spring-core` | 4.1.6.RELEASE | compile | — | 🔴 EOL |
| 18 | `org.springframework:spring-expression` | 4.1.6.RELEASE | compile | — | 🔴 EOL |
| 19 | `org.springframework:spring-test` | 4.1.6.RELEASE | test | — | 🔴 EOL (duplicate of #1) |

Key transitive dependencies inherited from `spring-boot-starter-parent:1.2.3.RELEASE` (not pinned in this POM): embedded Tomcat 8.0.x, Jackson 2.4.x, SLF4J 1.7.x, Logback 1.1.x, Hibernate Validator 5.1.x, JUnit 4.x, Hamcrest, Mockito. All are the EOL versions that line shipped with and all will move when the parent is upgraded.

See also: [../analysis/dependency-analysis.md](../analysis/dependency-analysis.md) and [../architecture/dependencies.md](../architecture/dependencies.md).
