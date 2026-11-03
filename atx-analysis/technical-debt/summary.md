# Technical Debt Summary

[← Back to index](../README.md) · [Root report](../technical-debt-report.md)

Full inventory of findings. Priority order is fixed: (1) EOL/deprecated runtimes and frameworks, (2) outdated dependencies, (3) code quality and architectural issues.

## 1. Runtimes and Frameworks (High)

| ID | Finding | Location | Severity |
|---|---|---|---|
| TD-01 | Java 8 source/target — EOL for mainstream support | `pom.xml` (`maven-compiler-plugin` config) | High |
| TD-02 | Spring Boot `1.2.3.RELEASE` — EOL (2015 release line) | `pom.xml` `<parent>` + 3 starters | High |
| TD-03 | Spring Framework `4.1.6.RELEASE` — EOL, pre-Jakarta | `pom.xml` (9 `org.springframework` artifacts) | High |
| TD-04 | Plaintext `http://repo.spring.io/milestone/` repository | `pom.xml` `<repositories>` | High |
| TD-05 | `FileSystemStorage` is a stub; no real production storage | `FileSystemStorage.java:23-30` | High |

## 2. Outdated Dependencies (Medium)

| ID | Finding | Location | Severity |
|---|---|---|---|
| TD-06 | Guava `18.0` | `pom.xml` | Medium |
| TD-07 | Commons IO `2.4` (used only for `ONE_MB`) | `pom.xml`, `ExistingFile.java:21` | Medium |
| TD-08 | Commons Codec `1.10` — no source usage found | `pom.xml` | Medium |
| TD-09 | `groovy-all 2.4.3` at compile scope though test-only; legacy `org.codehaus.groovy` coordinates | `pom.xml` | Medium |
| TD-10 | `cglib-nodep 3.1` legacy proxy dependency | `pom.xml` | Medium |

## 3. Developer / Build Tooling (Low)

| ID | Finding | Location | Severity |
|---|---|---|---|
| TD-11 | `maven-compiler-plugin 3.0` (2012) | `pom.xml` | Low |
| TD-12 | Spock `1.0-groovy-2.4` test framework | `pom.xml` | Low |
| TD-13 | `spring-test 4.1.6.RELEASE` declared twice | `pom.xml` | Low |

## 4. Code Quality and Architecture (Low)

| ID | Finding | Location | Severity |
|---|---|---|---|
| TD-14 | Deprecated `new Integer("1234")` assigned to unused local `temp` | `MainApplication.java:10` | Low |
| TD-15 | Missing `@Override` on `FileStorageStub.findFile` | `FileStorageStub.java:20` | Low |
| TD-16 | Hardcoded 1 MB/s throttle rate, not configurable | `ExistingFile.java:99` | Low |
| TD-17 | Test class placed in `org.springframework.web.filter` package; never registered | `Sha512ShallowEtagHeaderFilter.java:1` | Low |
| TD-18 | Spock file/class name mismatch (`DownloadControllerTest.groovy` vs `DownloadControllerSpec`) | `DownloadControllerTest.groovy:25` | Low |
| TD-19 | `@RequestHeader` on `Optional<Date>` relies on legacy Spring 4.1 conversion behavior | `DownloadController.java:38,50` | Low |
| TD-20 | No explicit stream-close guarantee on error paths after `FilePointer.open()` | `ExistingFile.java:97-101` | Low |
| TD-21 | `MainApplication` is package-private but used as `@ContextConfiguration` class | `MainApplication.java:7` | Low |
| TD-22 | No `application.properties`/`application.yml`; Actuator exposed with default (unsecured in Boot 1.x) settings | `src/main/resources/` | Low |

## Debt Distribution by Area

| Area | High | Medium | Low |
|---|---|---|---|
| Build / dependency management (`pom.xml`) | 4 | 5 | 3 |
| Production source | 1 | 0 | 5 |
| Test source | 0 | 0 | 2 |
| Configuration | 0 | 0 | 1 |

## Positive Findings

These reduce overall remediation difficulty:

- Clean port/adapter separation (`FileStorage`, `FilePointer` interfaces) with no framework types leaking into the ports.
- Constructor injection throughout — no field injection, no static singletons in production code.
- No persistence layer, no messaging, no AWS SDK, no Log4j, no native code — nothing that typically complicates a JDK upgrade.
- Meaningful behavioral test coverage of HTTP caching semantics (12 Spock specifications).
- Single Maven module, no multi-module dependency ordering to manage.

Next: [outdated-components.md](outdated-components.md) · [maintenance-burden.md](maintenance-burden.md) · [remediation-plan.md](remediation-plan.md)
