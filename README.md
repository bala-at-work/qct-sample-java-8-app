# Amazon Q Code Transformation: Sample Java 8 Project Built With Maven
Use this to test out Amazon Q Code Transformation!.

The project now targets **Java 21** and **Spring Boot 3.5**.

## Build and run

```
./mvnw -B clean verify
java -jar target/download-server-0.0.1-SNAPSHOT.jar
```

Requires JDK 21 (Amazon Corretto 21 recommended).

## Endpoints

| Method | Path | Behaviour |
|---|---|---|
| `GET`, `HEAD` | `/download/{uuid}` | `301` redirect to `/download/{uuid}/{originalName}` (or `304` if the client cache is valid) |
| `GET`, `HEAD` | `/download/{uuid}/{filename}` | Streams the file throttled to 1 MB/s with `ETag` (SHA-512), `Last-Modified`, `Content-Length`; honours `If-None-Match` / `If-Modified-Since` with `304` |
| `GET` | `/actuator/health` | Health check; it is the only exposed actuator endpoint |

`{uuid}` must be a valid UUID (`400` otherwise); other HTTP verbs return `405`.

**Access model:** `/download/**` is public and unauthenticated; the UUID is an identifier, not an authorization control.

**Stub storage:** `FileSystemStorage` is a stub that serves the bundled `logback.xml` for every UUID. Replace it with a real `FileStorage` implementation before serving real content.
