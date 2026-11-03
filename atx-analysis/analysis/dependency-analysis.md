# Dependency Analysis

[← Back to index](../README.md) · Related: [../architecture/dependencies.md](../architecture/dependencies.md) · [../technical-debt/outdated-components.md](../technical-debt/outdated-components.md)

## Internal Dependency Matrix

Rows depend on columns (`X` = direct source-level reference).

| ↓ depends on → | MainApp | Controller | ExistingFile | FileStorage | FileSystemStorage | FilePointer | FileSystemPointer | Throttling |
|---|---|---|---|---|---|---|---|---|
| `MainApplication` | — | | | | | | | |
| `DownloadController` | | — | X | X | | | | |
| `ExistingFile` | | | — | | | X | | X |
| `FileStorage` | | | | — | | X | | |
| `FileSystemStorage` | | | | X | — | X | X | |
| `FilePointer` | | | | | | — | | |
| `FileSystemPointer` | | | | | | X | — | |
| `ThrottlingInputStream` | | | | | | | | — |

- **Cycles**: none.
- **Most depended-upon type**: `FilePointer` (fan-in 4).
- **Most dependent type**: `FileSystemStorage` (fan-out 3).
- **Instability**: the two interfaces are stable abstractions; all concrete types are leaves or near-leaves.

## External Dependency Usage Map

| Library | Imported symbols | Files |
|---|---|---|
| Guava | `Hashing`, `HashCode`, `Files.hash`, `MediaType`, `MoreObjects`, `RateLimiter`, `HttpHeaders` (test) | `FileSystemPointer`, `ExistingFile`, `ThrottlingInputStream`, `Sha512ShallowEtagHeaderFilter`, `DownloadControllerSpec` |
| Spring Web / MVC | `ResponseEntity`, `HttpMethod`, `HttpStatus`, `MediaType`, `HttpHeaders`, `@RestController`, `@RequestMapping`, `@PathVariable`, `@RequestHeader`, `ShallowEtagHeaderFilter` | `DownloadController`, `ExistingFile`, `Sha512ShallowEtagHeaderFilter` |
| Spring Core | `Resource`, `InputStreamResource` | `ExistingFile` |
| Spring Context / Beans | `@Component`, `@Profile`, `@Autowired` | `FileSystemStorage`, `FileStorageStub`, `DownloadController`, `DownloadControllerSpec` |
| Spring Boot | `SpringApplication`, `@SpringBootApplication` | `MainApplication` |
| Spring Test | `MockMvc`, `MockMvcBuilders`, `@WebAppConfiguration`, `@ContextConfiguration`, `@ActiveProfiles` | `DownloadControllerSpec` |
| Commons IO | `FileUtils.ONE_MB` only | `ExistingFile` |
| Commons Codec | **none** | — |
| cglib-nodep | none directly (Spock runtime need) | — |
| Groovy | language runtime for the spec | `DownloadControllerTest.groovy` |
| SLF4J | `Logger`, `LoggerFactory` | `ExistingFile`, `FileSystemStorage`, `FileStorageStub` |
| Spock | `Specification` | `DownloadControllerTest.groovy` |
| JDK | `java.io.*`, `java.net.{URI,URISyntaxException,URL}`, `java.nio.file.Files`, `java.time.{Instant,ZonedDateTime,ZoneOffset,format.DateTimeFormatter}`, `java.util.{Optional,UUID,Date,function.Function}` | all |

## Criticality Assessment

| Dependency | Criticality | Rationale | Replaceability |
|---|---|---|---|
| `spring-boot-starter-web` + Spring MVC/Core | **Critical** | the entire web and DI substrate | Not replaceable without a rewrite |
| Guava | **Critical** | hashing (ETag), rate limiting (throttle), media types | Replaceable: JDK `MessageDigest` for SHA-512, a small token-bucket for throttling, Spring `MediaType` for types |
| SLF4J + Logback | Medium | logging only | Easily swapped |
| `commons-io` | **Low** | a single `ONE_MB` constant | Trivially replaceable by a local constant |
| `spring-boot-starter-actuator` | **Low** | no code references it; currently a security liability | Removable, or secure it |
| `spring-context-support` | **Low** | no direct references | Likely removable |
| `commons-codec` | **None** | unused | Delete |
| `cglib-nodep` (compile scope) | **Low** | Spock mocking support | Move to `test` scope |
| `groovy-all` (compile scope) | **Low** | test-only language | Move to `test` scope |
| `spring-test` (declared twice) | Medium (tests) | duplicate declaration | De-duplicate |
| Spock 1.0 / Groovy 2.4 | Medium (tests) | the only test suite | Upgrade jointly (Spock 2.x needs JUnit 5 platform) |

## Version Consistency Issues

1. `<spring.version>4.1.1.RELEASE</spring.version>` is declared but **never referenced**; all Spring artifacts hardcode `4.1.6.RELEASE`. Dead and misleading.
2. Spring Framework artifacts are pinned to `4.1.6.RELEASE`, overriding the Boot 1.2.3 BOM (which manages `4.1.6`-era versions) — the explicit pins add maintenance cost with no benefit.
3. `spring-test` appears as two separate `<dependency>` blocks with identical coordinates and scope.
4. `spring-boot-starter-test` excludes `spring-test` to prevent a clash with the explicit pin — a workaround that disappears if the explicit pins are removed.
5. Starters restate the parent version (`1.2.3.RELEASE`) instead of inheriting it from the parent's dependency management.

## Build-Time Dependencies

| Item | Version | Note |
|---|---|---|
| `maven-compiler-plugin` | 3.0 | very old; pins Java 1.8 |
| `spring-boot-maven-plugin` | inherited from parent | repackaging |
| Maven Wrapper script | 3.3.2 | `distributionType=only-script` |
| Maven distribution | 3.9.8 | current enough |
| Groovy compiler plugin | **absent** | `src/test/groovy` is not compiled by the declared build |

## Supply-Chain Posture

| Aspect | Finding |
|---|---|
| Repositories | Maven Central (implicit) plus `http://repo.spring.io/milestone/` over **plaintext HTTP** — an interception/substitution risk at build time, and no declared dependency needs a milestone build. Recommended: remove it, or at minimum use `https://`. |
| Lockfile / reproducibility | None (Maven has no lockfile); versions are explicit, so builds are reasonably reproducible apart from the parent BOM resolution |
| Dependency scanning | No `dependency-check`, `versions-maven-plugin`, Dependabot config, or SBOM generation present in the repository |
| Vulnerability exposure | All runtime dependencies are anchored to an EOL Spring Boot 1.2.3 BOM; the Spring 4.1.x and Guava 18.0 lines have publicly known vulnerabilities and receive no fixes. Any upgrade should be re-checked against a CVE database at the exact resolved versions chosen. |
