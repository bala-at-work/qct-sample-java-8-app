# Architecture Diagrams

- [Back to README](../README.md)
- Siblings: [structural.md](structural.md) · [behavioral.md](behavioral.md)
- See also: [../architecture/system-overview.md](../architecture/system-overview.md) · [../analysis/security-patterns.md](../analysis/security-patterns.md)

## Table of Contents
1. [System Context](#system-context)
2. [Container View](#container-view)
3. [Hexagonal (Ports and Adapters) View](#hexagonal-ports-and-adapters-view)
4. [Service Map and Communication Flows](#service-map-and-communication-flows)
5. [Integration Patterns](#integration-patterns)
6. [Security Boundaries and Access Patterns](#security-boundaries-and-access-patterns)
7. [Trust Zones](#trust-zones)
8. [Configuration and Profile Boundaries](#configuration-and-profile-boundaries)
9. [Target Architecture Sketch](#target-architecture-sketch)

## System Context

```
                          ╔═══════════════════════════════╗
                          ║      SYSTEM BOUNDARY          ║
   ┌──────────────┐       ║                               ║
   │ HTTP Client  │       ║    ┌─────────────────────┐    ║
   │              │ GET   ║    │                     │    ║
   │ • browser    │ HEAD  ║    │   download-server   │    ║
   │ • curl       ├───────╫───►│                     │    ║
   │ • download   │       ║    │  Spring Boot 1.2.3  │    ║
   │   manager    │◄──────╫────┤  Java 8             │    ║
   │              │ bytes ║    │                     │    ║
   │ ANONYMOUS    │ 301   ║    └──────────┬──────────┘    ║
   │ (no auth)    │ 304   ║               │               ║
   └──────────────┘ 404   ║               │ java.io       ║
                          ║               ▼               ║
   ┌──────────────┐       ║    ┌─────────────────────┐    ║
   │ Operator /   │ GET   ║    │ Local filesystem /  │    ║
   │ Monitoring   ├───────╫───►│ classpath           │    ║
   │              │       ║    │                     │    ║
   │ ANONYMOUS    │◄──────╫────┤ ⚠ STUB: only ever   │    ║
   │ (no auth)    │ env,  ║    │   serves logback.xml│    ║
   └──────────────┘ beans,║    └─────────────────────┘    ║
                    trace ║                               ║
                          ╚═══════════════════════════════╝

   NO OTHER EXTERNAL ACTORS OR SYSTEMS EXIST:
     ✗ no database            ✗ no message broker
     ✗ no cache tier          ✗ no identity provider
     ✗ no outbound HTTP       ✗ no cloud SDK (no AWS SDK)
     ✗ no service discovery   ✗ no configuration server
     ✗ no email/SMS/webhook   ✗ no object store
```

**Context summary.** The system has exactly **one inbound integration** (HTTP) and **one outbound integration** (local filesystem). Both actors are unauthenticated. This is the narrowest possible integration footprint, which is a significant modernization advantage — there are no external contracts to preserve or coordinate.

## Container View

```
┌───────────────────────────────────────────────────────────────────────────┐
│                           CLIENT TIER                                     │
│   HTTP/1.1 clients — no authentication required                           │
└────────────────────────────────┬──────────────────────────────────────────┘
                                 │  ⚠ plain HTTP; no TLS configured
                   ┌ ─ ─ ─ ─ ─ ─ ▼ ─ ─ ─ ─ ─ ─ ┐
                     EDGE TIER                      ← NOT IN REPOSITORY
                   │  TLS terminator / LB       │     (assumed, undocumented)
                     Rate limiting (absent)
                   └ ─ ─ ─ ─ ─ ─ ┬ ─ ─ ─ ─ ─ ─ ┘
                                 │
┌────────────────────────────────▼──────────────────────────────────────────┐
│                      APPLICATION TIER  (single JVM)                       │
│                                                                           │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │ Embedded Tomcat 8.0.x — port 8080                                   │  │
│  │   connector thread pool ≈ 200                                       │  │
│  │   ⚠ one blocked thread per in-flight throttled download             │  │
│  └──────────────────────────────┬──────────────────────────────────────┘  │
│                                 │                                         │
│  ┌──────────────────────────────▼──────────────────────────────────────┐  │
│  │ Spring DispatcherServlet                                            │  │
│  └────────┬─────────────────────────────────────────────┬──────────────┘  │
│           │                                             │                 │
│  ┌────────▼────────────────────────┐   ┌────────────────▼──────────────┐  │
│  │ /download/**                    │   │ Actuator endpoints            │  │
│  │   DownloadController            │   │   /health /info /metrics      │  │
│  │     ↓                           │   │   /env /configprops /beans    │  │
│  │   ExistingFile                  │   │   /mappings /trace /dump      │  │
│  │     ↓                           │   │                               │  │
│  │   ThrottlingInputStream         │   │ ⚠ NO AUTHENTICATION           │  │
│  │     ↓                           │   │ ⚠ NOT USED by app code        │  │
│  │   FileStorage / FilePointer     │   └───────────────────────────────┘  │
│  └────────┬────────────────────────┘                                      │
└───────────│───────────────────────────────────────────────────────────────┘
            │
┌───────────▼───────────────────────────────────────────────────────────────┐
│                          STORAGE TIER                                     │
│   Local filesystem / classpath                                            │
│   ⚠ FileSystemStorage is a STUB — returns /logback.xml for every UUID     │
│                                                                           │
│   ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐    │
│     Real storage + UUID→file metadata store      NOT IMPLEMENTED         │
│   └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘    │
└───────────────────────────────────────────────────────────────────────────┘
```

## Hexagonal (Ports and Adapters) View

```
                    ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐
                       DRIVING SIDE (inbound)
                    └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘
                                  │
                    ┌─────────────▼──────────────┐
                    │  HTTP Adapter              │
                    │  DownloadController        │
                    │  @RestController           │
                    └─────────────┬──────────────┘
                                  │
      ╔═══════════════════════════▼══════════════════════════════╗
      ║                  APPLICATION CORE                        ║
      ║                                                          ║
      ║   ┌──────────────────────────────────────────────────┐   ║
      ║   │  ExistingFile                                    │   ║
      ║   │    HTTP response policy:                         │   ║
      ║   │      304 / 301 / 200 decision                    │   ║
      ║   │      header construction                         │   ║
      ║   │      HEAD body suppression                       │   ║
      ║   └──────────────────────────────────────────────────┘   ║
      ║                                                          ║
      ║   ┌──────────────────────────────────────────────────┐   ║
      ║   │  ThrottlingInputStream                           │   ║
      ║   │    bandwidth policy — 1 MB/s per download        │   ║
      ║   │    ✅ zero framework coupling                     │   ║
      ║   └──────────────────────────────────────────────────┘   ║
      ║                                                          ║
      ║   ○─────────────── FileStorage  «port» ───────────────○  ║
      ║         findFile(UUID) : Optional<FilePointer>           ║
      ║                                                          ║
      ║   ○─────────────── FilePointer  «port» ───────────────○  ║
      ║         open, getSize, getEtag, getMediaType,            ║
      ║         matchesEtag, getLastModified, modifiedAfter      ║
      ╚═══════════════════════════┬══════════════════════════════╝
                                  │
            ┌─────────────────────┼─────────────────────┐
            ▼                                           ▼
  ┌──────────────────────┐                  ┌──────────────────────┐
  │ Filesystem Adapter   │                  │ Stub Adapter  [test] │
  │ FileSystemStorage    │                  │ FileStorageStub      │
  │ FileSystemPointer    │                  │ FileExamples         │
  │ @Profile("!test")    │                  │ @Profile("test")     │
  └──────────────────────┘                  └──────────────────────┘
            │                                           │
            ▼                                           ▼
  ┌──────────────────────┐                  ┌──────────────────────┐
  │ Local filesystem     │                  │ Classpath fixture    │
  │ (logback.xml — stub) │                  │ (download.txt)       │
  └──────────────────────┘                  └──────────────────────┘

                    ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐
                       DRIVEN SIDE (outbound)
                    └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘
```

**Verification of the boundary.** `java.io.File` appears in only two main-source classes — both on the driven side. `DownloadController` and `ExistingFile` import neither `FileSystemStorage` nor `FileSystemPointer`. The test suite replacing the entire driven side via a single profile switch is the practical proof that these are real seams rather than nominal interfaces.

**One leak worth noting.** Guava's `MediaType` appears in the `FilePointer` port signature, so the ports are not entirely library-neutral. See [../reference/data-models.md](../reference/data-models.md#external-types-in-the-domain).

## Service Map and Communication Flows

```
 ┌─────────────┐
 │   Client    │
 └──────┬──────┘
        │
        │ ① GET /download/{uuid}            [synchronous, request-response]
        ├──────────────────────────────────────────────────────┐
        │                                                      ▼
        │                                         ┌────────────────────────┐
        │ ◄── 301 Location: /download/{uuid}/{name}│ DownloadController     │
        │     (or 304 if client cache is valid)    │   .redirect()          │
        │                                         └────────────────────────┘
        │
        │ ② GET /download/{uuid}/{filename}  [synchronous, streaming response]
        ├──────────────────────────────────────────────────────┐
        │                                                      ▼
        │                                         ┌────────────────────────┐
        │ ◄── 200 + body streamed at ≤ 1 MB/s     │ DownloadController     │
        │     (or 304 / 404)                      │   .download()          │
        │                                         └───────────┬────────────┘
        │                                                     │ in-process
        │                                                     ▼
        │                                         ┌────────────────────────┐
        │                                         │ FileStorage port       │
        │                                         │   → filesystem         │
        │                                         └────────────────────────┘
        │
        │ ③ HEAD (either path)                [synchronous, headers only]
        ├──────────────────────────────────────────────────────►
        │ ◄── 200/304 with full headers, empty body
        │
        │ ④ GET /health, /env, /beans, …      [synchronous, UNAUTHENTICATED]
        └──────────────────────────────────────────────────────►
          ◄── actuator JSON

 OUTBOUND COMMUNICATION: none.
   The application makes no network call of any kind — no HTTP client,
   no database connection, no broker, no DNS lookup beyond the JVM's own.
```

| Flow | Protocol | Style | Auth | Notes |
|---|---|---|---|---|
| ① short URL | HTTP/1.1 | Synchronous request-response | None | Cache check precedes the redirect |
| ② long URL | HTTP/1.1 | Synchronous **streaming** | None | Throttled; blocks a worker thread |
| ③ HEAD | HTTP/1.1 | Synchronous, metadata only | None | No stream opened |
| ④ actuator | HTTP/1.1 | Synchronous | **None** | Not used by application code |

All internal communication is **in-process method invocation**. There is no asynchronous, event-driven, or message-based flow anywhere in the system.

## Integration Patterns

| Pattern | Present | Detail |
|---|---|---|
| **Synchronous request-response** | ✅ | The only interaction style |
| **Streaming response** | ✅ | `InputStreamResource` — bytes are not buffered in memory |
| **Conditional GET / HTTP caching** | ✅ | ETag + `Last-Modified`, both validators honoured |
| **Canonical URL redirect** | ✅ | `301` from the opaque URL to the filename-bearing URL |
| **Capability URL (bearer identifier)** | ✅ | Possession of the UUID is the sole access requirement |
| **Rate limiting / throttling** | ✅ | Guava `RateLimiter`, per download |
| **Ports and adapters** | ✅ | Two ports, two adapter sets |
| Asynchronous messaging | ❌ | No broker, no queue, no topic |
| Event-driven / pub-sub | ❌ | No events published or consumed |
| Webhooks / callbacks | ❌ | No outbound notification |
| Batch / scheduled jobs | ❌ | No `@Scheduled`, no cron |
| Service discovery | ❌ | No Eureka/Consul/registry |
| Externalized configuration server | ❌ | No Spring Cloud Config |
| Circuit breaker / retry / bulkhead | ❌ | Not applicable — no remote calls to protect |
| API gateway / BFF | ❌ | Direct client-to-service |
| Partial content / `Range` requests | ❌ | **Gap** — interrupted downloads cannot be resumed, which matters for a deliberately throttled download server |
| Content negotiation | ❌ | `Accept` is ignored; the server dictates `Content-Type` |
| Compression | ❌ | No `Content-Encoding`; `Accept-Encoding` ignored |
| Pagination / partial collections | ❌ | Not applicable — no collection endpoints |

## Security Boundaries and Access Patterns

```
 ┌───────────────────────────────────────────────────────────────────────┐
 │ ZONE 0 — PUBLIC INTERNET (untrusted)                                  │
 │                                                                       │
 │   Anonymous HTTP clients                                              │
 └────────────────────────────────┬──────────────────────────────────────┘
                                  │
   ╔══════════════════════════════╪══════════════════════════════════════╗
   ║  ⚠ NO SECURITY BOUNDARY IS ENFORCED HERE                            ║
   ║     ✗ no TLS configured        ✗ no authentication                  ║
   ║     ✗ no authorization         ✗ no rate limiting per client        ║
   ║     ✗ no WAF / IP filtering    ✗ no request size/count limits       ║
   ║     ✗ no security headers (nosniff, HSTS, CSP, Content-Disposition) ║
   ║                                                                      ║
   ║  THE ONLY ACCESS CONTROL IS KNOWLEDGE OF A UUID                     ║
   ║    ✓ unguessable (≈122 bits when randomly generated)                ║
   ║    ✗ not revocable      ✗ no expiry                                 ║
   ║    ✗ not per-user       ✗ leaks via history / Referer / proxy logs  ║
   ╚══════════════════════════════╪══════════════════════════════════════╝
                                  │
 ┌────────────────────────────────▼──────────────────────────────────────┐
 │ ZONE 1 — APPLICATION (implicitly trusted)                             │
 │                                                                       │
 │  ┌──────────────────────────────┐  ┌──────────────────────────────┐   │
 │  │ /download/**                 │  │ ACTUATOR                     │   │
 │  │                              │  │                              │   │
 │  │ Controls PRESENT:            │  │ Controls PRESENT: NONE       │   │
 │  │  ✓ UUID type validation      │  │                              │   │
 │  │    (400 on malformed)        │  │ Exposes UNAUTHENTICATED:     │   │
 │  │  ✓ GET/HEAD only (405)       │  │  🔴 /env       environment   │   │
 │  │  ✓ read-only (no write path) │  │  🔴 /configprops config      │   │
 │  │  ✓ 404 does not distinguish  │  │  🟠 /beans     bean graph    │   │
 │  │    "absent" from "forbidden" │  │  🟠 /mappings  URL surface   │   │
 │  │  ✓ 1 MB/s throttle           │  │  🟠 /trace     HTTP exchanges│   │
 │  │  ✓ SHA-512 (not MD5) ETags   │  │  🟠 /dump      thread dump   │   │
 │  │                              │  │  🟡 /metrics /health /info   │   │
 │  │ Controls ABSENT:             │  │                              │   │
 │  │  ✗ {filename} unvalidated    │  │ Not used by application code │   │
 │  │  ✗ no Content-Disposition    │  │ ⇒ pure attack surface        │   │
 │  │  ✗ no per-client rate limit  │  │                              │   │
 │  │  ✗ no audit trail (debug log │  │                              │   │
 │  │    only)                     │  │                              │   │
 │  └──────────────────────────────┘  └──────────────────────────────┘   │
 │                                                                       │
 │  ⚠ FRAMEWORK: Spring Boot 1.2.3 / Spring 4.1.6 / Java 8 — all EOL.    │
 │    Any newly disclosed CVE is UNPATCHABLE at these versions.          │
 │  ⚠ Unused EOL transitives on the classpath: jackson-databind 2.4,     │
 │    snakeyaml, hibernate-validator 5.1 — all with CVE histories.       │
 └────────────────────────────────┬──────────────────────────────────────┘
                                  │ java.io / java.nio
 ┌────────────────────────────────▼──────────────────────────────────────┐
 │ ZONE 2 — STORAGE (trusted)                                            │
 │                                                                       │
 │  Controls PRESENT:                                                    │
 │   ✓ No user input participates in path construction                   │
 │     (FileSystemStorage uses a hard-coded classpath resource)          │
 │   ✓ {filename} never reaches the filesystem                           │
 │                                                                       │
 │  ⚠ This safety is INCIDENTAL — it holds only because storage is a     │
 │    stub. Path traversal becomes a first-class risk the moment a real  │
 │    FileStorage is implemented. Mandatory then:                        │
 │      • resolve the UUID via metadata lookup, never string concat      │
 │      • canonicalize and assert containment within the storage root    │
 │      • never incorporate {filename} into a path or object key         │
 └───────────────────────────────────────────────────────────────────────┘
```

### Access pattern summary
| Resource | Who can access | How | Audit |
|---|---|---|---|
| `/download/{uuid}` | Anyone with the UUID | HTTP GET/HEAD | `log.debug` only |
| `/download/{uuid}/{filename}` | Anyone with the UUID | HTTP GET/HEAD | `log.debug` / `log.trace` only |
| Actuator (all endpoints) | **Anyone** | HTTP GET | None |
| File contents on disk | The JVM process user | `java.io` | None |

## Trust Zones

| Zone | Contents | Trust level | Boundary control |
|---|---|---|---|
| 0 — Internet | HTTP clients, operators | **Untrusted** | **None enforced** — no TLS, no auth, no rate limiting |
| 1 — Application | Tomcat, Spring context, controller, policy, actuator | Implicitly trusted | UUID type validation and HTTP method restriction only |
| 2 — Storage | Local filesystem, classpath | Trusted | No user input in path construction (incidental — stub only) |

**Assessment.** There is effectively **one trust boundary that matters** (Zone 0 → Zone 1) and it is unguarded apart from input typing. Everything inside is treated as trusted. For a service intended to distribute public files behind a TLS-terminating load balancer this may be acceptable — but nothing in the repository documents that assumption, and the unauthenticated actuator is not acceptable under any deployment model.

## Configuration and Profile Boundaries

```
 ┌────────────────────────────────────────────────────────────────────┐
 │ CONFIGURATION SOURCES                                              │
 │                                                                    │
 │  ✓ src/main/resources/logback.xml                                  │
 │       root = DEBUG, com.nurkiewicz = ALL, com/net/org = INFO        │
 │       console appender                                             │
 │       ⚠ ALSO the file served by FileSystemStorage                  │
 │                                                                    │
 │  ✗ application.properties   — DOES NOT EXIST                       │
 │  ✗ application.yml          — DOES NOT EXIST                       │
 │  ✗ bootstrap.yml            — DOES NOT EXIST                       │
 │  ✗ environment-specific config — DOES NOT EXIST                    │
 │  ✗ external config server   — DOES NOT EXIST                       │
 │                                                                    │
 │  ⇒ Port, actuator exposure, throttle rate and log levels are all   │
 │    framework defaults or source literals. Nothing is tunable        │
 │    per environment except via CLI args / env vars.                  │
 └────────────────────────────────────────────────────────────────────┘

 ┌────────────────────────────────────────────────────────────────────┐
 │ PROFILE BOUNDARY — the only environment-dependent behavior         │
 │                                                                    │
 │    spring.profiles.active = test          anything else (or unset) │
 │             │                                      │               │
 │             ▼                                      ▼               │
 │    ┌──────────────────┐                 ┌──────────────────────┐   │
 │    │ FileStorageStub  │                 │ FileSystemStorage    │   │
 │    │ @Profile("test") │                 │ @Profile("!test")    │   │
 │    │                  │                 │                      │   │
 │    │ TXT_FILE_UUID →  │                 │ ANY uuid →           │   │
 │    │   download.txt   │                 │   /logback.xml       │   │
 │    │ else → empty()   │                 │ (never empty ⇒ 404   │   │
 │    │   ⇒ 404 reachable│                 │  is UNREACHABLE)     │   │
 │    └──────────────────┘                 └──────────────────────┘   │
 │                                                                    │
 │  ⚠ The pairing is LOAD-BEARING: removing either @Profile yields    │
 │    two FileStorage candidates and an ambiguous-bean startup failure│
 │  ⚠ Production configuration expressed in terms of a TEST concept   │
 └────────────────────────────────────────────────────────────────────┘
```

## Target Architecture Sketch

A reference target consistent with the remediation plan. Illustrative only — **no transformation is being applied by this analysis**.

```
 ┌───────────────────────────────────────────────────────────────────────┐
 │ CLIENT                                                                │
 └────────────────────────────────┬──────────────────────────────────────┘
                                  │ HTTPS
 ┌────────────────────────────────▼──────────────────────────────────────┐
 │ EDGE                                                                  │
 │   TLS termination · per-client rate limiting · WAF                    │
 │   authentication (if content is non-public)                           │
 └────────────────────────────────┬──────────────────────────────────────┘
                                  │
 ┌────────────────────────────────▼──────────────────────────────────────┐
 │ APPLICATION — unchanged architecture, modernized platform             │
 │                                                                       │
 │   Java 21 (LTS, Amazon Corretto) · Spring Boot 3.x                    │
 │   externalized application.yml (port, log levels, throttle rate,      │
 │     explicit actuator exposure)                                       │
 │                                                                       │
 │   DownloadController  ──unchanged structure──►  ExistingFile          │
 │                                                      │                │
 │                                                      ▼                │
 │                                          FileStorage / FilePointer    │
 │                                               «ports unchanged»       │
 │   Actuator: /actuator/** — restricted exposure, authenticated         │
 └────────────────────────────────┬──────────────────────────────────────┘
                                  │ new adapter, ports unchanged
 ┌────────────────────────────────▼──────────────────────────────────────┐
 │ STORAGE                                                               │
 │   Object store for file bytes                                         │
 │   Metadata store for UUID → object key, name, size, ETag, type        │
 │                                                                       │
 │   ✓ ETag taken from stored metadata — no per-request full-file hash   │
 │   ✓ Path/key resolved by metadata lookup — no traversal surface       │
 └───────────────────────────────────────────────────────────────────────┘
```

**Why the architecture itself needs no change.** The existing ports-and-adapters boundary means a storage migration requires exactly one new adapter pair (`FileStorage` + `FilePointer` implementations) with no modification to `DownloadController`, `ExistingFile`, or `ThrottlingInputStream`. The modernization work is therefore concentrated in the platform (JDK, Spring Boot, dependencies) and in configuration — not in redesign.

**Prerequisite.** Register `src/test/groovy` as a test-source root first, so the 12-feature Spock specification can verify that the HTTP contract survives each upgrade stage. See [../technical-debt/remediation-plan.md](../technical-debt/remediation-plan.md#phase-0--restore-the-safety-net).
