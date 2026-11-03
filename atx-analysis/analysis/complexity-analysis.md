# Complexity Analysis

[← Back to index](../README.md) · [Code metrics](code-metrics.md)

## Overall Assessment

Complexity in this codebase is **low and well distributed**. No method contains a loop, no method nests conditionals, and the deepest decision structure is a single `if` or ternary. The functional `Optional`-chaining style eliminates most branching that would otherwise appear as explicit conditionals.

## Cyclomatic Complexity by Method

Counted as 1 + the number of decision points (`if`, ternary, `&&`/`||`, loop, `catch`). Lambda/`Optional` chains are counted at the points where they introduce a branch.

### `ExistingFile` — highest-complexity class

| Method | CC | Decision points |
|---|---|---|
| `cached` | 4 | two `Optional.map(...).orElse(false)` chains + `||` |
| `response` | 2 | `getMediaType().map(...).ifPresent(...)` |
| `redirectDownload` | 2 | `catch (URISyntaxException)` |
| `serveWithCaching` | 2 | `if (cached(...))` |
| `resourceToReturn` | 2 | `if (method == HttpMethod.GET)` |
| `toMediaType` | 2 | Guava `charset().transform(...).or(...)` |
| `buildResource` | 1 | — |
| `serveDownload` | 1 | — |
| `notModified` | 1 | — |
| `redirect` | 1 | — |
| `handle` | 1 | — |
| constructor | 1 | — |
| **Class total** | **20** | across 12 members |

### `DownloadController`

| Method | CC | Decision points |
|---|---|---|
| `redirect` | 2 | `map(...).orElseGet(...)` |
| `download` | 2 | `map(...).orElseGet(...)` |
| `findExistingFile` | 2 | `map(...)` |
| constructor | 1 | — |
| **Class total** | **7** | |

Notably, the controller contains **zero `if` statements**. All conditional behavior is expressed through `Optional` combinators.

### `FileSystemPointer`

| Method | CC | Decision points |
|---|---|---|
| constructor | 3 | `catch (IOException)` + ternary on `contentType != null` |
| `open` | 2 | `catch (FileNotFoundException)` |
| `getMediaType` | 1 | `Optional.ofNullable` (no branch in source) |
| `modifiedAfter` | 1 | single boolean expression |
| `getSize`, `getOriginalName`, `getEtag`, `getLastModified`, `matchesEtag`, `toString` | 1 each | — |
| **Class total** | **13** | |

### `ThrottlingInputStream`

| Method | CC |
|---|---|
| all 10 methods + constructor | 1 each |
| **Class total** | **11** |

Zero branching — the decorator is pure delegation plus a permit acquisition. Its complexity is entirely *behavioral* (blocking semantics), not structural.

### `FileSystemStorage`, `MainApplication`

| Method | CC |
|---|---|
| `FileSystemStorage.findFile` | 1 |
| `MainApplication.main` | 1 |

## Aggregate

| Metric | Value |
|---|---|
| Total production cyclomatic complexity | ~53 |
| Production methods | 46 |
| Average CC per method | ~1.15 |
| Maximum CC (single method) | 4 (`ExistingFile.cached`) |
| Methods with CC ≥ 5 | 0 |
| Methods with CC ≥ 10 | 0 |
| Loops in production code | **0** |
| Nested conditionals | **0** |
| Maximum nesting depth | 2 (method body → `try` block) |

Every method is comfortably below any conventional complexity threshold. There is no method in this codebase that a reviewer would need to trace on paper.

## Cognitive Complexity Hotspots

Structural complexity is low, but a few constructs carry above-average *cognitive* load:

### 1. `ExistingFile.cached` — Medium cognitive load
```java
final boolean matchingEtag = requestEtagOpt.map(filePointer::matchesEtag).orElse(false);
final boolean notModifiedSince = ifModifiedSinceOpt.map(Date::toInstant).map(filePointer::modifiedAfter).orElse(false);
return matchingEtag || notModifiedSince;
```
Two independent `Optional` pipelines, a `Date`→`Instant` hop, and a method (`modifiedAfter`) whose name reads as the inverse of its meaning — it returns `true` when the client's copy is **current**. Reading this correctly requires holding the RFC 7232 semantics in mind simultaneously. This is the single densest piece of logic in the repository and the one most likely to be misread during modification.

