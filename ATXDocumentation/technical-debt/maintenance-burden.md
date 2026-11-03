# Maintenance Burden

- [Back to README](../README.md) · [Root report](../technical-debt-report.md)
- Siblings: [summary.md](summary.md) · [outdated-components.md](outdated-components.md) · [remediation-plan.md](remediation-plan.md)

## Table of Contents
1. [How to Read This](#how-to-read-this)
2. [Build Configuration Gaps](#build-configuration-gaps)
3. [Unfinished Production Code](#unfinished-production-code)
4. [Framework-Coupled Code](#framework-coupled-code)
5. [Hard-Coded Configuration](#hard-coded-configuration)
6. [Dead and Misplaced Code](#dead-and-misplaced-code)
7. [Missing Automation](#missing-automation)
8. [Repository Hygiene](#repository-hygiene)
9. [Low-Burden Areas](#low-burden-areas)

## How to Read This

This file records the parts of the codebase that will consume disproportionate effort during maintenance or modernization, independent of their raw size. Severity uses **High / Medium / Low** only; effort is described qualitatively.

## Build Configuration Gaps

### `src/test/groovy` is not a declared source root — 🟠 Medium
The Spock specification lives at `src/test/groovy/com/nurkiewicz/download/DownloadControllerTest.groovy`, but the POM declares **only** `maven-compiler-plugin:3.0`. There is no `gmavenplus-plugin`, no `groovy-eclipse-compiler`, and no `build-helper-maven-plugin` adding `src/test/groovy` to the test-source roots.

**Consequence.** The project's entire behavioral safety net — 12 feature methods covering the full HTTP contract — is silently not compiled or executed by a default Maven build. The test suite exists but does not run. This is the single highest-leverage maintenance problem in the repository, because it removes the verification mechanism that a framework upgrade depends on.

**Burden.** Low effort to fix, high value. Add a Groovy compilation plugin (or move the spec under a configured source root) before any upgrade work begins.

### No surefire configuration — 🟡 Low
Test execution relies entirely on the parent POM's surefire defaults. Once the Boot parent is upgraded to a JUnit 5 era (`spring-boot-starter-test` 2.2+), the Spock 1.x engine will not be discovered by the JUnit Platform provider, so test execution must be re-established as part of the upgrade rather than assumed.

### Language level hidden in plugin configuration — 🟡 Low
The Java version is set only inside the `maven-compiler-plugin` `<configuration>` block; no `<java.version>` property exists. Tools and humans that look for the conventional Boot property will not find it, and the upgrade must edit the plugin block directly.

## Unfinished Production Code

### `FileSystemStorage` is an acknowledged stub — 🟠 Medium
```java
/**
 * Stub implementation that for any UUID returns logback.xml file.
 * Replace with real implementation.
 */
@Component
@Profile("!test")
class FileSystemStorage implements FileStorage { ... }
```
`findFile(UUID uuid)` ignores its argument entirely and resolves the classpath resource `/logback.xml`.

**Consequence.** Outside the `test` profile the application is not functional as a download server: every UUID returns the application's own logging configuration. There is also no upload path, no storage directory configuration, and no UUID→file mapping anywhere in the codebase.

**Burden.** Significant and open-ended — implementing real storage is a feature, not a refactor. Any modernization plan must explicitly declare whether this is in scope. The good news is that `FileStorage` is a one-method interface, so the blast radius of implementing it is confined to a single new class.

### No persistence of file metadata — 🟠 Medium
UUIDs are the public addressing scheme but nothing stores them. A real implementation needs a metadata store (the natural AWS shape being S3 for bytes plus DynamoDB for the UUID→object mapping), which currently has no place to live in the architecture.

## Framework-Coupled Code

### `Sha512ShallowEtagHeaderFilter` lives in a Spring package — 🟠 Medium
The class is declared in package `org.springframework.web.filter` purely so it can override the **protected** method `generateETagHeaderValue(byte[])`. This is package-injection into a third-party namespace.

**Consequence.** The class is coupled to a Spring internal signature that has since changed (the current hook takes an `InputStream`). It will fail to compile after a Spring upgrade, and because it sits in a foreign package it is easy to miss when auditing project code. It is also never registered, so it provides no value today.

**Burden.** Low effort — delete it. Recreate against the current API only if a SHA-512 shallow ETag filter is actually wanted.

### `ExistingFile` concentrates all HTTP protocol knowledge — 🟡 Low
Ten private methods in one 121-line class encode the entire conditional-GET/redirect/throttle decision tree. This is good cohesion, not a defect, but it does mean `ExistingFile` is the one file where a Spring MVC behavioral change will surface. Expect every upgrade step to require attention here: `ResponseEntity` builder APIs, `HttpHeaders` date handling, and `MediaType` conversion have all evolved since Spring 4.1.

## Hard-Coded Configuration

No `application.properties` or `application.yml` exists anywhere in the project. Every tunable is a literal in source or a framework default — 🟡 **Low** severity individually, but collectively it means the application cannot be configured per environment.

| Value | Hard-coded in | Should be |
|---|---|---|
| 1 MB/s throttle rate | `ExistingFile` (`RateLimiter.create(FileUtils.ONE_MB)`) | Externalized property |
| Served file path `/logback.xml` | `FileSystemStorage` | Storage root configuration |
| ETag hash algorithm (SHA-512) | `FileSystemPointer` | Acceptable as a constant, but see performance note |
| Redirect path template `/download/{uuid}/{name}` | `ExistingFile` | Acceptable |
| Server port, actuator exposure, logging levels | Framework defaults / `logback.xml` | Explicit configuration file |

## Dead and Misplaced Code

| Item | Issue | Severity |
|---|---|---|
| `Sha512ShallowEtagHeaderFilter` | Never registered as a bean or servlet filter; unreachable | 🟡 Low |
| `Integer temp = new Integer("1234");` in `MainApplication` | Assigned, never read; uses a constructor deprecated for removal | 🟡 Low |
| `<spring.version>4.1.1.RELEASE</spring.version>` property | Declared, never referenced, and contradicts the 4.1.6 pins actually used | 🟡 Low |
| `commons-codec` dependency | Declared; no usage found in any source file | 🟡 Low |
| Duplicate `spring-test` declaration | Same GAV declared twice | 🟡 Low |
| `DownloadControllerTest.groovy` contains `DownloadControllerSpec` | Filename/class-name mismatch hinders navigation and some tooling | 🟡 Low |

## Missing Automation

| Missing | Severity | Consequence |
|---|---|---|
| CI pipeline (no `.github/`, Jenkinsfile, `buildspec.yml`) | 🟡 Low | No automated build, test, or dependency-vulnerability gate. Regressions are caught only by whoever runs Maven locally — and today that run does not execute the Spock spec at all. |
| Container image (no Dockerfile) | 🟡 Low | No reproducible runtime; deployment model is undocumented. Blocks ECS/EKS/App Runner targets. |
| Infrastructure as code | 🟡 Low | No CloudFormation/CDK/Terraform; nothing describes where this runs. |
| Dependency scanning | 🟡 Low | Given an all-EOL dependency set, the absence of automated CVE scanning is a compounding risk. Git history shows Dependabot upgrade merges were later reverted by `73556be reset pom.xml to original version`. |
| Code coverage reporting | 🟡 Low | No JaCoCo; coverage of the Spock spec is unmeasured. |
| `.gitignore` | 🟡 Low | Build output and OS artifacts are not excluded — `.DS_Store` is already committed. |

## Repository Hygiene

- `/repo/.DS_Store` (6148 bytes) is committed — a macOS Finder artifact with no business in version control.
- No `.gitignore`, so `target/` and IDE files are at permanent risk of being committed.
- `README.md` is two lines and describes the repository only as a transformation sample; it documents neither the endpoints, nor how to run the app, nor the stub-storage caveat.
- Git history ends with a deliberate revert of Dependabot's Spring upgrades, confirming the EOL versions are intentional for sample purposes rather than accidental.

## Low-Burden Areas

These require little ongoing attention and should be preserved as-is through modernization:

| Component | Why it is low burden |
|---|---|
| `FileStorage` | Single-method interface; stable contract |
| `FilePointer` | Pure query interface; 8 methods, no side effects |
| `ThrottlingInputStream` | Self-contained `InputStream` decorator with no framework coupling; portable across any Java version |
| `FileSystemPointer` | Only filesystem-aware class; uses standard `java.io`/`java.nio`/`java.time` plus Guava hashing |
| `DownloadController` | Thin delegation layer; two endpoints, one private helper |
| `logback.xml` | Small, standard configuration |
