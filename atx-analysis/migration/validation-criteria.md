# Validation Criteria

[← Back to index](../README.md)

Criteria for judging a successful migration of this codebase. Two distinct scopes are covered: criteria for **this documentation transformation**, and criteria for a **subsequent code migration** (such as the recommended JDK upgrade).

---

## Part 1 — Criteria for this documentation transformation

This transformation is static-analysis only: it never builds, compiles, or executes the code, and never runs the test suite. All validation is performed on the documentation output.

| # | Criterion | Check |
|---|---|---|
| 1 | Structure complete | Every required file exists under `ATXDocumentation/` with meaningful content; no empty files or directories |
| 2 | AWS recommendation present | `technical-debt-report.md` opens with the `## 🎯 AWS Transformation Recommendation` section in the exact prescribed format, naming a transformation that exists in the AWS-managed registry |
| 3 | Architecture documented | `architecture/system-overview.md`, `components.md`, `dependencies.md`, `patterns.md` describe the design, components, dependency graph, and patterns |
| 4 | Diagrams present | `diagrams/structural.md`, `behavioral.md`, `architecture.md` contain text-based diagrams; behavioral diagrams cover application logic only (no build/test/publish flows) |
| 5 | Reference complete | `reference/program-structure.md`, `interfaces.md`, `data-models.md`, `api-reference.md`, `modules.md` document every class, interface, and public method |
| 6 | Analysis complete | `analysis/code-metrics.md`, `complexity-analysis.md`, `dependency-analysis.md`, `security-patterns.md`, `tech-debt.md` present with quality metrics |
| 7 | Migration guidance | `migration/component-order.md`, `test-specifications.md`, `validation-criteria.md` present |
| 8 | Technical debt rated | `technical-debt/summary.md`, `outdated-components.md`, `maintenance-burden.md`, `remediation-plan.md` use only High/Medium/Low severities and list prioritized actions |
| 9 | Navigation functional | `README.md` links to every section; cross-references between documents resolve |
| 10 | No time estimates | Priority, effort, and complexity are expressed only qualitatively |
| 11 | Source fidelity | Every documented class, interface, method, dependency, and version corresponds to something actually present in the repository |

---

## Part 2 — Criteria for a subsequent code migration

### Gate 1 — Build integrity

| # | Criterion | Acceptance |
|---|---|---|
| 1.1 | Dependency resolution succeeds without any plaintext HTTP repository | No `http://` URL remains in `pom.xml`; resolution completes from Maven Central (and any pre-existing trusted HTTPS repository) |
| 1.2 | Compilation succeeds with zero errors | Full clean build |
| 1.3 | No deprecated-API warnings from project code | `new Integer(String)` removed from `MainApplication` |
| 1.4 | Groovy test sources are compiled | A Groovy compiler plugin is configured and `DownloadControllerSpec` is discovered |
| 1.5 | An executable JAR is produced | `spring-boot-maven-plugin` declared and the repackage goal runs |
| 1.6 | No version pin overrides the parent BOM | No explicit `<version>` on any `spring-boot-starter-*` or `org.springframework:*` dependency |
| 1.7 | No duplicate dependency declarations | `spring-test` appears once |
| 1.8 | No unused declared dependencies | `commons-codec` and other unreferenced artifacts removed |
| 1.9 | No undeclared direct dependencies | `slf4j-api` declared explicitly |

### Gate 2 — Behavioral equivalence (the primary gate)

All 12 existing specifications must pass. The following behaviors must be **identical** before and after migration:

| # | Behavior | Verified by |
|---|---|---|
| 2.1 | `GET /download/{uuid}/{filename}` returns `200` with exact file bytes | Spec 1 |
| 2.2 | Unknown UUID returns `404` with an empty body | Spec 2 |
| 2.3 | Absent `If-None-Match` returns `200` | Spec 3 |
| 2.4 | Non-matching `If-None-Match` returns `200` | Spec 4 |
| 2.5 | Matching `If-None-Match` returns `304` and echoes the `ETag` | Spec 5 |
| 2.6 | `If-Modified-Since` **equal** to last-modified returns `304` | Spec 6 |
| 2.7 | `If-Modified-Since` **later** than last-modified returns `304` | Spec 7 |
| 2.8 | `If-Modified-Since` **earlier** than last-modified returns `200` | Spec 8 |
| 2.9 | `HEAD` returns `200` with headers and an empty body | Spec 9 |
| 2.10 | `HEAD` with a matching ETag returns `304` | Spec 10 |
| 2.11 | `Content-Length` equals the real file size | Spec 11 |
| 2.12 | `Content-Type` reflects the probed media type | Spec 12 |

> **Critical:** verify 2.5 through 2.8 and 2.10 **individually**. A regression in `@RequestHeader(IF_MODIFIED_SINCE) Optional<Date>` binding would silently convert `304` responses into `200` responses — functionally "working" but with client caching disabled. An aggregate pass/fail count can mask this if other specifications are also failing for unrelated reasons.

Behaviors not covered by the existing suite that must still be preserved (verify manually or add the specifications from [test-specifications.md](test-specifications.md) Group B):

| # | Behavior |
|---|---|
| 2.13 | `GET`/`HEAD /download/{uuid}` returns `301` with `Location: /download/{uuid}/{originalName}` |
| 2.14 | `Last-Modified` header is emitted on `200` and `304` responses |
| 2.15 | Malformed UUID returns `400` |
| 2.16 | `GET` response bodies remain rate-limited |

