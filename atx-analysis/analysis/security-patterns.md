# Security Patterns

[← Back to index](../README.md)

Code-level security analysis from static inspection. No scanning tool was run and no code was executed.

## Summary

The service implements **no authentication, no authorization, and no transport security**. Every endpoint is anonymously reachable. For a pure static-file download service this may be an accepted design position, but it is undocumented, and it is combined with an unsecured Spring Boot 1.x Actuator surface and a production storage stub that serves an internal configuration file.

## Existing Security Controls

| Control | Present? | Detail |
|---|---|---|
| Authentication | **No** | No Spring Security dependency, no filters, no credentials handling anywhere in the repository |
| Authorization | **No** | No role or ownership check — any caller who knows (or guesses) a UUID gets the file |
| Transport security (TLS) | **No** | No `server.ssl.*` configuration; no `application.properties`/`application.yml` exists at all |
| Input validation | **Partial** | `@PathVariable UUID uuid` relies on Spring's `String`→`UUID` converter, which rejects malformed values with `400`. This is the only validation in the system. |
| Output encoding | N/A | No HTML or templated output; bodies are raw file bytes |
| CSRF protection | **No** | Not strictly required: only `GET`/`HEAD` (safe, idempotent) methods are exposed |
| Rate limiting | **Partial** | `ThrottlingInputStream` caps **bandwidth per download** at 1 MB/s. It does **not** limit request rate, concurrent downloads, or per-client usage. A new `RateLimiter` is created per request, so N concurrent downloads consume up to N MB/s. |
| Audit logging | **Partial** | `FileSystemStorage`/`FileStorageStub` log the requested UUID at DEBUG; `ExistingFile` logs serve/redirect at DEBUG/TRACE. No caller identity is available to log. |
| Secrets management | N/A | **No secrets, credentials, API keys, tokens, or connection strings appear anywhere in the repository.** Verified across all source, `pom.xml`, and `logback.xml`. |
| Dependency vulnerability scanning | **No** | No OWASP dependency-check, Dependabot config, or equivalent |

## Positive Security Properties

These are genuine strengths and must be preserved through any transformation:

1. **Opaque identifiers.** Files are addressed by `UUID`, not by client-supplied paths or sequential IDs. Enumeration is impractical, and the `UUID` type conversion makes path-traversal-via-identifier structurally impossible at the controller boundary.
2. **The `{filename}` path segment is never used for file resolution.** `DownloadController.download` declares `/{uuid}/{filename}` but does not bind `filename` at all — it exists only so browsers derive a save-as name. This closes what would otherwise be the obvious traversal vector.
3. **No dynamic code execution.** No `eval`, no reflection-driven dispatch, no `Runtime.exec`, no `ProcessBuilder`, no script engine, no deserialization of untrusted input anywhere in the codebase.
4. **No SQL, LDAP, XPath, or command interpolation.** There is no data store and no shell interaction, so the entire injection class is absent.
5. **Strong ETags derived from content.** SHA-512 content hashes cannot leak filesystem metadata the way inode- or mtime-based tags can, and they are not predictable from the file name.
6. **Immutable domain objects and no shared mutable state.** Eliminates request-to-request state leakage.
7. **Safe-methods-only HTTP surface.** Only `GET` and `HEAD` are mapped; there is no upload, delete, or mutation endpoint to abuse.
8. **No secrets in source or configuration.**

## Findings

### High

**S-01 — Plaintext HTTP Maven repository (supply chain)**
`pom.xml` declares `<url>http://repo.spring.io/milestone/</url>`. Artifacts resolved over unencrypted HTTP can be tampered with in transit, allowing malicious code into the build. All declared dependencies are GA releases available from Maven Central, so this repository appears unnecessary. **Remove the `<repositories>` block entirely** (R-04). Never add a non-standard or non-TLS repository.

