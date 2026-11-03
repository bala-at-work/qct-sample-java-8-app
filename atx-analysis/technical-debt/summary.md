# Technical Debt — Summary

[← Back to index](../README.md) · Root report: [../technical-debt-report.md](../technical-debt-report.md)

## Overview

`download-server` presents a clear split: **excellent application code on an end-of-life platform**. Of 16 identified debt items, 4 are High severity and all 4 are platform/supply-chain issues rather than code issues.

| Severity | Items |
|---|---|
| **High** | H1 Java 8 target · H2 Spring Boot 1.2.3 (EOL) · H3 Spring Framework 4.1.6 (EOL) · H4 plaintext `http://` Maven repository |
| **Medium** | M1 Guava 18.0 · M2 Commons IO 2.4 · M3 Commons Codec 1.10 (unused) · M4 cglib-nodep 3.1 at compile scope · M5 unsecured Actuator |
| **Low** | L1 dead `new Integer("1234")` · L2 unused/inconsistent `spring.version` property · L3 Groovy at compile scope · L4 outdated + duplicated test dependencies · L5 `maven-compiler-plugin` 3.0 · L6 `FileSystemStorage` is a stub · L7 unwired, package-squatting ETag filter |

Supporting findings: performance items P1–P6 in [../analysis/complexity-analysis.md](../analysis/complexity-analysis.md), security items S1–S12 in [../analysis/security-patterns.md](../analysis/security-patterns.md), anti-patterns A1–A11 in [../architecture/patterns.md](../architecture/patterns.md).

## By Category

### Outdated runtimes and frameworks (highest priority)
Java 1.8 compile target; Spring Boot 1.2.3.RELEASE; Spring Framework 4.1.6.RELEASE; Groovy 2.4.3; Spock 1.0. Nothing in this group receives security fixes. Detail: [outdated-components.md](outdated-components.md).

### Outdated dependencies
Guava 18.0 (critical to ETag/throttling/media types), Commons IO 2.4, Commons Codec 1.10, cglib-nodep 3.1, plus every transitive pinned by the Boot 1.2.3 BOM (embedded Tomcat, Jackson, Logback, SLF4J).

### Supply chain / build
Plaintext `http://repo.spring.io/milestone/` repository; ancient compiler plugin; **no Groovy compiler plugin**, so `src/test/groovy` is not compiled by the declared build; no dependency scanning, SBOM, or CI definition.

### Security
No authentication/authorization; Actuator exposed with Boot 1.x defaults; no TLS or security headers; probed `Content-Type` served inline without `nosniff`/`Content-Disposition`.

### Code quality and architecture
One dead statement; one unused dependency; two mis-scoped dependencies; one duplicated dependency; one unused property; checked exceptions flattened into `IllegalArgumentException`; unchecked `getResource()` dereferences; `FilePointer.modifiedAfter` named inversely to its behaviour; test class/file name mismatch; missing `@Override`.

### Performance
Full-file SHA-512 hash on every request with no ETag caching; per-request content-type probing; blocking throttled transfers occupy container threads; throttle is per-request so aggregate egress is uncapped.

### Operability and completeness
`FileSystemStorage` returns `logback.xml` for every UUID — there is no real storage backend. No `application.properties`, Dockerfile, IaC, or pipeline.

## What Is Healthy

Worth preserving through any migration:

- Ports-and-adapters design (`FileStorage`, `FilePointer`) that makes the storage backend swappable.
- Correct, non-trivial HTTP semantics: strong ETags, `If-None-Match`, `If-Modified-Since`, `304`, `HEAD` with headers and no body, `301` canonicalization, accurate `Content-Length`.
- Constant-memory streaming with a decorator-based throttle.
- Constructor injection, immutable types, `Optional` instead of `null` for absence.
- Low complexity: average cyclomatic complexity ≈ 1.1, maximum 4; no method over 12 lines.
- 12 behaviour-focused Spock specifications that serve directly as migration acceptance tests.

## Overall Assessment

**Debt level: High (platform), Low (code).** Effort to remediate: moderate and highly leveraged — a single-module Maven project with 652 lines of clean code and an existing behavioural test suite. Recommended action order is in [remediation-plan.md](remediation-plan.md); the recommended AWS transformation is in [../technical-debt-report.md](../technical-debt-report.md).
