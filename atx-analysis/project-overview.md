# Project Overview

[← Back to index](README.md)

## Identity

| Field | Value |
|---|---|
| Group / Artifact | `com.nurkiewicz.download` / `download-server` |
| Version | `0.0.1-SNAPSHOT` |
| Build tool | Maven (wrapper `mvnw`, Maven 3.9.8, wrapper 3.3.2) |
| Packaging | Spring Boot executable JAR (inherited from parent) |
| Language level | Java 1.8 (`maven-compiler-plugin` 3.0 `source`/`target` = 1.8) |
| Secondary language | Groovy 2.4.3 (tests, Spock) |
| Framework | Spring Boot 1.2.3.RELEASE / Spring Framework 4.1.x |
| License | See `LICENSE` at repo root |

Repository README states the project is a sample Java 8 Maven project used to exercise Amazon Q Code Transformation.

## Purpose

An HTTP file-download server. A client requests a file by UUID; the server resolves the UUID to a `FilePointer`, then streams the bytes back with full HTTP caching semantics (ETag, `If-None-Match`, `Last-Modified`, `If-Modified-Since`), correct `Content-Length`/`Content-Type`, `HEAD` support, and server-side bandwidth throttling (1 MB/s per download).

## Technology Stack

- **Runtime**: JVM, Java 8 target
- **Web**: Spring Boot Web starter (embedded Tomcat), Spring MVC `@RestController`
- **Ops**: Spring Boot Actuator
- **Utilities**: Google Guava 18.0 (hashing, `RateLimiter`, `MediaType`, `MoreObjects`), Apache Commons IO 2.4, Commons Codec 1.10, cglib-nodep 3.1
- **Logging**: SLF4J API + Logback (`src/main/resources/logback.xml`)
- **Testing**: Spock 1.0-groovy-2.4, Spring Test 4.1.6, Spring Boot Test 1.2.3

## Source Layout

```
/repo
├── pom.xml                   single-module Maven build
├── mvnw, mvnw.cmd, .mvn/     Maven wrapper (3.9.8)
├── README.md, LICENSE, CODE_OF_CONDUCT.md
└── src
    ├── main/java/com/nurkiewicz/download/   8 production types
    ├── main/resources/logback.xml
    ├── test/java/com/nurkiewicz/download/   test doubles & fixtures
    ├── test/java/org/springframework/web/filter/  framework-package filter subclass
    ├── test/groovy/com/nurkiewicz/download/ Spock specification
    └── test/resources/download.txt          fixture ("foobar")
```

## Entry Points

| Entry point | Location |
|---|---|
| `main(String[])` | `MainApplication.java:9` |
| `GET|HEAD /download/{uuid}` | `DownloadController.java:33` (redirect to canonical URL) |
| `GET|HEAD /download/{uuid}/{filename}` | `DownloadController.java:45` (serve bytes) |
| Actuator endpoints | Provided by `spring-boot-starter-actuator` (auto-configured, unsecured) |

## Size

| Metric | Value |
|---|---|
| Java + Groovy lines (excluding excluded dirs) | 652 |
| Production classes/interfaces | 8 |
| Test classes/specs | 4 |
| Maven modules | 1 |
| Declared direct dependencies | 20 |

Not a large codebase (< 1,000,000 LOC), so this documentation is exhaustive rather than summarized.

## Notable Observations

- No persistence layer, no database, no message broker, no infrastructure-as-code, no containerization.
- `FileSystemStorage` is explicitly a stub: it returns `logback.xml` for *every* UUID (see `FileSystemStorage.java:13-16`).
- No security configuration exists; all endpoints are anonymous.
- The declared `spring.version` property (`4.1.1.RELEASE`) is unused — all Spring dependencies hardcode `4.1.6.RELEASE`.
- `pom.xml` adds an **insecure `http://repo.spring.io/milestone/`** repository.

See [technical-debt-report.md](technical-debt-report.md) for prioritized findings.