### Gate 3 — Test integrity

| # | Criterion | Acceptance |
|---|---|---|
| 3.1 | All 12 feature methods still exist | None deleted, renamed away, ignored, or disabled |
| 3.2 | No test is skipped or conditionally excluded | No `@Ignore`, `@Disabled`, or `spock.lang.Ignore` introduced |
| 3.3 | Test doubles preserved | `FileStorageStub` still returns `Optional.empty()` for unknown UUIDs |
| 3.4 | Profile seam preserved | `@Profile("test")` / `@Profile("!test")` split intact |
| 3.5 | Fixtures intact | `src/test/resources/download.txt` unchanged |

### Gate 4 — API compatibility

| # | Criterion | Acceptance |
|---|---|---|
| 4.1 | Public type names unchanged | `MainApplication`, `DownloadController`, `ExistingFile`, `FileStorage`, `FilePointer`, `FileSystemStorage`, `FileSystemPointer`, `ThrottlingInputStream` all retained |
| 4.2 | Primary type declarations retained | Every source file keeps its main type |
| 4.3 | Public method signatures preserved | All 26 public production methods (see [../reference/api-reference.md](../reference/api-reference.md)) remain, modulo intentional `javax`→`jakarta` type changes |
| 4.4 | Interface contracts unchanged | `FileStorage` (1 method) and `FilePointer` (8 methods) keep their shape |
| 4.5 | HTTP paths unchanged | `/download/{uuid}` and `/download/{uuid}/{filename}` |
| 4.6 | HTTP methods unchanged | `GET` and `HEAD` only |
| 4.7 | No duplicate signatures introduced | — |

### Gate 5 — Security controls preserved

Per the modification guardrails, none of these may be weakened:

| # | Control | Acceptance |
|---|---|---|
| 5.1 | `@PathVariable UUID` typing | Not relaxed to `String` — this is the sole input validation |
| 5.2 | `{filename}` remains unbound | Never used for file resolution; the path-traversal vector stays closed |
| 5.3 | Throttling retained | `ThrottlingInputStream` still wraps `GET` bodies; the rate may become configurable but throttling must not be removed |
| 5.4 | ETag digest strength | SHA-512 not downgraded |
| 5.5 | Safe methods only | No mutating HTTP method added |
| 5.6 | No secrets in source | Any new configuration comes from environment variables or externalized config |
| 5.7 | No new untrusted repositories | Only standard public registries or pre-existing trusted repositories |
| 5.8 | No new `eval`/`exec`/reflection on untrusted input | — |
| 5.9 | Dependency versions not downgraded | No artifact below its originally declared version |
| 5.10 | Introduced dependency versions free of known vulnerabilities | Each resolved coordinate checked against a vulnerability database |

### Gate 6 — Legal and documentation integrity

| # | Criterion |
|---|---|
| 6.1 | `LICENSE` unchanged |
| 6.2 | `CODE_OF_CONDUCT.md` unchanged |
| 6.3 | Copyright and license headers preserved verbatim |
| 6.4 | The 4-line `FileSystemStorage` Javadoc block preserved (or updated only if the stub is actually replaced, in which case it should be rewritten to describe the new behavior) |

### Gate 7 — Target-state criteria (upgrade-specific)

| # | Criterion | Acceptance |
|---|---|---|
| 7.1 | JDK target raised | Compiler configured to a supported LTS via `release`, not `source`/`target` |
| 7.2 | Spring Boot on a supported release | Parent at 3.x or later |
| 7.3 | No EOL Spring Framework pins | No `4.1.6.RELEASE` anywhere |
| 7.4 | Namespace migration complete | No `javax.servlet`/`javax.annotation` on the compile classpath where `jakarta` is required |
| 7.5 | Actuator exposure restricted | Explicit configuration exists; sensitive endpoints are not anonymously reachable |
| 7.6 | Application context starts cleanly | No bean-definition or auto-configuration errors |
| 7.7 | Exactly one `FileStorage` bean per profile | Context contains no ambiguity |

### Gate 8 — Functional completeness (R-05 only)

Applies when the storage stub is replaced:

| # | Criterion |
|---|---|
| 8.1 | `findFile` returns content corresponding to the requested UUID — not a fixed file |
| 8.2 | `findFile` returns `Optional.empty()` for unknown UUIDs, making the `404` path reachable |
| 8.3 | The application's own `logback.xml` is no longer served to clients |
| 8.4 | Resource resolution works inside a packaged JAR (no `new File(url.getFile())`) |
| 8.5 | No client-supplied value is interpolated into a filesystem path |
| 8.6 | All 12 original specifications still pass (the `@Profile` split means they should be unaffected) |

---

## Overall Acceptance

A migration is accepted when:

1. **Gate 1** passes — the project builds and produces a runnable artifact.
2. **Gate 2** passes with all 12 specifications green, and 2.5–2.8 / 2.10 confirmed individually.
3. **Gates 3, 4, 5, 6** pass — no test, API, security control, or legal notice was weakened.
4. **Gate 7** passes for the platform upgrade.
5. **Gate 8** passes if and only if the storage stub was in scope.

Any FAIL or PARTIAL result that is not attributable to an unavoidable third-party constraint must be fixed and re-validated rather than waived.

See [component-order.md](component-order.md) for sequencing and [test-specifications.md](test-specifications.md) for the specification detail behind Gate 2.
