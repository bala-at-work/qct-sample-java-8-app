# Test Specifications

[← Back to index](../README.md) · Related: [component-order.md](component-order.md) · [validation-criteria.md](validation-criteria.md)

This document catalogs the existing test suite as the behavioural contract of the system, then specifies the gaps a migration should additionally cover.

**No tests were executed during this analysis.** All content below is derived from reading the source.

## Existing Suite

### `DownloadControllerSpec` — `src/test/groovy/com/nurkiewicz/download/DownloadControllerTest.groovy`

Spock `Specification`. Context: `@WebAppConfiguration`, `@ContextConfiguration(classes = [MainApplication])`, `@ActiveProfiles("test")`, driven through `MockMvc` built from the injected `WebApplicationContext`. Fixture URL: `/download/${FileExamples.TXT_FILE_UUID}/file.txt`, resolving via `FileStorageStub` to `src/test/resources/download.txt` (content `foobar`).

| # | Feature method | Request | Expected | Lines |
|---|---|---|---|---|
| T1 | `should return bytes of existing file` | `GET` known UUID | `200`, body `foobar` | 36–43 |
| T2 | `should return 404 and no content` | `GET` `NOT_FOUND_UUID` | `404`, empty body | 45–52 |
| T3 | `should send file if ETag not present` | `GET`, no validators | `200` | 54–61 |
| T4 | `should send file if ETag present but not matching` | `GET` with `If-None-Match: "WHATEVER"` | `200` | 63–71 |
| T5 | `should not send file if ETag matches content` | `GET` with `If-None-Match` = current ETag | `304` **and** `ETag` header echoed | 73–85 |
| T6 | `should not return file if wasn't modified recently` | `GET` with `If-Modified-Since` = file's `Last-Modified` | `304` | 87–98 |
| T7 | `should not return file if server has older version than the client` | `GET` with `If-Modified-Since` = `lastModified + 60s` | `304` | 100–111 |
| T8 | `should return file if was modified after last retrieval` | `GET` with `If-Modified-Since` = `lastModified - 60s` | `200` | 113–124 |
| T9 | `should return 200 OK on HEAD request, but without body` | `HEAD` | `200`, empty body | 126–135 |
| T10 | `should return 304 on HEAD request if we have cached version` | `HEAD` with matching `If-None-Match` | `304` **and** `ETag` echoed | 137–147 |
| T11 | `should return Content-length header` | `HEAD` | `200`, `Content-Length` == `TXT_FILE.size` | 149–158 |
| T12 | `should return content type in response` | `HEAD` | `200`, `Content-Type: text/plain` | 160–169 |

Helper: `toDateHeader(Instant)` (171–174) formats with `DateTimeFormatter.RFC_1123_DATE_TIME` at `ZoneOffset.UTC`.

### Test support types

| Type | Role |
|---|---|
| `FileStorageStub` | `@Profile("test")` `FileStorage`; resolves only `TXT_FILE_UUID` |
| `FileExamples` | `TXT_FILE_UUID`, `TXT_FILE` (`FileSystemPointer` over `/download.txt`), `NOT_FOUND_UUID` |
| `src/test/resources/download.txt` | 6-byte `foobar` fixture, probes as `text/plain` |

## Behavioural Contract Extracted from the Suite

Any migration must preserve all of the following observable behaviours:

| ID | Contract | Pinned by |
|---|---|---|
| C1 | Known UUID returns the exact file bytes with `200` | T1, T3 |
| C2 | Unknown UUID returns `404` with an empty body | T2 |
| C3 | Non-matching `If-None-Match` does not suppress the body | T4 |
| C4 | Matching `If-None-Match` returns `304` and re-sends the `ETag` | T5, T10 |
| C5 | `If-Modified-Since` equal to or later than `Last-Modified` returns `304` | T6, T7 |
| C6 | `If-Modified-Since` earlier than `Last-Modified` returns `200` | T8 |
| C7 | `HEAD` returns `200` with full headers and no body | T9, T11, T12 |
| C8 | `Content-Length` equals the file size even on `HEAD` | T11 |
| C9 | `Content-Type` reflects the probed media type | T12 |
| C10 | ETag format is a quoted SHA-512 hash (compared by exact equality against `FilePointer.getEtag()`) | T5, T10 |
| C11 | `If-Modified-Since` is parsed from RFC 1123 | T6–T8 |
| C12 | Profile `test` selects `FileStorageStub`; the real adapter is excluded | all |

