# Program Structure

- [Back to README](../README.md)
- Siblings: [interfaces.md](interfaces.md) · [data-models.md](data-models.md) · [api-reference.md](api-reference.md) · [modules.md](modules.md)

## Table of Contents
1. [File Tree](#file-tree)
2. [Package Structure](#package-structure)
3. [Type Hierarchy](#type-hierarchy)
4. [Complete Type Reference](#complete-type-reference)
5. [Searchable Member Index](#searchable-member-index)
6. [Non-Source Files](#non-source-files)

## File Tree

```
/repo
├── pom.xml                                            Maven build, 19 dependency declarations
├── mvnw / mvnw.cmd                                    Maven wrapper scripts
├── .mvn/wrapper/maven-wrapper.properties              Maven 3.9.8, only-script distribution
├── README.md / LICENSE / CODE_OF_CONDUCT.md           Repository metadata
├── .DS_Store                                          Stray macOS artifact
├── ATXDocumentation/                                  This documentation
└── src
    ├── main
    │   ├── java/com/nurkiewicz/download
    │   │   ├── MainApplication.java                   13 lines  — Boot entry point
    │   │   ├── DownloadController.java                63 lines  — REST controller
    │   │   ├── ExistingFile.java                     121 lines  — HTTP response policy
    │   │   ├── ThrottlingInputStream.java             66 lines  — rate-limiting decorator
    │   │   ├── FileStorage.java                        8 lines  — lookup port
    │   │   ├── FilePointer.java                       26 lines  — file port
    │   │   ├── FileSystemStorage.java                 33 lines  — filesystem lookup adapter
    │   │   └── FileSystemPointer.java                 86 lines  — filesystem file adapter
    │   └── resources
    │       └── logback.xml                                      — logging config (also the served file)
    └── test
        ├── java/com/nurkiewicz/download
        │   ├── FileExamples.java                                — fixtures
        │   └── FileStorageStub.java                             — test storage adapter
        ├── java/org/springframework/web/filter
        │   └── Sha512ShallowEtagHeaderFilter.java               — unused filter in a Spring package
        ├── groovy/com/nurkiewicz/download
        │   └── DownloadControllerTest.groovy         176 lines  — Spock spec (class: DownloadControllerSpec)
        └── resources
            └── download.txt                                     — 6-byte fixture ("foobar")
```

## Package Structure

| Package | Source root | Types | Purpose |
|---|---|---|---|
| `com.nurkiewicz.download` | `src/main/java` | 8 | The entire application |
| `com.nurkiewicz.download` | `src/test/java` | 2 | Test fixtures and stub |
| `com.nurkiewicz.download` | `src/test/groovy` | 1 | Spock specification |
| `org.springframework.web.filter` | `src/test/java` | 1 | One class placed in a third-party package to access a protected member |

There is **no sub-package structure** — the application is flat. At 8 main types this is appropriate; introducing `web`/`domain`/`storage` sub-packages would make the existing port/adapter boundaries explicit if the codebase grows.

## Type Hierarchy

### Interfaces and implementations
```
FileStorage «interface»
├── FileSystemStorage          @Component @Profile("!test")   [main]
└── FileStorageStub            @Component @Profile("test")    [test]

FilePointer «interface»
└── FileSystemPointer                                         [main]

java.io.InputStream (JDK)
└── ThrottlingInputStream                                     [main]

org.springframework.web.filter.ShallowEtagHeaderFilter (Spring)
└── Sha512ShallowEtagHeaderFilter                             [test, unused]

spock.lang.Specification (Spock)
└── DownloadControllerSpec                                    [test]
```

### Standalone types (no supertype beyond `Object`)
```
MainApplication        @SpringBootApplication
DownloadController     @RestController @RequestMapping("/download")
ExistingFile
FileExamples           [test]
```

**Observations.** Inheritance depth never exceeds 1 beyond a framework or JDK base class. There are no abstract classes in the project, no generic type parameters declared by project types, and no sealed or nested types. Polymorphism is achieved entirely through the two port interfaces.

## Complete Type Reference

### `MainApplication` — `src/main/java/com/nurkiewicz/download/MainApplication.java`
| Property | Value |
|---|---|
| Declaration | `class MainApplication` (package-private) |
| Annotations | `@SpringBootApplication` |
| Fields | none |
| Constructors | implicit default |

| Member | Signature | Notes |
|---|---|---|
| `main` | `public static void main(String[] args)` | Declares `Integer temp = new Integer("1234");` (unread local, deprecated constructor) then calls `SpringApplication.run(MainApplication.class, args)` |

### `DownloadController` — `src/main/java/com/nurkiewicz/download/DownloadController.java`
| Property | Value |
|---|---|
| Declaration | `public class DownloadController` |
| Annotations | `@RestController`, `@RequestMapping("/download")` |
| Fields | `private final FileStorage storage` |

| Member | Signature | Mapping |
|---|---|---|
| constructor | `@Autowired public DownloadController(FileStorage storage)` | — |
| `redirect` | `public ResponseEntity<Resource> redirect(HttpMethod method, @PathVariable UUID uuid, @RequestHeader(IF_NONE_MATCH) Optional<String> requestEtagOpt, @RequestHeader(IF_MODIFIED_SINCE) Optional<Date> ifModifiedSinceOpt)` | `@RequestMapping(method = {GET, HEAD}, value = "/{uuid}")` |
| `download` | `public ResponseEntity<Resource> download(HttpMethod method, @PathVariable UUID uuid, @RequestHeader(IF_NONE_MATCH) Optional<String> requestEtagOpt, @RequestHeader(IF_MODIFIED_SINCE) Optional<Date> ifModifiedSinceOpt)` | `@RequestMapping(method = {GET, HEAD}, value = "/{uuid}/{filename}")` |
| `findExistingFile` | `private Optional<ExistingFile> findExistingFile(HttpMethod method, @PathVariable UUID uuid)` | — |

Both public handlers return `new ResponseEntity<>(NOT_FOUND)` via `orElseGet` when the UUID is unknown.

### `ExistingFile` — `src/main/java/com/nurkiewicz/download/ExistingFile.java`
| Property | Value |
|---|---|
| Declaration | `public class ExistingFile` |
| Annotations | none |
| Static fields | `private static final Logger log` |
| Instance fields | `private final HttpMethod method`, `private final FilePointer filePointer`, `private final UUID uuid` |

| Member | Signature | Returns |
|---|---|---|
| constructor | `public ExistingFile(HttpMethod method, FilePointer filePointer, UUID uuid)` | — |
| `redirect` | `public ResponseEntity<Resource> redirect(Optional<String> requestEtagOpt, Optional<Date> ifModifiedSinceOpt)` | 304 or 301 |
| `handle` | `public ResponseEntity<Resource> handle(Optional<String> requestEtagOpt, Optional<Date> ifModifiedSinceOpt)` | 304 or 200 |
| `serveWithCaching` | `private ResponseEntity<Resource> serveWithCaching(Optional<String>, Optional<Date>, Function<FilePointer, ResponseEntity<Resource>>)` | delegates |
| `cached` | `private boolean cached(Optional<String>, Optional<Date>)` | `matchingEtag \|\| notModifiedSince` |
| `redirectDownload` | `private ResponseEntity<Resource> redirectDownload(FilePointer)` | 301 + `Location`, body `null` |
| `serveDownload` | `private ResponseEntity<Resource> serveDownload(FilePointer)` | 200 |
| `resourceToReturn` | `private InputStreamResource resourceToReturn(FilePointer)` | resource for GET, `null` otherwise |
| `buildResource` | `private InputStreamResource buildResource(FilePointer)` | `InputStreamResource` over a `ThrottlingInputStream` with a fresh `RateLimiter.create(ONE_MB)` |
| `notModified` | `private ResponseEntity<Resource> notModified(FilePointer)` | 304, body `null` |
| `response` | `private ResponseEntity<Resource> response(FilePointer, HttpStatus, Resource)` | sets `ETag`, `Content-Length`, `Last-Modified`, optional `Content-Type` |
| `toMediaType` | `private MediaType toMediaType(com.google.common.net.MediaType)` | Spring `MediaType`, charset preserved when present |

### `ThrottlingInputStream` — `src/main/java/com/nurkiewicz/download/ThrottlingInputStream.java`
| Property | Value |
|---|---|
| Declaration | `public class ThrottlingInputStream extends InputStream` |
| Fields | `private final InputStream target`, `private final RateLimiter maxBytesPerSecond` |

| Member | Signature | Permits acquired |
|---|---|---|
| constructor | `public ThrottlingInputStream(InputStream target, RateLimiter maxBytesPerSecond)` | — |
| `read` | `public int read() throws IOException` | 1 |
| `read` | `public int read(byte[] b) throws IOException` | `b.length` |
| `read` | `public int read(byte[] b, int off, int len) throws IOException` | `len` |
| `skip` | `public long skip(long n) throws IOException` | none (pass-through) |
| `available` | `public int available() throws IOException` | none |
| `mark` | `public synchronized void mark(int readlimit)` | none |
| `reset` | `public synchronized void reset() throws IOException` | none |
| `markSupported` | `public boolean markSupported()` | none |
| `close` | `public void close() throws IOException` | none |

### `FileStorage` — `src/main/java/com/nurkiewicz/download/FileStorage.java`
| Property | Value |
|---|---|
| Declaration | `public interface FileStorage` |
| Members | `Optional<FilePointer> findFile(UUID uuid)` |

### `FilePointer` — `src/main/java/com/nurkiewicz/download/FilePointer.java`
| Property | Value |
|---|---|
| Declaration | `public interface FilePointer` |

| Member | Signature |
|---|---|
| `open` | `InputStream open()` |
| `getSize` | `long getSize()` |
| `getOriginalName` | `String getOriginalName()` |
| `getEtag` | `String getEtag()` |
| `getMediaType` | `Optional<com.google.common.net.MediaType> getMediaType()` |
| `matchesEtag` | `boolean matchesEtag(String requestEtag)` |
| `getLastModified` | `Instant getLastModified()` |
| `modifiedAfter` | `boolean modifiedAfter(Instant isModifiedSince)` |

### `FileSystemStorage` — `src/main/java/com/nurkiewicz/download/FileSystemStorage.java`
| Property | Value |
|---|---|
| Declaration | `public class FileSystemStorage implements FileStorage` |
| Annotations | `@Component`, `@Profile("!test")` |
| Javadoc | "Stub implementation that for any UUID returns `logback.xml` file. Replace with real implementation." |
| Static fields | `private static final Logger log` |

| Member | Signature | Behavior |
|---|---|---|
| `findFile` | `@Override public Optional<FilePointer> findFile(UUID uuid)` | Logs `"Downloading {}"`, resolves `getClass().getResource("/logback.xml")`, wraps it in `new File(resource.getFile())` and a `FileSystemPointer`, and returns `Optional.of(pointer)`. The `uuid` argument is used only for logging. |

### `FileSystemPointer` — `src/main/java/com/nurkiewicz/download/FileSystemPointer.java`
| Property | Value |
|---|---|
| Declaration | `public class FileSystemPointer implements FilePointer` |
| Fields | `private final File target`, `private final HashCode tag`, `private final MediaType mediaTypeOrNull` |

| Member | Signature | Implementation |
|---|---|---|
| constructor | `public FileSystemPointer(File target)` | `Files.hash(target, Hashing.sha512())` (Guava) and `java.nio.file.Files.probeContentType(target.toPath())`; `MediaType.parse(contentType)` or `null`; `IOException` → `IllegalArgumentException` |
| `open` | `@Override public InputStream open()` | `new BufferedInputStream(new FileInputStream(target))`; `FileNotFoundException` → `IllegalArgumentException` |
| `getSize` | `@Override public long getSize()` | `target.length()` |
| `getOriginalName` | `@Override public String getOriginalName()` | `target.getName()` |
| `getEtag` | `@Override public String getEtag()` | `"\"" + tag + "\""` |
| `getMediaType` | `@Override public Optional<MediaType> getMediaType()` | `Optional.ofNullable(mediaTypeOrNull)` |
| `toString` | `@Override public String toString()` | `MoreObjects.toStringHelper` with `target`, `originalName`, `size`, `tag`, `mediaType` |
| `matchesEtag` | `@Override public boolean matchesEtag(String requestEtag)` | `getEtag().equals(requestEtag)` |
| `getLastModified` | `@Override public Instant getLastModified()` | `Instant.ofEpochMilli(target.lastModified())` |
| `modifiedAfter` | `@Override public boolean modifiedAfter(Instant clientTime)` | `!clientTime.isBefore(getLastModified())` |

### `FileStorageStub` *(test)* — `src/test/java/com/nurkiewicz/download/FileStorageStub.java`
| Property | Value |
|---|---|
| Declaration | `public class FileStorageStub implements FileStorage` |
| Annotations | `@Component`, `@Profile("test")` |
| Static fields | `private static final Logger log` |

| Member | Signature | Behavior |
|---|---|---|
| `findFile` | `public Optional<FilePointer> findFile(UUID uuid)` | `Optional.of(TXT_FILE)` when `uuid.equals(TXT_FILE_UUID)`, else `Optional.empty()` |

### `FileExamples` *(test)* — `src/test/java/com/nurkiewicz/download/FileExamples.java`
| Member | Signature | Value |
|---|---|---|
| `TXT_FILE_UUID` | `public static final UUID` | `UUID.randomUUID()` — regenerated per JVM run |
| `TXT_FILE` | `public static final FilePointer` | `txtFile()` |
| `NOT_FOUND_UUID` | `public static final UUID` | `UUID.randomUUID()` |
| `txtFile` | `private static FileSystemPointer txtFile()` | `FileSystemPointer` over the classpath resource `/download.txt`, located via `FileStorageStub.class.getResource` |

### `Sha512ShallowEtagHeaderFilter` *(test, unused)* — `src/test/java/org/springframework/web/filter/Sha512ShallowEtagHeaderFilter.java`
| Property | Value |
|---|---|
| Declaration | `public class Sha512ShallowEtagHeaderFilter extends ShallowEtagHeaderFilter` |
| Package | `org.springframework.web.filter` — a third-party namespace, used to access a protected member |

| Member | Signature | Behavior |
|---|---|---|
| `generateETagHeaderValue` | `@Override protected String generateETagHeaderValue(byte[] bytes)` | `Hashing.sha512().hashBytes(bytes)`, returned quoted — replacing Spring's MD5 default |

Never registered as a bean or servlet filter.

### `DownloadControllerSpec` *(test)* — `src/test/groovy/com/nurkiewicz/download/DownloadControllerTest.groovy`
| Property | Value |
|---|---|
| Declaration | `class DownloadControllerSpec extends Specification` |
| Annotations | `@WebAppConfiguration`, `@ContextConfiguration(classes = [MainApplication])`, `@ActiveProfiles("test")` |
| Fields | `private MockMvc mockMvc`, `private static final String TEXT_FILE = '/download/' + FileExamples.TXT_FILE_UUID + '/file.txt'` |

| Member | Signature | Purpose |
|---|---|---|
| `setWebApplicationContext` | `@Autowired void setWebApplicationContext(WebApplicationContext wac)` | Builds `MockMvc` from the injected context |
| `toDateHeader` | `private static String toDateHeader(Instant)` | Formats an RFC 1123 date header value |
| *12 feature methods* | — | See [../migration/test-specifications.md](../migration/test-specifications.md) |

## Searchable Member Index

| Member | Declaring type | Kind |
|---|---|---|
| `available()` | `ThrottlingInputStream` | method |
| `buildResource(FilePointer)` | `ExistingFile` | private method |
| `cached(Optional, Optional)` | `ExistingFile` | private method |
| `close()` | `ThrottlingInputStream` | method |
| `download(...)` | `DownloadController` | endpoint handler |
| `findExistingFile(HttpMethod, UUID)` | `DownloadController` | private method |
| `findFile(UUID)` | `FileStorage`, `FileSystemStorage`, `FileStorageStub` | interface + impls |
| `generateETagHeaderValue(byte[])` | `Sha512ShallowEtagHeaderFilter` | protected override |
| `getEtag()` | `FilePointer`, `FileSystemPointer` | interface + impl |
| `getLastModified()` | `FilePointer`, `FileSystemPointer` | interface + impl |
| `getMediaType()` | `FilePointer`, `FileSystemPointer` | interface + impl |
| `getOriginalName()` | `FilePointer`, `FileSystemPointer` | interface + impl |
| `getSize()` | `FilePointer`, `FileSystemPointer` | interface + impl |
| `handle(Optional, Optional)` | `ExistingFile` | public method |
| `main(String[])` | `MainApplication` | entry point |
| `mark(int)` | `ThrottlingInputStream` | synchronized method |
| `markSupported()` | `ThrottlingInputStream` | method |
| `matchesEtag(String)` | `FilePointer`, `FileSystemPointer` | interface + impl |
| `modifiedAfter(Instant)` | `FilePointer`, `FileSystemPointer` | interface + impl |
| `notModified(FilePointer)` | `ExistingFile` | private method |
| `open()` | `FilePointer`, `FileSystemPointer` | interface + impl |
| `read()` / `read(byte[])` / `read(byte[],int,int)` | `ThrottlingInputStream` | overrides |
| `redirect(...)` | `DownloadController` | endpoint handler |
| `redirect(Optional, Optional)` | `ExistingFile` | public method |
| `redirectDownload(FilePointer)` | `ExistingFile` | private method |
| `reset()` | `ThrottlingInputStream` | synchronized method |
| `resourceToReturn(FilePointer)` | `ExistingFile` | private method |
| `response(FilePointer, HttpStatus, Resource)` | `ExistingFile` | private method |
| `serveDownload(FilePointer)` | `ExistingFile` | private method |
| `serveWithCaching(...)` | `ExistingFile` | private method |
| `setWebApplicationContext(WebApplicationContext)` | `DownloadControllerSpec` | test setup |
| `skip(long)` | `ThrottlingInputStream` | method |
| `toDateHeader(Instant)` | `DownloadControllerSpec` | test helper |
| `toMediaType(com.google.common.net.MediaType)` | `ExistingFile` | private method |
| `toString()` | `FileSystemPointer` | override |
| `txtFile()` | `FileExamples` | private static factory |

## Non-Source Files

| File | Role |
|---|---|
| `pom.xml` | Build definition: Boot 1.2.3 parent, 19 dependency declarations, `maven-compiler-plugin` 3.0 at Java 1.8, one plain-HTTP milestone repository |
| `src/main/resources/logback.xml` | Logback configuration — console appender, pattern `%d{HH:mm:ss.SSS} \| %thread \| %-5level \| %logger{1} \| %msg%n`; loggers `com`=INFO, `com.nurkiewicz`=ALL, `net`=INFO, `org`=INFO; root DEBUG. Also the file returned by `FileSystemStorage` for every UUID |
| `src/test/resources/download.txt` | 6-byte fixture containing `foobar`; backs `FileExamples.TXT_FILE` |
| `.mvn/wrapper/maven-wrapper.properties` | `wrapperVersion=3.3.2`, `distributionType=only-script`, Maven 3.9.8 |
| `mvnw`, `mvnw.cmd` | Maven wrapper launchers |
| `README.md` | Two lines; identifies the repository as a transformation sample |
| `LICENSE`, `CODE_OF_CONDUCT.md` | Legal and community metadata |
| `.DS_Store` | macOS artifact committed in error |
