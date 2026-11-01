# Program Structure

[← Back to index](../README.md) · Related: [modules.md](modules.md) · [interfaces.md](interfaces.md) · [api-reference.md](api-reference.md)

## File Tree (analysis scope)

```
/repo
├── pom.xml
├── mvnw / mvnw.cmd
├── .mvn/wrapper/maven-wrapper.properties
├── README.md / LICENSE / CODE_OF_CONDUCT.md
└── src
    ├── main
    │   ├── java/com/nurkiewicz/download
    │   │   ├── MainApplication.java          (13 lines)
    │   │   ├── DownloadController.java       (63 lines)
    │   │   ├── ExistingFile.java            (121 lines)
    │   │   ├── FileStorage.java               (8 lines)
    │   │   ├── FileSystemStorage.java        (33 lines)
    │   │   ├── FilePointer.java              (26 lines)
    │   │   ├── FileSystemPointer.java        (86 lines)
    │   │   └── ThrottlingInputStream.java    (67 lines)
    │   └── resources/logback.xml             (15 lines)
    └── test
        ├── java/com/nurkiewicz/download
        │   ├── FileStorageStub.java          (27 lines)
        │   └── FileExamples.java             (20 lines)
        ├── java/org/springframework/web/filter
        │   └── Sha512ShallowEtagHeaderFilter.java (13 lines)
        ├── groovy/com/nurkiewicz/download
        │   └── DownloadControllerTest.groovy (176 lines)
        └── resources/download.txt
```

## Type Hierarchy

```
java.lang.Object
├── MainApplication                         [package-private, @SpringBootApplication]
├── DownloadController                      [public, @RestController]
├── ExistingFile                            [public, final-field immutable]
├── FileSystemPointer         implements FilePointer
├── FileSystemStorage         implements FileStorage    [@Component @Profile("!test")]
├── FileStorageStub           implements FileStorage    [@Component @Profile("test"), test]
├── FileExamples                            [test fixtures, static only]
└── java.io.InputStream
    └── ThrottlingInputStream

javax.servlet.Filter
└── ... org.springframework.web.filter.ShallowEtagHeaderFilter
        └── Sha512ShallowEtagHeaderFilter   [test tree]

spock.lang.Specification
└── DownloadControllerSpec                  [Groovy, test]

Interfaces
├── FileStorage   { findFile }
└── FilePointer   { open, getSize, getOriginalName, getEtag,
                    getMediaType, matchesEtag, getLastModified, modifiedAfter }
```

## Member Inventory

### `MainApplication` — `MainApplication.java`
| Member | Signature | Line |
|---|---|---|
| `main` | `public static void main(String[] args)` | 9 |

### `DownloadController` — `DownloadController.java`
| Member | Signature | Line |
|---|---|---|
| `storage` | `private final FileStorage` | 26 |
| constructor | `public DownloadController(FileStorage storage)` | 29 |
| `redirect` | `public ResponseEntity<Resource> redirect(HttpMethod, UUID, Optional<String>, Optional<Date>)` | 34 |
| `download` | `public ResponseEntity<Resource> download(HttpMethod, UUID, Optional<String>, Optional<Date>)` | 46 |
| `findExistingFile` | `private Optional<ExistingFile> findExistingFile(HttpMethod, UUID)` | 57 |

### `ExistingFile` — `ExistingFile.java`
| Member | Signature | Line |
|---|---|---|
| `log` | `private static final Logger` | 26 |
| `method` | `private final HttpMethod` | 28 |
| `filePointer` | `private final FilePointer` | 29 |
| `uuid` | `private final UUID` | 30 |
| constructor | `public ExistingFile(HttpMethod, FilePointer, UUID)` | 32 |
| `redirect` | `public ResponseEntity<Resource> redirect(Optional<String>, Optional<Date>)` | 38 |
| `handle` | `public ResponseEntity<Resource> handle(Optional<String>, Optional<Date>)` | 42 |
| `serveWithCaching` | `private ResponseEntity<Resource> serveWithCaching(Optional<String>, Optional<Date>, Function<FilePointer, ResponseEntity<Resource>>)` | 46 |
| `cached` | `private boolean cached(Optional<String>, Optional<Date>)` | 54 |
| `redirectDownload` | `private ResponseEntity<Resource> redirectDownload(FilePointer)` | 65 |
| `serveDownload` | `private ResponseEntity<Resource> serveDownload(FilePointer)` | 77 |
| `resourceToReturn` | `private InputStreamResource resourceToReturn(FilePointer)` | 83 |
| `buildResource` | `private InputStreamResource buildResource(FilePointer)` | 90 |
| `notModified` | `private ResponseEntity<Resource> notModified(FilePointer)` | 97 |
| `response` | `private ResponseEntity<Resource> response(FilePointer, HttpStatus, Resource)` | 102 |
| `toMediaType` | `private MediaType toMediaType(com.google.common.net.MediaType)` | 115 |

