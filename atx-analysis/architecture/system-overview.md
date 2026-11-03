# System Overview

[← Back to index](../README.md)

## Purpose and Scope

`download-server` is a single-module Spring Boot web application whose sole responsibility is serving file downloads over HTTP with correct and complete HTTP caching semantics. It has no persistence layer, no outbound integrations, and no asynchronous processing.

## Architectural Style

**Layered hexagonal (ports and adapters) within a single deployable.**

```
┌─────────────────────────────────────────────────────┐
│ Delivery layer      DownloadController              │  Spring MVC
├─────────────────────────────────────────────────────┤
│ Domain layer        ExistingFile                    │  HTTP caching +
│                     ThrottlingInputStream           │  response policy
├─────────────────────────────────────────────────────┤
│ Ports               FileStorage, FilePointer         │  framework-free
├─────────────────────────────────────────────────────┤
│ Adapters            FileSystemStorage               │  @Profile("!test")
│                     FileSystemPointer               │
│                     FileStorageStub (test)          │  @Profile("test")
└─────────────────────────────────────────────────────┘
```

The ports (`FileStorage`, `FilePointer`) are deliberately free of Spring types — they reference only `java.io`, `java.time`, `java.util`, and Guava's `MediaType`. This keeps the storage abstraction substitutable and is the codebase's strongest architectural property.

## Technology Stack

| Concern | Choice | Version |
|---|---|---|
| Language | Java | source/target `1.8` |
| Test language | Groovy | `2.4.3` |
| Application framework | Spring Boot | `1.2.3.RELEASE` |
| Web framework | Spring WebMVC | `4.1.6.RELEASE` |
| HTTP server | Embedded Tomcat (Boot default) | managed by Boot 1.2.3 |
| Operational endpoints | Spring Boot Actuator | `1.2.3.RELEASE` |
| Utility library | Google Guava | `18.0` |
| IO constants | Apache Commons IO | `2.4` |
| Logging facade / backend | SLF4J / Logback | managed by Boot |
| Test framework | Spock + Spring Test | `1.0-groovy-2.4` / `4.1.6.RELEASE` |
| Build | Maven + Maven Wrapper | `maven-compiler-plugin 3.0` |

See [dependencies.md](dependencies.md) for the full graph and [../technical-debt/outdated-components.md](../technical-debt/outdated-components.md) for currency analysis.

## Key Architectural Decisions

| Decision | Where | Rationale / consequence |
|---|---|---|
| **Two-step download: redirect then serve** | `DownloadController.redirect` → `ExistingFile.redirectDownload` | `/download/{uuid}` answers `301` with `Location: /download/{uuid}/{originalName}`, so the browser's saved filename is correct without a `Content-Disposition` header. Consequence: every download costs two round trips. |
| **Content-hash ETags** | `FileSystemPointer.getEtag()` → `Files.hash(target, Hashing.sha512())` | ETags are strong validators derived from content, so they remain correct across replicas and restarts (unlike inode/mtime-based tags). Consequence: the full file is hashed on every `FileSystemPointer` construction — an eager, O(filesize) cost on every request. |
| **Storage behind a port** | `FileStorage` / `FilePointer` interfaces | Allows the filesystem adapter to be swapped for S3/DB with no delivery-layer change, and enables the profile-based test stub. |
| **Profile-based adapter selection** | `@Profile("!test")` on `FileSystemStorage`, `@Profile("test")` on `FileStorageStub` | Integration tests exercise the real controller and real caching logic against deterministic fixture content, with no mocking framework. |
| **Application-level bandwidth throttling** | `ThrottlingInputStream` + Guava `RateLimiter` | Caps each download at 1 MB/s to protect upstream bandwidth. Consequence: a blocking `acquire` on the serving thread — see [patterns.md](patterns.md) for the concurrency implication. |
| **`HEAD` shares the `GET` code path** | `ExistingFile.resourceToReturn` returns `null` for non-`GET` | Guarantees `HEAD` headers are byte-identical to `GET` headers without duplicating logic, and avoids opening the file at all. |
| **Constructor injection only** | `DownloadController(FileStorage)` | No field injection, no setters, no static state in production code. |
| **Guava `MediaType` in the port, Spring `MediaType` at the edge** | `ExistingFile.toMediaType` | Keeps the port framework-free at the cost of a conversion bridge. |

## Deployment Model

- **Artifact:** executable JAR with embedded servlet container (Spring Boot default packaging; no `<packaging>` override in `pom.xml`).
- **Process model:** single JVM process, started via `MainApplication.main`.
- **Configuration:** no `application.properties`/`application.yml` present. All settings are Boot defaults; the only configuration input is the active Spring profile (`test` vs. not-`test`).
- **External state:** none. The service is stateless apart from the content served by the storage adapter.
- **Scaling:** horizontally scalable without coordination — content-hash ETags and the absence of session state mean any replica can serve any request. The 1 MB/s throttle is per-request and per-instance, not global.
- **Infrastructure as code:** none present. No `Dockerfile`, no Kubernetes manifests, no CloudFormation/CDK/Terraform, no CI workflow definitions. See [../specialized/deployment.md](../specialized/deployment.md).

## Cross-Cutting Concerns

| Concern | Implementation |
|---|---|
| Logging | SLF4J loggers in `ExistingFile`, `FileSystemStorage`, `FileStorageStub`; Logback config at `src/main/resources/logback.xml` |
| Error handling | Checked exceptions wrapped in `IllegalArgumentException` (`FileSystemPointer` constructor and `open()`, `ExistingFile.redirectDownload`); missing files surface as `Optional.empty()` → `404` |
| Caching | `ExistingFile.cached` evaluates `If-None-Match` and `If-Modified-Since` |
| Rate limiting | `ThrottlingInputStream` |
| Monitoring | Spring Boot Actuator (auto-configured, unconfigured) |
| Security | None — see [../analysis/security-patterns.md](../analysis/security-patterns.md) |

## Related Documents

[components.md](components.md) · [dependencies.md](dependencies.md) · [patterns.md](patterns.md) · [../diagrams/architecture.md](../diagrams/architecture.md)
