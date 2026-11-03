# Test Specifications

- [Back to README](../README.md)
- Siblings: [component-order.md](component-order.md) · [validation-criteria.md](validation-criteria.md)

> **No tests were executed during this analysis.** The transformation operates strictly on static code analysis and forbids running the codebase's tests. Everything below is derived from reading `src/test/groovy/com/nurkiewicz/download/DownloadControllerTest.groovy` and the supporting fixture classes.

## Table of Contents
1. [Existing Test Inventory](#existing-test-inventory)
2. [Test Infrastructure](#test-infrastructure)
3. [Existing Specification — Feature by Feature](#existing-specification--feature-by-feature)
4. [Coverage Matrix](#coverage-matrix)
5. [Critical Blocker](#critical-blocker)
6. [Required Additional Specifications](#required-additional-specifications)
7. [Migration Regression Suite](#migration-regression-suite)
8. [Silent Failure Watchlist](#silent-failure-watchlist)
9. [Test Preservation Obligations](#test-preservation-obligations)

## Existing Test Inventory

| Artifact | Path | Type | Count |
|---|---|---|---|
| `DownloadControllerSpec` | `src/test/groovy/com/nurkiewicz/download/DownloadControllerTest.groovy` | Spock specification (integration, MockMvc) | 12 feature methods |
| `FileStorageStub` | `src/test/java/com/nurkiewicz/download/FileStorageStub.java` | Test double (`@Profile("test")`) | — |
| `FileExamples` | `src/test/java/com/nurkiewicz/download/FileExamples.java` | Fixtures | 3 constants |
| `Sha512ShallowEtagHeaderFilter` | `src/test/java/org/springframework/web/filter/` | Unused class in the test tree | — |
| `download.txt` | `src/test/resources/download.txt` | Data fixture | 6 bytes (`foobar`) |

**Totals:** 12 test methods, 0 unit tests, 1 test double, 0 mocking-framework usages.

## Test Infrastructure

### Specification setup
```groovy
@WebAppConfiguration
@ContextConfiguration(classes = [MainApplication])
@ActiveProfiles("test")
class DownloadControllerSpec extends Specification {

    private MockMvc mockMvc

    private static final String TEXT_FILE =
        '/download/' + FileExamples.TXT_FILE_UUID + '/file.txt'

    @Autowired
    public void setWebApplicationContext(WebApplicationContext wac) {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build()
    }

    private static String toDateHeader(Instant lastModified) {
        ZonedDateTime dateTime = ZonedDateTime.ofInstant(lastModified, ZoneOffset.UTC)
        DateTimeFormatter.RFC_1123_DATE_TIME.format(dateTime)
    }
}
```

| Element | Purpose |
|---|---|
| `@ActiveProfiles("test")` | Activates `FileStorageStub` and deactivates `FileSystemStorage` (`@Profile("!test")`) |
| `@ContextConfiguration(classes = [MainApplication])` | Boots the real application context — this is an integration test, not a unit test |
| `MockMvcBuilders.webAppContextSetup` | Exercises the full Spring MVC stack: handler mapping, argument binding, message conversion |
| `TEXT_FILE` | Note the requested filename is `file.txt` while the fixture is `download.txt` — this **relies on** `{filename}` being unvalidated |
| `toDateHeader` | Formats RFC 1123 date headers for the conditional-request features |

### Fixtures
| Fixture | Definition | Note |
|---|---|---|
| `FileExamples.TXT_FILE_UUID` | `UUID.randomUUID()` | Regenerated on every JVM run — correct but not reproducible |
| `FileExamples.TXT_FILE` | `FileSystemPointer` over classpath `/download.txt` | Supplies the real ETag, size, and media type used in assertions |
| `FileExamples.NOT_FOUND_UUID` | `UUID.randomUUID()` | Never registered in the stub ⇒ drives the `404` path |
| `download.txt` | 6 bytes, content `foobar` | Content, length (6) and type (`text/plain`) are all asserted |

### Test double
`FileStorageStub.findFile(uuid)` returns `Optional.of(TXT_FILE)` for `TXT_FILE_UUID` and `Optional.empty()` otherwise. This is what makes the `404` path reachable at all — `FileSystemStorage` never returns `empty()`.

## Existing Specification — Feature by Feature

| # | Feature method | Request | Asserts | Guards |
|---|---|---|---|---|
| 1 | `should return bytes of existing file` | `GET /download/{uuid}/file.txt` | `200`; body == `"foobar"` | End-to-end content correctness through the throttled stream |
| 2 | `should return 404 and no content` | `GET /download/{NOT_FOUND_UUID}/file.txt` | `404`; body == empty byte array | `Optional.empty()` → `404` path in `DownloadController` |
| 3 | `should send file if ETag not present` | `GET` with no conditional headers | `200` | `cached()` returns `false` when `requestEtagOpt` is empty |
| 4 | `should send file if ETag present but not matching` | `GET` + `If-None-Match: "WHATEVER"` | `200` | `matchesEtag` correctly rejects a non-matching ETag |
| 5 | `should not send file if ETag matches content` | `GET` + `If-None-Match: <real etag>` | `304`; `ETag` header == the request ETag | ETag validator **and** the ETag echo on `304` |
| 6 | `should not return file if wasn't modified recently` | `GET` + `If-Modified-Since: <lastModified>` | `304` | `modifiedAfter` boundary: client time **equal** to server lastModified ⇒ cached |
| 7 | `should not return file if server has older version than the client` | `GET` + `If-Modified-Since: <lastModified + 60s>` | `304` | Client copy newer than server ⇒ cached |
| 8 | `should return file if was modified after last retrieval` | `GET` + `If-Modified-Since: <lastModified − 60s>` | `200` | File changed since the client's copy ⇒ serve. **The inverse of #6/#7** |
| 9 | `should return 200 OK on HEAD request, but without body` | `HEAD` | `200`; body == empty byte array | `resourceToReturn` returns `null` for HEAD |
| 10 | `should return 304 on HEAD request if we have cached version` | `HEAD` + matching `If-None-Match` | `304`; `ETag` echoed | HEAD and GET share identical cache semantics |
| 11 | `should return Content-length header` | `HEAD` | `200`; `Content-Length` == `TXT_FILE.size` (6) | HEAD reports the **true** body length despite sending no body |
| 12 | `should return content type in response` | `HEAD` | `200`; `Content-Type` == `text/plain` | `probeContentType` → Guava `MediaType` → Spring `MediaType` conversion chain |

### Assessment of what this suite achieves
Features 3–8 form a complete decision table over `ExistingFile.cached` — the single most logic-dense and most upgrade-fragile method in the codebase. Features 9–11 pin the HEAD contract, including the subtle requirement that `Content-Length` reflect the real file size on a bodyless response. Feature 12 pins the three-way `MediaType` conversion chain.

This is a genuinely well-targeted suite: it specifies exactly the behavior that a Spring version upgrade is most likely to break, and it does so at the HTTP boundary rather than against internal implementation detail — so it remains valid across refactors.

## Coverage Matrix

| Behavior | Covered | Feature(s) |
|---|---|---|
| `200` with correct body bytes | ✅ | 1 |
| `404` for unknown UUID, empty body | ✅ | 2 |
| Serve when no `If-None-Match` | ✅ | 3 |
| Serve when `If-None-Match` non-matching | ✅ | 4 |
| `304` when `If-None-Match` matches | ✅ | 5 |
| `ETag` echoed on `304` | ✅ | 5, 10 |
| `304` when client time == lastModified | ✅ | 6 |
| `304` when client time > lastModified | ✅ | 7 |
| `200` when client time < lastModified | ✅ | 8 |
| `HEAD` → `200`, empty body | ✅ | 9 |
| `HEAD` → `304` with cached ETag | ✅ | 10 |
| `Content-Length` correctness | ✅ | 11 |
| `Content-Type` correctness | ✅ | 12 |
| **`301` redirect from the short URL** | ❌ | — |
| **`Location` header correctness** | ❌ | — |
| `304` on the short URL (cache check preceding redirect) | ❌ | — |
| Throttling actually limits throughput | ❌ | — |
| `ThrottlingInputStream` in isolation | ❌ | — |
| Permits acquired per read variant | ❌ | — |
| `400` for a malformed UUID | ❌ | — |
| `405` for an unmapped verb (POST/PUT/DELETE) | ❌ | — |
| `Content-Type` omitted when undeterminable | ❌ | — |
| `FileSystemStorage` (the production adapter) | ❌ | Replaced by the `test` profile |
| Exception paths (I/O failure → `500`) | ❌ | — |
| Actuator endpoints | ❌ | — |

**Coverage summary.** 13 of 24 identified behaviors are specified. The notable omission is the **entire `301` redirect flow** — one of the system's two endpoints — along with throttling, which is the application's most distinctive feature.

## Critical Blocker

🟠 **`src/test/groovy` is not a registered test-source root, so none of the 12 feature methods compiles or executes in a default Maven build.**

The POM declares only `maven-compiler-plugin:3.0`. There is no `gmavenplus-plugin`, no `groovy-eclipse-compiler`, and no `build-helper-maven-plugin` adding the directory.

| Consequence | Impact |
|---|---|
| The behavioral safety net is inert | The migration has no verification mechanism |
| Regressions are invisible | Nothing fails when behavior changes |
| False assurance | A reader seeing 12 well-written feature methods reasonably assumes they run |

**This must be fixed before any other migration work begins** — it is Stage 0 in [component-order.md](component-order.md) and P0.1 in [../technical-debt/remediation-plan.md](../technical-debt/remediation-plan.md). Fixing it converts an unused asset into the single most valuable tool available for the upgrade.

Also note: once the Boot parent reaches 2.2+, `spring-boot-starter-test` adopts the JUnit 5 platform and the **Spock 1.x engine will no longer be discovered**. Spock must be upgraded to 2.x at that point or test execution silently stops again.

## Required Additional Specifications

Specifications to add, ordered by value for the migration. These close the coverage gaps above.

### Priority 1 — the unspecified endpoint
| Spec | Request | Expected |
|---|---|---|
| `should redirect short URL to canonical URL` | `GET /download/{uuid}` | `301`; `Location: /download/{uuid}/download.txt` |
| `should return 304 on short URL when client cache valid` | `GET /download/{uuid}` + matching `If-None-Match` | `304` (the cache check precedes the redirect, so no `301`) |
| `should redirect on HEAD of short URL` | `HEAD /download/{uuid}` | `301`; `Location` set; empty body |
| `should return 404 on short URL for unknown UUID` | `GET /download/{NOT_FOUND_UUID}` | `404` |

### Priority 2 — framework-binding guards
| Spec | Request | Expected | Why |
|---|---|---|---|
| `should reject malformed UUID` | `GET /download/not-a-uuid/file.txt` | `400` | Pins the UUID converter, which is the only input validation in the system |
| `should reject unmapped verbs` | `POST`, `PUT`, `DELETE` on both paths | `405` | Pins the read-only contract |
| `should ignore unparseable If-Modified-Since` | `GET` + `If-Modified-Since: garbage` | `200` (header does not bind) | Guards the silent-failure mode directly |

### Priority 3 — `ThrottlingInputStream` unit tests
The most unit-testable class in the codebase currently has zero tests, despite being the application's distinguishing feature.

| Spec | Setup | Expected |
|---|---|---|
| `read() acquires one permit` | Mock/fake `RateLimiter`, single-byte reads | One permit per `read()` |
| `read(byte[]) acquires array length permits` | 1024-byte array | `acquire(1024)` |
| `read(byte[],int,int) acquires len permits` | `len = 512` | `acquire(512)` |
| `delegates skip/available/mark/reset/markSupported/close` | Spy on the target stream | Each call forwarded unchanged |
| `propagates target IOException` | Target throws | Exception surfaces unwrapped |
| `reads full content correctly` | Known byte array | Content identical to the unthrottled stream |

### Priority 4 — `FileSystemPointer` unit tests
| Spec | Expected |
|---|---|
| ETag is the quoted SHA-512 of file contents | Matches an independently computed hash |
| ETag changes when content changes | Different content ⇒ different ETag |
| `getSize` matches the actual byte count | Consistent with `open()` output length |
| `getOriginalName` returns the file name | — |
| `matchesEtag` is exact and quote-sensitive | Unquoted hash does **not** match |
| `modifiedAfter` boundary behavior | `true` when client time ≥ lastModified; `false` when below |
| `getMediaType` empty for an undeterminable type | `Optional.empty()` ⇒ no `Content-Type` header |
| `open()` returns an independent stream each call | Two concurrent reads do not interfere |

### Priority 5 — error paths
| Spec | Expected |
|---|---|
| Storage I/O failure yields `500` without leaking internal paths | Generic error body (requires the Stage 4 `@ControllerAdvice`) |
| File deleted between `findFile` and `open()` | Clean failure, not a truncated response |

## Migration Regression Suite

The minimum set that must pass unchanged at **every** checkpoint in [component-order.md](component-order.md). These assert the externally observable contract and must not be relaxed to accommodate a framework change — if one of them fails, the framework change has altered behavior.

| ID | Assertion | Source |
|---|---|---|
| R1 | `GET` long URL, no conditional headers ⇒ `200` + exact body bytes | Existing feature 1 |
| R2 | `GET` unknown UUID ⇒ `404` + empty body | Existing feature 2 |
| R3 | `GET` + matching `If-None-Match` ⇒ `304` + ETag echoed | Existing feature 5 |
| R4 | `GET` + non-matching `If-None-Match` ⇒ `200` | Existing feature 4 |
| R5 | `GET` + `If-Modified-Since` == lastModified ⇒ `304` | Existing feature 6 |
| R6 | `GET` + `If-Modified-Since` > lastModified ⇒ `304` | Existing feature 7 |
| R7 | `GET` + `If-Modified-Since` < lastModified ⇒ `200` | Existing feature 8 |
| R8 | `HEAD` ⇒ `200` + empty body + true `Content-Length` | Existing features 9, 11 |
| R9 | `HEAD` + matching ETag ⇒ `304` | Existing feature 10 |
| R10 | `Content-Type` == `text/plain` for the text fixture | Existing feature 12 |
| R11 | `GET` short URL ⇒ `301` + correct `Location` | **To be added** (Priority 1) |
| R12 | ETag value for identical content is unchanged across the migration | **To be added** — guards against an accidental hash-algorithm change |
| R13 | Throttle rate remains 1 MB/s | **To be added** (Priority 3) |

R12 deserves emphasis: the ETag is a SHA-512 of file content, and silently changing the algorithm (for example while "optimizing" the per-request hash) would invalidate every client's cache without any test failing. Assert the literal expected hash for the fixture.

## Silent Failure Watchlist

Behaviors that would break **without** throwing an exception, logging an error, or failing a build. These are the reason the regression suite must actually run rather than merely exist.

| # | Silent failure | Mechanism | Guarded by |
|---|---|---|---|
| 1 | `If-Modified-Since` stops binding | Spring's `Optional<Date>` header conversion changes ⇒ `ifModifiedSinceOpt` is always empty ⇒ `cached()` returns `false` ⇒ the full file is served on every request. No error, no log | R5, R6, R7 |
| 2 | `If-None-Match` stops binding | Same mechanism for `Optional<String>` | R3, R4 |
| 3 | `HttpMethod` argument stops resolving | `method == HttpMethod.GET` becomes false ⇒ **GET requests return empty bodies**. Catastrophic and silent | R1, R8 |
| 4 | ETag algorithm changes | A Guava upgrade or a "performance optimization" alters the hash ⇒ every client cache is invalidated | R12 |
| 5 | `Content-Type` stops being set | `probeContentType` platform difference or a Guava `MediaType` API change ⇒ header silently omitted | R10 |
| 6 | Throttling stops applying | `RateLimiter` API change or a refactor that drops the decorator ⇒ unthrottled egress | R13 |
| 7 | `Content-Length` becomes wrong | `ResponseEntity.contentLength` behavior change ⇒ clients hang or truncate | R8 |
| 8 | Spock stops being discovered | Boot 2.2+ adopts the JUnit 5 platform ⇒ **zero tests run and the build still passes green** | Assert a non-zero test count in CI |

Watchlist item 8 is especially important: it is the same class of failure the project is already experiencing today via the unregistered source root. CI should assert that the expected number of tests actually ran, not merely that the build succeeded.

## Test Preservation Obligations

Per the code modification guardrails, the following must hold through every stage of any migration:

| Obligation | Detail |
|---|---|
| **No test file may be deleted** | `DownloadControllerTest.groovy`, `FileStorageStub.java`, `FileExamples.java` and `download.txt` must all be retained. Renaming the spec file to match its class name (`DownloadControllerSpec.groovy`) is permitted and recommended |
| **No feature method may be removed or disabled** | All 12 must remain. Do not add `@Ignore`/`@Disabled`, do not comment them out, and do not delete assertions |
| **Modification for compatibility is permitted** | Updating imports, the `MockMvc` builder API, or Spock 1.x → 2.x syntax is expected and fine |
| **Assertions may be updated, not weakened** | If a framework change legitimately alters an HTTP detail, update the expected value and **document why**. Do not replace a specific assertion with a looser one (e.g. do not change `status().isNotModified()` to "any 2xx/3xx") |
| **`Sha512ShallowEtagHeaderFilter` may be deleted** | This is **not** a test — it is an unused production-style class that happens to live in the test tree, is never registered, and targets a changed Spring signature. Deleting it does not reduce test coverage |
| **Coverage must not decrease** | The additional specifications above are additive; none replaces an existing one |
