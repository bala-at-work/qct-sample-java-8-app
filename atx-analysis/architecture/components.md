# Components

[← Back to index](../README.md) · Related: [system-overview.md](system-overview.md) · [../reference/program-structure.md](../reference/program-structure.md)

## Production Components

### `MainApplication`
- **File**: `src/main/java/com/nurkiewicz/download/MainApplication.java` (13 lines)
- **Visibility**: package-private class, `public static void main`
- **Responsibility**: Spring Boot bootstrap. `@SpringBootApplication` enables component scanning over `com.nurkiewicz.download`, auto-configuration, and configuration-class semantics.
- **Interactions**: constructs the application context, which instantiates `DownloadController` and the profile-selected `FileStorage`.
- **Note**: line 10 allocates `new Integer("1234")` into an unused local — dead code.

### `DownloadController`
- **File**: `DownloadController.java` (63 lines)
- **Stereotype**: `@RestController`, `@RequestMapping("/download")`
- **Responsibility**: HTTP routing and request binding; translation of "file not found" to `404`.
- **Dependency**: `FileStorage` injected via constructor (`@Autowired`, line 28).
- **Public methods**:
  - `redirect(HttpMethod, UUID, Optional<String>, Optional<Date>)` → `ResponseEntity<Resource>` — `GET|HEAD /download/{uuid}`
  - `download(HttpMethod, UUID, Optional<String>, Optional<Date>)` → `ResponseEntity<Resource>` — `GET|HEAD /download/{uuid}/{filename}`
- **Private helper**: `findExistingFile(HttpMethod, UUID)` → `Optional<ExistingFile>` — the single place where a storage result is lifted into the domain object.

### `ExistingFile`
- **File**: `ExistingFile.java` (121 lines) — the behavioural core of the service
- **Responsibility**: decide and build the HTTP response for a file that is known to exist.
- **Collaborators**: `FilePointer` (data), `ThrottlingInputStream` + Guava `RateLimiter` (streaming), Spring `ResponseEntity` (output), Guava `MediaType` → Spring `MediaType` conversion.
- **Public API**: `redirect(...)`, `handle(...)`
- **Internal responsibilities**: `serveWithCaching` (branch), `cached` (conditional-request evaluation), `redirectDownload` (`301`), `serveDownload` (`200`), `resourceToReturn` (`GET` vs `HEAD`), `buildResource` (throttled stream), `notModified` (`304`), `response` (header population), `toMediaType` (type conversion).
- **Immutability**: all three fields (`method`, `filePointer`, `uuid`) are `final`; the object is per-request and thread-confined.

### `FileStorage` (interface)
- **File**: `FileStorage.java` (8 lines)
- **Contract**: `Optional<FilePointer> findFile(UUID uuid)` — must never return `null`; absence is expressed as `Optional.empty()`.
- **Implementations**: `FileSystemStorage` (production), `FileStorageStub` (tests).

### `FileSystemStorage`
- **File**: `FileSystemStorage.java` (33 lines)
- **Stereotype**: `@Component`, `@Profile("!test")`
- **Responsibility**: nominally the production adapter. **Actually a stub** — its Javadoc states it returns `logback.xml` for any UUID, and the implementation does exactly that by resolving the classpath resource `/logback.xml`.
- **Risk**: `getResource` can return `null` and `new File(null.getFile())` would NPE; also ignores the requested UUID entirely.

### `FilePointer` (interface)
- **File**: `FilePointer.java` (26 lines)
- **Contract**: 8 methods — `open()`, `getSize()`, `getOriginalName()`, `getEtag()`, `getMediaType()`, `matchesEtag(String)`, `getLastModified()`, `modifiedAfter(Instant)`.
- **Role**: the abstraction that lets `ExistingFile` serve any byte source with caching metadata.

### `FileSystemPointer`
- **File**: `FileSystemPointer.java` (86 lines)
- **Responsibility**: `FilePointer` over a `java.io.File`.
- **Behaviour**: eagerly computes a SHA-512 `HashCode` of the whole file and probes the content type in the constructor; wraps `IOException` in `IllegalArgumentException`. ETag is the quoted hash. `open()` returns a `BufferedInputStream` over a `FileInputStream`.
- **Cost**: constructor is O(file size) — see [../analysis/complexity-analysis.md](../analysis/complexity-analysis.md).

### `ThrottlingInputStream`
- **File**: `ThrottlingInputStream.java` (67 lines)
- **Responsibility**: decorator over an `InputStream` that acquires permits from a Guava `RateLimiter` proportional to the bytes requested, capping throughput.
- **Overrides**: all three `read` variants (rate-limited) plus `skip`, `available`, `mark`, `reset`, `markSupported`, `close` (plain delegation). `skip` is deliberately *not* throttled.

## Test Components

### `FileStorageStub`
- `@Component`, `@Profile("test")`; returns `FileExamples.TXT_FILE` for `TXT_FILE_UUID`, otherwise `Optional.empty()`. Note: it does not declare `@Override` on `findFile`.

### `FileExamples`
- Static fixtures: `TXT_FILE_UUID`, `TXT_FILE` (a `FileSystemPointer` over the classpath resource `/download.txt`, contents `foobar`), `NOT_FOUND_UUID`.

### `Sha512ShallowEtagHeaderFilter`
- Declared in package `org.springframework.web.filter` inside the test source tree so it can override the protected `generateETagHeaderValue(byte[])` of Spring's `ShallowEtagHeaderFilter`, replacing MD5 with Guava SHA-512. Not registered in any configuration — effectively unused demonstration code.

### `DownloadControllerSpec`
- Spock `Specification` in `DownloadControllerTest.groovy` (note the class/file name mismatch). `@WebAppConfiguration`, `@ContextConfiguration(classes = [MainApplication])`, `@ActiveProfiles("test")`, driving `MockMvc`. 12 feature methods — see [../migration/test-specifications.md](../migration/test-specifications.md).

## Interaction Summary

| Caller | Callee | Nature |
|---|---|---|
| Tomcat / Spring MVC | `DownloadController` | HTTP dispatch |
| `DownloadController` | `FileStorage` | UUID lookup (constructor-injected) |
| `DownloadController` | `ExistingFile` | instantiation per request |
| `ExistingFile` | `FilePointer` | metadata + `open()` |
| `ExistingFile` | `ThrottlingInputStream`, `RateLimiter` | body construction |
| `FileSystemPointer` | Guava `Hashing`/`Files`, `java.nio.file.Files` | ETag + content type |
| `ThrottlingInputStream` | wrapped `InputStream` | delegation |
