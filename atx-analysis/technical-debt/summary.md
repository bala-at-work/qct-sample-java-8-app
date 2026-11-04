# Technical Debt — Summary

- [Back to README](../README.md) · [Root report](../technical-debt-report.md)
- Siblings: [outdated-components.md](outdated-components.md) · [maintenance-burden.md](maintenance-burden.md) · [remediation-plan.md](remediation-plan.md)

## Table of Contents
1. [Overview](#overview)
2. [Findings by Category](#findings-by-category)
3. [Severity Distribution](#severity-distribution)
4. [Risk Narrative](#risk-narrative)

## Overview

| Metric | Value |
|---|---|
| Application source analyzed | 652 lines (11 Java + 1 Groovy) |
| Total findings | 21 |
| High severity | 4 |
| Medium severity | 7 |
| Low severity | 10 |
| Modules affected | 1 (single-module project) |
| Blocking finding class | EOL runtime and framework versions |

Severity is assigned per the fixed scheme: **High** = outdated/EOL/deprecated runtimes and frameworks; **Medium** = outdated runtime/production dependencies; **Low** = outdated developer/build dependencies and code-quality/architectural issues.

## Findings by Category

### Runtime and framework (High)
| Finding | Current | Evidence |
|---|---|---|
| Java language level EOL for free updates | 1.8 | `pom.xml` → `maven-compiler-plugin` `<source>1.8</source><target>1.8</target>` |
| Spring Boot EOL | 1.2.3.RELEASE (2015) | `pom.xml` parent + 3 starter dependencies |
| Spring Framework EOL | 4.1.6.RELEASE | 8 explicit `org.springframework:*` dependencies |
| Spring versions pinned against the parent BOM | 4.1.6 vs Boot-managed | `pom.xml` dependency block |

### Runtime dependencies (Medium)
| Finding | Current | Used by |
|---|---|---|
| Guava far behind | 18.0 | `ExistingFile`, `FileSystemPointer`, `ThrottlingInputStream` |
| commons-io behind | 2.4 | `ExistingFile` (`FileUtils.ONE_MB`) |
| commons-codec behind | 1.10 | declared; no direct source usage found |
| cglib-nodep behind, wrong scope | 3.1, compile | required only by Spock mocking |
| `groovy-all` at compile scope | 2.4.3 | test-only library on the production classpath |

### Security (Medium)
| Finding | Detail |
|---|---|
| Plain-HTTP artifact repository | `http://repo.spring.io/milestone/` declared in `pom.xml` |
| No authentication or authorization | No Spring Security dependency; both download endpoints are public |
| Actuator unsecured | `spring-boot-starter-actuator` with no configuration; Boot 1.2.x exposes sensitive endpoints by default |
| No transport security configuration | No TLS/HTTPS settings; no `application.properties` at all |

### Build and test infrastructure (Medium / Low)
| Finding | Severity | Detail |
|---|---|---|
| `src/test/groovy` not a declared test-source root | Medium | No `build-helper`, `gmavenplus` or `groovy-eclipse` plugin; the Spock spec is not compiled by a default build |
| Storage layer is a stub | Medium | `FileSystemStorage.findFile` ignores the UUID and always returns `logback.xml` |
| Spock 1.0-groovy-2.4 | Low | Predates Spock 2.x / JUnit 5 platform |
| `maven-compiler-plugin` 3.0 | Low | Released 2012; only plugin declared |
| Duplicate `spring-test` declaration | Low | Same GAV declared twice in `pom.xml` |
| Unused, contradictory `spring.version` property | Low | Declares 4.1.1.RELEASE while dependencies pin 4.1.6.RELEASE |
| No CI/CD pipeline | Low | No `.github/`, Jenkinsfile, or buildspec |
| No containerization | Low | No Dockerfile or Compose file |
| `.DS_Store` committed, no `.gitignore` | Low | Repository hygiene |

### Code quality (Low)
| Finding | Location |
|---|---|
| `Integer temp = new Integer("1234");` — deprecated constructor, unused local | `MainApplication.java` |
| Dead code: `Sha512ShallowEtagHeaderFilter` never registered as a bean or filter | `src/test/java/org/springframework/web/filter/` |
| Class in a foreign package (`org.springframework.web.filter`) to reach protected API | `Sha512ShallowEtagHeaderFilter.java` |
| Spec filename does not match its class name | `DownloadControllerTest.groovy` / `DownloadControllerSpec` |
| Full-file SHA-512 computed eagerly in the constructor | `FileSystemPointer.java` |
| Checked `IOException` rewrapped as `IllegalArgumentException` | `FileSystemPointer.java` |
| Hard-coded 1 MB/s throttle rate, not configurable | `ExistingFile.java` |

## Severity Distribution

```
High   ████                     4  (19%)  runtime + framework EOL
Medium ███████                  7  (33%)  dependencies, security, build/test wiring
Low    ██████████              10  (48%)  dev tooling, code quality, hygiene
```

## Risk Narrative

The dominant risk is **unpatchable platform exposure**. Spring Boot 1.2.x and Spring Framework 4.1.x receive no upstream security fixes, and Java 8 receives no free public updates; a newly disclosed Spring MVC deserialization or request-mapping vulnerability has no upgrade path short of a major migration. That risk is amplified by the absence of any authentication layer and by Boot 1.2.x's default-open actuator endpoints, which in that era exposed environment and configuration data without credentials.

The mitigating factor is **scale and shape**. At 652 lines with a single package, no persistence layer, no declarative service definitions and a clean ports-and-adapters boundary, this application presents an unusually small migration surface. The Spock specification asserts the full externally visible HTTP contract — status codes, ETag echo, `Content-Length`, `Content-Type`, HEAD body emptiness — which makes it possible to verify a framework upgrade behaviorally rather than by inspection. The one caveat is that the Groovy source root is not wired into the build, so this safety net must be activated before the upgrade begins rather than after.

A secondary, non-security risk is **functional incompleteness**: `FileSystemStorage` is explicitly labelled a stub and serves the application's own logging configuration for every request. The application is therefore a reference implementation rather than a deployable service, and any modernization plan must state whether the real storage adapter is in scope.
