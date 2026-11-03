# Test Specifications

[← Back to index](../README.md)

Specification of the existing test suite and the behaviors it pins, plus the additional coverage needed for a safe migration.

> Per the transformation constraints, no tests were executed during this analysis. Everything below is derived from static reading of the test sources.

## Existing Test Inventory

| File | Type | Count |
|---|---|---|
| `src/test/groovy/com/nurkiewicz/download/DownloadControllerTest.groovy` | Spock integration specification (`class DownloadControllerSpec`) | 12 feature methods |
| `src/test/java/com/nurkiewicz/download/FileStorageStub.java` | Test double (`@Profile("test")` `FileStorage`) | — |
| `src/test/java/com/nurkiewicz/download/FileExamples.java` | Static fixtures | 3 constants |
| `src/test/java/org/springframework/web/filter/Sha512ShallowEtagHeaderFilter.java` | Unregistered filter subclass | 0 tests |
| `src/test/resources/download.txt` | Fixture content (`foobar`, 6 bytes) | — |

### Harness configuration

```groovy
@WebAppConfiguration
@ContextConfiguration(classes = [MainApplication])
@ActiveProfiles("test")
class DownloadControllerSpec extends Specification
```

- Full Spring context, `MockMvc` built via `MockMvcBuilders.webAppContextSetup(wac)` in an `@Autowired` setter.
- `@ActiveProfiles("test")` activates `FileStorageStub` and deactivates `FileSystemStorage`.
- **No mocking framework is used** — the test double is a profile-selected Spring bean.
- Fixture path constant: `TEXT_FILE = '/download/' + FileExamples.TXT_FILE_UUID + '/file.txt'`.
- Date helper: `toDateHeader(Instant)` formats with `DateTimeFormatter.RFC_1123_DATE_TIME` at `ZoneOffset.UTC`.

## Existing Specifications

| # | Feature method | Request | Asserts | Behavior pinned |
|---|---|---|---|---|
| 1 | `should return bytes of existing file` | `GET` `TEXT_FILE` | `200`, body `"foobar"` | Content is served verbatim through the throttling stream |
| 2 | `should return 404 and no content` | `GET` `/download/{NOT_FOUND_UUID}/file.txt` | `404`, body is empty `byte[0]` | `Optional.empty()` → `404` with no body |
| 3 | `should send file if ETag not present` | `GET` `TEXT_FILE`, no validators | `200` | Absent `If-None-Match` is treated as not-cached |
| 4 | `should send file if ETag present but not matching` | `GET` + `If-None-Match: "WHATEVER"` | `200` | Non-matching ETag does not suppress the body |
| 5 | `should not send file if ETag matches content` | `GET` + `If-None-Match: <actual etag>` | `304`, response `ETag` equals the request value | Matching strong ETag → `304`, validator echoed |
| 6 | `should not return file if wasn't modified recently` | `GET` + `If-Modified-Since: <lastModified>` | `304` | Client timestamp **equal** to server's counts as current |
| 7 | `should not return file if server has older version than the client` | `GET` + `If-Modified-Since: <lastModified + 60s>` | `304` | Client timestamp **later** than server's counts as current |
| 8 | `should return file if was modified after last retrieval` | `GET` + `If-Modified-Since: <lastModified − 60s>` | `200` | Client timestamp **earlier** than server's → stale → full body |
| 9 | `should return 200 OK on HEAD request, but without body` | `HEAD` `TEXT_FILE` | `200`, body `byte[0]` | `HEAD` yields status and headers with no body |
| 10 | `should return 304 on HEAD request if we have cached version` | `HEAD` + matching `If-None-Match` | `304`, `ETag` echoed | Caching applies identically to `HEAD` |
| 11 | `should return Content-length header` | `HEAD` `TEXT_FILE` | `200`, `Content-Length` == `TXT_FILE.size` | `Content-Length` reflects real file size even on `HEAD` |
| 12 | `should return content type in response` | `HEAD` `TEXT_FILE` | `200`, `Content-Type: text/plain` | Probed media type is emitted |

Specifications 6, 7, and 8 together form the complete boundary set for `FilePointer.modifiedAfter` (equal / later / earlier), which is the single most upgrade-fragile behavior in the codebase.

## Coverage Assessment

| Behavior | Covered? | By |
|---|---|---|
| `200` with body (`GET`) | Yes | 1, 3, 4, 8 |
| `200` headers only (`HEAD`) | Yes | 9, 11, 12 |
| `304` via ETag | Yes | 5, 10 |
| `304` via `If-Modified-Since` | Yes | 6, 7 |
| `200` when stale | Yes | 8 |
| `404` not found | Yes | 2 |
| `ETag` header emitted | Yes | 5, 10 |
| `Content-Length` header | Yes | 11 |
| `Content-Type` header | Yes | 12 |
| **`301` redirect endpoint (`/download/{uuid}`)** | **No** | — |
| **`Location` header contents** | **No** | — |
| **`Last-Modified` header emitted** | **No** | — |
| **`400` on malformed UUID** | **No** | — |
| **Throttling behavior / rate** | **No** | — |
| **`ThrottlingInputStream` unit behavior** | **No** | — |
| **`FileSystemPointer` unit behavior** | **No** | — |
| **`FileSystemStorage`** | **No** | — |
| **Error paths (`IllegalArgumentException` → `500`)** | **No** | — |

