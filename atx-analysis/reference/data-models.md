# Data Models

[← Back to index](../README.md) · Related: [interfaces.md](interfaces.md) · [api-reference.md](api-reference.md)

There is no database, ORM, or serialized persistence model in this project. The "data model" consists of in-memory domain types plus the HTTP representation of a file.

## Domain Types

### `FilePointer` (interface) — logical file descriptor
| Attribute (accessor) | Type | Semantics |
|---|---|---|
| content (`open()`) | `InputStream` | lazily opened byte source |
| size (`getSize()`) | `long` | bytes |
| originalName (`getOriginalName()`) | `String` | filename for the canonical URL |
| etag (`getEtag()`) | `String` | quoted strong validator |
| mediaType (`getMediaType()`) | `Optional<com.google.common.net.MediaType>` | absent = unknown |
| lastModified (`getLastModified()`) | `java.time.Instant` | modification time |

### `FileSystemPointer` — filesystem-backed implementation
| Field | Type | Derivation |
|---|---|---|
| `target` | `java.io.File` | supplied to the constructor |
| `tag` | `com.google.common.hash.HashCode` | `Files.hash(target, Hashing.sha512())`, computed eagerly |
| `mediaTypeOrNull` | `com.google.common.net.MediaType` (nullable) | `MediaType.parse(Files.probeContentType(path))`, null when probing fails |

Invariants: all fields `final`; the instance is immutable and its ETag is a snapshot taken at construction (if the underlying file changes afterwards, the pointer is stale).

### `ExistingFile` — per-request request/response context
| Field | Type | Source |
|---|---|---|
| `method` | `org.springframework.http.HttpMethod` | bound from the request (`GET` or `HEAD`) |
| `filePointer` | `FilePointer` | resolved from `FileStorage` |
| `uuid` | `java.util.UUID` | path variable |

### `ThrottlingInputStream` — streaming wrapper
| Field | Type | Role |
|---|---|---|
| `target` | `InputStream` | delegate |
| `maxBytesPerSecond` | `com.google.common.util.concurrent.RateLimiter` | permits = bytes; created at `ONE_MB` per request |

## Request Model (inbound binding)

| Element | Java type | Binding |
|---|---|---|
| `uuid` path segment | `java.util.UUID` | `@PathVariable` — Spring converts the string; a malformed UUID yields a binding failure (`400`-class) before controller code runs |
| `filename` path segment | not bound | present in the URL template only, so the saved filename is client-visible; the served bytes are chosen by UUID alone |
| HTTP verb | `org.springframework.http.HttpMethod` | resolved argument |
| `If-None-Match` | `Optional<String>` | `@RequestHeader(IF_NONE_MATCH)` |
| `If-Modified-Since` | `Optional<java.util.Date>` | `@RequestHeader(IF_MODIFIED_SINCE)` — Spring parses RFC 1123; converted to `Instant` via `Date::toInstant` in `ExistingFile.cached` |

## Response Model (outbound representation)

| Status | Body | Headers |
|---|---|---|
| `200 OK` | `InputStreamResource` over `ThrottlingInputStream` (`GET`) or `null` (`HEAD`) | `ETag`, `Content-Length`, `Last-Modified`, `Content-Type` (when known) |
| `301 MOVED_PERMANENTLY` | `null` | `Location: /download/{uuid}/{originalName}` |
| `304 NOT_MODIFIED` | `null` | `ETag`, `Content-Length`, `Last-Modified`, `Content-Type` (when known) |
| `404 NOT_FOUND` | empty | none set by the application |

Carrier type: `org.springframework.http.ResponseEntity<org.springframework.core.io.Resource>` throughout.

## Type Conversions

```
java.io.File ──(FileSystemPointer ctor)──> HashCode ──> quoted ETag String
java.io.File ──(probeContentType)──> String ──> guava MediaType ──(ExistingFile.toMediaType)──> spring MediaType
long epochMillis ──> java.time.Instant ──(toEpochMilli)──> Last-Modified header
java.util.Date (header) ──(Date::toInstant)──> Instant ──(modifiedAfter)──> boolean
InputStream ──(ThrottlingInputStream)──> InputStream ──(InputStreamResource)──> Resource
```

## Fixture Data

| Fixture | Value |
|---|---|
| `FileExamples.TXT_FILE_UUID` | random `UUID` generated per JVM run |
| `FileExamples.TXT_FILE` | `FileSystemPointer` over classpath `/download.txt` |
| `FileExamples.NOT_FOUND_UUID` | random `UUID`, never resolvable |
| `src/test/resources/download.txt` | text `foobar` (6 bytes, `text/plain`) |

## Relationship Diagram

```
FileStorage ──returns Optional of──> FilePointer <──implements── FileSystemPointer ──wraps──> java.io.File
     ^                                    ^
     │                                    │ held by
 injected into                            │
DownloadController ──creates per request──> ExistingFile ──produces──> ResponseEntity<Resource>
                                                 │
                                                 └──wraps pointer stream in──> ThrottlingInputStream
```
