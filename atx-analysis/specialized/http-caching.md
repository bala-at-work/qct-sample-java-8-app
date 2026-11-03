# HTTP Caching and Download Semantics

[← Back to index](../README.md)

The service's distinguishing feature is a careful implementation of RFC 7232 conditional requests. This document specifies the exact rules as implemented.

## Response Matrix

| Endpoint | Method | Cached? | Status | Body | Headers |
|---|---|---|---|---|---|
| `/download/{uuid}` | `GET` | no | `301` | none | `Location` only |
| `/download/{uuid}` | `HEAD` | no | `301` | none | `Location` only |
| `/download/{uuid}` | `GET`/`HEAD` | yes | `304` | none | `ETag`, `Content-Length`, `Last-Modified`, `Content-Type`* |
| `/download/{uuid}/{filename}` | `GET` | no | `200` | throttled bytes | `ETag`, `Content-Length`, `Last-Modified`, `Content-Type`* |
| `/download/{uuid}/{filename}` | `HEAD` | no | `200` | none | same as above |
| `/download/{uuid}/{filename}` | `GET`/`HEAD` | yes | `304` | none | same as above |
| either | `GET`/`HEAD` | — (not found) | `404` | none | none |
| either | `GET`/`HEAD` | — (bad UUID) | `400` | framework default | framework default |

\* `Content-Type` is emitted only when `FilePointer.getMediaType()` is present.

## Cache Evaluation Rules

Implemented in `ExistingFile.cached(Optional<String>, Optional<Date>)`:

```
cached = matchingEtag OR notModifiedSince

matchingEtag      = ifNoneMatch.map(filePointer::matchesEtag).orElse(false)
notModifiedSince  = ifModifiedSince.map(Date::toInstant)
                                   .map(filePointer::modifiedAfter)
                                   .orElse(false)
```

| Rule | Behavior |
|---|---|
| R1 | A matching `If-None-Match` alone is sufficient for `304`. |
| R2 | A satisfied `If-Modified-Since` alone is sufficient for `304`. |
| R3 | The two conditions are **OR**-combined. RFC 7232 §6 gives `If-None-Match` precedence and says `If-Modified-Since` should be ignored when `If-None-Match` is present; this implementation evaluates both and ORs them. The practical difference is narrow (a non-matching ETag combined with a satisfied date still yields `304` here, whereas strict RFC precedence would yield `200`), but it is a deliberate deviation worth noting. |
| R4 | A missing header contributes `false` — never an error. |
| R5 | An unparseable `If-Modified-Since` yields an empty `Optional` and therefore `false` (treated as not cached). |
| R6 | Caching is evaluated **before** the redirect-vs-serve decision, so `304` can be returned from either endpoint. |
| R7 | On the `304` path the file is never opened. |

### `If-Modified-Since` comparison

`FileSystemPointer.modifiedAfter(Instant clientTime)`:

```java
return !clientTime.isBefore(getLastModified());
```

| Client timestamp vs. server last-modified | Result | Response | Pinned by |
|---|---|---|---|
| Earlier | `false` | `200` with body | Spec 8 |
| Exactly equal | `true` | `304` | Spec 6 |
| Later | `true` | `304` | Spec 7 |

The "equal counts as current" boundary is correct for HTTP, since `Last-Modified` has one-second granularity while the underlying `File.lastModified()` is millisecond-precision — treating equality as stale would cause spurious re-downloads.

> The method name reads as the inverse of what it returns: `modifiedAfter` is `true` when the resource was **not** modified after the client's copy. This is the most easily misread line in the codebase.

## ETag Generation

| Aspect | Implementation |
|---|---|
| Algorithm | SHA-512 (`com.google.common.hash.Hashing.sha512()`) |
| Input | Full file content (`Files.hash(target, Hashing.sha512())`) |
| Format | `"<128 hex characters>"` — including the surrounding double quotes |
| Validator strength | **Strong** (no `W/` prefix) |
| Computed | Eagerly, in the `FileSystemPointer` constructor |
| Comparison | Exact string equality in `matchesEtag` |

**Properties:**
- Content-derived, so the tag is identical across replicas and stable across restarts and file moves. A load balancer can route freely without invalidating client caches.
- Leaks no filesystem metadata, unlike inode- or mtime-derived tags.
- Changes if and only if the content changes.

**Costs and gaps:**
- **O(file size) on every request**, including `HEAD` and `304` responses that transfer no bytes. This is the dominant runtime cost in the system and an unauthenticated CPU/disk amplification lever (finding S-07).
- Only a single exact ETag is compared. `If-None-Match` lists (`"a", "b"`) and the wildcard `*` are not handled — a multi-value header simply fails to match and yields `200`.
- `If-Match` and `If-Unmodified-Since` are not implemented (they are only meaningful for mutating requests, which this service does not expose).
- `tag` is cached from construction while `getSize()`/`getLastModified()` read live from the filesystem, so the validators can disagree if the file changes mid-request.

## The Redirect Pattern

```
GET /download/3f1b2c84-9a6e-4d0b-8c11-7e2a5d4f9b03
  → 301 Moved Permanently
    Location: /download/3f1b2c84-9a6e-4d0b-8c11-7e2a5d4f9b03/logback.xml

GET /download/3f1b2c84-9a6e-4d0b-8c11-7e2a5d4f9b03/logback.xml
  → 200 OK + content
```

**Rationale:** the filename appears as the last path segment, so browsers derive the correct save-as name without a `Content-Disposition` header. The `{filename}` segment is declared in the mapping but **never bound and never used for file resolution** — it is purely cosmetic, which is also what keeps the path-traversal vector closed.

