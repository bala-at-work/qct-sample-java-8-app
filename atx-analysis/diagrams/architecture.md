# Architecture Diagrams

[← Back to index](../README.md) · Related: [structural.md](structural.md) · [behavioral.md](behavioral.md) · [../analysis/security-patterns.md](../analysis/security-patterns.md)

## 1. System Context (C4 level 1)

```
                     ┌──────────────────────────────┐
                     │        HTTP Client           │
                     │  browser / curl / service    │
                     │  Downloads files by UUID     │
                     └──────────────┬───────────────┘
                                    │ HTTP GET/HEAD
                                    │ /download/{uuid}[/{filename}]
                                    ▼
          ┌───────────────────────────────────────────────────┐
          │              download-server                      │
          │  Serves files by UUID with HTTP caching and        │
          │  bandwidth throttling. Stateless Spring Boot app.  │
          └──────────────────────────┬────────────────────────┘
                                     │ file reads
                                     ▼
                     ┌──────────────────────────────┐
                     │      Local Filesystem        │
                     │  (currently: classpath       │
                     │   resource logback.xml —     │
                     │   storage is a stub)         │
                     └──────────────────────────────┘

          ┌───────────────────────────────────────────────────┐
          │ Operator / monitoring (potential)                 │
          │  Spring Boot Actuator endpoints — auto-configured,│
          │  unsecured, not consumed by anything in the repo  │
          └───────────────────────────────────────────────────┘

External systems NOT present: no database, no object store, no message broker,
no identity provider, no third-party API, no AWS service integration.
```

## 2. Container / Component View (C4 level 2–3)

```
┌───────────────────────────────── JVM process ─────────────────────────────────┐
│                                                                               │
│  ┌──────────────── Web container (embedded Tomcat) ────────────────────────┐  │
│  │  thread-per-request pool                                               │  │
│  │  DispatcherServlet ──► handler mapping /download/**                    │  │
│  └────────────────────────────────┬──────────────────────────────────────┘  │
│                                   ▼                                          │
│  ┌──────────── Web adapter ───────────────────────────────────────────────┐  │
│  │  DownloadController   — routing, binding, 404 translation             │  │
│  └────────────────────────────────┬──────────────────────────────────────┘  │
│                                   ▼                                          │
│  ┌──────────── Domain ────────────────────────────────────────────────────┐  │
│  │  ExistingFile          — conditional requests, status + header policy  │  │
│  │  ThrottlingInputStream — egress shaping                                │  │
│  └──────────────┬─────────────────────────────────────────────────────────┘  │
│                 ▼                                                            │
│  ┌──────────── Ports ─────────────────────────────────────────────────────┐  │
│  │  FileStorage (lookup)        FilePointer (content + metadata)          │  │
│  └──────────────┬─────────────────────────────────────────────────────────┘  │
│                 ▼                                                            │
│  ┌──────────── Adapters ──────────────────────────────────────────────────┐  │
│  │  FileSystemStorage / FileSystemPointer   (profile !test)               │  │
│  │  FileStorageStub                          (profile test)               │  │
│  └──────────────┬─────────────────────────────────────────────────────────┘  │
│                 │                                                            │
│  ┌──────────── Cross-cutting ─────────────────────────────────────────────┐  │
│  │  SLF4J + Logback (logback.xml)   │   Spring Boot Actuator (unused)      │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────┬───────────────────────────────────────────┘
                                    ▼  java.io / java.nio.file
                            ┌───────────────┐
                            │  Filesystem   │
                            └───────────────┘
```

## 3. Integration Patterns and External Connections

| Direction | Integration | Pattern | Protocol | Notes |
|---|---|---|---|---|
| Inbound | HTTP file download | Request/response, resource-oriented REST | HTTP/1.1 | Conditional requests (RFC 7232), `301` canonicalization, `HEAD` support |
| Inbound | Actuator management | Request/response | HTTP/1.1 | Auto-configured on the same port; unsecured |
| Outbound | Filesystem read | Synchronous blocking I/O | file:// | The only outbound dependency |
| Outbound | Logging | Fire-and-forget appender | stdout (console) | `logback.xml` |

No asynchronous messaging, no event bus, no scheduled jobs, no outbound HTTP, no retry/circuit-breaker machinery — because there is nothing remote to call.

## 4. Service Map and Communication Flows

