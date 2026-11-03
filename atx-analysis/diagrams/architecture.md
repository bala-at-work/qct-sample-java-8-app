# Architecture Diagrams

[← Back to index](../README.md)

## System Context

```
                        ┌──────────────────────────┐
                        │      HTTP Client         │
                        │  browser / curl / CDN    │
                        └────────────┬─────────────┘
                                     │ GET | HEAD
                                     │ /download/**
                                     │ If-None-Match, If-Modified-Since
                                     │ ⚠ no TLS configured
                                     ▼
          ╔══════════════════════════════════════════════════════╗
          ║            download-server  (system boundary)        ║
          ║                                                      ║
          ║   single Spring Boot JVM process, stateless          ║
          ║   ├ /download/{uuid}            → 301 redirect       ║
          ║   ├ /download/{uuid}/{filename} → 200 | 304 | 404    ║
          ║   └ Actuator endpoints          ⚠ unsecured          ║
          ║                                                      ║
          ╚═══════════════════════════┬══════════════════════════╝
                                      │ java.io / java.nio.file
                                      ▼
                        ┌──────────────────────────┐
                        │    Local filesystem      │
                        │  (currently: only the    │
                        │   app's own logback.xml) │
                        └──────────────────────────┘

  External actors:   1 (HTTP client)
  External systems:  1 (local filesystem)
  Outbound network calls: NONE — no databases, no queues, no APIs,
                          no cloud services, no AWS SDK
```

## Container / Process View

```
┌───────────────────────────────────────────────────────────────────────┐
│ Host                                                                  │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │ JVM — java -jar download-server-0.0.1-SNAPSHOT.jar              │  │
│  │                                                                 │  │
│  │  ┌───────────────────────────────────────────────────────────┐  │  │
│  │  │ Embedded Tomcat                                           │  │  │
│  │  │  thread-per-request worker pool                           │  │  │
│  │  │  ⚠ throttled transfers park workers for the full transfer │  │  │
│  │  └──────────────────────┬────────────────────────────────────┘  │  │
│  │                         ▼                                       │  │
│  │  ┌───────────────────────────────────────────────────────────┐  │  │
│  │  │ Spring ApplicationContext                                 │  │  │
│  │  │  scan root: com.nurkiewicz.download                       │  │  │
│  │  │                                                           │  │  │
│  │  │  profile (default) ──▶ FileSystemStorage  [STUB]          │  │  │
│  │  │  profile "test"    ──▶ FileStorageStub                    │  │  │
│  │  │                                                           │  │  │
│  │  │  DownloadController ─▶ ExistingFile ─▶ ThrottlingInputStream│ │  │
│  │  │  Actuator auto-configuration (no config file present)     │  │  │
│  │  └───────────────────────────────────────────────────────────┘  │  │
│  │                                                                 │  │
│  │  Logback ──▶ stdout (ConsoleAppender)                           │  │
│  └─────────────────────────────────────────────────────────────────┘  │
│                                      │                                │
│                                      ▼                                │
│                             ┌────────────────┐                        │
│                             │  filesystem    │                        │
│                             └────────────────┘                        │
└───────────────────────────────────────────────────────────────────────┘

Horizontal scaling: unconstrained. No session state, no coordination.
Content-hash ETags stay valid across replicas, so a load balancer can
route any request to any instance without breaking client caches.
```

## Integration Patterns

| Direction | Pattern | Protocol | Detail |
|---|---|---|---|
| Inbound | Request/response | HTTP/1.1 | `GET`/`HEAD` on two path templates |
| Inbound | Conditional GET (RFC 7232) | HTTP | `If-None-Match` / `If-Modified-Since` → `304` |
| Inbound | Redirect-then-fetch | HTTP | `301` from `/{uuid}` to `/{uuid}/{filename}` so the client derives a correct save-as name |
| Inbound | Operational polling | HTTP | Actuator endpoints (unsecured, unconfigured) |
| Outbound | Local file read | filesystem | `FileInputStream`, `Files.hash`, `Files.probeContentType` |
| Outbound | Log emission | stdout | SLF4J → Logback `ConsoleAppender` |

