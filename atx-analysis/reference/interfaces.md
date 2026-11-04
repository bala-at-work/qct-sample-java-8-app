# Interfaces

- [Back to README](../README.md)
- Siblings: [program-structure.md](program-structure.md) · [data-models.md](data-models.md) · [api-reference.md](api-reference.md) · [modules.md](modules.md)

## Table of Contents
1. [Overview](#overview)
2. [`FileStorage`](#filestorage)
3. [`FilePointer`](#filepointer)
4. [Inheritance Hierarchies](#inheritance-hierarchies)
5. [Implicit Contracts](#implicit-contracts)
6. [Framework Extension Points](#framework-extension-points)
7. [Contract Risks](#contract-risks)

## Overview

The project declares exactly **two** interfaces, both outbound ports, both in `com.nurkiewicz.download`. There are **no abstract classes**, no generic project-declared type parameters, and no default or static interface methods.

| Interface | Methods | Implementations | Referencing types |
|---|---|---|---|
| `FileStorage` | 1 | `FileSystemStorage` (main), `FileStorageStub` (test) | `DownloadController` |
| `FilePointer` | 8 | `FileSystemPointer` (main) | `ExistingFile`, `FileStorage`, `FileStorageStub`, `FileExamples` |

Both are **pure query interfaces** — no method mutates state, and no method declares a checked exception.

## `FileStorage`

`src/main/java/com/nurkiewicz/download/FileStorage.java`

```java
public interface FileStorage {
    Optional<FilePointer> findFile(UUID uuid);
}
```

**Role.** The system's only lookup port: translate a public UUID identifier into a readable file handle.

### Contract

| Aspect | Specification |
|---|---|
| Input | A non-null `UUID`. Spring's converter guarantees well-formedness before the controller is reached. |
| Output | `Optional.of(pointer)` when a file is associated with the UUID; `Optional.empty()` when none is. |
| Null | Must never return `null`; absence is expressed as `Optional.empty()`. |
| Exceptions | None declared. Implementations are expected not to throw for a simple miss — a miss is `empty()`, not an exception. |
| Idempotence | Required. Repeated calls with the same UUID must yield equivalent results for the ETag/Last-Modified caching contract to hold. |
| Side effects | None expected beyond logging. |
| Thread safety | Required — the implementation is a Spring singleton invoked concurrently from Tomcat worker threads. Both current implementations are stateless and therefore safe. |

### Implementations

| Implementation | Profile | Behavior | Contract compliance |
|---|---|---|---|
| `FileSystemStorage` | `!test` | Ignores `uuid`; always returns a pointer to the classpath `/logback.xml` | Satisfies the **signature** but not the **intent** — it never returns `empty()`, so a 404 is unreachable in production. Explicitly labelled a stub in its Javadoc. |
| `FileStorageStub` | `test` | `Optional.of(TXT_FILE)` for `TXT_FILE_UUID`, else `Optional.empty()` | Fully compliant; this is the implementation that exercises both the found and not-found paths |

### Extension note
This one-method interface is the natural seam for real storage. An `S3FileStorage` implementation would need to resolve a UUID to an object key (requiring a metadata store such as DynamoDB) and return an `S3FilePointer`. No other class in the system would change.

## `FilePointer`

`src/main/java/com/nurkiewicz/download/FilePointer.java`

```java
public interface FilePointer {
    InputStream open();
    long getSize();
    String getOriginalName();
    String getEtag();
    Optional<MediaType> getMediaType();          // com.google.common.net.MediaType
    boolean matchesEtag(String requestEtag);
    Instant getLastModified();
    boolean modifiedAfter(Instant isModifiedSince);
}
```

**Role.** Expose a file's content and the metadata required to construct HTTP caching headers, without revealing the storage mechanism.

### Method contracts

| Method | Returns | Contract | Consumer |
|---|---|---|---|
| `open()` | `InputStream` | A fresh, independently closeable stream positioned at byte 0. The **caller owns closing it**. May be called repeatedly. No checked exception is declared, so I/O failures must surface as runtime exceptions. | `ExistingFile.buildResource` — wrapped in `ThrottlingInputStream` and an `InputStreamResource` |
| `getSize()` | `long` | Exact byte length; must agree with the number of bytes `open()` yields, since it becomes the `Content-Length` header. | `ExistingFile.response` |
| `getOriginalName()` | `String` | The filename to present to the client; used verbatim in the `301` `Location` URL. Must be URI-safe — **not currently enforced**. | `ExistingFile.redirectDownload` |
| `getEtag()` | `String` | A complete `ETag` header value **including** surrounding double quotes. `FileSystemPointer` returns `"\"" + sha512 + "\""`. | `ExistingFile.response` |
| `getMediaType()` | `Optional<MediaType>` | Guava media type if determinable, else `Optional.empty()` — in which case no `Content-Type` header is set at all. | `ExistingFile.response` via `toMediaType` |
| `matchesEtag(String)` | `boolean` | `true` if the supplied `If-None-Match` value identifies the current content. `FileSystemPointer` uses exact string equality against `getEtag()`. | `ExistingFile.cached` |
| `getLastModified()` | `Instant` | Last-modification timestamp, converted to epoch milliseconds for the `Last-Modified` header. | `ExistingFile.response` |
| `modifiedAfter(Instant)` | `boolean` | **Despite the name, returns `true` when the client's cached copy may still be used** — `FileSystemPointer` implements `!clientTime.isBefore(getLastModified())`, i.e. the client copy is at least as new as the server copy. | `ExistingFile.cached` |

### Design strengths
- **Freshness logic lives on the port.** `matchesEtag` and `modifiedAfter` are interface methods rather than comparisons performed in `ExistingFile`, so each adapter defines its own validator semantics. An S3 adapter could delegate `matchesEtag` to S3's own ETag without touching the HTTP layer.
- **Explicit optionality.** `getMediaType()` returning `Optional` makes "content type unknown" a first-class state, and `ExistingFile` simply omits the header rather than guessing `application/octet-stream`.
- **Time via `java.time`.** The port is expressed in `Instant`, keeping legacy `java.util.Date` confined to the HTTP binding layer.

### Design concerns
| Concern | Detail |
|---|---|
| Misleading method name | `modifiedAfter` reads as a question about the server file but answers a question about the client's cache. A name such as `clientCopyStillValid` or `notModifiedSince` would prevent misimplementation by a new adapter. |
| Quoting responsibility on the implementation | `getEtag()` must return a *pre-quoted* value. This is an undocumented obligation; an adapter that returns a bare hash would emit a malformed `ETag`. |
| No checked exceptions | `open()` cannot signal I/O failure through the type system, forcing implementations into unchecked rewrapping (`FileSystemPointer` throws `IllegalArgumentException`). |
| Guava type in the port signature | `getMediaType()` exposes `com.google.common.net.MediaType`, binding every implementation to Guava and requiring `ExistingFile.toMediaType` to translate. A JDK or Spring type, or a plain `String`, would keep the port library-neutral. |
| Eager-cost assumption | `ExistingFile` constructs a `FilePointer` before deciding whether a `304` suffices. Because `FileSystemPointer` hashes the whole file in its constructor, the port's cheap-to-create assumption is violated by its only implementation. |

## Inheritance Hierarchies

### Project interfaces
```
FileStorage
├── FileSystemStorage    implements    @Component @Profile("!test")
└── FileStorageStub      implements    @Component @Profile("test")

FilePointer
└── FileSystemPointer    implements
```

### Extension of JDK and framework types
```
java.io.InputStream
└── ThrottlingInputStream
        overrides: read(), read(byte[]), read(byte[],int,int),
                   skip, available, mark, reset, markSupported, close

org.springframework.web.filter.ShallowEtagHeaderFilter
└── Sha512ShallowEtagHeaderFilter        [test tree, never registered]
        overrides: protected String generateETagHeaderValue(byte[])

spock.lang.Specification
└── DownloadControllerSpec               [test]
```

Maximum inheritance depth beyond `Object` is **2** (`ThrottlingInputStream` → `InputStream` → `Object`). No diamond situations, no interface default methods, no abstract intermediate classes.

### Types with no supertype
`MainApplication`, `DownloadController`, `ExistingFile`, `FileExamples`.

## Implicit Contracts

Contracts enforced by convention or by the framework rather than by the type system. These are the constraints most likely to be broken by a careless refactor.

| Contract | Enforced by | Breakage consequence |
|---|---|---|
| Exactly one `FileStorage` bean must be active | `@Profile("!test")` on `FileSystemStorage` paired with `@Profile("test")` on `FileStorageStub` | Removing either profile yields two candidates and context startup fails with an ambiguous-bean error |
| `getEtag()` includes surrounding quotes | Convention in `FileSystemPointer` | Malformed `ETag` header; clients would fail validation |
| `getSize()` equals the byte count from `open()` | Convention | `Content-Length` mismatch — truncated or hung responses |
| `getOriginalName()` is URI-safe | **Unenforced** | `ExistingFile.redirectDownload` throws `URISyntaxException`, rewrapped as `IllegalArgumentException`, yielding a 500 |
| The caller closes the stream from `open()` | Spring's `InputStreamResource` handling | File-descriptor leak |
| `HttpMethod` is resolvable as a handler argument | Spring MVC argument resolution | `resourceToReturn` would mis-decide body inclusion, breaking HEAD |
| `@RequestHeader(...) Optional<T>` binds an absent header to `Optional.empty()` | Spring MVC | Null-pointer failures in `cached()` |
| `Optional<Date>` conversion from an RFC 1123 header | Spring MVC date conversion | `If-Modified-Since` silently stops working — a correctness regression with no error |
| `{filename}` need not match `getOriginalName()` | Nothing | Currently *relied upon*: the spec requests `/download/{uuid}/file.txt` while the fixture is named `download.txt` |

The last two rows are the highest-risk items for a Spring version upgrade, because both would fail **silently** rather than loudly. They are exactly what the Spock specification's `If-Modified-Since` feature methods guard — which is why activating that suite before upgrading matters.

## Framework Extension Points

| Extension point | Used by | Status |
|---|---|---|
| `@SpringBootApplication` auto-configuration | `MainApplication` | Active; supplies the whole runtime |
| Spring MVC handler-method argument resolution (`HttpMethod`, `@PathVariable`, `@RequestHeader`) | `DownloadController` | Active |
| Spring `Resource` abstraction (`InputStreamResource`) | `ExistingFile` | Active; enables streaming responses |
| `ResponseEntity.BodyBuilder` fluent API | `ExistingFile.response` | Active |
| Spring `@Profile` conditional bean registration | `FileSystemStorage`, `FileStorageStub` | Active |
| `ShallowEtagHeaderFilter.generateETagHeaderValue(byte[])` protected hook | `Sha512ShallowEtagHeaderFilter` | **Unused** — the class is never registered, and the hook's signature changed in later Spring versions |
| `@ControllerAdvice` / `HandlerExceptionResolver` | — | Not used; errors fall through to Boot defaults |
| `WebMvcConfigurer` / `WebMvcConfigurerAdapter` | — | Not used; no MVC customization |
| `HandlerInterceptor` / servlet `Filter` registration | — | Not used |

## Contract Risks

Consolidated view of what a framework or JDK upgrade threatens, in priority order.

| # | Risk | Severity | Affected contract |
|---|---|---|---|
| 1 | `Optional<Date>` `@RequestHeader` binding and RFC 1123 date conversion semantics differ across Spring versions | 🔴 High | `If-Modified-Since` → `304`. Fails silently |
| 2 | `ResponseEntity`/`HttpHeaders` builder APIs evolved in Spring 5 and 6 | 🔴 High | Every header set in `ExistingFile.response` |
| 3 | `ShallowEtagHeaderFilter.generateETagHeaderValue` signature change (now `InputStream`-based) | 🟠 Medium | `Sha512ShallowEtagHeaderFilter` will not compile. Resolution: delete the dead class |
| 4 | Guava `MediaType`, `Hashing`, `RateLimiter` API drift across major versions | 🟠 Medium | `FilePointer.getMediaType` signature, ETag generation, throttling |
| 5 | `HttpMethod` became a class rather than an enum in Spring 6 | 🟠 Medium | `resourceToReturn`'s `method == HttpMethod.GET` comparison |
| 6 | Servlet API `javax` → `jakarta` namespace migration | 🟡 Low | Only transitively relevant — no application class imports `javax.*` |
| 7 | `@Profile("!test")` negation syntax | 🟡 Low | Stable across versions; low risk |
