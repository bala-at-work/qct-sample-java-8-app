# Patterns

[← Back to index](../README.md) · [System overview](system-overview.md)

## Architectural Patterns

### Hexagonal Architecture (Ports and Adapters)
- **Ports:** `FileStorage` (driven port for file lookup), `FilePointer` (value/resource abstraction).
- **Adapters:** `FileSystemStorage` + `FileSystemPointer` (production), `FileStorageStub` (test).
- **Evidence of discipline:** neither port references a Spring type. The only third-party type in their signatures is Guava's `MediaType`.
- **Assessment:** correctly applied and the codebase's main strength — it is what makes the storage backend replaceable and the test suite mock-free.

### Layered Architecture
Three layers with strictly downward dependencies: delivery (`DownloadController`) → domain (`ExistingFile`, `ThrottlingInputStream`) → ports → adapters. No upward or lateral dependencies exist.

### MVC (REST variant)
Spring WebMVC with `@RestController`. There is no view layer — responses are `ResponseEntity<Resource>` carrying raw bytes and headers rather than rendered or serialized models.

### Profile-Based Composition
`@Profile("!test")` / `@Profile("test")` select the storage adapter at context-startup time. This is dependency injection used as a test seam, avoiding a mocking framework entirely while still exercising the real controller and real caching logic.

### Stateless Service
No session state, no caches, no instance mutable fields in the request path. `DownloadController`'s only field is `final`. Enables unconstrained horizontal scaling.

## Design Patterns

| Pattern | Participants | Location | Notes |
|---|---|---|---|
| **Decorator** | `ThrottlingInputStream` wraps any `InputStream` | `ThrottlingInputStream.java` | Textbook application: same type, added behavior, all nine methods delegated. Note `skip` is intentionally un-throttled. |
| **Strategy (via method reference)** | `ExistingFile.serveWithCaching(..., Function<FilePointer, ResponseEntity<Resource>>)` with `this::redirectDownload` / `this::serveDownload` | `ExistingFile.java:46-52` | The caching check is written once; the two divergent behaviors are injected as functions. The cleanest construct in the codebase. |
| **Adapter** | `FileSystemPointer` adapts `java.io.File` to `FilePointer`; `ExistingFile.toMediaType` adapts Guava `MediaType` to Spring `MediaType` | `FileSystemPointer.java`, `ExistingFile.java:124` | — |
| **Null Object / absence encoding** | `Optional<FilePointer>`, `Optional<MediaType>`, `Optional<String>`, `Optional<Date>` throughout | all layers | "Not found" is a value, never an exception. |
| **Factory Method** | `FileExamples.txtFile()`, `RateLimiter.create(...)` | test + `ExistingFile.java:99` | — |
| **Immutable Object** | `FileSystemPointer` (3 `final` fields), `ExistingFile` (3 `final` fields), `ThrottlingInputStream` (2 `final` fields) | — | All domain objects are immutable; thread-safety follows for free. |
| **Dependency Injection (constructor)** | `DownloadController(FileStorage)` | `DownloadController.java:28` | No field or setter injection anywhere in production code. |
| **Tell, Don't Ask** | `FilePointer.matchesEtag(String)`, `FilePointer.modifiedAfter(Instant)` | `FilePointer.java` | Comparison logic lives with the data rather than in `ExistingFile`. |
| **Template Method (inherited)** | `Sha512ShallowEtagHeaderFilter` overrides the protected hook `generateETagHeaderValue` | test scope | Participates in Spring's filter template — but is never registered. |
| **Specification (test)** | Spock `given/expect` blocks | `DownloadControllerTest.groovy` | 12 behavior-named specifications. |

## Functional Programming Idioms

The codebase consistently prefers `Optional` chaining over imperative branching:

- `DownloadController.redirect`/`download`: `findExistingFile(...).map(...).orElseGet(...)` — zero `if` statements in the controller.
- `ExistingFile.cached`: two independent `map(...).orElse(false)` chains combined with `||`.
- `ExistingFile.response`: `getMediaType().map(this::toMediaType).ifPresent(responseBuilder::contentType)` — conditional header set without a branch.
- Method references used in preference to lambdas wherever possible (`filePointer::matchesEtag`, `Date::toInstant`, `this::redirectDownload`).

This is a deliberate and consistently applied style.

## Concurrency and Threading Patterns

