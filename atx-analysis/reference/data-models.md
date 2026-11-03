# Data Models

[← Back to index](../README.md) · [Program structure](program-structure.md)

This service has **no persistence layer and no serialized DTOs**. Its "data model" consists of the in-memory types that carry file identity, file metadata, and HTTP response state. No entity, table, schema, or JSON contract exists in the repository.

## Domain Types

### `FilePointer` (abstract file handle)
Interface; see [interfaces.md](interfaces.md) for the full contract.

**Conceptual attributes** exposed by the interface:

| Attribute | Type | Accessor | Purpose |
|---|---|---|---|
| content stream | `InputStream` | `open()` | Response body source |
| size | `long` | `getSize()` | `Content-Length` |
| original name | `String` | `getOriginalName()` | `301` `Location` path segment |
| entity tag | `String` | `getEtag()` | `ETag`, `If-None-Match` comparison |
| media type | `Optional<com.google.common.net.MediaType>` | `getMediaType()` | `Content-Type` |
| last modified | `Instant` | `getLastModified()` | `Last-Modified`, `If-Modified-Since` comparison |

### `FileSystemPointer` (concrete, immutable)

| Field | Type | Mutability | Derivation |
|---|---|---|---|
| `target` | `java.io.File` | `final` | Constructor argument |
| `tag` | `com.google.common.hash.HashCode` | `final` | `Files.hash(target, Hashing.sha512())` — computed eagerly in the constructor |
| `mediaTypeOrNull` | `com.google.common.net.MediaType` | `final`, **nullable** | `MediaType.parse(java.nio.file.Files.probeContentType(target.toPath()))`, or `null` when the probe returns `null` |

Derived values (not stored):
- `getSize()` → `target.length()` (read from the filesystem on each call)
- `getLastModified()` → `Instant.ofEpochMilli(target.lastModified())` (read on each call)
- `getEtag()` → `"\"" + tag + "\""`

**Invariants:** fully immutable after construction; the constructor either completes with a valid hash and probed type, or throws `IllegalArgumentException`. Note that `size` and `lastModified` are read live from the filesystem while `tag` is cached from construction time — if the underlying file changes between construction and header assembly, the ETag and the size/timestamp can disagree.

### `ExistingFile` (request-scoped value object)

| Field | Type | Mutability | Source |
|---|---|---|---|
| `method` | `org.springframework.http.HttpMethod` | `final` | Request method, bound by Spring MVC |
| `filePointer` | `FilePointer` | `final` | Result of `FileStorage.findFile` |
| `uuid` | `java.util.UUID` | `final` | `@PathVariable` |

Immutable; constructed once per request in `DownloadController.findExistingFile` and discarded after the response is built. Represents the state "this file exists and here is the request context for it".

### `ThrottlingInputStream` (stateful decorator)

| Field | Type | Mutability |
|---|---|---|
| `target` | `java.io.InputStream` | `final` reference (the stream itself is stateful) |
| `maxBytesPerSecond` | `com.google.common.util.concurrent.RateLimiter` | `final` reference (the limiter carries internal permit state) |

## Identifier Model

| Identifier | Type | Generation | Scope |
|---|---|---|---|
| File ID | `java.util.UUID` | Externally supplied in the request path | Opaque to the service |
| Entity tag | `String` (quoted SHA-512 hex) | Derived from file content | Globally stable for identical content |

**UUID handling:** `@PathVariable UUID uuid` relies on Spring's built-in `String`→`UUID` converter. A malformed UUID is rejected by the framework before the handler runs. This is the service's only input validation.

**ETag format:** `"<128 hex characters>"` — a quoted SHA-512 digest, i.e. a strong validator per RFC 7232. Produced identically by `FileSystemPointer.getEtag()` and `Sha512ShallowEtagHeaderFilter.generateETagHeaderValue`.

## HTTP Representation Model

Request-side bound types:

