# Components

[← Back to index](../README.md) · [System overview](system-overview.md)

## Component Inventory

| Component | Type | Scope | Spring role | Source |
|---|---|---|---|---|
| `MainApplication` | class (package-private) | main | `@SpringBootApplication` | `src/main/java/com/nurkiewicz/download/MainApplication.java` |
| `DownloadController` | class (public) | main | `@RestController`, `@RequestMapping("/download")` | `DownloadController.java` |
| `ExistingFile` | class (public) | main | plain object (manually constructed) | `ExistingFile.java` |
| `FileStorage` | interface (public) | main | injection point (port) | `FileStorage.java` |
| `FilePointer` | interface (public) | main | — (port) | `FilePointer.java` |
| `FileSystemStorage` | class (public) | main | `@Component`, `@Profile("!test")` | `FileSystemStorage.java` |
| `FileSystemPointer` | class (public) | main | plain object | `FileSystemPointer.java` |
| `ThrottlingInputStream` | class (public) | main | plain object | `ThrottlingInputStream.java` |
| `FileStorageStub` | class (public) | test | `@Component`, `@Profile("test")` | `src/test/java/.../FileStorageStub.java` |
| `FileExamples` | class (public) | test | static fixture holder | `src/test/java/.../FileExamples.java` |
| `Sha512ShallowEtagHeaderFilter` | class (public) | test | unregistered `ShallowEtagHeaderFilter` subclass | `src/test/java/org/springframework/web/filter/...` |
| `DownloadControllerSpec` | Spock `Specification` | test | `@WebAppConfiguration`, `@ContextConfiguration`, `@ActiveProfiles("test")` | `src/test/groovy/.../DownloadControllerTest.groovy` |

## Responsibilities and Interfaces

### `MainApplication`
- **Responsibility:** Spring Boot bootstrap. Declares component scanning and auto-configuration for the `com.nurkiewicz.download` package.
- **Public API:** `static void main(String[] args)`.
- **Collaborators:** `SpringApplication`.
- **Note:** Also serves as the `@ContextConfiguration` class for `DownloadControllerSpec` despite being package-private.

### `DownloadController`
- **Responsibility:** HTTP delivery layer. Maps `/download/**` requests, binds path variables and conditional-request headers, delegates all decision-making, and converts a missing file into `404`.
- **Public API:**
  - `DownloadController(FileStorage storage)` — constructor injection
  - `ResponseEntity<Resource> redirect(HttpMethod, UUID, Optional<String>, Optional<Date>)` — `GET|HEAD /download/{uuid}`
  - `ResponseEntity<Resource> download(HttpMethod, UUID, Optional<String>, Optional<Date>)` — `GET|HEAD /download/{uuid}/{filename}`
- **Private API:** `Optional<ExistingFile> findExistingFile(HttpMethod, UUID)` — the shared lookup that lifts a `FilePointer` into an `ExistingFile`.
- **Collaborators:** `FileStorage` (injected), `ExistingFile` (constructed per request).
- **Notes:** The `{filename}` path variable is intentionally not bound — it exists purely so browsers derive the correct save-as name. Both handlers are a `map`/`orElseGet` pair over `Optional`, so no conditional branching appears in the controller.

### `ExistingFile`
- **Responsibility:** The domain core. Owns the HTTP caching decision, the redirect-vs-serve decision, response header assembly, throttled body construction, and Guava→Spring `MediaType` conversion.
- **Public API:**
  - `ExistingFile(HttpMethod method, FilePointer filePointer, UUID uuid)`
  - `ResponseEntity<Resource> redirect(Optional<String> requestEtagOpt, Optional<Date> ifModifiedSinceOpt)`
  - `ResponseEntity<Resource> handle(Optional<String> requestEtagOpt, Optional<Date> ifModifiedSinceOpt)`
- **Private API:** `serveWithCaching`, `cached`, `redirectDownload`, `serveDownload`, `resourceToReturn`, `buildResource`, `notModified`, `response`, `toMediaType`.
- **Collaborators:** `FilePointer`, `ThrottlingInputStream`, Guava `RateLimiter`, Spring `ResponseEntity`/`InputStreamResource`.
- **Notes:** `redirect` and `handle` are the two public entry points; both funnel through `serveWithCaching`, which takes the not-cached behavior as a `Function<FilePointer, ResponseEntity<Resource>>`. This is the Strategy pattern applied via method references (`this::redirectDownload`, `this::serveDownload`) and is the cleanest construct in the codebase. Highest-complexity component — see [../technical-debt/maintenance-burden.md](../technical-debt/maintenance-burden.md).

