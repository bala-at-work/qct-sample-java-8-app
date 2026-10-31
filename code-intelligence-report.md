# Code Intelligence Report

## Build System
Maven only (single `pom.xml` at project root). No `build.gradle*`. Maven wrapper present (`mvnw`).
Build command: `mvn clean verify`

## Spring Boot Version
3.5.16 (via `spring-boot-starter-parent`)

## Java Version
`java.version` = 17 (project property). Environment JDK: Corretto 21.0.13.

## Size Metrics
- Modules: 1
- Estimated LOC (main + test Java/Groovy): 783
- Main Java files: 8, Test Java files: 3, Test Groovy (Spock) files: 4

## Build Dependencies
  org.springframework.boot:spring-boot-starter-parent
  org.codehaus.gmavenplus:gmavenplus-plugin
  org.apache.maven.plugins:maven-surefire-plugin
  org.springframework.boot:spring-boot-maven-plugin
  org.springframework.boot:spring-boot-starter-web
  org.springframework.boot:spring-boot-starter-actuator
  org.springframework.boot:spring-boot-starter-test
  com.google.guava:guava
  org.apache.groovy:groovy
  org.spockframework:spock-core
  org.spockframework:spock-spring

## Jackson Usage
No `com.fasterxml.jackson.*` imports in source. No custom serializers/deserializers (0).

## Spring Security
Not present. 0 SecurityFilterChain beans.

## Testing Infrastructure
Spock 2.4-groovy-4.0 + spring-boot-starter-test. `@SpringBootTest`, `@AutoConfigureMockMvc`,
`MockMvcBuilders.webAppContextSetup`. No `@MockBean` (0), no `@SpyBean` (0), no Testcontainers.

## Observability
`spring-boot-starter-actuator` present. No micrometer-registry-* or OpenTelemetry artifacts.
Actuator properties present in `application.properties` (management.endpoints/endpoint/info keys).

## Reactive / WebFlux
Not present.

## Other Signals
Spring AI: absent. Spring Batch: absent. Spring Kafka: absent. Spring AMQP: absent.
Spring Cloud: absent. Native/AOT: absent. Jersey: absent. Spring Retry: absent.
Undertow/Jetty: absent. SpringDoc/Spring Boot Admin: absent. gRPC: absent. jOOQ: absent.
JSpecify-relevant tooling (Kotlin/SpotBugs/NullAway/legacy nullability imports): absent.
