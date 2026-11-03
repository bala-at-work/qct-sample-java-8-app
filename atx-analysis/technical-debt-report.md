# Technical Debt Report

[← Back to index](README.md)

## 🎯 AWS Transformation Recommendation

### **RECOMMENDED TRANSFORMATIONS: AWS/java-version-upgrade**

The project targets Java 8 (`maven-compiler-plugin` `<source>/<target>` = `1.8`) and runs on Spring Boot `1.2.3.RELEASE` / Spring Framework `4.1.6.RELEASE`, all of which are long past end of life. `AWS/java-version-upgrade` raises the JDK to a supported LTS release and performs the accompanying Spring ecosystem, Jakarta namespace, and dependency modernization that must land before any other upgrade path becomes viable. `AWS/spring-boot-version-upgrade` is deliberately **not** recommended: its scope starts at Spring Boot 3.x, and this repository must first be brought onto a supported JDK and Spring Boot 3.x baseline.

---

## Executive Summary

`download-server` is a small (652 LOC), cleanly structured single-module Spring Boot service. Its *code quality* debt is modest; its *platform currency* debt is severe. Every runtime and framework version in the build is end-of-life and no longer receives security patches, and the build resolves one repository over plaintext HTTP.

| Severity | Count | Theme |
|---|---|---|
| **High** | 5 | EOL Java 8 runtime, EOL Spring Boot 1.2.3, EOL Spring Framework 4.1.6, insecure HTTP Maven repository, unimplemented production storage backend |
| **Medium** | 5 | Outdated runtime dependencies: Guava 18.0, Commons IO 2.4, Commons Codec 1.10, Groovy 2.4.3 on the compile classpath, `maven-compiler-plugin` 3.0 |
| **Low** | 7 | Deprecated `new Integer(String)` and dead local, missing `@Override`, hardcoded throttle rate, unclosed stream on `304`/`HEAD` paths, test-scope class in a `org.springframework` package, duplicate `spring-test` declaration |

## Prioritized Findings

### 🔴 High — EOL / deprecated / outdated runtimes and frameworks

1. **Java 8 is end of life for mainstream support.** `pom.xml` pins `maven-compiler-plugin` `<source>1.8</source><target>1.8</target>`. No `<java.version>` property is set. Blocks modern language features, performance improvements, and current security baselines.
2. **Spring Boot `1.2.3.RELEASE` (2015) is end of life.** Declared both as `<parent>` and repeated explicitly on each `spring-boot-starter-*` dependency. No security patches; incompatible with current starters, Actuator endpoint model, and configuration property names.
3. **Spring Framework `4.1.6.RELEASE` is end of life.** Pinned directly on nine `org.springframework` artifacts, overriding the Boot-managed versions. Pre-Jakarta (`javax.servlet`) namespace; cannot run on a modern servlet container.
4. **Insecure plaintext Maven repository.** `pom.xml` declares `<url>http://repo.spring.io/milestone/</url>` over HTTP. Dependency resolution is subject to tampering; Spring's repositories also no longer serve plaintext HTTP, so this is both a supply-chain risk and a latent build failure.
5. **Production storage backend is a stub.** `FileSystemStorage.findFile(UUID)` ignores the UUID and returns `/logback.xml` for every request — the class's own Javadoc says "Replace with real implementation." The service has no usable production behavior.

### 🟠 Medium — Outdated runtime/production dependencies

| Dependency | Declared version | Notes |
|---|---|---|
| `com.google.guava:guava` | `18.0` | Many major releases behind; used for hashing, `RateLimiter`, `MediaType`. `RateLimiter` and `MoreObjects` remain `@Beta`/stable-ish but the API has moved on. |
| `commons-io:commons-io` | `2.4` | Only `FileUtils.ONE_MB` is actually used. |
| `commons-codec:commons-codec` | `1.10` | No usage found in source — likely removable. |
| `org.codehaus.groovy:groovy-all` | `2.4.3` | Declared at **compile** scope although Groovy is used only by tests. `org.codehaus.groovy` has been superseded by `org.apache.groovy`. |
| `cglib:cglib-nodep` | `3.1` | Legacy Spock/proxy support dependency. |

### 🟡 Low — Code quality and architectural issues

1. `MainApplication.main` calls the deprecated constructor `new Integer("1234")` and assigns it to an unused local `temp` — dead code plus a deprecated API. (`MainApplication.java:10`)
2. `maven-compiler-plugin` pinned to `3.0` (2012), far behind the Boot-parent-managed version.
3. `FileStorageStub.findFile` is missing `@Override`.
4. Throttle rate is hardcoded to `ONE_MB` inside `ExistingFile.buildResource` — not externalized to configuration.
5. `InputStream` returned by `FilePointer.open()` is wrapped in an `InputStreamResource` and left to the container to close; on the `HEAD` path `resourceToReturn` returns `null` without ever opening a stream (safe), but there is no explicit lifecycle management for error paths.
6. `Sha512ShallowEtagHeaderFilter` lives in the `org.springframework.web.filter` package under `src/test/java` — package-squatting a third-party namespace to access a protected method, and it is never registered anywhere.
7. `spring-test:4.1.6.RELEASE` is declared twice in `pom.xml`.
8. The Spock file is named `DownloadControllerTest.groovy` but declares `class DownloadControllerSpec` — a filename/class mismatch that prevents the Groovy source from compiling as-is.

## Detailed Sections

- [technical-debt/summary.md](technical-debt/summary.md) — full inventory with severity ratings
- [technical-debt/outdated-components.md](technical-debt/outdated-components.md) — version-by-version EOL analysis
- [technical-debt/maintenance-burden.md](technical-debt/maintenance-burden.md) — hotspots requiring attention
- [technical-debt/remediation-plan.md](technical-debt/remediation-plan.md) — prioritized action items
- [analysis/tech-debt.md](analysis/tech-debt.md) — assessment cross-referenced to source locations
- [analysis/security-patterns.md](analysis/security-patterns.md) — security posture
