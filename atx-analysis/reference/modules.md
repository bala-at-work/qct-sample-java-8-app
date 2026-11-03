# Modules

[← Back to index](../README.md) · Related: [program-structure.md](program-structure.md) · [../architecture/dependencies.md](../architecture/dependencies.md)

## Build Modules

Single Maven module — there is no `<modules>` aggregation.

| Coordinate | Path | Packaging |
|---|---|---|
| `com.nurkiewicz.download:download-server:0.0.1-SNAPSHOT` | `/repo` | Spring Boot JAR (parent default) |

Parent: `org.springframework.boot:spring-boot-starter-parent:1.2.3.RELEASE` (external, `relativePath` blanked so it resolves from the repository).

## Source Roots

| Root | Language | Contents |
|---|---|---|
| `src/main/java` | Java 8 | 8 production types, all in one package |
| `src/main/resources` | — | `logback.xml` |
| `src/test/java` | Java 8 | 3 test support types across 2 packages |
| `src/test/groovy` | Groovy 2.4 | 1 Spock specification (**no Groovy compiler plugin is configured in `pom.xml`**) |
| `src/test/resources` | — | `download.txt` fixture |

## Packages

### `com.nurkiewicz.download` (main) — the entire application
| Type | Role |
|---|---|
| `MainApplication` | bootstrap / component-scan root |
| `DownloadController` | web adapter |
| `ExistingFile` | domain logic (HTTP caching + response shaping) |
| `FileStorage`, `FilePointer` | ports |
| `FileSystemStorage`, `FileSystemPointer` | filesystem adapters |
| `ThrottlingInputStream` | streaming utility |

Outbound package dependencies: `java.io`, `java.net`, `java.time`, `java.util`, `java.util.function`, `java.nio.file`, `org.springframework.{beans,core.io,http,web.bind.annotation,context.annotation,stereotype,boot}`, `com.google.common.{hash,io,net,base,util.concurrent}`, `org.apache.commons.io`, `org.slf4j`.

Inbound dependencies: none (no other package depends on it except the test packages).

### `com.nurkiewicz.download` (test)
| Type | Role |
|---|---|
| `FileStorageStub` | profile-`test` port implementation |
| `FileExamples` | fixtures |
| `DownloadControllerSpec` (Groovy) | end-to-end MockMvc specification |

### `org.springframework.web.filter` (test)
| Type | Role |
|---|---|
| `Sha512ShallowEtagHeaderFilter` | framework-package subclass overriding a protected ETag hook; unwired |

This package placement is deliberate package squatting; it is the one structural deviation in the project. See [../architecture/patterns.md](../architecture/patterns.md) item A1.

## Package Dependency Graph

```
org.springframework.web.filter (test) ──> com.google.common.hash
                                      └─> org.springframework.web.filter (framework)

com.nurkiewicz.download (test) ──> com.nurkiewicz.download (main)
                                └─> spock.lang, org.springframework.test.*, com.google.common.net

com.nurkiewicz.download (main) ──> spring-{core,beans,context,web,webmvc}, spring-boot
                               ├─> guava
                               ├─> commons-io
                               └─> slf4j-api
```

Acyclic at every level. No internal module boundaries exist to violate, which is proportionate to a 652-line codebase.

## Module-Level Observations

- The flat single-package layout is appropriate at this size; if the storage layer grows (multiple backends, S3, metadata store), splitting into `web`, `domain`, and `storage` packages would preserve the existing port boundaries.
- `src/test/groovy` is not covered by any configured compiler plugin, so the Spock specification is effectively orphaned from the declared build. Any modernization must add `gmavenplus-plugin` (or move the spec) for the tests to participate.
