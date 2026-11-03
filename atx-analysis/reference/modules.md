# Modules

[← Back to index](../README.md) · [Program structure](program-structure.md)

## Build Modules

The project is a **single Maven module**. There is no `<modules>` section in `pom.xml` and no nested POMs.

| Coordinate | Packaging | Parent |
|---|---|---|
| `com.nurkiewicz.download:download-server:0.0.1-SNAPSHOT` | jar (default) | `org.springframework.boot:spring-boot-starter-parent:1.2.3.RELEASE` |

Consequence for modernization: no inter-module dependency ordering has to be planned. Any upgrade is a single-POM change.

## Source Roots

| Root | Language | Convention | Files |
|---|---|---|---|
| `src/main/java` | Java | Maven standard | 8 |
| `src/main/resources` | — | Maven standard | 1 (`logback.xml`) |
| `src/test/java` | Java | Maven standard | 3 |
| `src/test/groovy` | Groovy | **Non-standard for plain Maven** | 1 |
| `src/test/resources` | — | Maven standard | 1 (`download.txt`) |

> `src/test/groovy` is not compiled by the default Maven lifecycle. No `gmavenplus-plugin`, `groovy-eclipse-compiler`, or `build-helper-maven-plugin` is configured in `pom.xml`, so the Spock specification has no configured compiler. This is a structural gap in the build definition, compounded by the filename/class-name mismatch (TD-18).

## Package Organization

### `com.nurkiewicz.download` (main) — 8 types
The entire production codebase lives in one flat package. Given 8 types and ~380 production LOC, this is appropriate — subdividing would add ceremony without clarifying anything.

Logical groupings within the package:

| Group | Types | Role |
|---|---|---|
| Bootstrap | `MainApplication` | Context startup, component-scan root |
| Delivery | `DownloadController` | HTTP mapping and binding |
| Domain | `ExistingFile`, `ThrottlingInputStream` | Caching policy, response assembly, rate limiting |
| Ports | `FileStorage`, `FilePointer` | Framework-free abstractions |
| Adapters | `FileSystemStorage`, `FileSystemPointer` | Filesystem implementations |

The package is also the component-scan root: `@SpringBootApplication` on `MainApplication` scans `com.nurkiewicz.download` and below, which is how `FileSystemStorage` and (in tests) `FileStorageStub` are discovered.

### `com.nurkiewicz.download` (test) — 2 types
`FileExamples`, `FileStorageStub`. Deliberately the same package as production code so that the test stub is picked up by the production component scan when the `test` profile is active, and so `FileExamples` can construct a `FileSystemPointer` without widening its visibility.

### `com.nurkiewicz.download` (test, Groovy) — 1 type
`DownloadControllerSpec`, in `src/test/groovy`.

### `org.springframework.web.filter` (test) — 1 type
`Sha512ShallowEtagHeaderFilter`. A project class placed in a **third-party package** purely to access the `protected generateETagHeaderValue` hook on `ShallowEtagHeaderFilter`. Package squatting; see TD-17 in [../technical-debt/summary.md](../technical-debt/summary.md).

## Package Dependency Graph

```
com.nurkiewicz.download (main)
  ├─▶ org.springframework.boot                 (SpringApplication, @SpringBootApplication)
  ├─▶ org.springframework.beans.factory.annotation  (@Autowired)
  ├─▶ org.springframework.context.annotation   (@Profile)
  ├─▶ org.springframework.stereotype           (@Component)
  ├─▶ org.springframework.core.io              (Resource, InputStreamResource)
  ├─▶ org.springframework.http                 (ResponseEntity, HttpMethod, HttpStatus, MediaType, HttpHeaders)
  ├─▶ org.springframework.web.bind.annotation  (@RestController, @RequestMapping, @PathVariable, @RequestHeader)
  ├─▶ com.google.common.{hash,io,net,base,util.concurrent}
  ├─▶ org.apache.commons.io                    (FileUtils.ONE_MB)
  ├─▶ org.slf4j                                (Logger, LoggerFactory)
  └─▶ java.{io,net,nio.file,time,util,util.function}

com.nurkiewicz.download (test)
  ├─▶ com.nurkiewicz.download (main)           — same package, direct access
  ├─▶ org.springframework.{context.annotation,stereotype,test.*,web.context}
  ├─▶ com.google.common.net                    (HttpHeaders)
  ├─▶ spock.lang                               (Specification)
  └─▶ java.{io,net,time,time.format,util}

org.springframework.web.filter (test)
  ├─▶ org.springframework.web.filter           (ShallowEtagHeaderFilter — same package)
  └─▶ com.google.common.hash
```

No cycles. The main package has zero dependencies on the test packages; the test packages depend on main (expected).

## Module Boundaries and Cohesion

| Boundary | Enforcement | Assessment |
|---|---|---|
| Ports vs. adapters | Convention only (no module system, no ArchUnit rules, no package-private tricks) | Held consistently in practice: `DownloadController` and `ExistingFile` reference only `FileStorage`/`FilePointer`, never the concrete adapters. |
| Production vs. test | Maven source roots | Held, with the exception of `MainApplication` being package-private yet referenced by the Spock `@ContextConfiguration` — which works only because the spec shares the package. |
| Framework vs. domain | Convention | Partially held: the ports are clean, but `ExistingFile` is saturated with Spring HTTP types. Acceptable for a service this thin, since `ExistingFile` *is* the HTTP response policy. |

**Cohesion:** high. Every type in the main package participates in the single use case of serving a file download; there is no unrelated utility code.

**Coupling:** low and correctly directed. Fan-in concentrates on the two interfaces (`FilePointer`: 4 dependents, `FileStorage`: 3), which are the stable abstractions.

## Java Module System (JPMS)

No `module-info.java` is present; the project builds and runs on the classpath. Note that the `org.springframework.web.filter` test class would be illegal on a module path (split package), so introducing JPMS would require resolving TD-17 first.

## Recommended Structure Changes

| Change | Rationale | Related item |
|---|---|---|
| Configure a Groovy compiler plugin (e.g. `gmavenplus-plugin`) for `src/test/groovy`, or move the spec under a configured source root | The Spock specification currently has no configured compiler in `pom.xml` | TD-18 / R-10 |
| Rename `DownloadControllerTest.groovy` → `DownloadControllerSpec.groovy` | Groovy requires the filename to match the declared public class | TD-18 / R-10 |
| Move `Sha512ShallowEtagHeaderFilter` into `com.nurkiewicz.download` (or delete it) | Removes third-party package squatting; prerequisite for any module-path build | TD-17 / R-11 |
| Promote `MainApplication` to `public` | It is referenced as a `@ContextConfiguration` class | TD-21 / R-12 |
| Add `src/main/resources/application.yml` | Externalize Actuator exposure and the throttle rate instead of relying on defaults and hardcoded constants | TD-22 / R-09, R-12 |

See [../migration/component-order.md](../migration/component-order.md) for sequencing.
