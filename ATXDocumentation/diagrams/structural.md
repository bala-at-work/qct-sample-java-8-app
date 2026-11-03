# Structural Diagrams

- [Back to README](../README.md)
- Siblings: [behavioral.md](behavioral.md) · [architecture.md](architecture.md)
- See also: [../architecture/components.md](../architecture/components.md) · [../reference/program-structure.md](../reference/program-structure.md)

All diagrams are text-based for universal readability without rendering tooling.

## Table of Contents
1. [Component Diagram](#component-diagram)
2. [Class Diagram](#class-diagram)
3. [Interface Realization Diagram](#interface-realization-diagram)
4. [Package and Source-Root Diagram](#package-and-source-root-diagram)
5. [Dependency Graph](#dependency-graph)
6. [External Library Coupling Diagram](#external-library-coupling-diagram)
7. [Spring Bean Wiring Diagram](#spring-bean-wiring-diagram)
8. [Object Composition at Runtime](#object-composition-at-runtime)
9. [Deployment Architecture Diagram](#deployment-architecture-diagram)

## Component Diagram

```
╔══════════════════════════════════════════════════════════════════════════╗
║ download-server  (single Maven module, Spring Boot 1.2.3, Java 8)        ║
║                                                                          ║
║  ┌────────────────────────────────────────────────────────────────────┐  ║
║  │ «bootstrap»                                                        │  ║
║  │  MainApplication          @SpringBootApplication                   │  ║
║  └───────────────────────────────┬────────────────────────────────────┘  ║
║                                  │ component scan                        ║
║  ┌───────────────────────────────▼────────────────────────────────────┐  ║
║  │ «web adapter»                                                      │  ║
║  │  DownloadController       @RestController @RequestMapping(/download)│  ║
║  │    provides: GET|HEAD /download/{uuid}                             │  ║
║  │              GET|HEAD /download/{uuid}/{filename}                  │  ║
║  │    requires: FileStorage                                           │  ║
║  └───────────────────────────────┬────────────────────────────────────┘  ║
║                                  │ «creates»                             ║
║  ┌───────────────────────────────▼────────────────────────────────────┐  ║
║  │ «protocol policy»                                                  │  ║
║  │  ExistingFile              304 / 301 / 200 decision + headers      │  ║
║  │  ThrottlingInputStream     1 MB/s Guava RateLimiter                │  ║
║  │    requires: FilePointer                                           │  ║
║  └───────────────────────────────┬────────────────────────────────────┘  ║
║                                  │                                       ║
║  ┌───────────────────────────────▼────────────────────────────────────┐  ║
║  │ «ports»                                                            │  ║
║  │  ○── FileStorage   findFile(UUID) : Optional<FilePointer>           │  ║
║  │  ○── FilePointer   open/getSize/getEtag/getMediaType/…              │  ║
║  └───────────────────────────────▲────────────────────────────────────┘  ║
║                                  │ «implements»                          ║
║  ┌───────────────────────────────┴────────────────────────────────────┐  ║
║  │ «storage adapter»                                                  │  ║
║  │  FileSystemStorage   @Component @Profile("!test")   [STUB]          │  ║
║  │  FileSystemPointer   SHA-512 ETag, probeContentType                 │  ║
║  └───────────────────────────────┬────────────────────────────────────┘  ║
╚══════════════════════════════════│═══════════════════════════════════════╝
                                   │ java.io / java.nio
                       ┌───────────▼────────────┐
                       │ Local filesystem /     │
                       │ classpath (logback.xml)│
                       └────────────────────────┘

  ┌──────────────────────── test-time substitution ────────────────────────┐
  │  FileStorageStub  @Component @Profile("test")  ──implements── FileStorage│
  │  FileExamples     fixtures over classpath /download.txt                 │
  └────────────────────────────────────────────────────────────────────────┘
```

**Key.** `○──` = provided/required interface (port). Boxes are logical components; the test-time block replaces the storage adapter entirely when the `test` profile is active.

## Class Diagram

```
┌─────────────────────────────────────┐
│ «class» MainApplication             │  package-private
│ @SpringBootApplication              │
├─────────────────────────────────────┤
│ + main(String[]) : void      static │
└─────────────────────────────────────┘

┌───────────────────────────────────────────────────────────────┐
│ «class» DownloadController                                    │
│ @RestController @RequestMapping("/download")                  │
├───────────────────────────────────────────────────────────────┤
│ - storage : FileStorage                                 final │
├───────────────────────────────────────────────────────────────┤
│ «@Autowired» + DownloadController(FileStorage)                │
│ + redirect(HttpMethod, UUID, Optional<String>,                │
│            Optional<Date>) : ResponseEntity<Resource>         │
│ + download(HttpMethod, UUID, Optional<String>,                │
│            Optional<Date>) : ResponseEntity<Resource>         │
│ - findExistingFile(HttpMethod, UUID) : Optional<ExistingFile> │
└───────────┬──────────────────────────────────┬────────────────┘
            │ «creates»                        │ «uses»
            ▼                                  ▼
┌─────────────────────────────────────────┐   ┌──────────────────────────────┐
│ «class» ExistingFile                    │   │ «interface» FileStorage      │
├─────────────────────────────────────────┤   ├──────────────────────────────┤
│ - log : Logger            static final  │   │ + findFile(UUID)             │
│ - method : HttpMethod            final  │   │     : Optional<FilePointer>  │
│ - filePointer : FilePointer      final  │   └──────────────▲───────────────┘
│ - uuid : UUID                    final  │                  │ implements
├─────────────────────────────────────────┤      ┌───────────┴────────────┐
│ + ExistingFile(HttpMethod,              │      │                        │
│                FilePointer, UUID)       │  ┌───┴──────────────────┐ ┌───┴──────────────────┐
│ + redirect(Optional, Optional)          │  │ «class»              │ │ «class»       [test] │
│ + handle(Optional, Optional)             │  │ FileSystemStorage    │ │ FileStorageStub      │
│ - serveWithCaching(Optional, Optional,  │  │ @Component            │ │ @Component           │
│       Function<FilePointer, RE<R>>)     │  │ @Profile("!test")     │ │ @Profile("test")     │
│ - cached(Optional, Optional) : boolean  │  ├──────────────────────┤ ├──────────────────────┤
│ - redirectDownload(FilePointer)         │  │ - log : Logger        │ │ - log : Logger       │
│ - serveDownload(FilePointer)            │  ├──────────────────────┤ ├──────────────────────┤
│ - resourceToReturn(FilePointer)         │  │ + findFile(UUID)      │ │ + findFile(UUID)     │
│ - buildResource(FilePointer)            │  │   [STUB: always       │ │   [TXT_FILE_UUID →   │
│ - notModified(FilePointer)              │  │    /logback.xml]      │ │    TXT_FILE, else    │
│ - response(FilePointer, HttpStatus,     │  └──────────┬───────────┘ │    empty()]          │
│            Resource)                    │             │ «creates»   └──────────────────────┘
│ - toMediaType(guava.MediaType)          │             │
│            : spring.MediaType           │             │
└────────┬──────────────────────┬─────────┘             │
         │ «uses»               │ «creates»             │
         ▼                      ▼                       │
┌──────────────────────────────┐ ┌──────────────────────▼───────────────────┐
│ «interface» FilePointer      │ │ «class» ThrottlingInputStream            │
├──────────────────────────────┤ │   extends java.io.InputStream            │
│ + open() : InputStream       │ ├──────────────────────────────────────────┤
│ + getSize() : long           │ │ - target : InputStream             final │
│ + getOriginalName() : String │ │ - maxBytesPerSecond : RateLimiter  final │
│ + getEtag() : String         │ ├──────────────────────────────────────────┤
│ + getMediaType()             │ │ + ThrottlingInputStream(InputStream,     │
│     : Optional<MediaType>    │ │                         RateLimiter)     │
│ + matchesEtag(String)        │ │ + read() : int              acquire(1)   │
│     : boolean                │ │ + read(byte[]) : int        acquire(len) │
│ + getLastModified()          │ │ + read(byte[],int,int):int  acquire(len) │
│     : Instant                │ │ + skip(long) : long                      │
│ + modifiedAfter(Instant)     │ │ + available() : int                      │
│     : boolean                │ │ + mark(int)          synchronized        │
└──────────────▲───────────────┘ │ + reset()            synchronized        │
               │ implements      │ + markSupported() : boolean              │
               │                 │ + close()                                │
┌──────────────┴─────────────────┴─────────┐                                │
│ «class» FileSystemPointer                │                                │
├──────────────────────────────────────────┤                                │
│ - target : File                    final │                                │
│ - tag : HashCode                   final │  ← Guava SHA-512 of contents    │
│ - mediaTypeOrNull : MediaType      final │  ← probeContentType            │
├──────────────────────────────────────────┤                                │
│ + FileSystemPointer(File)                │                                │
│ + open() / getSize() / getOriginalName() │                                │
│ + getEtag() / getMediaType()             │                                │
│ + matchesEtag(String)                    │                                │
│ + getLastModified() / modifiedAfter()    │                                │
│ + toString()                             │                                │
└──────────────────────────────────────────┘
```

### Test-tree classes
```
┌──────────────────────────────────────┐  ┌───────────────────────────────────────┐
│ «class» FileExamples          [test] │  │ «class» DownloadControllerSpec [test] │
├──────────────────────────────────────┤  │   extends spock.lang.Specification    │
│ + TXT_FILE_UUID : UUID  public s.f.  │  │ @WebAppConfiguration                  │
│ + TXT_FILE : FilePointer public s.f. │  │ @ContextConfiguration([MainApplication])│
│ + NOT_FOUND_UUID : UUID public s.f.  │  │ @ActiveProfiles("test")               │
├──────────────────────────────────────┤  ├───────────────────────────────────────┤
│ - txtFile() : FileSystemPointer  s.  │  │ - mockMvc : MockMvc                   │
└──────────────────────────────────────┘  │ - TEXT_FILE : String      static final│
                                          ├───────────────────────────────────────┤
┌──────────────────────────────────────┐  │ «@Autowired» + setWebApplicationContext│
│ «class» Sha512ShallowEtagHeaderFilter│  │ + 12 feature methods                  │
│   extends ShallowEtagHeaderFilter    │  │ - toDateHeader(Instant) : String  s.  │
│   package org.springframework.web.   │  └───────────────────────────────────────┘
│           filter          [UNUSED]   │
├──────────────────────────────────────┤
│ # generateETagHeaderValue(byte[])    │
│     : String        SHA-512, quoted  │
└──────────────────────────────────────┘
```

## Interface Realization Diagram

```
                       FileStorage «interface»
                   findFile(UUID) : Optional<FilePointer>
                                 △
                 ┌───────────────┴───────────────┐
                 │                               │
    FileSystemStorage                    FileStorageStub
    @Profile("!test")                    @Profile("test")        [test tree]
    ignores uuid                         TXT_FILE_UUID → TXT_FILE
    always /logback.xml                  otherwise     → empty()
    ╰─ never returns empty()             ╰─ exercises both branches
       ⇒ 404 unreachable in prod

                       FilePointer «interface»
            open, getSize, getOriginalName, getEtag,
            getMediaType, matchesEtag, getLastModified,
            modifiedAfter                            (8 methods)
                                 △
                                 │
                        FileSystemPointer
                        ╰─ ETag  = quoted SHA-512 of file contents
                        ╰─ type  = java.nio probeContentType
                        ╰─ size, lastModified read live from File
```

**Note.** The `@Profile("!test")` / `@Profile("test")` pairing is load-bearing: it guarantees exactly one `FileStorage` bean. Removing either annotation causes an ambiguous-bean startup failure.

## Package and Source-Root Diagram

```
download-server
│
├── src/main/java ─────────────────────── registered ✅
│   └── com.nurkiewicz.download
│       ├── MainApplication          (bootstrap)
│       ├── DownloadController       (web adapter)
│       ├── ExistingFile             (protocol policy)
│       ├── ThrottlingInputStream    (I/O decorator)
│       ├── FileStorage              «interface» port
│       ├── FilePointer              «interface» port
│       ├── FileSystemStorage        (storage adapter)
│       └── FileSystemPointer        (storage adapter)
│
├── src/main/resources ───────────────── registered ✅
│   └── logback.xml                  (logging config AND the file served by the stub)
│
├── src/test/java ───────────────────── registered ✅
│   ├── com.nurkiewicz.download
│   │   ├── FileExamples             (fixtures)
│   │   └── FileStorageStub          (test adapter)
│   └── org.springframework.web.filter        ⚠ third-party namespace
│       └── Sha512ShallowEtagHeaderFilter     ⚠ never registered — dead code
│
├── src/test/groovy ─────────────── ❌ NOT REGISTERED AS A SOURCE ROOT
│   └── com.nurkiewicz.download
│       └── DownloadControllerTest.groovy     ⚠ class is DownloadControllerSpec
│           ╰─ 12 feature methods that DO NOT RUN in a default build
│
└── src/test/resources ──────────────── registered ✅
    └── download.txt                 (6 bytes: "foobar")
```

The unregistered Groovy root is the most consequential structural defect in the project — see [../technical-debt/maintenance-burden.md](../technical-debt/maintenance-burden.md#build-configuration-gaps).

## Dependency Graph

```
                      MainApplication
                            │ component scan (runtime wiring)
            ┌───────────────┴───────────────┐
            ▼                               ▼
    DownloadController              FileSystemStorage
      fan-out 2 / in 1                fan-out 3 / in 0
            │                               │
     ┌──────┴──────┐                        │ creates
     ▼             ▼                        ▼
ExistingFile   FileStorage ◄─implements─ FileSystemPointer
fan-out 2/in 1  «interface»                fan-out 1 / in 3
     │          fan-out 1 / in 4                 │
     │                                           │ implements
     ├──────────────┬────────────────────────────┤
     ▼              ▼                            ▼
Throttling     FilePointer «interface» ◄─────────┘
InputStream    fan-out 0 / in 4  ◄── STABLE CORE
fan-out 0/in 1
```

| Property | Value |
|---|---|
| Cycles | **0** |
| Max depth | 4 (`MainApplication → DownloadController → ExistingFile → FilePointer`) |
| Highest fan-in | `FilePointer` and `FileStorage` (4 each) |
| Highest fan-out | `FileSystemStorage` (3) |
| Zero-dependency leaves | `FilePointer`, `ThrottlingInputStream`, `MainApplication` |
| Instability pattern | Interfaces most stable (0.00–0.20), adapters/policy most instable (0.67–1.00) — matches the Stable Abstractions Principle |

## External Library Coupling Diagram

```
                    ┌─────────────────────────────────────┐
                    │ Spring Boot 1.2.3 / Spring 4.1.6    │
                    └──┬────────┬──────────┬──────────┬───┘
                       │        │          │          │
     @SpringBootApp ───┘        │          │          └─── @Component, @Profile
     MainApplication            │          │               FileSystemStorage
                                │          │
       @RestController ─────────┘          └───── ResponseEntity, HttpMethod,
       @RequestMapping                            HttpHeaders, MediaType,
       @PathVariable                              Resource, InputStreamResource
       @RequestHeader                             ExistingFile
       DownloadController

                    ┌─────────────────────────────────────┐
                    │ Guava 18.0                          │
                    └──┬──────────┬──────────┬────────────┘
                       │          │          │
      RateLimiter ─────┘          │          └──── MediaType
      ExistingFile                │                FilePointer  ⚠ PORT SIGNATURE
      ThrottlingInputStream       │                FileSystemPointer
        ⚠ PUBLIC CONSTRUCTOR     │                ExistingFile.toMediaType
                                  │
             Hashing / HashCode ──┘
             MoreObjects
             Files.hash
             FileSystemPointer

                    ┌──────────────────────┐   ┌──────────────────────┐
                    │ Commons IO 2.4       │   │ SLF4J + Logback      │
                    └──────────┬───────────┘   └──────────┬───────────┘
                               │                          │
                   FileUtils.ONE_MB                Logger / LoggerFactory
                   ExistingFile                    ExistingFile
                   (one constant only)             FileSystemStorage

                    ┌──────────────────────────────────────────┐
                    │ DECLARED BUT NOT IMPORTED ANYWHERE       │
                    │  commons-codec 1.10      (zero usage)    │
                    │  spring-context-support  (zero usage)    │
                    │  spring-aop              (no direct use) │
                    │  spring-expression       (no direct use) │
                    │  cglib-nodep 3.1         (test-only)     │
                    │  groovy-all 2.4.3        (test-only)     │
                    │  actuator                (no code use)   │
                    └──────────────────────────────────────────┘
```

⚠ marks the two architecturally significant couplings: Guava's `MediaType` sits in the `FilePointer` **port signature**, and Guava's `RateLimiter` sits in `ThrottlingInputStream`'s **public constructor**. A Guava major upgrade therefore propagates into abstraction boundaries rather than staying inside an adapter.

## Spring Bean Wiring Diagram

```
          ┌──────────────────────────────────────────────────────┐
          │ ApplicationContext (Spring Boot auto-configuration)  │
          │                                                      │
          │  ┌──────────────────────────────────────────────┐    │
          │  │ Auto-configured (not declared by this project)│   │
          │  │  • Embedded Tomcat + DispatcherServlet        │    │
          │  │  • HttpMessageConverters (Jackson — unused)   │    │
          │  │  • Actuator endpoints (UNSECURED)             │    │
          │  │  • Logback via logback.xml                    │    │
          │  └──────────────────────────────────────────────┘    │
          │                                                      │
          │  ┌──────────────────────────────────────────────┐    │
          │  │ downloadController : DownloadController       │    │
          │  │   singleton, @RestController                  │    │
          │  │   constructor-injected ◄──────┐               │    │
          │  └──────────────────────────────│───────────────┘    │
          │                                 │                     │
          │        ┌────────────────────────┴──────────────┐      │
          │        │  exactly ONE of the following is      │      │
          │        │  registered, selected by profile      │      │
          │        ├───────────────────────────────────────┤      │
          │        │  profile ≠ test:                      │      │
          │        │    fileSystemStorage : FileSystemStorage│    │
          │        │    @Component @Profile("!test")        │      │
          │        ├───────────────────────────────────────┤      │
          │        │  profile = test:                      │      │
          │        │    fileStorageStub : FileStorageStub   │      │
          │        │    @Component @Profile("test")         │      │
          │        └───────────────────────────────────────┘      │
          │                                                      │
          │  NOT beans — instantiated per request:                │
          │    ExistingFile, FileSystemPointer,                   │
          │    ThrottlingInputStream, RateLimiter                 │
          │                                                      │
          │  No @Configuration classes. No @Bean methods.         │
          │  No XML bean definitions. No property files.          │
          └──────────────────────────────────────────────────────┘
```

## Object Composition at Runtime

Object graph for a single successful `GET` request.

```
 DownloadController (singleton)
   └─ storage : FileSystemStorage | FileStorageStub (singleton)
                      │
                      │ findFile(uuid) ⇒ Optional<FilePointer>
                      ▼
            FileSystemPointer                      ← NEW per request
              ├─ target : File
              ├─ tag : HashCode                    ← full-file SHA-512 computed HERE
              └─ mediaTypeOrNull : MediaType       ← probeContentType called HERE
                      │
                      ▼
            ExistingFile                           ← NEW per request
              ├─ method : HttpMethod
              ├─ filePointer : FilePointer  ───────┘
              └─ uuid : UUID
                      │
                      │ handle(...) ⇒ 200
                      ▼
            ResponseEntity<Resource>               ← NEW per request
              ├─ status  : 200 OK
              ├─ headers : ETag, Content-Length,
              │            Last-Modified, Content-Type
              └─ body    : InputStreamResource     ← NEW per request (null for HEAD)
                             └─ ThrottlingInputStream   ← NEW per request
                                  ├─ target : BufferedInputStream
                                  │             └─ FileInputStream → File
                                  └─ maxBytesPerSecond : RateLimiter
                                           ← NEW per response ⇒ 1 MB/s is
                                             PER DOWNLOAD, not global
```

**Two facts this diagram makes visible:**
1. The SHA-512 hash is computed inside `FileSystemPointer`'s constructor — i.e. before the response status is even decided — so it is paid even for `304` and `301` responses.
2. The `RateLimiter` is allocated per response, so N concurrent downloads consume up to N MB/s. There is no global egress ceiling.

## Deployment Architecture Diagram

Dashed boxes are **not present in the repository** — they are the implied or required elements of any real deployment.

```
            ┌───────────────────────────────┐
            │ HTTP Client                   │
            │  browser / curl / downloader  │
            └───────────────┬───────────────┘
                            │ HTTP/1.1  (⚠ no TLS configured)
            ┌ ─ ─ ─ ─ ─ ─ ─ ▼ ─ ─ ─ ─ ─ ─ ─ ┐
              TLS terminator / load balancer     NOT IN REPO
            │ (assumed but undocumented)      │  (required for HTTPS)
            └ ─ ─ ─ ─ ─ ─ ─ ┬ ─ ─ ─ ─ ─ ─ ─ ┘
                            │
    ╔═══════════════════════▼═══════════════════════════════╗
    ║ JVM process   java -jar download-server.jar            ║
    ║               (⚠ spring-boot-maven-plugin NOT declared ║
    ║                 ⇒ no executable fat JAR is produced)   ║
    ║                                                        ║
    ║  ┌──────────────────────────────────────────────────┐  ║
    ║  │ Embedded Tomcat 8.0.x  — port 8080 (default)     │  ║
    ║  │   connector thread pool ≈ 200 threads            │  ║
    ║  │   ⚠ each in-flight download blocks ONE thread    │  ║
    ║  │     for its full duration (RateLimiter.acquire)  │  ║
    ║  └────────────────────┬─────────────────────────────┘  ║
    ║                       │                                ║
    ║  ┌────────────────────▼─────────────────────────────┐  ║
    ║  │ Spring ApplicationContext                        │  ║
    ║  │   /download/**   DownloadController   (anonymous)│  ║
    ║  │   /health /info /env /beans /trace …            │  ║
    ║  │                  Actuator      ⚠ UNAUTHENTICATED│  ║
    ║  └────────────────────┬─────────────────────────────┘  ║
    ╚═══════════════════════│════════════════════════════════╝
                            │ java.io / java.nio
            ┌───────────────▼───────────────┐
            │ Local filesystem / classpath  │
            │   /logback.xml   ← the ONLY   │
            │   file ever served (STUB)     │
            └───────────────────────────────┘

            ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐
              Real file storage                   NOT IN REPO
            │  • object store for bytes       │   (F1 — must be implemented)
              • metadata store for UUID → key
            └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘

    Also absent from the repository:
      ✗ Dockerfile / container image
      ✗ Kubernetes / ECS / CloudFormation / CDK / Terraform
      ✗ CI pipeline (.github, Jenkinsfile, buildspec)
      ✗ application.properties / application.yml
      ✗ Any authentication or authorization layer
```

### Deployment properties
| Property | Value |
|---|---|
| Artifact | Plain JAR (fat-JAR repackaging not configured) |
| Process model | Single JVM, stateless |
| Horizontal scalability | Yes — no session, no server-side cache, any instance serves any request |
| Vertical scalability limit | Tomcat thread-pool size, because throttled downloads block their worker thread |
| Configuration mechanism | Command-line arguments or environment variables only (no config file exists) |
| Profiles | `test` selects the stub storage; anything else selects `FileSystemStorage` |
| External dependencies at runtime | None — no database, broker, cache, or outbound HTTP |
| Shared state between instances | None (and none possible until real shared storage exists) |
