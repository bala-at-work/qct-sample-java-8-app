# Security Patterns

- [Back to README](../README.md)
- Siblings: [code-metrics.md](code-metrics.md) · [complexity-analysis.md](complexity-analysis.md) · [dependency-analysis.md](dependency-analysis.md) · [tech-debt.md](tech-debt.md)

> This is a static, code-level review of security-relevant implementation patterns. No scanning tool was run, no code was executed, and no penetration testing was performed. Findings are observations from reading the source, not a certified security assessment.

## Table of Contents
1. [Security Posture Summary](#security-posture-summary)
2. [Authentication and Authorization](#authentication-and-authorization)
3. [Exposure Surface](#exposure-surface)
4. [Input Validation](#input-validation)
5. [Path Traversal Review](#path-traversal-review)
6. [Cryptographic Patterns](#cryptographic-patterns)
7. [Injection Review](#injection-review)
8. [Information Disclosure](#information-disclosure)
9. [Transport Security](#transport-security)
10. [Resource Exhaustion](#resource-exhaustion)
11. [Secrets Management](#secrets-management)
12. [Supply Chain](#supply-chain)
13. [Security Controls Inventory](#security-controls-inventory)
14. [Findings Register](#findings-register)

## Security Posture Summary

| Dimension | State | Severity |
|---|---|---|
| Authentication | **None implemented** | 🟠 Medium |
| Authorization | **None implemented** | 🟠 Medium |
| Actuator exposure | Unauthenticated, default Boot 1.2.x endpoint set | 🟠 Medium |
| Framework patch status | EOL — no security fixes available | 🔴 High |
| Transport security | Not configured | 🟠 Medium |
| Input validation | Partial — UUID validated by the framework; `{filename}` unvalidated | 🟡 Low |
| Path traversal | **Not exploitable** as written | 🟡 Low |
| Cryptography | SHA-512 for ETags — appropriate, non-security use | ✅ Good |
| Injection (SQL/command/LDAP) | **No injection surface exists** | ✅ Good |
| Dynamic code execution | None — no `eval`, no `Runtime.exec`, no reflection on untrusted input | ✅ Good |
| Deserialization | No untrusted deserialization | ✅ Good |
| Secrets in source | **None found** | ✅ Good |
| Supply chain | Plain-HTTP repository; no CVE scanning | 🟠 Medium |
| Resource exhaustion | Thread-per-download blocking; no global egress limit | 🟡 Low |

**Headline.** The application's *code* contains no classic injection or deserialization vulnerabilities — there is simply no attack surface of those kinds, because there is no database, no command execution, no templating and no untrusted deserialization. The security risk is concentrated in two places: an **EOL framework stack that cannot be patched**, and the **complete absence of an access-control layer** on endpoints that serve file content.

## Authentication and Authorization

**Both are absent.** Confirmed by exhaustive search:

| Searched for | Found |
|---|---|
| `spring-boot-starter-security` / `spring-security-*` dependency | — |
| `@EnableWebSecurity`, `WebSecurityConfigurerAdapter`, `SecurityFilterChain` | — |
| `@PreAuthorize`, `@Secured`, `@RolesAllowed` | — |
| Servlet `Filter` or `HandlerInterceptor` registrations | — |
| JWT, OAuth2, SAML, API-key handling | — |
| `Principal` / `Authentication` parameters on handlers | — |
| `web.xml` security constraints | — (no `web.xml` at all) |

### Consequence
`GET /download/{uuid}` and `GET /download/{uuid}/{filename}` are **fully anonymous**. Any client that knows or guesses a UUID receives the file.

### Assessment of the UUID-as-capability model
The design relies on **capability URLs**: possession of the UUID is the only requirement for access. This is a legitimate pattern (used by many file-sharing services) but it carries specific properties that must be acknowledged rather than assumed:

| Property | Reality |
|---|---|
| Unguessability | A random v4 UUID has ~122 bits of entropy — brute force is infeasible |
| Revocability | **None.** There is no mechanism to invalidate a UUID once shared |
| Expiry | **None.** No TTL, no signed-URL expiration |
| Audit | Minimal — a `log.debug("Downloading {}", uuid)` line only |
| Leak resistance | **Weak.** URLs appear in browser history, `Referer` headers, proxy logs, chat transcripts and the server access log |
| Per-user scoping | None — the same UUID grants access to everyone |

Critically, the codebase contains **no UUID generation for real files** (only `UUID.randomUUID()` in test fixtures) and no persistence of UUID→file mappings. A real implementation must guarantee cryptographically random UUIDs; a sequential or predictable scheme would reduce this from a capability model to no protection at all.

**Recommendation.** Decide and document explicitly whether `/download/**` content is public. If it is not, add authentication (and consider pre-signed, expiring URLs rather than permanent capability URLs). Tracked as P2.8 in [../technical-debt/remediation-plan.md](../technical-debt/remediation-plan.md). Note that no existing security control is being removed by this analysis — there are none to preserve.

## Exposure Surface

### Application endpoints
| Endpoint | Auth | Exposure |
|---|---|---|
| `GET\|HEAD /download/{uuid}` | none | Public |
| `GET\|HEAD /download/{uuid}/{filename}` | none | Public |

### Actuator endpoints — 🟠 Medium severity
`spring-boot-starter-actuator` 1.2.3.RELEASE is on the classpath with **zero configuration**: there is no `application.properties`, no `management.*` setting, and no Spring Security. In Boot 1.2.x, sensitive endpoints were exposed at the root context **without authentication** by default.

| Endpoint | Information disclosed | Sensitivity |
|---|---|---|
| `/env` | Environment variables, system properties, resolved property sources | 🔴 **High** — a common path to leaked credentials in real deployments |
| `/configprops` | All resolved configuration properties | 🔴 **High** |
| `/beans` | Full application bean graph and class names | 🟠 Medium — aids reconnaissance |
| `/mappings` | Every request mapping, revealing the full URL surface | 🟠 Medium |
| `/trace` | Recent HTTP exchanges including headers | 🟠 Medium — can expose other users' request headers |
| `/dump` | Full thread dump | 🟠 Medium |
| `/autoconfig` | Auto-configuration decisions | 🟡 Low |
| `/metrics` | JVM and request metrics | 🟡 Low |
| `/health`, `/info` | Status | 🟡 Low |
| `/shutdown` | Would terminate the application | Disabled by default — would be 🔴 High if enabled |

No application code references the actuator; it contributes nothing to the download capability. **As configured it is pure attack surface.** This is the highest-value quick win in the repository: either remove the dependency or configure `management.endpoints.web.exposure.include` explicitly and put the management port behind authentication. Boot 2.x+ restricts these by default, so the framework upgrade partially remediates this on its own — but the intent should be made explicit rather than inherited.

## Input Validation

| Input | Validation | Mechanism | Adequacy |
|---|---|---|---|
| `{uuid}` path segment | ✅ Strict | Spring's `String`→`UUID` converter; malformed input yields `400 Bad Request` before any application code runs | Adequate — the type system is the validator |
| `{filename}` path segment | ❌ **None** | Mapped in the URL template but never bound to a parameter or compared against `getOriginalName()` | See below |
| `If-None-Match` header | ⚠️ Format-only | Bound as `Optional<String>`; passed to `matchesEtag`, which performs exact string equality | Adequate — equality comparison cannot be subverted |
| `If-Modified-Since` header | ✅ Type-checked | Spring converts to `Optional<Date>`; unparseable values do not bind | Adequate |
| HTTP method | ✅ Restricted | `method = {GET, HEAD}` — other verbs yield `405` | Adequate |
| Request body | N/A | No endpoint accepts a body | — |

### The unvalidated `{filename}` — 🟡 Low severity
`DownloadController.download` maps `/{uuid}/{filename}` but never binds `filename`. Any value serves the same content for a given UUID.

**What this is not.** It is **not** a path-traversal vulnerability — see the next section.

**What it actually is:**
| Concern | Detail |
|---|---|
| Non-canonical URLs | The same content is reachable at unbounded distinct URLs (`/download/{uuid}/a.txt`, `/download/{uuid}/b.exe`, …) |
| Cache pollution | A CDN or reverse proxy would cache each variant separately, and an attacker could inflate cache storage cheaply |
| Content-type confusion | A link can advertise `.pdf` or `.exe` while the server serves the real content with its own `Content-Type`. Combined with the **missing `Content-Disposition` header**, a victim may be misled about what they are downloading |
| Relied upon by the tests | The Spock spec requests `/download/{uuid}/file.txt` while the fixture is actually `download.txt` — so simply adding strict validation would fail the existing specification |

**Recommendation.** Either validate `filename` against `getOriginalName()` and redirect (`301`) when it differs, or document explicitly that it is decorative. If validation is added, update the specification accordingly. Also add `Content-Disposition: attachment; filename="..."` so the client's saved filename comes from the server rather than the URL.

## Path Traversal Review

This deserves explicit treatment because a download server is the canonical path-traversal target.

**Finding: not exploitable as written.** Reasoning:

1. The only user-controlled input reaching storage is `{uuid}`, and it is constrained to a well-formed `java.util.UUID` by Spring's converter. A UUID cannot contain `/`, `.`, `\` or `..`.
2. `{filename}` is user-controlled but **never reaches the filesystem** — it is not bound to a parameter and is not passed to `FileStorage`, `FilePointer`, or any `File` constructor.
3. `FileSystemStorage.findFile` ignores its `uuid` argument entirely and resolves a hard-coded classpath resource:
   ```java
   final URL resource = getClass().getResource("/logback.xml");
   final File file = new File(resource.getFile());
   ```
   No user input participates in path construction anywhere in the main source tree.

**However — this safety is incidental, not designed.** It holds only because `FileSystemStorage` is a stub. The `FileStorage` contract is `findFile(UUID) → Optional<FilePointer>`, and a real implementation must map a UUID to a path. **Path traversal becomes a first-class risk the moment that implementation is written**, particularly if a future design passes `filename` through to the storage layer.

**Mandatory controls for any real `FileStorage` implementation:**
- Resolve the UUID to a path via a **metadata store lookup**, never by string-concatenating user input into a path.
- Canonicalize the resolved path (`File.getCanonicalPath()` / `Path.normalize()`) and assert it remains within the configured storage root before opening it.
- Never incorporate the `{filename}` path segment into any filesystem or object-key operation.
- For object storage, construct keys from the looked-up metadata, not from request input.

A secondary note on the existing stub: `new File(resource.getFile())` is a known-fragile idiom — `URL.getFile()` returns a URL-encoded path, so it breaks for paths containing spaces or non-ASCII characters, and it fails outright when the resource is inside a JAR. This is a robustness defect rather than a security one, but a real implementation should use `Paths.get(resource.toURI())` or stream the resource directly.

## Cryptographic Patterns

| Usage | Algorithm | Purpose | Assessment |
|---|---|---|---|
| ETag generation | **SHA-512** via Guava `Hashing.sha512()` over full file contents (`FileSystemPointer`) | HTTP cache validator | ✅ **Appropriate.** A strong, modern hash used for a non-security purpose. Notably stronger than Spring's default MD5 shallow ETag |
| Unused filter | SHA-512 via `Hashing.sha512().hashBytes(bytes)` (`Sha512ShallowEtagHeaderFilter`) | Would replace Spring's MD5 ETag | ✅ Intent is good — but the class is dead code and should be deleted |
| Identifier generation | `UUID.randomUUID()` — cryptographically strong (backed by `SecureRandom`) | Test fixtures only | ✅ Appropriate where used |
| Password hashing | — | — | N/A — no credentials exist |
| Encryption at rest / in transit | — | — | None implemented |

**No deprecated or weak algorithms were found.** There is no MD5, SHA-1, DES, RC4, ECB-mode cipher, `Math.random()` for security purposes, or hard-coded IV anywhere in the codebase. This is a genuinely clean result.

**One caveat.** `matchesEtag` uses `getEtag().equals(requestEtag)` — a non-constant-time comparison. This is **correct here**: an ETag is a public cache validator, not a secret, so timing-attack resistance is irrelevant. It is recorded only to pre-empt a false-positive finding from an automated scanner.

**Performance note with a security dimension.** Hashing the full file on every request (including for `304` and `301` responses) is both a performance hotspot and a cheap amplification vector: an unauthenticated attacker can force repeated full-file SHA-512 computation with trivially small requests. See [complexity-analysis.md](complexity-analysis.md#performance-hotspots) H1 and [Resource Exhaustion](#resource-exhaustion) below.

## Injection Review

| Injection class | Surface present? | Finding |
|---|---|---|
| SQL / JPQL / HQL | **No** | No database, no JDBC, no ORM, no SQL string anywhere |
| NoSQL | **No** | No NoSQL client |
| OS command | **No** | No `Runtime.exec`, no `ProcessBuilder`, no `ProcessHandle` |
| Code execution | **No** | No `eval`, no scripting engine, no `ScriptEngineManager`, no dynamic class loading of untrusted input |
| LDAP | **No** | No directory access |
| XPath / XQuery | **No** | No XML parsing of untrusted input |
| XXE | **No** | The only XML files (`pom.xml`, `logback.xml`) are build/config artifacts parsed by trusted tooling, not request input |
| Log injection | ⚠️ **Marginal** | `log.debug("Downloading {}", uuid)` logs a UUID, but the value is already validated as a well-formed UUID and therefore cannot contain CR/LF. `log.trace("Redirecting {} '{}'", method, filePointer)` logs a `FilePointer.toString()` containing a server-side filename, not user input |
| Header injection / response splitting | ⚠️ **Theoretical** | `redirectDownload` builds `new URI("/download/" + uuid + "/" + filePointer.getOriginalName())`. The `uuid` is type-validated; `getOriginalName()` is **server-controlled** (a filesystem name), not request input. A malicious filename on disk could produce a malformed URI, but this requires prior filesystem compromise. `URISyntaxException` is caught and rewrapped, yielding a `500` rather than a split response |
| Template injection | **No** | No template engine |
| Deserialization | **No** | No `ObjectInputStream`, no Java serialization of request data, no `readObject`. Jackson is on the classpath transitively but no endpoint accepts or deserializes a request body |
| Open redirect | **No** | The `301` `Location` is built from a validated UUID and a server-side filename — a client cannot influence the redirect target |
| CSRF | **N/A** | All endpoints are read-only `GET`/`HEAD`; no state-changing operation exists |
| SSRF | **No** | The application makes no outbound network calls of any kind |

**Conclusion.** There is no injection vulnerability in this codebase, and more importantly there is no *surface* for one. This is a structural consequence of the application's narrow scope (read-only, no datastore, no outbound calls) rather than of defensive coding, but it is a genuine and valuable property that any modernization must preserve.

## Information Disclosure

| Vector | Finding | Severity |
|---|---|---|
| **Actuator `/env` and `/configprops`** | Unauthenticated environment and configuration disclosure | 🔴 High |
| **Actuator `/beans`, `/mappings`, `/autoconfig`** | Application structure and full URL surface disclosure, aiding reconnaissance | 🟠 Medium |
| **Actuator `/trace`** | Recent HTTP exchanges including headers — potentially other clients' data | 🟠 Medium |
| **Default error pages** | No `@ControllerAdvice` or custom error handling. `IllegalArgumentException` from `FileSystemPointer` (I/O failure) or `redirectDownload` propagates to Boot's default handler, which in Boot 1.2.x can include exception type and message in the response body | 🟠 Medium |
| **Verbose logging configuration** | `logback.xml` sets root level `DEBUG` and `com.nurkiewicz` to `ALL`. In production this writes high-volume logs including every file served. Not switchable without a config file, since none exists | 🟡 Low |
| **Server-side filename in `Location`** | The `301` reveals the real on-disk filename. Intentional and part of the design | 🟡 Low — by design |
| **`FilePointer.toString()` in logs** | Logs the absolute `target` path, size, SHA-512 hash and media type at `trace`/`debug` level — internal filesystem layout in log output | 🟡 Low |
| **Stack traces** | Exception rewrapping (`IOException` → `IllegalArgumentException`) preserves the cause, so a `500` response from Boot's default handler may surface internal paths | 🟡 Low |

**Recommendations.** Secure or remove the actuator (highest value). Add a `@ControllerAdvice` that maps storage I/O failures to a generic `500` without exception detail. Externalize log levels to configuration and set production defaults to `INFO` or above.

## Transport Security

| Aspect | Finding | Severity |
|---|---|---|
| HTTPS / TLS configuration | **None.** No `server.ssl.*` properties — there is no configuration file at all | 🟠 Medium |
| HSTS (`Strict-Transport-Security`) | Not set | 🟡 Low |
| `X-Content-Type-Options: nosniff` | Not set — notable since `Content-Type` is probe-derived and may be absent entirely | 🟡 Low |
| `Content-Security-Policy` | Not set | 🟡 Low (no HTML served) |
| `X-Frame-Options` | Not set | 🟡 Low (no HTML served) |
| `Content-Disposition` | **Not set** — browsers decide inline vs download from `Content-Type` alone. Combined with the unvalidated `{filename}`, this enables download-filename confusion | 🟡 Low |
| `Cache-Control` / `Expires` | Not set. Only `ETag` and `Last-Modified` are provided, so intermediaries apply heuristic caching to potentially non-public content | 🟡 Low |
| CORS | Not configured — same-origin only by default, which is appropriate | ✅ |

**Assessment.** The application serves plain HTTP as built. In practice a Boot 1.x service like this would sit behind a TLS-terminating load balancer or reverse proxy, which is an acceptable architecture — but nothing in the repository documents or enforces that assumption. Without TLS at some layer, file content and capability URLs travel in cleartext, which undermines the entire UUID-as-capability model since the capability itself is exposed on the wire.

## Resource Exhaustion

| Vector | Mechanism | Severity |
|---|---|---|
| **Thread-pool exhaustion** | `RateLimiter.acquire` blocks the Tomcat worker thread for the whole download. At 1 MB/s, Tomcat's ~200 default threads are consumed by ~200 concurrent downloads, after which **all** requests queue — including `/health` | 🟡 Low |
| **Unbounded aggregate egress** | A fresh `RateLimiter.create(ONE_MB)` is created per response, so the 1 MB/s limit is **per download, not global**. N concurrent downloads consume up to N MB/s. The throttle protects per-client fairness, not server bandwidth | 🟡 Low |
| **CPU amplification via ETag hashing** | Every request — including those answered with `304` or `301` — triggers a full-file SHA-512 computation in `FileSystemPointer`'s constructor. A small, cheap, unauthenticated request forces work proportional to file size. With a real storage backend serving large files, this is a meaningful amplification factor | 🟠 Medium (once real storage exists) |
| **Double file read** | A `200` response reads the file once to hash it and again to stream it, doubling I/O | 🟡 Low |
| **No rate limiting per client** | No request-count limiting, no per-IP throttle, no connection limit. Nothing prevents one client from opening many concurrent downloads and consuming the whole thread pool | 🟠 Medium |
| **File-descriptor leak potential** | `FilePointer.open()` transfers ownership of the stream to the caller; Spring's `InputStreamResource` handling closes it on the normal path, but an exception thrown between `open()` and response commit could leak a descriptor | 🟡 Low |

**Recommendation.** The CPU-amplification and per-client limiting items are the two that matter, and both become materially more serious when real storage is implemented. Make the ETag lazy/cached (P3.5) and add per-client rate limiting at the edge before serving real files.

## Secrets Management

| Check | Result |
|---|---|
| Hard-coded passwords | **None found** |
| API keys or tokens | **None found** |
| Database credentials | **None found** (no database exists) |
| Private keys, certificates, keystores | **None found** |
| Cloud credentials (AWS access keys, etc.) | **None found** |
| Connection strings | **None found** |
| Secrets in `logback.xml` / `pom.xml` | **None found** |
| `.env` or credentials files | **None present** |
| Secrets in test fixtures | None — `download.txt` contains the literal string `foobar` |
| Externalized secret management | N/A — the application requires no secrets |

✅ **Clean result.** This application legitimately needs no secrets, and none are present. If a real `FileStorage` implementation is added (for example an S3-backed adapter), credentials must be supplied through environment variables, an instance role, or a secret manager — **never** hard-coded into source or committed configuration.

## Security Controls Inventory

Recorded so that modernization does not inadvertently remove a control. Per the modification guardrails, every existing security control must be preserved through any change.

| Control | Implemented by | Must be preserved |
|---|---|---|
| UUID type validation (rejects malformed identifiers with `400`) | Spring's `String`→`UUID` converter via `@PathVariable UUID` | ✅ Yes — keep the parameter typed as `UUID`, never as `String` |
| HTTP method restriction (`GET`/`HEAD` only) | `@RequestMapping(method = {GET, HEAD})` | ✅ Yes — do not widen to additional verbs |
| Read-only API (no upload, mutation, or delete endpoint) | Absence of any write handler | ✅ Yes |
| No user input in filesystem path construction | `FileSystemStorage` uses a hard-coded classpath resource | ✅ Yes — and any real implementation must resolve paths via metadata lookup plus canonicalization |
| Strong hash for ETags (SHA-512, not MD5) | `FileSystemPointer` via Guava `Hashing.sha512()` | ✅ Yes — do not downgrade the algorithm for performance |
| Bandwidth throttling (1 MB/s per download) | `ThrottlingInputStream` + Guava `RateLimiter` | ✅ Yes — partial mitigation against bandwidth abuse |
| `404` rather than an error leak for unknown identifiers | `DownloadController.orElseGet(() -> new ResponseEntity<>(NOT_FOUND))` | ✅ Yes — does not distinguish "no such UUID" from "not permitted" |
| Cryptographically strong identifier generation | `UUID.randomUUID()` (test fixtures; required of any real implementation) | ✅ Yes |
| Same-origin default (no CORS relaxation) | No CORS configuration | ✅ Yes — do not add permissive CORS |
| No dynamic code execution | Absence of `eval`/`exec`/scripting | ✅ Yes |
| No untrusted deserialization | No request-body endpoints | ✅ Yes |

**Note.** This is a short list because the application implements few controls — not because controls were removed. The guardrail obligation is to keep every item above intact; several of them (notably SHA-512 and UUID typing) are easy to weaken accidentally while "optimizing" or "simplifying."

## Findings Register

Severity uses **High / Medium / Low** only. Ordered by the fixed prioritization: EOL runtimes and frameworks first, then dependencies, then code-level issues.

| # | Finding | Severity | Location | Remediation |
|---|---|---|---|---|
| S1 | **EOL framework stack cannot receive security patches** — Spring Boot 1.2.3, Spring 4.1.6, Java 8. Any newly disclosed Spring MVC vulnerability has no fix for these versions | 🔴 **High** | `pom.xml` | Staged upgrade — P1.1–P1.3 |
| S2 | **EOL transitive libraries with CVE history on the production classpath** — `jackson-databind` 2.4.x, `snakeyaml`, `hibernate-validator` 5.1.x, all unused by application code and unpatchable while the parent is EOL | 🔴 **High** | Boot 1.2.3 transitives | Upgrade the Boot parent — P1.2 |
| S3 | **Actuator exposed without authentication** — Boot 1.2.x defaults expose `/env`, `/configprops`, `/trace`, `/beans` | 🟠 Medium | `spring-boot-starter-actuator`, no config | Configure `management.endpoints.web.exposure.include`; add Spring Security or remove the dependency — P2.7 |
| S4 | **No authentication or authorization on file endpoints** — access depends solely on knowing a UUID, with no revocation, expiry, or audit | 🟠 Medium | `DownloadController` | Decide and document the access model; add authentication or expiring signed URLs — P2.8 |
| S5 | **Plain-HTTP artifact repository** — `http://repo.spring.io/milestone/` permits in-transit artifact substitution | 🟠 Medium | `pom.xml` | Delete the repository block; all dependencies are Maven Central GA releases — P2.6 |
| S6 | **No automated dependency vulnerability scanning** — and no CI in which to run it | 🟠 Medium | repository-wide | Add CI with CVE scanning — P2.10 / P3.10 |
| S7 | **Test-only libraries on the production classpath** — `groovy-all` and `cglib-nodep` at `compile` scope widen the runtime attack surface | 🟠 Medium | `pom.xml` | Re-scope to `test` or remove — P2.4 / P2.5 |
| S8 | **No per-client rate limiting** — one client can consume the entire Tomcat thread pool with concurrent downloads | 🟠 Medium | no control present | Add edge rate limiting before serving real files |
| S9 | **CPU amplification via per-request full-file SHA-512** — cheap requests force work proportional to file size, even for `304`/`301` | 🟠 Medium (with real storage) | `FileSystemPointer` constructor | Make the ETag lazy and cached — P3.5 |
| S10 | **No transport security configuration** — no TLS settings; capability URLs and content travel in cleartext unless an external terminator is present | 🟠 Medium | no config file exists | Terminate TLS at a load balancer and document it, or configure `server.ssl.*` |
| S11 | **Default error handling may disclose exception detail** — no `@ControllerAdvice`; rewrapped `IllegalArgumentException` reaches Boot's default handler | 🟠 Medium | `ExistingFile`, `FileSystemPointer` | Add a `@ControllerAdvice` returning a generic `500` |
| S12 | **Unvalidated `{filename}` path segment** — enables non-canonical URLs, cache pollution, and download-name confusion | 🟡 Low | `DownloadController.download` | Validate against `getOriginalName()` or document as decorative; add `Content-Disposition` |
| S13 | **Verbose production logging** — root `DEBUG`, `com.nurkiewicz` at `ALL`; logs absolute paths and hashes | 🟡 Low | `logback.xml` | Externalize levels; default to `INFO` in production — P3.4 |
| S14 | **Missing security response headers** — no `nosniff`, HSTS, `Content-Disposition`, or `Cache-Control` | 🟡 Low | `ExistingFile.response` | Add headers appropriate to the content model |
| S15 | **Fragile resource resolution** — `new File(resource.getFile())` breaks on encoded paths and inside JARs | 🟡 Low | `FileSystemStorage` | Use `Paths.get(resource.toURI())` or stream the resource |
| S16 | **Path traversal risk is latent, not present** — safe only because the storage adapter is a stub; becomes first-class once real storage is implemented | 🟡 Low now, **High if implemented carelessly** | future `FileStorage` implementation | Mandatory canonicalization and metadata-based path resolution — see [Path Traversal Review](#path-traversal-review) |
| S17 | **Dead security-relevant code** — `Sha512ShallowEtagHeaderFilter` in a Spring package, never registered, targeting a changed signature | 🟡 Low | `src/test/java/org/springframework/web/filter/` | Delete — P3.2 |

### Positive findings (no action required)
| Finding | Basis |
|---|---|
| No SQL, command, LDAP, XPath, template, or code injection surface | No datastore, no process execution, no templating, no scripting engine |
| No untrusted deserialization | No request-body endpoint; no `ObjectInputStream` |
| No SSRF surface | No outbound HTTP client of any kind |
| No open redirect | `Location` is built from a validated UUID and a server-controlled filename |
| No hard-coded secrets | Exhaustive search found none; the application needs none |
| No weak or deprecated cryptography | SHA-512 only; no MD5, SHA-1, DES, RC4, or ECB |
| No dynamic code execution | No `eval`, `Runtime.exec`, `ProcessBuilder`, or untrusted reflection |
| Strong input typing at the boundary | `@PathVariable UUID` makes malformed identifiers unrepresentable |
| CSRF not applicable | Read-only `GET`/`HEAD` API with no state mutation |
| No permissive CORS | No CORS configuration present |
