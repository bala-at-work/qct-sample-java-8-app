# Structural Diagrams

[← Back to index](../README.md)

Text-based diagrams for universal readability.

## Component Diagram

```
┌──────────────────────────────────────────────────────────────────────┐
│                      download-server (single JVM)                    │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │  MainApplication            «@SpringBootApplication»           │  │
│  │  component-scan root: com.nurkiewicz.download                  │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ── Delivery ──────────────────────────────────────────────────────  │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │  DownloadController         «@RestController»                  │  │
│  │  @RequestMapping("/download")                                  │  │
│  │  + redirect(..)   GET|HEAD /download/{uuid}                    │  │
│  │  + download(..)   GET|HEAD /download/{uuid}/{filename}         │  │
│  └───────────────┬──────────────────────────────┬─────────────────┘  │
│                  │ constructs per request       │ «uses»             │
│  ── Domain ──────▼──────────────────────────    │                    │
│  ┌──────────────────────────────────┐           │                    │
│  │  ExistingFile                    │           │                    │
│  │  + redirect(etag, ifModSince)    │           │                    │
│  │  + handle(etag, ifModSince)      │           │                    │
│  │  - cached() / response() / ...   │           │                    │
│  └────────┬──────────────────┬──────┘           │                    │
│           │ «uses»           │ «uses»           │                    │
│  ┌────────▼───────────────┐  │                  │                    │
│  │ ThrottlingInputStream  │  │                  │                    │
│  │ (Decorator, 1 MB/s)    │  │                  │                    │
│  └────────────────────────┘  │                  │                    │
│                              │                  │                    │
│  ── Ports (framework-free) ──▼──────────────────▼─────────────────   │
│  ┌──────────────────────────┐    ┌──────────────────────────────┐   │
│  │ «interface» FilePointer  │◀───│ «interface» FileStorage      │   │
│  │  open, getSize, getEtag, │    │  findFile(UUID)              │   │
│  │  getMediaType, ...       │    │    : Optional<FilePointer>   │   │
│  └────────────▲─────────────┘    └──────────────▲───────────────┘   │
│               │ implements                      │ implements         │
│  ── Adapters ─┴──────────────────────────────────┴───────────────    │
│  ┌──────────────────────────┐    ┌──────────────────────────────┐   │
│  │ FileSystemPointer        │◀───│ FileSystemStorage  «@Component│   │
│  │ SHA-512 hash, probe type │    │  @Profile("!test")»  [STUB]  │   │
│  └──────────────────────────┘    └──────────────────────────────┘   │
│                                  ┌──────────────────────────────┐   │
│                                  │ FileStorageStub   «@Component│   │
│                                  │  @Profile("test")»   (test)  │   │
│                                  └──────────────────────────────┘   │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │  Spring Boot Actuator (auto-configured, unconfigured)          │  │
│  └────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
          ▲                                      │
          │ HTTP GET/HEAD                        ▼ reads
     HTTP client                          local filesystem
```

## Class Diagram

