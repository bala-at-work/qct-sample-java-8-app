# Dependencies

[← Back to index](../README.md) · Related: [../analysis/dependency-analysis.md](../analysis/dependency-analysis.md) · [../technical-debt/outdated-components.md](../technical-debt/outdated-components.md)

## Internal Dependency Graph (class-to-class)

```
MainApplication
   └─(bootstraps)─> Spring context
                      ├─> DownloadController ──> FileStorage (interface)
                      │        │                     ├── FileSystemStorage   [@Profile("!test")]
                      │        │                     └── FileStorageStub     [@Profile("test"), test tree]
                      │        └──> ExistingFile
                      │                 ├──> FilePointer (interface)
                      │                 │        └── FileSystemPointer
                      │                 ├──> ThrottlingInputStream ──> java.io.InputStream
                      │                 └──> com.google.common.util.concurrent.RateLimiter
                      └─> FileSystemStorage ──> FileSystemPointer
```

No cycles. Fan-in/fan-out:

| Type | Depends on (internal) | Depended on by (internal) |
|---|---|---|
| `MainApplication` | — (context-mediated) | `DownloadControllerSpec` (as config class) |
| `DownloadController` | `FileStorage`, `ExistingFile` | Spring MVC dispatch |
| `ExistingFile` | `FilePointer`, `ThrottlingInputStream` | `DownloadController` |
| `FileStorage` | `FilePointer` | `DownloadController`, both storage impls |
| `FileSystemStorage` | `FileStorage`, `FilePointer`, `FileSystemPointer` | Spring context |
| `FilePointer` | — | `ExistingFile`, `FileStorage`, both pointer users |
| `FileSystemPointer` | `FilePointer` | `FileSystemStorage`, `FileExamples` |
| `ThrottlingInputStream` | — | `ExistingFile` |

## External Dependencies (declared in `pom.xml`)

### Parent
`org.springframework.boot:spring-boot-starter-parent:1.2.3.RELEASE` — supplies dependency management and the Boot repackage plugin.

### Compile / runtime scope

| Coordinates | Version | Used by | Criticality |
|---|---|---|---|
| `org.springframework.boot:spring-boot-starter-web` | 1.2.3.RELEASE | whole web layer (embedded Tomcat, Jackson, Spring MVC) | Critical |
| `org.springframework.boot:spring-boot-starter-actuator` | 1.2.3.RELEASE | ops endpoints (no code references) | Low (removable) |
| `org.springframework:spring-core` | 4.1.6.RELEASE | transitive core | Critical |
| `org.springframework:spring-beans` | 4.1.6.RELEASE | DI | Critical |
| `org.springframework:spring-context` | 4.1.6.RELEASE | `@Component`, `@Profile` | Critical |
| `org.springframework:spring-context-support` | 4.1.6.RELEASE | no direct code reference | Low |
| `org.springframework:spring-web` | 4.1.6.RELEASE | `HttpMethod`, `HttpStatus`, `ResponseEntity`, `MediaType` | Critical |
| `org.springframework:spring-webmvc` | 4.1.6.RELEASE | `@RestController`, `ShallowEtagHeaderFilter` | Critical |
| `org.springframework:spring-aop` | 4.1.6.RELEASE | framework internals | Medium |
| `org.springframework:spring-expression` | 4.1.6.RELEASE | framework internals | Medium |
| `com.google.guava:guava` | 18.0 | `Hashing`, `HashCode`, `Files.hash`, `RateLimiter`, `MediaType`, `MoreObjects`, `HttpHeaders` (test) | Critical |
| `commons-io:commons-io` | 2.4 | only `FileUtils.ONE_MB` (`ExistingFile:21`) | Low |
| `commons-codec:commons-codec` | 1.10 | **no references found** | None — dead |
| `cglib:cglib-nodep` | 3.1 | Spock mocking (needed at test scope only) | Low, wrong scope |
| `org.codehaus.groovy:groovy-all` | 2.4.3 | Spock spec (test only) | Low, wrong scope |

