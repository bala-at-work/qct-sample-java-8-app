# Behavioral Diagrams

[← Back to index](../README.md) · Related: [structural.md](structural.md) · [../reference/api-reference.md](../reference/api-reference.md)

These diagrams cover **core application business flows only**. Build, test, and packaging flows are deliberately excluded.

## 1. Sequence — Successful file download (`GET /download/{uuid}/{filename}`)

```
Client   Tomcat/MVC   DownloadController   FileStorage   FileSystemPointer   ExistingFile   ThrottlingInputStream
  │          │                │                 │               │                 │                  │
  ├─GET─────►│                │                 │               │                 │                  │
  │          ├─bind uuid,─────►│                 │               │                 │                  │
  │          │  headers       │                 │               │                 │                  │
  │          │                ├─findFile(uuid)─►│               │                 │                  │
  │          │                │                 ├─new(File)────►│                 │                  │
  │          │                │                 │               ├ SHA-512 hash    │                  │
  │          │                │                 │               ├ probeContentType│                  │
  │          │                │                 │◄─pointer──────┤                 │                  │
  │          │                │◄─Optional.of()──┤               │                 │                  │
  │          │                ├─new ExistingFile(method,pointer,uuid)────────────►│                  │
  │          │                ├─handle(etagOpt, ifModifiedSinceOpt)──────────────►│                  │
  │          │                │                 │               │   cached()? ────┤                  │
  │          │                │                 │               │◄─matchesEtag/───┤                  │
  │          │                │                 │               │  modifiedAfter  │                  │
  │          │                │                 │               │   = false       │                  │
  │          │                │                 │               │◄─open()─────────┤                  │
  │          │                │                 │               ├─InputStream────►│                  │
  │          │                │                 │               │                 ├─new(stream,─────►│
  │          │                │                 │               │                 │  RateLimiter 1MB)│
  │          │                │                 │               │  build response │                  │
  │          │                │                 │               │  200 + ETag +   │                  │
  │          │                │                 │               │  Content-Length+│                  │
  │          │                │                 │               │  Last-Modified +│                  │
  │          │                │                 │               │  Content-Type   │                  │
  │          │                │◄─ResponseEntity<Resource>───────────────────────┤                   │
  │          │◄───────────────┤                 │               │                 │                  │
  │          │  copy body ────────────────────────────────────────────────────────────────────────►│
  │          │                │                 │               │                 │  acquire(n);     │
  │          │                │                 │               │                 │  target.read(..) │
  │◄─200 + throttled bytes────┤                 │               │                 │                  │
```

## 2. Sequence — Conditional request returning `304`

```
Client            DownloadController      FileStorage      ExistingFile      FilePointer
  │                        │                   │                │                │
  ├─GET /download/{uuid}/f ─►│                 │                │                │
  │  If-None-Match: "<etag>"│                  │                │                │
  │                        ├─findFile(uuid)───►│                │                │
  │                        │◄─Optional.of(ptr)─┤                │                │
  │                        ├─handle(etag, ∅)──────────────────►│                │
  │                        │                   │   cached() ────┼─matchesEtag───►│
  │                        │                   │                │◄─true──────────┤
  │                        │                   │   notModified()│                │
  │                        │                   │   304 + ETag + Content-Length + │
  │                        │                   │   Last-Modified, body = null    │
  │◄─304 Not Modified ─────┤◄──────────────────────────────────┤                │
  │   (no body)            │                   │                │                │
```

## 3. Sequence — `HEAD` request (headers only)

```
Client            DownloadController      ExistingFile              FilePointer
  │                       │                    │                        │
  ├─HEAD /download/{u}/f ─►│                   │                        │
  │                       ├─handle(∅, ∅)─────►│                        │
  │                       │                    ├ cached() = false       │
  │                       │                    ├ serveDownload()        │
  │                       │                    ├ resourceToReturn():    │
  │                       │                    │   method != GET ⇒ null │
  │                       │                    ├ response(ptr, OK, null)│
  │                       │                    │   ◄─getEtag/getSize/───┤
  │                       │                    │     getLastModified/   │
  │                       │                    │     getMediaType       │
  │◄─200 OK, full headers,│◄───────────────────┤                        │
  │  empty body           │                    │                        │
```

Note: `open()` is never called for `HEAD`, so no file bytes are read — but the SHA-512 ETag was still computed when the pointer was constructed.