**Assessment:** behavioral coverage of the *serve* endpoint is strong and includes every boundary of the caching logic. Three significant gaps exist: the entire `301` redirect endpoint is untested, the `Last-Modified` header is never asserted, and the two most algorithmically interesting classes (`ThrottlingInputStream`, `FileSystemPointer`) have no direct tests.

## Known Defects in the Test Suite

| Defect | Detail | Action |
|---|---|---|
| Filename/class mismatch | `DownloadControllerTest.groovy` declares `class DownloadControllerSpec`. Groovy requires a public class name to match its filename, so this source does not compile as written. | Rename the file to `DownloadControllerSpec.groovy` (migration step 0.4) |
| No Groovy compiler configured | `pom.xml` declares no `gmavenplus-plugin` or equivalent, so `src/test/groovy` is not compiled by the default Maven lifecycle | Add a Groovy compiler plugin (step 0.5) |
| Missing `@Override` | `FileStorageStub.findFile` | Add the annotation |
| Unreferenced test class | `Sha512ShallowEtagHeaderFilter` is never registered or asserted against | Register and test, or delete |
| Non-deterministic fixtures | `FileExamples.TXT_FILE_UUID` and `NOT_FOUND_UUID` are `UUID.randomUUID()` per JVM run | Acceptable — tests derive paths from the constants rather than hardcoding them |
| Environment-dependent assertion | Specification 12 asserts `Content-Type: text/plain`, which depends on the platform's `Files.probeContentType` implementation for `.txt` | Note as a possible cross-platform flake; do not weaken the assertion |

## Test Integrity Requirements

Per the modification guardrails, during any transformation:

1. **All 12 feature methods must be preserved.** None may be deleted, renamed away, `@Ignore`d, or otherwise disabled.
2. Assertions may be **adjusted** for framework behavior changes (e.g. header casing, Actuator paths) but the behavior being asserted must remain the same.
3. The `@Profile("test")` / `@Profile("!test")` split must be retained — it is the test seam.
4. `FileStorageStub` must continue to return `Optional.empty()` for unknown UUIDs; this is the only thing that makes specification 2 meaningful.
5. New tests are additive and encouraged; they do not substitute for preserving the existing ones.

## Required Validation Tests for Migration

These are the specifications a migration must satisfy. The first group already exists and must continue to pass; the second group should be added to close the coverage gaps.

### Group A — regression (must pass unchanged)

All 12 existing feature methods. Verify specifications **5, 6, 7, 8, and 10 individually**, not merely as part of an aggregate run — a silent `@RequestHeader Optional<Date>` binding regression would turn `304` responses into `200`s, and only these specifications detect it.

### Group B — recommended additions

| Proposed specification | Rationale |
|---|---|
| `GET /download/{uuid}` returns `301` with `Location: /download/{uuid}/{originalName}` | The entire redirect endpoint is currently untested |
| `HEAD /download/{uuid}` returns `301` | Confirms `HEAD` parity on the redirect path |
| `GET /download/{uuid}` with a matching ETag returns `304` | The redirect path's caching short-circuit is untested |
| Response includes a correct `Last-Modified` header | Header is emitted by `ExistingFile.response` but never asserted |
| `GET /download/not-a-uuid/file.txt` returns `400` | Pins the sole input-validation behavior |
| `ThrottlingInputStream` delegates `read()`, `read(byte[])`, `read(byte[],int,int)` correctly and acquires the expected permit counts | Unit coverage for the throttling decorator |
| `ThrottlingInputStream.skip` does **not** consume permits | Documents the intentional asymmetry |
| `ThrottlingInputStream.close` closes the wrapped stream | Resource-lifecycle guarantee |
| `FileSystemPointer.getEtag` returns a quoted SHA-512 of file content | Pins the ETag format |
| `FileSystemPointer.matchesEtag` is true only for the exact quoted hash | Cache-validation correctness |
| `FileSystemPointer.modifiedAfter` boundary cases (equal / later / earlier instants) | Unit-level equivalent of specifications 6–8 |
| `FileSystemPointer.getMediaType` returns `Optional.empty()` when the type cannot be probed | The nullable branch of the constructor is untested |
| A real `FileStorage` adapter returns `Optional.empty()` for an unknown UUID | Required once the stub is replaced (migration step 4.1) |

## Verification Approach for This Transformation

This transformation is documentation-only and performs **no** build, compile, or test execution. Validation is therefore limited to static checks on the generated documentation — see [validation-criteria.md](validation-criteria.md). The specifications above describe what a *subsequent* code transformation (such as the recommended JDK upgrade) must verify.
