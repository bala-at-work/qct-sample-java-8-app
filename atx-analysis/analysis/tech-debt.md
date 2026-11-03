# Technical Debt Assessment

- [Back to README](../README.md) · [Root technical debt report](../technical-debt-report.md)
- Siblings: [code-metrics.md](code-metrics.md) · [complexity-analysis.md](complexity-analysis.md) · [dependency-analysis.md](dependency-analysis.md) · [security-patterns.md](security-patterns.md)
- Detailed debt documents: [../technical-debt/summary.md](../technical-debt/summary.md) · [../technical-debt/outdated-components.md](../technical-debt/outdated-components.md) · [../technical-debt/maintenance-burden.md](../technical-debt/maintenance-burden.md) · [../technical-debt/remediation-plan.md](../technical-debt/remediation-plan.md)

> This document is the analysis-side consolidation of technical debt, cross-referencing the metric, complexity, dependency and security findings. The executive view and the AWS transformation recommendation live in [../technical-debt-report.md](../technical-debt-report.md).

## Table of Contents
1. [Debt Profile](#debt-profile)
2. [Priority 1 — Runtime and Framework Debt](#priority-1--runtime-and-framework-debt)
3. [Priority 2 — Dependency Debt](#priority-2--dependency-debt)
4. [Priority 3 — Code Quality and Architectural Debt](#priority-3--code-quality-and-architectural-debt)
5. [Code-Level Findings](#code-level-findings)
6. [Build Hygiene](#build-hygiene)
7. [Dead Code](#dead-code)
8. [Conventions](#conventions)
9. [Test Debt](#test-debt)
10. [Operational Debt](#operational-debt)
11. [Documentation Debt](#documentation-debt)
12. [Debt Not Present](#debt-not-present)
13. [Interest Analysis](#interest-analysis)
14. [Consolidated Register](#consolidated-register)

## Debt Profile

This codebase has an unusual and favourable debt profile: **structural quality is high while platform currency is uniformly poor.**

| Debt category | Volume | Severity | Notes |
|---|---|---|---|
| Runtime / framework currency | Pervasive | 🔴 High | Java 8, Boot 1.2.3, Spring 4.1.6 — all EOL |
| Dependency currency | 18 of 18 dependencies | 🟠 Medium | Not one dependency is at a current version |
| Security posture | 7 findings at Medium or above | 🟠 Medium | Mostly consequences of the EOL stack plus a missing access-control layer |
| Build and test wiring | 3 defects | 🟠 Medium | Unregistered Groovy root, no repackage plugin, no CI |
| Configuration externalization | Complete absence | 🟠 Medium | Zero configuration files |
| Code quality | 7 localized findings | 🟡 Low | All small and mechanically fixable |
| Architectural debt | **Effectively none** | ✅ | 0 cycles, correct dependency inversion, ideal stability distribution |
| Complexity debt | **None** | ✅ | No method exceeds cyclomatic complexity 3 |
| Documentation | Near-total absence | 🟡 Low | 1 Javadoc comment, 2-line README |
| Functional completeness | 1 major gap | 🟠 Medium | Storage is an acknowledged stub |

**Interpretation.** Debt is concentrated in the *environment around the code* rather than in the code itself. That is the cheaper kind to repay: version upgrades and build wiring are mechanical, whereas architectural debt would require redesign. Consequently the modernization risk here is lower than the raw version numbers suggest — provided the test suite is activated first so that behavior can be verified.

## Priority 1 — Runtime and Framework Debt

🔴 **High severity.** Prioritized first per the fixed scheme: EOL/deprecated/outdated runtimes and frameworks before all else.

| Finding | Current | Evidence | Why it is High |
|---|---|---|---|
| Java language level | `1.8` | `pom.xml` → `maven-compiler-plugin:3.0` `<source>`/`<target>` | No free public updates; three LTS generations behind; blocks dependencies requiring Java 17+ |
| Spring Boot | 1.2.3.RELEASE (2015) | Parent POM + 3 starters | Fully EOL — no security fixes will ever be issued |
| Spring Framework | 4.1.6.RELEASE | 9 explicitly pinned artifacts | EOL; a Spring MVC CVE would be unpatchable |
| Spring pins defeating the Boot BOM | 4.1.6 overriding Boot-managed versions | `pom.xml` dependency block | A parent bump silently fails to move Spring Framework, producing an untested pairing that fails at **runtime**, not compile time |

### Compounding factors
- The language level is set only inside the compiler-plugin configuration — there is no `<java.version>` property — so an upgrade must edit the plugin block rather than a property.
- A `<spring.version>4.1.1.RELEASE</spring.version>` property is declared but never used, while the dependencies pin 4.1.6. The POM documents one version and uses another.
- Three EOL transitive libraries with notable CVE histories (`jackson-databind` 2.4.x, `snakeyaml`, `hibernate-validator` 5.1.x) sit on the production classpath entirely unused — see [dependency-analysis.md](dependency-analysis.md#transitive-dependency-exposure). They cannot be patched while the parent is EOL.

### Upgrade path
Remove the Spring pins → Java 8 → 11 → 17 → 21 → Boot 1.5 → 2.0 → 2.7 → 3.x. Full detail and ordering in [../technical-debt/remediation-plan.md](../technical-debt/remediation-plan.md#phase-1--high-severity-runtime-and-framework).

## Priority 2 — Dependency Debt

🟠 **Medium severity.** Outdated runtime/production dependencies.

| Dependency | Version | Year | Issue |
|---|---|---|---|
| `com.google.guava:guava` | 18.0 | 2014 | Many majors behind. Critical path: `RateLimiter`, `Hashing.sha512`, `MediaType` (**in the `FilePointer` port signature**), `MoreObjects`. Current Guava requires Java 11+, so the JDK must move first |
| `commons-io:commons-io` | 2.4 | 2012 | Used for exactly one constant (`FileUtils.ONE_MB`) — removable |
| `commons-codec:commons-codec` | 1.10 | 2014 | **Zero imports anywhere** — pure dead weight |
| `cglib:cglib-nodep` | 3.1 | 2014 | Test-only (Spock mocking) yet at `compile` scope |
| `org.codehaus.groovy:groovy-all` | 2.4.3 | 2015 | Test language runtime at `compile` scope — ships into the production artifact |

### Scope and hygiene defects
| Defect | Severity | Impact |
|---|---|---|
| `groovy-all` at `compile` scope | 🟠 Medium | Large test-only library in the production JAR; widened attack surface |
| `cglib-nodep` at `compile` scope | 🟠 Medium | Same class of problem |
| `spring-test` declared twice | 🟡 Low | Maintenance trap — the copies could drift |
| 7 redundant Spring artifacts | 🟡 Low | `spring-core`, `-beans`, `-context`, `-context-support`, `-web`, `-webmvc`, `-aop`, `-expression` are all supplied transitively by `spring-boot-starter-web` |
| Plain-HTTP milestone repository | 🟠 Medium | Supply-chain exposure; also a repository that no longer serves these artifacts |

**Reduction opportunity.** 19 declarations collapse to roughly 6 with no behavioral change — see [dependency-analysis.md](dependency-analysis.md#removal-candidates). This is the highest-value, lowest-risk POM change available, and it is a prerequisite for the Boot parent upgrade to take effect.

## Priority 3 — Code Quality and Architectural Debt

🟡 **Low severity.** All findings here are localized and mechanically fixable. Architectural debt is essentially absent.

### Architecture scorecard
| Property | Result |
|---|---|
| Dependency cycles | 0 |
| Dependency inversion | Fully observed — `DownloadController` and `ExistingFile` reference only interfaces |
| Storage abstraction leakage | None — `java.io.File` appears in only the two `FileSystem*` adapters |
| God classes | None — maximum fan-out is 3 |
| Stability distribution | Interfaces have the highest fan-in and lowest instability (Stable Abstractions Principle satisfied) |
| Maximum cyclomatic complexity | 3 |
| Maximum nesting depth | 2 |
| Immutability | 11 of 11 fields `final`; zero setters |

**No architectural remediation is warranted.** Any proposal to restructure this code for quality reasons should be declined; the effort belongs on the version upgrades.

## Code-Level Findings

| # | Finding | Location | Severity | Fix |
|---|---|---|---|---|
| C1 | `Integer temp = new Integer("1234");` — constructor deprecated for removal since Java 9, and the local is never read | `MainApplication.main` | 🟡 Low | **Delete the line** |
| C2 | Full-file SHA-512 hash plus `probeContentType` executed in a constructor, on every request, including those answered with `304` or `301` | `FileSystemPointer(File)` | 🟡 Low now, 🟠 Medium with real storage | Compute lazily; cache by (path, size, lastModified). Keep SHA-512 |
| C3 | `IOException` and `FileNotFoundException` rewrapped as `IllegalArgumentException` | `FileSystemPointer` | 🟡 Low | Use `UncheckedIOException` or a domain exception so callers can distinguish caller error from I/O failure |
| C4 | `URISyntaxException` rewrapped as `IllegalArgumentException` | `ExistingFile.redirectDownload` | 🟡 Low | Same; also validate that `getOriginalName()` is URI-safe |
| C5 | Hard-coded 1 MB/s throttle rate | `ExistingFile.buildResource` | 🟡 Low | Externalize to configuration with 1 MB/s as the default |
| C6 | `modifiedAfter(Instant)` name reads as the inverse of its semantics — it returns `true` when the *client's* cached copy is still valid | `FilePointer`, `FileSystemPointer` | 🟡 Low | Rename to something like `clientCopyStillValid`. **Note:** this is a public interface method; renaming it is an API change and must be weighed against compatibility for any downstream consumer |
| C7 | `getEtag()` must return a *pre-quoted* value — an undocumented obligation on every implementation | `FilePointer` contract | 🟡 Low | Document the contract in Javadoc |
| C8 | Guava `MediaType` and `RateLimiter` appear on abstraction boundaries (`FilePointer` port signature; `ThrottlingInputStream` public constructor) | `FilePointer`, `ThrottlingInputStream` | 🟡 Low | A Guava major upgrade propagates into the port contract rather than staying in an adapter. Consider a library-neutral type if the port is extended |
| C9 | Guava `com.google.common.io.Files` import forces a fully-qualified `java.nio.file.Files` in the same constructor — a readability trap | `FileSystemPointer` | 🟡 Low | Import only what is needed, or alias clearly |
| C10 | `new File(resource.getFile())` — `URL.getFile()` returns a URL-encoded path, so this breaks for paths with spaces or non-ASCII characters and fails entirely inside a JAR | `FileSystemStorage`, `FileExamples` | 🟡 Low | Use `Paths.get(resource.toURI())` or stream the resource directly |
| C11 | `{filename}` path variable mapped but never bound or validated | `DownloadController.download` | 🟡 Low | Validate against `getOriginalName()` and redirect on mismatch, or document as decorative. Add `Content-Disposition` |
| C12 | `DownloadController.redirect` and `.download` are byte-for-byte identical apart from the mapping and the delegated method | `DownloadController` | 🟡 Low | Acceptable duplication; the only cost is editing the parameter list twice |
| C13 | `@Profile("!test")` on a production bean — production configuration expressed in terms of a testing concept | `FileSystemStorage` | 🟡 Low | Replace with a test-side `@Primary`/`@TestConfiguration` override once real storage exists |
| C14 | `FileSystemPointer` reads `size` and `lastModified` live from disk while `tag` is captured at construction, so the ETag and `Content-Length` can describe different file versions | `FileSystemPointer` | 🟡 Low | Capture all metadata at the same instant, or make all of it lazy and consistent |

## Build Hygiene

| # | Finding | Severity | Detail |
|---|---|---|---|
| B1 | `src/test/groovy` not registered as a test-source root | 🟠 **Medium** | No `gmavenplus-plugin`, `groovy-eclipse-compiler`, or `build-helper-maven-plugin`. **The project's entire 12-feature behavioral specification is neither compiled nor executed by a default build.** Tests that exist but never run imply false assurance |
| B2 | `spring-boot-maven-plugin` not declared in `<build><plugins>` | 🟠 Medium | Only plugin *management* is inherited from the parent, so `mvn package` produces a plain JAR rather than an executable fat JAR. The application cannot be launched with `java -jar` as built |
| B3 | Unused `<spring.version>4.1.1.RELEASE</spring.version>` property contradicting the 4.1.6 pins in use | 🟡 Low | The POM documents one version and uses another |
| B4 | Duplicate `spring-test` declaration | 🟡 Low | Same GAV twice |
| B5 | `maven-compiler-plugin` 3.0 (2012) — the only declared plugin | 🟡 Low | Later versions are needed for `release`-based compilation and modern JDK support |
| B6 | Language level hidden in plugin configuration rather than a `<java.version>` property | 🟡 Low | Tools and humans looking for the conventional Boot property will not find it |
| B7 | No surefire configuration | 🟡 Low | Relies on parent defaults; Spock 1.x will stop being discovered once `spring-boot-starter-test` adopts the JUnit 5 platform at Boot 2.2+ |
| B8 | No `.gitignore`; `.DS_Store` committed | 🟡 Low | `target/` and IDE files are at permanent risk of being committed |

## Dead Code

| Item | Location | Severity | Action |
|---|---|---|---|
| `Sha512ShallowEtagHeaderFilter` — never registered as a bean or servlet filter; sits in the `org.springframework.web.filter` package to reach a protected member; targets a signature that later Spring versions changed | `src/test/java/org/springframework/web/filter/` | 🟡 Low | **Delete.** Recreate against the current API only if a SHA-512 shallow ETag filter is genuinely wanted |
| `Integer temp` local | `MainApplication.main` | 🟡 Low | Delete |
| `commons-codec` dependency | `pom.xml` | 🟡 Low | Delete — zero imports |
| `spring-context-support`, `spring-aop`, `spring-expression` declarations | `pom.xml` | 🟡 Low | Delete — no direct use; supplied transitively |
| `<spring.version>` property | `pom.xml` | 🟡 Low | Delete |
| `spring-boot-starter-actuator` | `pom.xml` | 🟠 Medium | Not dead exactly — it exposes endpoints — but no application code uses it, and as configured it is unauthenticated attack surface. Decide explicitly: remove, or keep and secure |

## Conventions

| Finding | Severity | Detail |
|---|---|---|
| `DownloadControllerTest.groovy` declares class `DownloadControllerSpec` | 🟡 Low | Filename/class-name mismatch breaks navigation and naming-convention-based tooling. Rename the file to match the class |
| A project class placed in a third-party package (`org.springframework.web.filter`) | 🟠 Medium | Invisible to audits that scan project packages; couples to Spring internals |
| `public` visibility on types not intended for external use (`FileSystemPointer`, `ExistingFile`) | 🟡 Low | Would be tightenable with sub-packages. **Note:** these are public API today; narrowing visibility is a breaking change for any downstream consumer and should not be done casually |
| Flat package structure — all 8 main types in one package | 🟡 Low | Hides the M1–M5 logical boundaries the design actually honours. Acceptable at this size |
| Tabs for indentation throughout | ✅ | Internally consistent; not debt |
| `@Override` applied to all 20 overriding methods | ✅ | Good discipline |

## Test Debt

| # | Finding | Severity |
|---|---|---|
| T1 | **Zero tests execute in a default build** (B1 above) | 🟠 **Medium** — the most consequential test finding |
| T2 | No unit tests — all 12 feature methods are integration tests through the full Spring MVC stack | 🟡 Low |
| T3 | `ThrottlingInputStream` untested, despite being the most reusable and most unit-testable class in the codebase | 🟡 Low |
| T4 | The `301` redirect path is entirely unspecified — one of the system's two endpoints has no test coverage | 🟠 Medium |
| T5 | `FileSystemStorage` (the production adapter) is never exercised — the `test` profile replaces it | 🟡 Low (it is a stub) |
| T6 | Throttling behavior is not asserted anywhere | 🟡 Low |
| T7 | `400` (malformed UUID) and `405` (unmapped verb) paths untested | 🟡 Low |
| T8 | No coverage instrumentation (no JaCoCo); actual coverage is unknown | 🟡 Low |
| T9 | `FileExamples` uses `UUID.randomUUID()` in static initializers, so fixture UUIDs differ on every JVM run — correct but not reproducible | 🟡 Low |
| T10 | The specification depends on `probeContentType` returning `text/plain` for `download.txt`, which is platform-dependent | 🟡 Low |

**Offsetting strength.** The 12 feature methods that do exist cover precisely the subtlest and most upgrade-fragile behavior — ETag matching, `If-Modified-Since` evaluation in both directions, HEAD body suppression, `Content-Length` and `Content-Type`. This is exactly the right safety net for a framework migration. The debt is that it does not run. Fixing B1 converts an unused asset into the migration's primary verification mechanism.

## Operational Debt

| # | Finding | Severity |
|---|---|---|
| O1 | **No configuration file at all** — no `application.properties`/`yml`. Port, log levels, actuator exposure and throttle rate are all framework defaults or source literals; nothing is tunable per environment | 🟠 Medium |
| O2 | **Storage is an acknowledged stub** — `FileSystemStorage.findFile` ignores its UUID and always returns `logback.xml`. The application is non-functional as a download server outside the `test` profile | 🟠 Medium |
| O3 | No UUID→file metadata store, and no upload/registration path to mint UUIDs | 🟠 Medium |
| O4 | No CI/CD — no `.github/`, Jenkinsfile, or buildspec. No automated build, test, or vulnerability gate | 🟡 Low |
| O5 | No containerization — no Dockerfile | 🟡 Low |
| O6 | No infrastructure as code | 🟡 Low |
| O7 | Verbose production logging — root `DEBUG`, `com.nurkiewicz` at `ALL`, logging absolute paths and hashes | 🟡 Low |
| O8 | Minimal observability — actuator defaults only; no custom metrics, tracing, or structured logging | 🟡 Low |
| O9 | No `Range` / partial-content support, so interrupted downloads cannot be resumed — a notable gap for a deliberately throttled download server | 🟡 Low |
| O10 | No per-client rate limiting; one client can consume the whole Tomcat thread pool | 🟠 Medium |

## Documentation Debt

| Metric | Value | Severity |
|---|---|---|
| Javadoc'd types | 1 of 12 | 🟡 Low |
| Javadoc'd public methods | 0 of 21 | 🟡 Low |
| Inline comments in main source | 1 | 🟡 Low |
| `README.md` length | 2 lines — documents neither the endpoints, nor how to run the application, nor the stub-storage caveat | 🟡 Low |
| Architecture documentation (pre-existing) | None | 🟡 Low |

**Mitigating factors.** The code is highly readable (short methods, descriptive names, `Optional`-based signatures), and the single Javadoc comment present is the most important one in the project — the declaration that `FileSystemStorage` is a stub requiring replacement. The Spock specification is the de facto behavioral documentation; activating it (B1) also restores the documentation value of those 12 named feature methods.

**Note on guardrails.** The repository-level `LICENSE` and `CODE_OF_CONDUCT.md` must be preserved unchanged, as must the `FileSystemStorage` Javadoc block — it is load-bearing documentation, not a candidate for cleanup.

## Debt Not Present

Verified absent, so that effort is not misdirected toward non-problems:

| Common debt category | Status |
|---|---|
| Dependency cycles | None |
| God classes / god methods | None — largest class 121 lines, largest method 11 lines |
| Deep inheritance hierarchies | Maximum depth 2 beyond `Object` |
| Copy-paste duplication | Only the two near-identical controller handlers |
| Long parameter lists | Maximum 4 parameters |
| Primitive obsession | Avoided — `UUID`, `Instant`, `MediaType`, `Optional` used appropriately |
| Mutable global state | None — zero static mutable fields |
| Thread-safety defects | None — 100% `final` fields, stateless singletons, no shared mutable state |
| Null-handling debt | Avoided via pervasive `Optional` |
| SQL injection / N+1 queries / ORM misuse | No datastore exists |
| Hard-coded secrets | None |
| Weak or deprecated cryptography | None — SHA-512 only |
| Dynamic code execution | None |
| Untrusted deserialization | None |
| Commented-out code blocks | None |
| TODO/FIXME/HACK markers | None |
| Swallowed exceptions (empty catch blocks) | None — exceptions are rewrapped, not discarded |
| Reflection or classpath scanning of untrusted input | None |

## Interest Analysis

What each debt category costs if left unaddressed.

| Debt | Interest accrual | Trend |
|---|---|---|
| EOL framework and runtime | **Compounding.** Every month adds unpatchable CVE exposure, and the migration distance to current versions grows as more intermediate breaking changes accumulate | Worsening |
| EOL transitive libraries | **Compounding.** `jackson-databind`, `snakeyaml` and `hibernate-validator` have active CVE histories and cannot be patched while the parent is EOL | Worsening |
| Unregistered test source root | **High immediate cost.** Blocks safe execution of every other remediation, since behavior cannot be verified | Flat, but gates everything |
| Dependency currency | **Compounding.** Each major release skipped makes the eventual jump harder; Guava in particular has had API removals | Worsening |
| Missing configuration externalization | **Flat.** Painful on first deployment, then constant | Flat |
| Code-level findings (C1–C14) | **Flat and small.** None grows worse with time | Flat |
| Architectural debt | **None to accrue** | — |
| Stub storage | **Flat but blocking.** The application cannot be deployed for its stated purpose until addressed | Flat |
| Missing CI and CVE scanning | **Compounding indirectly.** Without a gate, the dependency set drifts back into debt after remediation — as git history already shows (Dependabot upgrades were reverted) | Worsening |
| Documentation | **Slowly compounding** as original authors' context is lost | Slowly worsening |

**Conclusion.** Three categories compound — framework currency, dependency currency, and the absence of a CI gate — and they interact: without CI scanning, repaid dependency debt returns. The remediation sequence in [../technical-debt/remediation-plan.md](../technical-debt/remediation-plan.md) therefore front-loads the test-suite activation (so upgrades are verifiable) and ends with CI (so the repayment holds).

## Consolidated Register

| ID | Finding | Severity | Category | Plan item |
|---|---|---|---|---|
| D1 | Java 8 language/runtime target | 🔴 High | Runtime | P1.3 |
| D2 | Spring Boot 1.2.3.RELEASE (EOL) | 🔴 High | Framework | P1.2 |
| D3 | Spring Framework 4.1.6.RELEASE (EOL) | 🔴 High | Framework | P1.1, P1.2 |
| D4 | Spring pins defeat the Boot parent BOM | 🔴 High | Framework | P1.1 |
| D5 | EOL transitives with CVE history on the production classpath | 🔴 High | Dependency / Security | P1.2 |
| D6 | Guava 18.0 | 🟠 Medium | Dependency | P2.1 |
| D7 | commons-io 2.4 (one constant) | 🟠 Medium | Dependency | P2.2 |
| D8 | commons-codec 1.10 (unused) | 🟠 Medium | Dependency | P2.3 |
| D9 | cglib-nodep 3.1 at compile scope | 🟠 Medium | Dependency | P2.4 |
| D10 | groovy-all 2.4.3 at compile scope | 🟠 Medium | Dependency | P2.5 |
| D11 | Plain-HTTP milestone repository | 🟠 Medium | Security | P2.6 |
| D12 | Actuator unauthenticated | 🟠 Medium | Security | P2.7 |
| D13 | No authentication/authorization on download endpoints | 🟠 Medium | Security | P2.8 |
| D14 | No dependency CVE scanning | 🟠 Medium | Security | P2.10 |
| D15 | `src/test/groovy` not a registered source root | 🟠 Medium | Build / Test | P0.1 |
| D16 | `spring-boot-maven-plugin` not declared | 🟠 Medium | Build | — (add during Phase 3) |
| D17 | Storage is a stub | 🟠 Medium | Completeness | F1 |
| D18 | No configuration externalization | 🟠 Medium | Operations | P3.4 |
| D19 | No per-client rate limiting | 🟠 Medium | Security / Operations | — (edge control) |
| D20 | `301` redirect path untested | 🟠 Medium | Test | P0.2 follow-up |
| D21 | Spock 1.0 / Groovy 2.4 | 🟡 Low | Dev dependency | P1.2 (Boot 2.x stage) |
| D22 | `maven-compiler-plugin` 3.0 | 🟡 Low | Build tooling | P3.9 |
| D23 | `new Integer("1234")` dead assignment | 🟡 Low | Code quality | P3.1 |
| D24 | Duplicate `spring-test` declaration | 🟡 Low | Build hygiene | P2.9 |
| D25 | Unused `spring.version` property | 🟡 Low | Build hygiene | P1.1 |
| D26 | Unregistered `Sha512ShallowEtagHeaderFilter` (dead code in a Spring package) | 🟡 Low | Dead code | P3.2 |
| D27 | Spec filename/class-name mismatch | 🟡 Low | Convention | P0.3 |
| D28 | Eager full-file SHA-512 in a constructor | 🟡 Low | Performance | P3.5 |
| D29 | Exception type laundering | 🟡 Low | Code quality | P3.6 |
| D30 | Hard-coded throttle rate | 🟡 Low | Code quality | P3.3 |
| D31 | `.DS_Store` committed, no `.gitignore` | 🟡 Low | Hygiene | P3.7 |
| D32 | Two-line README | 🟡 Low | Documentation | P3.8 |
| D33 | No CI/CD | 🟡 Low | Operations | P3.10 |
| D34 | No containerization or coverage reporting | 🟡 Low | Operations | P3.11 |
| D35 | Verbose production logging | 🟡 Low | Operations | P3.4 |
| D36 | No `Range` / resumable download support | 🟡 Low | Functionality | — (feature) |
| D37 | Unvalidated `{filename}`; no `Content-Disposition` | 🟡 Low | Code quality / Security | — (Phase 3) |
| D38 | `new File(resource.getFile())` fragility | 🟡 Low | Code quality | — (Phase 3) |
| D39 | Guava types on abstraction boundaries | 🟡 Low | Architecture | — (review with P2.1) |
| D40 | ETag/metadata capture inconsistency in `FileSystemPointer` | 🟡 Low | Code quality | — (with P3.5) |
