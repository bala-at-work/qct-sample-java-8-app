# Java 17 / Spring Boot 3.2.12 Upgrade Notes

## Dependencies removed (with rationale)
- `cglib-nodep` 3.1 — removed. It was only needed by Spock 1.0 to mock concrete classes on Groovy 2.4.
  Spock 2.4-M4-groovy-4.0 uses ByteBuddy (pulled in transitively), so an explicit cglib pin is both
  unnecessary and incompatible (cglib 3.1 does not support Java 17 bytecode).
- Explicit `org.springframework:spring-*` 4.1.6.RELEASE pins — removed. Versions are now managed by the
  `spring-boot-starter-parent` 3.2.12 BOM (Spring Framework 6.1.x). Keeping explicit pins would override
  the BOM and break Boot 3 / Jakarta compatibility.
- `http://repo.spring.io/milestone/` repository — removed. Insecure (plain HTTP) and no longer needed:
  all artifacts resolve from Maven Central.

## Updates skipped
- None. Every dependency present in the baseline pom was either upgraded, or intentionally removed/replaced
  as documented above. No update was skipped for incompatibility.

## Mandated items not applicable to this project
- Gradle wrapper upgrade — Maven project, no Gradle build files.
- Lombok — not used by the project (absent from the dependency tree).
- Direct Jakarta servlet/persistence/validation dependencies — not declared; `jakarta.annotation-api` 2.1.1
  comes transitively from the Boot 3.2.12 BOM.
- `maven-compiler-plugin` 3.13+ — the explicit pin was dropped in favour of the version inherited from the
  Boot parent (3.11.0), because the local Maven is 3.8.7 and 3.13+ requires Maven >= 3.9.0.

## Verification
`JAVA_HOME=/usr/lib/jvm/java-17-amazon-corretto mvn -B clean verify` — BUILD SUCCESS, 12/12 tests pass.
