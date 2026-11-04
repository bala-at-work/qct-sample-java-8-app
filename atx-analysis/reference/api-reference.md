# API Reference

- [Back to README](../README.md)
- Siblings: [program-structure.md](program-structure.md) · [interfaces.md](interfaces.md) · [data-models.md](data-models.md) · [modules.md](modules.md)
- See also: [../specialized/http-api-examples.md](../specialized/http-api-examples.md)

## Table of Contents
1. [Endpoint Summary](#endpoint-summary)
2. [`GET|HEAD /download/{uuid}`](#gethead-downloaduuid)
3. [`GET|HEAD /download/{uuid}/{filename}`](#gethead-downloaduuidfilename)
4. [Actuator Endpoints](#actuator-endpoints)
5. [Status Code Reference](#status-code-reference)
6. [Header Reference](#header-reference)
7. [Public Java API](#public-java-api)
8. [Declarative Service Definitions](#declarative-service-definitions)

## Endpoint Summary

All application endpoints are declared in code via Spring MVC annotations on `DownloadController` (`@RequestMapping("/download")`).

| Methods | Path | Handler | Success | Purpose |
|---|---|---|---|---|
| `GET`, `HEAD` | `/download/{uuid}` | `DownloadController.redirect` | `301` | Redirect to the canonical, filename-bearing URL |
| `GET`, `HEAD` | `/download/{uuid}/{filename}` | `DownloadController.download` | `200` | Serve the file, throttled to 1 MB/s |

No other HTTP verbs are mapped — `POST`, `PUT`, `DELETE`, `PATCH` and `OPTIONS` against these paths yield `405 Method Not Allowed`. There is **no upload endpoint**: the system is read-only.

## `GET|HEAD /download/{uuid}`

Resolves an opaque identifier to the canonical download URL.

### Request
| Element | Type | Required | Notes |
|---|---|---|---|
| `uuid` *(path)* | `java.util.UUID` | yes | Malformed values are rejected with `400` by Spring's converter before any application code runs |
| `If-None-Match` *(header)* | quoted ETag string | no | Bound as `Optional<String>`; evaluated by `FilePointer.matchesEtag` |
| `If-Modified-Since` *(header)* | RFC 1123 date | no | Bound as `Optional<Date>`; evaluated by `FilePointer.modifiedAfter` |

### Responses
| Status | When | Headers | Body |
|---|---|---|---|
| `301 Moved Permanently` | File exists and the client has no valid cached copy | `Location: /download/{uuid}/{originalName}` | empty |
| `304 Not Modified` | `If-None-Match` matches **or** the client copy is not older than the server copy | `ETag`, `Content-Length`, `Last-Modified`, `Content-Type` (if known) | empty |
| `404 Not Found` | `FileStorage.findFile` returned `Optional.empty()` | none set by the application | empty |
| `400 Bad Request` | `{uuid}` is not a valid UUID | Boot default | Boot default error body |

**Note.** The cache check precedes the redirect, so a client holding a valid cached copy receives `304` directly from the short URL and never follows the redirect. Note also that the `301` response carries **only** `Location` — no `ETag` or caching headers — because `redirectDownload` builds its response independently of `ExistingFile.response`.

### Behavior of `HEAD`
Identical to `GET`: both produce `301` or `304` with an empty body, since neither status carries content.

## `GET|HEAD /download/{uuid}/{filename}`

Serves file content.

### Request
| Element | Type | Required | Notes |
|---|---|---|---|
| `uuid` *(path)* | `java.util.UUID` | yes | As above |
| `filename` *(path)* | `String` | yes (structurally) | **Mapped but never bound or validated.** It is decorative — it exists so the saved file has a meaningful name. Any value serves the same content for a given UUID |
| `If-None-Match` *(header)* | quoted ETag string | no | As above |
| `If-Modified-Since` *(header)* | RFC 1123 date | no | As above |

### Responses
| Status | When | Headers | Body |
|---|---|---|---|
| `200 OK` (`GET`) | File exists, no valid client cache | `ETag`, `Content-Length`, `Last-Modified`, `Content-Type` (if known) | File bytes, streamed and throttled to 1 MB/s |
| `200 OK` (`HEAD`) | Same condition | Same headers, including the real `Content-Length` | **empty** — `resourceToReturn` returns `null` for non-GET |
| `304 Not Modified` | Valid client cache | `ETag`, `Content-Length`, `Last-Modified`, `Content-Type` (if known) | empty |
| `404 Not Found` | Unknown UUID | none set by the application | empty |
| `400 Bad Request` | Malformed UUID | Boot default | Boot default error body |

### Throttling
A fresh `RateLimiter.create(1_048_576)` is created **per response** in `ExistingFile.buildResource`, so the 1 MB/s ceiling applies **per concurrent download, not globally**. `RateLimiter.acquire` blocks the serving thread, so each in-flight download occupies one Tomcat worker thread for its full duration.

### Cache validation precedence
`ExistingFile.cached` returns `matchingEtag || notModifiedSince` — a match on **either** validator yields `304`. Strict HTTP would prefer `If-None-Match` and ignore `If-Modified-Since` when both are present; this implementation treats them as equally sufficient, which is more permissive but not harmful for immutable file content.

## Actuator Endpoints

`spring-boot-starter-actuator` 1.2.3.RELEASE is on the classpath with **no configuration** — no `application.properties`, no `management.*` settings, no Spring Security. Boot 1.2.x therefore exposes its default endpoint set at the root context with no authentication.

| Endpoint | Boot 1.2.x default | Sensitivity |
|---|---|---|
| `/health` | exposed | Low |
| `/info` | exposed | Low |
| `/metrics` | exposed | Medium — JVM and request metrics |
| `/env` | exposed | **High** — environment and system properties |
| `/configprops` | exposed | **High** — resolved configuration |
| `/beans` | exposed | Medium — full bean graph |
| `/mappings` | exposed | Medium — all request mappings |
| `/trace` | exposed | Medium — recent HTTP exchanges |
| `/dump`, `/autoconfig` | exposed | Medium — thread dump, auto-configuration report |
| `/shutdown` | disabled by default | Would be High if enabled |

No application code references the actuator; it contributes nothing to the download capability. It is pure attack surface as currently configured. See [../analysis/security-patterns.md](../analysis/security-patterns.md#exposure-surface) and remediation item P2.7 in [../technical-debt/remediation-plan.md](../technical-debt/remediation-plan.md).

## Status Code Reference

| Status | Produced by | Trigger |
|---|---|---|
| `200 OK` | `ExistingFile.serveDownload` → `response(..., OK, resource)` | File exists, no valid cache, long-URL request |
| `301 Moved Permanently` | `ExistingFile.redirectDownload` | File exists, no valid cache, short-URL request |
| `304 Not Modified` | `ExistingFile.notModified` | `cached()` returned `true` |
| `400 Bad Request` | Spring MVC type conversion | `{uuid}` not parseable as a UUID |
| `404 Not Found` | `DownloadController` — `orElseGet(() -> new ResponseEntity<>(NOT_FOUND))` | `findFile` returned `Optional.empty()` |
| `405 Method Not Allowed` | Spring MVC | Verb other than GET/HEAD |
| `500 Internal Server Error` | Boot default error handling | `IllegalArgumentException` from `FileSystemPointer` (I/O failure) or from `redirectDownload` (non-URI-safe filename) |

Note that `404` is unreachable in the non-`test` profile, because `FileSystemStorage` always returns a pointer.

## Header Reference

### Request headers consumed
| Header | Bound as | Used by |
|---|---|---|
| `If-None-Match` | `@RequestHeader(IF_NONE_MATCH) Optional<String>` | `FilePointer.matchesEtag` via `ExistingFile.cached` |
| `If-Modified-Since` | `@RequestHeader(IF_MODIFIED_SINCE) Optional<Date>` | `FilePointer.modifiedAfter` via `ExistingFile.cached` |

No other request header is read. Notably **`Range` is not supported** — there is no partial-content handling, so interrupted downloads cannot be resumed and no `Accept-Ranges` header is advertised. For a download server with deliberate 1 MB/s throttling, this is a significant functional gap.

### Response headers produced
| Header | Source | Set on |
|---|---|---|
| `ETag` | `FilePointer.getEtag()` — quoted SHA-512 of file contents | `200`, `304` |
| `Content-Length` | `FilePointer.getSize()` | `200`, `304` |
| `Last-Modified` | `FilePointer.getLastModified().toEpochMilli()` | `200`, `304` |
| `Content-Type` | `FilePointer.getMediaType()`, translated by `toMediaType`; omitted when unknown | `200`, `304` |
| `Location` | `/download/{uuid}/{originalName}` | `301` |

No `Cache-Control`, `Expires`, `Content-Disposition`, `Accept-Ranges`, or security headers (`X-Content-Type-Options`, `Content-Security-Policy`, `Strict-Transport-Security`) are set. The absence of `Content-Disposition` means browsers decide inline-versus-download behavior from `Content-Type` alone.

## Public Java API

Callable public members of project types, for consumers embedding these classes.

### `com.nurkiewicz.download.DownloadController`
| Member | Signature |
|---|---|
| constructor | `public DownloadController(FileStorage storage)` |
| `redirect` | `public ResponseEntity<Resource> redirect(HttpMethod, UUID, Optional<String>, Optional<Date>)` |
| `download` | `public ResponseEntity<Resource> download(HttpMethod, UUID, Optional<String>, Optional<Date>)` |

### `com.nurkiewicz.download.ExistingFile`
| Member | Signature |
|---|---|
| constructor | `public ExistingFile(HttpMethod method, FilePointer filePointer, UUID uuid)` |
| `redirect` | `public ResponseEntity<Resource> redirect(Optional<String> requestEtagOpt, Optional<Date> ifModifiedSinceOpt)` |
| `handle` | `public ResponseEntity<Resource> handle(Optional<String> requestEtagOpt, Optional<Date> ifModifiedSinceOpt)` |

### `com.nurkiewicz.download.FileStorage` *(interface)*
| Member | Signature |
|---|---|
| `findFile` | `Optional<FilePointer> findFile(UUID uuid)` |

### `com.nurkiewicz.download.FilePointer` *(interface)*
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

### `com.nurkiewicz.download.FileSystemPointer`
| Member | Signature |
|---|---|
| constructor | `public FileSystemPointer(File target)` |
| *all eight* `FilePointer` methods | `@Override public …` |
| `toString` | `@Override public String toString()` |

### `com.nurkiewicz.download.FileSystemStorage`
| Member | Signature |
|---|---|
| `findFile` | `@Override public Optional<FilePointer> findFile(UUID uuid)` |

### `com.nurkiewicz.download.ThrottlingInputStream`
| Member | Signature |
|---|---|
| constructor | `public ThrottlingInputStream(InputStream target, RateLimiter maxBytesPerSecond)` |
| `read` | `public int read()`, `public int read(byte[])`, `public int read(byte[], int, int)` |
| `skip` | `public long skip(long n)` |
| `available` | `public int available()` |
| `mark` | `public synchronized void mark(int readlimit)` |
| `reset` | `public synchronized void reset()` |
| `markSupported` | `public boolean markSupported()` |
| `close` | `public void close()` |

### `com.nurkiewicz.download.MainApplication`
| Member | Signature | Note |
|---|---|---|
| `main` | `public static void main(String[] args)` | The class itself is package-private |

### Test-tree public API
| Type | Members |
|---|---|
| `FileStorageStub` | `public Optional<FilePointer> findFile(UUID uuid)` |
| `FileExamples` | `public static final UUID TXT_FILE_UUID`, `public static final FilePointer TXT_FILE`, `public static final UUID NOT_FOUND_UUID` |
| `Sha512ShallowEtagHeaderFilter` | `protected String generateETagHeaderValue(byte[] bytes)` — plus everything inherited from `ShallowEtagHeaderFilter` |

**API stability note.** All of the above are the public surface that must be preserved through any migration. `ThrottlingInputStream` is the only class genuinely reusable outside this application — it has no framework dependency and would work unchanged on any Java version.

## Declarative Service Definitions

**None exist.** An exhaustive scan for services, endpoints, handlers and components declared in non-code files found nothing:

| Definition style | Files searched for | Found |
|---|---|---|
| Spring XML bean contexts | `applicationContext*.xml`, `beans*.xml`, `*-context.xml` | — |
| Servlet mappings | `WEB-INF/web.xml` | — |
| Service definition directories | `servicedef/`, `services*.xml` | — |
| EJB descriptors | `ejb-jar.xml` | — |
| OSGi service declarations | `OSGI-INF/*.xml` | — |
| SOAP contracts | `*.wsdl` | — |
| OpenAPI / Swagger | `openapi.yaml`, `swagger.json` | — |
| GraphQL | `*.graphql`, `*.graphqls` | — |
| gRPC / Protobuf | `*.proto` | — |
| Thrift / Avro IDL | `*.thrift`, `*.avsc` | — |
| AsyncAPI | `asyncapi.yaml` | — |
| Persistence mappings | `*Mapper.xml`, `*.hbm.xml`, `persistence.xml` | — |
| Integration routes | Camel route XML, Struts config | — |
| Workflow | BPMN, Airflow DAG YAML | — |
| Infrastructure | CloudFormation/SAM, Kubernetes manifests, Docker Compose | — |

**Conclusion.** Every callable service in this system is declared **in code** through Spring MVC annotations. The two endpoints documented above, plus the auto-configured actuator endpoints, constitute the complete callable API surface. The only non-code configuration present in the repository is `logback.xml` (logging), `pom.xml` (build), and `maven-wrapper.properties` (tooling) — none of which declare a service.
