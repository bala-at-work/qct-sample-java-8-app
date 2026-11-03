# ATXDocumentation — download-server

Comprehensive static-analysis documentation for `com.nurkiewicz.download:download-server` (Spring Boot 1.2.3.RELEASE / Java 8 / Maven).

> This documentation was produced by **static analysis only**. No code was compiled, built, executed, or tested.

## 🔴 Start Here

| Document | Purpose |
|---|---|
| [technical-debt-report.md](technical-debt-report.md) | **Executive technical debt report + AWS transformation recommendation** |
| [project-overview.md](project-overview.md) | What this project is, technology stack, repository layout |

## Table of Contents

### Architecture
- [architecture/system-overview.md](architecture/system-overview.md) — High-level architecture, stack, deployment model
- [architecture/components.md](architecture/components.md) — Components, responsibilities, interactions
- [architecture/dependencies.md](architecture/dependencies.md) — Internal + external dependencies with versions
- [architecture/patterns.md](architecture/patterns.md) — Architectural/design patterns and anti-patterns

### Diagrams (text-based)
- [diagrams/structural.md](diagrams/structural.md) — Component, class and package diagrams
- [diagrams/behavioral.md](diagrams/behavioral.md) — Sequence, activity, state and data-flow diagrams
- [diagrams/architecture.md](diagrams/architecture.md) — System context, integration and security boundaries

### Technical Debt
- [technical-debt/summary.md](technical-debt/summary.md) — Overview of all findings
- [technical-debt/outdated-components.md](technical-debt/outdated-components.md) — EOL runtimes, frameworks, libraries
- [technical-debt/maintenance-burden.md](technical-debt/maintenance-burden.md) — High-maintenance areas
- [technical-debt/remediation-plan.md](technical-debt/remediation-plan.md) — Prioritized action items

### Reference
- [reference/program-structure.md](reference/program-structure.md) — Full structural hierarchy
- [reference/interfaces.md](reference/interfaces.md) — Interfaces, abstract types, contracts
- [reference/data-models.md](reference/data-models.md) — Types and relationships
- [reference/api-reference.md](reference/api-reference.md) — Endpoints and public methods
- [reference/modules.md](reference/modules.md) — Module organization

### Analysis
- [analysis/code-metrics.md](analysis/code-metrics.md) — Size and quality indicators
- [analysis/complexity-analysis.md](analysis/complexity-analysis.md) — Complexity hotspots
- [analysis/dependency-analysis.md](analysis/dependency-analysis.md) — Dependency mapping and criticality
- [analysis/security-patterns.md](analysis/security-patterns.md) — Code-level security posture
- [analysis/tech-debt.md](analysis/tech-debt.md) — Comprehensive debt assessment

### Migration
- [migration/component-order.md](migration/component-order.md) — Dependency-ordered migration sequence
- [migration/test-specifications.md](migration/test-specifications.md) — Test specifications for validation
- [migration/validation-criteria.md](migration/validation-criteria.md) — Success criteria

### Specialized
- [specialized/http-api-examples.md](specialized/http-api-examples.md) — HTTP request/response examples, caching and throttling semantics
- [specialized/infrastructure-and-deployment.md](specialized/infrastructure-and-deployment.md) — Build/packaging, runtime configuration, profiles, logging, observability, capacity

*Not applicable to this codebase (verified absent): database schemas, UI components, message queue/event patterns.*

## Component Index

| Component | Type | Source |
|---|---|---|
| `MainApplication` | Spring Boot entry point | `src/main/java/com/nurkiewicz/download/MainApplication.java` |
| `DownloadController` | REST controller | `src/main/java/com/nurkiewicz/download/DownloadController.java` |
| `ExistingFile` | HTTP response builder | `src/main/java/com/nurkiewicz/download/ExistingFile.java` |
| `FilePointer` | Interface (file port) | `src/main/java/com/nurkiewicz/download/FilePointer.java` |
| `FileStorage` | Interface (lookup port) | `src/main/java/com/nurkiewicz/download/FileStorage.java` |
| `FileSystemPointer` | `FilePointer` implementation | `src/main/java/com/nurkiewicz/download/FileSystemPointer.java` |
| `FileSystemStorage` | `FileStorage` implementation (stub) | `src/main/java/com/nurkiewicz/download/FileSystemStorage.java` |
| `ThrottlingInputStream` | Rate-limiting stream decorator | `src/main/java/com/nurkiewicz/download/ThrottlingInputStream.java` |
| `FileExamples` | Test fixtures | `src/test/java/com/nurkiewicz/download/FileExamples.java` |
| `FileStorageStub` | Test `FileStorage` | `src/test/java/com/nurkiewicz/download/FileStorageStub.java` |
| `Sha512ShallowEtagHeaderFilter` | Unused servlet filter | `src/test/java/org/springframework/web/filter/Sha512ShallowEtagHeaderFilter.java` |
| `DownloadControllerSpec` | Spock specification | `src/test/groovy/com/nurkiewicz/download/DownloadControllerTest.groovy` |
