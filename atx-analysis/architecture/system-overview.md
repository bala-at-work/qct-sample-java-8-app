# System Overview

[← Back to index](../README.md) · Related: [components.md](components.md) · [dependencies.md](dependencies.md) · [patterns.md](patterns.md)

## High-Level Architecture

`download-server` is a single-process, stateless Spring Boot web application. It is a thin HTTP layer over a pluggable file-lookup abstraction:

```
HTTP client ──> embedded Tomcat ──> DownloadController ──> FileStorage (port)
                                          │                      │
                                          v                      v
                                    ExistingFile ──────> FilePointer (port)
                                          │                      │
                                          v                      v
                              ThrottlingInputStream <──── local filesystem
```

Three layers:

1. **Web layer** — `DownloadController` maps two URL templates onto `GET`/`HEAD`, binds the UUID path variable and the two conditional-request headers, and converts an empty lookup into `404 NOT FOUND`.
2. **Domain layer** — `ExistingFile` owns all HTTP caching/response-shaping logic: conditional-request evaluation, `301` redirect, `200` with throttled body, `304` without body, header population.
3. **Resource layer** — `FileStorage` resolves a UUID to a `FilePointer`; `FilePointer` exposes size, original name, ETag, media type, last-modified instant, and an `InputStream`. `FileSystemPointer` implements this over `java.io.File` using Guava SHA-512 hashing and `java.nio.file.Files.probeContentType`.

## Technology Stack

| Concern | Technology | Version |
|---|---|---|
| Language | Java | 1.8 target |
| Test language | Groovy | 2.4.3 |
| Application framework | Spring Boot | 1.2.3.RELEASE |
| Web framework | Spring MVC (Spring Framework) | 4.1.6.RELEASE |
| HTTP server | Embedded Tomcat (via `spring-boot-starter-web`) | managed by parent |
| Operations | Spring Boot Actuator | 1.2.3.RELEASE |
| Utilities | Guava / Commons IO / Commons Codec / cglib-nodep | 18.0 / 2.4 / 1.10 / 3.1 |
| Logging | SLF4J + Logback | managed by parent |
| Test framework | Spock + Spring Test | 1.0-groovy-2.4 / 4.1.6.RELEASE |
| Build | Maven + Maven Wrapper | 3.9.8 / wrapper 3.3.2 |

## Deployment Model

- **Artifact**: single Spring Boot fat JAR (`spring-boot-starter-parent` supplies the repackage lifecycle).
- **Process model**: one JVM, embedded servlet container, started by `MainApplication.main`.
- **State**: fully stateless at the application tier — no session, no cache, no database. Scalability is horizontal behind any HTTP load balancer, with the caveat that files must be reachable by every instance (currently not true: the storage implementation reads a classpath resource).
- **Configuration**: no `application.properties`/`application.yml` is present; all behaviour comes from Spring Boot defaults plus `logback.xml`. Spring profiles (`test` / `!test`) select the `FileStorage` implementation.
- **No** Dockerfile, Kubernetes manifest, CloudFormation/CDK/Terraform, or CI pipeline definition exists in the repository.

## Key Architectural Decisions

| Decision | Where | Rationale / consequence |
|---|---|---|
| Ports-and-adapters around file access | `FileStorage`, `FilePointer` | Storage backend is swappable (filesystem today, object store tomorrow) without touching the web or caching logic |
| Profile-based implementation selection instead of mocking | `@Profile("!test")` on `FileSystemStorage`, `@Profile("test")` on `FileStorageStub` | Integration tests run against a real Spring context with a deterministic fixture |
| HTTP caching implemented in domain code rather than relying on a servlet filter | `ExistingFile.serveWithCaching` | Full control over `304` semantics for both `GET` and `HEAD`; the alternative (`ShallowEtagHeaderFilter`) would buffer entire responses, defeating streaming |
| Content-based strong ETag (SHA-512 of file bytes) | `FileSystemPointer:22` | Correct invalidation, but hashing the whole file on pointer construction is expensive for large files |
| Streaming with `InputStreamResource` + `RateLimiter` decorator | `ExistingFile.buildResource`, `ThrottlingInputStream` | Constant memory regardless of file size; per-request bandwidth cap of 1 MB/s |
| `HEAD` handled by returning a `null` body with full headers | `ExistingFile.resourceToReturn` | Avoids reading file content for metadata-only requests |
| Two URL forms with `301` redirect | `DownloadController.redirect` | Canonicalizes `/download/{uuid}` to a filename-bearing URL so browsers save a meaningful filename |

## Boundaries

- **Inbound**: HTTP only (`/download/**` plus auto-configured Actuator paths).
- **Outbound**: local filesystem reads only. No outbound network calls, no AWS SDK usage, no third-party APIs.
- **Trust boundary**: the public HTTP interface. No authentication or authorization exists at that boundary — see [../analysis/security-patterns.md](../analysis/security-patterns.md).
