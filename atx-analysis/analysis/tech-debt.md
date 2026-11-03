# Technical Debt Assessment

[← Back to index](../README.md) · Canonical summary: [../technical-debt-report.md](../technical-debt-report.md) · Detail: [../technical-debt/summary.md](../technical-debt/summary.md)

This file is the analysis-side cross-reference of the technical debt findings. The authoritative prioritized list, including the AWS transformation recommendation, lives in [../technical-debt-report.md](../technical-debt-report.md).

## Debt Profile

| Dimension | Rating | Basis |
|---|---|---|
| Platform / runtime currency | **High debt** | Java 8 target, Spring Boot 1.2.3, Spring 4.1.6 — all EOL |
| Dependency currency | **High debt** | every runtime dependency is ~a decade old |
| Build tooling | Medium debt | `maven-compiler-plugin` 3.0; no Groovy compiler plugin; wrapper itself is current |
| Security posture | **High debt** | unpatched frameworks, plaintext repository, unsecured Actuator, no authn/authz |
| Code quality | **Low debt** | small methods, low complexity, immutable types, clean ports-and-adapters |
| Test coverage | Medium debt | 12 integration specs cover the download endpoint well; no unit tests for throttling, storage, or error paths; the redirect endpoint is untested |
| Architecture | Low debt | clean layering; the only structural blemish is the squatted Spring package |
| Documentation | Medium debt | one 3-line Javadoc in the entire codebase; this ATXDocumentation set now addresses the gap |
| Operability | Medium debt | no configuration file, no container image, no IaC, no CI definition, no health/metric usage despite Actuator being present |
| Performance | Medium debt | full-file SHA-512 per request; blocking throttled transfers hold container threads |

## Debt Distribution by Artifact

| Artifact | Debt items | Weight |
|---|---|---|
| `pom.xml` | H1–H4, M1–M5, L2–L5 | Dominant — most debt is declarative, not algorithmic |
| `FileSystemStorage.java` | L6, S10, P1 | Medium |
| `FileSystemPointer.java` | A4, A7, P1, P2, S11 | Medium |
| `ExistingFile.java` | A4, A6, P3, P4, S7 | Medium |
| `MainApplication.java` | L1 | Trivial |
| `Sha512ShallowEtagHeaderFilter.java` | A1, L7 | Trivial |
| `DownloadControllerTest.groovy` | A10, test-stack age | Low |

Finding identifiers map to [../technical-debt-report.md](../technical-debt-report.md) (H/M/L), [../architecture/patterns.md](../architecture/patterns.md) (A), [complexity-analysis.md](complexity-analysis.md) (P), and [security-patterns.md](security-patterns.md) (S).

## Interest Rate (what the debt costs over time)

- **Compounding**: the EOL platform blocks every other improvement. Modern libraries, security patches, container base images, and observability tooling all assume Java 17+ and the `jakarta.*` namespace. Each additional year makes the jump wider.
- **Security**: unpatched Spring 4.1.x / Boot 1.2.3 means new CVEs are permanently unmitigated.
- **Hiring/tooling**: Spock 1.0 + Groovy 2.4 and Maven compiler plugin 3.0 are unsupported by current IDE/CI defaults.
- **Low-interest items**: the code-quality findings (dead statement, unused dependency, wrong scopes) cost almost nothing per unit time and can be cleaned opportunistically.

## Conclusion

This is an unusually favourable modernization target: the debt is almost entirely in `pom.xml` and the platform version, while the application code is small, clean, well-abstracted, and covered by behaviour-focused integration tests that can validate an upgrade. Recommended path: [../technical-debt/remediation-plan.md](../technical-debt/remediation-plan.md).
