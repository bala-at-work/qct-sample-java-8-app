# Component Migration Order

[← Back to index](../README.md) · [Remediation plan](../technical-debt/remediation-plan.md)

Dependency-driven sequencing for modernizing this codebase. The project is a **single Maven module**, so there is no inter-module ordering to resolve — the ordering below is driven by the internal type dependency graph and by build-level prerequisites.

## Ordering Principles

1. **Unblock the build first.** The plaintext HTTP repository can prevent dependency resolution outright, and the deprecated-API line can fail a strict modern-JDK compile. Both are trivial edits.
2. **Stable abstractions before volatile implementations.** `FilePointer` (fan-in 4) and `FileStorage` (fan-in 3) are the highest-impact change surfaces; they should be touched early and then left alone.
3. **Leaves before orchestrators.** `ThrottlingInputStream` and `FileSystemPointer` have no internal dependents other than `ExistingFile`/`FileSystemStorage`, so they can be verified in isolation.
4. **Platform before features.** The JDK and framework upgrade must land before cosmetic or functional improvements, otherwise the latter are redone.
5. **Tests move with the code they cover.** All 12 specifications must remain executable at every checkpoint.

## Phase 0 — Build unblocking (no behavior change)

| Step | Component | Action | Verification |
|---|---|---|---|
| 0.1 | `pom.xml` | Remove the `<repositories>` block containing `http://repo.spring.io/milestone/` (R-04) | Dependency resolution succeeds from Maven Central alone |
| 0.2 | `pom.xml` | De-duplicate `spring-test`; delete the unused `<spring.version>` property (R-08) | POM parses; resolved dependency set unchanged |
| 0.3 | `MainApplication.java:10` | Delete `Integer temp = new Integer("1234");` (R-07) | No deprecation warning; `main` still calls `SpringApplication.run` |
| 0.4 | `DownloadControllerTest.groovy` | Rename the file to `DownloadControllerSpec.groovy` to match the declared class (R-10) | Groovy source name matches its public class |
| 0.5 | `pom.xml` | Add a Groovy compiler plugin for `src/test/groovy` so the specification is actually compiled | The 12 specifications are discovered by the build |

**Checkpoint 0:** the existing stack builds and all 12 specifications execute and pass. This establishes the behavioral baseline against which every later phase is compared. Do not proceed without it.

## Phase 1 — Platform upgrade (highest severity)

| Step | Component | Action | Depends on |
|---|---|---|---|
| 1.1 | `pom.xml` — compiler | Replace `<source>/<target>` with `maven.compiler.release` targeting a supported LTS; remove the `maven-compiler-plugin:3.0` pin (R-01) | 0.3 |
| 1.2 | `pom.xml` — parent | Bump `spring-boot-starter-parent` to a supported Spring Boot 3.x release (R-02) | 1.1 |
| 1.3 | `pom.xml` — starters | Delete the explicit `<version>` from `spring-boot-starter-web`, `-actuator`, `-test` so the parent BOM governs (R-02) | 1.2 |
| 1.4 | `pom.xml` — Spring Framework | Delete the nine `org.springframework:*` version pins; remove the seven artifacts already supplied transitively by `spring-boot-starter-web`, keeping only what is genuinely needed (R-03) | 1.3 |
| 1.5 | dependency graph | Confirm the `javax` → `jakarta` transition. **No source file imports `javax.*`**, so this is expected to be a dependency-graph-only change. | 1.4 |

**Checkpoint 1:** the application context starts on the new platform. Expect breakage concentrated in one place — see Phase 2.

## Phase 2 — Framework behavior reconciliation

Ordered by the internal dependency graph: ports first, then adapters, then the orchestrator, then the delivery edge.

| Step | Component | Risk | What to watch |
|---|---|---|---|
| 2.1 | `FilePointer`, `FileStorage` (ports) | **Low** | No Spring types in either signature set, so no change is expected. Verify first precisely because fan-in is highest (4 and 3). |
| 2.2 | `ThrottlingInputStream` | **Low** | Pure JDK + Guava `RateLimiter`. Only affected if Guava is upgraded in the same pass — `RateLimiter` remains `@Beta`. |
| 2.3 | `FileSystemPointer` | **Low** | Guava `Files.hash`, `Hashing`, `HashCode`, `MediaType`, `MoreObjects` — all stable APIs. `java.nio.file.Files.probeContentType` is JDK. |
| 2.4 | `FileSystemStorage` | **Low** | `@Component`/`@Profile` are stable. Note the `new File(resource.getFile())` defect here is addressed separately in Phase 4. |
| 2.5 | `ExistingFile` | **Medium** | `ResponseEntity.status(..).eTag(..).contentLength(..).lastModified(..)` fluent builder, `InputStreamResource`, and `toMediaType`'s Guava `Optional.transform/or`. The `lastModified(long)` overload and Guava `Optional` idioms are the likely friction points. |
| 2.6 | `DownloadController` | **High** | `@RequestHeader(IF_MODIFIED_SINCE) Optional<Date>` — the single most upgrade-fragile construct in the codebase. A silent binding regression yields empty `Optional`s, which **disables `304` responses while most specifications still pass**. The three `If-Modified-Since` specifications are the only guardrail; verify them explicitly and individually. |
| 2.7 | `MainApplication` | **Low** | `@SpringBootApplication` and `SpringApplication.run` are stable across Boot majors. |
| 2.8 | Actuator | **Medium** | Boot 3.x relocates endpoints under `/actuator` and changes exposure defaults. No configuration file currently exists; add one (see 4.3). |

**Checkpoint 2:** all 12 specifications pass on the new platform, with the three `If-Modified-Since` specifications and the two ETag specifications confirmed individually rather than only in aggregate.

