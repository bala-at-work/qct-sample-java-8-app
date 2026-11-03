# Program Structure

[← Back to index](../README.md)

Complete structural hierarchy of the repository. Line references point at declaration sites in the source as analyzed.

## Repository Tree

```
/repo
├── pom.xml                                 Maven build (single module)
├── mvnw / mvnw.cmd / .mvn/wrapper/         Maven Wrapper
├── README.md / LICENSE / CODE_OF_CONDUCT.md
├── ATXDocumentation/                       this documentation set
└── src
    ├── main
    │   ├── java/com/nurkiewicz/download/
    │   │   ├── MainApplication.java
    │   │   ├── DownloadController.java
    │   │   ├── ExistingFile.java
    │   │   ├── FileStorage.java            «interface»
    │   │   ├── FilePointer.java            «interface»
    │   │   ├── FileSystemStorage.java
    │   │   ├── FileSystemPointer.java
    │   │   └── ThrottlingInputStream.java
    │   └── resources/
    │       └── logback.xml
    └── test
        ├── groovy/com/nurkiewicz/download/
        │   └── DownloadControllerTest.groovy      (class DownloadControllerSpec)
        ├── java/com/nurkiewicz/download/
        │   ├── FileExamples.java
        │   └── FileStorageStub.java
        ├── java/org/springframework/web/filter/
        │   └── Sha512ShallowEtagHeaderFilter.java
        └── resources/
            └── download.txt                       fixture content: "foobar"
```

## Package `com.nurkiewicz.download` (main)

### `MainApplication` — class, package-private, `final` fields: none
```java
@SpringBootApplication
class MainApplication
```
| Member | Signature | Visibility | Notes |
|---|---|---|---|
| `main` | `static void main(String[] args)` | public | Declares local `Integer temp = new Integer("1234")` (unused, deprecated API) then calls `SpringApplication.run(MainApplication.class, args)` |

### `DownloadController` — class, public
```java
@RestController
@RequestMapping("/download")
public class DownloadController
```
| Member | Signature | Visibility |
|---|---|---|
| `storage` | `private final FileStorage` | private |
| constructor | `DownloadController(FileStorage storage)` — `@Autowired` | public |
| `redirect` | `ResponseEntity<Resource> redirect(HttpMethod method, @PathVariable UUID uuid, @RequestHeader(IF_NONE_MATCH) Optional<String> requestEtagOpt, @RequestHeader(IF_MODIFIED_SINCE) Optional<Date> ifModifiedSinceOpt)` — `@RequestMapping(method={GET,HEAD}, value="/{uuid}")` | public |
| `download` | `ResponseEntity<Resource> download(HttpMethod method, @PathVariable UUID uuid, @RequestHeader(IF_NONE_MATCH) Optional<String> requestEtagOpt, @RequestHeader(IF_MODIFIED_SINCE) Optional<Date> ifModifiedSinceOpt)` — `@RequestMapping(method={GET,HEAD}, value="/{uuid}/{filename}")` | public |
| `findExistingFile` | `Optional<ExistingFile> findExistingFile(HttpMethod method, UUID uuid)` | private |

Static imports: `HttpHeaders.IF_MODIFIED_SINCE`, `HttpHeaders.IF_NONE_MATCH`, `HttpStatus.NOT_FOUND`, `RequestMethod.GET`, `RequestMethod.HEAD`.

