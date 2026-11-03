# Technical Debt Report

[← Back to index](README.md)

## 🎯 AWS Transformation Recommendation

### **RECOMMENDED TRANSFORMATIONS: AWS/java-version-upgrade**

The project compiles against Java 1.8 (`maven-compiler-plugin` source/target `1.8`) and is built on Spring Boot 1.2.3.RELEASE / Spring Framework 4.1.x, all of which are long past end of life and receive no security fixes. `AWS/java-version-upgrade` is the right fit because it upgrades the JDK target to a supported LTS release *and* performs the accompanying dependency modernization (Spring ecosystem, Jakarta EE namespace, build plugins) that this single-module Maven project requires. `AWS/spring-boot-version-upgrade` is not applicable yet — it targets Spring Boot 3.x → 4.x, so it only becomes relevant after the Java/Spring Boot upgrade above lands the project on Spring Boot 3.x.

---

## Executive Summary

`download-server` is a small (652 LOC), well-factored single-module Spring Boot service whose *code quality* is good but whose *platform* is severely outdated. Every production dependency is roughly a decade behind current releases, and the two framework generations in use (Spring Boot 1.x, Spring Framework 4.1) are end-of-life with no security backports. The functional debt is small and localized; the platform debt is large and blocks everything else.

| Severity | Count | Theme |
|---|---|---|
| High | 4 | EOL Java 8 target, EOL Spring Boot 1.2.3, EOL Spring Framework 4.1.6, insecure `http://` Maven repository |
| Medium | 5 | Outdated Guava 18.0, Commons IO 2.4, Commons Codec 1.10, cglib-nodep 3.1, unsecured Actuator endpoints |
| Low | 7 | Outdated Spock/Groovy test stack, Maven compiler plugin 3.0, unused `spring.version` property, dead `new Integer(...)` statement, stub storage implementation, compile-scope Groovy, test-tree class in `org.springframework` package |

## Prioritized Findings

### High — EOL / deprecated / outdated runtimes and frameworks

| # | Finding | Evidence | Impact |
|---|---|---|---|
| H1 | **Java 8 compile target.** Java 8 public updates ended; no access to modern language or JVM features, and modern libraries increasingly require Java 17+. | `pom.xml` `<source>1.8</source><target>1.8</target>` | Blocks all dependency modernization; growing security exposure |
| H2 | **Spring Boot 1.2.3.RELEASE (EOL).** Spring Boot 1.x reached end of support long ago; no CVE fixes. | `pom.xml` parent + starters | Unpatched framework; incompatible with current tooling |
| H3 | **Spring Framework 4.1.6.RELEASE (EOL).** Known CVEs exist in the 4.x line; `javax.*` servlet namespace only. | all `org.springframework:spring-*` deps | Unpatched web stack |
| H4 | **Insecure plaintext Maven repository.** `http://repo.spring.io/milestone/` is fetched over unencrypted HTTP, and milestone repositories should not be used for releases. | `pom.xml` `<repositories>` | Supply-chain / MITM risk at build time |

### Medium — Outdated runtime dependencies

| # | Finding | Evidence | Impact |
|---|---|---|---|
| M1 | Guava 18.0 (very old; core to hashing, `RateLimiter`, `MediaType`) | `pom.xml` | Missing fixes; API drift on upgrade (`MediaType.charset().transform(...)` removed in later versions) |
| M2 | Apache Commons IO 2.4 | `pom.xml` | Used only for the `ONE_MB` constant — easily removable |
| M3 | Apache Commons Codec 1.10 | `pom.xml` | **Unused** by any source file; dead dependency |
| M4 | cglib-nodep 3.1 at compile scope | `pom.xml` | Only needed by Spock mocking; wrong scope, old version |
| M5 | Spring Boot Actuator exposed without any security configuration | `spring-boot-starter-actuator` with no `SecurityConfig` anywhere in the tree | Operational/system information disclosure |

### Low — Code quality and architectural issues

| # | Finding | Evidence |
|---|---|---|
| L1 | Dead, deprecated allocation: `Integer temp = new Integer("1234");` never used | `MainApplication.java:10` |
| L2 | `spring.version` property declared but never referenced; Spring versions hardcoded and inconsistent with it (4.1.1 vs 4.1.6) | `pom.xml` |
| L3 | Groovy 2.4.3 declared at **compile** scope though used only by tests | `pom.xml` |
| L4 | Spock 1.0-groovy-2.4 / Spring Test 4.1.6 — outdated test stack; `spring-test` declared twice | `pom.xml` |
| L5 | `maven-compiler-plugin` 3.0 (ancient) | `pom.xml` |
| L6 | `FileSystemStorage` is a stub returning `logback.xml` for every UUID — no real storage backend | `FileSystemStorage.java:13-30` |
| L7 | `Sha512ShallowEtagHeaderFilter` placed in the `org.springframework.web.filter` package (package-squatting to access a protected method) and appears unwired | `src/test/java/org/springframework/web/filter/Sha512ShallowEtagHeaderFilter.java` |

## Detail & Navigation

- [technical-debt/summary.md](technical-debt/summary.md) — consolidated findings overview
- [technical-debt/outdated-components.md](technical-debt/outdated-components.md) — per-dependency version analysis and EOL status
- [technical-debt/maintenance-burden.md](technical-debt/maintenance-burden.md) — where effort concentrates
- [technical-debt/remediation-plan.md](technical-debt/remediation-plan.md) — ordered action items
- [analysis/tech-debt.md](analysis/tech-debt.md) — assessment cross-reference
- [analysis/security-patterns.md](analysis/security-patterns.md) — security posture

## Recommended Next Steps (regardless of transformation choice)

1. Run `AWS/java-version-upgrade` targeting a supported LTS JDK (Java 21), allowing it to carry Spring Boot/Spring Framework to a supported generation and migrate `javax.*` → `jakarta.*`.
2. Replace `http://repo.spring.io/milestone/` with HTTPS, or delete the repository entirely (nothing in the dependency list requires a milestone build).
3. Remove the unused `commons-codec` dependency and the dead `new Integer("1234")` statement.
4. Move `groovy-all` and `cglib-nodep` to `test` scope; de-duplicate `spring-test`.
5. Add explicit security configuration for Actuator endpoints before any non-local deployment.
6. Replace the `FileSystemStorage` stub with a real storage backend (e.g., Amazon S3 via AWS SDK for Java v2) if this service is intended for production use.