**Integration patterns deliberately absent:** no messaging or events, no RPC/gRPC/SOAP, no database connections, no cache servers, no service discovery, no circuit breakers or retries, no webhooks, no scheduled jobs, no outbound HTTP of any kind. The service's only external dependency is the local filesystem.

## Service Map and Communication Flows

```
   ┌──────────┐
   │  Client  │
   └────┬─────┘
        │
        │ (1) GET /download/{uuid}
        ▼
   ┌──────────────────┐
   │ download-server  │── 301 Location: /download/{uuid}/{name} ──┐
   └──────────────────┘                                           │
        ▲                                                         │
        └─────────────────── (2) client follows ──────────────────┘
        │
        │ (3) GET /download/{uuid}/{name}  [+ validators]
        ▼
   ┌──────────────────┐
   │ download-server  │
   │                  │──▶ 304 Not Modified   (client cache is current)
   │                  │──▶ 200 + throttled body (1 MB/s)
   │                  │──▶ 404 Not Found
   │                  │──▶ 400 Bad Request   (malformed UUID)
   └────────┬─────────┘
            │ read + hash
            ▼
     ┌────────────┐
     │ filesystem │
     └────────────┘

   Synchronous only. Every flow is a single blocking request/response.
   No fan-out, no orchestration, no compensating transactions.
```

## Security Boundaries and Access Patterns

```
╔════════════════════════════════════════════════════════════════════════╗
║ ZONE: PUBLIC / UNTRUSTED                                               ║
║                                                                        ║
║   Any HTTP client                                                      ║
║                                                                        ║
║   Trust controls at this edge:                                         ║
║     ✗ authentication            ✗ authorization                        ║
║     ✗ TLS                       ✗ request-rate limiting                ║
║     ✗ WAF / API gateway         ✗ IP allow-listing                     ║
║     ✓ UUID type validation → 400 on malformed input                    ║
║     ✓ GET/HEAD only — no mutating methods exposed                      ║
╚════════════════════════════════╤═══════════════════════════════════════╝
                                 │  ⚠ NO AUTHENTICATION BOUNDARY
                                 │     the only access control is
                                 │     knowledge of a UUID
                                 ▼
╔════════════════════════════════════════════════════════════════════════╗
║ ZONE: APPLICATION                                                      ║
║                                                                        ║
║   ┌──────────────────────────────────────────────────────────────────┐ ║
║   │ DownloadController                                               │ ║
║   │  ✓ @PathVariable UUID   — sole input validation                  │ ║
║   │  ✓ {filename} declared but NEVER bound or used for resolution    │ ║
║   │    → closes the path-traversal vector structurally               │ ║
║   └──────────────────────────────┬───────────────────────────────────┘ ║
║                                  ▼                                     ║
║   ┌──────────────────────────────────────────────────────────────────┐ ║
║   │ ExistingFile · ThrottlingInputStream                             │ ║
║   │  ✓ bandwidth throttling (1 MB/s per download)                    │ ║
║   │  ⚠ per-request limiter → N downloads = N MB/s                    │ ║
║   │  ⚠ blocking acquire → thread-pool exhaustion vector              │ ║
║   └──────────────────────────────┬───────────────────────────────────┘ ║
║                                  ▼                                     ║
║   ┌──────────────────────────────────────────────────────────────────┐ ║
║   │ FileStorage / FilePointer  (ports)                               │ ║
║   │  ✓ opaque UUID addressing — no client-supplied paths             │ ║
║   │  ✓ SHA-512 content ETags — leak no filesystem metadata           │ ║
║   └──────────────────────────────┬───────────────────────────────────┘ ║
║                                  ▼                                     ║
║   ┌──────────────────────────────────────────────────────────────────┐ ║
║   │ Actuator endpoints            ⚠⚠ UNSECURED                      │ ║
║   │  Boot 1.2.x defaults, no application.yml anywhere in the repo    │ ║
║   │  /env /configprops /beans /mappings /dump /trace reachable       │ ║
║   │  anonymously → environment and wiring disclosure                 │ ║
║   └──────────────────────────────────────────────────────────────────┘ ║
╚════════════════════════════════╤═══════════════════════════════════════╝
                                 │  ⚠ NO SANDBOX / NO CHROOT
                                 ▼
╔════════════════════════════════════════════════════════════════════════╗
║ ZONE: DATA (local filesystem)                                          ║
║                                                                        ║
║   ⚠ FileSystemStorage is a STUB: ignores the UUID and returns the      ║
║     application's own logback.xml for EVERY request                    ║
║     → unauthenticated internal-configuration disclosure                ║
║   ⚠ full SHA-512 computed per request, including HEAD and 304          ║
║     → unauthenticated CPU/disk amplification                           ║
╚════════════════════════════════════════════════════════════════════════╝

╔════════════════════════════════════════════════════════════════════════╗
║ ZONE: BUILD / SUPPLY CHAIN                                             ║
║                                                                        ║
║   Maven Central  ──── HTTPS ────▶ ✓                                    ║
║   repo.spring.io ──── HTTP  ────▶ ⚠⚠ PLAINTEXT — tamperable            ║
║   ⚠ entire framework stack is EOL and receives no security patches     ║
║   ✗ no dependency vulnerability scanning, no CI pipeline               ║
╚════════════════════════════════════════════════════════════════════════╝
```

