# ATXDocumentation — download-server

Comprehensive static-analysis documentation for `com.nurkiewicz.download:download-server` (0.0.1-SNAPSHOT), a Spring Boot HTTP file-download service.

> This documentation was produced by static analysis only. No code was compiled, built, or executed.

## Start Here

| Document | Purpose |
|---|---|
| [project-overview.md](project-overview.md) | What the project is, stack, layout, entry points |
| [**technical-debt-report.md**](technical-debt-report.md) | ⚠️ Executive technical debt summary + **AWS transformation recommendation** |

## Architecture
- [architecture/system-overview.md](architecture/system-overview.md) — stack, deployment model, key decisions
- [architecture/components.md](architecture/components.md) — components and responsibilities
- [architecture/dependencies.md](architecture/dependencies.md) — internal + external dependency graph
- [architecture/patterns.md](architecture/patterns.md) — design patterns and anti-patterns

## Diagrams (text-based)
- [diagrams/structural.md](diagrams/structural.md) — component, class, package diagrams
- [diagrams/behavioral.md](diagrams/behavioral.md) — sequence, activity, state, data flow
- [diagrams/architecture.md](diagrams/architecture.md) — context, integration, security boundaries

## Reference
- [reference/program-structure.md](reference/program-structure.md) — full structural hierarchy
- [reference/interfaces.md](reference/interfaces.md) — interfaces, hierarchies, contracts
- [reference/data-models.md](reference/data-models.md) — types and relationships
- [reference/api-reference.md](reference/api-reference.md) — HTTP endpoints and public methods
- [reference/modules.md](reference/modules.md) — module/package organization

## Analysis
- [analysis/code-metrics.md](analysis/code-metrics.md)
- [analysis/complexity-analysis.md](analysis/complexity-analysis.md)
- [analysis/dependency-analysis.md](analysis/dependency-analysis.md)
- [analysis/security-patterns.md](analysis/security-patterns.md)
- [analysis/tech-debt.md](analysis/tech-debt.md)

## Technical Debt (detail)
- [technical-debt/summary.md](technical-debt/summary.md)
- [technical-debt/outdated-components.md](technical-debt/outdated-components.md)
- [technical-debt/maintenance-burden.md](technical-debt/maintenance-burden.md)
- [technical-debt/remediation-plan.md](technical-debt/remediation-plan.md)

## Migration
- [migration/component-order.md](migration/component-order.md)
- [migration/test-specifications.md](migration/test-specifications.md)
- [migration/validation-criteria.md](migration/validation-criteria.md)

## Specialized
- [specialized/README.md](specialized/README.md) — detected specialized concerns (HTTP API, logging config, build/CI)

## Component Index

| Component | Kind | Source |
|---|---|---|
| `MainApplication` | Spring Boot entry point | `src/main/java/com/nurkiewicz/download/MainApplication.java` |
| `DownloadController` | `@RestController` | `src/main/java/com/nurkiewicz/download/DownloadController.java` |
| `ExistingFile` | Response-building domain helper | `src/main/java/com/nurkiewicz/download/ExistingFile.java` |
| `FileStorage` | Interface (lookup port) | `src/main/java/com/nurkiewicz/download/FileStorage.java` |
| `FileSystemStorage` | `FileStorage` impl (profile `!test`) | `src/main/java/com/nurkiewicz/download/FileSystemStorage.java` |
| `FilePointer` | Interface (file metadata/content port) | `src/main/java/com/nurkiewicz/download/FilePointer.java` |
| `FileSystemPointer` | `FilePointer` impl | `src/main/java/com/nurkiewicz/download/FileSystemPointer.java` |
| `ThrottlingInputStream` | Rate-limiting stream decorator | `src/main/java/com/nurkiewicz/download/ThrottlingInputStream.java` |
| `FileStorageStub` | Test `FileStorage` (profile `test`) | `src/test/java/com/nurkiewicz/download/FileStorageStub.java` |
| `FileExamples` | Test fixtures | `src/test/java/com/nurkiewicz/download/FileExamples.java` |
| `Sha512ShallowEtagHeaderFilter` | Spring filter subclass (test tree) | `src/test/java/org/springframework/web/filter/Sha512ShallowEtagHeaderFilter.java` |
| `DownloadControllerSpec` | Spock specification | `src/test/groovy/com/nurkiewicz/download/DownloadControllerTest.groovy` |
