# API Reference

[← Back to index](../README.md) · [Program structure](program-structure.md) · [Interfaces](interfaces.md)

Complete catalog of callable surfaces: HTTP endpoints, declarative definitions, and public Java methods.

## HTTP Endpoints

Base path: `/download` (`@RequestMapping("/download")` on `DownloadController`).

### 1. Redirect to canonical download URL

| | |
|---|---|
| **Methods** | `GET`, `HEAD` |
| **Path** | `/download/{uuid}` |
| **Handler** | `DownloadController.redirect` → `ExistingFile.redirect` |
| **Path variables** | `uuid` — `java.util.UUID`; malformed values are rejected by Spring with `400` before the handler runs |
| **Request headers** | `If-None-Match` (optional), `If-Modified-Since` (optional, RFC 1123) |
| **Auth** | None |

Responses:

| Status | Condition | Headers | Body |
|---|---|---|---|
| `301 Moved Permanently` | File exists and is not cached client-side | `Location: /download/{uuid}/{originalName}` | empty |
| `304 Not Modified` | `If-None-Match` matches the content ETag, **or** `If-Modified-Since` indicates the client copy is current | `ETag`, `Content-Length`, `Last-Modified`, `Content-Type` (when known) | empty |
| `404 Not Found` | `FileStorage.findFile` returned `Optional.empty()` | — | empty |
| `400 Bad Request` | `uuid` is not a valid UUID | framework default | framework default |

Example:
```http
GET /download/3f1b2c84-9a6e-4d0b-8c11-7e2a5d4f9b03 HTTP/1.1

HTTP/1.1 301 Moved Permanently
Location: /download/3f1b2c84-9a6e-4d0b-8c11-7e2a5d4f9b03/logback.xml
```

Note: the `301` response is built with `.body(null)` and does **not** carry `ETag`/`Content-Length`/`Last-Modified`, unlike the `200` and `304` responses which go through `ExistingFile.response`.

### 2. Download file content

| | |
|---|---|
| **Methods** | `GET`, `HEAD` |
| **Path** | `/download/{uuid}/{filename}` |
| **Handler** | `DownloadController.download` → `ExistingFile.handle` |
| **Path variables** | `uuid` — `UUID`. `filename` — **declared in the mapping but never bound or validated**; it exists solely so browsers derive the correct save-as name. Any value is accepted and ignored. |
| **Request headers** | `If-None-Match` (optional), `If-Modified-Since` (optional, RFC 1123) |
| **Auth** | None |

Responses:

| Status | Condition | Headers | Body |
|---|---|---|---|
| `200 OK` | `GET`, file exists, not cached | `ETag`, `Content-Length`, `Last-Modified`, `Content-Type` (when probeable) | File bytes, throttled to 1 MB/s |
| `200 OK` | `HEAD`, file exists, not cached | same as above | empty (`resourceToReturn` returns `null` for non-`GET`) |
| `304 Not Modified` | ETag match or not-modified-since | `ETag`, `Content-Length`, `Last-Modified`, `Content-Type` | empty |
| `404 Not Found` | file not found | — | empty |
| `400 Bad Request` | malformed `uuid` | framework default | framework default |
| `500` | `FilePointer.open()` or URI construction fails (wrapped as `IllegalArgumentException`) | framework default | framework default |

Example — conditional request:
```http
GET /download/3f1b2c84-.../logback.xml HTTP/1.1
If-None-Match: "cf83e1357eefb8bd...<sha512>"

HTTP/1.1 304 Not Modified
ETag: "cf83e1357eefb8bd...<sha512>"
Content-Length: 412
Last-Modified: Mon, 03 Nov 2025 08:58:00 GMT
Content-Type: text/xml
```

Example — full download:
```http
GET /download/3f1b2c84-.../logback.xml HTTP/1.1

HTTP/1.1 200 OK
ETag: "cf83e1357eefb8bd...<sha512>"
Content-Length: 412
Last-Modified: Mon, 03 Nov 2025 08:58:00 GMT
Content-Type: text/xml

<configuration>...
```

