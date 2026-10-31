# Code Metrics

[← Back to index](../README.md) · Related: [complexity-analysis.md](complexity-analysis.md) · [tech-debt.md](tech-debt.md)

Metrics derived by static inspection only; no coverage tool or analyzer was executed.

## Size

| Metric | Value |
|---|---|
| Total Java + Groovy lines | 652 |
| Production lines (`src/main/java`) | 417 |
| Test lines (`src/test`) | 236 |
| Resource/config lines | 15 (`logback.xml`) |
| Production types | 8 (6 classes, 2 interfaces) |
| Test types | 4 |
| Packages | 2 (plus 1 squatted framework package) |
| Maven modules | 1 |
| Declared direct dependencies | 20 (14 compile/runtime, 6 test) |

## Per-File Metrics

| File | Lines | Types | Methods | Max nesting | Notes |
|---|---|---|---|---|---|
| `ExistingFile.java` | 121 | 1 | 11 | 2 | largest and most behaviour-dense |
| `FileSystemPointer.java` | 86 | 1 | 9 | 2 | constructor does I/O |
| `ThrottlingInputStream.java` | 67 | 1 | 10 | 1 | pure delegation |
| `DownloadController.java` | 63 | 1 | 4 | 1 | no branching |
| `FileSystemStorage.java` | 33 | 1 | 1 | 1 | stub |
| `FilePointer.java` | 26 | 1 | 8 | — | interface |
| `MainApplication.java` | 13 | 1 | 1 | 1 | contains dead statement |
| `FileStorage.java` | 8 | 1 | 1 | — | interface |
| `DownloadControllerTest.groovy` | 176 | 1 | 14 | 1 | 12 feature methods |
| `FileStorageStub.java` | 27 | 1 | 1 | 2 | — |
| `FileExamples.java` | 20 | 1 | 1 | 1 | — |
| `Sha512ShallowEtagHeaderFilter.java` | 13 | 1 | 1 | 1 | — |

## Method Size Distribution

| Lines per method | Count |
|---|---|
| 1–3 | 32 |
| 4–8 | 11 |
| 9–12 | 2 (`ExistingFile.response`, `redirectDownload`) |
| > 12 | 0 |

Average production method length is roughly 3 lines. No method exceeds 12 lines, and no class exceeds 121 lines — well inside common maintainability thresholds.

## Quality Indicators

| Indicator | Assessment |
|---|---|
| Method length | Excellent — no long methods |
| Class size | Excellent — largest is 121 lines |
| Parameter counts | Acceptable — maximum 4 (controller handlers, driven by header binding) |
| Nesting depth | Excellent — maximum 2 |
| Inheritance depth | Shallow — maximum 2 from `Object` (3 for the test filter via Spring's hierarchy) |
| Coupling | Low; dependencies flow through two narrow interfaces |
| Cohesion | High; each type has a single clear responsibility |
| Immutability | Strong — all non-logger fields are `final` |
| `null` usage | Mostly avoided via `Optional`; `null` is used deliberately for absent response bodies (`resourceToReturn`, `notModified`) and for `mediaTypeOrNull` |
| Comments | Sparse; only `FileSystemStorage` has Javadoc (3 lines) and `ThrottlingInputStream` has one inline marker comment. No copyright headers in source files. |
| Dead code | 1 instance (`MainApplication:10`); 1 unwired class (`Sha512ShallowEtagHeaderFilter`); 1 unused dependency (`commons-codec`); 1 unused property (`spring.version`) |
| Duplication | Near-zero — the two handlers share `findExistingFile`, and the two response flows share `serveWithCaching` |
| Exception handling | 3 `catch` blocks, all rethrowing as `IllegalArgumentException`; no global `@ControllerAdvice` |
| Logging | 5 statements (1 DEBUG + 2 TRACE in `ExistingFile`, 1 DEBUG each in both storages); no sensitive data logged |
| Test count | 12 Spock feature methods covering both endpoints, all caching paths, and `HEAD` |
| Assertion density | 1–3 `andExpect` assertions per feature method |
| Test gaps | `ThrottlingInputStream`, `FileSystemStorage`, `FileSystemPointer` error paths, and the `301` redirect endpoint have no direct tests |

## Framework Annotation Usage

| Annotation | Count | Where |
|---|---|---|
| `@SpringBootApplication` | 1 | `MainApplication` |
| `@RestController` | 1 | `DownloadController` |
| `@RequestMapping` | 3 | class-level + 2 handlers |
| `@PathVariable` | 3 | including one misplaced on a private helper parameter (`DownloadController:57`) |
| `@RequestHeader` | 4 | conditional-request headers |
| `@Autowired` | 2 | controller constructor, Spock setter |
| `@Component` | 2 | `FileSystemStorage`, `FileStorageStub` |
| `@Profile` | 2 | `!test`, `test` |
| `@Override` | 12 | missing on `FileStorageStub.findFile` |
