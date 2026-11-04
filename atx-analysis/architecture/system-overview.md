# System Overview

- [Back to README](../README.md) · [project-overview.md](../project-overview.md)
- Siblings: [components.md](components.md) · [dependencies.md](dependencies.md) · [patterns.md](patterns.md)
- Diagrams: [../diagrams/architecture.md](../diagrams/architecture.md) · [../diagrams/structural.md](../diagrams/structural.md)

## Table of Contents
1. [Architectural Summary](#architectural-summary)
2. [Layered View](#layered-view)
3. [Technology Stack](#technology-stack)
4. [Runtime Model](#runtime-model)
5. [Deployment Model](#deployment-model)
6. [Key Architectural Decisions](#key-architectural-decisions)
7. [Cross-Cutting Concerns](#cross-cutting-concerns)
8. [Quality Attributes](#quality-attributes)

## Architectural Summary

`download-server` is a **single-module, stateless Spring Boot web application** implementing one capability: serving files over HTTP with correct caching, redirect and throttling semantics. It follows a **ports-and-adapters (hexagonal)** arrangement with a very small surface:

- **Inbound adapter** — `DownloadController` (Spring MVC `@RestController`)
- **Domain / protocol logic** — `ExistingFile` (HTTP response policy), `ThrottlingInputStream` (bandwidth policy)
- **Outbound ports** — `FileStorage` (locate a file by UUID), `FilePointer` (read a file's bytes and metadata)
- **Outbound adapters** — `FileSystemStorage`, `FileSystemPointer` (local filesystem/classpath)

There is **no persistence layer, no messaging, no caching tier, and no outbound service client**. The entire system is request-scoped and holds no mutable state between requests.

## Layered View

```
┌──────────────────────────────────────────────────────────────┐
│ HTTP Client (browser, curl, download manager)                │
└───────────────────────────────┬──────────────────────────────┘
                                │ HTTP/1.1  GET | HEAD
┌───────────────────────────────▼──────────────────────────────┐
│ Embedded Tomcat  (Spring Boot 1.2.3 default)                 │
│   DispatcherServlet → handler mapping → argument resolution  │
└───────────────────────────────┬──────────────────────────────┘
                                │
┌───────────────────────────────▼──────────────────────────────┐
│ WEB ADAPTER                                                  │
│   DownloadController   @RestController @RequestMapping(…)     │
│   • binds HttpMethod, @PathVariable UUID,                     │
│     @RequestHeader If-None-Match / If-Modified-Since          │
│   • 404 when the UUID is unknown                              │
└───────────────────────────────┬──────────────────────────────┘
                                │ new ExistingFile(method, pointer, uuid)
┌───────────────────────────────▼──────────────────────────────┐
│ PROTOCOL / POLICY LAYER                                       │
│   ExistingFile          → 304 / 301 / 200 decision tree       │
│   ThrottlingInputStream → 1 MB/s Guava RateLimiter            │
└───────────────────────────────┬──────────────────────────────┘
                                │ FilePointer / FileStorage (ports)
┌───────────────────────────────▼──────────────────────────────┐
│ STORAGE ADAPTER                                               │
│   FileSystemStorage  @Component @Profile("!test")             │
│   FileSystemPointer  SHA-512 ETag, probeContentType           │
└───────────────────────────────┬──────────────────────────────┘
                                │ java.io / java.nio
┌───────────────────────────────▼──────────────────────────────┐
│ Local filesystem / classpath                                  │
└──────────────────────────────────────────────────────────────┘
```

Dependency direction is strictly inward-to-port: the controller depends on `FileStorage`, never on `FileSystemStorage`. No class in the policy layer imports a filesystem type.

## Technology Stack

| Concern | Choice | Version | Notes |
|---|---|---|---|
| Language | Java | 8 | `maven-compiler-plugin` source/target `1.8` |
| Application framework | Spring Boot | 1.2.3.RELEASE | Parent POM; EOL |
| Web | Spring Web MVC | 4.1.6.RELEASE | Explicitly pinned, overriding the Boot BOM |
| HTTP server | Embedded Tomcat | Boot-managed (8.0.x) | Default; not configured |
| Operations | Spring Boot Actuator | 1.2.3.RELEASE | Default endpoints, unconfigured |
| Rate limiting | Guava `RateLimiter` | 18.0 | 1 MB/s per request |
| Hashing | Guava `Hashing.sha512` | 18.0 | Content-addressed ETags |
| Content type | Guava `MediaType` + `java.nio.file.Files.probeContentType` | 18.0 / JDK | Probe-based detection |
| Constants | Apache Commons IO `FileUtils.ONE_MB` | 2.4 | Single constant only |
| Time | `java.time.Instant` | JDK 8 | Domain-side time type |
| Logging | SLF4J API + Logback | Boot-managed | `src/main/resources/logback.xml` |
| Tests | Spock + Groovy | 1.0-groovy-2.4 / 2.4.3 | MockMvc-driven |
| Build | Maven (wrapper 3.9.8) | — | Single module |

## Runtime Model

**Concurrency.** The threading model is entirely Tomcat's connector thread pool; the application creates no threads, executors, or schedulers. All application classes are either stateless singletons (`DownloadController`, `FileSystemStorage`) or request-scoped immutables (`ExistingFile`, `FileSystemPointer`, `ThrottlingInputStream`), so there is no shared mutable state and no locking in application code.

The only synchronization in the codebase is on `ThrottlingInputStream.mark(int)` and `reset()`, which are `synchronized` purely to match the `InputStream` contract — each instance is confined to one request thread in practice.

**Throttling semantics.** A fresh `RateLimiter.create(ONE_MB)` is created **per response** inside `ExistingFile.buildResource`. The 1 MB/s limit is therefore **per concurrent download, not global**: N simultaneous downloads consume up to N MB/s. `RateLimiter.acquire` blocks the Tomcat worker thread, so a slow or large download occupies a request thread for its full duration — a scalability characteristic worth noting for capacity planning.

**Statelessness.** No session is used, no `@SessionAttributes`, no server-side cache. Any instance can serve any request, which makes the application trivially horizontally scalable (subject to the storage adapter being shared).

**Streaming.** Responses are `InputStreamResource` wrappers, so file bytes are streamed rather than buffered in memory. The one exception is ETag computation, which reads the whole file eagerly in `FileSystemPointer`'s constructor before any byte is written to the response.

## Deployment Model

**Observed artifacts:** none. There is no Dockerfile, no Kubernetes manifest, no CloudFormation/SAM/CDK/Terraform, no CI pipeline, and no `application.properties`/`application.yml`.

**Implied model:** a Spring Boot executable JAR, run as `java -jar download-server-0.0.1-SNAPSHOT.jar`, serving on the Boot 1.x default port 8080 with an embedded Tomcat. Configuration would have to be supplied entirely via command-line arguments or environment variables, since no configuration file exists.

**Profiles:** the `test` profile selects `FileStorageStub`; any other profile (including none) selects `FileSystemStorage`. This is the only environment-dependent behavior in the system.

See [../specialized/infrastructure-and-deployment.md](../specialized/infrastructure-and-deployment.md) for detail.

## Key Architectural Decisions

| # | Decision | Rationale (as evidenced by the code) | Consequence |
|---|---|---|---|
| 1 | Abstract storage behind `FileStorage`/`FilePointer` | Keep the HTTP layer independent of where bytes live | Swapping to S3-backed storage requires one new adapter pair and no controller change |
| 2 | Content-addressed ETags via SHA-512 of file contents | ETag changes if and only if content changes — strictly correct caching | Full file must be hashed; cost is O(file size) per `FilePointer` construction |
| 3 | Concentrate all status/header policy in `ExistingFile` | One place decides 304 vs 301 vs 200 | `ExistingFile` is the single point of impact for Spring MVC upgrades |
| 4 | Two-URL scheme: opaque `/{uuid}` redirecting to `/{uuid}/{filename}` | Clients get a canonical, filename-bearing URL that saves with the right name | Costs an extra round trip on first access; `filename` is decorative and not validated against the stored name |
| 5 | Throttle via an `InputStream` decorator | Rate limiting is orthogonal to HTTP and to storage | Blocks the serving thread; limit is per-download rather than global |
| 6 | `HttpMethod` as a handler argument instead of separate GET/HEAD methods | One handler implements both verbs; HEAD differs only in body suppression | Header computation is guaranteed identical between GET and HEAD |
| 7 | Profile-based `FileStorage` bean selection (`@Profile("!test")` / `@Profile("test")`) | Guarantees exactly one candidate bean | Couples production bean definition to the existence of a test profile |
| 8 | Pin Spring Framework explicitly despite the Boot parent BOM | No rationale evident in the code | Upgrading the Boot parent will not move Spring Framework; see [../technical-debt/outdated-components.md](../technical-debt/outdated-components.md#4-explicit-spring-version-pinning) |
| 9 | Storage left as an explicit stub | The project is a reference implementation | Not deployable as a real service without implementing `FileStorage` |

## Cross-Cutting Concerns

| Concern | Implementation | Assessment |
|---|---|---|
| Logging | SLF4J facade, Logback backend, config in `logback.xml`. `ExistingFile` logs at `trace`/`debug`; `FileSystemStorage` holds a logger. Root level `DEBUG`, `com.nurkiewicz` at `ALL`, console appender. | Verbose defaults for production; levels are not environment-switchable without a config file |
| Error handling | `DownloadController` maps a missing UUID to `404`. `ExistingFile` rewraps `URISyntaxException` as `IllegalArgumentException`; `FileSystemPointer` rewraps `IOException` as `IllegalArgumentException`. No `@ControllerAdvice`. | Unhandled exceptions fall through to Boot's default error handling. Rewrapping I/O faults as argument faults conflates caller error with environment failure |
| Validation | Implicit only: Spring's `UUID` converter rejects a malformed UUID path segment with `400`. No `@Valid`, no explicit validation logic. | The `{filename}` path segment is accepted without any check — see [../analysis/security-patterns.md](../analysis/security-patterns.md) |
| Security | None. No Spring Security dependency, no authentication, no authorization, no CSRF (irrelevant for read-only GET/HEAD), no TLS configuration. | Both endpoints and all actuator endpoints are anonymous |
| Configuration | None externalized. No properties/YAML file; all values are literals or framework defaults. | Cannot be tuned per environment |
| Observability | Actuator defaults only (`/health`, `/info`, `/metrics`, `/trace`, … under Boot 1.2.x). No custom metrics, no tracing, no structured logging. | Minimal; unsecured |
| Transactions | Not applicable — no datastore | — |
| Caching | HTTP-level only (ETag / Last-Modified). No server-side cache. | Correct and well implemented |

## Quality Attributes

| Attribute | Current state |
|---|---|
| **Modularity** | Strong for its size. Four of eight main classes are framework-agnostic; ports isolate storage. |
| **Testability** | Design is highly testable — `FileStorageStub` substitutes the whole storage layer via a profile, and MockMvc exercises the real controller. Undermined only by the Groovy source root not being registered in the build. |
| **Scalability** | Horizontally scalable (stateless), but thread-per-download blocking throttle limits vertical density. |
| **Performance** | Streaming responses are memory-efficient; eager full-file SHA-512 hashing per request is the clear hotspot. |
| **Security** | Weak: no authentication, unsecured actuator, EOL framework with no patch path, plain-HTTP artifact repository. |
| **Maintainability** | Code is clean, small, immutable and readable. The burden is version currency, not structure. |
| **Operability** | Poor: no configuration file, no container, no CI, no IaC, verbose default logging. |
| **Completeness** | Incomplete by design — storage is a stub, and no upload path exists. |
