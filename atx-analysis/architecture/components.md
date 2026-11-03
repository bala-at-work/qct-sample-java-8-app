# Components

- [Back to README](../README.md)
- Siblings: [system-overview.md](system-overview.md) · [dependencies.md](dependencies.md) · [patterns.md](patterns.md)
- Reference: [../reference/program-structure.md](../reference/program-structure.md) · [../reference/api-reference.md](../reference/api-reference.md)

## Table of Contents
1. [Component Catalog](#component-catalog)
2. [Main Components](#main-components)
3. [Test Components](#test-components)
4. [Interaction Matrix](#interaction-matrix)
5. [Spring Bean Inventory](#spring-bean-inventory)
6. [Non-Code Component Definitions](#non-code-component-definitions)

## Component Catalog

| Component | Kind | Visibility | Lines | Source |
|---|---|---|---|---|
| `MainApplication` | Bootstrap | package-private | 13 | `src/main/java/com/nurkiewicz/download/MainApplication.java` |
| `DownloadController` | Web adapter | public | 63 | `src/main/java/com/nurkiewicz/download/DownloadController.java` |
| `ExistingFile` | Protocol policy | public | 121 | `src/main/java/com/nurkiewicz/download/ExistingFile.java` |
| `ThrottlingInputStream` | I/O decorator | public | 66 | `src/main/java/com/nurkiewicz/download/ThrottlingInputStream.java` |
| `FileStorage` | Outbound port | public interface | 8 | `src/main/java/com/nurkiewicz/download/FileStorage.java` |
| `FilePointer` | Outbound port | public interface | 26 | `src/main/java/com/nurkiewicz/download/FilePointer.java` |
| `FileSystemStorage` | Storage adapter | public | 33 | `src/main/java/com/nurkiewicz/download/FileSystemStorage.java` |
| `FileSystemPointer` | Storage adapter | public | 86 | `src/main/java/com/nurkiewicz/download/FileSystemPointer.java` |
| `FileStorageStub` | Test storage adapter | public | — | `src/test/java/com/nurkiewicz/download/FileStorageStub.java` |
| `FileExamples` | Test fixtures | public | — | `src/test/java/com/nurkiewicz/download/FileExamples.java` |
| `Sha512ShallowEtagHeaderFilter` | Servlet filter (unused) | public | — | `src/test/java/org/springframework/web/filter/Sha512ShallowEtagHeaderFilter.java` |
| `DownloadControllerSpec` | Specification | public | 176 | `src/test/groovy/com/nurkiewicz/download/DownloadControllerTest.groovy` |

## Main Components

### `MainApplication`
**Responsibility.** Spring Boot entry point.

**Interface.** `public static void main(String[] args)`.

**Detail.** Annotated `@SpringBootApplication`, which enables auto-configuration and component scanning of `com.nurkiewicz.download`. The class itself is package-private — acceptable for Boot, and it is also the `@ContextConfiguration` class used by `DownloadControllerSpec`.

**Note.** Contains `Integer temp = new Integer("1234");` — a dead assignment using a constructor deprecated for removal. See [../technical-debt/outdated-components.md](../technical-debt/outdated-components.md#deprecated-apis-in-source).

### `DownloadController`
**Responsibility.** Translate HTTP requests into domain lookups and delegate response construction. It makes exactly one decision of its own: `404` when the UUID is unknown.

**Collaborators.** Depends on `FileStorage` (constructor-injected, `final`); constructs `ExistingFile`.

**Interface.**
| Method | Mapping | Returns |
|---|---|---|
| `redirect(HttpMethod, UUID, Optional<String>, Optional<Date>)` | `GET, HEAD /download/{uuid}` | `ResponseEntity<Resource>` |
| `download(HttpMethod, UUID, Optional<String>, Optional<Date>)` | `GET, HEAD /download/{uuid}/{filename}` | `ResponseEntity<Resource>` |
| `findExistingFile(HttpMethod, UUID)` *(private)* | — | `Optional<ExistingFile>` |

**Detail.** Both handlers are structurally identical and differ only in which `ExistingFile` method they invoke — `redirect(...)` versus `handle(...)`. Spring resolves the `HttpMethod` argument automatically, so a single handler serves both GET and HEAD. Conditional-request headers are bound as `Optional<String>` and `Optional<Date>`, so absent headers are `Optional.empty()` rather than null.

Notably, the `{filename}` path variable is **not bound or used** — it exists purely to make the canonical URL carry a human-meaningful name. Any filename is accepted for a valid UUID.

### `ExistingFile`
**Responsibility.** The HTTP response policy engine. Given an HTTP method, a `FilePointer` and a UUID, decide the status code, build the headers, and decide whether to attach a body.

**Collaborators.** `FilePointer` (port), `ThrottlingInputStream`, Guava `RateLimiter`, Guava `MediaType`, Spring `ResponseEntity`/`InputStreamResource`.

**State.** Three `final` fields — `method`, `filePointer`, `uuid`. Fully immutable, request-scoped, constructed fresh per request.

**Public interface.**
| Method | Behavior |
|---|---|
| `redirect(Optional<String>, Optional<Date>)` | `304` if client cache is valid, otherwise `301` to the canonical URL |
| `handle(Optional<String>, Optional<Date>)` | `304` if client cache is valid, otherwise `200` with the (throttled) file |

**Private methods and their roles.**
| Method | Role |
|---|---|
| `serveWithCaching(etag, ifModifiedSince, notCachedResponse)` | Common cache gate; takes the non-cached branch as a `Function<FilePointer, ResponseEntity<Resource>>` |
| `cached(etag, ifModifiedSince)` | `true` if the ETag matches **or** the client copy is not older than the server copy |
| `redirectDownload(FilePointer)` | Builds `301` with `Location: /download/{uuid}/{originalName}`; wraps `URISyntaxException` as `IllegalArgumentException` |
| `serveDownload(FilePointer)` | Builds `200` with the resource from `resourceToReturn` |
| `resourceToReturn(FilePointer)` | Returns the resource for `GET`, `null` for `HEAD` — this is how HEAD gets full headers with no body |
| `buildResource(FilePointer)` | Opens the stream, creates a fresh `RateLimiter.create(ONE_MB)`, wraps in `ThrottlingInputStream`, returns `InputStreamResource` |
| `notModified(FilePointer)` | Builds `304` with headers but no body |
| `response(FilePointer, HttpStatus, Resource)` | Single header-building funnel: sets `ETag`, `Content-Length`, `Last-Modified`, and `Content-Type` when known |
| `toMediaType(com.google.common.net.MediaType)` | Converts Guava's media type to Spring's, preserving charset when present |

**Design note.** Because `notModified`, `serveDownload` and the `301` path all converge on `response(...)`, header values are identical across `200`, `304` and (for the headers it sets) `301`. That is why a `304` still carries a correct `ETag` and `Content-Length`.

### `ThrottlingInputStream`
**Responsibility.** Limit read throughput of any `InputStream`.

**Collaborators.** Guava `RateLimiter` (injected), target `InputStream` (injected). No framework dependency at all.

**Interface.** Extends `java.io.InputStream`. Overrides `read()`, `read(byte[])`, `read(byte[],int,int)` — each acquiring permits equal to the byte count before delegating — plus straight pass-throughs for `skip`, `available`, `mark`, `reset`, `markSupported`, `close`.

**Detail.** `read()` acquires 1 permit, `read(byte[] b)` acquires `b.length`, `read(b, off, len)` acquires `len`. Permits are acquired for the *requested* length rather than the *actual* bytes read, so a short read at end-of-file slightly over-charges the limiter — a negligible and benign inaccuracy. `RateLimiter.acquire` blocks, so throttling consumes the calling thread.

Fully reusable and the most portable class in the codebase.

### `FileStorage` (port)
**Responsibility.** Locate a file by its public UUID identifier.

**Interface.** `Optional<FilePointer> findFile(UUID uuid)` — one method, no side effects, no exceptions declared.

**Implementations.** `FileSystemStorage` (profile `!test`), `FileStorageStub` (profile `test`).

### `FilePointer` (port)
**Responsibility.** Expose a file's bytes and the metadata needed to construct HTTP caching headers, without revealing where or how it is stored.

**Interface.** 8 methods — see [../reference/interfaces.md](../reference/interfaces.md) for full contracts.

| Method | Purpose |
|---|---|
| `InputStream open()` | Obtain the content stream |
| `long getSize()` | `Content-Length` |
| `String getOriginalName()` | Canonical filename for the redirect |
| `String getEtag()` | `ETag` header value (quoted) |
| `Optional<MediaType> getMediaType()` | `Content-Type`, absent if undetermminable |
| `boolean matchesEtag(String)` | `If-None-Match` evaluation |
| `Instant getLastModified()` | `Last-Modified` |
| `boolean modifiedAfter(Instant)` | `If-Modified-Since` evaluation |

**Design note.** Placing `matchesEtag` and `modifiedAfter` on the port — rather than comparing values in `ExistingFile` — lets each storage implementation define its own freshness semantics (for example, an S3 adapter could delegate to S3's own ETag).

### `FileSystemStorage` (adapter)
**Responsibility.** Nominally, resolve UUIDs to filesystem files.

**Annotations.** `@Component`, `@Profile("!test")`.

**Interface.** `Optional<FilePointer> findFile(UUID uuid)`.

**Actual behavior.** A deliberate stub: the Javadoc reads "Stub implementation that for any UUID returns logback.xml file. Replace with real implementation." The `uuid` argument is ignored and the classpath resource `/logback.xml` is always returned. See [../technical-debt/maintenance-burden.md](../technical-debt/maintenance-burden.md#unfinished-production-code).

### `FileSystemPointer` (adapter)
**Responsibility.** `FilePointer` backed by a `java.io.File`.

**State.** `final File target`, `final HashCode tag`, `final MediaType mediaTypeOrNull`.

**Construction.** `FileSystemPointer(File target)` eagerly (a) computes a Guava SHA-512 `HashCode` over the full file contents and (b) probes the content type via `java.nio.file.Files.probeContentType`. An `IOException` from either step is rewrapped as `IllegalArgumentException`.

**Interface.** All eight `FilePointer` methods plus `toString()` (Guava `MoreObjects.toStringHelper`).

| Method | Implementation |
|---|---|
| `open()` | Buffered `FileInputStream` over `target` |
| `getSize()` | `target.length()` |
| `getOriginalName()` | `target.getName()` |
| `getEtag()` | The SHA-512 hash, quoted |
| `getMediaType()` | `Optional.ofNullable(mediaTypeOrNull)` |
| `matchesEtag(String)` | Compares the request ETag against `getEtag()` |
| `getLastModified()` | `Instant.ofEpochMilli(target.lastModified())` |
| `modifiedAfter(Instant clientTime)` | `!clientTime.isBefore(getLastModified())` |

**Notes.** The only filesystem-aware class in the main source tree. Content-addressed ETags are strictly correct but cost a full file read per construction — the principal performance hotspot ([../analysis/complexity-analysis.md](../analysis/complexity-analysis.md#performance-hotspots)). The `modifiedAfter` name reads as the inverse of its semantics: it answers "is the client copy at least as new as the server copy", i.e. "the client may use its cache".

## Test Components

### `FileStorageStub`
`@Component @Profile("test")` implementation of `FileStorage`. Returns `FileExamples.TXT_FILE` for `FileExamples.TXT_FILE_UUID` and `Optional.empty()` for anything else. Paired with `FileSystemStorage`'s `@Profile("!test")` so exactly one `FileStorage` bean is ever active.

### `FileExamples`
Test fixture holder: `public static final UUID TXT_FILE_UUID`, `public static final FilePointer TXT_FILE` (a `FileSystemPointer` over the classpath resource `/download.txt`), `public static final UUID NOT_FOUND_UUID`, and a private factory `txtFile()`.

### `Sha512ShallowEtagHeaderFilter`
Extends Spring's `ShallowEtagHeaderFilter` and overrides the **protected** `generateETagHeaderValue(byte[] bytes)` to emit a quoted SHA-512 instead of Spring's MD5. Declared in package `org.springframework.web.filter` solely to access that protected member.

**Status: dead code.** It is never registered as a bean, a `FilterRegistrationBean`, or a servlet filter, and it lives in the test tree. It also targets a Spring 4.1 signature that later versions changed. See [../technical-debt/maintenance-burden.md](../technical-debt/maintenance-burden.md#framework-coupled-code).

### `DownloadControllerSpec`
Spock `Specification` annotated `@WebAppConfiguration`, `@ContextConfiguration(classes = [MainApplication])`, `@ActiveProfiles("test")`. Builds a `MockMvc` from the injected `WebApplicationContext` and exercises the controller through the real Spring MVC stack. 12 feature methods — enumerated in [../migration/test-specifications.md](../migration/test-specifications.md).

The file is named `DownloadControllerTest.groovy` while the class is `DownloadControllerSpec`; `src/test/groovy` is also not a declared source root, so this specification does not run in a default build.

## Interaction Matrix

Rows call columns. `●` = direct dependency.

| ↓ calls → | Controller | ExistingFile | FileStorage | FilePointer | ThrottlingInputStream | FileSystemPointer |
|---|---|---|---|---|---|---|
| `MainApplication` | | | | | | |
| `DownloadController` | — | ● | ● | | | |
| `ExistingFile` | | — | | ● | ● | |
| `FileSystemStorage` | | | *implements* | | | ● |
| `FileSystemPointer` | | | | *implements* | | — |
| `FileStorageStub` | | | *implements* | | | |
| `FileExamples` | | | | | | ● |
| `DownloadControllerSpec` | ● (via MockMvc) | | | | | ● (via FileExamples) |

No cycles exist. `ThrottlingInputStream`, `FilePointer` and `FileStorage` have no outgoing dependencies on project code.

## Spring Bean Inventory

| Bean | Type | Scope | Condition | Registered by |
|---|---|---|---|---|
| `downloadController` | `DownloadController` | singleton | always | `@RestController` + component scan |
| `fileSystemStorage` | `FileSystemStorage` | singleton | profile ≠ `test` | `@Component` + `@Profile("!test")` |
| `fileStorageStub` | `FileStorageStub` | singleton | profile = `test` | `@Component` + `@Profile("test")` |
| *(auto-configured)* | `DispatcherServlet`, embedded Tomcat, Jackson converters, actuator endpoints | singleton | always | `@SpringBootApplication` auto-configuration |

`ExistingFile`, `FileSystemPointer` and `ThrottlingInputStream` are **not** beans — they are instantiated directly per request. There are no `@Configuration` classes, no `@Bean` methods, and no XML bean definitions in the project.

## Non-Code Component Definitions

An exhaustive scan for declaratively-defined services and components found **none**. Specifically absent:

| Category | Searched for | Result |
|---|---|---|
| Spring XML contexts | `applicationContext*.xml`, `beans*.xml`, `*-context.xml` | None |
| Servlet descriptors | `WEB-INF/web.xml` | None |
| Service definition directories | `servicedef/`, `services*.xml` | None |
| EJB / OSGi | `ejb-jar.xml`, OSGi declarations | None |
| SOAP | `*.wsdl` | None |
| API specs | OpenAPI/Swagger, AsyncAPI | None |
| Schema-first IDLs | `*.proto`, `*.graphql`, Thrift, Avro | None |
| Persistence mappings | `*Mapper.xml`, `*.hbm.xml`, `persistence.xml` | None |
| Routing / workflow | Camel routes, Struts config, BPMN, Airflow DAGs | None |
| Infrastructure | CloudFormation/SAM, Kubernetes manifests, Docker Compose | None |

The only non-code configuration files in the repository are:

| File | Type | Content |
|---|---|---|
| `src/main/resources/logback.xml` | Logback configuration | Console appender with pattern `%d{HH:mm:ss.SSS} \| %thread \| %-5level \| %logger{1} \| %msg%n`; loggers `com`=INFO, `com.nurkiewicz`=ALL, `net`=INFO, `org`=INFO; root level DEBUG. Also doubles as the file served by `FileSystemStorage`. |
| `src/test/resources/download.txt` | Test fixture | 6 bytes, `foobar` |
| `pom.xml` | Maven build | Dependencies, parent, compiler plugin, milestone repository |
| `.mvn/wrapper/maven-wrapper.properties` | Maven wrapper | `wrapperVersion=3.3.2`, `distributionType=only-script`, Maven 3.9.8 |

Every service and component in this system is therefore declared **in code**, via Spring annotations. The complete component inventory is the annotation-driven bean list above.
