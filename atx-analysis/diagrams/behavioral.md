# Behavioral Diagrams

[← Back to index](../README.md)

Runtime flows of the application's business logic. Build, test, lint, and publish flows are intentionally out of scope.

## Sequence 1 — Full download (`GET /download/{uuid}/{filename}`, not cached)

```
Client   DownloadController   FileStorage   FilePointer   ExistingFile   ThrottlingInputStream
  │             │                  │            │              │                 │
  │─GET /download/{uuid}/{file}──▶ │            │              │                 │
  │             │                  │            │              │                 │
  │             │─findFile(uuid)──▶│            │              │                 │
  │             │                  │─new FileSystemPointer(file)                 │
  │             │                  │   ├─ Files.hash(SHA-512)  │  O(file size)   │
  │             │                  │   └─ probeContentType()   │                 │
  │             │◀─Optional.of(ptr)┤            │              │                 │
  │             │                  │            │              │                 │
  │             │─new ExistingFile(method, ptr, uuid)─────────▶ │                 │
  │             │─handle(etagOpt, ifModSinceOpt)─────────────▶ │                 │
  │             │                  │            │              │                 │
  │             │                  │            │◀─matchesEtag("")──┤ (empty)     │
  │             │                  │            │◀─modifiedAfter()──┤ (empty)     │
  │             │                  │            │              │ cached() = false │
  │             │                  │            │              │                 │
  │             │                  │            │◀─open()──────┤                 │
  │             │                  │            │─BufferedInputStream─▶           │
  │             │                  │            │              │─RateLimiter.create(1 MB)
  │             │                  │            │              │─new ThrottlingInputStream──▶
  │             │                  │            │              │─new InputStreamResource    │
  │             │                  │            │              │                 │
  │             │                  │            │◀─getEtag()───┤                 │
  │             │                  │            │◀─getSize()───┤                 │
  │             │                  │            │◀─getLastModified()──┤           │
  │             │                  │            │◀─getMediaType()─────┤           │
  │             │                  │            │              │ toMediaType()   │
  │             │◀─ResponseEntity(200, headers, resource)─────┤                  │
  │             │                  │            │              │                 │
  │◀─200 OK + headers──────────────┤            │              │                 │
  │             │                  │            │              │                 │
  │◀═ body bytes (container reads the resource) ═══════════════════════════════▶ │
  │             │                  │            │              │   acquire(len)  │
  │             │                  │            │              │   BLOCKS to     │
  │             │                  │            │              │   1 MB/s        │
```

Note: the body is streamed by the servlet container **after** the handler returns. The `acquire` calls therefore block a container worker thread for the whole transfer.

## Sequence 2 — Conditional request returning `304`

```
Client            DownloadController      FileStorage      FilePointer      ExistingFile
  │                       │                    │                │                │
  │─GET /{uuid}/{file}───▶│                    │                │                │
  │  If-None-Match: "<etag>"                   │                │                │
  │                       │─findFile(uuid)────▶│                │                │
  │                       │                    │─new FileSystemPointer──▶        │
  │                       │                    │   (full SHA-512 recomputed)     │
  │                       │◀─Optional.of(ptr)──┤                │                │
  │                       │─handle(Optional.of("<etag>"), empty)───────────────▶  │
  │                       │                    │                │  cached():     │
  │                       │                    │                │◀─matchesEtag───┤
  │                       │                    │                │──true─────────▶│
  │                       │                    │                │  → notModified()│
  │                       │                    │                │◀─getEtag()─────┤
  │                       │                    │                │◀─getSize()─────┤
  │                       │                    │                │◀─getLastModified()
  │                       │                    │                │◀─getMediaType()─┤
  │                       │◀─ResponseEntity(304, headers, null)────────────────┤ │
  │◀─304 Not Modified ────┤                    │                │                │
  │   ETag, Content-Length, Last-Modified, Content-Type · no body                │

  The file is never opened. The hash is still computed in full.
```

## Sequence 3 — Redirect (`GET /download/{uuid}`)

