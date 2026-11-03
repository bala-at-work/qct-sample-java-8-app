# Technical Debt Report — download-server

## 🎯 AWS Transformation Recommendation

### **RECOMMENDED TRANSFORMATIONS: AWS/java-version-upgrade**

The project is pinned to Java 8 (`maven-compiler-plugin` source/target `1.8`) on Spring Boot 1.2.3.RELEASE and Spring Framework 4.1.6.RELEASE — all long past end of open-source support — which is the single highest-severity risk in the repository. `AWS/java-version-upgrade` is the correct entry point because it raises the JDK to a supported LTS release while performing the accompanying Spring ecosystem and `javax`→`jakarta` namespace modernization that must land before any other work. `AWS/spring-boot-version-upgrade` is explicitly **not** recommended at this time: its scope begins at Spring Boot 3.x, and this project must first reach Spring Boot 3.x via the Java version upgrade before that transformation can execute.

---

- [Back to README](README.md)
- Detailed findings: [technical-debt/summary.md](technical-debt/summary.md) · [technical-debt/outdated-components.md](technical-debt/outdated-components.md) · [technical-debt/maintenance-burden.md](technical-debt/maintenance-burden.md) · [technical-debt/remediation-plan.md](technical-debt/remediation-plan.md)
- Supporting analysis: [analysis/tech-debt.md](analysis/tech-debt.md) · [analysis/dependency-analysis.md](analysis/dependency-analysis.md) · [analysis/security-patterns.md](analysis/security-patterns.md)

