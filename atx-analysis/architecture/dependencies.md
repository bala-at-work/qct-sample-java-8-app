# Dependencies

[← Back to index](../README.md) · [System overview](system-overview.md)

## Internal Dependency Graph

```
MainApplication
    (Spring component scan of com.nurkiewicz.download)
        │
        ├──▶ DownloadController ──▶ FileStorage        «interface»
        │          │                     ▲
        │          │                     ├── FileSystemStorage  @Profile("!test")
        │          │                     └── FileStorageStub    @Profile("test")
        │          │                              │
        │          │                              ▼
        │          └──▶ ExistingFile ──▶ FilePointer «interface»
        │                     │                ▲
        │                     │                └── FileSystemPointer
        │                     │
        │                     └──▶ ThrottlingInputStream ──▶ java.io.InputStream
        │
        └──▶ (Actuator auto-configuration)
```

### Class-to-class edges

| From | To | Nature |
|---|---|---|
| `DownloadController` | `FileStorage` | constructor injection (`@Autowired`) |
| `DownloadController` | `ExistingFile` | instantiation per request |
| `DownloadController` | `FilePointer` | via `FileStorage.findFile` return type |
| `ExistingFile` | `FilePointer` | field, constructor parameter |
| `ExistingFile` | `ThrottlingInputStream` | instantiation in `buildResource` |
| `ExistingFile` | Guava `RateLimiter` | instantiation in `buildResource` |
| `ThrottlingInputStream` | Guava `RateLimiter` | field |
| `FileSystemStorage` | `FileSystemPointer` | instantiation in `findFile` |
| `FileSystemStorage` | `FileStorage` | implements |
| `FileSystemPointer` | `FilePointer` | implements |
| `FileStorageStub` | `FileStorage` | implements |
| `FileStorageStub` | `FileExamples` | static import of fixtures |
| `FileExamples` | `FileSystemPointer` | instantiation |
| `DownloadControllerSpec` | `MainApplication`, `FileExamples` | `@ContextConfiguration` class, fixtures |

**Properties of the graph:**
- **Acyclic.** No dependency cycles exist between production classes.
- **Depth 4** at most (`MainApplication → DownloadController → ExistingFile → FilePointer`).
- **Dependency inversion holds:** the delivery and domain layers depend only on the `FileStorage`/`FilePointer` interfaces, never on `FileSystemStorage`/`FileSystemPointer`.
- **Fan-in:** `FilePointer` (4 dependents) and `FileStorage` (3) are the most-depended-upon types — they are the stable abstractions, which is the correct shape.
- **Fan-out:** `ExistingFile` has the highest outgoing coupling (6 collaborators).

## External Dependencies

All versions are declared in `/repo/pom.xml`.

### Parent

| Artifact | Version | Role |
|---|---|---|
| `org.springframework.boot:spring-boot-starter-parent` | `1.2.3.RELEASE` | Dependency management BOM + plugin management. `<relativePath/>` set to force resolution from the repository. |

### Compile / runtime scope

| Artifact | Version | Criticality | Used by |
|---|---|---|---|
| `org.springframework.boot:spring-boot-starter-web` | `1.2.3.RELEASE` | **Critical** — provides Spring MVC, embedded Tomcat, Jackson | `DownloadController`, whole web layer |
| `org.springframework.boot:spring-boot-starter-actuator` | `1.2.3.RELEASE` | Low — operational only, no source references | — |
| `com.google.guava:guava` | `18.0` | **Critical** — hashing, rate limiting, media types, `toString` helper | `ExistingFile`, `FileSystemPointer`, `ThrottlingInputStream`, `FilePointer`, `Sha512ShallowEtagHeaderFilter` |
| `commons-io:commons-io` | `2.4` | Low — one constant (`FileUtils.ONE_MB`) | `ExistingFile` |
| `commons-codec:commons-codec` | `1.10` | **Unused** — no source reference found | — |
| `org.codehaus.groovy:groovy-all` | `2.4.3` | Misdeclared — compile scope but test-only need | `DownloadControllerSpec` |
| `cglib:cglib-nodep` | `3.1` | Low — no direct source reference; legacy proxying support | — |
| `org.springframework:spring-core` | `4.1.6.RELEASE` | **Critical** | transitively required |
| `org.springframework:spring-beans` | `4.1.6.RELEASE` | **Critical** | `@Autowired` |
| `org.springframework:spring-context` | `4.1.6.RELEASE` | **Critical** | `@Component`, `@Profile` |
| `org.springframework:spring-context-support` | `4.1.6.RELEASE` | Low — no source reference | — |
| `org.springframework:spring-web` | `4.1.6.RELEASE` | **Critical** | `ResponseEntity`, `HttpMethod`, `MediaType`, `HttpHeaders` |
| `org.springframework:spring-webmvc` | `4.1.6.RELEASE` | **Critical** | `@RestController`, `@RequestMapping`, `ShallowEtagHeaderFilter` |
| `org.springframework:spring-aop` | `4.1.6.RELEASE` | Low — no source reference | — |
| `org.springframework:spring-expression` | `4.1.6.RELEASE` | Low — no source reference | — |

