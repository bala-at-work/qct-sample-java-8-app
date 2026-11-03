# Code Metrics

- [Back to README](../README.md)
- Siblings: [complexity-analysis.md](complexity-analysis.md) · [dependency-analysis.md](dependency-analysis.md) · [security-patterns.md](security-patterns.md) · [tech-debt.md](tech-debt.md)

> All metrics below are derived from static inspection of source files. No build, compilation, coverage run, or test execution was performed.

## Table of Contents
1. [Size Metrics](#size-metrics)
2. [Per-File Metrics](#per-file-metrics)
3. [Type and Member Counts](#type-and-member-counts)
4. [Language and File Inventory](#language-and-file-inventory)
5. [Coupling Metrics](#coupling-metrics)
6. [Quality Indicators](#quality-indicators)
7. [Test Metrics](#test-metrics)
8. [Dependency Metrics](#dependency-metrics)
9. [Documentation Metrics](#documentation-metrics)
10. [Metric Summary Scorecard](#metric-summary-scorecard)

## Size Metrics

| Metric | Value |
|---|---|
| Total source lines (Java + Groovy, excluding build output) | **652** |
| Main Java source files | 8 |
| Test Java source files | 3 |
| Groovy specification files | 1 |
| Total source files | 12 |
| Main source lines | 416 |
| Test source lines (Java + Groovy) | ~236 |
| Packages | 2 (`com.nurkiewicz.download`, `org.springframework.web.filter`) |
| Maven modules | 1 |
| Largest file | `ExistingFile.java` (121 lines) |
| Smallest file | `FileStorage.java` (8 lines) |
| Mean main-file size | 52 lines |

**Scale classification: very small.** At 652 lines the entire codebase can be read in one sitting, and it is three orders of magnitude below the 1,000,000-line large-codebase threshold. No summarization was necessary for this analysis — every source file was read in full.

## Per-File Metrics

### Main sources
| File | Lines | Types | Methods | Fields | Imports | Max nesting |
|---|---|---|---|---|---|---|
| `ExistingFile.java` | 121 | 1 | 11 | 4 | 18 | 2 |
| `FileSystemPointer.java` | 86 | 1 | 10 | 3 | 9 | 2 |
| `ThrottlingInputStream.java` | 66 | 1 | 10 | 2 | 3 | 1 |
| `DownloadController.java` | 63 | 1 | 4 | 1 | 18 | 1 |
| `FileSystemStorage.java` | 33 | 1 | 1 | 1 | 8 | 1 |
| `FilePointer.java` | 26 | 1 | 8 (abstract) | 0 | 3 | — |
| `MainApplication.java` | 13 | 1 | 1 | 0 | 2 | 1 |
| `FileStorage.java` | 8 | 1 | 1 (abstract) | 0 | 2 | — |
| **Total** | **416** | **8** | **46** | **11** | — | — |

### Test sources
| File | Lines | Types | Methods / features |
|---|---|---|---|
| `DownloadControllerTest.groovy` | 176 | 1 | 12 feature methods + 2 helpers |
| `FileStorageStub.java` | ~27 | 1 | 1 |
| `FileExamples.java` | ~21 | 1 | 1 (private factory) + 3 constants |
| `Sha512ShallowEtagHeaderFilter.java` | ~13 | 1 | 1 |

## Type and Member Counts

| Category | Count |
|---|---|
| Classes (main) | 6 |
| Interfaces (main) | 2 |
| Abstract classes | 0 |
| Enums | 0 |
| Annotations declared | 0 |
| Nested / inner types | 0 |
| Generic types declared by the project | 0 |
| Classes (test) | 4 |
| **Total project types** | **12** |

| Member category | Count (main) |
|---|---|
| Public methods | 21 |
| Private methods | 10 |
| Interface methods | 9 |
| Constructors | 4 |
| Static methods | 1 (`main`) |
| Instance fields | 8 |
| Static fields | 3 (all loggers) |
| **`final` fields** | **11 of 11 (100%)** |
| Setters | 0 |
| Methods with `@Override` | 20 |

## Language and File Inventory

| Language / format | Files | Role |
|---|---|---|
| Java | 11 | Application and test code |
| Groovy | 1 | Spock specification |
| XML | 2 | `pom.xml`, `logback.xml` |
| Properties | 1 | `maven-wrapper.properties` |
| Markdown | 3 | `README.md`, `CODE_OF_CONDUCT.md` (+ `LICENSE`) |
| Shell / Batch | 2 | `mvnw`, `mvnw.cmd` |
| Plain text | 1 | `download.txt` test fixture |
| Binary (unwanted) | 1 | `.DS_Store` |

**Single-language application.** Java accounts for 100% of production code; Groovy appears only in tests. There is no SQL, no JavaScript, no templating language, and no IaC in the repository.

## Coupling Metrics

Fan-in = number of project types that reference this type. Fan-out = number of project types this type references.

| Type | Fan-in | Fan-out | Instability (Fo/(Fi+Fo)) | Assessment |
|---|---|---|---|---|
| `FilePointer` | 4 | 0 | 0.00 | Maximally stable — correct for a core port |
| `FileStorage` | 4 | 1 | 0.20 | Stable port |
| `ExistingFile` | 1 | 3 | 0.75 | Instable — correct for policy code |
| `DownloadController` | 1 | 3 | 0.75 | Instable — correct for an adapter |
| `FileSystemPointer` | 3 | 1 | 0.25 | Stable adapter |
| `FileSystemStorage` | 0 | 2 | 1.00 | Leaf adapter (wired by component scan) |
| `ThrottlingInputStream` | 1 | 0 | 0.00 | Fully independent, reusable |
| `MainApplication` | 1 | 0 | 0.00 | Entry point |

**Interpretation.** The two interfaces have the highest fan-in and the lowest instability, while the concrete policy and adapter classes are instable — exactly the distribution the Stable Abstractions Principle prescribes. Average fan-out across main types is 1.25, i.e. each class knows about roughly one other project class.

| Graph metric | Value |
|---|---|
| Dependency cycles | **0** |
| Maximum dependency depth | 4 (`MainApplication → DownloadController → ExistingFile → FilePointer`) |
| Abstractness (interfaces / total main types) | 0.25 |
| Types with zero project dependencies | 4 of 8 (`FilePointer`, `ThrottlingInputStream`, `MainApplication`, and `FileStorage` apart from its own return type) |

## Quality Indicators

### Positive
| Indicator | Measurement |
|---|---|
| Immutability | 11 of 11 fields are `final`; zero setters |
| Dependency inversion | 2 of 2 outbound dependencies go through interfaces |
| Cycles | 0 |
| Dead-simple methods | 31 of 46 main methods are 1–3 statements |
| Constructor injection | 1 of 1 injected dependency (no field injection) |
| `Optional` usage | 14 occurrences — absence is modelled explicitly, no null checks in the request path |
| Average method length | ~4 lines |
| Maximum method length | 11 lines (`ExistingFile.response`) |
| Maximum nesting depth | 2 |
| `@Override` discipline | 20 of 20 overriding methods annotated |

### Negative
| Indicator | Measurement |
|---|---|
| Deprecated API usages | 2 (`new Integer(String)`; `ShallowEtagHeaderFilter.generateETagHeaderValue(byte[])`) |
| Dead code items | 4 (unregistered filter, unread local, unused `commons-codec`, unused `spring.version` property) |
| Unused declared dependencies | 1 fully (`commons-codec`), 2 mis-scoped (`groovy-all`, `cglib-nodep`) |
| Hard-coded policy values | 2 (1 MB/s throttle, `/logback.xml` path) |
| Externalized configuration files | **0** |
| Methods swallowing or laundering exceptions | 3 (`IOException`/`FileNotFoundException`/`URISyntaxException` → `IllegalArgumentException`) |
| Executable test suites in a default build | **0** (the Groovy source root is unregistered) |
| Public types without Javadoc | 11 of 12 |

## Test Metrics

| Metric | Value |
|---|---|
| Specification files | 1 |
| Feature (test) methods | 12 |
| Test-to-main line ratio | ~0.57 : 1 |
| Test style | Spock `given`/`when`/`then` behavioral specification |
| Test scope | Integration — full Spring MVC stack via `MockMvc` |
| Unit tests | 0 |
| Test doubles | 1 (`FileStorageStub`, profile-activated) |
| Mocking framework usage | None, despite `cglib-nodep` and Mockito being on the classpath |
| **Tests executed by a default `mvn test`** | **0** — `src/test/groovy` is not a registered source root |
| Coverage instrumentation | None (no JaCoCo) |
| Measured coverage | Unknown |

### Behavioral coverage by concern
Based on the 12 feature methods (enumerated in [../migration/test-specifications.md](../migration/test-specifications.md)):

| Concern | Covered |
|---|---|
| File content correctness | ✅ |
| `404` for unknown UUID | ✅ |
| Serve when no `If-None-Match` present | ✅ |
| Serve when `If-None-Match` does not match | ✅ |
| `304` + ETag echo when `If-None-Match` matches | ✅ |
| `304` when `If-Modified-Since` equals last-modified | ✅ |
| `304` when the client copy is newer | ✅ |
| `200` when the file was modified after the client copy | ✅ |
| `HEAD` → `200` with empty body | ✅ |
| `HEAD` → `304` with a cached ETag | ✅ |
| `Content-Length` correctness | ✅ |
| `Content-Type` correctness | ✅ |
| `301` redirect from the short URL | ❌ **not covered** |
| Throttling behavior | ❌ not covered |
| `ThrottlingInputStream` in isolation | ❌ not covered |
| `FileSystemStorage` (the production adapter) | ❌ not covered — the `test` profile replaces it |
| `400` for a malformed UUID | ❌ not covered |
| `405` for an unmapped verb | ❌ not covered |

**Assessment.** The caching and HEAD semantics — the subtlest and most upgrade-fragile behavior — are thoroughly specified. The notable gap is the `301` redirect path, which is one of the system's two endpoints and is entirely unspecified. `ThrottlingInputStream` is also untested despite being the most reusable class in the codebase and a natural unit-test target.

## Dependency Metrics

| Metric | Value |
|---|---|
| Declared dependencies | 19 (18 distinct coordinates; one duplicate) |
| Compile-scope dependencies | 15 |
| Test-scope dependencies | 4 |
| Declared plugins | 1 (`maven-compiler-plugin` 3.0) |
| Custom repositories | 1 (plain HTTP) |
| Distinct third-party libraries actually imported by main source | 4 (Spring, Guava, Commons IO, SLF4J) |
| Dependencies with **no** source usage | 1 (`commons-codec`) |
| Dependencies at the wrong scope | 2 (`groovy-all`, `cglib-nodep`) |
| Redundant Spring pins (supplied transitively by the web starter) | 7 |
| Dependencies at an EOL version | 13 of 18 |
| **Dependency currency** | **0 of 18 at a current version** |

## Documentation Metrics

| Metric | Value |
|---|---|
| Javadoc'd types | 1 of 12 (`FileSystemStorage`) |
| Javadoc'd public methods | 0 of 21 |
| Inline comments in main source | 1 (`//less important below...` in `ThrottlingInputStream`) |
| License headers in source files | 0 (a repository-level `LICENSE` file exists) |
| Repository `README.md` length | 2 lines |
| Documents endpoints, run instructions, or the stub caveat | No |

**Assessment.** Documentation is essentially absent, with one important exception: the single Javadoc comment in the codebase is the one that matters most — it declares `FileSystemStorage` to be a stub requiring replacement. Code readability partially compensates for the lack of comments (short methods, descriptive names, `Optional`-based signatures), but a newcomer has no written description of the two endpoints, the caching strategy, or the throttling policy. The Spock specification is the de facto behavioral documentation — and it does not currently run.

## Metric Summary Scorecard

| Dimension | Rating | Basis |
|---|---|---|
| Size and comprehensibility | **Strong** | 652 lines, 12 types, max nesting 2, mean method 4 lines |
| Structural design | **Strong** | 0 cycles, correct dependency inversion, ideal stability distribution |
| Immutability and thread safety | **Strong** | 100% `final` fields, no shared mutable state |
| Complexity | **Strong** | No method exceeds 11 lines; highest branch count is 4 |
| Test design | **Strong** | Behavioral specification at the HTTP boundary with a profile-swapped stub |
| Test execution | **Weak** | 0 tests run in a default build; no coverage measurement |
| Dependency currency | **Weak** | 0 of 18 dependencies current; 13 EOL |
| Configuration externalization | **Weak** | 0 configuration files; all values hard-coded or framework defaults |
| Documentation | **Weak** | 1 Javadoc comment; 2-line README |
| Build completeness | **Weak** | Unregistered Groovy source root; no `spring-boot-maven-plugin` declaration |
| Security posture | **Weak** | No authentication; unsecured actuator; plain-HTTP repository |

**Overall.** The *code* scores well on every intrinsic quality dimension — it is small, immutable, acyclic, well-factored and low in complexity. Every weak rating concerns the *environment around the code*: dependency versions, build wiring, configuration, documentation and security. This is a favourable profile for modernization, because the hard-to-fix dimension (structural design) is already sound and the weak dimensions are mechanically addressable. See [../technical-debt/remediation-plan.md](../technical-debt/remediation-plan.md).
