# Data Models

- [Back to README](../README.md)
- Siblings: [program-structure.md](program-structure.md) · [interfaces.md](interfaces.md) · [api-reference.md](api-reference.md) · [modules.md](modules.md)

## Table of Contents
1. [Summary](#summary)
2. [Domain Types](#domain-types)
3. [Type Relationship Diagram](#type-relationship-diagram)
4. [Field Reference](#field-reference)
5. [Identifier Model](#identifier-model)
6. [Time Model](#time-model)
7. [Content-Type Model](#content-type-model)
8. [HTTP Representation Model](#http-representation-model)
9. [Persistence](#persistence)
10. [External Types in the Domain](#external-types-in-the-domain)

## Summary

This application has **no persisted data model**. There is no database, no ORM, no `@Entity`, no schema file, and no serialization format — responses are raw byte streams plus HTTP headers.

What exists instead is a small set of **request-scoped value types and ports** that describe a file for the duration of one HTTP exchange:

| Type | Kind | Lifetime | Mutability |
|---|---|---|---|
| `FilePointer` | Interface (port) | — | — |
| `FileSystemPointer` | Value object / adapter | Per request (per `findFile` call) | Immutable |
| `ExistingFile` | Value object | Per request | Immutable |
| `FileStorage` | Interface (port) | — | — |
| `java.util.UUID` | Identifier | Per request | Immutable |

Every project-declared field in the main source tree is `final`. There is no setter anywhere in the codebase.

## Domain Types

### `FilePointer` — the central abstraction
The closest thing to a domain model in this system. It is a **read-only projection of a file** consisting of content plus caching metadata:

| Conceptual attribute | Accessor | Type | Maps to HTTP |
|---|---|---|---|
| Content | `open()` | `InputStream` | response body |
| Size | `getSize()` | `long` | `Content-Length` |
| Name | `getOriginalName()` | `String` | `Location` path segment on `301` |
| Version identity | `getEtag()` | `String` (quoted) | `ETag` |
| Content type | `getMediaType()` | `Optional<MediaType>` | `Content-Type` |
| Modification time | `getLastModified()` | `Instant` | `Last-Modified` |
| ETag validator | `matchesEtag(String)` | `boolean` | `If-None-Match` evaluation |
| Time validator | `modifiedAfter(Instant)` | `boolean` | `If-Modified-Since` evaluation |

Note that the model carries **no owner, no permissions, no expiry, no upload timestamp, and no UUID of its own** — the UUID lives only in `FileStorage`'s lookup signature and in `ExistingFile`. A real implementation would need a richer metadata type.

### `FileSystemPointer` — the filesystem projection
| Field | Type | Derivation |
|---|---|---|
| `target` | `java.io.File` | Constructor argument |
| `tag` | `com.google.common.hash.HashCode` | `Files.hash(target, Hashing.sha512())` — computed eagerly over the full file |
| `mediaTypeOrNull` | `com.google.common.net.MediaType` | `MediaType.parse(java.nio.file.Files.probeContentType(target.toPath()))`, or `null` |

All derived attributes (`size`, `originalName`, `lastModified`) are read **live** from `target` on each call rather than captured at construction, so a file changed on disk after construction yields a stale `tag` alongside a fresh `size` and `lastModified`. This is a consistency subtlety worth knowing: the ETag and the `Content-Length` can disagree about which version of the file they describe.

### `ExistingFile` — request-resource binding
| Field | Type | Role |
|---|---|---|
| `method` | `org.springframework.http.HttpMethod` | Determines whether a body is attached (`GET` yes, `HEAD` no) |
| `filePointer` | `FilePointer` | The resource being served |
| `uuid` | `java.util.UUID` | Needed to construct the `301` `Location` URL |

A pure value object with no identity and no lifecycle; constructed in `DownloadController.findExistingFile` and discarded when the response is built. Its existence as a type is what allows `DownloadController` to stay at three short methods.

## Type Relationship Diagram

```
                    ┌──────────────────────┐
                    │ java.util.UUID       │  public identifier
                    └──────────┬───────────┘
                               │ findFile(uuid)
                    ┌──────────▼───────────┐
                    │ FileStorage          │  «interface» port
                    └──────────┬───────────┘
                               │ returns Optional<FilePointer>
                    ┌──────────▼───────────┐
      ┌─────────────│ FilePointer          │  «interface» port
      │             └──────────▲───────────┘
      │                        │ implements
      │             ┌──────────┴───────────┐
      │             │ FileSystemPointer    │
      │             ├──────────────────────┤
      │             │ - File target        │──► java.io.File
      │             │ - HashCode tag       │──► Guava SHA-512
      │             │ - MediaType …OrNull  │──► Guava MediaType
      │             └──────────────────────┘
      │
      │ composed into (1:1, per request)
      │
┌─────▼──────────────────────────┐
│ ExistingFile                   │
├────────────────────────────────┤
│ - HttpMethod method            │
│ - FilePointer filePointer      │
│ - UUID uuid                    │
└─────┬──────────────────────────┘
      │ produces
┌─────▼──────────────────────────┐
│ ResponseEntity<Resource>       │  Spring HTTP representation
│   status + headers + body      │
└─────┬──────────────────────────┘
      │ body (GET only)
┌─────▼──────────────────────────┐
│ InputStreamResource            │
│   └─ ThrottlingInputStream     │
│        ├─ InputStream target   │  from FilePointer.open()
│        └─ RateLimiter          │  Guava, 1 MB/s, fresh per response
└────────────────────────────────┘
```

### Cardinalities
| Relationship | Cardinality |
|---|---|
| `FileStorage` → `FilePointer` | 0..1 per UUID (`Optional`) |
| `ExistingFile` → `FilePointer` | 1 |
| `ExistingFile` → `UUID` | 1 |
| `FileSystemPointer` → `File` | 1 |
| `FilePointer` → `InputStream` | 0..n (a new stream per `open()` call) |
| `ExistingFile` → `ResponseEntity` | 1 per handler invocation |
| `ThrottlingInputStream` → `RateLimiter` | 1 (not shared between responses) |

## Field Reference

Every field declared by a project type, across main and test sources.

| Type | Field | Declared type | Modifiers | Source of value |
|---|---|---|---|---|
| `DownloadController` | `storage` | `FileStorage` | `private final` | Constructor injection |
| `ExistingFile` | `log` | `org.slf4j.Logger` | `private static final` | `LoggerFactory.getLogger(ExistingFile.class)` |
| `ExistingFile` | `method` | `HttpMethod` | `private final` | Constructor |
| `ExistingFile` | `filePointer` | `FilePointer` | `private final` | Constructor |
| `ExistingFile` | `uuid` | `UUID` | `private final` | Constructor |
| `ThrottlingInputStream` | `target` | `InputStream` | `private final` | Constructor |
| `ThrottlingInputStream` | `maxBytesPerSecond` | `RateLimiter` | `private final` | Constructor |
| `FileSystemPointer` | `target` | `File` | `private final` | Constructor |
| `FileSystemPointer` | `tag` | `HashCode` | `private final` | `Files.hash(target, Hashing.sha512())` |
| `FileSystemPointer` | `mediaTypeOrNull` | `MediaType` | `private final` | `probeContentType` + `MediaType.parse`, or `null` |
| `FileSystemStorage` | `log` | `Logger` | `private static final` | `LoggerFactory.getLogger(...)` |
| `FileStorageStub` *(test)* | `log` | `Logger` | `private static final` | `LoggerFactory.getLogger(...)` |
| `FileExamples` *(test)* | `TXT_FILE_UUID` | `UUID` | `public static final` | `UUID.randomUUID()` |
| `FileExamples` *(test)* | `TXT_FILE` | `FilePointer` | `public static final` | `txtFile()` |
| `FileExamples` *(test)* | `NOT_FOUND_UUID` | `UUID` | `public static final` | `UUID.randomUUID()` |
| `DownloadControllerSpec` *(test)* | `mockMvc` | `MockMvc` | `private` | Built from the injected `WebApplicationContext` |
| `DownloadControllerSpec` *(test)* | `TEXT_FILE` | `String` | `private static final` | `'/download/' + FileExamples.TXT_FILE_UUID + '/file.txt'` |

**Observation.** `mockMvc` is the single non-`final` field in the entire codebase, and it is test infrastructure. The production code is 100% immutable.

## Identifier Model

| Aspect | Detail |
|---|---|
| Type | `java.util.UUID` |
| Role | The sole public addressing scheme for files |
| Appears in | `FileStorage.findFile(UUID)`, both controller handlers as `@PathVariable UUID`, `ExistingFile.uuid` |
| Validation | Delegated entirely to Spring's `String`→`UUID` converter; a malformed segment produces `400 Bad Request` before any application code executes |
| Generation | **Nowhere in main source.** Only `FileExamples` generates UUIDs, via `UUID.randomUUID()` for test fixtures |
| Persistence | None — no UUID→file mapping is stored anywhere |
| Security property | Unguessable (122 bits of randomness when generated randomly), but an identifier is not an authorization control. See [../analysis/security-patterns.md](../analysis/security-patterns.md) |

**Gap.** The model assumes UUIDs are minted and mapped to files by some process that does not exist in this codebase. `FileSystemStorage` sidesteps this by ignoring the UUID entirely. Any real implementation must introduce a metadata store — the natural AWS shape being DynamoDB for the UUID→S3-key mapping.

A second consequence: because `FileExamples` uses `UUID.randomUUID()` in static initializers, the test fixture UUIDs differ on every JVM run. The tests are correct (they reference the same constants) but the UUIDs are not reproducible across runs.

## Time Model

| Layer | Type | Where |
|---|---|---|
| Storage | `long` epoch millis | `File.lastModified()` |
| Domain / port | `java.time.Instant` | `FilePointer.getLastModified()`, `modifiedAfter(Instant)` |
| HTTP binding | `java.util.Date` | `@RequestHeader(IF_MODIFIED_SINCE) Optional<Date>` |
| HTTP wire | RFC 1123 string | `Last-Modified` / `If-Modified-Since` headers |

**Conversion chain for a conditional request:**
```
"Wed, 21 Oct 2015 07:28:00 GMT"      ← wire
  → java.util.Date                    ← Spring MVC converter
  → Date::toInstant                   ← ExistingFile.cached
  → FilePointer.modifiedAfter(Instant)
  → !clientTime.isBefore(getLastModified())
```

**Conversion chain for a response:**
```
File.lastModified()  (long millis)
  → Instant.ofEpochMilli(...)         ← FileSystemPointer
  → getLastModified().toEpochMilli()  ← ExistingFile.response
  → ResponseEntity.lastModified(long)
  → "Wed, 21 Oct 2015 07:28:00 GMT"   ← wire
```

**Precision note.** HTTP date headers have **one-second** granularity while `File.lastModified()` has millisecond granularity. A file modified within the same second as the client's cached copy can therefore be judged unmodified. This is standard HTTP behavior and is exactly why the ETag validator exists as the stronger check — but it means the `If-Modified-Since` path alone is not sufficient for rapidly-changing files.

**Modernization note.** `java.util.Date` appears only at the HTTP boundary; the domain is already on `java.time`. Migrating the `@RequestHeader` binding to `Instant`/`ZonedDateTime` would remove the legacy type entirely and shorten the conversion chain.

## Content-Type Model

Three different `MediaType`-like representations coexist:

| Representation | Type | Where |
|---|---|---|
| Probe result | `String` (e.g. `"text/plain"`) | `java.nio.file.Files.probeContentType` |
| Domain | `com.google.common.net.MediaType` | `FilePointer.getMediaType()`, `FileSystemPointer.mediaTypeOrNull` |
| HTTP | `org.springframework.http.MediaType` | `ResponseEntity` `Content-Type` |

Translation happens in `ExistingFile.toMediaType`:
```java
return input.charset()
        .transform(c -> new MediaType(input.type(), input.subtype(), c))
        .or(new MediaType(input.type(), input.subtype()));
```
Charset is preserved when Guava detected one, otherwise a type/subtype-only Spring `MediaType` is produced. Note this uses Guava's `Optional.transform`/`or` API rather than `java.util.Optional` — a Guava-18-era idiom that has since been deprecated in favour of `java.util.Optional` interop, and therefore an upgrade touch point.

**Absence handling.** `probeContentType` returns `null` for unrecognized files, which becomes `Optional.empty()`, which causes `ExistingFile.response` to omit `Content-Type` entirely rather than defaulting to `application/octet-stream`. Strictly honest, though many clients would prefer the explicit octet-stream default.

**Portability caveat.** `probeContentType` is implementation-dependent — it consults OS-level MIME databases on Linux but relies on file extensions on some platforms. The same file can therefore yield a different `Content-Type` on different hosts. The Spock specification asserts `Content-Type: text/plain` for `download.txt`, making that assertion mildly platform-sensitive.

## HTTP Representation Model

The "output model" of the system — there are no DTOs, so this table is the complete response contract.

| Status | Body | `ETag` | `Content-Length` | `Last-Modified` | `Content-Type` | `Location` |
|---|---|---|---|---|---|---|
| `200 OK` (GET) | `InputStreamResource` over `ThrottlingInputStream` | ✅ | ✅ | ✅ | ✅ if known | — |
| `200 OK` (HEAD) | `null` | ✅ | ✅ | ✅ | ✅ if known | — |
| `301 Moved Permanently` | `null` | — | — | — | — | ✅ `/download/{uuid}/{originalName}` |
| `304 Not Modified` | `null` | ✅ | ✅ | ✅ | ✅ if known | — |
| `404 Not Found` | empty | — | — | — | — | — |
| `400 Bad Request` | Boot default error body | — | — | — | — | — |

The `200`/`304` rows are identical except for the body because all three of `serveDownload`, `notModified` and HEAD handling funnel through `ExistingFile.response(...)`. The `301` row is built separately in `redirectDownload` and therefore carries **only** `Location` — no caching headers.

## Persistence

**None.** Exhaustively verified absent:

| Searched for | Result |
|---|---|
| `@Entity`, `@Table`, `@Id`, `@Column` | None |
| JPA / Hibernate configuration, `persistence.xml` | None |
| `*.hbn.xml`, `*.hbm.xml`, MyBatis `*Mapper.xml` | None |
| `JdbcTemplate`, `DataSource`, `Connection` | None |
| `.sql` files, Flyway/Liquibase migrations | None |
| Spring Data repositories | None |
| NoSQL clients (MongoDB, DynamoDB, Cassandra, Redis) | None |
| `spring-boot-starter-data-*` dependencies | None |

The only data store is the **local filesystem**, accessed through `java.io.File` in `FileSystemPointer` and the classpath in `FileSystemStorage`. `hibernate-validator` is present transitively via `spring-boot-starter-web` but is entirely unused.

## External Types in the Domain

Third-party types that appear in project type signatures — each one a coupling point for dependency upgrades.

| External type | Library | Appears in | Upgrade sensitivity |
|---|---|---|---|
| `com.google.common.net.MediaType` | Guava 18.0 | `FilePointer.getMediaType()` **(port signature)** | 🟠 Medium — a library type in a port interface; every adapter is bound to Guava |
| `com.google.common.hash.HashCode` | Guava 18.0 | `FileSystemPointer.tag` | 🟡 Low — adapter-internal |
| `com.google.common.util.concurrent.RateLimiter` | Guava 18.0 | `ThrottlingInputStream` constructor **(public signature)** | 🟠 Medium — `RateLimiter` has long carried `@Beta`-adjacent status |
| `org.springframework.http.HttpMethod` | Spring Web 4.1.6 | `ExistingFile` constructor **(public signature)** | 🟠 Medium — became a class rather than an enum in Spring 6 |
| `org.springframework.core.io.Resource` / `InputStreamResource` | Spring Core 4.1.6 | Controller and `ExistingFile` return types | 🟡 Low — stable API |
| `org.springframework.http.ResponseEntity` | Spring Web 4.1.6 | All handler return types | 🟠 Medium — builder API evolved across Spring 5 and 6 |
| `java.util.Date` | JDK | Controller handler parameters | 🟡 Low — replaceable with `java.time` |

**Assessment.** Two leaks are worth noting architecturally: Guava's `MediaType` in the `FilePointer` port, and Guava's `RateLimiter` in `ThrottlingInputStream`'s public constructor. Both place a third-party type on an otherwise clean abstraction boundary, meaning a Guava major upgrade propagates into the port contract rather than staying inside an adapter. Neither is severe at this size, but both are worth addressing if the storage port is extended for a real implementation.