### 3. Actuator endpoints

Provided by `spring-boot-starter-actuator:1.2.3.RELEASE`, auto-configured with **no** customization (no `application.properties`/`application.yml` exists in the repository). Under Spring Boot 1.2.x the default set — `/health`, `/info`, `/metrics`, `/env`, `/beans`, `/mappings`, `/configprops`, `/trace`, `/dump`, `/autoconfig`, `/shutdown` (disabled by default) — is served at the context root without authentication unless Spring Security is present, which it is not. Several of these are sensitive. See [../analysis/security-patterns.md](../analysis/security-patterns.md).

## Declarative Service Definitions

A full repository scan found **no** declarative service, endpoint, or component definitions. Specifically absent: Spring XML bean contexts (`applicationContext*.xml`, `beans*.xml`, `*-context.xml`), `WEB-INF/web.xml`, `servicedef/`, EJB or OSGi descriptors, WSDL, OpenAPI/Swagger, GraphQL schemas, `.proto`/Thrift/Avro IDL, AsyncAPI, MyBatis/Hibernate mappings, `persistence.xml`, Camel routes, Struts configs, BPMN, Airflow DAGs, CloudFormation/SAM, Kubernetes manifests, and Docker Compose files.

The only non-code definition files are `pom.xml` (build) and `src/main/resources/logback.xml` (logging). All services are declared **in code** via annotations:

| Declared component | Annotation | Class | Active when |
|---|---|---|---|
| Application / component-scan root | `@SpringBootApplication` | `MainApplication` | always |
| `/download/**` REST handler | `@RestController` + `@RequestMapping` | `DownloadController` | always |
| Production file storage | `@Component` + `@Profile("!test")` | `FileSystemStorage` | profile `test` inactive |
| Test file storage | `@Component` + `@Profile("test")` | `FileStorageStub` | profile `test` active |

Mapping from declaration to implementation is therefore 1:1 and direct — there are no XML-to-class indirections to resolve.

## Public Java API

### `MainApplication`
| Method | Signature | Description |
|---|---|---|
| `main` | `public static void main(String[] args)` | JVM entry point; starts the Spring application context |

### `DownloadController`
| Method | Signature | Description |
|---|---|---|
| constructor | `public DownloadController(FileStorage storage)` | `@Autowired` constructor injection |
| `redirect` | `public ResponseEntity<Resource> redirect(HttpMethod, UUID, Optional<String>, Optional<Date>)` | Endpoint 1 handler |
| `download` | `public ResponseEntity<Resource> download(HttpMethod, UUID, Optional<String>, Optional<Date>)` | Endpoint 2 handler |

### `ExistingFile`
| Method | Signature | Description |
|---|---|---|
| constructor | `public ExistingFile(HttpMethod method, FilePointer filePointer, UUID uuid)` | Binds request context to a known-existing file |
| `redirect` | `public ResponseEntity<Resource> redirect(Optional<String> requestEtagOpt, Optional<Date> ifModifiedSinceOpt)` | Returns `304` if cached, else `301` with `Location` |
| `handle` | `public ResponseEntity<Resource> handle(Optional<String> requestEtagOpt, Optional<Date> ifModifiedSinceOpt)` | Returns `304` if cached, else `200` with throttled body (`GET`) or no body (`HEAD`) |

### `FileStorage` (interface)
| Method | Signature | Description |
|---|---|---|
| `findFile` | `Optional<FilePointer> findFile(UUID uuid)` | Resolve identifier to file handle; `Optional.empty()` when unknown |