## Table of Contents
1. [Executive Summary](#executive-summary)
2. [Severity Dashboard](#severity-dashboard)
3. [Prioritized Critical Issues](#prioritized-critical-issues)
4. [What Is Healthy](#what-is-healthy)
5. [Navigation](#navigation)

## Executive Summary

This is a small (652 lines of application code), well-factored single-module Spring Boot application whose **code quality is good but whose entire platform is end-of-life**. There are no architectural knots to untangle and no database to migrate; the debt is concentrated almost entirely in the runtime and framework versions.

| Dimension | Assessment |
|---|---|
| Runtime / framework currency | 🔴 **High severity** — Java 8, Spring Boot 1.2.3, Spring 4.1.6 all EOL |
| Dependency currency | 🟠 **Medium severity** — every runtime library is many major/minor versions behind |
| Build & tooling currency | 🟡 **Low severity** — `maven-compiler-plugin` 3.0, Groovy 2.4, Spock 1.0 |
| Code quality & architecture | 🟡 **Low severity** — clean ports-and-adapters design; a handful of localized smells |
| Security posture | 🟠 **Medium severity** — plain-HTTP repository, unauthenticated actuator, unsecured endpoints |
| Test coverage | 🟠 **Medium severity** — strong controller specs, but the spec source root is not wired into the build |
| Operational readiness | 🟠 **Medium severity** — storage is a stub; no containerization or CI |

**Overall judgement:** modernization effort is **moderate** and dominated by version migration rather than rewriting. The small, interface-driven codebase and comprehensive Spock specification make the upgrade materially lower-risk than for a comparable legacy application.

## Severity Dashboard

| # | Finding | Severity | Category | Detail |
|---|---|---|---|---|
| 1 | Java 8 language/runtime target (EOL for free updates) | 🔴 High | Runtime | [outdated-components.md](technical-debt/outdated-components.md#1-javaruntime-version) |
| 2 | Spring Boot 1.2.3.RELEASE (EOL; released 2015) | 🔴 High | Framework | [outdated-components.md](technical-debt/outdated-components.md#2-spring-boot) |
| 3 | Spring Framework 4.1.6.RELEASE (EOL) | 🔴 High | Framework | [outdated-components.md](technical-debt/outdated-components.md#3-spring-framework) |
| 4 | Spring versions pinned explicitly, fighting the Boot parent BOM | 🔴 High | Framework | [outdated-components.md](technical-debt/outdated-components.md#4-explicit-spring-version-pinning) |
| 5 | Guava 18.0 (2014) | 🟠 Medium | Dependency | [outdated-components.md](technical-debt/outdated-components.md#runtime-dependencies) |
| 6 | commons-io 2.4, commons-codec 1.10, cglib-nodep 3.1 | 🟠 Medium | Dependency | [outdated-components.md](technical-debt/outdated-components.md#runtime-dependencies) |
| 7 | `groovy-all` 2.4.3 declared at **compile** scope | 🟠 Medium | Dependency | [outdated-components.md](technical-debt/outdated-components.md#scope-defects) |
| 8 | Plain-HTTP Maven repository `http://repo.spring.io/milestone/` | 🟠 Medium | Security | [security-patterns.md](analysis/security-patterns.md#supply-chain) |
| 9 | Actuator exposed with no authentication and no Spring Security | 🟠 Medium | Security | [security-patterns.md](analysis/security-patterns.md#exposure-surface) |
| 10 | `src/test/groovy` not registered as a test-source root | 🟠 Medium | Build | [maintenance-burden.md](technical-debt/maintenance-burden.md#build-configuration-gaps) |
| 11 | `FileSystemStorage` is a stub serving `logback.xml` for every UUID | 🟠 Medium | Completeness | [maintenance-burden.md](technical-debt/maintenance-burden.md#unfinished-production-code) |
| 12 | Spock 1.0 / Groovy 2.4 test stack | 🟡 Low | Dev dependency | [outdated-components.md](technical-debt/outdated-components.md#developer-and-build-dependencies) |
| 13 | `maven-compiler-plugin` 3.0 (2012) | 🟡 Low | Build tooling | [outdated-components.md](technical-debt/outdated-components.md#developer-and-build-dependencies) |
| 14 | `new Integer("1234")` in `MainApplication` — deprecated boxed constructor, dead assignment | 🟡 Low | Code quality | [tech-debt.md](analysis/tech-debt.md#code-level-findings) |
| 15 | Duplicate `spring-test` dependency declaration | 🟡 Low | Build hygiene | [tech-debt.md](analysis/tech-debt.md#build-hygiene) |
| 16 | Unused `spring.version` property (4.1.1) contradicting actual pins (4.1.6) | 🟡 Low | Build hygiene | [tech-debt.md](analysis/tech-debt.md#build-hygiene) |
| 17 | Dead code: `Sha512ShallowEtagHeaderFilter` never registered | 🟡 Low | Code quality | [tech-debt.md](analysis/tech-debt.md#dead-code) |
| 18 | Spec filename (`DownloadControllerTest.groovy`) differs from class name (`DownloadControllerSpec`) | 🟡 Low | Convention | [tech-debt.md](analysis/tech-debt.md#conventions) |
| 19 | SHA-512 hash computed over the whole file on every `FileSystemPointer` construction | 🟡 Low | Performance | [complexity-analysis.md](analysis/complexity-analysis.md#performance-hotspots) |
| 20 | Committed `.DS_Store`, no `.gitignore` | 🟡 Low | Hygiene | [maintenance-burden.md](technical-debt/maintenance-burden.md#repository-hygiene) |
| 21 | No CI/CD and no containerization | 🟡 Low | Operations | [maintenance-burden.md](technical-debt/maintenance-burden.md#missing-automation) |

## Prioritized Critical Issues

Findings are prioritized in the fixed order: **(1) EOL runtimes and frameworks → (2) outdated dependencies → (3) code quality and architecture.**

### Priority 1 — EOL/deprecated runtimes and frameworks (High)

Java 8, Spring Boot 1.2.3 and Spring Framework 4.1.6 no longer receive security patches from their upstream open-source projects. Any CVE discovered in Spring MVC 4.1.x or Boot 1.2.x will never be fixed for these versions, and the only remedy is a major upgrade. Spring Boot 1.x also predates the `javax`→`jakarta` namespace migration, Spring Security's modern DSL, and the Boot 2.x actuator/configuration model, which means an upgrade must be staged rather than attempted in one jump. This is the only finding class rated High and it blocks every other remediation.

### Priority 2 — Outdated dependencies (Medium)

Guava 18.0, commons-io 2.4, commons-codec 1.10 and cglib-nodep 3.1 all date from 2014–2015. They are pinned directly rather than being managed by the parent BOM, so they will not move when the Boot parent is upgraded. `groovy-all` at compile scope leaks a 7 MB test-only library into the production classpath. Additionally, the project resolves artifacts over plain HTTP from `repo.spring.io/milestone`, which is both a supply-chain exposure and a repository that no longer serves this content.

### Priority 3 — Code quality and architectural issues (Low)

The architecture is genuinely clean — `FileStorage`/`FilePointer` are proper ports with filesystem adapters, and HTTP concerns are isolated in `ExistingFile`. The remaining issues are small and local: a deprecated `new Integer(String)` call with a dead assignment, a never-registered ETag filter, a duplicate dependency declaration, a contradictory unused property, a filename/class-name mismatch, an eager full-file SHA-512 hash, and an unregistered Groovy test-source root that silently prevents the Spock specification from running.

## What Is Healthy

Recorded so that modernization does not accidentally regress it:

- **Ports and adapters.** `FileStorage` and `FilePointer` are narrow interfaces; `FileSystemStorage` and `FileSystemPointer` are the only filesystem-aware classes. Swapping to S3-backed storage requires no controller change.
- **Separation of HTTP concerns.** `DownloadController` does lookup and delegation only; all status/header decisions live in `ExistingFile`.
- **Correct HTTP semantics.** Conditional GET, HEAD, canonical-URL redirects and content-addressed ETags are implemented properly — better than most hand-rolled download endpoints.
- **Behavioral test coverage.** The 12-feature Spock specification pins the externally observable contract, which is exactly the safety net a version upgrade needs.
- **Profile-based bean selection.** `@Profile("!test")` / `@Profile("test")` guarantees exactly one `FileStorage` bean, avoiding ambiguous-bean failures.
- **Immutability.** Every field in `ExistingFile`, `FileSystemPointer` and `ThrottlingInputStream` is `final`.

## Navigation

- [technical-debt/summary.md](technical-debt/summary.md) — consolidated findings overview
- [technical-debt/outdated-components.md](technical-debt/outdated-components.md) — every outdated component with exact versions
- [technical-debt/maintenance-burden.md](technical-debt/maintenance-burden.md) — areas demanding ongoing attention
- [technical-debt/remediation-plan.md](technical-debt/remediation-plan.md) — prioritized, actionable remediation steps
- [migration/component-order.md](migration/component-order.md) — dependency-ordered migration sequence
- [migration/validation-criteria.md](migration/validation-criteria.md) — how to confirm a successful migration