## 4. Sequence — Canonicalizing redirect (`GET /download/{uuid}`)

```
Client            DownloadController      FileStorage      ExistingFile
  │                       │                    │                │
  ├─GET /download/{uuid}──►│                   │                │
  │                       ├─findFile(uuid)────►│                │
  │                       │◄─Optional.of(ptr)──┤                │
  │                       ├─redirect(etag, ims)───────────────►│
  │                       │                    │  cached()?     │
  │                       │                    │   true  ⇒ 304  │
  │                       │                    │   false ⇒ redirectDownload():
  │                       │                    │     301 + Location:
  │                       │                    │     /download/{uuid}/{originalName}
  │◄─301 / 304 ───────────┤◄──────────────────────────────────┤
```

## 5. Sequence — Unknown UUID (`404`)

```
Client            DownloadController      FileStorage
  │                       │                    │
  ├─GET /download/{unknown}/f─►│               │
  │                       ├─findFile(uuid)────►│
  │                       │◄─Optional.empty()──┤
  │                       ├ map(...) skipped;  │
  │                       │ orElseGet ⇒ new ResponseEntity<>(NOT_FOUND)
  │◄─404 Not Found, empty ┤                    │
```

## 6. Activity Diagram — Request handling decision flow

```
                      ┌───────────────────┐
                      │ HTTP GET or HEAD  │
                      └─────────┬─────────┘
                                ▼
                    ┌───────────────────────┐
                    │ Bind uuid (UUID type) │
                    │ + If-None-Match       │
                    │ + If-Modified-Since   │
                    └───────────┬───────────┘
                                │ binding failure ──► 400-class (framework)
                                ▼
                     ┌────────────────────┐
                     │ storage.findFile() │
                     └─────────┬──────────┘
                               ▼
                       ┌───────────────┐
                       │ present?      │
                       └───┬───────┬───┘
                      no   │       │  yes
                           ▼       ▼
                    ┌──────────┐  ┌──────────────────────────────┐
                    │ 404      │  │ new ExistingFile(...)        │
                    └──────────┘  └──────────────┬───────────────┘
                                                 ▼
                                   ┌─────────────────────────────┐
                                   │ cached() :                  │
                                   │  etag matches  OR           │
                                   │  client copy not stale      │
                                   └──────┬───────────────┬──────┘
                                     yes  │               │ no
                                          ▼               ▼
                               ┌──────────────┐   ┌──────────────────────┐
                               │ 304          │   │ which endpoint?      │
                               │ headers only │   └───┬──────────────┬───┘
                               └──────────────┘  /{uuid}        /{uuid}/{file}
                                                     │              │
                                                     ▼              ▼
                                            ┌────────────┐  ┌──────────────────┐
                                            │ 301 +      │  │ method == GET ?  │
                                            │ Location   │  └──┬───────────┬───┘
                                            └────────────┘ yes │           │ no
                                                               ▼           ▼
                                                   ┌──────────────┐ ┌──────────────┐
                                                   │ 200 +        │ │ 200 +        │
                                                   │ throttled    │ │ headers only │
                                                   │ stream       │ │ (null body)  │
                                                   └──────────────┘ └──────────────┘
```

## 7. Activity Diagram — `FileSystemPointer` construction (business rules for metadata)

```
   ┌──────────────────────┐
   │ new FileSystemPointer│
   │        (file)        │
   └──────────┬───────────┘
              ▼
   ┌───────────────────────────────┐
   │ Files.hash(file, SHA-512)     │  ◄── O(file size) read + digest
   └──────────┬────────────────────┘
              ▼
   ┌───────────────────────────────┐
   │ Files.probeContentType(path)  │
   └──────────┬────────────────────┘
              ▼
       ┌──────────────┐
       │ type != null?│
       └──┬────────┬──┘
     yes  │        │ no
          ▼        ▼
 ┌────────────┐ ┌──────────────────┐
 │ parse into │ │ mediaTypeOrNull  │
 │ MediaType  │ │ = null           │
 └─────┬──────┘ └────────┬─────────┘
       └────────┬────────┘
                ▼
     ┌──────────────────────┐        IOException
     │ immutable pointer    │◄──────────┐
     └──────────────────────┘           │
                                 ┌──────────────────────────┐
                                 │ throw IllegalArgumentExc.│
                                 └──────────────────────────┘
```

