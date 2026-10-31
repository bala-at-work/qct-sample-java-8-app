# Outdated Components

[← Back to index](../README.md) · Related: [summary.md](summary.md) · [remediation-plan.md](remediation-plan.md) · [../analysis/dependency-analysis.md](../analysis/dependency-analysis.md)

Every version below was read directly from `pom.xml`, `.mvn/wrapper/maven-wrapper.properties`, or source imports.

## Runtime / Language

| Component | Declared version | Status | Severity | Notes |
|---|---|---|---|---|
| Java compile target | `1.8` (`maven-compiler-plugin` `<source>`/`<target>`) | End of public support; superseded by LTS 11, 17, 21 | **High** | Blocks all modern dependency versions; recommended target Java 21 |
| Groovy | `2.4.3` | Long superseded (Groovy 4.x current line) | Low | Test-only; must move in lockstep with Spock |

## Frameworks

| Component | Declared version | Status | Severity | Notes |
|---|---|---|---|---|
| `spring-boot-starter-parent` | `1.2.3.RELEASE` | **EOL** — Boot 1.x unsupported, no security fixes | **High** | Target the current supported Boot generation; requires Java 17+ and `jakarta.*` |
| `spring-boot-starter-web` | `1.2.3.RELEASE` | EOL | **High** | Embedded Tomcat from a 1.2.3-era BOM |
| `spring-boot-starter-actuator` | `1.2.3.RELEASE` | EOL; Boot 1.x exposes sensitive endpoints by default | **High** | Unreferenced by code — remove or secure |
| `spring-boot-starter-test` | `1.2.3.RELEASE` | EOL | Low | Test scope |
| Spring Framework (`spring-core`, `-beans`, `-context`, `-context-support`, `-web`, `-webmvc`, `-aop`, `-expression`) | `4.1.6.RELEASE` | **EOL** — 4.x line receives no fixes; known CVEs in this line | **High** | `javax.servlet` namespace; migration to `jakarta.*` required |
| `spring-test` | `4.1.6.RELEASE` | EOL; **declared twice** | Low | De-duplicate while upgrading |
| Spock (`spock-core`, `spock-spring`) | `1.0-groovy-2.4` | Superseded by Spock 2.x (JUnit 5 platform) | Low | Upgrading requires Groovy 3+/4+ and a JUnit 5 provider |

## Libraries

| Component | Declared version | Status | Severity | Upgrade friction |
|---|---|---|---|---|
| `com.google.guava:guava` | `18.0` | Severely outdated (current line is 33.x-era); no backported fixes | **Medium** | **Breaking**: `ExistingFile.toMediaType` uses `MediaType.charset().transform(...).or(...)`, whose Guava `Optional`-based API was removed. Rewrite with `java.util.Optional`. `Files.hash(File, HashFunction)` is also deprecated in later versions in favour of `com.google.common.io.Files.asByteSource(file).hash(...)` |
| `commons-io:commons-io` | `2.4` | Outdated | **Medium** | None — only `FileUtils.ONE_MB` is used; consider deleting the dependency and inlining the constant |
| `commons-codec:commons-codec` | `1.10` | Outdated **and unused** (no import anywhere) | **Medium** | None — delete |
| `cglib:cglib-nodep` | `3.1` | Outdated; also at the wrong (compile) scope | **Medium** | Move to `test`; modern Spock/Mockito manage their own byte-code backend |
| `org.codehaus.groovy:groovy-all` | `2.4.3` | Outdated; compile scope for a test-only language; `groovy-all` became a POM-only aggregate in later versions | Low | Switch to `org.apache.groovy` coordinates at `test` scope |
| SLF4J / Logback | managed by Boot 1.2.3 BOM | Outdated via the BOM | Low | Resolved automatically by the Boot upgrade |
| Jackson, embedded Tomcat, Hibernate Validator, SnakeYAML, JUnit/Hamcrest/Mockito | managed by Boot 1.2.3 BOM | Outdated via the BOM | Medium | Resolved automatically by the Boot upgrade |

## Build Tooling

| Component | Version | Status | Severity |
|---|---|---|---|
| `maven-compiler-plugin` | `3.0` | Very old; predates `<release>` support | Low |
| `spring-boot-maven-plugin` | inherited (1.2.3) | EOL with the parent | Low |
| Maven Wrapper | `3.3.2`, Maven `3.9.8` | Current enough | None |
| Groovy compiler plugin | **absent** | `src/test/groovy` is not compiled by the declared build | Low |
| Dependency/vulnerability scanning, SBOM, CI config | **absent** | No `versions-maven-plugin`, `dependency-check`, Dependabot config, or pipeline file | Low |

## Repositories

| Id | URL | Status | Severity |
|---|---|---|---|
| `io.spring.repo.maven.milestone` | `http://repo.spring.io/milestone/` | **Plaintext HTTP** and a milestone channel; no declared dependency requires it. Spring's milestone host also no longer serves this path over HTTP. | **High** |

## Deprecated APIs and Language Constructs in Source

| Item | Location | Note |
|---|---|---|
| `new Integer(String)` | `MainApplication.java:10` | Deprecated for removal since Java 9; also dead code |
| `java.util.Date` for header binding | `DownloadController:38,50`; `ExistingFile:47,54` | Legacy date type; `ZonedDateTime`/`Instant` binding is available in modern Spring |
| `javax.servlet`-era Spring web types | `Sha512ShallowEtagHeaderFilter`, Spring MVC usage | Must become `jakarta.servlet` under Boot 3+ |
| Guava `Optional.transform/or` | `ExistingFile:116-118` | Removed in later Guava |
| Guava `Files.hash(File, HashFunction)` | `FileSystemPointer:22` | Deprecated in later Guava |

## Target Version Guidance

When executing the upgrade, resolve and verify exact versions from the project's resolved dependency tree rather than from memory, and check each chosen version against a vulnerability database before committing it.

| Component | Suggested direction |
|---|---|
| Java | Current LTS (Java 21) |
| Spring Boot | Current supported generation (3.x), which transitively modernizes Spring Framework, Tomcat, Jackson, Logback, and the test stack |
| Spring Framework explicit pins | **Remove** — let the Boot BOM manage them, deleting the `spring.version` property and the `spring-test` exclusion workaround |
| Guava | Current stable release, with the `toMediaType` and `Files.hash` call sites rewritten |
| Commons IO | Remove (inline `ONE_MB`) or upgrade to current |
| Commons Codec | Remove (unused) |
| cglib-nodep / groovy-all | `test` scope, current coordinates/versions |
| Spock | 2.x with the JUnit 5 platform and a matching Groovy line |
| `maven-compiler-plugin` | Current release, using `<release>` instead of `<source>`/`<target>` |
| Milestone repository | Delete, or change to `https://` if genuinely required |
