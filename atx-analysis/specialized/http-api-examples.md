# HTTP API — Request/Response Examples

- [Back to README](../README.md)
- See also: [../reference/api-reference.md](../reference/api-reference.md) · [../diagrams/behavioral.md](../diagrams/behavioral.md)

> Specialized documentation generated because an HTTP API was detected. Examples are **derived from static analysis** of `DownloadController`, `ExistingFile` and `FileSystemPointer`, plus the assertions in `DownloadControllerSpec`. No request was actually executed — the transformation does not run the code.

## Table of Contents
1. [Fixture Context](#fixture-context)
2. [Example 1 — Download a File](#example-1--download-a-file)
3. [Example 2 — Canonical URL Redirect](#example-2--canonical-url-redirect)
4. [Example 3 — Conditional GET with ETag](#example-3--conditional-get-with-etag)
5. [Example 4 — Conditional GET with If-Modified-Since](#example-4--conditional-get-with-if-modified-since)
6. [Example 5 — HEAD Request](#example-5--head-request)
7. [Example 6 — Unknown Identifier](#example-6--unknown-identifier)
8. [Example 7 — Malformed Identifier](#example-7--malformed-identifier)
9. [Example 8 — Unsupported Method](#example-8--unsupported-method)
10. [Example 9 — Decorative Filename](#example-9--decorative-filename)
11. [Actuator Examples](#actuator-examples)
12. [Client Behavior Notes](#client-behavior-notes)

## Fixture Context

Examples use the `test`-profile fixture, which is the only file whose exact properties are known from the code:

| Property | Value | Source |
|---|---|---|
| File | `download.txt` | `src/test/resources/download.txt` |
| Content | `foobar` | asserted by feature 1 |
| Size | 6 bytes | asserted by feature 11 |
| Content type | `text/plain` | asserted by feature 12 |
| ETag | `"<sha512-of-foobar>"` — a quoted 128-hex-character SHA-512 | `FileSystemPointer.getEtag()` |
| UUID | `FileExamples.TXT_FILE_UUID` — regenerated per JVM run | `UUID.randomUUID()` |

Below, `3f2a7c91-...` stands for that UUID and `<sha512>` for the 128-hex-character hash. In the non-`test` profile, `FileSystemStorage` returns `logback.xml` for **every** UUID.

## Example 1 — Download a File

### Request
```http
GET /download/3f2a7c91-8e4d-4b2a-9c1f-5d7e8a3b6c90/download.txt HTTP/1.1
Host: localhost:8080
```

### Response
```http
HTTP/1.1 200 OK
ETag: "<sha512>"
Content-Length: 6
Last-Modified: Wed, 21 Oct 2015 07:28:00 GMT
Content-Type: text/plain

foobar
```

### Notes
| Aspect | Detail |
|---|---|
| Body delivery | Streamed via `InputStreamResource` over a `ThrottlingInputStream` — not buffered in memory |
| Throttle | A fresh `RateLimiter.create(1_048_576)` per response ⇒ ≤ 1 MB/s **per download**, with no global ceiling |
| Header source | All four headers come from `ExistingFile.response(...)` |
| Hidden cost | `FileSystemPointer`'s constructor already read the **entire file** to compute the SHA-512 ETag, so the file is read twice for this response |
| Absent headers | No `Cache-Control`, `Expires`, `Content-Disposition`, or `Accept-Ranges` is set |

## Example 2 — Canonical URL Redirect

The short URL never serves content.

### Request
```http
GET /download/3f2a7c91-8e4d-4b2a-9c1f-5d7e8a3b6c90 HTTP/1.1
Host: localhost:8080
```

### Response
```http
HTTP/1.1 301 Moved Permanently
Location: /download/3f2a7c91-8e4d-4b2a-9c1f-5d7e8a3b6c90/download.txt
Content-Length: 0
```

### Notes
- The `Location` path is built in `ExistingFile.redirectDownload` as `"/download/" + uuid + "/" + filePointer.getOriginalName()`.
- `301` (permanent, cacheable) rather than `302` — clients and proxies may cache the mapping indefinitely.
- **This response carries no caching headers** (`ETag`, `Content-Length` of the file, `Last-Modified`, `Content-Type`) because `redirectDownload` bypasses the shared `response(...)` funnel.
- The `Location` is a relative path, not an absolute URL.
- **This flow has no test coverage** — see [../migration/test-specifications.md](../migration/test-specifications.md#coverage-matrix).

## Example 3 — Conditional GET with ETag

### 3a — Matching ETag ⇒ `304`
```http
GET /download/3f2a7c91-.../download.txt HTTP/1.1
Host: localhost:8080
If-None-Match: "<sha512>"
```
```http
HTTP/1.1 304 Not Modified
ETag: "<sha512>"
Content-Length: 6
Last-Modified: Wed, 21 Oct 2015 07:28:00 GMT
Content-Type: text/plain
```
The `304` carries the **same headers** as the `200`, because both route through `ExistingFile.response(...)`. Only the body differs. Asserted by feature 5.

### 3b — Non-matching ETag ⇒ `200`
```http
If-None-Match: "WHATEVER"
```
```http
HTTP/1.1 200 OK
… full body served
```
Comparison is exact string equality on the quoted value (`FileSystemPointer.matchesEtag`). Asserted by feature 4.

### 3c — Quoting matters
An **unquoted** hash will not match:
```http
If-None-Match: <sha512>          ⇒ 200 (no match)
If-None-Match: "<sha512>"        ⇒ 304 (match)
```
`getEtag()` returns a pre-quoted value and `matchesEtag` compares it verbatim. Weak ETags (`W/"..."`), comma-separated lists, and `*` receive **no special handling** — they simply fail the equality check and the file is served.

### 3d — Short URL with a valid cache
```http
GET /download/3f2a7c91-... HTTP/1.1
If-None-Match: "<sha512>"
```
```http
HTTP/1.1 304 Not Modified
```
The cache check precedes the redirect decision, so a client with a valid copy gets `304` directly and never follows the `301`.

## Example 4 — Conditional GET with If-Modified-Since

Server `Last-Modified` is `Wed, 21 Oct 2015 07:28:00 GMT` in all three cases.

### 4a — Client time equals server time ⇒ `304`
```http
If-Modified-Since: Wed, 21 Oct 2015 07:28:00 GMT
```
```http
HTTP/1.1 304 Not Modified
```
`modifiedAfter` is `!clientTime.isBefore(lastModified)` ⇒ `true` at equality. Asserted by feature 6.

### 4b — Client copy newer than server ⇒ `304`
```http
If-Modified-Since: Wed, 21 Oct 2015 07:29:00 GMT
```
```http
HTTP/1.1 304 Not Modified
```
Asserted by feature 7.

### 4c — File modified after the client's copy ⇒ `200`
```http
If-Modified-Since: Wed, 21 Oct 2015 07:27:00 GMT
```
```http
HTTP/1.1 200 OK
… full body served
```
Asserted by feature 8.

### 4d — Both validators present
```http
If-None-Match: "<sha512>"
If-Modified-Since: Wed, 21 Oct 2015 07:27:00 GMT
```
```http
HTTP/1.1 304 Not Modified
```
`cached()` returns `matchingEtag || notModifiedSince` — **either** validator alone suffices. Strict HTTP would prefer `If-None-Match` and ignore the date; this implementation is more permissive, which is harmless for immutable file content.

### 4e — Unparseable date
```http
If-Modified-Since: not-a-date
```
```http
HTTP/1.1 200 OK
```
Spring's converter fails to bind, `ifModifiedSinceOpt` is `Optional.empty()`, and the file is served. **This is also the silent failure mode to watch during a framework upgrade** — if the binding stops working entirely, every response becomes a full-body `200` with no error and no log entry.

### 4f — One-second granularity
HTTP date headers carry one-second precision while `File.lastModified()` is millisecond-precise. A file modified within the same second as the client's copy will be judged unmodified. This is standard HTTP behavior and is exactly why the ETag validator exists as the stronger check.

## Example 5 — HEAD Request

### 5a — `HEAD` ⇒ `200` with no body
```http
HEAD /download/3f2a7c91-.../download.txt HTTP/1.1
Host: localhost:8080
```
```http
HTTP/1.1 200 OK
ETag: "<sha512>"
Content-Length: 6
Last-Modified: Wed, 21 Oct 2015 07:28:00 GMT
Content-Type: text/plain

(no body)
```

**Note the `Content-Length: 6`** — the true file size is reported even though no bytes are sent. This is correct HTTP and is asserted by feature 11.

Implementation: `resourceToReturn` returns `null` for any non-GET method, so the same handler and the same `response(...)` funnel produce identical headers. Consequently `open()` is never called for a HEAD — no file descriptor is opened and no throttling occurs.

### 5b — `HEAD` with a valid cache ⇒ `304`
```http
HEAD /download/3f2a7c91-.../download.txt HTTP/1.1
If-None-Match: "<sha512>"
```
```http
HTTP/1.1 304 Not Modified
ETag: "<sha512>"
Content-Length: 6
Last-Modified: Wed, 21 Oct 2015 07:28:00 GMT
Content-Type: text/plain
```
Asserted by feature 10.

### 5c — `HEAD` on the short URL ⇒ `301`
```http
HEAD /download/3f2a7c91-... HTTP/1.1
```
```http
HTTP/1.1 301 Moved Permanently
Location: /download/3f2a7c91-.../download.txt
```

## Example 6 — Unknown Identifier

```http
GET /download/00000000-0000-0000-0000-000000000000/anything.txt HTTP/1.1
```
```http
HTTP/1.1 404 Not Found
Content-Length: 0

(no body)
```

`FileStorage.findFile` returned `Optional.empty()`, so `Optional.map(...)` short-circuits and `orElseGet(() -> new ResponseEntity<>(NOT_FOUND))` applies. No `ExistingFile` is ever constructed, so no headers are set. Asserted by feature 2.

**Important:** this response is **unreachable in the non-`test` profile**, because `FileSystemStorage` always returns a pointer to `logback.xml` regardless of the UUID.

Note also that a `404` does not distinguish "no such file" from "not permitted" — a desirable property for a capability-URL model.

## Example 7 — Malformed Identifier

```http
GET /download/not-a-uuid/file.txt HTTP/1.1
```
```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{ "timestamp": …, "status": 400, "error": "Bad Request", … }
```

Spring's `String`→`UUID` converter rejects the segment before any application code executes. The body is Boot 1.2.x's default error representation, not application output. This is the **only input validation** in the system, and it is achieved purely through parameter typing — which is why Gate 6.1 in [../migration/validation-criteria.md](../migration/validation-criteria.md) requires the parameter to stay typed as `UUID` rather than `String`.

## Example 8 — Unsupported Method

```http
POST /download/3f2a7c91-.../download.txt HTTP/1.1
Content-Length: 4

data
```
```http
HTTP/1.1 405 Method Not Allowed
Allow: GET, HEAD
```

Both handlers declare `method = {GET, HEAD}`. `POST`, `PUT`, `PATCH` and `DELETE` are all rejected — the API is strictly read-only, and **there is no upload endpoint anywhere in the system**.

## Example 9 — Decorative Filename

The `{filename}` segment is mapped but never bound or validated. All of the following return **identical content**:

```http
GET /download/3f2a7c91-.../download.txt      ⇒ 200  "foobar"
GET /download/3f2a7c91-.../file.txt          ⇒ 200  "foobar"
GET /download/3f2a7c91-.../anything.pdf      ⇒ 200  "foobar"  (Content-Type: text/plain)
GET /download/3f2a7c91-.../installer.exe     ⇒ 200  "foobar"  (Content-Type: text/plain)
```

The project's own test suite **relies on this**: `DownloadControllerSpec` requests `/download/{uuid}/file.txt` while the fixture is named `download.txt`.

### Implications
| Concern | Detail |
|---|---|
| Non-canonical URLs | The same content is reachable at unbounded distinct URLs |
| Cache pollution | A CDN or proxy caches each variant separately; cheaply inflatable by an attacker |
| Download-name confusion | Combined with the **missing `Content-Disposition` header**, a link can advertise `.exe` or `.pdf` while the server serves unrelated content. Browsers decide inline-vs-download from `Content-Type` alone |
| Not path traversal | `{filename}` never reaches the filesystem — it is not bound to a parameter and is never passed to storage. See [../analysis/security-patterns.md](../analysis/security-patterns.md#path-traversal-review) |

### Also note
The `Content-Type` always reflects the **real** file as probed by `java.nio.file.Files.probeContentType`, never the URL extension. Because that probe is platform-dependent (OS MIME database on Linux, extension-based elsewhere, `null` for unrecognized files), the same file can yield a different `Content-Type` on different hosts — and when the probe returns `null`, the header is **omitted entirely** rather than defaulted to `application/octet-stream`.

## Actuator Examples

`spring-boot-starter-actuator` 1.2.3.RELEASE is present with **no configuration**, so Boot 1.2.x's default endpoint set is exposed at the root context **without authentication**.

```http
GET /health HTTP/1.1
```
```http
HTTP/1.1 200 OK
Content-Type: application/json

{ "status": "UP" }
```

```http
GET /env HTTP/1.1
```
```http
HTTP/1.1 200 OK
Content-Type: application/json

{ "profiles": [], "systemProperties": { … }, "systemEnvironment": { … }, … }
```

🔴 **`/env` and `/configprops` disclose environment variables, system properties and resolved configuration to any anonymous caller.** `/beans`, `/mappings`, `/trace`, `/dump` and `/autoconfig` are likewise reachable. No application code uses the actuator, so as configured it is pure attack surface. See [../analysis/security-patterns.md](../analysis/security-patterns.md#exposure-surface) and remediation item P2.7.

## Client Behavior Notes

Practical consequences for anyone consuming this API.

| Topic | Behavior |
|---|---|
| **Recommended flow** | `GET /download/{uuid}` → follow the `301` → `GET /download/{uuid}/{name}`. The redirect exists so the saved file carries a meaningful name |
| **Download speed** | Capped at 1 MB/s per download. The limit is per-request, so parallel downloads scale linearly |
| **Resuming** | **Not supported.** `Range` requests are ignored and `Accept-Ranges` is never advertised, so an interrupted download must restart from zero — a significant limitation given the deliberate throttle |
| **Caching** | Store both the `ETag` and `Last-Modified` and send them back. Either validator alone is sufficient to receive a `304` |
| **ETag format** | A quoted 128-hex-character SHA-512 of the file content. It changes if and only if the bytes change |
| **Compression** | Not supported — `Accept-Encoding` is ignored and no `Content-Encoding` is set |
| **Content negotiation** | Not supported — `Accept` is ignored; the server dictates `Content-Type` |
| **Missing `Content-Type`** | Expect the header to be absent for files whose type cannot be probed; do not assume `application/octet-stream` |
| **Saved filename** | No `Content-Disposition` is sent, so the client must derive the filename from the URL path |
| **Transport** | Plain HTTP as built; no TLS is configured in the application. Any HTTPS must be provided by an upstream terminator |
| **Authentication** | None. Treat the UUID as a bearer credential: it cannot be revoked, does not expire, and leaks through browser history, `Referer` headers and proxy logs |
| **Concurrency ceiling** | Each in-flight download blocks one Tomcat worker thread for its full duration (~200 default threads), after which all requests queue |