**S-02 — End-of-life framework stack receiving no security patches**
Spring Boot `1.2.3.RELEASE`, Spring Framework `4.1.6.RELEASE`, and the Java 8 target are all past end of life. Any vulnerability disclosed against these versions will never be fixed upstream. This is the single largest security exposure in the repository and the reason the recommended remediation path is a JDK and framework upgrade (R-01 through R-03).

**S-03 — Production stub serves an internal configuration file**
`FileSystemStorage.findFile` ignores its `uuid` argument and returns the application's own `logback.xml` for **every** request. In a deployed state this is an unauthenticated information-disclosure endpoint exposing internal configuration, and it makes the `404` path unreachable. Implement a real storage adapter (R-05).

### Medium

**S-04 — Unsecured Actuator endpoints**
`spring-boot-starter-actuator:1.2.3.RELEASE` is on the classpath with **no configuration file anywhere in the repository** to restrict it. Spring Boot 1.2.x serves its default endpoint set at the context root without authentication when Spring Security is absent — which it is. Endpoints such as `/env`, `/configprops`, `/beans`, `/mappings`, `/dump`, and `/trace` disclose environment variables, bean wiring, request mappings, and thread dumps. Either remove the actuator dependency or add explicit exposure restrictions plus authentication (R-12).

**S-05 — No authentication or authorization on download endpoints**
Possession of a UUID is the only access control. There is no per-user ownership check, no expiry, and no revocation. If this is intentional ("capability URL" model), it should be documented explicitly, and the UUIDs must be treated as secrets — which in turn requires TLS (S-06) and means they must not be logged. Note that `FileSystemStorage`/`FileStorageStub` currently **do** log the UUID at DEBUG level.

**S-06 — No transport security**
Without TLS, UUIDs (which function as bearer capabilities under S-05) and file content travel in cleartext. TLS should be terminated at the application or at an upstream load balancer, and that decision should be recorded in configuration or deployment manifests — neither of which exist in this repository.

**S-07 — Denial-of-service exposure from per-request content hashing**
`new FileSystemPointer(file)` computes a full SHA-512 over the file **on every request**, including `HEAD` requests and requests that end in `304` with no bytes transferred. An unauthenticated caller can therefore force unbounded disk I/O and CPU work at near-zero cost by repeatedly issuing `HEAD` requests against a large file. Cache the hash keyed by (path, size, mtime).

**S-08 — Thread exhaustion from blocking throttled transfers**
`RateLimiter.acquire(n)` blocks the servlet thread for the duration of the throttle delay. Because the throttle is per-request rather than global, and there is no cap on concurrent downloads, a modest number of concurrent large-file requests can occupy the entire container thread pool. Add a concurrency limit, or move to `StreamingResponseBody`/a non-blocking stack.

### Low

**S-09 — Unsafe `File` construction from a classpath URL**
`FileSystemStorage.java:27` and `FileExamples.java:15` use `new File(resource.getFile())`. This does not URL-decode the path (breaking on paths containing spaces or `%` sequences) and fails outright when the resource resides inside a JAR, which is the normal Spring Boot packaging. It will also throw `NullPointerException` if the resource is missing. Use `Resource`/`ClassPathResource` or `Path` APIs instead.

**S-10 — Exception messages may leak filesystem paths**
`FileSystemPointer` wraps `IOException` and `FileNotFoundException` in `IllegalArgumentException(e)`, preserving the original message — which typically contains the absolute file path. With no `@ControllerAdvice` or `@ExceptionHandler` present, the default Boot 1.x error response may surface this to the client. Add a global exception handler that returns a generic message and logs the detail server-side.

**S-11 — Unused dependencies enlarge the attack surface**
`commons-codec`, `spring-aop`, `spring-expression`, `spring-context-support`, and `cglib-nodep` have no direct source usage. Every unused artifact is a potential source of transitive vulnerabilities with no offsetting benefit. Remove them (R-06).

**S-12 — No dependency vulnerability gating**
There is no automated check that dependencies are free of known CVEs, and no CI configuration of any kind in the repository. Add dependency scanning to the build.

