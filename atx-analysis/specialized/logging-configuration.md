# Logging Configuration

[← Back to index](../README.md)

The only framework configuration file in the repository.

## File: `src/main/resources/logback.xml`

```xml
<configuration>
    <logger name="com" level="INFO"/>
    <logger name="com.nurkiewicz" level="ALL"/>
    <logger name="net" level="INFO"/>
    <logger name="org" level="INFO"/>

    <root level="DEBUG">
        <appender class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} | %thread | %-5level | %logger{1} | %msg%n</pattern>
            </encoder>
        </appender>
    </root>
</configuration>
```

## Logger Hierarchy

| Logger | Level | Effect |
|---|---|---|
| `com` | `INFO` | Quiets third-party `com.*` libraries (notably Guava) |
| `com.nurkiewicz` | `ALL` | Full verbosity for application code — overrides the `com` level |
| `net` | `INFO` | Quiets `net.*` libraries |
| `org` | `INFO` | Quiets Spring, Tomcat, Apache Commons |
| root | `DEBUG` | Fallback for anything not matched above |

The `com` → `com.nurkiewicz` pair is the intended pattern: suppress the noisy parent namespace, then re-enable everything for the application's own package.

## Appender

| Property | Value |
|---|---|
| Class | `ch.qos.logback.core.ConsoleAppender` |
| Destination | stdout |
| Encoder pattern | `%d{HH:mm:ss.SSS} \| %thread \| %-5level \| %logger{1} \| %msg%n` |

Pattern fields: time (no date), thread name, left-aligned 5-char level, abbreviated logger name (`%logger{1}` → simple class name), message.

Example output:
```
08:58:13.204 | http-nio-8080-exec-1 | DEBUG | ExistingFile | Serving GET 'FileSystemPointer{target=/app/logback.xml, originalName=logback.xml, size=412, tag=cf83e135..., mediaType=text/xml}'
```

## Log Statements in Source

| Class | Level | Statement | Notes |
|---|---|---|---|
| `ExistingFile` | DEBUG | `log.debug("Serving {} '{}'", method, filePointer)` | Emits the full `FileSystemPointer.toString()` — absolute path, size, hash, media type |
| `ExistingFile` | TRACE | `log.trace("Redirecting {} '{}'", method, filePointer)` | Same disclosure |
| `ExistingFile` | TRACE | `log.trace("Cached on client side {}, returning 304", filePointer)` | Same disclosure |
| `FileSystemStorage` | DEBUG | `log.debug("Downloading {}", uuid)` | Logs the requested UUID |
| `FileStorageStub` (test) | DEBUG | `log.debug("Downloading {}", uuid)` | Logs the requested UUID |

All five use SLF4J parameterized placeholders (`{}`) rather than string concatenation — correct practice, avoiding formatting cost when the level is disabled.

## Dependency Note

SLF4J (`org.slf4j.Logger`, `LoggerFactory`) is imported directly by three classes but is **not declared** in `pom.xml`. It arrives transitively via `spring-boot-starter-web` → `spring-boot-starter-logging`, which also supplies the Logback implementation referenced by FQCN in this file. A change in Boot's logging starter would break compilation. SLF4J should be declared explicitly.

No Log4j 1.x or 2.x is present anywhere in the repository.

## Findings

| Finding | Severity | Detail |
|---|---|---|
| Sensitive values logged | Low | The UUID functions as a bearer capability under the service's implicit access model (see [../analysis/security-patterns.md](../analysis/security-patterns.md) S-05), yet it is logged at DEBUG. `FileSystemPointer.toString()` additionally discloses absolute filesystem paths. With `com.nurkiewicz` at `ALL`, both are emitted in full. |
| Verbose production defaults | Low | `com.nurkiewicz` at `ALL` and root at `DEBUG` produce high log volume. There is no profile-specific override because no `application.yml` exists. |
| Console-only output | Low | No file appender, no rolling policy, no syslog or structured output. Acceptable for a containerized deployment where stdout is collected, but no container definition exists in the repository to confirm that intent. |
| No structured logging | Low | Plain-text pipe-delimited output is not machine-parseable. JSON encoding would suit log aggregation. |
| No MDC / correlation IDs | Low | Nothing correlates log lines belonging to the same request. The pattern includes `%thread`, which is a weak proxy. |
| No date in the timestamp | Low | `%d{HH:mm:ss.SSS}` omits the date, making multi-day log analysis ambiguous. |
| Served as content | Medium | This file is what `FileSystemStorage` returns for every download request — see finding S-03. Any secret ever added to this file would be publicly readable. It currently contains none. |

## Recommendations

| Priority | Recommendation |
|---|---|
| Medium | Stop serving this file to clients by implementing a real storage adapter (R-05) |
| Low | Lower `com.nurkiewicz` from `ALL` to `INFO` for production and raise it only in a development profile |
| Low | Stop logging the full `FilePointer.toString()` (absolute paths) and reconsider logging the UUID given its capability semantics |
| Low | Add the date to the timestamp pattern |
| Low | Add MDC-based request correlation |
| Low | Consider JSON structured output if logs are aggregated centrally |

See [deployment.md](deployment.md) and [http-caching.md](http-caching.md).