### `FileStorage` — `FileStorage.java`
| Member | Signature | Line |
|---|---|---|
| `findFile` | `Optional<FilePointer> findFile(UUID uuid)` | 7 |

### `FileSystemStorage` — `FileSystemStorage.java`
| Member | Signature | Line |
|---|---|---|
| `log` | `private static final Logger` | 21 |
| `findFile` | `public Optional<FilePointer> findFile(UUID uuid)` `@Override` | 24 |

### `FilePointer` — `FilePointer.java`
| Member | Signature | Line |
|---|---|---|
| `open` | `InputStream open()` | 11 |
| `getSize` | `long getSize()` | 13 |
| `getOriginalName` | `String getOriginalName()` | 15 |
| `getEtag` | `String getEtag()` | 17 |
| `getMediaType` | `Optional<MediaType> getMediaType()` | 19 |
| `matchesEtag` | `boolean matchesEtag(String requestEtag)` | 21 |
| `getLastModified` | `Instant getLastModified()` | 23 |
| `modifiedAfter` | `boolean modifiedAfter(Instant isModifiedSince)` | 25 |

### `FileSystemPointer` — `FileSystemPointer.java`
| Member | Signature | Line |
|---|---|---|
| `target` | `private final File` | 15 |
| `tag` | `private final HashCode` | 16 |
| `mediaTypeOrNull` | `private final MediaType` (Guava) | 17 |
| constructor | `public FileSystemPointer(File target)` | 19 |
| `open` | `public InputStream open()` | 33 |
| `getSize` | `public long getSize()` | 42 |
| `getOriginalName` | `public String getOriginalName()` | 47 |
| `getEtag` | `public String getEtag()` | 52 |
| `getMediaType` | `public Optional<MediaType> getMediaType()` | 57 |
| `toString` | `public String toString()` | 62 |
| `matchesEtag` | `public boolean matchesEtag(String)` | 73 |
| `getLastModified` | `public Instant getLastModified()` | 78 |
| `modifiedAfter` | `public boolean modifiedAfter(Instant)` | 83 |

### `ThrottlingInputStream` — `ThrottlingInputStream.java`
| Member | Signature | Line |
|---|---|---|
| `target` | `private final InputStream` | 10 |
| `maxBytesPerSecond` | `private final RateLimiter` | 11 |
| constructor | `public ThrottlingInputStream(InputStream, RateLimiter)` | 13 |
| `read` | `public int read()` | 19 |
| `read` | `public int read(byte[] b)` | 25 |
| `read` | `public int read(byte[] b, int off, int len)` | 31 |
| `skip` | `public long skip(long n)` | 39 |
| `available` | `public int available()` | 44 |
| `mark` | `public synchronized void mark(int readlimit)` | 49 |
| `reset` | `public synchronized void reset()` | 54 |
| `markSupported` | `public boolean markSupported()` | 59 |
| `close` | `public void close()` | 64 |

### Test types
| Type | Members | File |
|---|---|---|
| `FileStorageStub` | `log` (18), `findFile(UUID)` (20) | `FileStorageStub.java` |
| `FileExamples` | `TXT_FILE_UUID` (9), `TXT_FILE` (10), `NOT_FOUND_UUID` (11), `txtFile()` private static (13) | `FileExamples.java` |
| `Sha512ShallowEtagHeaderFilter` | `generateETagHeaderValue(byte[])` protected override (9) | `Sha512ShallowEtagHeaderFilter.java` |
| `DownloadControllerSpec` | `mockMvc` (27), `TEXT_FILE` (29), `setWebApplicationContext(WebApplicationContext)` (32), 12 feature methods (36–169), `toDateHeader(Instant)` (171) | `DownloadControllerTest.groovy` |

## Static Imports Used (readability dependencies)

| Import | Consumer |
|---|---|
| `org.springframework.http.HttpHeaders.IF_MODIFIED_SINCE`, `.IF_NONE_MATCH` | `DownloadController` |
| `org.springframework.http.HttpStatus.NOT_FOUND` | `DownloadController` |
| `org.springframework.web.bind.annotation.RequestMethod.GET`, `.HEAD` | `DownloadController` |
| `org.apache.commons.io.FileUtils.ONE_MB` | `ExistingFile` |
| `org.springframework.http.HttpStatus.*` (`OK`, `NOT_MODIFIED`, `MOVED_PERMANENTLY`) | `ExistingFile` |
| `com.nurkiewicz.download.FileExamples.TXT_FILE`, `.TXT_FILE_UUID` | `FileStorageStub` |
| `com.google.common.net.HttpHeaders.*`, MockMvc builders/matchers | `DownloadControllerSpec` |