```
Client            DownloadController      FileStorage      ExistingFile
  │                       │                    │                │
  │─GET /download/{uuid}─▶│                    │                │
  │                       │─findFile(uuid)────▶│                │
  │                       │◀─Optional.of(ptr)──┤                │
  │                       │─redirect(etagOpt, ifModSinceOpt)──▶ │
  │                       │                    │  cached()=false│
  │                       │                    │  redirectDownload():
  │                       │                    │   getOriginalName()
  │                       │                    │   new URI("/download/"+uuid+"/"+name)
  │                       │◀─ResponseEntity(301, Location, null)│
  │◀─301 Moved Permanently┤                    │                │
  │   Location: /download/{uuid}/{originalName}                 │
  │                       │                    │                │
  │─GET /download/{uuid}/{originalName}──────────────────────▶  │  (Sequence 1)
```

The `301` response carries only `Location` — unlike the `200`/`304` paths it does not pass through `ExistingFile.response`, so it omits `ETag`, `Content-Length`, and `Last-Modified`.

## Sequence 4 — `HEAD` request

```
Client            DownloadController      FilePointer      ExistingFile
  │                       │                    │                │
  │─HEAD /{uuid}/{file}──▶│                    │                │
  │                       │─findFile ─▶ Optional.of(ptr)        │
  │                       │─handle(empty, empty)──────────────▶ │
  │                       │                    │  cached()=false│
  │                       │                    │  serveDownload():
  │                       │                    │   resourceToReturn():
  │                       │                    │     method != GET → return null
  │                       │                    │     (open() never called)
  │                       │                    │◀─getEtag/getSize/
  │                       │                    │  getLastModified/getMediaType
  │                       │◀─ResponseEntity(200, headers, null)─┤
  │◀─200 OK, headers only ┤                    │                │
  │   identical headers to GET · empty body                     │
```

## Sequence 5 — File not found

```
Client            DownloadController      FileStorage
  │                       │                    │
  │─GET /{uuid}/{file}───▶│                    │
  │                       │─findFile(uuid)────▶│
  │                       │◀─Optional.empty()──┤
  │                       │  .map(..) skipped  │
  │                       │  .orElseGet(() -> new ResponseEntity<>(NOT_FOUND))
  │◀─404 Not Found ───────┤                    │
  │   no headers, no body │                    │

  ⚠ Unreachable in production: FileSystemStorage never returns empty.
    Exercised only via FileStorageStub under the "test" profile.
```

## Activity Diagram — request handling

```
                         ┌──────────────────┐
                         │  HTTP request    │
                         │  /download/**    │
                         └────────┬─────────┘
                                  ▼
                       ┌──────────────────────┐
                       │ Spring binds uuid    │
                       │ to java.util.UUID    │
                       └──────┬───────────────┘
                     invalid  │  valid
                   ┌──────────┴──────────┐
                   ▼                     ▼
            ┌─────────────┐    ┌────────────────────────┐
            │ 400 Bad Req │    │ storage.findFile(uuid) │
            └─────────────┘    └──────┬─────────────────┘
                                      ▼
                            ┌───────────────────┐
                            │ pointer present?  │
                            └───┬───────────┬───┘
                             no │           │ yes
                                ▼           ▼
                        ┌───────────┐  ┌──────────────────────┐
                        │ 404       │  │ new ExistingFile(    │
                        │ Not Found │  │   method, ptr, uuid) │
                        └───────────┘  └──────┬───────────────┘
                                              ▼
                                   ┌─────────────────────────┐
                                   │ cached(etag, ifModSince)│
                                   │  etag matches? OR       │
                                   │  client copy current?   │
                                   └───┬─────────────────┬───┘
                                 yes   │                 │  no
                                       ▼                 ▼
                             ┌──────────────┐   ┌──────────────────┐
                             │ 304 Not      │   │ which endpoint?  │
                             │ Modified     │   └──┬────────────┬──┘
                             │ headers only │ /{uuid}      /{uuid}/{file}
                             └──────────────┘      ▼            ▼
                                        ┌────────────────┐  ┌──────────────┐
                                        │ 301 + Location │  │ method?      │
                                        │ (no body)      │  └──┬────────┬──┘
                                        └────────────────┘  HEAD│        │GET
                                                               ▼        ▼
                                                  ┌──────────────┐ ┌─────────────────┐
                                                  │ 200, headers │ │ open()          │
                                                  │ body = null  │ │ RateLimiter 1MB │
                                                  └──────────────┘ │ Throttling →    │
                                                                   │ InputStreamRes. │
                                                                   │ 200 + body      │
                                                                   └─────────────────┘
```