### `FileStorage` (port)
- **Responsibility:** Resolve a UUID to a file, if one exists.
- **API:** `Optional<FilePointer> findFile(UUID uuid)`.
- **Implementations:** `FileSystemStorage` (production), `FileStorageStub` (test).
- **Notes:** Framework-free. `Optional` return encodes "not found" without exceptions.

### `FilePointer` (port)
- **Responsibility:** Abstract a stored file's content stream and the metadata needed for HTTP responses and cache validation.
- **API:** `open()`, `getSize()`, `getOriginalName()`, `getEtag()`, `getMediaType()`, `matchesEtag(String)`, `getLastModified()`, `modifiedAfter(Instant)`.
- **Implementations:** `FileSystemPointer`.
- **Notes:** The only non-JDK type in the signature set is Guava's `MediaType`. `matchesEtag` and `modifiedAfter` place comparison logic in the pointer rather than the caller — tell-don't-ask.

### `FileSystemStorage` (adapter, production)
- **Responsibility:** Nominally the production `FileStorage`.
- **API:** `Optional<FilePointer> findFile(UUID uuid)` (`@Override`).
- **Notes:** **A stub.** Ignores `uuid` and returns a pointer to the classpath resource `/logback.xml` on every call. Documented as such in its own Javadoc. Never returns `Optional.empty()`, so the `404` path is unreachable in production. High-severity finding TD-05.

### `FileSystemPointer` (adapter)
- **Responsibility:** `FilePointer` over a `java.io.File`.
- **API:** the eight `FilePointer` methods, plus `toString()` via Guava `MoreObjects`.
- **Collaborators:** Guava `Files.hash`/`Hashing`/`HashCode`/`MediaType`/`MoreObjects`, `java.nio.file.Files.probeContentType`.
- **Notes:** The constructor eagerly computes the SHA-512 content hash and probes the content type, wrapping `IOException` in `IllegalArgumentException`. `getEtag()` returns the hash wrapped in quotes (a strong validator). `modifiedAfter(clientTime)` returns `!clientTime.isBefore(getLastModified())` — i.e. true when the client's copy is as new as, or newer than, the server's. Immutable: all three fields are `final`.

### `ThrottlingInputStream` (decorator)
- **Responsibility:** Cap read throughput on a wrapped `InputStream`.
- **API:** overrides all of `read()`, `read(byte[])`, `read(byte[],int,int)`, `skip`, `available`, `mark`, `reset`, `markSupported`, `close`.
- **Collaborators:** wrapped `InputStream`, Guava `RateLimiter`.
- **Notes:** Textbook Decorator. The three `read` overloads call `maxBytesPerSecond.acquire(n)` before delegating; the remaining methods are pass-through. `acquire` blocks the calling (servlet) thread. `skip` is **not** throttled — bytes skipped bypass the rate limit, though they also are not transferred.

### Test components

| Component | Responsibility |
|---|---|
| `FileStorageStub` | `@Profile("test")` `FileStorage` returning `FileExamples.TXT_FILE` for `TXT_FILE_UUID` and `Optional.empty()` otherwise — making the `404` path testable. Missing `@Override` (TD-15). |
| `FileExamples` | Static fixtures: `TXT_FILE_UUID`, `NOT_FOUND_UUID` (both random per JVM run), and `TXT_FILE`, a `FileSystemPointer` over `src/test/resources/download.txt` (content `foobar`). |
| `Sha512ShallowEtagHeaderFilter` | Overrides `generateETagHeaderValue` to emit a quoted SHA-512. Registered nowhere, referenced by nothing — dead code in a squatted package (TD-17). |
| `DownloadControllerSpec` | 12 `MockMvc` specifications covering body, `404`, ETag matching/mismatch, `If-Modified-Since` in three variants, `HEAD` status and empty body, `HEAD` with `304`, `Content-Length`, and `Content-Type`. |

## Interaction Summary

```
HTTP request
   │
   ▼
DownloadController ──findFile(uuid)──▶ FileStorage ──▶ FileSystemPointer
   │                                  (FileSystemStorage │ FileStorageStub)
   │ new ExistingFile(method, pointer, uuid)
   ▼
ExistingFile ──cached()?──▶ FilePointer.matchesEtag / modifiedAfter
   │                  ├─ yes ─▶ 304 Not Modified (no body)
   │                  └─ no  ─┬─ redirect()  ─▶ 301 + Location header
   │                          └─ handle()    ─▶ 200 + body
   │                                              │
   │                                              ▼
   │                            FilePointer.open() ─▶ ThrottlingInputStream
   │                                              ─▶ InputStreamResource
   ▼
ResponseEntity<Resource>
```

See [../diagrams/structural.md](../diagrams/structural.md) and [../diagrams/behavioral.md](../diagrams/behavioral.md) for full diagrams, and [../reference/api-reference.md](../reference/api-reference.md) for the complete callable surface.
