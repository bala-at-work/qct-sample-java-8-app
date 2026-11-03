# Structural Diagrams

[← Back to index](../README.md) · Related: [behavioral.md](behavioral.md) · [architecture.md](architecture.md)

All diagrams are text-based for universal readability.

## 1. Component Diagram

```
┌──────────────────────────────────────────────────────────────────────┐
│                    download-server (single JVM)                      │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │ Embedded Tomcat + Spring MVC DispatcherServlet                 │  │
│  └──────────────────────────────┬─────────────────────────────────┘  │
│                                 │ dispatch /download/**              │
│  ┌──────────────────────────────▼─────────────────────────────────┐  │
│  │ DownloadController           «@RestController»                 │  │
│  │  + redirect(...)   GET|HEAD /download/{uuid}                   │  │
│  │  + download(...)   GET|HEAD /download/{uuid}/{filename}        │  │
│  └───────────┬────────────────────────────────────┬───────────────┘  │
│              │ findFile(uuid)                     │ new per request  │
│  ┌───────────▼───────────────┐      ┌─────────────▼───────────────┐  │
│  │ «interface» FileStorage   │      │ ExistingFile                │  │
│  └───────────┬───────────────┘      │  caching + response shaping │  │
│              │ implements            └──────┬──────────┬──────────┘  │
│  ┌───────────▼───────────────┐              │          │             │
│  │ FileSystemStorage         │   metadata   │          │ throttled   │
│  │ «@Component @Profile !test»│◄─────────────┘          │ body        │
│  └───────────┬───────────────┘                          │             │
│              │ creates                   ┌──────────────▼──────────┐  │
│  ┌───────────▼───────────────┐           │ ThrottlingInputStream   │  │
│  │ «interface» FilePointer   │◄──────────┤  (+ Guava RateLimiter)  │  │
│  └───────────┬───────────────┘ open()    └─────────────────────────┘  │
│              │ implements                                             │
│  ┌───────────▼───────────────┐                                        │
│  │ FileSystemPointer         │                                        │
│  │  SHA-512 ETag, probeType  │                                        │
│  └───────────┬───────────────┘                                        │
│              │                                                        │
│  ┌───────────▼───────────────┐   ┌─────────────────────────────────┐  │
│  │ Logback  (logback.xml)    │   │ Spring Boot Actuator (unused by │  │
│  └───────────────────────────┘   │ app code, auto-configured)      │  │
│                                  └─────────────────────────────────┘  │
└──────────────────────────────┬───────────────────────────────────────┘
                               │ read
                     ┌─────────▼──────────┐
                     │  Local filesystem   │
                     └────────────────────┘
```

## 2. Class Diagram

```
                     «interface»                       «interface»
                     FileStorage                       FilePointer
            ┌──────────────────────────┐    ┌──────────────────────────────────┐
            │ + findFile(UUID)         │    │ + open(): InputStream            │
            │     : Optional<FilePointer>│  │ + getSize(): long                │
            └────────────┬─────────────┘    │ + getOriginalName(): String      │
                         △                   │ + getEtag(): String              │
         ┌───────────────┴───────────────┐   │ + getMediaType()                 │
         │                               │   │     : Optional<MediaType>        │
┌────────────────────┐  ┌──────────────────┐ │ + matchesEtag(String): boolean   │
│ FileSystemStorage  │  │ FileStorageStub  │ │ + getLastModified(): Instant     │
│ «@Component»       │  │ «@Component»     │ │ + modifiedAfter(Instant):boolean │
│ «@Profile("!test")»│  │ «@Profile("test")│ └──────────────┬───────────────────┘
│ - log: Logger      │  │ - log: Logger    │                △
│ + findFile(UUID)   │  │ + findFile(UUID) │                │ implements
└─────────┬──────────┘  └──────────────────┘   ┌────────────────────────────────┐
          │ creates                            │ FileSystemPointer              │
          └───────────────────────────────────▶│ - target: File                 │
                                               │ - tag: HashCode                │
┌───────────────────────────────────┐          │ - mediaTypeOrNull: MediaType   │
│ DownloadController «@RestController»│        │ + «ctor»(File)                 │
│ - storage: FileStorage  «final»    │         │ + open() / getSize() / ...     │
│ + «ctor»(FileStorage) «@Autowired» │         │ + toString()                   │
│ + redirect(HttpMethod, UUID,       │         └────────────────────────────────┘
│     Optional<String>,Optional<Date>)│
│ + download(... same ...)            │        ┌────────────────────────────────┐
│ - findExistingFile(HttpMethod,UUID) │        │ «abstract» java.io.InputStream │
└──────────────┬─────────────────────┘         └───────────────┬────────────────┘
               │ creates (1 per request)                       △ extends
┌──────────────▼──────────────────────┐        ┌────────────────────────────────┐
│ ExistingFile                        │        │ ThrottlingInputStream          │
│ - log: Logger «static final»        │───────▶│ - target: InputStream  «final» │
│ - method: HttpMethod   «final»      │ wraps  │ - maxBytesPerSecond:RateLimiter│
│ - filePointer: FilePointer «final»  │        │ + read() / read(b) / read(b,o,l)│
│ - uuid: UUID           «final»      │        │ + skip / available / mark      │
│ + «ctor»(HttpMethod,FilePointer,UUID)│       │ + reset / markSupported / close│
│ + redirect(Optional,Optional)       │        └────────────────────────────────┘
│ + handle(Optional,Optional)         │
│ - serveWithCaching(..., Function)   │        ┌────────────────────────────────┐
│ - cached(Optional,Optional):boolean │        │ MainApplication                │
│ - redirectDownload(FilePointer)     │        │ «@SpringBootApplication»       │
│ - serveDownload(FilePointer)        │        │ + main(String[]) «static»      │
│ - resourceToReturn(FilePointer)     │        └────────────────────────────────┘
│ - buildResource(FilePointer)        │
│ - notModified(FilePointer)          │        ┌────────────────────────────────┐
│ - response(FilePointer,HttpStatus,  │        │ ShallowEtagHeaderFilter (Spring)│
│     Resource)                       │        └───────────────┬────────────────┘
│ - toMediaType(guava MediaType)      │                        △ extends
└─────────────────────────────────────┘        ┌────────────────────────────────┐
                                               │ Sha512ShallowEtagHeaderFilter  │
                                               │ # generateETagHeaderValue(byte[])│
                                               └────────────────────────────────┘
```

