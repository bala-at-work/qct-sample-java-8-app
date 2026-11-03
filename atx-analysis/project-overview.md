# Project Overview

- [Back to README](README.md)
- Related: [architecture/system-overview.md](architecture/system-overview.md) · [technical-debt-report.md](technical-debt-report.md)

## Table of Contents
1. [Identity](#identity)
2. [Purpose](#purpose)
3. [Technology Stack](#technology-stack)
4. [Repository Layout](#repository-layout)
5. [How It Runs](#how-it-runs)
6. [Scope Boundaries](#scope-boundaries)

## Identity

| Attribute | Value |
|---|---|
| Maven coordinates | `com.nurkiewicz.download:download-server:0.0.1-SNAPSHOT` |
| Packaging | jar (Spring Boot default from parent) |
| Parent POM | `org.springframework.boot:spring-boot-starter-parent:1.2.3.RELEASE` |
| Language level | Java 1.8 (source and target) |
| Build tool | Maven (wrapper pins Maven 3.9.8) |
| Root package | `com.nurkiewicz.download` |
| Source size | 652 lines across 11 Java files + 1 Groovy spec |
| Modules | Single module |

`README.md` in the repository root describes it as "Amazon Q Code Transformation: Sample Java 8 Project Built With Maven".

## Purpose

An HTTP **file download server** that demonstrates correct, production-grade HTTP file serving semantics:

- Content-addressable **ETags** derived from a SHA-512 hash of file content
- Conditional GET handling (`If-None-Match`, `If-Modified-Since` → `304 Not Modified`)
- `HEAD` support that returns full headers with an empty body
- `301 Moved Permanently` redirect from an opaque UUID URL to a canonical, filename-bearing URL
- Server-side **bandwidth throttling** at 1 MB/s via a Guava `RateLimiter`

File storage itself is deliberately a stub: `FileSystemStorage` returns the application's own `logback.xml` for any UUID requested, and its Javadoc instructs the reader to "Replace with real implementation."

## Technology Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 8 |
| Test language | Groovy | 2.4.3 |
| Application framework | Spring Boot | 1.2.3.RELEASE |
| Web framework | Spring Web MVC | 4.1.6.RELEASE |
| Operations | Spring Boot Actuator | 1.2.3.RELEASE |
| Utilities | Guava | 18.0 |
| Utilities | Apache Commons IO | 2.4 |
| Utilities | Apache Commons Codec | 1.10 |
| Bytecode | cglib-nodep | 3.1 |
| Logging | Logback (via Boot starter) + SLF4J API | Boot-managed |
| Test framework | Spock | 1.0-groovy-2.4 |
| Embedded server | Tomcat (Boot 1.2.3 default) | Boot-managed |

No database, no message broker, no cache, no external service client is present anywhere in the codebase.

## Repository Layout

```
/repo
├── pom.xml                      # single-module Maven build
├── mvnw, mvnw.cmd               # Maven wrapper scripts
├── .mvn/wrapper/maven-wrapper.properties
├── README.md, LICENSE, CODE_OF_CONDUCT.md
├── .DS_Store                    # stray macOS artifact (should be removed)
├── ATXDocumentation/            # this documentation
└── src
    ├── main
    │   ├── java/com/nurkiewicz/download/   # 8 Java files
    │   └── resources/logback.xml           # logging config + the file served by the stub
    └── test
        ├── java/com/nurkiewicz/download/   # FileExamples, FileStorageStub
        ├── java/org/springframework/web/filter/Sha512ShallowEtagHeaderFilter.java
        ├── groovy/com/nurkiewicz/download/DownloadControllerTest.groovy
        └── resources/download.txt          # 6-byte fixture ("foobar")
```

## How It Runs

`MainApplication` is annotated `@SpringBootApplication`; `SpringApplication.run` starts an embedded Tomcat, component-scans `com.nurkiewicz.download`, and registers `DownloadController` plus whichever `FileStorage` bean matches the active profile:

- Profile **not** `test` → `FileSystemStorage` (`@Profile("!test")`)
- Profile `test` → `FileStorageStub` (`@Profile("test")`)

No `application.properties` or `application.yml` exists, so every setting is a Spring Boot 1.2.3 default (port 8080, default actuator endpoints, no security).

## Scope Boundaries

What this project does **not** contain, confirmed by exhaustive scan:

- No persistence: no `@Entity`, no JPA, no JDBC, no SQL, no `persistence.xml`
- No declarative service definitions: no Spring XML bean contexts, no WSDL, no `.proto`, no GraphQL schema, no OpenAPI spec, no MyBatis/Hibernate mappings, no `web.xml`
- No infrastructure as code: no Dockerfile, no CloudFormation/SAM, no Kubernetes manifests, no Docker Compose
- No CI/CD configuration: no `.github/`, no Jenkinsfile, no `buildspec.yml`, no `.gitlab-ci.yml`
- No UI: no templates, no static assets, no JavaScript
- No security configuration: no Spring Security, no authentication, no authorization
- No `.gitignore`