### 2. `ExistingFile.serveWithCaching` — Medium cognitive load
Higher-order function taking `Function<FilePointer, ResponseEntity<Resource>>`, invoked with `this::redirectDownload` or `this::serveDownload`. Structurally trivial (CC 2) but requires the reader to jump to the two call sites to understand what actually executes. The indirection is well justified — it removes duplication between the redirect and serve paths — but it is indirection nonetheless.

### 3. `ExistingFile.toMediaType` — Medium cognitive load
```java
return input.charset().transform(c -> new MediaType(input.type(), input.subtype(), c))
             .or(new MediaType(input.type(), input.subtype()));
```
Mixes **Guava's** `Optional` API (`transform`/`or`) with two same-named-but-different `MediaType` types (Guava's and Spring's), requiring a fully qualified parameter type in the signature. Type-shadowing plus a non-JDK `Optional` idiom in three lines.

### 4. `ThrottlingInputStream` blocking semantics — Medium cognitive load
`CC = 1` per method, but `RateLimiter.acquire(n)` blocks the servlet thread. The performance and thread-pool implications are invisible in the control flow. Also note `skip` is deliberately not throttled — an asymmetry a reader must notice on their own, as it is uncommented.

### 5. `DownloadController` header binding — Medium cognitive load
`@RequestHeader(IF_MODIFIED_SINCE) Optional<Date>` hides two layers of framework behavior: RFC 1123 parsing and `Optional` unwrapping. Nothing in the source signals which Spring version's semantics apply.

## Complexity Hotspot Ranking

| Rank | Location | Structural CC | Cognitive load | Primary driver |
|---|---|---|---|---|
| 1 | `ExistingFile.cached` | 4 | Medium | Dual `Optional` pipelines + inverted method naming + HTTP cache semantics |
| 2 | `ExistingFile` (class) | 20 | Medium | Five responsibilities in one class |
| 3 | `ExistingFile.toMediaType` | 2 | Medium | Guava/Spring type shadowing + Guava `Optional` API |
| 4 | `ThrottlingInputStream` | 11 (all CC 1) | Medium | Hidden blocking behavior; un-throttled `skip` |
| 5 | `FileSystemPointer` constructor | 3 | Low | Eager hashing + nullable probe result + exception wrapping |
| 6 | `DownloadController` handlers | 2 each | Low | Framework binding magic |

## Performance Complexity

Algorithmic cost, which the cyclomatic figures do not capture:

| Operation | Cost | Note |
|---|---|---|
| `new FileSystemPointer(file)` | **O(file size)** | `Files.hash(target, Hashing.sha512())` reads the entire file. Incurred on **every request**, including `HEAD` and `304` responses that transfer no bytes. This is the dominant cost in the system and the clearest optimization target — caching the hash keyed by (path, size, mtime) would eliminate it. |
| `FileSystemPointer` construction | + 1 filesystem syscall | `Files.probeContentType` |
| `getSize()`, `getLastModified()` | O(1), 1 syscall each | Read live, not cached |
| Serving the body | O(file size), **rate-limited to 1 MB/s** | A 100 MB file holds a servlet thread for ~100 seconds |
| `cached()` evaluation | O(1) | String equality + instant comparison |
| `FileStorage.findFile` | O(1) in both implementations | Stub returns a constant; test stub does one UUID comparison |

### Scalability implication
Each concurrent download occupies one container thread for the full (throttled) transfer duration, and each request pays a full file hash up front. Under concurrency the limits are, in order: (1) servlet thread pool exhaustion from blocked `acquire` calls, (2) disk I/O from repeated SHA-512 hashing. Neither is visible in the control-flow complexity metrics.

## Recommendations

| Priority | Recommendation |
|---|---|
| Medium | Cache the content hash in the storage adapter rather than recomputing it per request (addresses the O(file size) per-request cost). |
| Low | Rename `FilePointer.modifiedAfter` to something that reads in the direction of its return value (e.g. `isCurrentAsOf`), reducing the cognitive load in `cached`. |
| Low | Extract `toMediaType` into a dedicated converter class to isolate the Guava/Spring `MediaType` shadowing. |
| Low | Split `ExistingFile`'s media-type conversion and throttling-setup concerns out, leaving it focused on caching and response assembly. |
| Low | Add a comment or test documenting that `skip` intentionally bypasses throttling. |
| Low | Consider `StreamingResponseBody` or a reactive stack to avoid holding servlet threads during throttled transfers. |

See [../technical-debt/maintenance-burden.md](../technical-debt/maintenance-burden.md) and [../architecture/patterns.md](../architecture/patterns.md).