Relationship legend: `△` inheritance/realization, `──▶` association/dependency, `«»` stereotype.

## 3. Package / Module Dependency Graph

```
                      ┌─────────────────────────────┐
                      │ com.nurkiewicz.download     │   (main — 8 types)
                      │  web + domain + adapters    │
                      └──┬────┬────┬────┬────┬──────┘
                         │    │    │    │    │
       spring-boot ◄─────┘    │    │    │    └────► slf4j-api
       spring-web/webmvc ◄────┘    │    └─────────► commons-io (ONE_MB)
       spring-core/beans/context ◄─┘
       guava ◄──────────────────────────────────────┐
                                                    │
      ┌─────────────────────────────┐               │
      │ com.nurkiewicz.download     │ (test)        │
      │  FileStorageStub            │───────────────┤
      │  FileExamples               │               │
      │  DownloadControllerSpec     │──► spock, spring-test, groovy
      └──────────────┬──────────────┘
                     │ depends on main package
                     ▼
      ┌─────────────────────────────┐
      │ org.springframework.web.filter │ (test — squatted package)
      │  Sha512ShallowEtagHeaderFilter │──► guava.hash, spring-webmvc
      └─────────────────────────────┘

Cycles: none.   Fan-in leader: FilePointer (4).   Fan-out leader: FileSystemStorage (3).
```

## 4. Maven Build Structure

```
org.springframework.boot:spring-boot-starter-parent:1.2.3.RELEASE   (external parent)
        │ dependencyManagement + spring-boot-maven-plugin
        ▼
com.nurkiewicz.download:download-server:0.0.1-SNAPSHOT  (single module, JAR)
 ├── src/main/java        → compiled by maven-compiler-plugin 3.0 (source/target 1.8)
 ├── src/main/resources   → logback.xml
 ├── src/test/java        → compiled (test scope)
 ├── src/test/groovy      → ⚠ NO Groovy compiler plugin configured
 └── src/test/resources   → download.txt

Repositories: Maven Central (implicit)
              io.spring.repo.maven.milestone  ⚠ http:// (plaintext)
```

## 5. Deployment Architecture

```
          ┌────────────────┐
          │  HTTP client   │  (browser / curl / service)
          └───────┬────────┘
                  │ HTTP (no TLS configured in-app)
                  ▼
   ┌───────────────────────────────────┐
   │  download-server JVM process       │
   │  ─────────────────────────────────  │
   │  Spring Boot fat JAR               │
   │  Embedded Tomcat (thread-per-req)  │
   │  Profile: !test  ⇒ FileSystemStorage│
   │  Stateless (no session/cache/DB)   │
   │  Actuator endpoints: unsecured     │
   └──────────────┬────────────────────┘
                  │ file reads (classpath / local FS)
                  ▼
        ┌────────────────────┐
        │  Local filesystem   │
        └────────────────────┘

Horizontal scaling: possible (stateless app tier), but blocked in practice because
the current storage adapter reads a node-local classpath resource rather than
shared storage.

Not present in the repository: Dockerfile, Kubernetes manifests, CloudFormation/CDK/
Terraform, CI pipeline definition, application.properties/yml, reverse-proxy config.
```

## 6. Runtime Object Instantiation Scope

```
Singleton (Spring context)      Per request (new instance)       Per request (JDK)
─────────────────────────       ──────────────────────────       ─────────────────
DownloadController              ExistingFile                     FileInputStream
FileSystemStorage               FileSystemPointer                BufferedInputStream
(FileStorageStub in tests)      RateLimiter                      ThrottlingInputStream
Logback appenders               InputStreamResource              ResponseEntity
```