| Source | Bound type | Binding |
|---|---|---|
| Path segment `{uuid}` | `java.util.UUID` | `@PathVariable` |
| Path segment `{filename}` | — | **Not bound**; present only so browsers infer the save-as name |
| Request method | `org.springframework.http.HttpMethod` | Spring argument resolver |
| `If-None-Match` | `Optional<String>` | `@RequestHeader(IF_NONE_MATCH)` |
| `If-Modified-Since` | `Optional<java.util.Date>` | `@RequestHeader(IF_MODIFIED_SINCE)` — RFC 1123 string converted to `Date` by Spring |

Response-side types:

| Type | Role |
|---|---|
| `ResponseEntity<org.springframework.core.io.Resource>` | The single return type of both handlers |
| `org.springframework.core.io.InputStreamResource` | Body wrapper for the `200` path; `null` for `HEAD`, `301`, and `304` |
| `org.springframework.http.HttpStatus` | `OK`, `MOVED_PERMANENTLY`, `NOT_MODIFIED`, `NOT_FOUND` |
| `org.springframework.http.MediaType` | `Content-Type`, converted from Guava's `MediaType` in `ExistingFile.toMediaType` |

No request or response body is ever serialized from or to a Java object — Jackson is on the classpath via `spring-boot-starter-web` but is unused.

## Type Conversion Map

| From | To | Where |
|---|---|---|
| `String` (path) | `UUID` | Spring converter, `@PathVariable` |
| `String` (header) | `Date` | Spring converter, `@RequestHeader` |
| `Date` | `Instant` | `Date::toInstant` — `ExistingFile.cached` |
| `long` (epoch millis) | `Instant` | `Instant.ofEpochMilli` — `FileSystemPointer.getLastModified` |
| `Instant` | `long` (epoch millis) | `getLastModified().toEpochMilli()` — `ExistingFile.response` |
| `Instant` | RFC 1123 `String` | `DateTimeFormatter.RFC_1123_DATE_TIME` — test helper `toDateHeader` |
| `String` (probe result) | Guava `MediaType` | `MediaType.parse` — `FileSystemPointer` constructor |
| Guava `MediaType` | Spring `MediaType` | `ExistingFile.toMediaType` |
| `byte[]` / `File` | `HashCode` | `Hashing.sha512()` — `FileSystemPointer`, `Sha512ShallowEtagHeaderFilter` |
| `HashCode` | quoted `String` | `getEtag()` |

Note the three-way time representation (`Date` at the HTTP boundary, `Instant` in the domain, epoch millis at the response boundary). The `java.util.Date` hop exists only because of the Spring 4.1-era header converter and is the most upgrade-fragile conversion in the set (TD-19).

## Relationship Diagram

```
          UUID ────identifies────▶ FilePointer ◀──implements── FileSystemPointer
            │                           │                            │
            │                           │                            ├─▶ File        (target)
            │                           │                            ├─▶ HashCode    (tag, SHA-512)
            │                           │                            └─▶ MediaType?  (nullable)
            │                           │
            └────────┐        ┌─────────┘
                     ▼        ▼
                   ExistingFile ──builds──▶ ResponseEntity<Resource>
                     ▲                              ▲
                  HttpMethod                        │
                                      InputStreamResource ──wraps──▶ ThrottlingInputStream
                                                                            │
                                                                     ┌──────┴──────┐
                                                                InputStream   RateLimiter
```

## Persistence, Messaging, and External Schemas

| Concern | Status |
|---|---|
| Relational database | None — no JDBC, no JPA, no `persistence.xml`, no migrations |
| NoSQL | None |
| ORM mappings | None — no `*.hbm.xml`, no `*Mapper.xml` |
| Message queues / events | None |
| Serialization contracts | None — no `.proto`, `.avsc`, `.thrift`, `.graphql`, OpenAPI, or WSDL |
| Caching store | None — HTTP caching only, via client-side validators |
| File fixtures | `src/test/resources/download.txt` (content `foobar`, 6 bytes), `src/main/resources/logback.xml` (served by the production stub) |

See [api-reference.md](api-reference.md) for the HTTP contract and [../specialized/http-caching.md](../specialized/http-caching.md) for the caching semantics.