## Activity Diagram — cache evaluation (`ExistingFile.cached`)

```
      ┌─────────────────────────────────────────────────┐
      │ inputs: Optional<String> ifNoneMatch            │
      │         Optional<Date>   ifModifiedSince        │
      └───────────────────┬─────────────────────────────┘
          ┌───────────────┴────────────────┐
          ▼                                ▼
  ┌──────────────────┐           ┌────────────────────────┐
  │ ifNoneMatch      │           │ ifModifiedSince        │
  │ present?         │           │ present?               │
  └──┬────────────┬──┘           └───┬────────────────┬───┘
  no │            │ yes           no │                │ yes
     ▼            ▼                  ▼                ▼
 matchingEtag  filePointer      notModifiedSince   Date::toInstant
   = false     .matchesEtag()     = false                │
                    │                                    ▼
                    ▼                        filePointer.modifiedAfter(instant)
              matchingEtag                   = !clientTime.isBefore(lastModified)
                    │                                    │
                    └──────────────┬─────────────────────┘
                                   ▼
                     ┌───────────────────────────────┐
                     │ matchingEtag || notModifiedSince│
                     └───┬───────────────────────┬───┘
                     true│                       │false
                         ▼                       ▼
                  ┌─────────────┐      ┌────────────────────┐
                  │ 304         │      │ apply notCached    │
                  │ notModified │      │ strategy function  │
                  └─────────────┘      └────────────────────┘
```

Business rules encoded here:
1. A matching `If-None-Match` ETag alone is sufficient to return `304`.
2. `If-Modified-Since` yields `304` when the client's timestamp is **not before** the server's last-modified instant — i.e. a client timestamp equal to *or later than* the server's counts as current.
3. The two conditions are OR-combined; either is sufficient.
4. A missing or unparseable header is treated as "not cached" (`orElse(false)`), never as an error.

## State Diagram — `ThrottlingInputStream` during a transfer

```
      ┌──────────┐
      │ Created  │  target stream open, RateLimiter with 1 MB/s permits
      └────┬─────┘
           │ container calls read(byte[], off, len)
           ▼
      ┌─────────────────────┐
      │ Acquiring permits   │──── permits unavailable ──┐
      │ RateLimiter.acquire │                           │
      └────┬────────────────┘                           ▼
           │ permits granted                   ┌─────────────────┐
           ▼                                   │ Blocked         │
      ┌─────────────────────┐                  │ (servlet thread │
      │ Reading             │                  │  parked)        │
      │ target.read(..)     │◀─────────────────┴─────────────────┘
      └────┬───────────┬────┘      permits replenished
           │ n > 0     │ n == -1
           ▼           ▼
    ┌───────────┐  ┌──────────┐
    │ Streaming │  │ Exhausted│
    │ (loop) ───┘  └────┬─────┘
    └───────────        │ container calls close()
                        ▼
                  ┌──────────┐
                  │ Closed   │  target.close()
                  └──────────┘

  skip(n) transitions Reading → Reading without acquiring permits.
```

## State Diagram — file resource lifecycle per request

```
   ┌──────────────┐
   │ Unidentified │  UUID received from the path
   └──────┬───────┘
          │ storage.findFile(uuid)
     ┌────┴─────┐
     ▼          ▼
┌─────────┐ ┌────────────────────┐
│ Absent  │ │ Identified         │  FileSystemPointer constructed:
│ → 404   │ │                    │  content hashed (SHA-512), type probed
└─────────┘ └──────┬─────────────┘
                   │ cached() evaluated
          ┌────────┴─────────┐
          ▼                  ▼
   ┌─────────────┐    ┌────────────────┐
   │ Validated   │    │ Stale          │
   │ → 304       │    │                │
   │ never opened│    └───┬────────┬───┘
   └─────────────┘   HEAD │        │ GET
                          ▼        ▼
                  ┌──────────┐ ┌──────────────┐
                  │ Described│ │ Opened       │
                  │ → 200,   │ │ → 200 + body │
                  │  no body │ │  (throttled) │
                  │ never    │ └──────┬───────┘
                  │ opened   │        │ transfer completes
                  └──────────┘        ▼
                                ┌──────────┐
                                │ Closed   │
                                └──────────┘
```

