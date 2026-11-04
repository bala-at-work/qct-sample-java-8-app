# Dependencies

- [Back to README](../README.md)
- Siblings: [system-overview.md](system-overview.md) · [components.md](components.md) · [patterns.md](patterns.md)
- See also: [../analysis/dependency-analysis.md](../analysis/dependency-analysis.md) · [../technical-debt/outdated-components.md](../technical-debt/outdated-components.md)

## Table of Contents
1. [Internal Dependency Graph](#internal-dependency-graph)
2. [Class-to-Class Dependencies](#class-to-class-dependencies)
3. [External Dependencies](#external-dependencies)
4. [Library Usage Map](#library-usage-map)
5. [JDK API Usage](#jdk-api-usage)
6. [Build Tool Dependencies](#build-tool-dependencies)
7. [Transitive Dependencies](#transitive-dependencies)
8. [Repositories](#repositories)
9. [Declarative-to-Code Mappings](#declarative-to-code-mappings)
10. [External Services](#external-services)

## Internal Dependency Graph

```
MainApplication
    │ (bootstraps component scan)
    ▼
DownloadController ──────► FileStorage «interface»
    │                            ▲         ▲
    │                            │         │
    │                   FileSystemStorage  FileStorageStub
    │                    @Profile("!test")  @Profile("test")  [test]
    │                            │                  │
    ▼                            ▼                  ▼
ExistingFile ─────────────► FilePointer «interface» ◄── FileExamples [test]
    │                            ▲
    │                            │
    ▼                     FileSystemPointer
ThrottlingInputStream
```

Properties of this graph:

- **Acyclic.** No dependency cycles exist between any two classes.
- **Depth 4.** Longest chain: `MainApplication → DownloadController → ExistingFile → FilePointer → (FileSystemPointer)`.
- **Dependency inversion respected.** `DownloadController` and `ExistingFile` depend only on the `FileStorage`/`FilePointer` interfaces. No main-source class outside the two `FileSystem*` adapters references a filesystem type.
- **Leaf nodes.** `ThrottlingInputStream`, `FileStorage` and `FilePointer` have zero outgoing project dependencies.
- **Fan-in.** `FilePointer` has the highest fan-in (4 referencing types), making it the most stability-critical abstraction.

## Class-to-Class Dependencies

| Source | Target | Kind | Coupling |
|---|---|---|---|
| `MainApplication` | *(none)* | — | — |
| `DownloadController` | `FileStorage` | constructor injection (`final` field) | Interface — loose |
| `DownloadController` | `ExistingFile` | instantiation | Concrete — tight, but same layer |
| `ExistingFile` | `FilePointer` | field + parameter | Interface — loose |
| `ExistingFile` | `ThrottlingInputStream` | instantiation | Concrete — intentional decorator use |
| `FileSystemStorage` | `FileStorage` | implements | — |
| `FileSystemStorage` | `FileSystemPointer` | instantiation | Concrete — adapter-internal, correct |
| `FileSystemPointer` | `FilePointer` | implements | — |
| `FileStorageStub` *(test)* | `FileStorage`, `FileExamples` | implements / static reference | — |
| `FileExamples` *(test)* | `FileSystemPointer`, `FilePointer` | instantiation | — |
| `DownloadControllerSpec` *(test)* | `MainApplication`, `FileExamples` | context config / fixtures | — |
| `Sha512ShallowEtagHeaderFilter` *(test)* | Spring `ShallowEtagHeaderFilter` | inheritance into a third-party package | Very tight — see [../technical-debt/maintenance-burden.md](../technical-debt/maintenance-burden.md#framework-coupled-code) |

## External Dependencies

Declared in `/repo/pom.xml`. Versions are exactly as declared.

### Parent
| Coordinate | Version | Role |
|---|---|---|
| `org.springframework.boot:spring-boot-starter-parent` | 1.2.3.RELEASE | Dependency management BOM, plugin management, default packaging |

### Compile scope (production classpath)
| Coordinate | Version | Criticality | Purpose |
|---|---|---|---|
| `org.springframework.boot:spring-boot-starter-web` | 1.2.3.RELEASE | **Critical** | Spring MVC, embedded Tomcat, Jackson |
| `org.springframework.boot:spring-boot-starter-actuator` | 1.2.3.RELEASE | Low | Health/metrics endpoints; not configured or used by application code |
| `com.google.guava:guava` | 18.0 | **Critical** | `RateLimiter`, `Hashing`, `MediaType`, `MoreObjects` |
| `commons-io:commons-io` | 2.4 | Trivial | One constant: `FileUtils.ONE_MB` |
| `commons-codec:commons-codec` | 1.10 | **None** | No source usage found — removable |
| `cglib:cglib-nodep` | 3.1 | Low | Needed only by Spock mocking; wrongly at compile scope |
| `org.codehaus.groovy:groovy-all` | 2.4.3 | Low | Test language runtime; wrongly at compile scope |
| `org.springframework:spring-core` | 4.1.6.RELEASE | **Critical** | Transitively supplied by the web starter — explicit pin is redundant |
| `org.springframework:spring-beans` | 4.1.6.RELEASE | **Critical** | Redundant pin |
| `org.springframework:spring-context` | 4.1.6.RELEASE | **Critical** | Redundant pin |
| `org.springframework:spring-context-support` | 4.1.6.RELEASE | Low | Redundant pin; no feature of it is used |
| `org.springframework:spring-web` | 4.1.6.RELEASE | **Critical** | `ResponseEntity`, `HttpHeaders`, `MediaType`, `HttpMethod` |
| `org.springframework:spring-webmvc` | 4.1.6.RELEASE | **Critical** | `DispatcherServlet`, `@RestController` support |
| `org.springframework:spring-aop` | 4.1.6.RELEASE | Low | Redundant pin |
| `org.springframework:spring-expression` | 4.1.6.RELEASE | Low | Redundant pin |

### Test scope
| Coordinate | Version | Exclusions | Purpose |
|---|---|---|---|
| `org.springframework.boot:spring-boot-starter-test` | 1.2.3.RELEASE | `org.springframework:spring-test` | JUnit 4, Hamcrest, Mockito, Spring test support |
| `org.springframework:spring-test` | 4.1.6.RELEASE | — | `MockMvc`, `@ContextConfiguration`, `@WebAppConfiguration`. **Declared twice** |
| `org.spockframework:spock-core` | 1.0-groovy-2.4 | — | Spock specification engine |
| `org.spockframework:spock-spring` | 1.0-groovy-2.4 | `org.codehaus.groovy:groovy-all` | Spring context integration for Spock |

**Totals:** 19 declarations, 18 distinct coordinates (one duplicate), 15 at compile scope, 4 at test scope. Of the 15 compile-scope artifacts, 2 (`groovy-all`, `cglib-nodep`) are test-only in reality and 1 (`commons-codec`) is unused entirely.

## Library Usage Map

Which source file actually uses which external library — the ground truth behind the criticality ratings above.

| Library | Imported API | Used in |
|---|---|---|
| Guava | `com.google.common.util.concurrent.RateLimiter` | `ExistingFile.buildResource`, `ThrottlingInputStream` |
| Guava | `com.google.common.hash.Hashing` / `HashCode` | `FileSystemPointer` (SHA-512 ETag) |
| Guava | `com.google.common.net.MediaType` | `FilePointer`, `FileSystemPointer`, `ExistingFile.toMediaType` |
| Guava | `com.google.common.base.MoreObjects` | `FileSystemPointer.toString` |
| Commons IO | `org.apache.commons.io.FileUtils.ONE_MB` (static import) | `ExistingFile.buildResource` |
| Spring Web | `ResponseEntity`, `HttpStatus`, `HttpMethod`, `HttpHeaders`, `MediaType` | `DownloadController`, `ExistingFile` |
| Spring Core | `Resource`, `InputStreamResource` | `DownloadController`, `ExistingFile` |
| Spring Web MVC | `@RestController`, `@RequestMapping`, `@PathVariable`, `@RequestHeader` | `DownloadController` |
| Spring Beans | `@Autowired` | `DownloadController`, `DownloadControllerSpec` |
| Spring Context | `@Component`, `@Profile` | `FileSystemStorage`, `FileStorageStub` |
| Spring Boot | `@SpringBootApplication`, `SpringApplication` | `MainApplication` |
| Spring Test | `MockMvc`, `@WebAppConfiguration`, `@ContextConfiguration`, `@ActiveProfiles` | `DownloadControllerSpec` |
| Spring Web (filter) | `ShallowEtagHeaderFilter` | `Sha512ShallowEtagHeaderFilter` (unused) |
| SLF4J | `Logger`, `LoggerFactory` | `ExistingFile`, `FileSystemStorage` |
| Spock | `spock.lang.Specification` | `DownloadControllerSpec` |
| Commons Codec | — | **nothing** |
| Actuator | — | **nothing in application code** (endpoints only) |

## JDK API Usage

Relevant because it determines how much the `javax`→`jakarta` migration and JDK upgrade will touch application code.

| JDK API | Used in |
|---|---|
| `java.io.InputStream`, `FileInputStream`, `BufferedInputStream`, `File`, `IOException` | `FileSystemPointer`, `ThrottlingInputStream`, `FilePointer` |
| `java.nio.file.Files.probeContentType` | `FileSystemPointer` |
| `java.time.Instant` | `FilePointer`, `FileSystemPointer`, `ExistingFile` |
| `java.util.Optional` | Pervasive (ports and controller signatures) |
| `java.util.UUID` | `FileStorage`, `DownloadController`, `ExistingFile` |
| `java.util.Date` | `DownloadController`, `ExistingFile` (HTTP header binding only) |
| `java.util.function.Function` | `ExistingFile.serveWithCaching` |
| `java.net.URI`, `URISyntaxException` | `ExistingFile.redirectDownload` |

**No `javax.*` imports exist anywhere in the application source.** The only `javax`-namespace coupling is transitive, via the Servlet API that `ShallowEtagHeaderFilter` extends. This materially reduces the cost of a Jakarta EE namespace migration: the one affected class is unused dead code that should be deleted rather than migrated.

## Build Tool Dependencies

| Plugin / tool | Version | Configuration |
|---|---|---|
| `org.apache.maven.plugins:maven-compiler-plugin` | 3.0 | `<source>1.8</source>`, `<target>1.8</target>` — the **only** declared plugin |
| Maven wrapper | Maven 3.9.8 | `.mvn/wrapper/maven-wrapper.properties`: `wrapperVersion=3.3.2`, `distributionType=only-script` |
| `maven-surefire-plugin` | *inherited from parent* | Not declared; no custom includes. Will not discover Spock 2.x engines after a Boot upgrade without configuration |
| Groovy compilation plugin | **absent** | No `gmavenplus-plugin`, `groovy-eclipse-compiler`, or `build-helper-maven-plugin`. Consequence: `src/test/groovy` is not a test-source root and the Spock spec is not compiled by a default build |
| `spring-boot-maven-plugin` | *not declared* | Inherited plugin **management** only — without an explicit declaration no executable fat JAR is repackaged |
| Build properties | — | `project.build.sourceEncoding=UTF-8`; `spring.version=4.1.1.RELEASE` (declared, never referenced) |

## Transitive Dependencies

Managed by `spring-boot-starter-parent:1.2.3.RELEASE` and not pinned in this POM. These move automatically when the parent is upgraded — which is precisely why the explicit Spring Framework pins are harmful.

| Transitive artifact | Version supplied by Boot 1.2.3 | Arrives via | Significance |
|---|---|---|---|
| `org.apache.tomcat.embed:tomcat-embed-core` | 8.0.x | `spring-boot-starter-web` | HTTP server and Servlet 3.1 API |
| `com.fasterxml.jackson.core:jackson-databind` | 2.4.x | `spring-boot-starter-web` | JSON; unused by this application's endpoints but on the classpath |
| `org.slf4j:slf4j-api` | 1.7.x | `spring-boot-starter-logging` | Logging facade used directly |
| `ch.qos.logback:logback-classic` | 1.1.x | `spring-boot-starter-logging` | Logging backend; configured by `logback.xml` |
| `org.hibernate:hibernate-validator` | 5.1.x | `spring-boot-starter-web` | Bean Validation; unused |
| `junit:junit` | 4.x | `spring-boot-starter-test` | Test framework (Spock runs on the JUnit 4 runner) |
| `org.mockito:mockito-core`, `org.hamcrest:hamcrest-*` | Boot 1.2.3 era | `spring-boot-starter-test` | Unused by the Spock spec |

All of the above are the EOL versions of their respective lines. Note that `jackson-databind` and `hibernate-validator` are on the production classpath without being used — historically common CVE carriers, and a reason to treat the transitive set as part of the security surface even though no application code touches them.

## Repositories

| ID | URL | Snapshots | Assessment |
|---|---|---|---|
| `io.spring.repo.maven.milestone` | `http://repo.spring.io/milestone/` | disabled | 🟠 **Medium risk.** Plain HTTP exposes artifact resolution to man-in-the-middle substitution. Every dependency declared in this POM is a GA release available from Maven Central, so this repository is unnecessary and should be deleted rather than switched to HTTPS. |
| Maven Central | *implicit, via the Boot parent* | — | Standard and appropriate |

## Declarative-to-Code Mappings

No declarative service definitions exist in this repository (see [components.md](components.md#non-code-component-definitions) for the exhaustive negative scan), so there are no XML-to-class or schema-to-implementation mappings to record. The only configuration-to-code relationships are:

| Configuration artifact | Relates to code | Nature of the relationship |
|---|---|---|
| `src/main/resources/logback.xml` | `ExistingFile`, `FileSystemStorage` | Sets the log levels for the `com.nurkiewicz` logger hierarchy these classes write to |
| `src/main/resources/logback.xml` | `FileSystemStorage.findFile` | **Unusually, it is also the data**: the stub resolves and serves this exact file for every UUID |
| `src/test/resources/download.txt` | `FileExamples.TXT_FILE` | Backs the `FileSystemPointer` fixture used by every test assertion about size, content and media type |
| `pom.xml` compiler plugin | All Java sources | Fixes the language level at Java 8 |
| Spring profile `test` | `FileStorageStub` / `FileSystemStorage` | `@Profile` conditions select which `FileStorage` bean is registered |

## External Services

**None.** An exhaustive scan found no HTTP clients (`RestTemplate`, `HttpClient`, `OkHttp`), no database drivers or connection configuration, no message broker clients (JMS, Kafka, RabbitMQ, SQS), no cache clients (Redis, Memcached), no cloud SDKs (notably no `com.amazonaws` or `software.amazon.awssdk`), and no service-discovery or configuration-server integration.

The application's only outbound interaction is with the **local filesystem and classpath**, via `FileSystemPointer` and `FileSystemStorage`. This makes the storage port the single natural integration seam — an S3-backed adapter would be the only place an external service enters the system.
