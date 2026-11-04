# Modules

- [Back to README](../README.md)
- Siblings: [program-structure.md](program-structure.md) · [interfaces.md](interfaces.md) · [data-models.md](data-models.md) · [api-reference.md](api-reference.md)

## Table of Contents
1. [Build Module Structure](#build-module-structure)
2. [Source Roots](#source-roots)
3. [Logical Modules](#logical-modules)
4. [Logical Module Dependencies](#logical-module-dependencies)
5. [Java Module System](#java-module-system)
6. [Packaging](#packaging)
7. [Modularization Assessment](#modularization-assessment)

## Build Module Structure

**Single-module Maven project.** There is no `<modules>` section, no aggregator POM, and no submodule directory.

| Property | Value |
|---|---|
| Artifact | `com.nurkiewicz.download:download-server:0.0.1-SNAPSHOT` |
| Parent | `org.springframework.boot:spring-boot-starter-parent:1.2.3.RELEASE` (`<relativePath/>` empty — resolved from the repository, not the filesystem) |
| Packaging | `jar` (inherited default) |
| Submodules | none |
| Profiles | none declared |
| Properties | `project.build.sourceEncoding=UTF-8`, `spring.version=4.1.1.RELEASE` (unused) |

## Source Roots

| Root | Registered in the build? | Contents |
|---|---|---|
| `src/main/java` | ✅ Maven standard | 8 Java types in `com.nurkiewicz.download` |
| `src/main/resources` | ✅ Maven standard | `logback.xml` |
| `src/test/java` | ✅ Maven standard | 3 Java types (2 in `com.nurkiewicz.download`, 1 in `org.springframework.web.filter`) |
| `src/test/resources` | ✅ Maven standard | `download.txt` |
| `src/test/groovy` | ❌ **not registered** | `DownloadControllerTest.groovy` (class `DownloadControllerSpec`) |

**The unregistered Groovy root is the most consequential structural defect in the project.** The POM declares only `maven-compiler-plugin:3.0`; there is no `gmavenplus-plugin`, no `groovy-eclipse-compiler`, and no `build-helper-maven-plugin` adding the directory. The result is that the project's entire behavioral test suite — 12 Spock feature methods that pin the complete HTTP contract — is neither compiled nor executed by a default Maven build. See [../technical-debt/maintenance-burden.md](../technical-debt/maintenance-burden.md#build-configuration-gaps).

## Logical Modules

The code has no sub-package structure, but it does have clear logical groupings implied by the ports-and-adapters design. These are the natural seams if the project is ever decomposed into packages or modules.

### M1 — Bootstrap
| Property | Value |
|---|---|
| Types | `MainApplication` |
| Responsibility | Spring Boot startup and component scanning |
| Depends on | Spring Boot |
| Suggested package | `com.nurkiewicz.download` (root) |

### M2 — Web adapter
| Property | Value |
|---|---|
| Types | `DownloadController` |
| Responsibility | HTTP request binding, UUID lookup, 404 decision |
| Depends on | M3 (`ExistingFile`), M4 (`FileStorage` port), Spring MVC |
| Suggested package | `…download.web` |

### M3 — Protocol policy
| Property | Value |
|---|---|
| Types | `ExistingFile`, `ThrottlingInputStream` |
| Responsibility | Status-code and header decisions (304/301/200), HEAD body suppression, bandwidth throttling |
| Depends on | M4 (`FilePointer` port), Spring Web, Guava, Commons IO |
| Suggested package | `…download.http` |

### M4 — Ports
| Property | Value |
|---|---|
| Types | `FileStorage`, `FilePointer` |
| Responsibility | Define the storage contract |
| Depends on | JDK, Guava (`MediaType` appears in `FilePointer`'s signature) |
| Suggested package | `…download.storage` |

### M5 — Filesystem adapter
| Property | Value |
|---|---|
| Types | `FileSystemStorage`, `FileSystemPointer` |
| Responsibility | Implement the ports against the local filesystem and classpath |
| Depends on | M4, JDK I/O, Guava hashing, Spring Context (`@Component`, `@Profile`) |
| Suggested package | `…download.storage.fs` |

### M6 — Test support
| Property | Value |
|---|---|
| Types | `FileStorageStub`, `FileExamples`, `DownloadControllerSpec`, `Sha512ShallowEtagHeaderFilter` |
| Responsibility | Fixtures, stub adapter, specification |
| Depends on | M1, M4, M5, Spock, Spring Test |
| Note | `Sha512ShallowEtagHeaderFilter` is unused dead code and sits in a third-party package |

## Logical Module Dependencies

```
        M1 Bootstrap
             │ (component scan)
             ▼
        M2 Web adapter ────────────┐
             │                     │
             ▼                     ▼
        M3 Protocol policy ───► M4 Ports ◄──── M5 Filesystem adapter
                                   ▲                    │
                                   └────────────────────┘
                                       (implements)

        M6 Test support ──► M1, M4, M5
```

| From | To | Via |
|---|---|---|
| M1 | M2, M5 | Component scanning (runtime, not compile-time) |
| M2 | M3 | `new ExistingFile(...)` |
| M2 | M4 | `FileStorage` constructor injection |
| M3 | M4 | `FilePointer` field and parameters |
| M5 | M4 | `implements FileStorage` / `implements FilePointer` |
| M6 | M1, M4, M5 | Context configuration, stub implementation, fixtures |

**Properties.** The graph is acyclic. M4 (Ports) is the stable centre with the highest fan-in and zero dependencies on other logical modules. M2 and M3 never reference M5 — dependency inversion is genuinely observed, not merely declared. This is why the test suite can replace the whole storage side with a profile switch.

## Java Module System

**Not used.** There is no `module-info.java` anywhere in the project, which is expected for a Java 8 target (JPMS arrived in Java 9).

When the JDK is upgraded, JPMS adoption remains **optional** — Spring Boot applications conventionally run on the classpath rather than the module path. If modularization were pursued later, the M1–M5 groupings above are the natural module boundaries, with M4 (Ports) exporting the two interfaces and M5 remaining internal.

Relevant to the upgrade: there are **no `sun.*`, `com.sun.*`, or other JDK-internal API usages** anywhere in the codebase, so no strong-encapsulation breakage is expected when moving past Java 8.

## Packaging

| Aspect | Current state |
|---|---|
| Declared packaging | `jar` (inherited from the Boot parent) |
| `spring-boot-maven-plugin` | **Not declared** in `<build><plugins>` — only plugin *management* is inherited from the parent |
| Consequence | Without an explicit plugin declaration, `mvn package` produces a plain JAR rather than an executable fat JAR. The application cannot be launched with `java -jar` as built |
| Fat-JAR layout | Not applicable |
| WAR support | None — no `src/main/webapp`, no `SpringBootServletInitializer` |
| Container image | None — no Dockerfile |
| Native image | Not applicable to this Spring generation |

This is a real build gap: the project declares a Boot application but does not configure Boot's repackaging. Adding `spring-boot-maven-plugin` to the build section is part of making the artifact deployable.

## Modularization Assessment

| Dimension | Assessment |
|---|---|
| **Granularity** | A single Maven module is correct for 652 lines and one capability. Splitting the build would add ceremony with no benefit. |
| **Package structure** | Flat — all 8 main types share one package. Acceptable at this size, but it hides the M1–M5 boundaries that the design actually honours. Sub-packages (`web`, `http`, `storage`, `storage.fs`) would make the architecture visible in the directory tree and let the compiler enforce it via package-private visibility. |
| **Cohesion** | High. Each type has one clear responsibility; `ExistingFile` is the largest at 121 lines and is cohesively about HTTP response policy. |
| **Coupling** | Low and correctly directed. Two leaks are worth noting: Guava's `MediaType` in the `FilePointer` port and Guava's `RateLimiter` in `ThrottlingInputStream`'s public constructor place third-party types on abstraction boundaries. |
| **Encapsulation** | Weak in one respect — `FileSystemPointer` and `ExistingFile` are `public` though neither is intended for external use. With sub-packages they could be tightened. |
| **Test isolation** | Excellent in design: the profile-based stub replaces the whole storage hexagon. Undermined entirely in practice by the unregistered Groovy source root. |
| **Build correctness** | Two defects: the unregistered Groovy test root, and the missing `spring-boot-maven-plugin` declaration. |
| **Extensibility** | Strong. Adding an S3 storage adapter requires one new class pair implementing M4's interfaces, with no change to M1, M2 or M3. |

**Recommendation.** Keep the single Maven module. Fix the two build defects (register `src/test/groovy`, declare `spring-boot-maven-plugin`). Consider introducing the M2–M5 sub-packages only if the codebase grows — at the current size the flat package costs little, and the modernization effort is better spent on the version upgrades in [../technical-debt/remediation-plan.md](../technical-debt/remediation-plan.md).