### Access pattern summary

| Resource | Who may access | Control |
|---|---|---|
| `/download/{uuid}` | anyone | UUID knowledge only |
| `/download/{uuid}/{filename}` | anyone | UUID knowledge only (`filename` ignored) |
| Actuator endpoints | anyone | none |
| Local filesystem | the JVM process | OS process permissions only |

The implicit model is a **capability URL**: the UUID *is* the credential. That model requires TLS (absent) and demands the UUID never be logged (it currently is, at DEBUG). Neither requirement is documented or enforced. See [../analysis/security-patterns.md](../analysis/security-patterns.md).

## Profile-Based Composition Boundary

```
                       ┌───────────────────────────┐
                       │ Spring profile resolution │
                       └────────┬─────────┬────────┘
                   "test" active│         │"test" inactive
                                ▼         ▼
            ┌────────────────────────┐  ┌──────────────────────────┐
            │ FileStorageStub        │  │ FileSystemStorage        │
            │ @Profile("test")       │  │ @Profile("!test")        │
            │ src/test/java          │  │ src/main/java            │
            │ fixture or empty       │  │ [STUB: always logback.xml]│
            └────────────────────────┘  └──────────────────────────┘
                        │                            │
                        └────────────┬───────────────┘
                                     ▼
                         exactly one FileStorage bean
                         injected into DownloadController

  The profiles are mutually exclusive, so the context always has
  exactly one candidate. This is the test seam that lets the suite
  exercise the real controller and real caching logic without mocks.
```

## Target Architecture (post-remediation)

```
   ┌────────┐   HTTPS   ┌──────────────┐   ┌──────────────────────────┐
   │ Client │──────────▶│ Load balancer│──▶│ download-server          │
   └────────┘           │ TLS term.    │   │ supported LTS JDK        │
                        │ auth / WAF   │   │ Spring Boot 3.x          │
                        │ rate limiting│   │ ├ Actuator: restricted   │
                        └──────────────┘   │ ├ throttle: configurable │
                                           │ ├ hash: cached           │
                                           │ └ concurrency: bounded   │
                                           └───────────┬──────────────┘
                                                       ▼
                                           ┌──────────────────────────┐
                                           │ Real FileStorage adapter │
                                           │ (filesystem / S3 / DB)   │
                                           │ behind the EXISTING port │
                                           └──────────────────────────┘

  Unchanged by the upgrade: the FileStorage / FilePointer ports, the
  caching semantics in ExistingFile, and all 12 specifications.
```

Changes required to reach this state are enumerated in [../technical-debt/remediation-plan.md](../technical-debt/remediation-plan.md); sequencing is in [../migration/component-order.md](../migration/component-order.md).

See also [structural.md](structural.md) and [behavioral.md](behavioral.md).