```
                       ┌──────────────┐
                       │   Client     │
                       └──┬────────┬──┘
          GET/HEAD        │        │       GET/HEAD
     /download/{uuid}     │        │   /download/{uuid}/{file}
                          ▼        ▼
              ┌───────────────────────────────┐
              │      download-server          │
              │                               │
              │  redirect()        download() │
              │      │                 │      │
              │      └────────┬────────┘      │
              │               ▼               │
              │        findExistingFile       │
              │               │               │
              │               ▼               │
              │        FileStorage port       │
              └───────────────┬───────────────┘
                              ▼
                    ┌──────────────────┐
                    │   Filesystem     │
                    └──────────────────┘

Synchronous throughout. No internal service-to-service calls exist —
this is a single deployable with in-process method invocation only.
```

## 5. Security Boundaries and Access Patterns

```
╔═══════════════════════════════════════════════════════════════════════════════╗
║  UNTRUSTED ZONE — any network client                                          ║
║                                                                               ║
║   ┌──────────┐                                                                ║
║   │  Client  │   no credentials required                                       ║
║   └────┬─────┘                                                                ║
╚════════│══════════════════════════════════════════════════════════════════════╝
         │  ⚠ TRUST BOUNDARY — crossed with NO authentication and NO authorization
         │  ⚠ No TLS configured in the application
         ▼
╔═══════════════════════════════════════════════════════════════════════════════╗
║  APPLICATION ZONE — download-server JVM                                       ║
║                                                                               ║
║   Controls enforced at the boundary:                                          ║
║    ✔ uuid bound as java.util.UUID (strict type validation)                    ║
║    ✔ {filename} segment ignored for resolution ⇒ no path traversal            ║
║    ✔ request body never read                                                  ║
║    ✖ no authentication / authorization / rate limiting by request count       ║
║    ✖ no security headers (nosniff, CSP, Content-Disposition)                  ║
║                                                                               ║
║   ┌────────────────────┐          ┌───────────────────────────────────────┐   ║
║   │ DownloadController │          │ ⚠ Actuator endpoints                  │   ║
║   └─────────┬──────────┘          │   /env /beans /configprops /mappings  │   ║
║             ▼                     │   unsecured ⇒ config disclosure       │   ║
║   ┌────────────────────┐          └───────────────────────────────────────┘   ║
║   │ ExistingFile       │  SHA-512 strong ETag (integrity validator)           ║
║   └─────────┬──────────┘  1 MB/s per-request throttle (partial abuse control) ║
╚═════════════│═════════════════════════════════════════════════════════════════╝
              │  internal boundary — FileStorage / FilePointer ports
              ▼
╔═══════════════════════════════════════════════════════════════════════════════╗
║  STORAGE ZONE — local filesystem                                              ║
║   Access limited to whatever the adapter resolves (today: one classpath file) ║
║   Access control inherited from the OS process identity; no per-file ACLs     ║
╚═══════════════════════════════════════════════════════════════════════════════╝

╔═══════════════════════════════════════════════════════════════════════════════╗
║  BUILD-TIME BOUNDARY                                                          ║
║   Maven Central (https)                     ✔                                 ║
║   repo.spring.io/milestone  ⚠ http:// — plaintext, substitution risk          ║
╚═══════════════════════════════════════════════════════════════════════════════╝
```

**Effective access model**: capability-by-UUID. Possession of the UUID *is* the authorization; there is no revocation, expiry, scoping, or audit of who downloaded what. Full analysis: [../analysis/security-patterns.md](../analysis/security-patterns.md).

## 6. Target Architecture Sketch (post-remediation)

Shown for orientation only; see [../technical-debt/remediation-plan.md](../technical-debt/remediation-plan.md).

```
   Client ──HTTPS──► [ TLS termination / WAF ] ──► download-server (current LTS Java,
                                                     current Spring Boot)
                                                        │
                                   ┌────────────────────┴───────────────────┐
                                   │ FileStorage port (unchanged)           │
                                   └────────────────────┬───────────────────┘
                                                        ▼
                                          Shared object storage (e.g. S3)
                                          + metadata source for UUID→object
                                          + object checksum reused as ETag
                                            (removes per-request hashing)

   Management traffic on a separate, authenticated port.
   The ports-and-adapters boundary means the web and caching layers are untouched.
```