## Coverage Assessment

| Area | Covered | Gap |
|---|---|---|
| `GET /download/{uuid}/{filename}` | ✔ thoroughly (9 specs) | — |
| `GET /download/{uuid}` (`301` redirect) | ✖ | **No test at all** — `ExistingFile.redirect` and `redirectDownload` are entirely untested, including the `Location` header value |
| `HEAD` on both endpoints | ✔ for the download endpoint | redirect endpoint untested |
| `304` paths | ✔ both validators | multi-value / weak `If-None-Match` untested |
| `ThrottlingInputStream` | ✖ | No unit test for `read` overloads, `skip` (not throttled), `mark`/`reset`, `close` delegation |
| `FileSystemPointer` | indirectly, via `FileExamples` | Error paths untested: non-existent file, unreadable file, `probeContentType` returning `null` (the `mediaTypeOrNull` branch) |
| `FileSystemStorage` | ✖ | The production adapter has no test; its `getResource` null branch is unexercised |
| `MainApplication` | indirectly (context boot) | — |
| `Sha512ShallowEtagHeaderFilter` | ✖ | Unwired and untested |
| Malformed UUID handling | ✖ | No spec asserts the `400`-class binding failure |
| Throttling behaviour end-to-end | ✖ | No spec asserts the 1 MB/s cap |

## Recommended Additional Specifications

Added to the suite, not replacing it. Guardrail: existing tests must be preserved; these are new.

| ID | Specification | Rationale |
|---|---|---|
| N1 | `GET /download/{uuid}` returns `301` with `Location: /download/{uuid}/{originalName}` | Closes the largest coverage hole; `redirectDownload` is currently unverified |
| N2 | `GET /download/{uuid}` with a matching `If-None-Match` returns `304` (not `301`) | Pins the caching-before-redirect precedence in `serveWithCaching` |
| N3 | `HEAD /download/{uuid}` behaves as N1 | Verb parity |
| N4 | `GET /download/not-a-uuid/file.txt` returns a `400`-class response | Pins the type-binding validation that acts as the input-validation control |
| N5 | Unit test: `ThrottlingInputStream.read(byte[],int,int)` acquires `len` permits and delegates; `skip` acquires none | Protects the deliberate `skip` exemption |
| N6 | Unit test: `ThrottlingInputStream` delegates `available`, `mark`, `reset`, `markSupported`, `close` | Decorator completeness |
| N7 | Unit test: `FileSystemPointer` with a file whose content type cannot be probed ⇒ `getMediaType()` empty and no `Content-Type` header | Covers the `mediaTypeOrNull` null branch and the `response()` `ifPresent` branch |
| N8 | Unit test: `FileSystemPointer` over a non-existent file throws on construction | Pins the error-wrapping contract before it is narrowed (R8) |
| N9 | Unit test: `FileSystemPointer.getEtag()` is the quoted SHA-512 of the content and changes when content changes | Directly protects C10 through a Guava upgrade |
| N10 | Unit test: `modifiedAfter` truth table at `lastModified - 1`, `lastModified`, `lastModified + 1` | Pins the inverted-naming semantics before any rename (R9) |
| N11 | Spec: `FileSystemStorage` returns a present `Optional` for an arbitrary UUID (documents current stub behaviour) | Makes the stub's behaviour explicit so replacing it is a visible change |

## Execution Notes for the Migration

- The Groovy spec is **not compiled by the declared build** (no Groovy compiler plugin in `pom.xml`). Add `gmavenplus-plugin` before relying on the suite as a migration gate — this is the single most important test-infrastructure fix.
- The file is named `DownloadControllerTest.groovy` but declares `class DownloadControllerSpec`; align them (or configure Surefire includes for both `*Test` and `*Spec`) so discovery is deterministic.
- `FileExamples.TXT_FILE_UUID` is a fresh random UUID per JVM, so specs are order-independent and no fixture cleanup is required.
- `FileExamples.TXT_FILE` resolves a classpath resource through `File`, which requires the test resources to be on an exploded filesystem path — this works for Surefire but would break if the fixture were ever read from inside a JAR.
