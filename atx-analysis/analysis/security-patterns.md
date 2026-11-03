# Security Patterns

[← Back to index](../README.md) · Related: [../technical-debt-report.md](../technical-debt-report.md) · [../diagrams/architecture.md](../diagrams/architecture.md)

Findings from static reading of the source. No scanner was executed and no code was run.

## Security Controls Present

| Control | Implementation | Assessment |
|---|---|---|
| **Strong content-based ETags** | SHA-512 over file bytes (`FileSystemPointer:22`), quoted per RFC 7232 (`getEtag`) | Good. SHA-512 is a strong digest; no MD5/SHA-1 used in application code |
| **Typed path binding** | `@PathVariable UUID uuid` — Spring rejects non-UUID input before controller code executes | Good. This is the primary input-validation control and it also prevents path traversal through the `uuid` segment |
| **No filename-driven file resolution** | the `{filename}` path segment is never bound or used to locate a file; only the UUID selects content | Good. Eliminates the classic traversal vector in download endpoints |
| **Streaming instead of buffering** | `InputStreamResource` over `ThrottlingInputStream` | Good. Bounded memory, so a large file cannot drive an OOM |
| **Bandwidth throttling** | Guava `RateLimiter` at 1 MB/s per download | Partial abuse mitigation (per-request only) |
| **Profile isolation of test doubles** | `@Profile("test")` on `FileStorageStub`, `@Profile("!test")` on `FileSystemStorage` | Good. The stub cannot load in a non-test profile |
| **Immutable request-scoped objects** | `final` fields in `ExistingFile`, `FileSystemPointer` | Good. No cross-request state leakage |
| **No secrets in source** | repository-wide scan found no hardcoded passwords, API keys, tokens, or connection strings | Good |
| **No dangerous execution sinks** | no `Runtime.exec`, `ProcessBuilder`, reflection-based invocation, deserialization of untrusted input, SQL, or template evaluation | Good |

## Security Gaps

| # | Gap | Location | Severity |
|---|---|---|---|
| S1 | **No authentication or authorization anywhere.** Spring Security is not a dependency; there is no `WebSecurityConfigurer`, no `@PreAuthorize`, no filter chain. Any caller who knows (or guesses) a UUID downloads the file. Security rests entirely on UUID unguessability — which is capability-style access control with no revocation, no expiry, and no audit of *who* downloaded. | whole application | High |
| S2 | **Actuator exposed unauthenticated.** `spring-boot-starter-actuator` 1.2.3 is present with no configuration; Boot 1.x exposes sensitive endpoints (`/env`, `/beans`, `/configprops`, `/mappings`, `/dump`, `/trace`) by default on the application port. This discloses configuration, environment, and internal structure. | `pom.xml` | High |
| S3 | **Build fetches over plaintext HTTP** from `http://repo.spring.io/milestone/`, enabling dependency substitution by a network attacker during the build. | `pom.xml` `<repositories>` | High |
| S4 | **EOL frameworks with known unpatched CVEs.** Spring Framework 4.1.6 and Spring Boot 1.2.3 no longer receive security fixes; the Boot 1.2.3 BOM also pins an old embedded Tomcat and Jackson. This is the single largest security exposure in the project. | `pom.xml` | High |
| S5 | **Guava 18.0** is likewise a decade old with no backported fixes. | `pom.xml` | Medium |
| S6 | **No transport security concerns addressed.** No HTTPS/HSTS configuration, no `server.ssl.*` properties, no security headers (`X-Content-Type-Options`, `Content-Security-Policy`, `X-Frame-Options`). Downloads would travel in cleartext unless a reverse proxy terminates TLS. | no config file exists | Medium |
| S7 | **`Content-Type` echoed from `Files.probeContentType`** without restricting it to a safe set. If a future storage backend serves user-uploaded content, a probed `text/html` or `image/svg+xml` served inline becomes a stored-XSS vector. There is no `Content-Disposition: attachment` and no `X-Content-Type-Options: nosniff`. | `FileSystemPointer:23-26`, `ExistingFile:102-113` | Medium |
| S8 | **No rate limiting on request count**, only on bytes within a single response. An attacker can open many requests; each triggers a full-file SHA-512 hash (see P1 in [complexity-analysis.md](complexity-analysis.md)), making the hashing cost an amplification vector for CPU/IO exhaustion. | `FileSystemStorage:28` | Medium |
| S9 | **Error handling discloses nothing but also handles nothing.** I/O failures surface as `IllegalArgumentException` and reach the default Boot error handler; Boot 1.x default `/error` output can include stack-trace details depending on configuration. No `@ControllerAdvice` normalizes responses. | `FileSystemPointer:27`, `ExistingFile:72` | Low |
| S10 | **Potential `NullPointerException` on missing classpath resource** — `getClass().getResource(...)` result is dereferenced without a null check, turning a deployment packaging error into a `500` on every request. | `FileSystemStorage:26-27`, `FileExamples:14-15` | Low |
| S11 | **`If-None-Match` comparison is exact-match only** — multi-value lists and weak (`W/"..."`) validators are not parsed. A correctness rather than confidentiality issue, but it can cause unnecessary full transfers. | `FileSystemPointer:73-75` | Low |
| S12 | **No access logging / audit trail** for downloads; logging is DEBUG/TRACE only and records the UUID, not the caller identity (there is none). | `ExistingFile`, both storages | Low |

## Cryptography Inventory

| Use | Algorithm | Assessment |
|---|---|---|
| ETag generation (application) | SHA-512 via Guava `Hashing.sha512()` | Strong; appropriate (non-secret integrity/validator use) |
| ETag generation (test filter) | SHA-512 via Guava | Strong |
| Spring's own `ShallowEtagHeaderFilter` default (not used in production config) | MD5-based in Spring 4.x | Not active in this application; the SHA-512 subclass exists specifically to avoid it, but is unwired |
| Transport encryption | none configured | Gap (S6) |
| At-rest encryption, signing, key management | none | Not applicable to current scope |

No deprecated cryptographic primitives (DES, RC4, MD5, SHA-1) are used by application code.

## Input Handling Summary

| Input | Validation | Residual risk |
|---|---|---|
| `{uuid}` | strict type conversion to `java.util.UUID` | none notable |
| `{filename}` | none — value is ignored | none (not used for resolution) |
| `If-None-Match` | treated as an opaque string, compared by equality | none |
| `If-Modified-Since` | parsed by Spring into `java.util.Date`; unparseable values cause a binding error | low |
| Request body | not read by any endpoint | none |

## Guardrail Compliance Note

This analysis is documentation-only. No security control was modified, no test was altered or removed, and no code was changed.

## Priority Order for Security Remediation

1. **High** — Upgrade off EOL Java 8 / Spring Boot 1.2.3 / Spring 4.1.6 (S4); switch the Maven repository to HTTPS or remove it (S3); secure or remove Actuator (S2); add authentication/authorization if the service is exposed beyond a trusted network (S1).
2. **Medium** — Upgrade Guava (S5); terminate TLS and add security headers plus `Content-Disposition: attachment` and `nosniff` (S6, S7); cache ETags and add request-rate limiting (S8).
3. **Low** — Add a `@ControllerAdvice` and meaningful exception types (S9); null-check resource lookups (S10); parse `If-None-Match` properly (S11); add download audit logging (S12).
