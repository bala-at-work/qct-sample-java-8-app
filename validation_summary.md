# Validation Summary — Spring Boot 3.5.16 -> 4.1.2 Migration

Repo: download-server (single-module Maven)
Source: Spring Boot 3.5.16 / Java 17
Target: Spring Boot 4.1.2 / Java 21
Build command: `mvn -B clean verify`

## STEP RESULTS

**Step:** Router assessment (code-intelligence-report.md, migration-plan.json, .migration-test-baseline.tsv)
**Status:** PASS
**Evidence:** All three artifacts generated before source modification; complexity tier `green-small`; TDs required = core-migration, observability, verification.

**Step:** springboot4-core-migration — version bump
**Status:** PASS
**Evidence:** `pom.xml` parent `spring-boot-starter-parent` 3.5.16 -> 4.1.2; `java.version` 17 -> 21.

**Step:** springboot4-core-migration — starter renames / modular starters
**Status:** PASS
**Evidence:** `spring-boot-starter-web` -> `spring-boot-starter-webmvc`; added test-scoped `spring-boot-starter-webmvc-test`. No `spring-boot-starter-web<` remains.

**Step:** springboot4-core-migration — package relocations
**Status:** PASS
**Evidence:** `@AutoConfigureMockMvc` relocated to `org.springframework.boot.webmvc.test.autoconfigure` in `ActuatorExposureSpec.groovy`.

**Step:** Jackson 3 migration
**Status:** PASS (no-op)
**Evidence:** No `com.fasterxml.jackson.*` imports or coordinates in `src` or `pom.xml`.

**Step:** Spring Security 7 DSL / @MockBean removal / property renames
**Status:** PASS (no-op)
**Evidence:** No Spring Security dependency; no `@MockBean`/`@SpyBean`, `antMatchers`, `authorizeRequests()`, or removed property keys.

**Step:** springboot4-observability
**Status:** PASS (no-op)
**Evidence:** No Micrometer/OpenTelemetry dependencies or OTLP properties; no relocated actuator autoconfigure packages in use.

**Step:** Test-toolchain compatibility (Spock / Groovy 5)
**Status:** PASS
**Evidence:** `spock.version` 2.4-groovy-4.0 -> 2.4-groovy-5.0; gmavenplus `targetBytecode=${java.version}`.

**Step:** springboot4-verification — build and tests
**Status:** PASS
**Evidence:** `mvn -B clean verify` -> BUILD SUCCESS; `Tests run: 26, Failures: 0, Errors: 0, Skipped: 0`.

**Step:** Test integrity
**Status:** PASS
**Evidence:** No test files, classes, or methods removed or disabled; no skip flags. Only test change is the `/actuator/health` expected JSON, updated for Boot 4's default liveness/readiness groups with strict comparison retained.

**Step:** Honest results reporting
**Status:** PASS
**Evidence:** `migration-results.yaml` at project root with real build-log reference and counts matching Surefire (26/26/0/0); this `validation_summary.md` at project root.

## OVERALL STATUS: COMPLETE

## MANUAL REVIEW ITEMS
- Boot 4 enables liveness/readiness health groups by default; `/actuator/health` now returns `{"status":"UP","groups":["liveness","readiness"]}`. Set `management.health.probes.enabled=false` if group names should not be exposed.
- Spock moved to the groovy-5.0 variant to match the Groovy 5 version managed by the Boot 4 BOM.
