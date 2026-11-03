# Interfaces

[← Back to index](../README.md) · [Program structure](program-structure.md)

## Inheritance and Implementation Hierarchy

```
«interface» FileStorage
     ├── FileSystemStorage          @Component @Profile("!test")   (main)
     └── FileStorageStub            @Component @Profile("test")    (test)

«interface» FilePointer
     └── FileSystemPointer                                         (main)

java.io.InputStream                 (JDK)
     └── ThrottlingInputStream                                     (main)

org.springframework.web.filter.ShallowEtagHeaderFilter             (Spring)
     └── Sha512ShallowEtagHeaderFilter                             (test)

spock.lang.Specification                                           (Spock)
     └── DownloadControllerSpec                                    (test)
```

Depth is at most 2 everywhere. No abstract classes are declared in this codebase, and no interface extends another.

## Declared Interfaces

### `FileStorage`
`src/main/java/com/nurkiewicz/download/FileStorage.java`

```java
public interface FileStorage {
    Optional<FilePointer> findFile(UUID uuid);
}
```

**Contract**
| Aspect | Specification |
|---|---|
| Purpose | Resolve an opaque file identifier to a retrievable file handle. |
| Parameter | `uuid` — the file identifier from the request path. Non-null (Spring's type conversion rejects malformed UUIDs with `400` before the method is reached). |
| Returns | `Optional.of(pointer)` when the identifier is known; `Optional.empty()` when it is not. |
| Absence semantics | "Not found" **must** be expressed as `Optional.empty()`, never as `null` and never as an exception. `DownloadController` relies on this to produce `404`. |
| Exceptions | None declared. Implementations are expected to wrap IO failures in unchecked exceptions (the filesystem adapter uses `IllegalArgumentException`). |
| Idempotency | Must be side-effect free and repeatable; the controller may call it once per request and the result is not cached. |
| Thread-safety | Implementations must be thread-safe — a single bean instance serves all concurrent requests. |
| Framework coupling | None. Only `java.util` types appear in the signature. |

**Implementation conformance**
| Implementation | Conforms? | Notes |
|---|---|---|
| `FileStorageStub` (test) | Yes | Returns the fixture for `TXT_FILE_UUID`, `Optional.empty()` otherwise. Exercises both branches. Missing the `@Override` annotation, though the method does correctly implement the interface. |
| `FileSystemStorage` (main) | **Partially** | Never returns `Optional.empty()` — it ignores `uuid` and always returns a pointer to `/logback.xml`. Technically satisfies the signature but violates the intended semantics, making the `404` path unreachable in production. High-severity finding TD-05. |

### `FilePointer`
`src/main/java/com/nurkiewicz/download/FilePointer.java`

```java
public interface FilePointer {
    InputStream open();
    long getSize();
    String getOriginalName();
    String getEtag();
    Optional<MediaType> getMediaType();   // com.google.common.net.MediaType
    boolean matchesEtag(String requestEtag);
    Instant getLastModified();
    boolean modifiedAfter(Instant isModifiedSince);
}
```

**Contract**
| Method | Specification |
|---|---|
| `open()` | Returns a **fresh** stream positioned at byte 0 on each call. Ownership transfers to the caller, which must close it. May be called zero times (the `HEAD` and `304` paths never open the file). Implementations wrap IO failure in an unchecked exception. |
| `getSize()` | Byte length of the content, used verbatim as the `Content-Length` header. Must be consistent with what `open()` yields. |
| `getOriginalName()` | The filename a client should save as. Used to build the `301` `Location` header — therefore must be URI-safe or safely encodable. |
| `getEtag()` | A **strong** HTTP entity tag, including the surrounding double quotes. `FileSystemPointer` returns `"<sha512-hex>"`. Must change whenever content changes. |
| `getMediaType()` | `Optional.empty()` when the type cannot be determined, in which case no `Content-Type` header is emitted. Uses Guava's `MediaType` to keep the port free of Spring types. |
| `matchesEtag(String)` | True when the supplied `If-None-Match` value identifies this exact content. Comparison semantics are owned by the implementation (tell-don't-ask), not by `ExistingFile`. |
| `getLastModified()` | Content modification instant; drives the `Last-Modified` header (converted via `toEpochMilli`). |
| `modifiedAfter(Instant)` | True when the client's copy is **not older** than the server's, i.e. the client may reuse its cached copy. `FileSystemPointer` implements this as `!clientTime.isBefore(getLastModified())`. Note the parameter is named `isModifiedSince` in the interface and `clientTime` in the implementation; the method name reads as the inverse of what it returns, which is a minor clarity wart. |

**Consumers**
| Consumer | Methods used |
|---|---|
| `ExistingFile.cached` | `matchesEtag`, `modifiedAfter` |
| `ExistingFile.response` | `getEtag`, `getSize`, `getLastModified`, `getMediaType` |
| `ExistingFile.buildResource` | `open` |
| `ExistingFile.redirectDownload` | `getOriginalName` |
| `DownloadController` | none directly — passes the pointer through to `ExistingFile` |
| `FileStorageStub`, `FileExamples` (test) | `getEtag`, `getLastModified`, `getSize` |

All eight methods are consumed; there is no dead surface on this interface.

## Functional Interfaces in Use

| Interface | Site | Instantiated as |
|---|---|---|
| `java.util.function.Function<FilePointer, ResponseEntity<Resource>>` | `ExistingFile.serveWithCaching` parameter | `this::redirectDownload`, `this::serveDownload` |
| `java.util.function.Function` (implicit, via `Optional.map`) | `ExistingFile.cached` | `filePointer::matchesEtag`, `Date::toInstant`, `filePointer::modifiedAfter` |
| `java.util.function.Function` (implicit) | `DownloadController.redirect`/`download` | `file -> file.redirect(...)`, `pointer -> new ExistingFile(...)` |
| `java.util.function.Supplier` (implicit, via `orElseGet`) | `DownloadController` | `() -> new ResponseEntity<>(NOT_FOUND)` |
| `java.util.function.Consumer` (implicit, via `ifPresent`) | `ExistingFile.response` | `responseBuilder::contentType` |
| Guava `com.google.common.base.Function` | `ExistingFile.toMediaType` | `c -> new MediaType(input.type(), input.subtype(), c)` passed to Guava `Optional.transform` |

## Framework Contracts Implemented

| Contract | Implementer | Mechanism |
|---|---|---|
| Spring MVC handler | `DownloadController` | `@RestController` + `@RequestMapping` annotations (no interface) |
| Spring managed bean | `FileSystemStorage`, `FileStorageStub` | `@Component` + `@Profile` |
| Spring Boot application | `MainApplication` | `@SpringBootApplication` |
| `java.io.InputStream` abstract contract | `ThrottlingInputStream` | Overrides the single abstract `read()` plus eight optional methods |
| `ShallowEtagHeaderFilter` template hook | `Sha512ShallowEtagHeaderFilter` | Overrides `protected generateETagHeaderValue(byte[])` |
| `AutoCloseable` (via `InputStream`) | `ThrottlingInputStream` | `close()` delegates to the wrapped stream |

## Extension Points

The interfaces above are the intended seams for change:

1. **New storage backend** — implement `FileStorage` + `FilePointer`. No changes needed in `DownloadController` or `ExistingFile`. This is the path for replacing the stub (R-05) with S3, a database, or a real filesystem directory.
2. **New throttling policy** — the `RateLimiter` is a constructor parameter of `ThrottlingInputStream`, so the rate is injectable even though the current call site hardcodes it (R-09).
3. **New caching behavior** — `ExistingFile.serveWithCaching` accepts the not-cached behavior as a `Function`, so additional response strategies can be added without touching the cache-evaluation logic.

See [data-models.md](data-models.md) and [api-reference.md](api-reference.md).
