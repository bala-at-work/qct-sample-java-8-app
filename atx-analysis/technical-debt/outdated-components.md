# Outdated Components

[← Back to index](../README.md) · [Summary](summary.md)

Every version below is read directly from `/repo/pom.xml`.

## Runtime / Language

| Component | Declared | Status | Impact |
|---|---|---|---|
| Java language level | `1.8` (`maven-compiler-plugin` `<source>`/`<target>`) | End of mainstream public support | No access to current security baseline, modern GC, records, sealed types, pattern matching, virtual threads. Severity: **High** |

There is no `<java.version>` property, no `.sdkmanrc`, no `Dockerfile`, and no CI workflow — the compiler plugin configuration is the single source of truth for the target JDK.

## Frameworks

| Component | Declared | Status | Impact |
|---|---|---|---|
| `org.springframework.boot:spring-boot-starter-parent` | `1.2.3.RELEASE` | EOL | Severity: **High**. Boot 1.x configuration keys, Actuator model, and auto-configuration differ fundamentally from Boot 3.x. |
| `spring-boot-starter-web` | `1.2.3.RELEASE` | EOL | Severity: **High**. Pulls EOL embedded Tomcat and `javax.servlet`. |
| `spring-boot-starter-actuator` | `1.2.3.RELEASE` | EOL | Severity: **High**. Boot 1.x exposes sensitive endpoints without authentication by default. |
| `spring-boot-starter-test` | `1.2.3.RELEASE` | EOL | Severity: **Low** (test scope). |
| Spring Framework core artifacts | `4.1.6.RELEASE` | EOL | Severity: **High**. Nine artifacts pinned explicitly, overriding the parent BOM: `spring-beans`, `spring-context`, `spring-context-support`, `spring-webmvc`, `spring-web`, `spring-aop`, `spring-core`, `spring-expression`, `spring-test`. |

A `<spring.version>4.1.1.RELEASE</spring.version>` property is declared in `<properties>` but **never referenced** — all Spring dependencies hardcode `4.1.6.RELEASE`. This is a latent inconsistency.

### Namespace implication

All Spring 4.x / Boot 1.x code uses the `javax.*` namespace. Moving to Spring Boot 3.x requires the `javax` → `jakarta` migration. This codebase does not import `javax.*` directly in any source file, so the migration surface is confined to the dependency set — a favorable position.

## Runtime Libraries

| Component | Declared | Usage in source | Severity |
|---|---|---|---|
| `com.google.guava:guava` | `18.0` | `RateLimiter` (`ExistingFile`, `ThrottlingInputStream`), `Hashing`/`HashCode`/`Files.hash` (`FileSystemPointer`, `Sha512ShallowEtagHeaderFilter`), `MediaType` (`FilePointer`, `FileSystemPointer`, `ExistingFile`), `MoreObjects` (`FileSystemPointer`), `HttpHeaders` (test) | Medium |
| `commons-io:commons-io` | `2.4` | `FileUtils.ONE_MB` only — `ExistingFile.java:21` | Medium |
| `commons-codec:commons-codec` | `1.10` | **No usage found** in any source file | Medium |
| `org.codehaus.groovy:groovy-all` | `2.4.3` | Groovy runtime for the Spock spec; declared at **compile** scope (should be `test`). Legacy coordinates — modern Groovy publishes under `org.apache.groovy`. | Medium |
| `cglib:cglib-nodep` | `3.1` | No direct source usage; legacy proxy support for Spock/Spring | Medium |

### Deprecated API usage

| API | Location | Note |
|---|---|---|
| `new Integer(String)` | `MainApplication.java:10` | Deprecated since Java 9; result assigned to unused local `temp`. Replace with `Integer.valueOf` — or, better, delete the line entirely as dead code. |
| `Guava MediaType.charset().transform(...).or(...)` | `ExistingFile.java:124-127` | Uses the Guava `Optional` API rather than `java.util.Optional`; `transform`/`or` are the legacy Guava idioms. |

### Cryptographic methods

SHA-512 via `Hashing.sha512()` is used for ETag generation (`FileSystemPointer.java:22`, `Sha512ShallowEtagHeaderFilter.java:10`). This is a **non-security** use (cache validation), and SHA-512 is not deprecated. No MD5, SHA-1, DES, or custom crypto was found. No finding.

## Build Tooling

| Component | Declared | Status | Severity |
|---|---|---|---|
| `maven-compiler-plugin` | `3.0` (2012) | Severely outdated; predates `<release>` support | Low |
| Maven Wrapper | present (`mvnw`, `mvnw.cmd`, `.mvn/wrapper`) | Version not inspected as part of static source analysis; wrapper properties should be refreshed alongside the JDK upgrade | Low |
| `org.spockframework:spock-core` / `spock-spring` | `1.0-groovy-2.4` | Outdated; Spock 2.x requires JUnit 5 platform | Low |

## Repositories

| Repository | URL | Finding |
|---|---|---|
| `io.spring.repo.maven.milestone` | `http://repo.spring.io/milestone/` | **High** — plaintext HTTP. Supply-chain tampering risk, and Spring's infrastructure no longer serves plaintext HTTP, so this will also fail resolution. All declared dependencies are available from Maven Central; this repository appears unnecessary and should be removed rather than merely switched to HTTPS. |

## Coding Conventions

- Consistent tab indentation across all Java sources.
- Consistent package naming (`com.nurkiewicz.download`) — with the single exception noted in [summary.md](summary.md) TD-17.
- No checkstyle, spotbugs, PMD, SpotLess, or `.editorconfig` configuration present — no automated convention enforcement. Severity: **Low**.

Next: [maintenance-burden.md](maintenance-burden.md)