| Aspect | Observation |
|---|---|
| Threading model | Inherited from the servlet container — thread-per-request. No application-managed threads, executors, or async handlers. |
| Shared mutable state | None in production code. All domain objects are immutable. |
| Synchronization | Only the two `synchronized` overrides in `ThrottlingInputStream` (`mark`, `reset`), which merely match the `InputStream` superclass contract. No application locks. |
| Rate limiter scope | A **new** `RateLimiter` is created per request inside `ExistingFile.buildResource`. The 1 MB/s cap is therefore **per download**, not global or per-client — N concurrent downloads consume up to N MB/s. This may be the intended semantics, but it is not stated anywhere. |
| Blocking | `RateLimiter.acquire(n)` blocks the servlet thread for the duration of the throttle delay. Under concurrent slow downloads, container threads are held idle and the thread pool becomes the scaling limit. A reactive or `StreamingResponseBody`-based approach would avoid this. |
| Thread-safety of statics | `FileExamples` holds static non-final-initialized fixtures initialized once at class load — safe, test-only. |

## Error Handling Patterns

| Pattern | Location | Assessment |
|---|---|---|
| Wrap checked exception in `IllegalArgumentException` | `FileSystemPointer` constructor (`IOException`), `FileSystemPointer.open()` (`FileNotFoundException`), `ExistingFile.redirectDownload` (`URISyntaxException`) | Consistent, but `IllegalArgumentException` is semantically wrong for an IO failure — it will surface as `500`, and `IllegalStateException` or a domain exception would be more accurate. |
| Absence as `Optional.empty()` → `404` | `FileStorage.findFile` → `DownloadController.orElseGet(() -> new ResponseEntity<>(NOT_FOUND))` | Clean; no exception-driven control flow. |
| No global exception handler | — | There is no `@ControllerAdvice`/`@ExceptionHandler`. Any adapter failure becomes a default Boot error response. |

## Anti-Patterns Identified

| Anti-pattern | Location | Severity | Detail |
|---|---|---|---|
| **Stub masquerading as production code** | `FileSystemStorage` | High | The `@Profile("!test")` bean — the one active in production — ignores its input and always returns `logback.xml`. Its own Javadoc says "Replace with real implementation." |
| **Version pinning that fights the parent BOM** | `pom.xml` | High | Nine Spring artifacts and three Boot starters carry hardcoded `<version>` elements that override `spring-boot-starter-parent`, plus an unused `<spring.version>` property set to a third, different value. |
| **Insecure transport for dependency resolution** | `pom.xml` `<repositories>` | High | `http://repo.spring.io/milestone/` over plaintext HTTP. |
| **Package squatting** | `src/test/java/org/springframework/web/filter/Sha512ShallowEtagHeaderFilter.java` | Low | A project class placed inside a third-party package to reach a `protected` member. Breaks under JPMS, fragile against upstream changes, and the class is never registered or referenced. |
| **Dead code with deprecated API** | `MainApplication.java:10` | Low | `Integer temp = new Integer("1234");` — unused local via a constructor deprecated since Java 9. |
| **Hardcoded magic configuration** | `ExistingFile.java:99` | Low | `RateLimiter.create(ONE_MB)` — a policy decision buried in a private method with no way to tune it per environment. |
| **God-ish class (mild)** | `ExistingFile` | Low | Five concerns in one class: cache decision, redirect construction, response assembly, throttling setup, and media-type conversion. Still only ~130 LOC, so the smell is mild rather than acute. |
| **Unsafe `File` from classpath URL** | `FileSystemStorage.java:27`, `FileExamples.java:15` | Low | `new File(resource.getFile())` fails for resources inside a JAR (the normal Boot packaging) and does not URL-decode the path. Tolerable in the test fixture, incorrect in production code. |
| **Unused dependencies** | `pom.xml` | Medium | `commons-codec` has no source usage at all; `spring-aop`, `spring-expression`, `spring-context-support`, and `cglib-nodep` have no direct references. |
| **Dependency on a transitive artifact** | SLF4J | Low | `org.slf4j` is imported directly by three classes but never declared in `pom.xml` — it arrives transitively via `spring-boot-starter-logging`. |
| **Filename/class mismatch** | `DownloadControllerTest.groovy` declares `DownloadControllerSpec` | Low | Prevents the Groovy source from compiling as written. |

## Patterns Notably Absent

- No caching abstraction (`@Cacheable`) — content hashes are recomputed on every request.
- No security layer: no Spring Security, no authentication, no authorization, no CSRF protection, no input validation beyond the framework's UUID type conversion.
- No circuit breakers, retries, bulkheads, or timeouts — consistent with having no outbound calls.
- No API versioning strategy on the `/download` paths.
- No observability beyond unconfigured Actuator and SLF4J logging — no metrics, no tracing, no structured logging.

See [../analysis/security-patterns.md](../analysis/security-patterns.md) and [../analysis/complexity-analysis.md](../analysis/complexity-analysis.md).