**S-13 — Unregistered filter in a third-party package**
`src/test/java/org/springframework/web/filter/Sha512ShallowEtagHeaderFilter.java` places project code inside Spring's own package to reach a `protected` method. This is package squatting: it is fragile against upstream changes and illegal on a module path. The class is also never registered or referenced. Move it into `com.nurkiewicz.download` or delete it (R-11).

## Cryptographic Review

| Usage | Algorithm | Location | Assessment |
|---|---|---|---|
| ETag generation | SHA-512 (Guava `Hashing.sha512()`) | `FileSystemPointer.java:22`, `Sha512ShallowEtagHeaderFilter.java:10` | **Appropriate.** SHA-512 is not deprecated. This is a non-security use (cache validation), and the choice is conservative for the purpose. |
| Random identifier generation | `UUID.randomUUID()` | `FileExamples.java:9,11` (test only) | Cryptographically strong (`SecureRandom`-backed). Test-scope only; production UUIDs are supplied externally. |

**No** MD5, SHA-1, DES, 3DES, RC4, ECB-mode encryption, hardcoded keys or IVs, custom cipher implementations, or weak PRNG (`java.util.Random`, `Math.random`) usage was found anywhere in the repository.

## OWASP Top 10 Mapping

| Category | Status | Notes |
|---|---|---|
| A01 Broken Access Control | **Exposed** | No authentication or authorization (S-05). Mitigated only by UUID opacity. |
| A02 Cryptographic Failures | **Partially exposed** | No TLS (S-06). Hashing choices themselves are sound. |
| A03 Injection | Not applicable | No interpreters, no SQL, no command execution, no template rendering |
| A04 Insecure Design | **Exposed** | Production stub serving internal config (S-03); unbounded hashing and concurrency (S-07, S-08) |
| A05 Security Misconfiguration | **Exposed** | Unsecured Actuator with no configuration file at all (S-04); verbose exception propagation (S-10) |
| A06 Vulnerable and Outdated Components | **Exposed — highest severity** | Entire stack EOL (S-02); unused dependencies (S-11); no scanning (S-12) |
| A07 Identification and Authentication Failures | **Exposed** | No authentication mechanism exists (S-05) |
| A08 Software and Data Integrity Failures | **Exposed** | Plaintext HTTP dependency resolution (S-01) |
| A09 Logging and Monitoring Failures | **Partially exposed** | Only DEBUG/TRACE application logging; no security event logging, no metrics, no alerting |
| A10 Server-Side Request Forgery | Not applicable | No outbound HTTP requests are made |

## Controls to Preserve During Transformation

Per the modification guardrails, the following existing properties must not be weakened by any upgrade or refactor:

1. `@PathVariable UUID` typing — the sole input validation; do not relax it to `String`.
2. The `{filename}` segment must remain unbound and unused for file resolution.
3. `ThrottlingInputStream` and its application in `ExistingFile.buildResource` must remain in place (the rate may be made configurable, but throttling must not be removed).
4. SHA-512 ETag generation must not be downgraded to a weaker digest.
5. The `GET`/`HEAD`-only method restriction on both mappings must be retained.
6. All 12 Spock specifications must be preserved — assertions may be adjusted for framework behavior changes, but no specification may be deleted or disabled.
7. No secrets may be introduced into source; any new configuration values must come from environment variables or externalized configuration.

## Priority Order for Security Remediation

| Priority | Items |
|---|---|
| High | S-01 (remove HTTP repository), S-02 (upgrade EOL stack), S-03 (replace production stub) |
| Medium | S-04 (restrict Actuator), S-05/S-06 (decide and document the access-control and TLS model), S-07 (cache content hashes), S-08 (bound concurrency) |
| Low | S-09 (safe resource resolution), S-10 (global exception handler), S-11 (remove unused dependencies), S-12 (add dependency scanning), S-13 (relocate or delete the squatted filter) |

See [../technical-debt/remediation-plan.md](../technical-debt/remediation-plan.md) and [../diagrams/architecture.md](../diagrams/architecture.md) for security boundaries.
