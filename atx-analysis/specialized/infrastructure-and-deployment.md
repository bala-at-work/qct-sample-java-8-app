# Infrastructure, Deployment and Observability

- [Back to README](../README.md)
- See also: [../architecture/system-overview.md](../architecture/system-overview.md#deployment-model) · [../diagrams/architecture.md](../diagrams/architecture.md#container-view)

> Specialized documentation generated because deployment-relevant configuration was looked for and its **absence** is itself a significant finding. Everything below was determined by static inspection; nothing was built, packaged, or deployed.

## Table of Contents
1. [Infrastructure Inventory](#infrastructure-inventory)
2. [Build and Packaging](#build-and-packaging)
3. [Runtime Configuration](#runtime-configuration)
4. [Profiles](#profiles)
5. [Logging Configuration](#logging-configuration)
6. [Observability](#observability)
7. [Capacity Characteristics](#capacity-characteristics)
8. [Deployment Readiness Assessment](#deployment-readiness-assessment)
9. [Required Work for a Deployable Service](#required-work-for-a-deployable-service)

## Infrastructure Inventory

An exhaustive scan for infrastructure, deployment and CI artifacts. **Every category is absent.**

| Category | Searched for | Found |
|---|---|---|
| Container image | `Dockerfile`, `Containerfile`, `.dockerignore` | — |
| Container orchestration | `docker-compose.y*ml`, Kubernetes manifests, Helm charts, `kustomization.yaml` | — |
| AWS IaC | CloudFormation templates, SAM `template.y*ml`, CDK (`cdk.json`, `app.ts`), `samconfig.toml` | — |
| Other IaC | Terraform (`*.tf`), Pulumi, Ansible, Chef, Puppet | — |
| CI/CD | `.github/workflows/`, `Jenkinsfile`, `buildspec.yml`, `.gitlab-ci.yml`, `.travis.yml`, `azure-pipelines.yml`, `.circleci/` | — |
| Process management | `systemd` units, `Procfile`, `supervisord.conf`, init scripts | — |
| Service mesh / proxy | Envoy, nginx, HAProxy configuration | — |
| Secrets management | `.env`, vault configuration, SOPS, sealed secrets | — |
| Application config | `application.properties`, `application.yml`, `bootstrap.yml`, any profile-specific variant | — |
| Database migration | Flyway, Liquibase, `.sql` files | — |
| Monitoring config | Prometheus scrape config, Grafana dashboards, CloudWatch definitions | — |
| Repository hygiene | `.gitignore` | — (and `.DS_Store` is committed) |

**The only non-source configuration in the repository is:**

| File | Purpose |
|---|---|
| `pom.xml` | Maven build definition |
| `src/main/resources/logback.xml` | Logging configuration (and, oddly, the file the stub serves) |
| `.mvn/wrapper/maven-wrapper.properties` | Maven wrapper pinning (`wrapperVersion=3.3.2`, `distributionType=only-script`, Maven 3.9.8) |
| `mvnw`, `mvnw.cmd` | Maven wrapper launchers |

## Build and Packaging

| Aspect | State | Severity |
|---|---|---|
| Build tool | Maven, via the wrapper (Maven 3.9.8 — the only genuinely current piece of tooling in the project) | — |
| Modules | 1 (no aggregator, no submodules) | — |
| Declared plugins | **One**: `maven-compiler-plugin:3.0` (2012) | 🟡 Low |
| Packaging | `jar` (inherited from the Boot parent) | — |
| `spring-boot-maven-plugin` | **Not declared** in `<build><plugins>` — only plugin *management* is inherited | 🟠 Medium |
| Consequence | `mvn package` produces a **plain JAR, not an executable fat JAR**. The application cannot be launched with `java -jar` as currently built | 🟠 Medium |
| Groovy test compilation | **Not configured** — `src/test/groovy` is not a registered source root, so the Spock specification never compiles or runs | 🟠 Medium |
| Surefire configuration | None — relies on parent defaults | 🟡 Low |
| Artifact repository | Maven Central (implicit) plus `http://repo.spring.io/milestone/` (**plain HTTP**) | 🟠 Medium |
| Reproducibility | No dependency lock mechanism; version ranges are absent (all versions are exact), which is good | — |
| SBOM generation | None | 🟡 Low |

**Two build defects block deployment outright:** no fat-JAR repackaging (so there is no runnable artifact) and no Groovy test compilation (so there is no verification). Both are tracked in [../technical-debt/maintenance-burden.md](../technical-debt/maintenance-burden.md#build-configuration-gaps).

## Runtime Configuration

**There is no configuration file.** Every runtime value is either a Spring Boot 1.2.3 default or a literal in source code.

| Setting | Effective value | Where it comes from | Externalizable today? |
|---|---|---|---|
| HTTP port | 8080 | Boot default | Only via CLI arg / env var |
| Context path | `/` | Boot default | Only via CLI arg / env var |
| Tomcat `maxThreads` | ~200 | Boot default | Only via CLI arg / env var |
| TLS / HTTPS | **disabled** | no `server.ssl.*` | No |
| Actuator endpoint exposure | Boot 1.2.x defaults — **all sensitive endpoints open, unauthenticated** | no `management.*` | No |
| Throttle rate | 1 MB/s | **hard-coded** `RateLimiter.create(ONE_MB)` in `ExistingFile.buildResource` | **No** |
| Served file path | `/logback.xml` | **hard-coded** in `FileSystemStorage` | **No** |
| ETag algorithm | SHA-512 | **hard-coded** in `FileSystemPointer` | No (appropriate as a constant) |
| Redirect path template | `/download/{uuid}/{name}` | **hard-coded** in `ExistingFile` | No (appropriate) |
| Log levels | root `DEBUG`, `com.nurkiewicz` `ALL` | `logback.xml` | Only by editing the packaged file |
| Active profile | none | unset ⇒ `FileSystemStorage` is selected | Via `spring.profiles.active` |

**Assessment.** The application cannot be tuned per environment. Nothing distinguishes a development run from a production run except the `spring.profiles.active` value, and even that only swaps the storage bean. The two most operationally significant values — the throttle rate and the actuator exposure — are not configurable at all without a code change. This is remediation item P3.4 / Gate 8.1–8.2.

## Profiles

The only environment-dependent behavior in the system.

```
  spring.profiles.active = test            anything else, or unset
            │                                         │
            ▼                                         ▼
  ┌────────────────────────┐              ┌──────────────────────────┐
  │ FileStorageStub        │              │ FileSystemStorage        │
  │ @Profile("test")       │              │ @Profile("!test")        │
  │ [test source tree]     │              │ [main source tree]       │
  ├────────────────────────┤              ├──────────────────────────┤
  │ TXT_FILE_UUID →        │              │ ANY uuid →               │
  │   download.txt         │              │   /logback.xml           │
  │ anything else →        │              │                          │
  │   Optional.empty()     │              │ never returns empty()    │
  │   ⇒ 404 IS reachable   │              │ ⇒ 404 is UNREACHABLE     │
  └────────────────────────┘              └──────────────────────────┘
```

| Property | Detail |
|---|---|
| Profile count | 1 named profile (`test`), plus the implicit default |
| Maven profiles | None declared |
| Profile-specific config files | None |
| Load-bearing | **Yes** — the `@Profile("test")` / `@Profile("!test")` pairing guarantees exactly one `FileStorage` bean. Removing either annotation causes an ambiguous-bean startup failure |
| Design concern | Production bean registration is expressed in terms of a *testing* concept — an inverted dependency. The conventional alternative is a test-side `@Primary` / `@TestConfiguration` override |

## Logging Configuration

`src/main/resources/logback.xml`

| Element | Value |
|---|---|
| Appender | `ch.qos.logback.core.ConsoleAppender` |
| Pattern | `%d{HH:mm:ss.SSS} \| %thread \| %-5level \| %logger{1} \| %msg%n` |
| Logger `com` | `INFO` |
| Logger `com.nurkiewicz` | `ALL` |
| Logger `net` | `INFO` |
| Logger `org` | `INFO` |
| Root level | `DEBUG` |
| File appender | **None** — console only |
| Rolling policy | N/A |
| Structured / JSON output | **None** |
| Async appender | None |

### Where logging actually occurs
| Class | Statement | Level |
|---|---|---|
| `FileSystemStorage` | `log.debug("Downloading {}", uuid)` | DEBUG |
| `FileStorageStub` *(test)* | `log.debug("Downloading {}", uuid)` | DEBUG |
| `ExistingFile` | `log.debug("Serving {} '{}'", method, filePointer)` | DEBUG |
| `ExistingFile` | `log.trace("Redirecting {} '{}'", method, filePointer)` | TRACE |
| `ExistingFile` | `log.trace("Cached on client side {}, returning 304", filePointer)` | TRACE |

### Concerns
| Concern | Severity | Detail |
|---|---|---|
| Verbose production defaults | 🟡 Low | Root `DEBUG` plus `com.nurkiewicz` at `ALL` means every request produces log output, including `trace`-level entries |
| Internal detail in logs | 🟡 Low | `FilePointer.toString()` emits the absolute `target` path, size, full SHA-512 hash and media type |
| Not environment-switchable | 🟠 Medium | Levels live in a packaged resource with no properties file to override them |
| Console-only | 🟡 Low | Acceptable for container deployment (stdout capture), inadequate for a bare-metal process |
| No audit log | 🟠 Medium | Downloads are recorded only at `DEBUG`, so an access audit trail depends on a debug log level being enabled in production |
| **`logback.xml` is also the data** | — | `FileSystemStorage` resolves and serves this exact file for every UUID. Editing the logging configuration changes what the stub serves |

The last row is worth emphasizing: the logging configuration and the application's only served file are the same artifact. This is harmless given the stub's purpose but is a genuine surprise for a maintainer.

## Observability

| Capability | State | Severity |
|---|---|---|
| Health check | `/health` via actuator (unauthenticated) | 🟡 Low |
| Metrics | `/metrics` via actuator — JVM and request counters only | 🟡 Low |
| Custom application metrics | **None** — no counter for downloads served, bytes transferred, cache-hit ratio, or throttle wait time | 🟠 Medium |
| Distributed tracing | **None** — no Sleuth, OpenTelemetry, or X-Ray | 🟡 Low |
| Structured logging | **None** — plain text pattern only | 🟡 Low |
| Correlation IDs | **None** — no request ID in the log pattern or MDC | 🟠 Medium |
| Error tracking | **None** | 🟡 Low |
| Audit logging | Debug-level only | 🟠 Medium |
| Readiness vs liveness distinction | None — a single `/health` endpoint | 🟡 Low |
| Alerting definitions | **None** | 🟡 Low |

### Metrics that would matter for this specific service
Given the application's characteristics, the most valuable additions would be:

| Metric | Why it matters |
|---|---|
| Concurrent in-flight downloads | Directly measures the binding capacity constraint (one blocked thread per download) |
| Tomcat thread-pool utilization | Thread exhaustion is the failure mode, and it starves `/health` too |
| Cache-hit ratio (`304` vs `200`) | Measures whether the ETag/`Last-Modified` implementation is actually saving bandwidth |
| Bytes transferred | Aggregate egress is **unbounded** (the 1 MB/s limit is per download, not global), so this is otherwise invisible |
| ETag computation time | The full-file SHA-512 per request is the known hotspot |
| `404` rate | Would indicate UUID guessing attempts — currently undetectable |

## Capacity Characteristics

Derived from the code, not measured.

| Characteristic | Value | Source |
|---|---|---|
| Process model | Single JVM, stateless | No session, no server-side cache |
| Horizontal scalability | **Unlimited** (any instance serves any request) | Statelessness; subject to shared storage existing |
| Vertical scalability | **Capped by the Tomcat thread pool** | `RateLimiter.acquire` blocks the worker thread for the download's duration |
| Concurrent download ceiling | ≈ Tomcat `maxThreads` (~200 by default) | Beyond this, **all** requests queue — including `/health` and cheap `304`s |
| Per-download bandwidth | ≤ 1 MB/s | `RateLimiter.create(ONE_MB)` |
| **Aggregate egress** | **Unbounded** — N concurrent downloads consume up to N MB/s | A fresh `RateLimiter` is created per response, so there is no global ceiling |
| Memory per download | Small and constant | Streaming via `InputStreamResource`; bytes are not buffered |
| CPU per request | **O(file size)** | Full-file SHA-512 in `FileSystemPointer`'s constructor — paid even for `304` and `301` responses |
| I/O per `200` | **2× file size** | Once to hash, once to stream |
| Startup dependencies | None | No database, broker, or outbound call to wait for |
| Graceful shutdown | Not configured | In-flight downloads would be severed |

### Capacity arithmetic
At 1 MB/s, a 100 MB file occupies one worker thread for ~100 seconds. With ~200 threads, roughly 200 such downloads saturate the connector. An unauthenticated client can therefore exhaust the thread pool cheaply, and there is **no per-client rate limiting** anywhere in the system to prevent it.

A second, subtler amplification exists: because the full-file SHA-512 is computed before the status is decided, a client can force repeated whole-file hashing using trivially small conditional requests that return `304`. This is negligible with the current stub (a small `logback.xml`) and becomes meaningful the moment real files are served.

## Deployment Readiness Assessment

| Dimension | Ready? | Blocker |
|---|---|---|
| Runnable artifact | ❌ | `spring-boot-maven-plugin` not declared ⇒ no executable JAR |
| Verified behavior | ❌ | `src/test/groovy` unregistered ⇒ zero tests execute |
| Functional completeness | ❌ | `FileSystemStorage` is a stub serving `logback.xml` for every UUID; no upload path; no UUID→file metadata store |
| Environment configuration | ❌ | No configuration file of any kind |
| Transport security | ❌ | No TLS configuration; requires an external terminator that nothing documents |
| Access control | ❌ | No authentication; actuator endpoints anonymously expose `/env` and `/configprops` |
| Platform support | ❌ | Java 8, Spring Boot 1.2.3, Spring 4.1.6 — all EOL and unpatchable |
| Containerization | ❌ | No Dockerfile |
| Infrastructure definition | ❌ | No IaC |
| CI/CD | ❌ | No pipeline, no CVE scanning |
| Observability | ⚠️ Partial | Actuator defaults only; no custom metrics, correlation IDs, or audit trail |
| Operational resilience | ⚠️ Partial | Stateless and horizontally scalable, but no graceful shutdown and no per-client rate limiting |
| Scalability | ✅ | Genuinely stateless; horizontally scalable by design |

**Conclusion.** This repository is a **reference implementation**, not a deployable service — and it says so itself in the `FileSystemStorage` Javadoc. The HTTP semantics it demonstrates (conditional GET, HEAD, canonical redirects, content-addressed ETags, throttling) are implemented to a high standard, but every surrounding operational concern is absent.

## Required Work for a Deployable Service

Ordered by dependency. Items marked 🔴 are hard blockers.

| # | Work item | Severity | Reference |
|---|---|---|---|
| 1 | 🔴 Register `src/test/groovy` so behavior can be verified at all | 🟠 Medium | P0.1 |
| 2 | 🔴 Declare `spring-boot-maven-plugin` to produce an executable JAR | 🟠 Medium | Stage 1.7 |
| 3 | 🔴 Upgrade the platform — Java 8 → LTS, Boot 1.2.3 → 3.x (after removing the Spring version pins) | 🔴 High | P1.1–P1.3 |
| 4 | 🔴 Implement a real `FileStorage` plus a UUID→file metadata store, and an upload path to mint UUIDs | 🟠 Medium | F1–F3 |
| 5 | 🔴 Secure or remove the actuator; decide and document the `/download/**` access model | 🟠 Medium | P2.7, P2.8 |
| 6 | 🔴 Add `application.yml` externalizing port, log levels, actuator exposure and throttle rate | 🟠 Medium | P3.4 |
| 7 | Provide TLS — either `server.ssl.*` or a documented terminating load balancer | 🟠 Medium | S10 |
| 8 | Add per-client rate limiting at the edge | 🟠 Medium | S8 |
| 9 | Make the ETag lazy/cached so a `304` does not cost a full file read (keep SHA-512) | 🟡 Low | P3.5 |
| 10 | Add a Dockerfile and infrastructure definitions | 🟡 Low | P3.11 |
| 11 | Add CI running build, tests and dependency CVE scanning, asserting a non-zero test count | 🟡 Low | P3.10 |
| 12 | Add custom metrics, correlation IDs and an audit trail | 🟠 Medium | — |
| 13 | Configure graceful shutdown so in-flight downloads complete | 🟡 Low | — |
| 14 | Add a `.gitignore`, delete the committed `.DS_Store`, and expand the two-line `README.md` | 🟡 Low | P3.7, P3.8 |
| 15 | Consider `Range` / partial-content support so throttled downloads are resumable | 🟡 Low | S7 |