## 8. State Machine — Download stream lifecycle

```
            ┌─────────────┐
            │  UNOPENED   │  (pointer exists; no stream)
            └──────┬──────┘
                   │ open()  [GET only]
                   ▼
            ┌─────────────┐
            │   OPEN      │  BufferedInputStream(FileInputStream)
            └──────┬──────┘
                   │ wrapped in ThrottlingInputStream + InputStreamResource
                   ▼
            ┌─────────────────────┐    read(b,off,len)
            │  STREAMING          │◄───────────────────┐
            │  (rate-limited)     │                    │
            └──┬──────────┬───────┘                    │
               │          └── acquire(len) blocks ─────┘
               │ EOF / client disconnect / exception
               ▼
            ┌─────────────┐
            │   CLOSED    │  close() delegates to target
            └─────────────┘

Note: closure is driven by the framework after it copies the resource to the
response. An exception thrown between open() and hand-off would leave the
stream unclosed — see anti-pattern A6 in ../architecture/patterns.md.
```

## 9. State Machine — Client cache state (as seen by the server)

```
                  first request (no validators)
   ┌──────────────┐ ──────────────────────────► ┌──────────────┐
   │ NO COPY      │                              │ FRESH COPY   │
   │              │ ◄────────────────────────────│ (has ETag +  │
   └──────────────┘    200 + ETag/Last-Modified  │ Last-Modified)│
                                                 └───┬──────┬───┘
                                                     │      │
                   If-None-Match matches             │      │  file changes
                   OR client time >= lastModified    │      │  on server
                             ▼                       │      ▼
                      ┌─────────────┐                │  ┌──────────────┐
                      │ 304         │────────────────┘  │ STALE COPY   │
                      │ revalidated │                   └──────┬───────┘
                      └─────────────┘                          │ next request
                                                               ▼
                                                        ┌─────────────┐
                                                        │ 200 + new   │
                                                        │ ETag        │
                                                        └─────────────┘
```

Business rules encoded:
- **BR1**: a strong ETag match alone is sufficient to answer `304` (`ExistingFile.cached`, short-circuit `||`).
- **BR2**: `If-Modified-Since` at or after the file's `Last-Modified` also yields `304` — including a client timestamp *newer* than the server's file (deliberate; pinned by the spec `'should not return file if server has older version than the client'`).
- **BR3**: a `304` still carries `ETag`, `Content-Length`, `Last-Modified`, and `Content-Type`.
- **BR4**: `HEAD` returns identical headers to `GET` with an empty body.
- **BR5**: an unknown UUID yields a bare `404` with no headers.
- **BR6**: `/download/{uuid}` permanently redirects to the filename-bearing canonical URL.
- **BR7**: every `GET` body is throttled to 1 MB/s, per request.

## 10. Data Flow Diagram

```
  ┌────────┐  uuid, If-None-Match, If-Modified-Since   ┌──────────────────┐
  │ Client │ ─────────────────────────────────────────► │ DownloadController│
  └────────┘                                            └─────────┬────────┘
       ▲                                                          │ uuid
       │                                                          ▼
       │                                              ┌───────────────────────┐
       │                                              │ FileStorage           │
       │                                              └───────────┬───────────┘
       │                                                          │ File handle
       │                                                          ▼
       │                                   ┌──────────────────────────────────┐
       │                                   │ FileSystemPointer                │
       │                                   │  file bytes ─► SHA-512 ─► ETag   │
       │                                   │  path ─► probeContentType ─► type│
       │                                   │  File ─► length, name, mtime     │
       │                                   └──────────┬───────────────┬───────┘
       │                                   metadata   │               │ bytes
       │                                              ▼               ▼
       │                              ┌────────────────────┐  ┌──────────────────┐
       │                              │ ExistingFile       │  │ Throttling-      │
       │   headers + status + body    │  validator compare │  │ InputStream      │
       └──────────────────────────────┤  header assembly   │◄─┤ (1 MB/s)         │
                                      └─────────┬──────────┘  └──────────────────┘
                                                │ TRACE/DEBUG
                                                ▼
                                       ┌──────────────────┐
                                       │ Logback console  │
                                       └──────────────────┘

Data stores: none (no database, cache, or queue). The only persistent data is the
file content on the local filesystem.
Sensitive data in flight: file bytes only; no credentials or PII are handled,
and no request data is logged beyond the UUID.
```