### `ExistingFile` — class, public
```java
public class ExistingFile
```
| Member | Signature | Visibility |
|---|---|---|
| `log` | `private static final Logger` | private |
| `method` | `private final HttpMethod` | private |
| `filePointer` | `private final FilePointer` | private |
| `uuid` | `private final UUID` | private |
| constructor | `ExistingFile(HttpMethod method, FilePointer filePointer, UUID uuid)` | public |
| `redirect` | `ResponseEntity<Resource> redirect(Optional<String> requestEtagOpt, Optional<Date> ifModifiedSinceOpt)` | public |
| `handle` | `ResponseEntity<Resource> handle(Optional<String> requestEtagOpt, Optional<Date> ifModifiedSinceOpt)` | public |
| `serveWithCaching` | `ResponseEntity<Resource> serveWithCaching(Optional<String>, Optional<Date>, Function<FilePointer, ResponseEntity<Resource>> notCachedResponse)` | private |
| `cached` | `boolean cached(Optional<String> requestEtagOpt, Optional<Date> ifModifiedSinceOpt)` | private |
| `redirectDownload` | `ResponseEntity<Resource> redirectDownload(FilePointer filePointer)` | private |
| `serveDownload` | `ResponseEntity<Resource> serveDownload(FilePointer filePointer)` | private |
| `resourceToReturn` | `InputStreamResource resourceToReturn(FilePointer filePointer)` | private |
| `buildResource` | `InputStreamResource buildResource(FilePointer filePointer)` | private |
| `notModified` | `ResponseEntity<Resource> notModified(FilePointer filePointer)` | private |
| `response` | `ResponseEntity<Resource> response(FilePointer filePointer, HttpStatus status, Resource body)` | private |
| `toMediaType` | `org.springframework.http.MediaType toMediaType(com.google.common.net.MediaType input)` | private |

Static imports: `FileUtils.ONE_MB`, `HttpStatus.*` (uses `MOVED_PERMANENTLY`, `OK`, `NOT_MODIFIED`).

### `FileStorage` — interface, public
```java
public interface FileStorage {
    Optional<FilePointer> findFile(UUID uuid);
}
```

### `FilePointer` — interface, public
```java
public interface FilePointer
```
| Method | Signature |
|---|---|
| `open` | `InputStream open()` |
| `getSize` | `long getSize()` |
| `getOriginalName` | `String getOriginalName()` |
| `getEtag` | `String getEtag()` |
| `getMediaType` | `Optional<com.google.common.net.MediaType> getMediaType()` |
| `matchesEtag` | `boolean matchesEtag(String requestEtag)` |
| `getLastModified` | `Instant getLastModified()` |
| `modifiedAfter` | `boolean modifiedAfter(Instant isModifiedSince)` |

### `FileSystemStorage` — class, public, implements `FileStorage`
```java
@Component
@Profile("!test")
public class FileSystemStorage implements FileStorage
```
| Member | Signature | Notes |
|---|---|---|
| `log` | `private static final Logger` | — |
| `findFile` | `@Override Optional<FilePointer> findFile(UUID uuid)` | Ignores `uuid`; resolves `/logback.xml` from the classpath and returns a `FileSystemPointer` over it. Carries a 4-line Javadoc marking the class a stub to be replaced — preserved verbatim in source. |

### `FileSystemPointer` — class, public, implements `FilePointer`
```java
public class FileSystemPointer implements FilePointer
```
| Member | Signature | Notes |
|---|---|---|
| `target` | `private final File` | — |
| `tag` | `private final HashCode` | SHA-512 of file content |
| `mediaTypeOrNull` | `private final com.google.common.net.MediaType` | nullable; from `java.nio.file.Files.probeContentType` |
| constructor | `FileSystemPointer(File target)` | Eagerly hashes content and probes content type; wraps `IOException` in `IllegalArgumentException` |
| `open` | `@Override InputStream open()` | Returns `new BufferedInputStream(new FileInputStream(target))`; wraps `FileNotFoundException` |
| `getSize` | `@Override long getSize()` | `target.length()` |
| `getOriginalName` | `@Override String getOriginalName()` | `target.getName()` |
| `getEtag` | `@Override String getEtag()` | `"\"" + tag + "\""` |
| `getMediaType` | `@Override Optional<MediaType> getMediaType()` | `Optional.ofNullable(mediaTypeOrNull)` |
| `toString` | `@Override String toString()` | Guava `MoreObjects.toStringHelper` with target, originalName, size, tag, mediaType |
| `matchesEtag` | `@Override boolean matchesEtag(String requestEtag)` | `getEtag().equals(requestEtag)` |
| `getLastModified` | `@Override Instant getLastModified()` | `Instant.ofEpochMilli(target.lastModified())` |
| `modifiedAfter` | `@Override boolean modifiedAfter(Instant clientTime)` | `!clientTime.isBefore(getLastModified())` |