**Trade-offs:**
- Costs an extra round trip per download.
- `301` (permanent) rather than `302`/`307` means intermediaries and browsers may cache the mapping indefinitely. Since the target filename derives from `getOriginalName()`, a renamed file would leave stale cached redirects.
- The `301` response is built directly with `.body(null)` and does **not** pass through `ExistingFile.response`, so it omits `ETag`, `Content-Length`, and `Last-Modified` that the `200`/`304` paths include — an inconsistency in header coverage.
- The redirect endpoint still honors conditional requests (R6), so a client with a fresh cache gets `304` rather than `301`.
- `URISyntaxException` during `Location` construction is wrapped in `IllegalArgumentException` → `500`.

## `HEAD` Semantics

`ExistingFile.resourceToReturn`:

```java
if (method == HttpMethod.GET) return buildResource(filePointer);
else                          return null;
```

| Guarantee | Mechanism |
|---|---|
| `HEAD` headers are byte-identical to `GET` headers | Both paths call `ExistingFile.response` with the same `FilePointer` |
| `HEAD` never opens the file | `resourceToReturn` returns `null` before `buildResource` is reached |
| `HEAD` honors conditional requests | `serveWithCaching` runs before the method check |
| `Content-Length` is accurate on `HEAD` | Taken from `getSize()`, not from the (absent) body — pinned by Spec 11 |

This is a correct `HEAD` implementation: clients can probe size, type, and validators at no transfer cost. Note the hash is still computed, so the *server* cost is not zero.

## Bandwidth Throttling

| Aspect | Implementation |
|---|---|
| Mechanism | `ThrottlingInputStream` wrapping the file stream, gated by a Guava `RateLimiter` |
| Rate | `FileUtils.ONE_MB` = 1,048,576 bytes/second |
| Scope | **Per request** — a new `RateLimiter` is created in `ExistingFile.buildResource` for every download |
| Applied to | `read()`, `read(byte[])`, `read(byte[],int,int)` |
| Not applied to | `skip(long)` — bytes skipped bypass the limit (though they are not transferred either) |
| Configurability | **None** — hardcoded (finding TD-16 / R-09) |

**Consequences:**
- N concurrent downloads consume up to N MB/s; there is no global or per-client cap.
- `RateLimiter.acquire(n)` blocks the servlet worker thread for the throttle delay. Because the body is streamed by the container *after* the handler returns, the thread is held for the full transfer — a 100 MB file occupies one worker for roughly 100 seconds. Under concurrency the thread pool becomes the binding constraint (finding S-08).
- Throttling applies only to `GET`; `HEAD`, `301`, and `304` transfer nothing and are unaffected.

## Headers Emitted by `ExistingFile.response`

```java
ResponseEntity.status(status)
    .eTag(filePointer.getEtag())
    .contentLength(filePointer.getSize())
    .lastModified(filePointer.getLastModified().toEpochMilli());
filePointer.getMediaType().map(this::toMediaType).ifPresent(responseBuilder::contentType);
```

| Header | Source | Always present? |
|---|---|---|
| `ETag` | `getEtag()` | Yes on `200`/`304` |
| `Content-Length` | `getSize()` | Yes on `200`/`304` |
| `Last-Modified` | `getLastModified().toEpochMilli()` | Yes on `200`/`304` |
| `Content-Type` | `getMediaType()` via `toMediaType` | Only when the type was probed successfully |

Headers **not** emitted: `Cache-Control`, `Expires`, `Vary`, `Content-Disposition`, `Accept-Ranges`.

## Gaps Against Full HTTP Semantics

| Feature | Status | Impact |
|---|---|---|
| `Range` / `206 Partial Content` | Not implemented | No resumable downloads; `Accept-Ranges` is not advertised. Significant for a download service handling large files. |
| `Cache-Control` / `Expires` | Not emitted | Clients must revalidate on every request; the service gets no benefit from freshness-based caching, only validation-based. |
| `Content-Disposition` | Not emitted | Mitigated by the redirect pattern, but offers no control over `inline` vs `attachment`. |
| `If-None-Match` lists and `*` | Not handled | Multi-value headers silently fail to match → unnecessary `200` |
| `If-Match` / `If-Unmodified-Since` | Not implemented | Not applicable — no mutating endpoints |
| `Vary` | Not emitted | No content negotiation occurs, so no impact |
| Compression | Not configured | Appropriate for already-compressed binaries; a loss for text |
| `ETag` precedence over `If-Modified-Since` | Deviates (OR instead of precedence) | Narrow behavioral difference from RFC 7232 §6 |

## Recommendations

| Priority | Recommendation |
|---|---|
| Medium | Cache the content hash keyed by (path, size, mtime) to remove the O(file size) per-request cost and close the amplification vector |
| Medium | Bound concurrent downloads, or move to `StreamingResponseBody`/a non-blocking stack so throttled transfers do not park worker threads |
| Low | Externalize the throttle rate to configuration (R-09) |
| Low | Emit `Cache-Control` with an explicit policy |
| Low | Support `Range` requests for resumable large-file downloads |
| Low | Handle `If-None-Match` lists and `*` |
| Low | Include `ETag`/`Content-Length`/`Last-Modified` on the `301` response for consistency |
| Low | Rename `FilePointer.modifiedAfter` to read in the direction of its return value |

See [../diagrams/behavioral.md](../diagrams/behavioral.md) for the flows and [../migration/test-specifications.md](../migration/test-specifications.md) for the specifications that pin these rules.