### Test scope

| Coordinates | Version | Notes |
|---|---|---|
| `org.springframework:spring-test` | 4.1.6.RELEASE | **declared twice** in `pom.xml` |
| `org.springframework.boot:spring-boot-starter-test` | 1.2.3.RELEASE | excludes `spring-test` to avoid version clash |
| `org.spockframework:spock-core` | 1.0-groovy-2.4 | Spock engine |
| `org.spockframework:spock-spring` | 1.0-groovy-2.4 | excludes `groovy-all` (supplied at compile scope) |

### Build plugins

| Plugin | Version | Notes |
|---|---|---|
| `maven-compiler-plugin` | 3.0 | pins source/target to 1.8; very old plugin release |
| `spring-boot-maven-plugin` | inherited | from parent, repackages the JAR |
| Maven Wrapper | 3.3.2 / Maven 3.9.8 | `.mvn/wrapper/maven-wrapper.properties`, `only-script` distribution |

Note: **no Groovy compiler plugin (`gmavenplus`/`groovy-eclipse-compiler`) is configured**, so the Spock specification under `src/test/groovy` is not wired into compilation by the declared build configuration.

### Repositories

| Id | URL | Concern |
|---|---|---|
| `io.spring.repo.maven.milestone` | `http://repo.spring.io/milestone/` | Plaintext HTTP (supply-chain risk) and a milestone channel; no declared dependency requires it |

## Transitive Dependencies of Note

Pulled in by `spring-boot-starter-web` / `-actuator` / the Boot parent at 1.2.3-era versions:

- Embedded Tomcat (`spring-boot-starter-tomcat`) — servlet container
- Jackson Databind — JSON (unused by this application's own code; Actuator uses it)
- SLF4J API + Logback Classic + Logback Core — logging (`logback.xml` configures them)
- `jul-to-slf4j`, `log4j-over-slf4j` — logging bridges
- Snakeyaml, Hibernate Validator / Tomcat EL — Boot defaults
- JUnit, Hamcrest, Mockito, AssertJ, JsonPath — via `spring-boot-starter-test`

All transitives are anchored to the EOL Boot 1.2.3 BOM and therefore carry the same age/CVE exposure as the direct Spring dependencies.

## Declarative (Non-Code) Definitions

A repository-wide scan for declarative service/component definitions found:

| Category | Result |
|---|---|
| Spring XML bean/context files (`applicationContext*.xml`, `beans*.xml`, `*-context.xml`) | None — configuration is 100% annotation-driven |
| `web.xml` / servlet descriptors | None (embedded container, Boot auto-config) |
| OpenAPI / Swagger / GraphQL / `.proto` / `.wsdl` / Thrift / Avro / AsyncAPI | None |
| MyBatis / Hibernate mappings, `persistence.xml` | None |
| Camel routes, BPMN, Airflow DAGs, Struts configs | None |
| CloudFormation / SAM / CDK / Terraform / Kubernetes / Docker Compose / Dockerfile | None |
| EJB / OSGi / `servicedef/` | None |
| Logging configuration | `src/main/resources/logback.xml` — four logger level declarations plus a root `ConsoleAppender` with pattern `%d{HH:mm:ss.SSS} \| %thread \| %-5level \| %logger{1} \| %msg%n` |
| Build descriptors | `pom.xml`, `.mvn/wrapper/maven-wrapper.properties` |
| Test fixture data | `src/test/resources/download.txt` (content `foobar`) |

**Declarative-to-code mapping**: the only declarative artifact that binds to code is `logback.xml`, whose `com.nurkiewicz` logger (level `ALL`) governs the SLF4J loggers declared in `ExistingFile:26`, `FileSystemStorage:21`, and `FileStorageStub:18`. There are no XML-declared beans mapping to Java classes; all component wiring is via `@Component`/`@RestController`/`@Profile` annotations discovered by `@SpringBootApplication` scanning of `com.nurkiewicz.download`.
