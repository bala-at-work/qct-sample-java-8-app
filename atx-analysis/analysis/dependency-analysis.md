# Dependency Analysis

[← Back to index](../README.md) · [Architecture: dependencies](../architecture/dependencies.md)

## Internal Dependency Mapping

### Adjacency list (production)

| Type | Depends on |
|---|---|
| `MainApplication` | `SpringApplication` (framework) |
| `DownloadController` | `FileStorage`, `ExistingFile`, `FilePointer`, Spring MVC/HTTP types |
| `ExistingFile` | `FilePointer`, `ThrottlingInputStream`, Guava `RateLimiter`/`MediaType`, Commons IO `FileUtils`, SLF4J, Spring HTTP/core.io types |
| `ThrottlingInputStream` | `java.io.InputStream`, Guava `RateLimiter` |
| `FileStorage` | `FilePointer`, `java.util` |
| `FilePointer` | Guava `MediaType`, `java.io`, `java.time`, `java.util` |
| `FileSystemStorage` | `FileStorage`, `FileSystemPointer`, SLF4J, Spring `@Component`/`@Profile` |
| `FileSystemPointer` | `FilePointer`, Guava `Files`/`Hashing`/`HashCode`/`MediaType`/`MoreObjects`, `java.io`, `java.nio.file`, `java.time` |

### Graph properties

| Property | Value |
|---|---|
| Cycles | **None** |
| Maximum path depth | 4 (`MainApplication → DownloadController → ExistingFile → FilePointer`) |
| Strongly connected components | 8 (all singletons — confirms acyclicity) |
| Types with zero internal dependents | `MainApplication`, `DownloadController`, `FileSystemStorage` (all framework-instantiated entry points) |
| Abstraction compliance | 100% — no delivery or domain class references a concrete adapter |

### Fan-in / fan-out and instability

Instability I = fan-out / (fan-in + fan-out). Lower is more stable; abstractions should be stable (low I).

| Type | Fan-in | Fan-out | Instability | Abstract? | Assessment |
|---|---|---|---|---|---|
| `FilePointer` | 4 | 1 | 0.20 | Yes | Stable abstraction — ideal |
| `FileStorage` | 3 | 1 | 0.25 | Yes | Stable abstraction — ideal |
| `FileSystemPointer` | 3 | 5 | 0.63 | No | Concrete and volatile — acceptable for an adapter |
| `ThrottlingInputStream` | 1 | 2 | 0.67 | No | Concrete leaf |
| `ExistingFile` | 1 | 6 | 0.86 | No | Most volatile; the orchestrator |
| `DownloadController` | 1 | 3 | 0.75 | No | Volatile delivery edge |
| `FileSystemStorage` | 0 | 2 | 1.00 | No | Maximally volatile adapter — correct position |
| `MainApplication` | 1 | 1 | 0.50 | No | Bootstrap |

The two interfaces sit in the stable-abstract quadrant and everything concrete sits in the volatile-concrete quadrant. There are no "stable concrete" types (which would be rigid) and no "volatile abstract" types (which would be useless). The dependency structure is sound.

## Change Impact Analysis

| If this changes… | Directly affected | Transitively affected |
|---|---|---|
| `FilePointer` (interface) | `FileSystemPointer`, `ExistingFile`, `FileStorage`, `FileStorageStub`, `FileExamples` | Entire request path — **highest-impact change surface** |
| `FileStorage` (interface) | `FileSystemStorage`, `FileStorageStub`, `DownloadController` | Request entry path |
| `ExistingFile` | `DownloadController` | HTTP response shape → all 12 specifications |
| `ThrottlingInputStream` | `ExistingFile` | Body delivery only; no header impact |
| `FileSystemPointer` | `FileSystemStorage`, `FileExamples` | Production behavior + test fixtures |
| `FileSystemStorage` | none (framework-wired) | Production behavior only; tests unaffected (`@Profile("test")`) |
| `DownloadController` | none | HTTP contract → all 12 specifications |
| `MainApplication` | `DownloadControllerSpec` (`@ContextConfiguration`) | Context bootstrap |

**Implication for remediation:** replacing the stub storage (R-05) touches only `FileSystemStorage` and has zero blast radius on tests — the safest high-value change available. Conversely, any change to `FilePointer` ripples through five types and the entire specification suite.

## External Dependency Criticality