> All seven of the `spring-web*`/`spring-core`/`spring-beans`/`spring-context`/`spring-aop`/`spring-expression` artifacts are already supplied transitively by `spring-boot-starter-web`. Declaring them explicitly with hardcoded versions **overrides the parent BOM** and is the root cause of maintenance hotspot 1.

### Test scope

| Artifact | Version | Role |
|---|---|---|
| `org.springframework.boot:spring-boot-starter-test` | `1.2.3.RELEASE` | Test harness. Excludes `org.springframework:spring-test`. |
| `org.springframework:spring-test` | `4.1.6.RELEASE` | `MockMvc`, `@ContextConfiguration`, `@ActiveProfiles`, `@WebAppConfiguration`. **Declared twice** in `pom.xml`. |
| `org.spockframework:spock-core` | `1.0-groovy-2.4` | Spock specification engine |
| `org.spockframework:spock-spring` | `1.0-groovy-2.4` | Spring integration for Spock. Excludes `org.codehaus.groovy:groovy-all`. |

### Transitive dependencies of note

| Artifact | Source | Relevance |
|---|---|---|
| `org.slf4j:slf4j-api` | `spring-boot-starter-web` → `spring-boot-starter-logging` | Used directly by `ExistingFile`, `FileSystemStorage`, `FileStorageStub` — a **direct source dependency satisfied transitively**, which is fragile. Should be declared explicitly. |
| `ch.qos.logback:logback-classic`, `logback-core` | `spring-boot-starter-logging` | Referenced by FQCN in `src/main/resources/logback.xml` (`ch.qos.logback.core.ConsoleAppender`) |
| `org.apache.tomcat.embed:tomcat-embed-*` | `spring-boot-starter-tomcat` | Servlet container |
| `com.fasterxml.jackson.core:jackson-databind` | `spring-boot-starter-web` | Present but unused — this service returns `Resource` bodies, never JSON |
| `junit:junit` | `spring-boot-starter-test` | JUnit 4 platform under Spock 1.x |

## Build Tool Dependencies

| Plugin | Version | Configuration |
|---|---|---|
| `org.apache.maven.plugins:maven-compiler-plugin` | `3.0` | `<source>1.8</source><target>1.8</target>` — the authoritative declaration of the Java language level |
| `spring-boot-maven-plugin` | — | **Not declared.** Inherited plugin *management* from the parent exists, but without an explicit `<plugin>` entry the executable-JAR repackaging goal does not run. The build therefore produces a plain library JAR, not a runnable Boot JAR. |

Maven Wrapper is present (`mvnw`, `mvnw.cmd`, `.mvn/wrapper/`), pinning the Maven version used for builds.

## Repositories

| ID | URL | Snapshots | Finding |
|---|---|---|---|
| `io.spring.repo.maven.milestone` | `http://repo.spring.io/milestone/` | disabled | **High severity** — plaintext HTTP (TD-04). Appears unnecessary: all declared dependencies are GA releases available from Maven Central. |
| Maven Central | implicit | — | Default |

## Declarative Definition Files

A repository-wide scan for declarative service/component definitions found the following. There are **no** Spring XML bean contexts, no `web.xml`, no OpenAPI/Swagger specs, no GraphQL schemas, no `.proto`/Thrift/Avro IDL, no WSDL, no MyBatis/Hibernate mappings, no `persistence.xml`, no Camel routes, no BPMN or Airflow DAGs, no CloudFormation/SAM templates, no Kubernetes manifests, and no Docker Compose files.

| File | Type | Content |
|---|---|---|
| `pom.xml` | Maven POM | Build, dependency, and repository declarations (documented above) |
| `src/main/resources/logback.xml` | Logback configuration | Four logger level declarations plus a root `ConsoleAppender`. See [../specialized/logging-configuration.md](../specialized/logging-configuration.md). |
| `.mvn/wrapper/` | Maven Wrapper metadata | Pins the Maven distribution |

All services and components in this system are therefore declared **in code**, via annotations:

| Declaration | Mechanism | Implementing class |
|---|---|---|
| Application context root | `@SpringBootApplication` | `MainApplication` |
| `/download/**` endpoints | `@RestController` + `@RequestMapping` | `DownloadController` |
| Production storage bean | `@Component` + `@Profile("!test")` | `FileSystemStorage` |
| Test storage bean | `@Component` + `@Profile("test")` | `FileStorageStub` |

See [../analysis/dependency-analysis.md](../analysis/dependency-analysis.md) for criticality scoring and [../technical-debt/outdated-components.md](../technical-debt/outdated-components.md) for version currency.