## Phase 3 — Dependency modernization

| Step | Component | Action |
|---|---|---|
| 3.1 | `commons-codec` | **Remove** — no source usage anywhere in the repository |
| 3.2 | `spring-aop`, `spring-expression`, `spring-context-support` | **Remove** — no direct source references (they remain available transitively) |
| 3.3 | `commons-io` | Remove and replace `FileUtils.ONE_MB` with `1024L * 1024`, or upgrade. Removal eliminates a dependency for the sake of one constant. |
| 3.4 | `slf4j-api` | **Declare explicitly** — it is imported directly by three classes but currently arrives only transitively |
| 3.5 | `guava` | Upgrade. Review `ExistingFile.toMediaType` (Guava `Optional.transform/or`) and `RateLimiter` usage. |
| 3.6 | `groovy-all` | Rescope to `test`, move to `org.apache.groovy` coordinates, upgrade in lockstep with 3.7 |
| 3.7 | `spock-core`, `spock-spring` | Upgrade to Spock 2.x on the JUnit 5 platform (coupled to 3.6) |
| 3.8 | `cglib-nodep` | Remove if Spock 2.x and modern Spring no longer require it |
| 3.9 | `spring-boot-maven-plugin` | **Add** — currently absent, so no executable JAR is repackaged |

Before committing any target version, verify the exact resolved coordinate against a vulnerability database and never select a version below the one currently declared.

**Checkpoint 3:** all 12 specifications pass; the dependency tree contains no unused artifacts and no undeclared direct dependencies.

## Phase 4 — Functional and quality improvements

Independent of the upgrade path; may run in parallel with Phase 3 or after it.

| Step | Component | Action | Blast radius |
|---|---|---|---|
| 4.1 | `FileSystemStorage` | Implement a real `FileStorage` adapter behind the existing port. Return `Optional.empty()` for unknown UUIDs; replace `new File(resource.getFile())` with `Resource`/`Path` resolution (R-05) | **Zero on tests** — the `@Profile("test")` split means `FileStorageStub` is unaffected. Safest high-value change available. |
| 4.2 | `ExistingFile` + new config class | Externalize the throttle rate to `@ConfigurationProperties`, defaulting to the current 1 MB/s (R-09) | `ExistingFile`, `DownloadController` |
| 4.3 | `src/main/resources/application.yml` | Create it; restrict Actuator exposure explicitly; hold the throttle default (R-12) | Actuator surface |
| 4.4 | `FileSystemPointer` or the new adapter | Cache the SHA-512 hash keyed by (path, size, mtime) to remove the O(file size) per-request cost | Performance only |
| 4.5 | `Sha512ShallowEtagHeaderFilter` | Move into `com.nurkiewicz.download` and register it, or delete it (R-11) | None — currently unreferenced |
| 4.6 | `FileStorageStub`, `MainApplication` | Add the missing `@Override`; promote `MainApplication` to `public` (R-12) | None |
| 4.7 | new tests | Add unit tests for `ThrottlingInputStream` and `FileSystemPointer` — the two most algorithmically interesting classes, currently untested directly | Additive only |
| 4.8 | `pom.xml` | Add a static-analysis plugin and dependency vulnerability scanning (R-13) | Build only |

**Checkpoint 4:** all 12 original specifications plus the new unit tests pass; the service serves real content rather than its own `logback.xml`.

## Component Order Summary

```
  Phase 0  pom.xml (repo, dupes) → MainApplication (dead line) → Groovy filename + compiler plugin
                │
                ▼  [Checkpoint 0 — behavioral baseline established]
  Phase 1  compiler release → Boot parent → starters → Spring pins → jakarta
                │
                ▼  [Checkpoint 1 — context starts]
  Phase 2  FilePointer/FileStorage → ThrottlingInputStream → FileSystemPointer
           → FileSystemStorage → ExistingFile → DownloadController ⚠ → MainApplication → Actuator
                │
                ▼  [Checkpoint 2 — all 12 specs pass, conditional-request specs verified individually]
  Phase 3  remove unused → declare slf4j → guava → groovy+spock → boot plugin
                │
                ▼  [Checkpoint 3 — clean dependency tree]
  Phase 4  real storage adapter → config externalization → hash caching
           → filter cleanup → new unit tests → quality gates
                │
                ▼  [Checkpoint 4 — functionally complete]
```

## Risk-Ordered Component List

| Component | Upgrade risk | Reason |
|---|---|---|
| `DownloadController` | **High** | `Optional<Date>` header binding can regress silently |
| `ExistingFile` | **Medium** | `ResponseEntity` builder chain; Guava `Optional` idioms; highest fan-out (6) |
| Actuator configuration | **Medium** | Endpoint relocation and exposure-default changes in Boot 3.x |
| Test infrastructure | **Medium** | Groovy + Spock + JUnit platform must move together |
| `FileSystemPointer` | **Low** | Stable Guava and JDK APIs only |
| `ThrottlingInputStream` | **Low** | Pure JDK + `RateLimiter` |
| `FileSystemStorage` | **Low** | Trivial class; being replaced anyway |
| `FilePointer`, `FileStorage` | **Low** | Framework-free by design |
| `MainApplication` | **Low** | Two stable Boot APIs |

## Rollback Boundaries

Each phase is independently revertable because each ends at a checkpoint where all 12 specifications pass. Phase 0 and Phase 4.1 are the two safest units of work: Phase 0 changes no behavior, and Phase 4.1 cannot affect the test suite at all due to the profile split.

See [test-specifications.md](test-specifications.md) and [validation-criteria.md](validation-criteria.md).
