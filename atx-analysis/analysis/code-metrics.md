# Code Metrics

[← Back to index](../README.md)

All figures derive from static inspection of the source tree, excluding `.git`, build output, and the `ATXDocumentation/` directory itself.

## Size

| Metric | Value |
|---|---|
| Maven modules | 1 |
| Production Java files | 8 |
| Test Java files | 3 |
| Test Groovy files | 1 |
| Total source files (`.java` + `.groovy`) | 12 |
| Total source LOC (`.java` + `.groovy`, incl. blanks and comments) | 652 |
| Configuration files | 2 (`pom.xml`, `logback.xml`) |
| `pom.xml` lines | ~175 |
| Resource fixtures | 1 (`src/test/resources/download.txt`, 6 bytes) |

**Large-codebase classification:** Not applicable. At 652 LOC the repository is three orders of magnitude below the 1,000,000 LOC threshold, so all documentation in this set is exhaustive rather than summarized.

## Per-File Breakdown (production)

| File | Approx. LOC | Types | Methods | Fields |
|---|---|---|---|---|
| `ExistingFile.java` | ~130 | 1 | 11 (2 public, 9 private) | 4 |
| `ThrottlingInputStream.java` | ~72 | 1 | 10 (all public) | 2 |
| `FileSystemPointer.java` | ~92 | 1 | 10 (all public) | 3 |
| `DownloadController.java` | ~63 | 1 | 4 (3 public, 1 private) | 1 |
| `FilePointer.java` | ~25 | 1 (interface) | 8 (abstract) | 0 |
| `FileSystemStorage.java` | ~33 | 1 | 1 | 1 |
| `MainApplication.java` | ~13 | 1 | 1 | 0 |
| `FileStorage.java` | ~8 | 1 (interface) | 1 (abstract) | 0 |
| **Production total** | **~436** | **8** | **46** | **11** |

## Per-File Breakdown (test)

| File | Approx. LOC | Types | Methods/Features |
|---|---|---|---|
| `DownloadControllerTest.groovy` | ~160 | 1 | 12 feature methods + 2 helpers |
| `FileStorageStub.java` | ~29 | 1 | 1 |
| `FileExamples.java` | ~21 | 1 | 1 (private factory) + 3 constants |
| `Sha512ShallowEtagHeaderFilter.java` | ~14 | 1 | 1 |
| **Test total** | **~224** | **4** | **18** |

## Method Size Distribution (production)

| Range | Count | Share |
|---|---|---|
| 1–3 lines | 27 | 59% |
| 4–7 lines | 14 | 30% |
| 8–12 lines | 5 | 11% |
| >12 lines | 0 | 0% |

Largest methods: `ExistingFile.response` (~12 lines), `FileSystemPointer` constructor (~11), `FileSystemPointer.toString` (~9), `DownloadController.redirect`/`download` (~9 each). No method exceeds 12 lines — method granularity is consistently fine.

## Type Size Distribution

| Range | Types |
|---|---|
| < 20 LOC | `FileStorage`, `MainApplication` |
| 20–50 LOC | `FilePointer`, `FileSystemStorage` |
| 50–100 LOC | `DownloadController`, `ThrottlingInputStream`, `FileSystemPointer` |
| 100–150 LOC | `ExistingFile` |
| > 150 LOC | none |

No class approaches a size that would warrant splitting on volume grounds.

## Coupling Metrics

| Type | Fan-in (dependents) | Fan-out (collaborators) |
|---|---|---|
| `FilePointer` | 4 | 1 |
| `FileStorage` | 3 | 1 |
| `ExistingFile` | 1 | 6 |
| `FileSystemPointer` | 3 | 5 |
| `ThrottlingInputStream` | 1 | 2 |
| `DownloadController` | 1 (framework) | 3 |
| `FileSystemStorage` | 0 (framework-instantiated) | 2 |
| `MainApplication` | 1 (test) | 1 |

Fan-in concentrates on the two **interfaces**, and fan-out concentrates on the single orchestrating class. This is the desired shape: stable abstractions are widely depended upon, and volatile wiring is localized.

## Quality Indicators

| Indicator | Value | Assessment |
|---|---|---|
| Interfaces per implementation | 2 interfaces / 3 implementations | Healthy abstraction ratio for a service this size |
| Immutable production classes | 5 of 6 (`ExistingFile`, `FileSystemPointer`, `ThrottlingInputStream`, `DownloadController`, plus the stateless `FileSystemStorage`) | Excellent |
| Classes with mutable instance state | 0 in production | Excellent |
| Constructor injection usage | 1 of 1 injection site | Excellent — no field or setter injection |
| `@Override` coverage | 19 of 20 overriding methods | `FileStorageStub.findFile` is the single omission (TD-15) |
| Static mutable state | 0 in production; 3 static finals in `FileExamples` (test) | Good |
| Deprecated API usages | 1 (`new Integer(String)`, `MainApplication.java:10`) | Must fix before a modern-JDK build (R-07) |
| Dead code | 1 unused local (`temp`), 1 unregistered class (`Sha512ShallowEtagHeaderFilter`), 1 unused POM property (`spring.version`) | Low volume, all identified |
| Unused declared dependencies | `commons-codec`; plus `spring-aop`, `spring-expression`, `spring-context-support`, `cglib-nodep` with no direct references | Cleanup opportunity (R-06) |
| Undeclared direct dependency | `org.slf4j` (used in 3 classes, satisfied transitively) | Should be declared explicitly |
| Comment density | Very low — 1 Javadoc block (`FileSystemStorage`), 1 inline note (`ThrottlingInputStream`: "less important below...") | Acceptable: names are descriptive and methods are tiny |
| Magic values | `ONE_MB` throttle rate (`ExistingFile.java:99`) | Should be externalized (R-09) |
| Indentation consistency | Tabs throughout all Java sources | Consistent |
| Automated style/quality enforcement | None (no Checkstyle, SpotBugs, PMD, Spotless, `.editorconfig`) | Gap (R-13) |

## Test Metrics

| Metric | Value |
|---|---|
| Specification classes | 1 |
| Feature methods (test cases) | 12 |
| Test-to-production LOC ratio | ~0.51 : 1 |
| Mocking framework usage | None — a profile-selected stub is used instead |
| Test style | Integration (full Spring context via `MockMvc`) |
| Unit tests for `ThrottlingInputStream` | **None** |
| Unit tests for `FileSystemPointer` | **None** (exercised indirectly via `FileExamples`) |
| Unit tests for `FileSystemStorage` | **None** |

**Behavioral coverage of the HTTP contract is strong** — all four status codes, both endpoints' caching paths, `HEAD` semantics, and three `Content-*` headers are asserted. **Coverage of non-HTTP units is absent**: the throttling decorator and the hashing/metadata pointer have no direct tests, despite being the two most algorithmically interesting classes. See [../migration/test-specifications.md](../migration/test-specifications.md).

## Metric Summary

| Dimension | Rating |
|---|---|
| Size / scope | Very small, single-purpose |
| Structural quality | High — clean layering, immutability, DI discipline |
| Method-level complexity | Low (see [complexity-analysis.md](complexity-analysis.md)) |
| Platform currency | **Poor** — see [tech-debt.md](tech-debt.md) |
| Test breadth | Good for HTTP behavior, thin for units |
| Automated quality gates | Absent |
