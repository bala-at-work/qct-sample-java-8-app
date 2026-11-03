# Remediation Plan

[← Back to index](../README.md) · [Summary](summary.md)

Prioritized action items. Priority order is fixed: EOL runtimes/frameworks first, then outdated dependencies, then code quality and architecture. All sizing is expressed qualitatively — no time estimates.

## Priority 1 — High: EOL Runtimes and Frameworks

### R-01 Upgrade the JDK to a supported LTS release
- **Addresses:** TD-01
- **Action:** Replace the `maven-compiler-plugin` `<source>`/`<target>` pair with a `<maven.compiler.release>` property (or `<java.version>` inherited from a modern Boot parent) set to a supported LTS.
- **Complexity:** Moderate. The source uses only standard Java 8 features (lambdas, `java.util.Optional`, `java.time`, method references) — all forward-compatible. The one blocker is `new Integer("1234")` (see R-07).
- **Best practice:** Set the compiler `release` flag rather than `source`/`target` so the bootclasspath is validated.
- **Recommended vehicle:** `AWS/java-version-upgrade`.

### R-02 Upgrade Spring Boot to a supported 3.x release
- **Addresses:** TD-02
- **Action:** Bump `<parent>` to a current Spring Boot 3.x release and **delete** the explicit `<version>` elements from `spring-boot-starter-web`, `spring-boot-starter-actuator`, and `spring-boot-starter-test` so the parent BOM governs them.
- **Dependent on:** R-01 (Boot 3.x requires a modern LTS JDK).
- **Complexity:** Moderate. Breaking changes expected in: Actuator endpoint paths and exposure defaults, `@RequestHeader`/`Optional` binding, and `ResponseEntity.BodyBuilder` usage (the fluent API used in `ExistingFile.response` is stable).
- **Best practice:** Never pin starter versions when inheriting from `spring-boot-starter-parent`.

### R-03 Remove explicit Spring Framework version pins and migrate `javax` → `jakarta`
- **Addresses:** TD-03
- **Action:** Delete the `<version>` elements from all nine `org.springframework:*` dependencies (and ideally the dependencies themselves — `spring-boot-starter-web` already brings `spring-web`, `spring-webmvc`, `spring-core`, `spring-beans`, `spring-context`, `spring-aop`, `spring-expression` transitively). Delete the unused `<spring.version>` property.
- **Complexity:** Low for this codebase. No source file imports `javax.*` directly, so the Jakarta namespace migration is confined to the dependency graph.
- **Best practice:** Rely on BOM-managed versions; declare only the starters you need.

### R-04 Remove the plaintext HTTP Maven repository
- **Addresses:** TD-04
- **Action:** Delete the `io.spring.repo.maven.milestone` `<repositories>` block entirely. Every declared dependency is available from Maven Central, so no milestone repository is required. If a milestone repository is genuinely needed later, add it over HTTPS only.
- **Complexity:** Low.
- **Best practice:** Only resolve artifacts over TLS, and only from standard public registries or repositories already trusted by the organization.

### R-05 Implement a real `FileStorage` adapter
- **Addresses:** TD-05
- **Action:** Implement a production `FileStorage` that maps a UUID to actual stored content. The existing `FileStorage`/`FilePointer` ports require no change — only a new adapter class. Replace `new File(resource.getFile())` with a `Resource`-based or `Path`-based lookup that works inside a packaged JAR, and return `Optional.empty()` for unknown UUIDs instead of always returning a file.
- **Complexity:** Moderate; depends on the chosen backing store.
- **Best practice:** Validate/canonicalize any UUID-to-path mapping to prevent path traversal; never interpolate client input into a filesystem path.
- **Note:** The existing `@Profile("!test")` / `@Profile("test")` split means the test suite is unaffected by this change.

## Priority 2 — Medium: Outdated Dependencies

