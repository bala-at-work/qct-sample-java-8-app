# Specialized Documentation

[← Back to index](../README.md)

Specialized concerns are documented only where actually detected in the repository. Detection results:

| Concern | Detected | Section |
|---|---|---|
| Database schemas / query patterns | **No** — no DB driver, ORM, JDBC, `persistence.xml`, migration scripts, or SQL in the repository | — |
| HTTP API | **Yes** | [1. HTTP API](#1-http-api) |
| UI components | **No** — no templates, static assets, JS/CSS, or view resolvers; the service returns raw bytes and JSON errors only | — |
| Message queues / event patterns | **No** — no JMS, Kafka, AMQP, SQS/SNS, or event bus | — |
| Infrastructure / deployment configuration | **Partial** | [2. Deployment & Runtime Configuration](#2-deployment--runtime-configuration) |
| Logging configuration | **Yes** | [3. Logging Configuration](#3-logging-configuration) |
| Caching | **Yes (HTTP-level only)** | [4. HTTP Caching Behaviour](#4-http-caching-behaviour) |
| Rate limiting / traffic shaping | **Yes** | [5. Bandwidth Throttling](#5-bandwidth-throttling) |
| Scheduled jobs / batch | **No** | — |
| Internationalization | **No** | — |

---

## 1. HTTP API

Full endpoint catalog with request/response examples is in [../reference/api-reference.md](../reference/api-reference.md). Summary:

| Method | Path | Success | Caching | Notes |
|---|---|---|---|---|
| `GET`, `HEAD` | `/download/{uuid}` | `301` | `304` short-circuit | `Location: /download/{uuid}/{originalName}` |
| `GET`, `HEAD` | `/download/{uuid}/{filename}` | `200` | `304` short-circuit | `{filename}` is cosmetic; bytes selected by UUID |
| auto-configured | Actuator endpoints | varies | — | Boot 1.x defaults, unsecured; no code references them |

No OpenAPI/Swagger descriptor exists in the repository; the API is defined solely by Spring MVC annotations.

### Media type negotiation
There is no `produces`/`consumes` declaration and no `Accept` handling. `Content-Type` is taken from `java.nio.file.Files.probeContentType` on the backing file and omitted entirely when probing fails (`ExistingFile.response` uses `ifPresent`). Request bodies are never read.

---

## 2. Deployment & Runtime Configuration

### Present
| Artifact | Purpose |
|---|---|
| `pom.xml` | Spring Boot fat-JAR build via the inherited `spring-boot-maven-plugin` |
| `mvnw` / `mvnw.cmd` / `.mvn/wrapper/maven-wrapper.properties` | Maven Wrapper 3.3.2 pinning Maven 3.9.8, `distributionType=only-script` |
| Spring profiles | `test` / `!test` select the `FileStorage` implementation — the only runtime switch in the system |

### Absent
No `application.properties` or `application.yml`; no Dockerfile or container build; no Kubernetes manifests; no CloudFormation, SAM, CDK, or Terraform; no CI/CD pipeline definition; no systemd unit or process manager config; no reverse-proxy or TLS configuration.

**Consequence**: every operational parameter — server port, thread pool sizing, Actuator exposure, TLS, logging destination beyond the console — runs on Spring Boot 1.2.3 defaults. There is no externalized configuration surface, so deployment tuning requires either JVM/`--` arguments at launch or adding a configuration file.

### Runtime profile selection
```
default (no profile)      ⇒ FileSystemStorage  (serves logback.xml for every UUID)
-Dspring.profiles.active=test ⇒ FileStorageStub (test fixture only)
```
Note that the default/production path is a stub. See [../technical-debt/maintenance-burden.md](../technical-debt/maintenance-burden.md).

---

## 3. Logging Configuration

`src/main/resources/logback.xml`

```xml
<configuration>
    <logger name="com"              level="INFO"/>
    <logger name="com.nurkiewicz"   level="ALL"/>
    <logger name="net"              level="INFO"/>
    <logger name="org"              level="INFO"/>
    <root level="DEBUG">
        <appender class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} | %thread | %-5level | %logger{1} | %msg%n</pattern>
            </encoder>
        </appender>
    </root>
</configuration>
```

| Aspect | Detail |
|---|---|
| Backend | Logback via SLF4J (supplied by `spring-boot-starter-web`) |
| Appenders | One `ConsoleAppender` only — no file, rolling, or remote appender |
| Levels | Application package at `ALL`; framework packages damped to `INFO`; root `DEBUG` |
| Pattern | Time (no date), thread, level, abbreviated logger, message |
| Statements in code | 5 total: `ExistingFile` DEBUG (serving) + 2 TRACE (redirect, `304`); `FileSystemStorage` DEBUG; `FileStorageStub` DEBUG |
| Sensitive data | None logged — only the UUID and the `FilePointer.toString()` summary (path, name, size, hash, media type) |

**Observations**: the `ALL` level on `com.nurkiewicz` plus a console-only appender is development-oriented. The appender is declared inline inside `<root>` rather than as a named top-level appender with an `<appender-ref>` — valid but unconventional. The pattern omits the date, which complicates multi-day log analysis. There is no access log and no correlation/trace identifier.

---

## 4. HTTP Caching Behaviour

This is the system's most substantive specialized behaviour; it is implemented in application code rather than delegated to a filter.

| Mechanism | Implementation |
|---|---|
| Validator generation | SHA-512 over the full file content, quoted (`FileSystemPointer.getEtag`) |
| `If-None-Match` | exact string equality against the current quoted ETag (`matchesEtag`); multi-value lists and weak `W/` validators are not parsed |
| `If-Modified-Since` | bound as `Optional<java.util.Date>`, converted via `Date::toInstant`, evaluated by `modifiedAfter` |
| Decision point | `ExistingFile.cached` — `etagMatch \|\| clientCopyCurrent` (short-circuit) |
| `304` response | `ETag`, `Content-Length`, `Last-Modified`, and `Content-Type` are all still sent; body is `null` |
| Cache-Control | **not set** — no `max-age`, `private`/`public`, or `no-store` directive is emitted |
| Server-side caching | **none** — no ETag cache, so the SHA-512 digest is recomputed on every request including `304`s |

An unwired alternative implementation exists: `Sha512ShallowEtagHeaderFilter` (test tree) overrides Spring's `ShallowEtagHeaderFilter` to use SHA-512 instead of MD5. It is not registered, and registering it would buffer entire responses in memory, conflicting with this service's streaming design.

---

## 5. Bandwidth Throttling

| Aspect | Detail |
|---|---|
| Mechanism | Guava `RateLimiter` wrapped by the `ThrottlingInputStream` decorator |
| Rate | `FileUtils.ONE_MB` permits/second ⇒ 1 MB/s, permits denominated in bytes |
| Scope | **Per request** — a new `RateLimiter` is created in `ExistingFile.buildResource`, so aggregate server egress is uncapped |
| Applied to | all three `read` overloads; `skip` is deliberately exempt (no bytes are delivered to the caller) |
| Blocking | `RateLimiter.acquire` blocks the servlet worker thread for the transfer duration |
| Configurability | hardcoded; not externalized to any property |

**Implications**: throughput per download is predictable, but concurrency is bounded by the container thread pool rather than by bandwidth, and total egress cannot be limited. A shared `RateLimiter` plus async dispatch (`StreamingResponseBody`) would address both. See P3/P4 in [../analysis/complexity-analysis.md](../analysis/complexity-analysis.md).