## Data Flow Diagram

```
  ┌────────┐  UUID, If-None-Match, If-Modified-Since
  │ Client │──────────────────────────────────────────┐
  └────────┘                                         ▼
       ▲                            ┌─────────────────────────────────┐
       │                            │ DownloadController              │
       │                            │  String → UUID   (Spring)       │
       │                            │  String → Date   (Spring)       │
       │                            └──────┬──────────────────────────┘
       │                                   │ UUID
       │                                   ▼
       │                            ┌─────────────────────────────────┐
       │                            │ FileStorage / FileSystemStorage │
       │                            └──────┬──────────────────────────┘
       │                                   │ File handle
       │                                   ▼
       │                            ┌─────────────────────────────────┐
       │                            │ FileSystemPointer               │
       │   ┌────────────────────────┤  File ──▶ HashCode   (SHA-512)  │◀── filesystem
       │   │  metadata              │  Path ──▶ MediaType  (probe)    │     (bytes,
       │   │                        │  File ──▶ size, lastModified    │      size,
       │   │                        └──────┬──────────────────────────┘      mtime)
       │   │                               │ etag, size, instant, mediaType
       │   │                               ▼
       │   │                        ┌─────────────────────────────────┐
       │   │   Date → Instant ─────▶│ ExistingFile                    │
       │   │   guava.MediaType ────▶│  cached()  → boolean            │
       │   │     → spring.MediaType │  response() → ResponseEntity    │
       │   │   Instant → epochMillis│                                 │
       │   └───────────────────────▶└──────┬──────────────────────────┘
       │                                   │ InputStream (GET only)
       │                                   ▼
       │                            ┌─────────────────────────────────┐
       │                            │ ThrottlingInputStream           │
       │                            │  RateLimiter gates byte flow    │
       │                            └──────┬──────────────────────────┘
       │                                   │ throttled bytes
       │        status + headers + body    ▼
       └───────────────────────── InputStreamResource

  Side channel: SLF4J → Logback → console
    ExistingFile      (DEBUG serve, TRACE redirect/304)
    FileSystemStorage (DEBUG requested UUID)
```

## Error and Exception Flows

```
  FileSystemPointer constructor
      IOException ──────────────▶ IllegalArgumentException ──▶ (no handler) ──▶ 500
  FileSystemPointer.open()
      FileNotFoundException ────▶ IllegalArgumentException ──▶ (no handler) ──▶ 500
  ExistingFile.redirectDownload()
      URISyntaxException ───────▶ IllegalArgumentException ──▶ (no handler) ──▶ 500
  FileSystemStorage.findFile()
      missing classpath resource ──▶ NullPointerException ───▶ 500
  DownloadController
      malformed UUID ──▶ Spring type-conversion failure ─────▶ 400
      Optional.empty() from storage ─────────────────────────▶ 404  (not an exception)
```

There is no `@ControllerAdvice` or `@ExceptionHandler` in the codebase, so all unchecked exceptions fall through to Spring Boot's default error handling. The wrapped original messages typically contain absolute filesystem paths.

## Behavioral Invariants

| Invariant | Enforced by |
|---|---|
| `HEAD` and `GET` produce identical headers | Both share `ExistingFile.serveDownload`; only `resourceToReturn` differs |
| `HEAD` never opens the file | `resourceToReturn` returns `null` before `buildResource` is reached |
| `304` never opens the file | `serveWithCaching` short-circuits to `notModified` |
| `304` carries the same validators as `200` | Both call `ExistingFile.response` |
| Absence is never an exception | `Optional<FilePointer>` contract on `FileStorage` |
| Missing conditional headers mean "not cached" | `orElse(false)` in `cached()` |
| Body bytes are always rate-limited on `GET` | `buildResource` is the only path that wraps the stream |
| Only safe HTTP methods are accepted | `@RequestMapping(method = {GET, HEAD})` on both mappings |

See [../specialized/http-caching.md](../specialized/http-caching.md) for the caching rules and [../migration/test-specifications.md](../migration/test-specifications.md) for the specifications that pin these behaviors.