### `FilePointer` (interface)
| Method | Signature | Description |
|---|---|---|
| `open` | `InputStream open()` | Fresh stream at byte 0; caller owns closing |
| `getSize` | `long getSize()` | Content length in bytes |
| `getOriginalName` | `String getOriginalName()` | Filename for the `Location` header |
| `getEtag` | `String getEtag()` | Quoted strong entity tag |
| `getMediaType` | `Optional<com.google.common.net.MediaType> getMediaType()` | Content type, if determinable |
| `matchesEtag` | `boolean matchesEtag(String requestEtag)` | `If-None-Match` evaluation |
| `getLastModified` | `Instant getLastModified()` | Modification instant |
| `modifiedAfter` | `boolean modifiedAfter(Instant isModifiedSince)` | True when the client copy is current |

### `FileSystemStorage`
| Method | Signature | Description |
|---|---|---|
| `findFile` | `public Optional<FilePointer> findFile(UUID uuid)` | **Stub.** Ignores `uuid`; always returns a pointer to classpath `/logback.xml` |

### `FileSystemPointer`
| Method | Signature | Description |
|---|---|---|
| constructor | `public FileSystemPointer(File target)` | Eagerly hashes content (SHA-512) and probes content type; wraps `IOException` in `IllegalArgumentException` |
| `open` | `public InputStream open()` | `BufferedInputStream` over a `FileInputStream` |
| `getSize` | `public long getSize()` | `target.length()` |
| `getOriginalName` | `public String getOriginalName()` | `target.getName()` |
| `getEtag` | `public String getEtag()` | `"\"" + sha512 + "\""` |
| `getMediaType` | `public Optional<MediaType> getMediaType()` | From `Files.probeContentType` |
| `matchesEtag` | `public boolean matchesEtag(String requestEtag)` | String equality against `getEtag()` |
| `getLastModified` | `public Instant getLastModified()` | From `target.lastModified()` |
| `modifiedAfter` | `public boolean modifiedAfter(Instant clientTime)` | `!clientTime.isBefore(getLastModified())` |
| `toString` | `public String toString()` | Guava `MoreObjects` rendering of all fields |

### `ThrottlingInputStream`
| Method | Signature | Description |
|---|---|---|
| constructor | `public ThrottlingInputStream(InputStream target, RateLimiter maxBytesPerSecond)` | Wraps a stream with a rate limit |
| `read` | `public int read()` | Acquires 1 permit, then delegates |
| `read` | `public int read(byte[] b)` | Acquires `b.length` permits, then delegates |
| `read` | `public int read(byte[] b, int off, int len)` | Acquires `len` permits, then delegates |
| `skip` | `public long skip(long n)` | Delegates; **not throttled** |
| `available` | `public int available()` | Delegates |
| `mark` | `public synchronized void mark(int readlimit)` | Delegates |
| `reset` | `public synchronized void reset()` | Delegates |
| `markSupported` | `public boolean markSupported()` | Delegates |
| `close` | `public void close()` | Delegates |

### Test-scope public API

| Class | Member | Description |
|---|---|---|
| `FileStorageStub` | `public Optional<FilePointer> findFile(UUID uuid)` | Returns the fixture for `TXT_FILE_UUID`, else empty |
| `FileExamples` | `public static final UUID TXT_FILE_UUID` | Random UUID identifying the fixture |
| `FileExamples` | `public static final FilePointer TXT_FILE` | Pointer to `/download.txt` |
| `FileExamples` | `public static final UUID NOT_FOUND_UUID` | Random UUID guaranteed absent |
| `Sha512ShallowEtagHeaderFilter` | `protected String generateETagHeaderValue(byte[] bytes)` | Quoted SHA-512 ETag; filter is never registered |
| `DownloadControllerSpec` | `public void setWebApplicationContext(WebApplicationContext wac)` | `@Autowired` setter that builds `MockMvc` |

## Summary

| Surface | Count |
|---|---|
| HTTP endpoints (path + method combinations) | 4 (`GET`/`HEAD` × 2 paths) |
| Distinct HTTP response statuses produced by application code | 4 (`200`, `301`, `304`, `404`) |
| Declarative service definitions | 0 |
| Public production methods (incl. constructors) | 26 |
| Public interfaces | 2 |
