# API Reference

[← Back to index](../README.md) · Related: [interfaces.md](interfaces.md) · [../specialized/README.md](../specialized/README.md)

All endpoints are declared **in code** via Spring MVC annotations. A repository-wide scan found **no declarative (XML/YAML/OpenAPI/WSDL/proto) service or endpoint definitions** — see [../architecture/dependencies.md](../architecture/dependencies.md#declarative-non-code-definitions).

## HTTP Endpoints

Base path: `/download` (`@RequestMapping("/download")` on `DownloadController`).

### 1. Canonicalizing redirect

| Field | Value |
|---|---|
| Methods | `GET`, `HEAD` |
| Path | `/download/{uuid}` |
| Handler | `DownloadController.redirect` (`DownloadController.java:33-43`) |
| Auth | none |

**Parameters**

| Name | In | Type | Required | Notes |
|---|---|---|---|---|
| `uuid` | path | UUID | yes | malformed value → binding failure |
| `If-None-Match` | header | string | no | quoted strong ETag |
| `If-Modified-Since` | header | HTTP date | no | RFC 1123 |

**Responses**

| Status | Meaning | Headers |
|---|---|---|
| `301` | Redirect to `/download/{uuid}/{originalName}` | `Location` |
| `304` | Client cache is current | `ETag`, `Content-Length`, `Last-Modified`, `Content-Type` |
| `404` | Unknown UUID | — |

**Example**
```http
GET /download/4f6c1a2e-0000-0000-0000-000000000001 HTTP/1.1

HTTP/1.1 301 Moved Permanently
Location: /download/4f6c1a2e-0000-0000-0000-000000000001/file.txt
```

### 2. File download

| Field | Value |
|---|---|
| Methods | `GET`, `HEAD` |
| Path | `/download/{uuid}/{filename}` |
| Handler | `DownloadController.download` (`DownloadController.java:45-55`) |
| Auth | none |

**Parameters**

| Name | In | Type | Required | Notes |
|---|---|---|---|---|
| `uuid` | path | UUID | yes | selects the file |
| `filename` | path | string | yes | **not bound or validated**; cosmetic, lets the browser save a sensible name |
| `If-None-Match` | header | string | no | → `304` when it equals the current quoted ETag |
| `If-Modified-Since` | header | HTTP date | no | → `304` when not older than the file's `Last-Modified` |

**Responses**

| Status | Body | Notes |
|---|---|---|
| `200` | file bytes, throttled to 1 MB/s (`GET`); empty (`HEAD`) | `ETag`, `Content-Length`, `Last-Modified`, `Content-Type` when detectable |
| `304` | empty | same validators echoed back |
| `404` | empty | unknown UUID |

**Examples**
```http
GET /download/<uuid>/file.txt HTTP/1.1

HTTP/1.1 200 OK
ETag: "<sha-512 hex>"
Content-Length: 6
Last-Modified: Tue, 01 Oct 2024 10:00:00 GMT
Content-Type: text/plain

foobar
```
```http
GET /download/<uuid>/file.txt HTTP/1.1
If-None-Match: "<sha-512 hex>"

HTTP/1.1 304 Not Modified
ETag: "<sha-512 hex>"
```
```http
HEAD /download/<uuid>/file.txt HTTP/1.1

HTTP/1.1 200 OK
Content-Length: 6
Content-Type: text/plain
ETag: "<sha-512 hex>"
(no body)
```

### 3. Actuator endpoints (auto-configured, not application code)

`spring-boot-starter-actuator` 1.2.3 is on the classpath with no configuration file and no security configuration, so the Boot 1.x default endpoint set (for example `/health`, `/info`, `/metrics`, `/env`, `/beans`, `/mappings`, `/configprops`, `/trace`, `/dump`, `/autoconfig`) is exposed unauthenticated on the application port. No code in this repository references Actuator. See [../analysis/security-patterns.md](../analysis/security-patterns.md).

## Public Java API (callable from other code)

### `DownloadController`
| Method | Signature |
|---|---|
| constructor | `DownloadController(FileStorage storage)` |
| `redirect` | `ResponseEntity<Resource> redirect(HttpMethod, UUID, Optional<String>, Optional<Date>)` |
| `download` | `ResponseEntity<Resource> download(HttpMethod, UUID, Optional<String>, Optional<Date>)` |

### `ExistingFile`
| Method | Signature | Purpose |
|---|---|---|
| constructor | `ExistingFile(HttpMethod, FilePointer, UUID)` | — |
| `redirect` | `ResponseEntity<Resource> redirect(Optional<String>, Optional<Date>)` | caching-aware `301` |
| `handle` | `ResponseEntity<Resource> handle(Optional<String>, Optional<Date>)` | caching-aware `200`/`304` |

### `FileStorage` / `FilePointer`
See [interfaces.md](interfaces.md) for the full method contracts.

### `FileSystemPointer`
`open()`, `getSize()`, `getOriginalName()`, `getEtag()`, `getMediaType()`, `matchesEtag(String)`, `getLastModified()`, `modifiedAfter(Instant)`, `toString()`.

### `FileSystemStorage`
`findFile(UUID)`.

### `ThrottlingInputStream`
`ThrottlingInputStream(InputStream, RateLimiter)`, plus the standard `InputStream` surface: `read()`, `read(byte[])`, `read(byte[],int,int)`, `skip(long)`, `available()`, `mark(int)`, `reset()`, `markSupported()`, `close()`.

### `MainApplication`
`main(String[])`.