```
                       «interface»
                       FileStorage
          ┌──────────────────────────────────────┐
          │ + findFile(UUID): Optional<FilePointer>│
          └──────────────────────────────────────┘
                        △          △
         implements ─────┘          └───── implements
   ┌───────────────────────┐   ┌──────────────────────────┐
   │ FileSystemStorage     │   │ FileStorageStub  (test)  │
   │ «@Component»          │   │ «@Component»             │
   │ «@Profile("!test")»   │   │ «@Profile("test")»       │
   ├───────────────────────┤   ├──────────────────────────┤
   │ - log: Logger         │   │ - log: Logger            │
   ├───────────────────────┤   ├──────────────────────────┤
   │ + findFile(UUID)      │   │ + findFile(UUID)         │
   │   [STUB: always       │   │   [fixture or empty]     │
   │    returns logback.xml]│  │   (missing @Override)    │
   └───────────┬───────────┘   └────────────┬─────────────┘
               │ creates                    │ returns
               ▼                            ▼
                       «interface»
                       FilePointer
   ┌──────────────────────────────────────────────────┐
   │ + open(): InputStream                            │
   │ + getSize(): long                                │
   │ + getOriginalName(): String                      │
   │ + getEtag(): String                              │
   │ + getMediaType(): Optional<guava.MediaType>      │
   │ + matchesEtag(String): boolean                   │
   │ + getLastModified(): Instant                     │
   │ + modifiedAfter(Instant): boolean                │
   └──────────────────────────────────────────────────┘
                            △ implements
   ┌──────────────────────────────────────────────────┐
   │ FileSystemPointer                  «immutable»   │
   ├──────────────────────────────────────────────────┤
   │ - target: File              {final}              │
   │ - tag: HashCode             {final}  SHA-512     │
   │ - mediaTypeOrNull: MediaType{final}  nullable    │
   ├──────────────────────────────────────────────────┤
   │ + FileSystemPointer(File)   eager hash + probe   │
   │ + open() … + modifiedAfter(Instant)              │
   │ + toString()                 Guava MoreObjects   │
   └──────────────────────────────────────────────────┘

   ┌──────────────────────────────────────────────────┐
   │ DownloadController           «@RestController»   │
   │                   «@RequestMapping("/download")» │
   ├──────────────────────────────────────────────────┤
   │ - storage: FileStorage      {final}              │
   ├──────────────────────────────────────────────────┤
   │ + DownloadController(FileStorage)   «@Autowired» │
   │ + redirect(HttpMethod, UUID,                     │
   │     Optional<String>, Optional<Date>)            │
   │       : ResponseEntity<Resource>                 │
   │ + download(HttpMethod, UUID,                     │
   │     Optional<String>, Optional<Date>)            │
   │       : ResponseEntity<Resource>                 │
   │ - findExistingFile(HttpMethod, UUID)             │
   │       : Optional<ExistingFile>                   │
   └───────────────────────┬──────────────────────────┘
                           │ creates 1 per request
                           ▼
   ┌──────────────────────────────────────────────────┐
   │ ExistingFile                       «immutable»   │
   ├──────────────────────────────────────────────────┤
   │ - log: Logger               {static final}       │
   │ - method: HttpMethod        {final}              │
   │ - filePointer: FilePointer  {final}              │
   │ - uuid: UUID                {final}              │
   ├──────────────────────────────────────────────────┤
   │ + ExistingFile(HttpMethod, FilePointer, UUID)    │
   │ + redirect(Optional<String>, Optional<Date>)     │
   │ + handle(Optional<String>, Optional<Date>)       │
   │ - serveWithCaching(.., Function<FilePointer,     │
   │        ResponseEntity<Resource>>)                │
   │ - cached(Optional<String>, Optional<Date>)       │
   │ - redirectDownload(FilePointer)   301            │
   │ - serveDownload(FilePointer)      200            │
   │ - resourceToReturn(FilePointer)   null if !GET   │
   │ - buildResource(FilePointer)                     │
   │ - notModified(FilePointer)        304            │
   │ - response(FilePointer, HttpStatus, Resource)    │
   │ - toMediaType(guava.MediaType): spring.MediaType │
   └───────────────────────┬──────────────────────────┘
                           │ creates
                           ▼
   ┌──────────────────────────────────────────────────┐
   │ ThrottlingInputStream                            │
   │        extends java.io.InputStream  «Decorator»  │
   ├──────────────────────────────────────────────────┤
   │ - target: InputStream           {final}          │
   │ - maxBytesPerSecond: RateLimiter{final}          │
   ├──────────────────────────────────────────────────┤
   │ + read()                      throttled acquire(1)    │
   │ + read(byte[])                throttled acquire(len)  │
   │ + read(byte[],int,int)        throttled acquire(len)  │
   │ + skip(long)                  NOT throttled           │
   │ + available() + mark() + reset()                      │
   │ + markSupported() + close()   pass-through            │
   └──────────────────────────────────────────────────┘

   ┌──────────────────────────────────────────────────┐
   │ MainApplication        «@SpringBootApplication»  │
   │ (package-private)                                │
   ├──────────────────────────────────────────────────┤
   │ + main(String[])                                 │
   └──────────────────────────────────────────────────┘
```

### Test-scope classes

```
   spock.lang.Specification
            △
            │ extends
   ┌──────────────────────────────────────────────────┐
   │ DownloadControllerSpec                           │
   │   «@WebAppConfiguration»                         │
   │   «@ContextConfiguration(classes=[MainApplication])»│
   │   «@ActiveProfiles("test")»                      │
   ├──────────────────────────────────────────────────┤
   │ - mockMvc: MockMvc                               │
   │ - TEXT_FILE: String {static final}               │
   ├──────────────────────────────────────────────────┤
   │ + setWebApplicationContext(WebApplicationContext)│
   │   12 feature methods                             │
   │ - toDateHeader(Instant): String                  │
   └──────────────────────────────────────────────────┘

   org.springframework.web.filter.ShallowEtagHeaderFilter
            △
            │ extends  ⚠ project class inside a third-party package
   ┌──────────────────────────────────────────────────┐
   │ Sha512ShallowEtagHeaderFilter   (never registered)│
   │ # generateETagHeaderValue(byte[]): String        │
   └──────────────────────────────────────────────────┘

   ┌──────────────────────────────────────────────────┐
   │ FileExamples                                     │
   │ + TXT_FILE_UUID: UUID      {static final}        │
   │ + TXT_FILE: FilePointer    {static final}        │
   │ + NOT_FOUND_UUID: UUID     {static final}        │
   │ - txtFile(): FileSystemPointer {static}          │
   └──────────────────────────────────────────────────┘
```

## Package Dependency Graph