### R-06 Modernize the dependency set
- **Addresses:** TD-06 … TD-10
- **Actions:**
  | Dependency | Action |
  |---|---|
  | `guava:18.0` | Upgrade to a current release. Review `MoreObjects`, `RateLimiter` (still `@Beta`), and the `MediaType.charset().transform(...).or(...)` call in `ExistingFile.toMediaType` — the Guava `Optional` idioms there are the only API-churn risk. |
  | `commons-io:2.4` | Upgrade, or **remove**: its only use is the constant `FileUtils.ONE_MB`, trivially replaced by `1024L * 1024`. Removing eliminates a dependency outright. |
  | `commons-codec:1.10` | **Remove** — no source usage was found anywhere in the repository. |
  | `groovy-all:2.4.3` | Change scope to `test`, move to `org.apache.groovy` coordinates, and upgrade in lockstep with Spock (see R-10). |
  | `cglib-nodep:3.1` | **Remove** if Spock 2.x + modern Spring no longer require it; otherwise upgrade and scope to `test`. |
- **Best practice:** Before committing any target version, confirm the exact resolved coordinate has no known CVEs, and never downgrade below the version currently declared.

## Priority 3 — Low: Code Quality, Build Tooling, and Architecture

### R-07 Delete the deprecated, dead statement in `MainApplication`
- **Addresses:** TD-14. `MainApplication.java:10` — `Integer temp = new Integer("1234");` is unused and uses a constructor deprecated since Java 9. Delete the line. This is a hard blocker for a clean build on a modern JDK with deprecation warnings escalated.

### R-08 Clean up `pom.xml` structure
- **Addresses:** TD-11, TD-13. Remove the `maven-compiler-plugin` `3.0` pin (let the Boot parent manage it, or pin a current version); de-duplicate the `spring-test` declaration; move `<parent>` above `<properties>` for conventional ordering.

### R-09 Externalize the throttle rate
- **Addresses:** TD-16. `ExistingFile.java:99` hardcodes `RateLimiter.create(ONE_MB)`. Introduce a `@ConfigurationProperties`-bound property (e.g. `download.max-bytes-per-second`) with the current value as the default, and inject it into `ExistingFile` via `DownloadController`. Preserves behavior while making it tunable per environment.

### R-10 Repair and modernize the test suite
- **Addresses:** TD-18, TD-12. Rename `DownloadControllerTest.groovy` to `DownloadControllerSpec.groovy` so the filename matches the declared class (or rename the class). Then upgrade Spock to 2.x with the JUnit 5 platform alongside the Groovy upgrade. **All 12 existing specifications must be preserved** — adjust assertions for framework behavior changes, never delete or disable a specification.

### R-11 Resolve the orphaned ETag filter
- **Addresses:** TD-17. `src/test/java/org/springframework/web/filter/Sha512ShallowEtagHeaderFilter.java` is unregistered and unreferenced. Either delete it, or — if the SHA-512 ETag behavior is wanted — move it into `com.nurkiewicz.download`, register it as a `FilterRegistrationBean`, and add a specification covering it. Do not leave a class squatting the `org.springframework` namespace.

### R-12 Add missing `@Override` and configuration hygiene
- **Addresses:** TD-15, TD-21, TD-22. Add `@Override` to `FileStorageStub.findFile`. Promote `MainApplication` to `public` since it is referenced as a `@ContextConfiguration` class. Add an `application.yml` that explicitly restricts Actuator endpoint exposure rather than relying on defaults.

### R-13 Introduce automated quality enforcement
- **Addresses:** conventions gap. Add a static-analysis plugin (SpotBugs or PMD) and a formatter configuration so the conventions currently held only by convention are enforced mechanically.

## Dependency Order

```
R-07 (delete deprecated statement)
  └─> R-01 (JDK LTS upgrade)
        ├─> R-02 (Spring Boot 3.x)  ──> R-03 (drop Spring pins, jakarta)
        ├─> R-06 (dependency modernization)
        └─> R-10 (test suite repair + Spock 2.x)
R-04 (remove HTTP repository)   — independent, do first; unblocks resolution
R-08 (pom cleanup)              — independent
R-05 (real FileStorage adapter) — independent of the upgrade path
R-09, R-11, R-12, R-13          — after the upgrade lands
```

See [migration/component-order.md](../migration/component-order.md) for the component-level sequencing and [migration/validation-criteria.md](../migration/validation-criteria.md) for acceptance criteria.
