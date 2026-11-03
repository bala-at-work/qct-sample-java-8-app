# Infrastructure and Deployment

[← Back to index](../README.md)

## Current State

The repository contains **no infrastructure or deployment definitions**. This section documents what exists, what is absent, and what the code implies about deployment.

### Present

| Artifact | Purpose |
|---|---|
| `pom.xml` | Build definition (single Maven module) |
| `mvnw`, `mvnw.cmd`, `.mvn/wrapper/` | Maven Wrapper — pins the Maven version for reproducible builds |
| `src/main/resources/logback.xml` | Logging configuration (console appender) |

### Absent

| Category | Status |
|---|---|
| Container image definition | No `Dockerfile`, no `.dockerignore`, no Docker Compose file |
| Kubernetes manifests | None (no Deployment, Service, Ingress, ConfigMap, HPA) |
| AWS infrastructure as code | No CloudFormation, SAM, CDK, or Terraform |
| CI/CD pipeline | No `.github/workflows`, no `.gitlab-ci.yml`, no `buildspec.yml`, no Jenkinsfile |
| Application configuration | No `application.properties`, no `application.yml`, no profile-specific config files |
| Environment configuration | No `.env`, no externalized secrets reference |
| Health check definition | Actuator is present but unconfigured; no readiness/liveness probe definitions |
| Service mesh / API gateway config | None |
| Monitoring / alerting definitions | None |

## Implied Deployment Model

Derived from the code and build configuration:

| Aspect | Value | Source |
|---|---|---|
| Artifact type | JAR | Spring Boot parent default packaging; no `<packaging>` override |
| Startup | `MainApplication.main` → `SpringApplication.run` | `MainApplication.java` |
| HTTP server | Embedded Tomcat | `spring-boot-starter-web` default |
| Port | 8080 | Spring Boot default (no configuration present to change it) |
| Process model | Single JVM, thread-per-request | Servlet container default |
| State | Stateless | No session usage, no instance mutable state |
| External dependencies | Local filesystem only | `FileSystemStorage`, `FileSystemPointer` |
| Configuration input | Spring profile (`test` vs. not-`test`) | `@Profile` annotations — the only configuration switch in the system |
| Log destination | stdout | `logback.xml` `ConsoleAppender` |
| TLS | Not configured | No `server.ssl.*` settings exist |

### Build gap

`spring-boot-maven-plugin` is **not declared** in `pom.xml`. Plugin *management* is inherited from `spring-boot-starter-parent`, but without an explicit `<plugin>` entry the `repackage` goal does not execute. The build therefore produces a plain library JAR with no embedded dependencies and no manifest `Main-Class` — it is **not** runnable via `java -jar`. This must be fixed before the implied deployment model actually works (migration step 3.9).

## Scaling Characteristics

| Property | Assessment |
|---|---|
| Horizontal scaling | **Unconstrained.** No session state, no coordination, no shared cache. Content-hash ETags remain valid across replicas, so a load balancer can route any request to any instance without invalidating client caches. |
| Vertical scaling | Benefits from CPU (SHA-512 hashing on every request) and disk throughput |
| Binding constraint under load | **Servlet thread pool.** Throttled transfers park a worker thread for the full transfer duration (`RateLimiter.acquire` blocks), so concurrent large downloads exhaust the pool before CPU or bandwidth saturates. |
| Secondary constraint | Disk I/O from recomputing the full content hash on every request, including `HEAD` and `304` |
| Throttle semantics under scale | The 1 MB/s limit is **per request per instance** — a new `RateLimiter` is created per download. Aggregate egress is unbounded. |
| Startup time | Fast — small context, no database connections, no external handshakes |
| Statelessness caveat | The filesystem is instance-local. Multiple replicas serving the same UUIDs require shared storage (NFS, EFS, S3) or the content will differ per instance. |

## Operational Readiness Gaps

| Gap | Severity | Detail |
|---|---|---|
| No executable JAR produced | Medium | `spring-boot-maven-plugin` missing |
| No externalized configuration | Low | Port, throttle rate, Actuator exposure, and log levels are all baked in or defaulted |
| Unsecured Actuator | Medium | Boot 1.x defaults with no restricting configuration — see [../analysis/security-patterns.md](../analysis/security-patterns.md) S-04 |
| No health check contract | Low | Actuator `/health` exists by default but no probe definitions reference it |
| No TLS | Medium | Must be terminated upstream; nothing in the repository records that expectation |
| No container definition | Low | Deployment target is undocumented |
| No CI pipeline | Medium | No automated build, test, or dependency-vulnerability gate |
| No metrics or tracing | Low | Only DEBUG/TRACE text logging |
| No request correlation | Low | No MDC or trace IDs |
| No graceful shutdown configuration | Low | In-flight throttled transfers could be severed on termination — material given that transfers can last minutes |
| Console-only logging | Low | Fine if stdout is collected by the platform, but no platform definition exists to confirm |

## Required Runtime Environment

| Requirement | Value |
|---|---|
| JVM | Java 8 compatible (current `maven-compiler-plugin` target is `1.8`) |
| Filesystem | Read access to the served content; currently only the application's own classpath `logback.xml` is reachable |
| Network | One inbound TCP port (8080 by default). **No outbound network access is required** — the service makes no external calls. |
| Memory | Modest. Streaming is used throughout; no file is buffered whole in memory. |
| CPU | Proportional to request rate, due to per-request SHA-512 hashing |

## Deployment Recommendations

Ordered by priority. All are additive — none change application behavior.

| Priority | Recommendation |
|---|---|
| High | Declare `spring-boot-maven-plugin` so a runnable executable JAR is produced |
| High | Remove the plaintext `http://repo.spring.io/milestone/` repository before any build pipeline is introduced (finding S-01) |
| Medium | Add `src/main/resources/application.yml` externalizing the port, log levels, throttle rate, and — critically — explicit Actuator endpoint exposure |
| Medium | Add a CI pipeline that builds, runs the 12 specifications, and gates on dependency vulnerabilities |
| Medium | Terminate TLS at a load balancer or API gateway, and document that expectation in the repository |
| Medium | Place authentication or an API gateway in front of the service, or explicitly document and accept the capability-URL model (finding S-05) |
| Low | Add a `Dockerfile` using a supported Corretto base image matching the upgraded JDK |
| Low | Configure graceful shutdown with a drain timeout long enough for throttled transfers |
| Low | Add readiness and liveness probe definitions referencing the Actuator health endpoint |
| Low | Bound concurrent downloads so the thread pool cannot be exhausted (finding S-08) |
| Low | If running multiple replicas, provide shared storage so all instances serve identical content |

These should be sequenced after the platform upgrade — see [../migration/component-order.md](../migration/component-order.md) Phase 4.

See [logging-configuration.md](logging-configuration.md) and [../diagrams/architecture.md](../diagrams/architecture.md).
