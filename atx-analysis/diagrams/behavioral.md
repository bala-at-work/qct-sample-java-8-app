# Behavioral Diagrams

- [Back to README](../README.md)
- Siblings: [structural.md](structural.md) · [architecture.md](architecture.md)
- See also: [../reference/api-reference.md](../reference/api-reference.md) · [../architecture/patterns.md](../architecture/patterns.md)

> **Scope.** These diagrams cover **only core application business logic** — the HTTP file-download flows. Build, test, lint and publish flows are deliberately excluded.

## Table of Contents
1. [Sequence — Successful Download](#sequence--successful-download)
2. [Sequence — Conditional Request Returning 304](#sequence--conditional-request-returning-304)
3. [Sequence — Canonical URL Redirect](#sequence--canonical-url-redirect)
4. [Sequence — Unknown Identifier](#sequence--unknown-identifier)
5. [Sequence — HEAD Request](#sequence--head-request)
6. [Activity — Request Handling Decision Tree](#activity--request-handling-decision-tree)
7. [Activity — Cache Validation Logic](#activity--cache-validation-logic)
8. [Activity — Throttled Streaming Loop](#activity--throttled-streaming-loop)
9. [State Machine — Client Cache Lifecycle](#state-machine--client-cache-lifecycle)
10. [State Machine — Response Construction](#state-machine--response-construction)
11. [Data Flow — Request to Bytes](#data-flow--request-to-bytes)
12. [Data Flow — Metadata to Headers](#data-flow--metadata-to-headers)
13. [Business Rules](#business-rules)
14. [Exception Flows](#exception-flows)

## Sequence — Successful Download

`GET /download/{uuid}/{filename}` with no conditional headers.

```
Client   Tomcat/     Download     FileStorage   FileSystem   Existing   Throttling
         Dispatcher  Controller                 Pointer      File       InputStream
  │          │           │             │            │           │            │
  │ GET /download/{uuid}/file.txt      │            │           │            │
  ├─────────►│           │             │            │           │            │
  │          │ bind: HttpMethod=GET,   │            │           │            │
  │          │ UUID (validated),       │            │           │            │
  │          │ Optional.empty() ×2     │            │           │            │
  │          ├──────────►│             │            │           │            │
  │          │           │ findFile(uuid)           │           │            │
  │          │           ├────────────►│            │           │            │
  │          │           │             │ new FileSystemPointer(file)         │
  │          │           │             ├───────────►│           │            │
  │          │           │             │            │ ⚠ Files.hash(SHA-512)  │
  │          │           │             │            │   READS WHOLE FILE     │
  │          │           │             │            │ ⚠ probeContentType()   │
  │          │           │◄───────────-┤            │           │            │
  │          │           │◄────────────┤ Optional.of(pointer)   │            │
  │          │           │             │            │           │            │
  │          │           │ new ExistingFile(GET, pointer, uuid) │            │
  │          │           ├─────────────────────────────────────►│            │
  │          │           │ handle(empty, empty)                 │            │
  │          │           ├─────────────────────────────────────►│            │
  │          │           │             │            │           │ cached()?  │
  │          │           │             │            │           │ ⇒ false    │
  │          │           │             │            │           │            │
  │          │           │             │            │  open()   │            │
  │          │           │             │            │◄──────────┤            │
  │          │           │             │            ├──────────►│ Buffered-  │
  │          │           │             │            │           │ InputStream│
  │          │           │             │            │           │            │
  │          │           │             │            │ RateLimiter.create(1MB)│
  │          │           │             │            │           ├───────────►│
  │          │           │             │            │ getEtag(), getSize(),  │
  │          │           │             │            │ getLastModified(),     │
  │          │           │             │            │ getMediaType()         │
  │          │           │             │            │◄──────────┤            │
  │          │           │◄─────────────────────────────────────┤            │
  │          │           │   ResponseEntity(200, headers,       │            │
  │          │           │     InputStreamResource)             │            │
  │          │◄──────────┤             │            │           │            │
  │          │  write response body                 │           │            │
  │          ├─────────────────────────────────────────────────►│            │
  │          │                                      │           │ read(buf)  │
  │          │                                      │           │ acquire(n) │
  │          │                                      │           │ ⚠ BLOCKS   │
  │◄─────────┤ 200 OK                               │           │   thread   │
  │  ETag, Content-Length, Last-Modified, Content-Type          │            │
  │  body streamed at ≤ 1 MB/s                      │           │            │
  │          │                                      │           │ close()    │
```

**Notable.** The full-file SHA-512 hash happens inside `FileSystemPointer`'s constructor — before the response status is even decided. The file is therefore read **twice** for a `200`: once to hash, once to stream.

## Sequence — Conditional Request Returning 304

`GET /download/{uuid}/{filename}` with `If-None-Match` matching the current ETag.

```
Client      Download        FileStorage      FileSystem      Existing
            Controller                       Pointer         File
  │             │                │               │              │
  │ GET … + If-None-Match: "<sha512>"            │              │
  ├────────────►│                │               │              │
  │             │ findFile(uuid) │               │              │
  │             ├───────────────►│               │              │
  │             │                │ new FileSystemPointer(…)     │
  │             │                ├──────────────►│              │
  │             │                │   ⚠ FULL-FILE SHA-512 computed
  │             │                │     even though the response │
  │             │                │     will carry NO BODY       │
  │             │◄───────────────┤               │              │
  │             │ handle(Optional.of(etag), empty)              │
  │             ├─────────────────────────────────────────────►│
  │             │                │               │  cached():   │
  │             │                │               │ matchesEtag(etag)
  │             │                │               │◄─────────────┤
  │             │                │               ├─────────────►│ true
  │             │                │               │              │
  │             │                │               │  notModified(pointer)
  │             │                │               │  → response(…, 304, null)
  │             │◄─────────────────────────────────────────────┤
  │◄────────────┤ 304 Not Modified                             │
  │   ETag, Content-Length, Last-Modified, Content-Type         │
  │   NO body — client reuses its cached copy                   │
```

**Verified by.** Spock feature methods `'should not send file if ETag matches content'` and `'should return 304 on HEAD request if we have cached version'` — both assert the `ETag` is echoed back on the `304`.

## Sequence — Canonical URL Redirect

`GET /download/{uuid}` (short URL, no filename).

```
Client          Download          FileStorage      Existing
                Controller                         File
  │                 │                   │             │
  │ GET /download/{uuid}                │             │
  ├────────────────►│                   │             │
  │                 │ findFile(uuid)    │             │
  │                 ├──────────────────►│             │
  │                 │◄──────────────────┤ Optional.of(pointer)
  │                 │ redirect(empty, empty)          │
  │                 ├────────────────────────────────►│
  │                 │                   │  cached()? ⇒ false
  │                 │                   │  redirectDownload(pointer):
  │                 │                   │    getOriginalName() ⇒ "download.txt"
  │                 │                   │    new URI("/download/" + uuid
  │                 │                   │             + "/download.txt")
  │                 │◄────────────────────────────────┤
  │◄────────────────┤ 301 Moved Permanently           │
  │   Location: /download/{uuid}/download.txt         │
  │   (⚠ no ETag / Content-Length / Last-Modified —   │
  │    redirectDownload bypasses response(...))       │
  │                 │                   │             │
  │ GET /download/{uuid}/download.txt   │             │
  ├────────────────►│  ⇒ see "Successful Download"    │
```

**Coverage gap.** This flow — one of the system's two endpoints — has **no test coverage** in the Spock specification. See [../analysis/tech-debt.md](../analysis/tech-debt.md#test-debt) T4.

## Sequence — Unknown Identifier

```
Client          Download          FileStorage
                Controller
  │                 │                   │
  │ GET /download/{unknown-uuid}/file.txt
  ├────────────────►│                   │
  │                 │ findFile(uuid)    │
  │                 ├──────────────────►│
  │                 │◄──────────────────┤ Optional.empty()
  │                 │                   │
  │                 │ Optional.map(…) short-circuits
  │                 │ orElseGet(() -> new ResponseEntity<>(NOT_FOUND))
  │◄────────────────┤ 404 Not Found     │
  │   empty body, no ExistingFile ever constructed
```

**Important.** This path is **unreachable in the non-`test` profile**, because `FileSystemStorage` always returns a pointer. Only `FileStorageStub` ever returns `Optional.empty()`. Verified by the Spock feature `'should return 404 and no content'`.

## Sequence — HEAD Request

```
Client          Download        Existing        FileSystem
                Controller      File            Pointer
  │                 │               │               │
  │ HEAD /download/{uuid}/file.txt  │               │
  ├────────────────►│               │               │
  │                 │ bind HttpMethod = HEAD        │
  │                 │ (same handler as GET)         │
  │                 ├──────────────►│               │
  │                 │ handle(empty, empty)          │
  │                 │               │ cached()? ⇒ false
  │                 │               │ serveDownload(pointer):
  │                 │               │   resourceToReturn(pointer):
  │                 │               │     method == GET ? … : null
  │                 │               │     ⇒ returns NULL for HEAD
  │                 │               │   response(pointer, OK, null)
  │                 │               │     getEtag(), getSize(),
  │                 │               │     getLastModified(), getMediaType()
  │                 │               ├──────────────►│
  │                 │               │◄──────────────┤
  │                 │◄──────────────┤               │
  │◄────────────────┤ 200 OK        │               │
  │   ETag, Content-Length (the REAL size),         │
  │   Last-Modified, Content-Type                   │
  │   body: EMPTY — no stream opened, no throttling │
```

**Design note.** HEAD is implemented by returning a `null` resource rather than by branching the whole flow. Because headers are built by the same `response(...)` funnel, HEAD is guaranteed to report exactly the headers a GET would — including the true `Content-Length`. Notably, `open()` is never called for HEAD, so no file descriptor is opened and no throttling occurs.

**Verified by.** Three Spock features: `'should return 200 OK on HEAD request, but without body'`, `'should return Content-length header'`, `'should return content type in response'`.

## Activity — Request Handling Decision Tree

The complete business logic of the application in one diagram.

```
                         ┌──────────────────────┐
                         │  HTTP GET or HEAD    │
                         │  /download/…         │
                         └──────────┬───────────┘
                                    ▼
                         ┌──────────────────────┐
                         │ Spring binds UUID    │
                         └──────────┬───────────┘
                                    ▼
                              ╱──────────╲
                             ╱ valid UUID? ╲────── no ──► ┌──────────────┐
                             ╲             ╱              │ 400 Bad      │
                              ╲──────────╱                │ Request      │
                                    │ yes                 └──────────────┘
                                    ▼
                         ┌──────────────────────┐
                         │ FileStorage          │
                         │   .findFile(uuid)    │
                         └──────────┬───────────┘
                                    ▼
                              ╱──────────╲
                             ╱  present?  ╲────── no ──► ┌──────────────┐
                             ╲            ╱              │ 404 Not Found│
                              ╲──────────╱               │ empty body   │
                                    │ yes                └──────────────┘
                                    ▼
                         ┌──────────────────────┐
                         │ new ExistingFile(    │
                         │   method, ptr, uuid) │
                         └──────────┬───────────┘
                                    ▼
                              ╱──────────╲
                             ╱  cached()? ╲────── yes ─► ┌──────────────────┐
                             ╲            ╱              │ 304 Not Modified │
                              ╲──────────╱               │ ETag, Length,    │
                                    │ no                 │ Last-Modified,   │
                                    │                    │ Content-Type     │
                                    │                    │ NO body          │
                                    ▼                    └──────────────────┘
                              ╱──────────╲
                             ╱ which URL? ╲
                            ╱              ╲
                 short ────╱                ╲──── long
            /{uuid}       ╱                  ╲      /{uuid}/{filename}
                  ▼                                 ▼
    ┌──────────────────────────┐        ┌──────────────────────┐
    │ redirectDownload():      │        │ serveDownload():     │
    │  Location = /download/   │        │  resourceToReturn()  │
    │    {uuid}/{originalName} │        └──────────┬───────────┘
    │  301 Moved Permanently   │                   ▼
    │  (no caching headers)    │             ╱──────────╲
    └──────────────────────────┘            ╱ method ==  ╲
                                           ╱    GET ?     ╲
                                    yes ──╱                ╲── no (HEAD)
                                         ▼                  ▼
                         ┌────────────────────────┐  ┌──────────────┐
                         │ open() stream          │  │ resource =   │
                         │ RateLimiter.create(1MB)│  │   null       │
                         │ wrap ThrottlingInputStr│  └──────┬───────┘
                         │ InputStreamResource    │         │
                         └───────────┬────────────┘         │
                                     └──────────┬───────────┘
                                                ▼
                                    ┌────────────────────────┐
                                    │ response(ptr, 200, res)│
                                    │  ETag, Content-Length, │
                                    │  Last-Modified,        │
                                    │  Content-Type if known │
                                    └────────────────────────┘
```

## Activity — Cache Validation Logic

Detail of `ExistingFile.cached(...)` — the most logic-dense method in the codebase.

```
            ┌─────────────────────────────────────────────┐
            │ cached(requestEtagOpt, ifModifiedSinceOpt)  │
            └──────────────────┬──────────────────────────┘
                               ▼
        ┌──────────────────────────────────────────────────┐
        │ BRANCH A — ETag validator                        │
        │                                                  │
        │   requestEtagOpt present?                        │
        │     no  ──► matchingEtag = false                 │
        │     yes ──► filePointer.matchesEtag(etag)        │
        │             ⇒ getEtag().equals(requestEtag)      │
        │             (exact string equality, quoted)      │
        └──────────────────┬───────────────────────────────┘
                           │
        ┌──────────────────▼───────────────────────────────┐
        │ BRANCH B — time validator                        │
        │                                                  │
        │   ifModifiedSinceOpt present?                    │
        │     no  ──► notModifiedSince = false             │
        │     yes ──► Date::toInstant                      │
        │             then filePointer.modifiedAfter(t)    │
        │             ⇒ !clientTime.isBefore(lastModified) │
        │                                                  │
        │   ⚠ Despite the name, this returns TRUE when the │
        │     CLIENT's copy is still valid, i.e. when the  │
        │     client time is at or after the server's      │
        │     last-modified instant.                       │
        └──────────────────┬───────────────────────────────┘
                           ▼
            ┌──────────────────────────────────┐
            │ return matchingEtag              │
            │        || notModifiedSince       │
            │                                  │
            │ EITHER validator alone suffices. │
            │ Strict HTTP would prefer ETag    │
            │ and ignore the date when both    │
            │ are present; this is more        │
            │ permissive but harmless for      │
            │ immutable file content.          │
            └──────────────────────────────────┘
```

### Truth table
| `If-None-Match` | `If-Modified-Since` | `matchingEtag` | `notModifiedSince` | Result | Response |
|---|---|---|---|---|---|
| absent | absent | false | false | `false` | serve (`200`/`301`) |
| non-matching | absent | false | false | `false` | serve |
| matching | absent | **true** | false | `true` | `304` |
| absent | == lastModified | false | **true** | `true` | `304` |
| absent | > lastModified (client newer) | false | **true** | `true` | `304` |
| absent | < lastModified (file changed) | false | false | `false` | serve |
| matching | < lastModified | **true** | false | `true` | `304` |

All seven rows except the last are directly asserted by Spock feature methods — this is the best-covered logic in the project.

## Activity — Throttled Streaming Loop

```
        ┌────────────────────────────────────────────┐
        │ Spring writes InputStreamResource to the   │
        │ servlet output stream (Tomcat worker thread)│
        └──────────────────┬─────────────────────────┘
                           ▼
            ┌──────────────────────────────┐
            │ ThrottlingInputStream        │
            │   .read(buf, off, len)       │
            └──────────────┬───────────────┘
                           ▼
            ┌──────────────────────────────┐
            │ maxBytesPerSecond            │
            │   .acquire(len)              │
            │                              │
            │ ⚠ BLOCKS the calling thread  │
            │   until permits are available│
            │   at 1 MB/s                  │
            │                              │
            │ note: permits charged for    │
            │ the REQUESTED len, not the   │
            │ bytes actually returned —     │
            │ a short read at EOF slightly │
            │ over-charges (benign)        │
            └──────────────┬───────────────┘
                           ▼
            ┌──────────────────────────────┐
            │ target.read(buf, off, len)   │
            │   BufferedInputStream        │
            │     → FileInputStream        │
            └──────────────┬───────────────┘
                           ▼
                     ╱──────────╲
                    ╱  n == -1 ?  ╲──── no ──┐
                    ╲   (EOF)     ╱          │
                     ╲──────────╱            │ loop
                           │ yes             │
                           ▼                 │
            ┌──────────────────────────────┐ │
            │ close() → target.close()     │ │
            │ response complete            │ │
            └──────────────────────────────┘ │
                           ▲                 │
                           └─────────────────┘
```

**Capacity implication.** At 1 MB/s a 100 MB file holds one Tomcat worker thread for ~100 seconds. With Tomcat's ~200 default threads, roughly 200 concurrent downloads exhaust the connector pool and **all** subsequent requests queue — including `/health` and cheap `304`s. Because a fresh `RateLimiter` is created per response, aggregate egress is unbounded at N MB/s for N downloads.

## State Machine — Client Cache Lifecycle

The states a client's cached copy moves through, from the server's perspective.

```
                       ┌─────────────────────┐
                       │   NO CACHED COPY    │ ◄── initial state
                       └──────────┬──────────┘
                                  │ GET (no conditional headers)
                                  │ server → 200 + ETag + Last-Modified
                                  ▼
                       ┌─────────────────────┐
              ┌───────►│   CACHE VALID       │
              │        │  (client holds ETag │
              │        │   + Last-Modified)  │
              │        └──────┬───────┬──────┘
              │               │       │
              │  conditional  │       │  conditional GET,
              │  GET, nothing │       │  file changed on server
              │  changed      │       │  (client date < lastModified
              │               │       │   AND ETag no longer matches)
              │               ▼       ▼
              │   ┌───────────────┐  ┌────────────────────┐
              │   │ REVALIDATED   │  │   CACHE STALE      │
              │   │ server → 304  │  └─────────┬──────────┘
              │   │ ETag echoed   │            │ server → 200
              │   │ body empty    │            │ + new ETag
              │   └───────┬───────┘            │ + new Last-Modified
              │           │                    │
              └───────────┘◄───────────────────┘
                   (returns to CACHE VALID with the fresh copy)
```

### Transition table
| From | Trigger | Server evaluation | To | Status |
|---|---|---|---|---|
| NO CACHED COPY | `GET` | — | CACHE VALID | `200` |
| CACHE VALID | `GET` + matching `If-None-Match` | `matchesEtag` ⇒ true | CACHE VALID | `304` |
| CACHE VALID | `GET` + `If-Modified-Since` ≥ lastModified | `modifiedAfter` ⇒ true | CACHE VALID | `304` |
| CACHE VALID | `GET` + `If-Modified-Since` < lastModified | both validators false | CACHE STALE → VALID | `200` |
| CACHE VALID | `GET` + non-matching `If-None-Match` | both validators false | CACHE STALE → VALID | `200` |
| any | `GET` on an unknown UUID | `findFile` ⇒ empty | NO CACHED COPY | `404` |

**Note.** Because the ETag is a SHA-512 of file *content*, it changes if and only if the bytes change — the cache state transitions are therefore content-exact rather than heuristic. This is stronger than the mtime+size ETags most implementations use.

## State Machine — Response Construction

The states a response passes through inside `ExistingFile`.

```
    ┌────────────────────────┐
    │ REQUEST BOUND          │  HttpMethod, UUID, Optional headers
    └───────────┬────────────┘
                ▼
    ┌────────────────────────┐
    │ FILE RESOLVED          │  FilePointer obtained
    │ (SHA-512 + MIME probe  │  ⚠ expensive work already done here
    │  already performed)    │
    └───────────┬────────────┘
                ▼
    ┌────────────────────────┐
    │ CACHE EVALUATED        │  cached() ⇒ true | false
    └─────┬──────────────┬───┘
          │ true         │ false
          ▼              ▼
┌──────────────┐   ┌──────────────────────┐
│ NOT_MODIFIED │   │ SERVE DECISION       │
│ status = 304 │   │ short URL vs long URL│
│ body = null  │   └────┬────────────┬────┘
└──────┬───────┘        │ short      │ long
       │                ▼            ▼
       │      ┌──────────────┐  ┌──────────────────┐
       │      │ REDIRECTING  │  │ BODY DECISION    │
       │      │ status = 301 │  │ GET vs HEAD      │
       │      │ Location set │  └───┬──────────┬───┘
       │      │ NO caching   │      │ GET      │ HEAD
       │      │ headers      │      ▼          ▼
       │      └──────┬───────┘  ┌─────────┐ ┌─────────┐
       │             │          │ STREAM  │ │ HEADERS │
       │             │          │ PREPARED│ │ ONLY    │
       │             │          │ 200+body│ │ 200+null│
       │             │          └────┬────┘ └────┬────┘
       │             │               └─────┬────┘
       ▼             ▼                     ▼
┌──────────────────────────────────────────────────────┐
│ HEADERS APPLIED  (via response(...) — except for 301)│
│   ETag, Content-Length, Last-Modified,               │
│   Content-Type if getMediaType() is present          │
└──────────────────────┬───────────────────────────────┘
                       ▼
            ┌────────────────────────┐
            │ RESPONSE COMMITTED     │
            └────────────────────────┘
```

**Key structural fact.** The `304`, `200`-with-body and `200`-HEAD states all converge on `response(...)`, which is why their headers are byte-identical. The `301` state is the only one that bypasses this funnel — and consequently the only response that carries no caching headers.

## Data Flow — Request to Bytes

```
  HTTP REQUEST
  "GET /download/3f2a…/file.txt"
  "If-None-Match: \"<sha512>\""
  "If-Modified-Since: Wed, 21 Oct 2015 07:28:00 GMT"
        │
        ▼
  ┌──────────────────────────────────────────────────┐
  │ Spring MVC argument binding                      │
  │   "3f2a…"  ──converter──►  java.util.UUID        │
  │   "\"…\""  ────────────►  Optional<String>       │
  │   "Wed,…"  ──converter──►  Optional<Date>        │
  │   verb     ────────────►  HttpMethod.GET         │
  │   "file.txt" ─────────►  ⚠ DISCARDED (unbound)  │
  └──────────────────┬───────────────────────────────┘
                     ▼
  ┌──────────────────────────────────────────────────┐
  │ FileStorage.findFile(UUID)                       │
  │   ⚠ FileSystemStorage IGNORES the UUID and       │
  │     resolves the classpath resource /logback.xml │
  └──────────────────┬───────────────────────────────┘
                     ▼
  ┌──────────────────────────────────────────────────┐
  │ FileSystemPointer construction                   │
  │   File              ──► target                   │
  │   full file bytes   ──► SHA-512 HashCode (tag)   │
  │   probeContentType  ──► Guava MediaType | null    │
  └──────────────────┬───────────────────────────────┘
                     ▼
  ┌──────────────────────────────────────────────────┐
  │ ExistingFile.cached(etagOpt, dateOpt)            │
  │   Optional<Date> ──toInstant──► Instant          │
  │                  ──modifiedAfter──► boolean      │
  │   Optional<String> ──matchesEtag──► boolean      │
  └──────────────────┬───────────────────────────────┘
                     │ not cached
                     ▼
  ┌──────────────────────────────────────────────────┐
  │ FilePointer.open()                               │
  │   File ──► FileInputStream ──► BufferedInputStream│
  └──────────────────┬───────────────────────────────┘
                     ▼
  ┌──────────────────────────────────────────────────┐
  │ ThrottlingInputStream(stream, RateLimiter(1MB))  │
  │   every read: acquire(n) then delegate           │
  └──────────────────┬───────────────────────────────┘
                     ▼
  ┌──────────────────────────────────────────────────┐
  │ InputStreamResource ──► ResponseEntity body      │
  └──────────────────┬───────────────────────────────┘
                     ▼
  ┌──────────────────────────────────────────────────┐
  │ Tomcat servlet output stream ──► network socket  │
  │   rate-limited to ≤ 1 MB/s per download          │
  └──────────────────────────────────────────────────┘
                     ▼
  HTTP RESPONSE  200 OK + headers + streamed bytes
```

## Data Flow — Metadata to Headers

How each response header is derived.

```
  java.io.File (on disk)
    │
    ├─ .length() ──────────────────► FilePointer.getSize()
    │                                      │
    │                                      └──► ResponseEntity.contentLength(long)
    │                                                  └──► "Content-Length: 6"
    │
    ├─ .lastModified() (epoch ms)
    │       └─► Instant.ofEpochMilli() ──► FilePointer.getLastModified()
    │                                              │
    │                                              └─► .toEpochMilli()
    │                                                   └─► ResponseEntity.lastModified()
    │                                                        └─► "Last-Modified: Wed, …"
    │
    ├─ full contents
    │       └─► Guava Files.hash(SHA-512) ──► HashCode tag
    │                                              │
    │                                              └─► "\"" + tag + "\""
    │                                                   └─► FilePointer.getEtag()
    │                                                        └─► ResponseEntity.eTag()
    │                                                             └─► "ETag: \"<sha512>\""
    │
    ├─ .toPath()
    │       └─► Files.probeContentType() ──► String | null
    │                 │
    │                 └─► MediaType.parse() ──► Guava MediaType | null
    │                            │
    │                            └─► Optional.ofNullable()
    │                                 └─► FilePointer.getMediaType()
    │                                      └─► ExistingFile.toMediaType()
    │                                           charset present ? MediaType(t,s,c)
    │                                                           : MediaType(t,s)
    │                                           └─► ResponseEntity.contentType()
    │                                                └─► "Content-Type: text/plain"
    │                                           (header OMITTED when empty)
    │
    └─ .getName() ─────────────────► FilePointer.getOriginalName()
                                           │
                                           └─► "/download/" + uuid + "/" + name
                                                └─► new URI(...)
                                                     └─► ResponseEntity.location()
                                                          └─► "Location: /download/…"
                                                               (301 only)
```

## Business Rules

Extracted from the source. These are the complete behavioral rules of the system.

| # | Rule | Implemented in |
|---|---|---|
| BR1 | A file is addressed publicly by a UUID; no other identifier is accepted | `@PathVariable UUID` in both handlers |
| BR2 | A malformed UUID is rejected before any application logic executes | Spring's `String`→`UUID` converter ⇒ `400` |
| BR3 | An unknown UUID yields `404` with an empty body — it does not distinguish "no such file" from "not permitted" | `DownloadController.orElseGet(…NOT_FOUND)` |
| BR4 | The short URL `/download/{uuid}` never serves content; it always redirects to the canonical filename-bearing URL | `ExistingFile.redirect` → `redirectDownload` |
| BR5 | The canonical URL is `/download/{uuid}/{originalName}`, using the server-side filename | `redirectDownload` |
| BR6 | The redirect is `301 Moved Permanently` (permanent, cacheable), not `302` | `redirectDownload` |
| BR7 | The `{filename}` segment is decorative: any value serves the same content for a given UUID | `download(...)` never binds `filename` |
| BR8 | A client's cached copy is valid if its ETag matches **or** its copy is not older than the server's | `ExistingFile.cached` ⇒ `\|\|` |
| BR9 | A valid client cache yields `304` with full headers and no body — including on the short URL, so the redirect is skipped | `notModified`; cache check precedes the redirect decision |
| BR10 | An ETag identifies file **content**, not file metadata: it is a SHA-512 of the bytes | `FileSystemPointer.tag` |
| BR11 | ETag comparison is exact string equality on the quoted value; weak ETags and `*` are not specially handled | `FileSystemPointer.matchesEtag` |
| BR12 | `HEAD` returns exactly the headers a `GET` would, including the true `Content-Length`, with an empty body | `resourceToReturn` returns `null` for non-GET |
| BR13 | `HEAD` opens no stream and performs no throttling | `buildResource` is never called for HEAD |
| BR14 | Content is served at no more than 1 MB/s **per download**; there is no global egress limit | `RateLimiter.create(ONE_MB)` created per response |
| BR15 | `Content-Type` is omitted entirely when it cannot be determined, rather than defaulting to `application/octet-stream` | `getMediaType()` ⇒ `Optional.empty()` ⇒ header not set |
| BR16 | Only `GET` and `HEAD` are permitted; the API is strictly read-only | `method = {GET, HEAD}` ⇒ `405` otherwise |
| BR17 | Exactly one storage implementation is active, selected by Spring profile | `@Profile("!test")` / `@Profile("test")` |
| BR18 | `304`, `200`-with-body and `200`-HEAD responses carry identical headers | all converge on `response(...)` |
| BR19 | A `301` response carries **only** `Location` — no caching headers | `redirectDownload` bypasses `response(...)` |
| BR20 | No request header other than `If-None-Match` and `If-Modified-Since` influences behavior; `Range` is unsupported | handler signatures |

## Exception Flows

```
  ┌──────────────────────────────────────────────────────────────┐
  │ FileSystemPointer constructor                                │
  │   Files.hash(...) or probeContentType(...) throws IOException │
  │        └──► rewrapped as IllegalArgumentException            │
  │             └──► propagates past DownloadController          │
  │                  └──► Boot default error handling ⇒ 500      │
  │   ⚠ An environment/I-O failure is reported as a caller error │
  └──────────────────────────────────────────────────────────────┘

  ┌──────────────────────────────────────────────────────────────┐
  │ FileSystemPointer.open()                                     │
  │   new FileInputStream(target) throws FileNotFoundException    │
  │        └──► rewrapped as IllegalArgumentException ⇒ 500       │
  │   Occurs if the file is deleted between findFile and open     │
  └──────────────────────────────────────────────────────────────┘

  ┌──────────────────────────────────────────────────────────────┐
  │ ExistingFile.redirectDownload()                              │
  │   new URI("/download/" + uuid + "/" + originalName)          │
  │        throws URISyntaxException if the filename is not       │
  │        URI-safe (server-side filename, not request input)     │
  │        └──► rewrapped as IllegalArgumentException ⇒ 500       │
  └──────────────────────────────────────────────────────────────┘

  ┌──────────────────────────────────────────────────────────────┐
  │ ThrottlingInputStream.read*()                                │
  │   IOException from the target stream propagates UNWRAPPED     │
  │        └──► surfaces mid-response, after headers are already  │
  │             committed ⇒ truncated response, not a clean 500   │
  └──────────────────────────────────────────────────────────────┘

  ┌──────────────────────────────────────────────────────────────┐
  │ FileSystemStorage.findFile()                                 │
  │   getResource("/logback.xml") returns null if absent          │
  │        └──► NullPointerException on resource.getFile() ⇒ 500  │
  │   No null check is performed                                  │
  └──────────────────────────────────────────────────────────────┘
```

### Error handling assessment
| Aspect | State |
|---|---|
| `@ControllerAdvice` / global exception handler | **None** — all unhandled exceptions reach Boot's default handler |
| Empty `catch` blocks (swallowed exceptions) | **None** — exceptions are rewrapped, never discarded |
| Cause preservation | Yes — each rewrap passes the cause, so stack traces are retained (and may surface internal paths in a `500` body) |
| Exception type appropriateness | Poor — `IOException` and `URISyntaxException` become `IllegalArgumentException`, conflating environment failure with caller error |
| Mid-stream failure handling | None — an `IOException` during streaming truncates an already-committed response |
| Null-safety at the resource boundary | Missing in `FileSystemStorage` |

Recommended remediation: add a `@ControllerAdvice` mapping storage I/O failures to a generic `500` without exception detail, and replace `IllegalArgumentException` with `UncheckedIOException` or a domain-specific exception. Tracked as P3.6 and S11 in [../technical-debt/remediation-plan.md](../technical-debt/remediation-plan.md) and [../analysis/security-patterns.md](../analysis/security-patterns.md).
