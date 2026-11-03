# Patterns

- [Back to README](../README.md)
- Siblings: [system-overview.md](system-overview.md) · [components.md](components.md) · [dependencies.md](dependencies.md)
- See also: [../analysis/complexity-analysis.md](../analysis/complexity-analysis.md) · [../technical-debt/summary.md](../technical-debt/summary.md)

## Table of Contents
1. [Architectural Patterns](#architectural-patterns)
2. [Design Patterns](#design-patterns)
3. [Idioms and Conventions](#idioms-and-conventions)
4. [HTTP Protocol Patterns](#http-protocol-patterns)
5. [Anti-Patterns and Smells](#anti-patterns-and-smells)
6. [Patterns Deliberately Absent](#patterns-deliberately-absent)

## Architectural Patterns

### Ports and Adapters (Hexagonal) — strongly present
The dominant pattern. Two outbound **ports** are defined as interfaces in the application package, and the filesystem **adapters** that implement them are the only classes aware of storage mechanics.

| Role | Type |
|---|---|
| Inbound adapter (driving) | `DownloadController` |
| Application/protocol core | `ExistingFile`, `ThrottlingInputStream` |
| Outbound ports (driven) | `FileStorage`, `FilePointer` |
| Outbound adapters | `FileSystemStorage`, `FileSystemPointer` |
| Test adapters | `FileStorageStub`, `FileExamples` |

**Evidence of correctness.** `DownloadController` imports `FileStorage`, never `FileSystemStorage`. `ExistingFile` imports `FilePointer`, never `FileSystemPointer`. No `java.io.File` import exists outside the two `FileSystem*` classes. The test suite replaces the entire storage hexagon side by activating a profile — the clearest possible demonstration that the ports are real seams rather than nominal interfaces.

### Layered Architecture — present
Web adapter → protocol policy → storage port → storage adapter, with dependencies pointing in one direction only. Thin, but consistently observed.

### Model-View-Controller — partially applicable
Spring MVC supplies the controller and dispatch infrastructure, but there is no view layer: responses are raw byte streams and headers via `@RestController`, not rendered representations. The "model" is the `FilePointer` port.

### Microservice / single-responsibility service — present
One module, one capability (file download), no shared database, stateless, independently deployable. The application is service-shaped even though no deployment tooling exists to deploy it.

### Convention over Configuration — present
Spring Boot auto-configuration supplies the entire runtime: embedded server, dispatcher, message converters, actuator endpoints. The project adds no `@Configuration` class and no `application.properties`. Taken to an extreme here — see the anti-patterns section.

## Design Patterns

| Pattern | Where | How it is applied |
|---|---|---|
| **Decorator** | `ThrottlingInputStream extends InputStream` wrapping a target `InputStream` | Adds rate limiting transparently; every method either acquires permits then delegates, or delegates directly. The canonical textbook form. |
| **Strategy** (as a lambda/method reference) | `ExistingFile.serveWithCaching(..., Function<FilePointer, ResponseEntity<Resource>> notCachedResponse)` called with `this::redirectDownload` or `this::serveDownload` | The cache-check logic is written once; the "what to do when not cached" branch is passed in. This is why `redirect()` and `handle()` share identical 304 semantics with zero duplication. |
| **Repository / Finder** | `FileStorage.findFile(UUID) : Optional<FilePointer>` | Collection-like lookup abstraction returning an `Optional` rather than null or an exception. |
| **Adapter** | `FileSystemPointer` adapting `java.io.File` to `FilePointer`; `ExistingFile.toMediaType` adapting Guava `MediaType` to Spring `MediaType` | Both are genuine interface translations. |
| **Factory Method** | `FileSystemStorage.findFile` creating `FileSystemPointer`; `FileExamples.txtFile()` | Encapsulates construction of the adapter type. |
| **Null Object (via `Optional`)** | `Optional<FilePointer>`, `Optional<MediaType>`, `Optional<String>`, `Optional<Date>` | Absence is modelled explicitly at every boundary; there is no null-check-then-branch code in the request path. |
| **Template Method** | `Sha512ShallowEtagHeaderFilter` overriding the protected `generateETagHeaderValue` hook | Correct use of Spring's extension point — but the class is dead code, and the hook's signature has since changed. |
| **Value Object** | `ExistingFile` (`method` + `filePointer` + `uuid`, all `final`) | A short-lived immutable binding of request context to resource, created per request. |
| **Dependency Injection** | `DownloadController`'s `@Autowired` constructor with a `final` field | Constructor injection, not field injection — the preferred form, and it makes the controller trivially unit-constructible. |
| **Stub / Test Double** | `FileStorageStub` | Substitutes the whole storage side via `@Profile`. |

## Idioms and Conventions

| Idiom | Evidence | Assessment |
|---|---|---|
| **Immutability by default** | Every field in `ExistingFile`, `FileSystemPointer`, `ThrottlingInputStream` and `DownloadController` is `final` | Consistent and thread-safety-relevant |
| **`Optional` chaining over conditionals** | `findExistingFile(...).map(file -> file.handle(...)).orElseGet(() -> new ResponseEntity<>(NOT_FOUND))` | Idiomatic Java 8; the 404 path is expressed declaratively |
| **Single header-building funnel** | `notModified`, `serveDownload` and the 301 path all converge on `response(FilePointer, HttpStatus, Resource)` | Guarantees `ETag`/`Content-Length`/`Last-Modified` consistency across 200, 304 and 301 |
| **Body-null for HEAD** | `resourceToReturn` returns `null` for non-GET methods | Elegant: one handler, identical headers, body suppressed by type rather than by branching the whole flow |
| **Static imports for readability** | `import static ...HttpStatus.*`, `...HttpHeaders.IF_NONE_MATCH`, `...FileUtils.ONE_MB`, `...RequestMethod.GET` | Improves signature legibility |
| **Freshness logic on the port** | `matchesEtag` and `modifiedAfter` are `FilePointer` methods, not comparisons in `ExistingFile` | Lets each adapter define its own ETag semantics — good extensibility for an S3 adapter |
| **Package-private where possible** | `MainApplication` | Minimal public surface |
| **Tabs for indentation** | All Java sources | Internally consistent |
| **Specification-style tests** | Spock `given/when/then` blocks with descriptive feature-method names | Readable executable documentation of the HTTP contract |

## HTTP Protocol Patterns

The application's real subject matter. These are implemented to a higher standard than most hand-written download endpoints.

| Pattern | Implementation |
|---|---|
| **Conditional GET (ETag)** | `If-None-Match` → `FilePointer.matchesEtag` → `304 Not Modified` with the ETag echoed back |
| **Conditional GET (time)** | `If-Modified-Since` → `Date::toInstant` → `FilePointer.modifiedAfter` → `304` |
| **Content-addressed ETag** | SHA-512 over full file contents, quoted. The ETag changes if and only if the bytes change — stronger than the mtime+size heuristics commonly used |
| **Canonical URL redirect** | `301 Moved Permanently` from `/download/{uuid}` to `/download/{uuid}/{originalName}` so clients save the file under a meaningful name |
| **HEAD as metadata query** | Identical headers to GET, empty body; implemented by returning a `null` resource |
| **Streaming response** | `InputStreamResource` — bytes are not buffered in memory |
| **Server-side throttling** | Guava `RateLimiter` at 1 MB/s, applied per response via an `InputStream` decorator |
| **Opaque identifiers** | `java.util.UUID` as the public addressing scheme; Spring's converter rejects malformed UUIDs with `400` |
| **Either-or cache validation** | `cached()` returns `matchingEtag \|\| notModifiedSince` — a match on either validator is sufficient |

## Anti-Patterns and Smells

Severity uses **High / Medium / Low** only.

| # | Anti-pattern | Location | Severity | Why it matters |
|---|---|---|---|---|
| 1 | **Version pinning that defeats the BOM** — explicit Spring 4.1.6 pins under a Spring Boot parent | `pom.xml` | 🔴 High | Upgrading the Boot parent silently fails to move Spring Framework, producing an untested combination |
| 2 | **Package injection into a third-party namespace** — a project class declared in `org.springframework.web.filter` to reach a protected member | `Sha512ShallowEtagHeaderFilter` | 🟠 Medium | Couples to an internal signature that has since changed; invisible to audits of project packages |
| 3 | **Test-only dependencies at compile scope** — `groovy-all`, `cglib-nodep` | `pom.xml` | 🟠 Medium | Bloats the production artifact and widens the runtime attack surface |
| 4 | **Convention-over-configuration taken to zero configuration** — no `application.properties`/`yml` at all | project-wide | 🟠 Medium | Nothing can be tuned per environment; actuator exposure, port and log levels are all implicit |
| 5 | **Production bean defined in terms of a test profile** — `@Profile("!test")` on `FileSystemStorage` | `FileSystemStorage` | 🟡 Low | Inverts the dependency: production configuration now references a testing concept. Conventional alternative is a test `@Primary`/`@TestConfiguration` override |
| 6 | **Stub masquerading as an implementation** — `findFile` ignores its `uuid` and always returns `logback.xml` | `FileSystemStorage` | 🟠 Medium | The component satisfies the interface but not the contract; the application is non-functional outside the test profile |
| 7 | **Exception type laundering** — `IOException` and `URISyntaxException` rewrapped as `IllegalArgumentException` | `FileSystemPointer`, `ExistingFile.redirectDownload` | 🟡 Low | Conflates caller error with environment failure; callers cannot distinguish a bad argument from a disk fault |
| 8 | **Eager expensive work in a constructor** — full-file SHA-512 plus content-type probe in `FileSystemPointer(File)` | `FileSystemPointer` | 🟡 Low | Constructing a pointer performs I/O proportional to file size, even for a request that will end in a `304` |
| 9 | **Magic number** — `RateLimiter.create(ONE_MB)` hard-coded | `ExistingFile.buildResource` | 🟡 Low | The throttle rate is a policy decision baked into code |
| 10 | **Dead code** — unregistered filter, unread `Integer temp` local, unused `commons-codec`, unused `spring.version` property | several | 🟡 Low | Misleads readers and inflates the dependency set |
| 11 | **Duplicate dependency declaration** — `spring-test` declared twice | `pom.xml` | 🟡 Low | Maintenance trap; the two could drift to different versions |
| 12 | **Filename/class-name mismatch** — `DownloadControllerTest.groovy` declares `DownloadControllerSpec` | test source | 🟡 Low | Breaks navigation and naming-convention-based tooling |
| 13 | **Orphaned source root** — `src/test/groovy` not registered in the build | `pom.xml` | 🟠 Medium | The project's entire behavioral test suite does not execute; tests that exist but never run are worse than no tests, because they imply false assurance |
| 14 | **Insecure transport for dependency resolution** — `http://repo.spring.io/milestone/` | `pom.xml` | 🟠 Medium | Supply-chain exposure |
| 15 | **Thread-blocking rate limiter** — `RateLimiter.acquire` blocks the Tomcat worker for the download's duration | `ThrottlingInputStream` | 🟡 Low | A design trade-off rather than a defect, but it caps concurrent downloads at the connector thread-pool size |
| 16 | **Unvalidated decorative path variable** — `{filename}` is mapped but never bound or checked against `getOriginalName()` | `DownloadController.download` | 🟡 Low | Any filename serves any valid UUID, so the same content is reachable at unbounded distinct URLs — a cache-pollution and canonicalization concern. See [../analysis/security-patterns.md](../analysis/security-patterns.md) |

## Patterns Deliberately Absent

Recorded so their absence is not mistaken for an oversight. Each is genuinely unnecessary for this system's scope.

| Pattern | Why absent |
|---|---|
| Service layer | The controller-to-port path is short enough that an intermediate service would add no behavior |
| DTO / mapper layer | No serialized representations — responses are byte streams and headers |
| Repository with a datastore | No persistence tier exists |
| CQRS / event sourcing | Read-only, single-operation system |
| Circuit breaker / retry / bulkhead | No remote calls to protect |
| Caching abstraction (`@Cacheable`) | Caching is pushed to the HTTP client, which is the correct layer for static file content |
| Transaction management | No transactional resource |
| `@ControllerAdvice` global error handling | Only one error case (404) exists and the controller handles it inline; Boot's default error handling covers the rest |
| Security / authentication filter chain | None implemented — this is a gap, not a justified absence. See [../analysis/security-patterns.md](../analysis/security-patterns.md) |
| Asynchronous / reactive request handling | A plausible future improvement given the blocking throttle, but Spring 4.1/Boot 1.2 predates WebFlux |