### `ThrottlingInputStream` — class, public, extends `java.io.InputStream`
```java
public class ThrottlingInputStream extends InputStream
```
| Member | Signature | Throttled |
|---|---|---|
| `target` | `private final InputStream` | — |
| `maxBytesPerSecond` | `private final RateLimiter` | — |
| constructor | `ThrottlingInputStream(InputStream target, RateLimiter maxBytesPerSecond)` | — |
| `read` | `@Override int read()` | yes — `acquire(1)` |
| `read` | `@Override int read(byte[] b)` | yes — `acquire(b.length)` |
| `read` | `@Override int read(byte[] b, int off, int len)` | yes — `acquire(len)` |
| `skip` | `@Override long skip(long n)` | no |
| `available` | `@Override int available()` | no |
| `mark` | `@Override synchronized void mark(int readlimit)` | no |
| `reset` | `@Override synchronized void reset()` | no |
| `markSupported` | `@Override boolean markSupported()` | no |
| `close` | `@Override void close()` | no |

## Package `com.nurkiewicz.download` (test)

### `FileExamples` — class, public
| Member | Signature | Notes |
|---|---|---|
| `TXT_FILE_UUID` | `public static final UUID` | `UUID.randomUUID()` — new per JVM run |
| `TXT_FILE` | `public static final FilePointer` | `FileSystemPointer` over `/download.txt` |
| `NOT_FOUND_UUID` | `public static final UUID` | `UUID.randomUUID()` |
| `txtFile` | `private static FileSystemPointer txtFile()` | Resolves `/download.txt` via `FileStorageStub.class.getResource` |

### `FileStorageStub` — class, public, implements `FileStorage`
```java
@Component
@Profile("test")
public class FileStorageStub implements FileStorage
```
| Member | Signature | Notes |
|---|---|---|
| `log` | `private static final Logger` | — |
| `findFile` | `Optional<FilePointer> findFile(UUID uuid)` | Returns `TXT_FILE` for `TXT_FILE_UUID`, else `Optional.empty()`. **Missing `@Override`** (TD-15). |

### `DownloadControllerSpec` — Spock `Specification`
File: `src/test/groovy/com/nurkiewicz/download/DownloadControllerTest.groovy`
```groovy
@WebAppConfiguration
@ContextConfiguration(classes = [MainApplication])
@ActiveProfiles("test")
class DownloadControllerSpec extends Specification
```
| Member | Kind |
|---|---|
| `mockMvc` | `private MockMvc` |
| `TEXT_FILE` | `private static final String` = `'/download/' + FileExamples.TXT_FILE_UUID + '/file.txt'` |
| `setWebApplicationContext(WebApplicationContext wac)` | `@Autowired` setter building `MockMvc` via `MockMvcBuilders.webAppContextSetup` |
| 12 feature methods | see [../migration/test-specifications.md](../migration/test-specifications.md) |
| `toDateHeader(Instant)` | `private static String` — RFC 1123 formatting at UTC |

## Package `org.springframework.web.filter` (test)

### `Sha512ShallowEtagHeaderFilter` — class, public, extends `ShallowEtagHeaderFilter`
| Member | Signature | Notes |
|---|---|---|
| `generateETagHeaderValue` | `@Override protected String generateETagHeaderValue(byte[] bytes)` | Returns quoted `Hashing.sha512().hashBytes(bytes)`. Never registered; dead code (TD-17). |

## Type Summary

| Kind | Count | Names |
|---|---|---|
| Interfaces (main) | 2 | `FileStorage`, `FilePointer` |
| Classes (main) | 6 | `MainApplication`, `DownloadController`, `ExistingFile`, `FileSystemStorage`, `FileSystemPointer`, `ThrottlingInputStream` |
| Classes (test, Java) | 3 | `FileExamples`, `FileStorageStub`, `Sha512ShallowEtagHeaderFilter` |
| Classes (test, Groovy) | 1 | `DownloadControllerSpec` |
| Enums / annotations / records | 0 | — |

See [interfaces.md](interfaces.md), [data-models.md](data-models.md), [api-reference.md](api-reference.md), [modules.md](modules.md).
