# Complexity Analysis

- [Back to README](../README.md)
- Siblings: [code-metrics.md](code-metrics.md) · [dependency-analysis.md](dependency-analysis.md) · [security-patterns.md](security-patterns.md) · [tech-debt.md](tech-debt.md)

> Complexity figures below are derived by static inspection — counting decision points, nesting levels and dependencies in the source. No profiling, instrumentation, build or test execution was performed.

## Table of Contents
1. [Summary](#summary)
2. [Cyclomatic Complexity by Method](#cyclomatic-complexity-by-method)
3. [Class-Level Complexity](#class-level-complexity)
4. [Complexity Hotspots](#complexity-hotspots)
5. [Performance Hotspots](#performance-hotspots)
6. [Concurrency Complexity](#concurrency-complexity)
7. [Cognitive Complexity](#cognitive-complexity)
8. [Upgrade-Risk Complexity](#upgrade-risk-complexity)
9. [Conclusions](#conclusions)

## Summary

| Metric | Value | Assessment |
|---|---|---|
| Highest method cyclomatic complexity | **3** (`ExistingFile.cached`) | Very low |
| Methods with complexity > 5 | **0** | None |
| Methods with complexity > 1 | 7 of 46 | 85% of methods are straight-line |
| Maximum nesting depth | **2** | Very low |
| Longest method | 11 lines (`ExistingFile.response`) | Very low |
| Longest class | 121 lines (`ExistingFile`) | Very low |
| Loops in the entire codebase | **0** | No iteration logic |
| `switch` statements | **0** | — |
| Recursion | **0** | — |
| Dependency cycles | **0** | — |

**Overall: complexity is not a problem in this codebase.** There is no method anywhere that a reader cannot hold in their head. The genuine risks in this repository are version currency and build wiring, not algorithmic or structural complexity. That said, two *performance* hotspots and one *concurrency* characteristic deserve attention, and they are documented below.

## Cyclomatic Complexity by Method

Complexity counted as 1 + the number of independent decision points (branches, ternaries, boolean short-circuits, lambdas with conditional effect).

### `ExistingFile`
| Method | Complexity | Decision points |
|---|---|---|
| `cached(Optional, Optional)` | **3** | Two `Optional.map(...).orElse(false)` chains plus the `\|\|` short-circuit |
| `toMediaType(MediaType)` | 2 | Guava `Optional.transform(...).or(...)` — charset present or absent |
| `resourceToReturn(FilePointer)` | 2 | `if (method == HttpMethod.GET)` |
| `serveWithCaching(...)` | 2 | `if (cached(...))` |
| `redirectDownload(FilePointer)` | 2 | `try`/`catch (URISyntaxException)` |
| `response(FilePointer, HttpStatus, Resource)` | 2 | `getMediaType().map(...).ifPresent(...)` |
| `redirect`, `handle`, `serveDownload`, `buildResource`, `notModified`, constructor | 1 | Straight-line |

### `DownloadController`
| Method | Complexity | Decision points |
|---|---|---|
| `redirect(...)` | 2 | `.map(...).orElseGet(...)` — found or not found |
| `download(...)` | 2 | Same |
| `findExistingFile(...)` | 1 | Straight-line `.map` |
| constructor | 1 | — |

### `FileSystemPointer`
| Method | Complexity | Decision points |
|---|---|---|
| `FileSystemPointer(File)` | **3** | `try`/`catch (IOException)` plus the `contentType != null` ternary |
| `open()` | 2 | `try`/`catch (FileNotFoundException)` |
| `getMediaType()` | 1 | `Optional.ofNullable` — no explicit branch |
| `getSize`, `getOriginalName`, `getEtag`, `toString`, `matchesEtag`, `getLastModified`, `modifiedAfter` | 1 | Single-expression |

### `ThrottlingInputStream`
Every one of the 10 methods has complexity **1** — each either acquires permits and delegates, or delegates directly. No branches at all.

### `FileSystemStorage` / `FileStorageStub` / `MainApplication`
| Method | Complexity |
|---|---|
| `FileSystemStorage.findFile` | 1 — no branch; the `uuid` is not even examined |
| `FileStorageStub.findFile` | 2 — `if (uuid.equals(TXT_FILE_UUID))` |
| `MainApplication.main` | 1 |

### Distribution
```
Complexity 1  ██████████████████████████████████████  39 methods (85%)
Complexity 2  ██████                                   5 methods (11%)
Complexity 3  ██                                       2 methods ( 4%)
Complexity 4+                                          0 methods ( 0%)
```

## Class-Level Complexity

| Class | Lines | Methods | Fields | Weighted methods (Σ complexity) | Assessment |
|---|---|---|---|---|---|
| `ExistingFile` | 121 | 11 | 4 | 19 | Highest in the project, still low. The 11 methods are small and form one coherent decision pipeline |
| `FileSystemPointer` | 86 | 10 | 3 | 13 | Low. Complexity concentrated in the constructor |
| `ThrottlingInputStream` | 66 | 10 | 2 | 10 | Minimal — 10 methods, all complexity 1 |
| `DownloadController` | 63 | 4 | 1 | 6 | Minimal. Two near-identical handlers |
| `FileSystemStorage` | 33 | 1 | 1 | 1 | Trivial (it is a stub) |
| `FilePointer` | 26 | 8 abstract | 0 | — | Interface; 8 methods is moderately wide for a port but each is a distinct HTTP concern |
| `MainApplication` | 13 | 1 | 0 | 1 | Trivial |
| `FileStorage` | 8 | 1 abstract | 0 | — | Ideal interface width |

## Complexity Hotspots

Ranked by where a maintainer's attention is actually required. Note that none of these is "complex" in absolute terms — this is a relative ranking within a very simple codebase.

### 1. `ExistingFile.cached(...)` — the most logic-dense method
```java
final boolean matchingEtag = requestEtagOpt
        .map(filePointer::matchesEtag)
        .orElse(false);
final boolean notModifiedSince = ifModifiedSinceOpt
        .map(Date::toInstant)
        .map(filePointer::modifiedAfter)
        .orElse(false);
return matchingEtag || notModifiedSince;
```
**Why it is the hotspot.** Six lines encode the entire HTTP cache-validation contract, and every subtlety of the system's correctness lives here: two independent validators, each defaulting to `false` when its header is absent, combined with `||` so that either alone suffices. A reader must also know that `modifiedAfter` answers "the client copy is still valid" despite its name reading as the opposite.

**Risk.** This is the method most likely to break *silently* during a Spring upgrade, because it depends on `Optional<Date>` header binding behavior rather than on anything in this file. If Spring stops populating `ifModifiedSinceOpt`, the method returns `false` and the server simply serves the full file every time — a correctness and bandwidth regression with no error, no exception and no log entry.

**Mitigation.** Four of the 12 Spock feature methods target exactly this logic. This is the single strongest argument for registering `src/test/groovy` before any upgrade.

### 2. `FileSystemPointer` constructor — complexity plus I/O
```java
this.target = target;
this.tag = Files.hash(target, Hashing.sha512());
final String contentType = java.nio.file.Files.probeContentType(target.toPath());
this.mediaTypeOrNull = contentType != null ? MediaType.parse(contentType) : null;
```
**Why it is a hotspot.** Complexity 3 (the try/catch plus the null ternary) is the joint highest in the project, but the real issue is that a *constructor* performs two I/O operations — a full-file hash and a filesystem MIME probe. See [Performance Hotspots](#performance-hotspots).

**Secondary concern.** A fully-qualified `java.nio.file.Files` is required because Guava's `com.google.common.io.Files` is imported — a readability trap that makes it easy to misread which library is being called.

### 3. `ExistingFile.serveWithCaching` + the method-reference strategy
```java
private ResponseEntity<Resource> serveWithCaching(
        Optional<String> requestEtagOpt, Optional<Date> ifModifiedSinceOpt,
        Function<FilePointer, ResponseEntity<Resource>> notCachedResponse) {
    if (cached(requestEtagOpt, ifModifiedSinceOpt))
        return notModified(filePointer);
    return notCachedResponse.apply(filePointer);
}
```
**Why it rates a mention.** Cyclomatic complexity is only 2, but the *indirection* is the highest in the codebase: understanding `redirect()` requires following `this::redirectDownload` through a `Function` parameter into `apply`. This is the right design — it is why the 301 and 200 paths share identical 304 semantics with zero duplication — but it is the one place where control flow is not locally obvious.

### 4. `DownloadController` — duplication rather than complexity
`redirect(...)` and `download(...)` are byte-for-byte identical apart from the mapping annotation and whether they call `file.redirect` or `file.handle`. Complexity 2 each; the only cost is that a change to the parameter list must be made twice.

### 5. `ExistingFile.toMediaType` — library-API complexity
```java
return input.charset()
        .transform(c -> new MediaType(input.type(), input.subtype(), c))
        .or(new MediaType(input.type(), input.subtype()));
```
Complexity 2, but it uses Guava 18's `Optional.transform`/`or` rather than `java.util.Optional`. This Guava-era API has since been deprecated in favour of JDK `Optional` interop, making this three-line method a concrete Guava-upgrade touch point.

## Performance Hotspots

Identified by static reasoning about I/O and allocation in the request path.

### 🔴 H1 — Full-file SHA-512 hash computed per request
**Location.** `FileSystemPointer` constructor → `Files.hash(target, Hashing.sha512())`

**Mechanism.** `FileStorage.findFile` constructs a new `FileSystemPointer` on every request, and the constructor reads the **entire file** to compute its hash. The cost is O(file size) in both I/O and CPU, paid before a single byte of response is written.

**Consequences.**
| Scenario | Effect |
|---|---|
| `200 OK` for a 1 GB file | 1 GB is read to hash it, then 1 GB is read again to stream it — the file is read **twice** |
| `304 Not Modified` | The full file is hashed solely to produce an ETag for a response with no body — pure waste |
| `301` redirect | Same: the full file is hashed to serve a redirect that needs only the filename |
| Concurrent requests | Each request hashes independently; no result is shared or cached |

**Severity: 🟡 Low** in the current codebase, because the stub only ever serves a small `logback.xml`. **It becomes severe the moment real files are served**, which makes it a prerequisite fix for any real storage implementation rather than an optimization.

**Remediation.** Compute the ETag lazily (only when `getEtag()`/`matchesEtag()` is called) and cache it keyed by (path, size, lastModified). Keep SHA-512 — do not weaken the hash to a cheaper one. For an S3-backed adapter, use the object's stored ETag or metadata rather than hashing content at all. Tracked as P3.5 in [../technical-debt/remediation-plan.md](../technical-debt/remediation-plan.md).

### 🟠 H2 — `probeContentType` filesystem call per request
**Location.** `FileSystemPointer` constructor → `java.nio.file.Files.probeContentType(target.toPath())`

**Mechanism.** An additional filesystem/OS MIME-database lookup on every pointer construction, i.e. every request. Cheaper than H1 but equally unnecessary for a `304` or `301`.

**Secondary issue — correctness, not just speed.** `probeContentType` is platform-dependent: it consults OS MIME databases on Linux but may fall back to extension matching elsewhere, and can return `null`. The same file can therefore yield different `Content-Type` headers on different hosts, and the Spock assertion of `text/plain` for `download.txt` is mildly platform-sensitive.

**Severity: 🟡 Low.** Remediation is the same lazy/cached approach as H1.

### 🟠 H3 — Blocking rate limiter holds a request thread
**Location.** `ThrottlingInputStream.read*` → `RateLimiter.acquire(n)`

**Mechanism.** `RateLimiter.acquire` **blocks** the calling thread until permits are available. Since Spring writes the response body on the Tomcat worker thread, each in-flight download occupies one worker for its entire duration.

**Arithmetic.** At 1 MB/s, a 100 MB file holds a thread for ~100 seconds. Tomcat's default `maxThreads` in this Boot generation is 200, so roughly 200 concurrent downloads exhaust the connector pool — after which **all** requests queue, including the cheap `/health` and `304` ones.

**Compounding factor.** The limiter is created fresh per response (`RateLimiter.create(ONE_MB)` inside `buildResource`), so the 1 MB/s ceiling is **per download, not global**. Aggregate egress is unbounded: N concurrent downloads consume up to N MB/s. The throttle protects the client's fair share, not the server's bandwidth.

**Severity: 🟡 Low** as a design trade-off, but it is the system's binding scalability constraint and should be stated explicitly in any capacity plan. A reactive/async rewrite (`StreamingResponseBody` or WebFlux) would decouple throughput from thread count, but neither is available in Spring 4.1/Boot 1.2 — another reason the framework upgrade unlocks downstream improvements.

### 🟡 H4 — Permits acquired for requested rather than actual bytes
**Location.** `ThrottlingInputStream.read(byte[] b)` acquires `b.length`; `read(b, off, len)` acquires `len`.

Permits are charged for the length *requested*, not the count *returned*. A short read near end-of-file slightly over-charges the limiter. Benign and self-correcting; noted only for completeness.

### 🟡 H5 — Double allocation per response
Each `200` response allocates a `BufferedInputStream`, a `FileInputStream`, a `RateLimiter`, a `ThrottlingInputStream`, an `InputStreamResource`, a `ResponseEntity` and an `ExistingFile`. All are short-lived and trivially collected; at the request rates this application could sustain (bounded by H3), allocation pressure is irrelevant.

## Concurrency Complexity

| Aspect | Finding |
|---|---|
| Threads created by application code | **0** |
| Executors, thread pools, schedulers | **0** |
| `synchronized` blocks | 0 |
| `synchronized` methods | 2 — `ThrottlingInputStream.mark(int)` and `reset()`, purely to honour the `InputStream` contract |
| `volatile` fields | 0 |
| Atomic types | 0 |
| Locks, semaphores, latches | 0 (Guava's `RateLimiter` manages its own internal synchronization) |
| Shared mutable state | **None** |
| Thread-confined objects | `ExistingFile`, `FileSystemPointer`, `ThrottlingInputStream`, `RateLimiter` — all per-request |
| Singleton beans | `DownloadController`, `FileSystemStorage` — both stateless |

**Assessment: concurrency complexity is effectively zero.** All threading is Tomcat's; the application contributes no coordination logic. Thread safety is achieved structurally through 100% `final` fields and stateless singletons rather than through locking — the strongest form of thread safety and one that carries no complexity cost.

One subtlety worth recording: because a fresh `RateLimiter` is created per response rather than shared, there is no contention on the limiter — but also no global throttle. That is a deliberate (if undocumented) trade of coordination complexity for unbounded aggregate egress.

## Cognitive Complexity

Where a new maintainer will actually spend reading effort, as distinct from cyclomatic complexity.

| Rank | Area | Why it demands thought |
|---|---|---|
| 1 | `ExistingFile.cached` + `FilePointer.modifiedAfter` | The method name reads as the inverse of its semantics (`modifiedAfter` returns `true` when the *client's* copy is still valid). Requires HTTP conditional-request knowledge to follow |
| 2 | `serveWithCaching` with a `Function` parameter | Control flow leaves the method via a passed-in method reference; understanding `redirect()` means tracing `this::redirectDownload` |
| 3 | `resourceToReturn` returning `null` for HEAD | The HEAD/GET distinction is encoded as a `null` resource rather than an explicit branch in the response pipeline — elegant, but surprising on first read |
| 4 | Three coexisting `MediaType`-like types | `String` from `probeContentType`, Guava `MediaType` in the port, Spring `MediaType` on the wire, bridged by `toMediaType` |
| 5 | Guava vs JDK `Files` collision | `com.google.common.io.Files` is imported, forcing a fully-qualified `java.nio.file.Files` in the same constructor |
| 6 | Profile-based bean selection | Understanding which `FileStorage` is active requires knowing that `@Profile("!test")` and `@Profile("test")` are complementary — and that the pairing is load-bearing |
| 7 | ETag quoting convention | `getEtag()` must return a pre-quoted value; this obligation is undocumented and only visible by reading `FileSystemPointer` |

None of these is a defect. They are the seven things worth knowing before modifying this codebase.

## Upgrade-Risk Complexity

Which complexity actually threatens the modernization described in [../technical-debt/remediation-plan.md](../technical-debt/remediation-plan.md).

| Area | Cyclomatic complexity | Upgrade risk | Rationale |
|---|---|---|---|
| `ExistingFile.cached` | 3 | 🔴 **High** | Depends on `Optional<Date>` header binding, which changes across Spring versions and **fails silently** |
| `ExistingFile.response` | 2 | 🔴 **High** | Uses `ResponseEntity.BodyBuilder` — `eTag`, `contentLength`, `lastModified`, `contentType` all evolved in Spring 5/6 |
| `ExistingFile.resourceToReturn` | 2 | 🟠 Medium | `method == HttpMethod.GET` reference comparison; `HttpMethod` became a class rather than an enum in Spring 6 |
| `ExistingFile.toMediaType` | 2 | 🟠 Medium | Guava 18 `Optional.transform`/`or` API, deprecated in later Guava |
| `FileSystemPointer` constructor | 3 | 🟠 Medium | Guava `Files.hash` and `Hashing.sha512()` signatures across Guava majors |
| `ThrottlingInputStream` | 1 (all methods) | 🟡 Low | Only coupling is Guava's `RateLimiter`; no framework dependency at all |
| `DownloadController` annotations | 2 | 🟡 Low | `@RequestMapping(method = {GET, HEAD})` still supported; modernizing to `@GetMapping` is optional |
| `Sha512ShallowEtagHeaderFilter` | 1 | 🟠 Medium | Will not compile — the protected hook's signature changed. Resolution: delete the dead class |

**Key insight.** Upgrade risk is *inversely* correlated with cyclomatic complexity here. The simplest class (`ThrottlingInputStream`, all methods complexity 1) is the safest to carry forward, while the two riskiest methods are riskier because of their *external* dependencies than because of their internal logic. Complexity metrics alone would misdirect the migration effort — framework coupling is the right lens.

## Conclusions

1. **Complexity requires no remediation.** Zero methods exceed complexity 3, zero loops, zero recursion, zero cycles, maximum nesting 2. No refactoring for complexity reduction is warranted, and any proposal to "simplify" this code should be declined.
2. **The real hotspot is H1** — a full-file SHA-512 hash in `FileSystemPointer`'s constructor, executed per request even for `304` and `301` responses. Harmless with the current stub, and a blocking prerequisite for any real storage implementation.
3. **H3 is the binding scalability constraint.** The blocking `RateLimiter` caps concurrent downloads at the Tomcat thread-pool size and provides no global egress ceiling. This should be documented in capacity planning, not "fixed" — it is a deliberate trade-off.
4. **Upgrade risk concentrates in `ExistingFile`**, specifically `cached` and `response`. Both can fail silently. The Spock specification covers both, which is why registering `src/test/groovy` (P0.1) must precede the framework upgrade.
5. **`ThrottlingInputStream` is the safest asset in the codebase** — framework-free, branch-free, and portable to any Java version unchanged.