| Artifact | Version | Criticality | Rationale | Removal feasible? |
|---|---|---|---|---|
| `spring-boot-starter-web` | `1.2.3.RELEASE` | **Critical** | Provides MVC, embedded Tomcat, the entire delivery layer | No |
| `spring-webmvc` | `4.1.6.RELEASE` | **Critical** | `@RestController`, `@RequestMapping` | No (but should be transitive, not declared) |
| `spring-web` | `4.1.6.RELEASE` | **Critical** | `ResponseEntity`, `HttpMethod`, `HttpStatus`, `MediaType`, `HttpHeaders` | No (should be transitive) |
| `spring-core` | `4.1.6.RELEASE` | **Critical** | `Resource`, `InputStreamResource` | No (should be transitive) |
| `spring-beans` | `4.1.6.RELEASE` | **Critical** | `@Autowired` | No (should be transitive) |
| `spring-context` | `4.1.6.RELEASE` | **Critical** | `@Component`, `@Profile` | No (should be transitive) |
| `guava` | `18.0` | **Critical** | `RateLimiter` (throttling), `Hashing`/`Files.hash` (ETags), `MediaType` (port type), `MoreObjects` | No — deeply embedded in 5 classes |
| `slf4j-api` | transitive | High | Logging in 3 classes | No — but **must be declared explicitly** (currently an undeclared direct dependency) |
| `logback-classic` / `logback-core` | transitive | High | Logging backend; referenced by FQCN in `logback.xml` | No |
| `commons-io` | `2.4` | **Low** | Single use: the constant `FileUtils.ONE_MB` | **Yes** — replace with `1024L * 1024` |
| `commons-codec` | `1.10` | **None** | **No source usage found anywhere** | **Yes — remove** |
| `spring-boot-starter-actuator` | `1.2.3.RELEASE` | Low | No source references; operational endpoints only | Yes, if monitoring is not required (also removes a security exposure — see [security-patterns.md](security-patterns.md)) |
| `spring-aop` | `4.1.6.RELEASE` | **None** | No source reference | Yes |
| `spring-expression` | `4.1.6.RELEASE` | **None** | No source reference | Yes (though transitively required by `spring-context`) |
| `spring-context-support` | `4.1.6.RELEASE` | **None** | No source reference | Yes |
| `cglib-nodep` | `3.1` | **None** | No direct reference; legacy proxy support | Likely — verify against Spock/Spring proxying needs |
| `groovy-all` | `2.4.3` | Medium (test only) | Groovy runtime for the Spock spec, but declared at **compile** scope | Rescope to `test` |
| `spring-boot-starter-test` | `1.2.3.RELEASE` | Medium (test) | Test harness | No |
| `spring-test` | `4.1.6.RELEASE` | Medium (test) | `MockMvc`, context test support. **Declared twice.** | No — de-duplicate |
| `spock-core` / `spock-spring` | `1.0-groovy-2.4` | Medium (test) | Specification engine | No |

### Dependency hygiene findings

| Finding | Severity | Detail |
|---|---|---|
| Unused declared dependencies | Medium | `commons-codec`, `spring-aop`, `spring-expression`, `spring-context-support`, `cglib-nodep` — five artifacts with no direct source reference |
| Undeclared direct dependency | Low | `org.slf4j` is imported by `ExistingFile`, `FileSystemStorage`, `FileStorageStub` but never declared; it arrives transitively via `spring-boot-starter-logging`. A change in Boot's logging starter would break compilation. |
| Redundant explicit declarations | High | Seven `org.springframework:*` artifacts are already supplied by `spring-boot-starter-web`; declaring them with hardcoded versions overrides the parent BOM |
| Duplicate declaration | Low | `org.springframework:spring-test:4.1.6.RELEASE` appears twice |
| Scope error | Medium | `groovy-all` at compile scope leaks the Groovy runtime into the production classpath |
| Unused property | Low | `<spring.version>4.1.1.RELEASE</spring.version>` is declared but never referenced; actual Spring artifacts use `4.1.6.RELEASE` |
| Version override conflict | High | Parent BOM manages Boot 1.2.3's Spring version, but all Spring artifacts are pinned to `4.1.6.RELEASE` explicitly — a parent bump alone will not move them |
| Insecure resolution source | High | `http://repo.spring.io/milestone/` over plaintext HTTP |
| Missing build plugin | Medium | `spring-boot-maven-plugin` is not declared, so no executable JAR is repackaged |

## Transitive Dependency Notes

| Artifact | Arrives via | Relevance |
|---|---|---|
| `slf4j-api` | `spring-boot-starter-web` → `spring-boot-starter-logging` | Direct source dependency (see above) |
| `logback-classic`, `logback-core` | `spring-boot-starter-logging` | Referenced by FQCN in `logback.xml` |
| `tomcat-embed-core`, `tomcat-embed-el`, `tomcat-embed-logging-juli` | `spring-boot-starter-tomcat` | Servlet container; carries the `javax.servlet` API that forces the `jakarta` migration on upgrade |
| `jackson-databind`, `jackson-core`, `jackson-annotations` | `spring-boot-starter-web` | Present but **entirely unused** — this service never serializes an object |
| `junit:junit` (4.x) | `spring-boot-starter-test` | Platform under Spock 1.x; replaced by the JUnit 5 platform when moving to Spock 2.x |
| `hamcrest`, `mockito`, `assertj` | `spring-boot-starter-test` | Available but unused — no mocking or matcher usage in the specs |
| Exclusions in effect | `spock-spring` excludes `org.codehaus.groovy:groovy-all`; `spring-boot-starter-test` excludes `org.springframework:spring-test` | Both exclusions exist so the explicitly declared versions win |

## Dependency Risk Summary

| Risk | Severity | Items |
|---|---|---|
| EOL framework versions with no security patches | **High** | Spring Boot 1.2.3, Spring Framework 4.1.6 |
| Insecure plaintext artifact resolution | **High** | `http://repo.spring.io/milestone/` |
| Version management fighting the parent BOM | **High** | 10 hardcoded `<version>` elements |
| Outdated runtime libraries | **Medium** | Guava 18.0, Commons IO 2.4, Commons Codec 1.10, Groovy 2.4.3 |
| Unused dependencies enlarging the attack surface | **Medium** | 5 artifacts |
| Reliance on a transitive artifact for a direct dependency | **Low** | SLF4J |

Before committing any replacement version, verify the exact resolved coordinate against a vulnerability database and never select a version below the one currently declared.

See [../technical-debt/outdated-components.md](../technical-debt/outdated-components.md) and [../technical-debt/remediation-plan.md](../technical-debt/remediation-plan.md).
