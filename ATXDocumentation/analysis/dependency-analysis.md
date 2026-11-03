# Dependency Analysis

- [Back to README](../README.md)
- Siblings: [code-metrics.md](code-metrics.md) · [complexity-analysis.md](complexity-analysis.md) · [security-patterns.md](security-patterns.md) · [tech-debt.md](tech-debt.md)
- See also: [../architecture/dependencies.md](../architecture/dependencies.md) · [../technical-debt/outdated-components.md](../technical-debt/outdated-components.md)

> Analysis performed by reading `pom.xml` and every source file's import list. No dependency resolution, build, or `dependency:tree` execution was performed — the transformation forbids building. Transitive versions are stated as those managed by `spring-boot-starter-parent:1.2.3.RELEASE`.

## Table of Contents
1. [Internal Dependency Analysis](#internal-dependency-analysis)
2. [External Dependency Criticality](#external-dependency-criticality)
3. [Declared vs Actually Used](#declared-vs-actually-used)
4. [Upgrade Blocking Order](#upgrade-blocking-order)
5. [Version Conflict Analysis](#version-conflict-analysis)
6. [Transitive Dependency Exposure](#transitive-dependency-exposure)
7. [Dependency Risk Register](#dependency-risk-register)
8. [Removal Candidates](#removal-candidates)
9. [Supply Chain](#supply-chain)

## Internal Dependency Analysis

### Dependency matrix
Rows depend on columns. `I` = implements, `●` = uses.

| | MainApp | Controller | ExistingFile | FileStorage | FilePointer | ThrottlingIS | FSStorage | FSPointer |
|---|---|---|---|---|---|---|---|---|
| **MainApplication** | — | | | | | | | |
| **DownloadController** | | — | ● | ● | | | | |
| **ExistingFile** | | | — | | ● | ● | | |
| **FileStorage** | | | | — | ● | | | |
| **FilePointer** | | | | | — | | | |
| **ThrottlingInputStream** | | | | | | — | | |
| **FileSystemStorage** | | | | I | ● | | — | ● |
| **FileSystemPointer** | | | | | I | | | — |

### Derived metrics
| Type | Fan-out | Fan-in | Instability | Role |
|---|---|---|---|---|
| `FilePointer` | 0 | 4 | 0.00 | Stable core abstraction |
| `FileStorage` | 1 | 4 | 0.20 | Stable port |
| `ThrottlingInputStream` | 0 | 1 | 0.00 | Independent utility |
| `MainApplication` | 0 | 1 | 0.00 | Entry point |
| `FileSystemPointer` | 1 | 3 | 0.25 | Stable adapter |
| `FileSystemStorage` | 3 | 0 | 1.00 | Leaf adapter |
| `DownloadController` | 2 | 1 | 0.67 | Instable adapter |
| `ExistingFile` | 2 | 1 | 0.67 | Instable policy |

### Structural findings
| Finding | Detail |
|---|---|
| Cycles | **0** — verified by inspection of all 12 types |
| Maximum depth | 4 (`MainApplication → DownloadController → ExistingFile → FilePointer`) |
| Dependency inversion | Fully observed. `DownloadController` and `ExistingFile` reference only interfaces; neither imports `FileSystemStorage` or `FileSystemPointer` |
| Leakage check | `java.io.File` appears in only two main classes (`FileSystemPointer`, `FileSystemStorage`) — the storage abstraction does not leak |
| Stability distribution | The two interfaces have the highest fan-in and lowest instability; concrete classes are instable. Matches the Stable Abstractions Principle |
| God classes | None — highest fan-out is 3 |
| Orphans | `Sha512ShallowEtagHeaderFilter` has fan-in 0 and is never registered — dead code |

## External Dependency Criticality

Criticality assessed by what breaks if the dependency is removed.

| Library | Version | Criticality | Blast radius if removed |
|---|---|---|---|
| `spring-boot-starter-web` | 1.2.3.RELEASE | **Critical** | No application at all — supplies MVC, embedded Tomcat, auto-configuration |
| `spring-web` | 4.1.6.RELEASE | **Critical** | `ResponseEntity`, `HttpMethod`, `HttpHeaders`, `MediaType` — used throughout `DownloadController` and `ExistingFile` |
| `spring-webmvc` | 4.1.6.RELEASE | **Critical** | `DispatcherServlet`, `@RestController` dispatch |
| `spring-core` | 4.1.6.RELEASE | **Critical** | `Resource`, `InputStreamResource` — the streaming response mechanism |
| `spring-beans` / `spring-context` | 4.1.6.RELEASE | **Critical** | `@Autowired`, `@Component`, `@Profile` |
| `guava` | 18.0 | **Critical** | Breaks 3 of 8 main classes — `RateLimiter` (throttling), `Hashing`/`HashCode` (ETags), `MediaType` (**in the `FilePointer` port signature**), `MoreObjects` (toString) |
| `slf4j-api` *(transitive)* | 1.7.x | Medium | `ExistingFile` and `FileSystemStorage` logging |
| `commons-io` | 2.4 | **Trivial** | One constant: `FileUtils.ONE_MB`. Replaceable with a one-line local constant |
| `spring-boot-starter-actuator` | 1.2.3.RELEASE | **None (functionally)** | No application code references it. Removing it loses only the unsecured default endpoints |
| `spring-context-support` | 4.1.6.RELEASE | **None** | No feature of it is used |
| `spring-aop` / `spring-expression` | 4.1.6.RELEASE | **None (directly)** | Transitively required by the Spring core container; the explicit pins add nothing |
| `commons-codec` | 1.10 | **None** | Zero imports anywhere |
| `cglib-nodep` | 3.1 | **None at runtime** | Test-only (Spock mocking), yet declared at `compile` scope |
| `groovy-all` | 2.4.3 | **None at runtime** | Test-only language runtime, yet declared at `compile` scope |
| `spock-core` / `spock-spring` | 1.0-groovy-2.4 | Test-critical | The entire specification |
| `spring-test` | 4.1.6.RELEASE | Test-critical | `MockMvc`, `@WebAppConfiguration`, `@ContextConfiguration` |
| `spring-boot-starter-test` | 1.2.3.RELEASE | Test-support | JUnit 4 runner that Spock 1.x executes under |

**Concentration risk.** Guava is the only non-Spring library on the critical path, and it is uniquely entangled: its `MediaType` appears in the `FilePointer` **port signature** and its `RateLimiter` in `ThrottlingInputStream`'s **public constructor**. A Guava major upgrade therefore propagates into two abstraction boundaries rather than staying inside an adapter. This is the single most architecturally significant dependency fact in the project.

## Declared vs Actually Used

Cross-referencing the 19 declarations against the import lists of all 12 source files.

| Declared dependency | Imported by main source? | Verdict |
|---|---|---|
| `spring-boot-starter-web` | ✅ (transitively supplies everything) | **Keep** |
| `spring-boot-starter-actuator` | ❌ no code reference | Keep only if the endpoints are wanted — and then secure them |
| `spring-boot-starter-test` | ✅ (test) | **Keep** |
| `guava` | ✅ `ExistingFile`, `FileSystemPointer`, `ThrottlingInputStream` | **Keep, upgrade** |
| `commons-io` | ✅ one static import (`FileUtils.ONE_MB`) | **Remove** — inline the constant |
| `commons-codec` | ❌ **zero imports** | **Remove** |
| `cglib-nodep` | ❌ (test-only, implicit) | **Remove or re-scope to `test`** |
| `groovy-all` | ❌ (test-only language runtime) | **Re-scope to `test`** |
| `spring-core` | ✅ `Resource`, `InputStreamResource` | **Unpin** — supplied by the web starter |
| `spring-beans` | ✅ `@Autowired` | **Unpin** — supplied transitively |
| `spring-context` | ✅ `@Component`, `@Profile` | **Unpin** — supplied transitively |
| `spring-web` | ✅ `ResponseEntity`, `HttpMethod`, … | **Unpin** — supplied transitively |
| `spring-webmvc` | ✅ `@RestController`, `@RequestMapping` | **Unpin** — supplied transitively |
| `spring-context-support` | ❌ no import | **Remove** |
| `spring-aop` | ❌ no direct import | **Remove** — supplied transitively |
| `spring-expression` | ❌ no direct import | **Remove** — supplied transitively |
| `spring-test` (×2) | ✅ (test) | **Keep one, unpin, delete the duplicate** |
| `spock-core` | ✅ (test) | **Keep, upgrade to Spock 2.x** |
| `spock-spring` | ✅ (test) | **Keep, upgrade to Spock 2.x** |

### Reduction potential
| Action | Count |
|---|---|
| Declarations removable outright | 5 (`commons-codec`, `commons-io`, `spring-context-support`, `spring-aop`, `spring-expression`) |
| Duplicate declarations removable | 1 (`spring-test`) |
| Declarations needing only unpinning (version element deleted) | 5 Spring artifacts, or removable entirely since the web starter supplies them |
| Scope corrections | 2 (`groovy-all`, `cglib-nodep`) |
| **Minimal correct dependency set** | **~6 declarations** — web starter, actuator (if wanted), guava, boot-starter-test, spock-core, spock-spring |

Reducing 19 declarations to roughly 6 is the single highest-value, lowest-risk change available in the POM, and it is a prerequisite for the Boot parent upgrade to take effect at all.

## Upgrade Blocking Order

Dependencies form an upgrade graph: some must move before others can.

```
 (1) Remove explicit Spring Framework version pins          ← MUST BE FIRST
          │  otherwise the Boot parent bump is a no-op
          ▼
 (2) Java 8 → 11 → 17 → 21
          │  Boot 3.x requires Java 17+; current Guava requires Java 11+
          ├──────────────────────────┐
          ▼                          ▼
 (3) Spring Boot 1.2.3 → 1.5     (4) Guava 18 → current
       → 2.0 → 2.7 → 3.x              │
          │                           │
          ├── Spock 1.x → 2.x  ◄──────┘
          │     (forced at Boot 2.2+, when starter-test adopts JUnit 5)
          │
          ├── Groovy 2.4 → 4.x
          │     (coordinate also moves org.codehaus → org.apache)
          │
          ▼
 (5) Delete commons-io / commons-codec / cglib / spring-context-support
          (independent — can happen at any point)
```

| Step | Blocked by | Blocks | Severity |
|---|---|---|---|
| Remove Spring pins | nothing | Everything — the Boot bump silently fails otherwise | 🔴 High |
| JDK upgrade | nothing | Boot 3.x, current Guava | 🔴 High |
| Boot parent upgrade | Spring pin removal | Spock 2.x, actuator config | 🔴 High |
| Guava upgrade | JDK 11+ | `FilePointer` port signature review | 🟠 Medium |
| Spock 2.x | Boot 2.2+ | Test execution continuing to work | 🟠 Medium |
| Dependency removals | nothing | nothing | 🟡 Low |

**Critical observation.** Step (1) is not merely a cleanup — it is a correctness gate. While the eight `4.1.6.RELEASE` pins exist, bumping `spring-boot-starter-parent` changes the starters but leaves Spring Framework at 4.1.6, producing a Boot/Spring pairing that upstream never tested. The failure mode is a runtime `NoSuchMethodError` rather than a compile error, so it would escape a build-only check.

## Version Conflict Analysis

| Conflict | Detail | Severity |
|---|---|---|
| **Boot BOM vs explicit Spring pins** | `spring-boot-starter-parent:1.2.3.RELEASE` manages Spring Framework versions; the POM overrides eight artifacts to `4.1.6.RELEASE`. The explicit pins win | 🔴 High |
| **Declared property vs actual pins** | `<spring.version>4.1.1.RELEASE</spring.version>` is declared but never referenced, while dependencies hard-code `4.1.6.RELEASE`. The POM documents one version and uses another | 🟡 Low (confusing, not breaking) |
| **Duplicate `spring-test`** | The same GAV declared twice at `test` scope. Harmless today, but the two copies could drift to different versions in a future edit | 🟡 Low |
| **`spring-test` excluded then re-added** | `spring-boot-starter-test` excludes `org.springframework:spring-test`, which is then declared explicitly at 4.1.6. This is deliberate (to control the version) but makes the effective version non-obvious | 🟡 Low |
| **`groovy-all` excluded then re-added** | `spock-spring` excludes `org.codehaus.groovy:groovy-all`, which is then declared explicitly at 2.4.3 — but at `compile` scope rather than `test` | 🟠 Medium |
| **Guava vs JDK `Files`** | Not a version conflict but a namespace collision: `com.google.common.io.Files` is imported in `FileSystemPointer`, forcing a fully-qualified `java.nio.file.Files` in the same constructor | 🟡 Low |

No duplicate-class or diverging-transitive-version conflicts were identified from the declaration set. A definitive conflict check would require `dependency:tree`, which cannot be run under this transformation's constraints.

## Transitive Dependency Exposure

Supplied by `spring-boot-starter-parent:1.2.3.RELEASE` and not pinned here. These matter because they are on the production classpath whether or not application code uses them.

| Transitive artifact | Boot 1.2.3 version | Used by application code? | Exposure note |
|---|---|---|---|
| `tomcat-embed-core` | 8.0.x | Indirectly (it is the HTTP server) | EOL servlet container; the primary network-facing component |
| `jackson-databind` | 2.4.x | **No** | On the classpath unused. Historically a frequent CVE carrier, including deserialization issues. Pure exposure |
| `hibernate-validator` | 5.1.x | **No** | Unused Bean Validation implementation on the classpath |
| `logback-classic` | 1.1.x | Yes (via SLF4J + `logback.xml`) | EOL logging backend |
| `slf4j-api` | 1.7.x | Yes (direct imports) | — |
| `snakeyaml` | Boot-managed | **No** | Pulled in by Boot for YAML config that does not exist here. Another unused classpath entry with CVE history |
| `junit` | 4.x | Indirectly (Spock 1.x runs on it) | Test scope |
| `mockito-core`, `hamcrest-*` | Boot 1.2.3 era | **No** | The Spock spec uses no mocking |

**Assessment.** Three unused libraries with notable CVE histories (`jackson-databind`, `hibernate-validator`, `snakeyaml`) sit on the production classpath purely as starter transitives. Because the parent is EOL, none of them can be patched without upgrading the parent. This is a concrete argument that the dependency risk here exceeds what the direct-dependency list suggests — and that the Boot parent upgrade is the single action that remediates the largest number of exposures at once.

## Dependency Risk Register

| # | Risk | Likelihood | Impact | Severity | Mitigation |
|---|---|---|---|---|---|
| 1 | An unpatchable CVE in Spring MVC 4.1.6 or Boot 1.2.3 | High (both EOL) | High | 🔴 **High** | Staged Boot upgrade to 3.x |
| 2 | Boot parent bump silently fails to move Spring Framework | **Certain** if attempted as-is | High | 🔴 **High** | Delete the eight Spring pins first |
| 3 | Unpatchable CVE in an EOL transitive (`jackson-databind`, `snakeyaml`, `hibernate-validator`) | Medium | High | 🔴 **High** | Upgrade the Boot parent; consider excluding genuinely unused transitives |
| 4 | Java 8 receives no free public updates | High | High | 🔴 **High** | Move to a supported LTS (Corretto 17/21) |
| 5 | Guava 18 API drift breaks the `FilePointer` port signature | Medium | Medium | 🟠 Medium | Upgrade Guava after the JDK; review `getMediaType()`'s signature |
| 6 | Test-only libraries shipped in the production artifact | **Certain** (`groovy-all`, `cglib` at compile scope) | Medium | 🟠 Medium | Re-scope to `test` or remove |
| 7 | Artifact substitution via the plain-HTTP milestone repository | Low | High | 🟠 Medium | Delete the repository block — all deps are on Maven Central |
| 8 | Spock 1.x stops being discovered when starter-test adopts JUnit 5 | High (at Boot 2.2+) | Medium | 🟠 Medium | Upgrade to Spock 2.x during the Boot 2.x stage |
| 9 | Dependency drift reintroducing debt | Medium | Medium | 🟠 Medium | Add CVE scanning to CI; verify every new version before pinning |
| 10 | `spring-test` duplicate declarations drifting apart | Low | Low | 🟡 Low | Keep one declaration |
| 11 | Milestone repository no longer serving the required artifacts | Medium | Low | 🟡 Low | Same as #7 — delete it |

## Removal Candidates

Changes that reduce the dependency surface with no behavioral impact.

| Dependency | Action | Justification | Risk of removal |
|---|---|---|---|
| `commons-codec:1.10` | **Delete** | Zero imports in any source file | None |
| `spring-context-support:4.1.6` | **Delete** | No feature used | None |
| `spring-aop:4.1.6` | **Delete** | No direct import; supplied transitively by the Spring container | None |
| `spring-expression:4.1.6` | **Delete** | No direct import; supplied transitively | None |
| `commons-io:2.4` | **Delete**, inline the constant | Used solely for `FileUtils.ONE_MB` | Very low — replace with `private static final long ONE_MB = 1024L * 1024L;` |
| `cglib-nodep:3.1` | **Delete or re-scope to `test`** | Needed only by Spock 1.x mocking, which this spec does not use; Spock 2.x uses ByteBuddy | Very low |
| `groovy-all:2.4.3` | **Re-scope to `test`** | Test language runtime; must not ship in the production JAR | None — it is not used at runtime |
| `spring-test` (second declaration) | **Delete** | Exact duplicate GAV | None |
| `spring-core`, `spring-beans`, `spring-context`, `spring-web`, `spring-webmvc` | **Delete the declarations** (not just the versions) | All five are supplied transitively by `spring-boot-starter-web` | Low — verify compilation after removal |
| `spring-boot-starter-actuator` | **Decide explicitly** | No application code uses it; as configured it is unauthenticated attack surface | Keep only with explicit `management.endpoints.web.exposure.include` configuration |
| `io.spring.repo.maven.milestone` repository | **Delete** | Plain HTTP; every dependency is a Maven Central GA release | None |

**Net effect.** 19 declarations reduce to roughly 6, with 2 scope corrections, 1 repository deletion and 1 three-line code change (`ONE_MB`). No behavior changes. This should be done **before** the version upgrades, because a smaller, correctly-scoped, unpinned dependency set is what allows the Boot parent bump to work as intended.

## Supply Chain

| Aspect | Finding | Severity |
|---|---|---|
| Artifact sources | Maven Central (implicit, via the parent) + `http://repo.spring.io/milestone/` | — |
| **Plain-HTTP resolution** | The milestone repository uses `http://`, allowing in-transit artifact substitution | 🟠 Medium |
| Custom/third-party repositories | 1 (the milestone repository above). No other non-standard repository is configured | 🟡 Low |
| Checksum / signature verification | Not configured beyond Maven defaults | 🟡 Low |
| Dependency locking | None — no `dependencyManagement` of its own beyond the parent, and no lockfile mechanism (Maven has no native lockfile) | 🟡 Low |
| Build-script integrity | Maven wrapper uses `distributionType=only-script` with Maven 3.9.8 — the one current piece of tooling | — |
| Automated vulnerability scanning | **None.** No CI at all, so no scanning gate exists | 🟠 Medium |
| Dependency update automation | Git history shows Dependabot Spring upgrade merges that were subsequently reverted by `73556be reset pom.xml to original version` | — |
| SBOM generation | None | 🟡 Low |

**Recommendations.** Delete the plain-HTTP repository rather than switching it to HTTPS, since it is unnecessary. Add automated CVE scanning as part of introducing CI (P2.10 / P3.10 in [../technical-debt/remediation-plan.md](../technical-debt/remediation-plan.md)), and verify each target version against a vulnerability database before pinning it during the upgrade. Do not introduce any new custom or third-party repository — Maven Central covers every dependency this project needs.
