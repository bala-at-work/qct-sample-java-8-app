# Patterns

[← Back to index](../README.md) · Related: [components.md](components.md) · [../analysis/code-metrics.md](../analysis/code-metrics.md)

## Architectural Patterns

| Pattern | Evidence | Assessment |
|---|---|---|
| **Layered / MVC** | `@RestController` web layer → `ExistingFile` domain → `FilePointer`/`FileStorage` resource layer | Clean, no layer skipping |
| **Hexagonal (ports & adapters)** | `FileStorage` and `FilePointer` are ports; `FileSystemStorage`/`FileSystemPointer` and `FileStorageStub` are adapters | Textbook application; makes the service storage-agnostic |
| **Monolith (single deployable)** | one Maven module, one JAR | Appropriate for the scope |
| **Stateless service** | no fields mutated across requests; no session/cache | Horizontally scalable |
| **REST / resource-oriented HTTP** | UUID-addressed resources, correct status codes (`200`, `301`, `304`, `404`), conditional requests | Strong HTTP semantics for a sample app |

## Design Patterns

| Pattern | Location | Notes |
|---|---|---|
| **Decorator** | `ThrottlingInputStream` wrapping any `InputStream` | Canonical `java.io` decorator; adds rate limiting transparently |
| **Strategy** (via method reference) | `ExistingFile.serveWithCaching(..., Function<FilePointer, ResponseEntity<Resource>>)` with `this::redirectDownload` / `this::serveDownload` | Removes duplicated caching logic between the two endpoints |
| **Adapter** | `FileSystemPointer` adapting `java.io.File` to `FilePointer`; `ExistingFile.toMediaType` adapting Guava `MediaType` to Spring `MediaType` | — |
| **Builder** | `ResponseEntity.status(...).eTag(...).contentLength(...).lastModified(...)` and `MoreObjects.toStringHelper` | Framework/library builders used fluently |
| **Null Object / Optional-as-absence** | `Optional<FilePointer>` from `FileStorage`, `Optional<MediaType>`, `Optional<String>`/`Optional<Date>` request headers | Avoids null checks throughout the request path |
| **Dependency Injection (constructor)** | `DownloadController(FileStorage)` with `@Autowired` | Fields `final`; no setter injection |
| **Test Double (stub)** | `FileStorageStub` selected by Spring profile | Profile-based substitution instead of a mocking framework |
| **Template Method override** | `Sha512ShallowEtagHeaderFilter.generateETagHeaderValue` | Overrides a protected hook on Spring's filter |
| **Immutable value object** | `ExistingFile`, `FileSystemPointer` (all fields `final`) | Thread-safe by construction |
| **Functional composition** | `Optional.map(...).orElseGet(...)` chains in `DownloadController` | Terse, no branching |

## Idioms Worth Noting

- **Static imports for HTTP constants** (`IF_NONE_MATCH`, `NOT_FOUND`, `MOVED_PERMANENTLY`, `ONE_MB`) keep the response code readable.
- **Content-addressed strong ETags** (SHA-512 of the body) rather than weak timestamp tags.
- **`HEAD` modelled as "same response, null body"** — one code path serves both verbs, with the body suppressed in `resourceToReturn`.

## Anti-Patterns and Smells

| # | Anti-pattern | Location | Severity |
|---|---|---|---|
| A1 | **Package squatting** — a class placed in `org.springframework.web.filter` to gain access to a protected framework method | `src/test/java/org/springframework/web/filter/Sha512ShallowEtagHeaderFilter.java` | Low |
| A2 | **Dead code / deprecated API** — `new Integer("1234")` assigned to an unused local; `Integer(String)` is deprecated for removal | `MainApplication.java:10` | Low |
| A3 | **Stub masquerading as production implementation** — `FileSystemStorage` ignores the requested UUID and always returns `logback.xml` | `FileSystemStorage.java:24-30` | Low (by design in a sample, High if deployed) |
| A4 | **Exception type abuse** — `IOException`/`URISyntaxException`/`FileNotFoundException` are all rethrown as `IllegalArgumentException`, which misrepresents I/O failures as caller errors and yields `500`-class behaviour with no diagnostics | `FileSystemPointer:27-29,36-38`; `ExistingFile:72-74` | Low |
| A5 | **Unchecked `getResource` result** — `getClass().getResource(...)` may return `null`, then `new File(resource.getFile())` NPEs | `FileSystemStorage:26-27`, `FileExamples:14-15` | Low |
| A6 | **Resource-lifecycle ambiguity** — the `InputStream` opened in `buildResource` is handed to Spring via `InputStreamResource`; closure depends on the framework, and no `try`/finally guards an early failure | `ExistingFile:90-95` | Low |
| A7 | **Eager whole-file hashing in a constructor** — O(file size) I/O plus SHA-512 on every pointer construction, i.e. on every request, with no caching | `FileSystemPointer:22` | Medium (performance) |
| A8 | **Dependency hygiene** — unused `commons-codec`; `groovy-all` and `cglib-nodep` at compile scope; `spring-test` declared twice; unused `spring.version` property that disagrees with the pinned versions | `pom.xml` | Low |
| A9 | **Plaintext `http://` repository** in the build | `pom.xml` `<repositories>` | High (supply chain) |
| A10 | **Test class/file name mismatch** — `DownloadControllerTest.groovy` declares `class DownloadControllerSpec`; with standard Surefire `*Test` include patterns this discrepancy is a discovery hazard | `src/test/groovy/.../DownloadControllerTest.groovy` | Low |
| A11 | **`@Override` omitted** on `FileStorageStub.findFile` | `FileStorageStub:20` | Low |

## Concurrency Model

- Request threads are supplied by embedded Tomcat; the application adds no threads, executors, or schedulers.
- Shared mutable state: none. `DownloadController` holds only a `final FileStorage`; both storage implementations are stateless.
- `ThrottlingInputStream` synchronizes `mark`/`reset` (inherited contract) and is confined to a single request thread; its Guava `RateLimiter` is created **per request** in `buildResource`, so the 1 MB/s cap is per download, not global — total egress scales with concurrent downloads.
- `RateLimiter.acquire` blocks the serving thread, so a slow/throttled client occupies a container thread for the duration of the transfer (thread-per-request exhaustion risk under many concurrent large downloads).
