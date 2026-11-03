# Complexity Analysis

[← Back to index](../README.md) · Related: [code-metrics.md](code-metrics.md) · [../architecture/patterns.md](../architecture/patterns.md)

Cyclomatic complexity below is counted by hand as 1 + (branches, loops, ternaries, boolean operators, lambdas treated as inline branches where they alter flow).

## Cyclomatic Complexity by Method

| Method | Complexity | Drivers |
|---|---|---|
| `ExistingFile.cached` | 4 | two `Optional.map(...).orElse(false)` chains + `\|\|` |
| `FileSystemPointer` constructor | 3 | ternary on `contentType` + `catch` |
| `ExistingFile.serveWithCaching` | 2 | one `if` |
| `ExistingFile.resourceToReturn` | 2 | `if/else` on `HttpMethod` |
| `ExistingFile.redirectDownload` | 2 | `try/catch` |
| `ExistingFile.response` | 2 | `ifPresent` on media type |
| `ExistingFile.toMediaType` | 2 | `transform(...).or(...)` |
| `DownloadController.redirect` / `.download` | 2 each | `map(...).orElseGet(...)` |
| `FileSystemPointer.open` | 2 | `try/catch` |
| `FileStorageStub.findFile` | 2 | `if` on UUID equality |
| All other methods | 1 | straight-line delegation |

**Aggregate**: total production complexity ≈ 38 across 35 production methods — average ≈ 1.1, maximum 4. This is a low-complexity codebase; no method approaches a refactoring threshold.

## Cognitive Complexity Hotspots

Ranked by the effort required to understand and change the code safely.

### 1. `ExistingFile` — High relative concentration
`ExistingFile.java` holds every HTTP caching decision in the system. Although each method is tiny, understanding the class requires simultaneously holding: RFC 7232 conditional-request semantics, the `GET` vs `HEAD` body suppression rule, the strategy indirection via `Function<FilePointer, ResponseEntity<Resource>>`, and the Guava→Spring `MediaType` conversion. Changes here affect correctness of `200`/`301`/`304` for both endpoints.

### 2. `ExistingFile.cached` + `FilePointer.modifiedAfter` — Medium, semantic trap
`cached` returns `true` when `modifiedAfter(clientTime)` is `true`, but `FileSystemPointer.modifiedAfter` is implemented as `!clientTime.isBefore(getLastModified())` — i.e. "the client's timestamp is at or after the file's last modification", meaning the client copy is *fresh*. The method name reads as the opposite of its behaviour. The test `'should not return file if server has older version than the client'` encodes this behaviour deliberately. This naming inversion is the most likely source of a future regression.

### 3. `FileSystemPointer` constructor — Medium, I/O in construction
Performs two filesystem operations (full-file SHA-512 hash, content-type probe) and converts checked exceptions. Complexity is low but the *cost* is high and hidden behind a constructor call.

### 4. `ThrottlingInputStream` — Low
Ten methods, all one-liners. Only subtlety: `skip` is intentionally not rate-limited.

### 5. `DownloadController` — Low
Two near-identical handlers differing only in the `ExistingFile` method invoked.

## Performance Hotspots

| # | Hotspot | Location | Impact | Severity |
|---|---|---|---|---|
| P1 | **Full-file SHA-512 hash on every request.** `FileSystemStorage.findFile` constructs a new `FileSystemPointer`, whose constructor hashes the entire file. There is no ETag cache, so an N-byte file is read and digested on every single request — including `HEAD` requests and requests that end in `304`. | `FileSystemPointer:22`, `FileSystemStorage:28` | O(file size) CPU + I/O per request; dominates cost for large files | Medium |
| P2 | **`probeContentType` per request** — an additional filesystem probe on each pointer construction. | `FileSystemPointer:23` | Small but unnecessary | Low |
| P3 | **Thread-per-request blocking during throttled transfer.** `RateLimiter.acquire` blocks the Tomcat worker; at 1 MB/s a 100 MB download holds a thread for a long time. Concurrency is bounded by the container thread pool, so a modest number of large downloads can exhaust it. | `ThrottlingInputStream.read*`, `ExistingFile:92` | Availability risk under load; no async/`StreamingResponseBody` usage | Medium |
| P4 | **Per-request `RateLimiter`** means the 1 MB/s cap is per download, not per server; aggregate egress is unbounded. | `ExistingFile:92` | Bandwidth cannot be capped globally | Low |
| P5 | **Unbuffered single-byte `read()` path** acquires a permit per byte if a caller uses the no-arg `read()`; permit acquisition per byte is far more expensive than the byte copy. Mitigated in practice because `FileSystemPointer.open` wraps in `BufferedInputStream` and Spring copies with a byte array. | `ThrottlingInputStream:19-22` | Low in current usage | Low |
| P6 | **No `Content-Range` / resumable download support** — an interrupted large download must restart from zero, re-hashing the file. | `ExistingFile` | Functional/efficiency gap | Low |

## Maintainability Hotspots

| Area | Why it is a hotspot |
|---|---|
| `pom.xml` | 20 manually version-pinned dependencies, several inconsistent with the parent BOM and with the unused `spring.version` property; this file carries nearly all of the project's debt |
| `FileSystemStorage` | Must be replaced entirely for any real deployment; everything downstream of it is already abstracted, so the blast radius is contained |
| Guava usage in `ExistingFile.toMediaType` | Uses `MediaType.charset().transform(...).or(...)`, an API removed in later Guava versions — a guaranteed edit during any dependency upgrade |
| `javax.*`-era Spring web types | A Spring Boot 3+ upgrade requires the `jakarta.*` namespace; `Sha512ShallowEtagHeaderFilter`'s framework-package subclassing is also sensitive to framework internals changing |

## Summary

Intrinsic code complexity is **Low** and well controlled. The real risk is concentrated in (a) platform age, (b) the per-request full-file hashing cost, and (c) the inverted `modifiedAfter` naming. See [../technical-debt/remediation-plan.md](../technical-debt/remediation-plan.md).
