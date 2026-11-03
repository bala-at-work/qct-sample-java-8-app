# Project Overview

[← Back to index](README.md)

## Identity

| Field | Value |
|---|---|
| Group ID | `com.nurkiewicz.download` |
| Artifact ID | `download-server` |
| Version | `0.0.1-SNAPSHOT` |
| Build tool | Apache Maven (with Maven Wrapper `mvnw`) |
| Packaging | `jar` (default, Spring Boot parent) |
| Primary language | Java (source/target `1.8`) |
| Secondary language | Groovy 2.4.3 (tests only) |
| Framework | Spring Boot `1.2.3.RELEASE`, Spring Framework `4.1.6.RELEASE` |
| License | See [LICENSE](../LICENSE) |

`README.md` in the repo root describes it as a sample Java 8 Maven project for testing Amazon Q Code Transformation.

## Purpose

A single-purpose HTTP service that serves file downloads with correct HTTP semantics:

- `GET`/`HEAD` on a UUID → `301 Moved Permanently` redirect to a filename-bearing URL
- `GET`/`HEAD` on UUID + filename → streams file bytes
- Conditional requests via `If-None-Match` (ETag, SHA-512 of content) and `If-Modified-Since` → `304 Not Modified`
- `Content-Length`, `Content-Type`, `Last-Modified`, `ETag` response headers
- Download bandwidth throttling (1 MB/s) via a Guava `RateLimiter`
- `HEAD` returns headers with an empty body

## Technology Stack

| Layer | Technology |
|---|---|
| Runtime | Java 8 JVM |
| Application framework | Spring Boot 1.2.3 (`spring-boot-starter-web`, `spring-boot-starter-actuator`) |
| Web / MVC | Spring WebMVC 4.1.6, embedded Tomcat (Boot default) |
| Utilities | Google Guava 18.0 (hashing, `RateLimiter`, `MediaType`, `MoreObjects`) |
| IO / codec | Apache Commons IO 2.4, Commons Codec 1.10 |
| Logging | SLF4J API + Logback (`src/main/resources/logback.xml`) |
| Testing | Spock 1.0-groovy-2.4, `spring-boot-starter-test`, `spring-test` 4.1.6, cglib-nodep 3.1 |

## Source Layout

```
/repo
├── pom.xml                      Maven build definition
├── mvnw, mvnw.cmd, .mvn/        Maven Wrapper
├── README.md, LICENSE, CODE_OF_CONDUCT.md
└── src
    ├── main
    │   ├── java/com/nurkiewicz/download/   8 production classes/interfaces
    │   └── resources/logback.xml           logging configuration
    └── test
        ├── groovy/com/nurkiewicz/download/ DownloadControllerTest.groovy (Spock)
        ├── java/com/nurkiewicz/download/   FileExamples, FileStorageStub
        ├── java/org/springframework/web/filter/Sha512ShallowEtagHeaderFilter.java
        └── resources/download.txt          test fixture ("foobar")
```

## Entry Points

| Entry point | Location |
|---|---|
| JVM `main` | `MainApplication.main(String[])` — `MainApplication.java:9` |
| HTTP `/download/{uuid}` | `DownloadController.redirect(...)` — `DownloadController.java:33` |
| HTTP `/download/{uuid}/{filename}` | `DownloadController.download(...)` — `DownloadController.java:45` |
| Actuator endpoints | Auto-configured by `spring-boot-starter-actuator` |

## Size

| Metric | Value |
|---|---|
| Production Java files | 8 |
| Test Java files | 3 |
| Test Groovy files | 1 |
| Total source LOC (`.java` + `.groovy`) | 652 |
| Maven modules | 1 |

Not a large codebase (far below the 1,000,000 LOC threshold); all documentation in this set is exhaustive rather than summarized.

## Noteworthy Characteristics

- No database, no message queue, no external service calls.
- `FileSystemStorage` is explicitly documented in-source as a **stub** that returns `logback.xml` for every UUID — the real storage backend is not implemented.
- Profile-based wiring: `FileSystemStorage` is active when profile `test` is **not** active; `FileStorageStub` is active when it is.
- Hexagonal-leaning design: `FileStorage` / `FilePointer` are ports with filesystem adapters.

See the [technical debt report](technical-debt-report.md) for modernization findings.
