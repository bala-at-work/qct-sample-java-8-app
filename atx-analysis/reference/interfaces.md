# Interfaces and Contracts

[← Back to index](../README.md) · Related: [program-structure.md](program-structure.md) · [data-models.md](data-models.md)

## `FileStorage`

`src/main/java/com/nurkiewicz/download/FileStorage.java`

```java
public interface FileStorage {
    Optional<FilePointer> findFile(UUID uuid);
}
```

**Contract**
- Input: a non-null `UUID` identifying a logical file.
- Output: `Optional.of(pointer)` when the file exists and is readable; `Optional.empty()` when it does not. Returning `null` violates the contract — `DownloadController.findExistingFile` calls `.map(...)` on the result directly.
- Side effects: implementations log at DEBUG. No mutation.
- Thread safety: implementations are singletons in the Spring context and must be thread-safe; both current implementations are stateless.

**Implementations**

| Implementation | Profile | Behaviour |
|---|---|---|
| `FileSystemStorage` | `!test` | Ignores the UUID; always returns a `FileSystemPointer` over the classpath resource `/logback.xml`. Declared in Javadoc as a stub to be replaced. |
| `FileStorageStub` | `test` | Returns `FileExamples.TXT_FILE` for `FileExamples.TXT_FILE_UUID`, otherwise `Optional.empty()`. |

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

**Method contracts**

| Method | Contract | Notes |
|---|---|---|
| `open()` | Returns a fresh, positioned-at-zero stream of the file's bytes. The caller owns closing it. Does not declare a checked exception — implementations must wrap I/O failures. | `FileSystemPointer` returns `BufferedInputStream(FileInputStream)` and wraps `FileNotFoundException` in `IllegalArgumentException` |
| `getSize()` | Byte length; becomes the `Content-Length` header. | `File.length()` |
| `getOriginalName()` | Filename used to build the canonical redirect URL. | `File.getName()` |
| `getEtag()` | A **quoted** strong ETag string (including the surrounding `"` characters, as required by RFC 7232). | `"\"" + sha512 + "\""` |
| `getMediaType()` | `Optional.empty()` means "unknown" — the response then carries no `Content-Type`. | From `java.nio.file.Files.probeContentType` |
| `matchesEtag(String)` | True when the supplied `If-None-Match` value identifies this exact content. Implementations compare the full quoted form. Multi-value `If-None-Match` lists and `W/` weak prefixes are **not** handled. | `getEtag().equals(requestEtag)` |
| `getLastModified()` | Modification instant; becomes the `Last-Modified` header (epoch millis). | `Instant.ofEpochMilli(File.lastModified())` |
| `modifiedAfter(Instant clientTime)` | **Name is inverted relative to behaviour**: returns `true` when the client's copy is *current* (i.e. `!clientTime.isBefore(getLastModified())`), which `ExistingFile.cached` uses to decide `304`. Read it as "client copy is not stale". | `FileSystemPointer:83-85` |

**Implementations**: `FileSystemPointer` (production and test fixture use).

## Inherited / Extended Framework Contracts

### `ThrottlingInputStream extends java.io.InputStream`
- Honours the full `InputStream` contract by delegation. The three `read` overloads first block on `RateLimiter.acquire(n)`; `skip` is intentionally unthrottled (bytes are not transferred to the caller).
- `mark`/`reset` are `synchronized` and delegate; `markSupported` reflects the wrapped stream.
- `close` closes the wrapped stream but **not** the `RateLimiter` (none required).

### `Sha512ShallowEtagHeaderFilter extends org.springframework.web.filter.ShallowEtagHeaderFilter`
- Overrides `protected String generateETagHeaderValue(byte[] bytes)` to return a quoted Guava SHA-512 hash instead of Spring's MD5-based value.
- Lives in the Spring package (test tree) solely because the hook is `protected`. Not registered as a bean or filter anywhere in the repository.

### `DownloadControllerSpec extends spock.lang.Specification`
- Spock contract: `given:`/`expect:` blocks, `def '...'()` feature methods. Context supplied by `@ContextConfiguration(classes = [MainApplication])` with `@WebAppConfiguration` and `@ActiveProfiles("test")`.

## Interface-Level Observations

- Both ports are minimal and free of framework types except Guava's `MediaType` in `FilePointer.getMediaType()`, which leaks a library type into the domain contract and forces `ExistingFile.toMediaType` conversion. Guava's `MediaType.charset().transform(...).or(...)` API used there was removed in later Guava releases — a known upgrade friction point.
- Neither port declares checked exceptions, so all I/O failure modes surface as unchecked `IllegalArgumentException`. See [../architecture/patterns.md](../architecture/patterns.md) item A4.
