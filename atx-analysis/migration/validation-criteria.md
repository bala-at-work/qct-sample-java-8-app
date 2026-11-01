# Validation Criteria

[← Back to index](../README.md) · Related: [component-order.md](component-order.md) · [test-specifications.md](test-specifications.md)

Criteria for judging a migration of this project successful. These apply to the *remediation work*, not to this analysis transformation (which performs no build, execution, or test run).

## 1. Build Validation

| # | Criterion | How to verify |
|---|---|---|
| B1 | The project compiles cleanly at the new language level with no errors | `./mvnw clean compile` |
| B2 | `src/test/groovy` is actually compiled (a Groovy compiler plugin is configured) | `./mvnw test-compile`, then confirm `DownloadControllerSpec` appears in the test-classes output |
| B3 | Dependency resolution succeeds with no `http://` repositories declared | inspect `pom.xml`; `./mvnw dependency:resolve` |
| B4 | No dependency version conflicts or duplicate declarations remain | `./mvnw dependency:tree -Dverbose`; `pom.xml` contains no duplicated coordinates |
| B5 | The Spring Boot fat JAR is produced and starts | `./mvnw package`, then launch the JAR |
| B6 | No deprecation warnings for APIs removed in the target platform | review compiler output |

## 2. Functional Validation — the behavioural contract must be preserved

All 12 existing specifications must pass unmodified in intent. Contract IDs refer to [test-specifications.md](test-specifications.md).

| # | Criterion |
|---|---|
| F1 | C1 — `GET` with a known UUID returns `200` and the exact file bytes |
| F2 | C2 — unknown UUID returns `404` with an empty body |
| F3 | C3 — non-matching `If-None-Match` still returns the body with `200` |
| F4 | C4 — matching `If-None-Match` returns `304` and echoes the `ETag` (both `GET` and `HEAD`) |
| F5 | C5 — `If-Modified-Since` at or after `Last-Modified` returns `304` |
| F6 | C6 — `If-Modified-Since` before `Last-Modified` returns `200` |
| F7 | C7/C8 — `HEAD` returns `200`, full headers, empty body, correct `Content-Length` |
| F8 | C9 — `Content-Type` reflects the probed media type (`text/plain` for the fixture) |
| F9 | C10 — the ETag is a quoted SHA-512 digest of the content, byte-for-byte identical in format to the pre-migration value |
| F10 | C11 — RFC 1123 `If-Modified-Since` values parse correctly |
| F11 | C12 — the `test` profile selects `FileStorageStub`; the production adapter is not loaded |
| F12 | `GET /download/{uuid}` still returns `301` with `Location: /download/{uuid}/{originalName}` (verify manually or via new spec N1 — currently untested) |
| F13 | `GET` bodies remain throttled at 1 MB/s per request |

**Zero regressions**: the test suite must have the same or greater number of passing feature methods than before the migration. No test may be deleted, `@Ignore`d, or weakened.

## 3. API Compatibility Validation

| # | Criterion |
|---|---|
| A1 | URL templates unchanged: `/download/{uuid}` and `/download/{uuid}/{filename}`, both accepting `GET` and `HEAD` |
| A2 | Public type names unchanged: `DownloadController`, `ExistingFile`, `FileStorage`, `FileSystemStorage`, `FilePointer`, `FileSystemPointer`, `ThrottlingInputStream`, `MainApplication` |
| A3 | Public method names and arities unchanged: `redirect`, `download`, `handle`, `findFile`, and the eight `FilePointer` methods |
| A4 | Every source file retains its primary type declaration |
| A5 | No duplicate method signatures introduced within any scope |
| A6 | Status codes, header names, and header value formats unchanged |

## 4. Structural Validation

| # | Criterion |
|---|---|
| S1 | Package structure preserved (`com.nurkiewicz.download` remains the component-scan root) |
| S2 | Ports-and-adapters boundary intact: `DownloadController` and `ExistingFile` still depend only on `FileStorage`/`FilePointer`, never on a concrete adapter |
| S3 | No dependency cycles introduced |
| S4 | Immutability preserved: fields in `ExistingFile` and `FileSystemPointer` remain `final` |
| S5 | `logback.xml` still configures the `com.nurkiewicz` logger and the console appender |

## 5. Security Validation

| # | Criterion |
|---|---|
| X1 | No repository declared over plaintext `http://` |
| X2 | No new dependency with known vulnerabilities; chosen versions verified against a CVE database at their exact resolved coordinates |
| X3 | No dependency downgraded below its pre-migration version |
| X4 | No secrets, credentials, API keys, or tokens introduced into source or configuration |
| X5 | Existing security controls preserved: strict `UUID` path binding; the `{filename}` segment still unused for file resolution; request body still never read |
| X6 | SHA-512 retained for ETag generation (no downgrade to MD5/SHA-1) |
| X7 | Actuator exposure explicitly decided — either the starter is removed or endpoint exposure is restricted and authenticated |
| X8 | No `eval`/`exec`/`Runtime.exec`/untrusted deserialization introduced |

## 6. Technical Debt Reduction Validation

| # | Criterion |
|---|---|
| D1 | Language level is a supported LTS; no EOL runtime target remains |
| D2 | Spring Boot and Spring Framework are on a supported generation |
| D3 | The unused `<spring.version>` property and the eight explicit `spring-*` pins are gone (versions managed by the parent BOM) |
| D4 | `commons-codec` removed (it was never imported) |
| D5 | `groovy-all` and `cglib-nodep` are at `test` scope |
| D6 | The duplicate `spring-test` declaration and the `spring-boot-starter-test` → `spring-test` exclusion are removed |
| D7 | `maven-compiler-plugin` is current and uses `<release>` |
| D8 | The dead `new Integer("1234")` statement is removed |
| D9 | `@Override` added to `FileStorageStub.findFile` |

## 7. Documentation Validation

| # | Criterion |
|---|---|
| Q1 | `ATXDocumentation/` is refreshed to reflect the new versions and any changed call sites |
| Q2 | Any behavioural change (there should be none) is explicitly recorded |
| Q3 | All copyright/license headers and comment blocks of 5+ lines are preserved unchanged |

## 8. Non-Goals (explicitly out of scope for a version migration)

- Replacing the `FileSystemStorage` stub with a real backend (product change — R7).
- Adding authentication/authorization (product/security decision — S1).
- ETag caching, async streaming, `Range` support (performance work — R6, R11).
- Any public API rename.

## Sign-Off Checklist

```
[ ] B1–B6  build and packaging
[ ] F1–F13 functional contract, zero regressions
[ ] A1–A6  API compatibility
[ ] S1–S5  structure preserved
[ ] X1–X8  security posture equal or improved
[ ] D1–D9  targeted debt actually retired
[ ] Q1–Q3  documentation current, legal headers intact
```