```
         ┌──────────────────────────────────────────────┐
         │  com.nurkiewicz.download        (src/main)   │
         │  8 types: 2 interfaces, 6 classes            │
         └───┬──────────────────────────────────────────┘
             │
   ┌─────────┼──────────┬───────────┬─────────┬──────────┐
   ▼         ▼          ▼           ▼         ▼          ▼
org.spring  com.google  org.apache  org.slf4j java.*  (JDK)
framework.* common.*    commons.io
  ├ boot      ├ hash       └ FileUtils
  ├ beans     ├ io              .ONE_MB
  ├ context   ├ net
  ├ core.io   ├ base
  ├ http      └ util.concurrent
  ├ stereotype
  └ web.bind.annotation

         ┌──────────────────────────────────────────────┐
         │  com.nurkiewicz.download        (src/test)   │
         │  FileExamples, FileStorageStub,              │
         │  DownloadControllerSpec (src/test/groovy)     │
         └───┬──────────────────────────────────────────┘
             │ depends on main (same package)
             ├─▶ org.springframework.test.*
             ├─▶ org.springframework.web.context
             ├─▶ com.google.common.net.HttpHeaders
             └─▶ spock.lang.Specification

         ┌──────────────────────────────────────────────┐
         │  org.springframework.web.filter (src/test) ⚠ │
         │  Sha512ShallowEtagHeaderFilter               │
         └───┬──────────────────────────────────────────┘
             ├─▶ org.springframework.web.filter (same pkg)
             └─▶ com.google.common.hash

   No cycles. Main has zero dependencies on test packages.
```

## Internal Type Dependency Graph

```
   MainApplication
        │ (component scan)
        ▼
   DownloadController ──────────────▶ «FileStorage»
        │                                  △
        │                      ┌───────────┴───────────┐
        │              FileSystemStorage        FileStorageStub
        │                      │                      │
        │                      ▼                      ▼
        │              FileSystemPointer ──▶ «FilePointer»
        │                                         △
        └──▶ ExistingFile ───────────────────────┘
                  │
                  └──▶ ThrottlingInputStream ──▶ RateLimiter (Guava)

   Fan-in:  FilePointer 4 · FileStorage 3 · FileSystemPointer 3
   Fan-out: ExistingFile 6 · FileSystemPointer 5 · DownloadController 3
   Max depth: 4 · Cycles: 0
```

## Source File Layout

```
src/
├── main/
│   ├── java/com/nurkiewicz/download/
│   │   ├── MainApplication.java          @SpringBootApplication
│   │   ├── DownloadController.java       @RestController
│   │   ├── ExistingFile.java             domain core
│   │   ├── FileStorage.java              «interface» port
│   │   ├── FilePointer.java              «interface» port
│   │   ├── FileSystemStorage.java        adapter  @Profile("!test")
│   │   ├── FileSystemPointer.java        adapter
│   │   └── ThrottlingInputStream.java    decorator
│   └── resources/
│       └── logback.xml                   (also: served by the stub)
└── test/
    ├── groovy/com/nurkiewicz/download/
    │   └── DownloadControllerTest.groovy  ⚠ declares DownloadControllerSpec
    ├── java/com/nurkiewicz/download/
    │   ├── FileExamples.java
    │   └── FileStorageStub.java           @Profile("test")
    ├── java/org/springframework/web/filter/
    │   └── Sha512ShallowEtagHeaderFilter.java  ⚠ squatted package
    └── resources/
        └── download.txt                   "foobar"
```

## Deployment Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                         Client                              │
│                  (browser / HTTP client)                    │
└───────────────────────────┬─────────────────────────────────┘
                            │ HTTP (no TLS configured)
                            ▼
┌─────────────────────────────────────────────────────────────┐
│  JVM process — java -jar download-server-0.0.1-SNAPSHOT.jar │
│                                                             │
│   ┌───────────────────────────────────────────────────────┐ │
│   │  Embedded Tomcat (thread-per-request)                 │ │
│   │  ⚠ throttled transfers block worker threads           │ │
│   ├───────────────────────────────────────────────────────┤ │
│   │  Spring application context                           │ │
│   │  profile: (none)  →  FileSystemStorage active         │ │
│   │  profile: test    →  FileStorageStub   active         │ │
│   ├───────────────────────────────────────────────────────┤ │
│   │  DownloadController · ExistingFile · ThrottlingInputStream│
│   │  Actuator endpoints (unsecured)                       │ │
│   └───────────────────────────────────────────────────────┘ │
└───────────────────────────┬─────────────────────────────────┘
                            │ java.io / java.nio.file
                            ▼
                   ┌──────────────────┐
                   │ Local filesystem │
                   │  (currently only │
                   │   logback.xml)   │
                   └──────────────────┘

Stateless → N replicas can run behind a load balancer with no
coordination (content-hash ETags remain valid across instances).

Not present in the repository:
  ✗ Dockerfile / container image definition
  ✗ Kubernetes / ECS / CloudFormation / CDK / Terraform manifests
  ✗ CI pipeline definition
  ✗ application.properties / application.yml
  ✗ spring-boot-maven-plugin (no executable JAR is repackaged)
```

See [behavioral.md](behavioral.md) for runtime flows and [architecture.md](architecture.md) for context and security boundaries.
