# Component Migration Order

[← Back to index](../README.md) · Related: [../technical-debt/remediation-plan.md](../technical-debt/remediation-plan.md) · [validation-criteria.md](validation-criteria.md)

Order derived from the internal dependency graph in [../analysis/dependency-analysis.md](../analysis/dependency-analysis.md). The graph is acyclic, so a clean leaf-first order exists.

## Dependency-Derived Ordering

```
Tier 0  (no internal dependencies — migrate first)
   ├── pom.xml / build configuration
   ├── FilePointer      (interface, zero internal deps)
   ├── FileStorage      (interface, depends only on FilePointer)
   └── ThrottlingInputStream  (depends only on the JDK + Guava RateLimiter)

Tier 1  (depends on Tier 0)
   ├── FileSystemPointer   implements FilePointer
   └── logback.xml         (independent, but validate after the Boot upgrade)

Tier 2  (depends on Tier 0–1)
   ├── FileSystemStorage   implements FileStorage, creates FileSystemPointer
   └── ExistingFile        uses FilePointer + ThrottlingInputStream

Tier 3  (depends on Tier 0–2)
   └── DownloadController  uses FileStorage + ExistingFile

Tier 4  (bootstrap)
   └── MainApplication

Tier 5  (test tree — must compile and pass against every tier above)
   ├── FileExamples               → FileSystemPointer
   ├── FileStorageStub            → FileStorage, FileExamples
   ├── Sha512ShallowEtagHeaderFilter → Spring framework internals
   └── DownloadControllerSpec     → MainApplication context, FileExamples
```

## Recommended Execution Sequence

### Step 1 — Build configuration (`pom.xml`)
Single highest-leverage change; everything else depends on it.
- Remove the plaintext `http://` milestone repository.
- Raise the parent to a supported Spring Boot generation; set `maven-compiler-plugin` to a current version with `<release>` for the target LTS.
- Delete the eight explicit `spring-*` version pins, the unused `<spring.version>` property, the duplicate `spring-test` declaration, and the now-pointless `spring-test` exclusion.
- Delete the unused `commons-codec`; move `groovy-all` and `cglib-nodep` to `test` scope.
- Add a Groovy compiler plugin so `src/test/groovy` is compiled.
- **Checkpoint**: dependencies resolve; no explicit version conflicts remain.

### Step 2 — Tier 0 interfaces and the streaming utility
`FilePointer`, `FileStorage`, `ThrottlingInputStream` have no framework coupling and need no changes beyond anything the language-level upgrade implies. Optionally clarify `modifiedAfter` naming here (see R9) because every downstream consumer is touched in later steps anyway.
- **Checkpoint**: the two interfaces are unchanged in shape; public method names preserved.

### Step 3 — `FileSystemPointer`
The Guava-sensitive class.
- `Files.hash(target, Hashing.sha512())` → `Files.asByteSource(target).hash(Hashing.sha512())`.
- Keep the ETag string format (quoted hash) byte-for-byte identical — the specs compare it.
- Optionally narrow the `IllegalArgumentException` wrapping.
- **Checkpoint**: ETag format and `getSize`/`getOriginalName`/`getLastModified` semantics unchanged.

### Step 4 — `FileSystemStorage`
- No framework-breaking change expected; add the missing null check on `getResource`.
- Defer replacing the stub with a real backend until after the upgrade is validated (it is a product change, not a migration step).
- **Checkpoint**: profile annotation still resolves under the new Boot generation.

### Step 5 — `ExistingFile`
The second Guava-sensitive class and the behavioural core.
- Rewrite `toMediaType` without Guava's removed `Optional.transform/or`.
- Verify `ResponseEntity.status(...).eTag(...).contentLength(...).lastModified(...)` builder methods against the new Spring version; `lastModified` accepts an `Instant` directly in modern Spring, which simplifies line 107.
- Preserve exactly: `200`/`301`/`304` selection, `HEAD` null-body behaviour, and the header set returned with `304`.
- **Checkpoint**: all caching-related specs pass.

### Step 6 — `DownloadController`
- Confirm `@RequestHeader(...) Optional<T>` binding still behaves identically.
- Consider `Optional<ZonedDateTime>` in place of `Optional<Date>`; if changed, update `ExistingFile.cached` accordingly.
- Remove the stray `@PathVariable` on the private helper's parameter (`DownloadController:57`) — it has no effect.
- **Guardrail**: do not rename `redirect` or `download`; they are the public API.
- **Checkpoint**: both endpoints still map to the same URL templates and verbs.

### Step 7 — `MainApplication`
- Delete the dead `new Integer("1234")` statement.
- **Guardrail**: keep the class name and `main` signature.
- **Checkpoint**: the application context starts under the `test` profile in the spec.

### Step 8 — Test tree
- `Sha512ShallowEtagHeaderFilter`: follow the `jakarta`-based `ShallowEtagHeaderFilter`; decide whether to relocate it out of the framework package.
- `FileExamples` / `FileStorageStub`: add `@Override`; otherwise unchanged.
- `DownloadControllerSpec`: migrate to Spock 2.x idioms if the Spock major version changes; align the file name with the declared class so test discovery is unambiguous.
- **Guardrail**: all 12 feature methods are preserved. Assertions may be adjusted only where a framework API genuinely changed, never deleted.
- **Checkpoint**: full suite green — see [test-specifications.md](test-specifications.md).

### Step 9 — Post-migration improvements (independent, any order)
ETag caching (R6), real storage backend (R7), error-handling advice (R8), response hardening (R11), Actuator decision (R3), supply-chain automation (R12).

## Risk by Step

| Step | Risk | Mitigation |
|---|---|---|
| 1 `pom.xml` | High — the whole dependency graph moves at once | Change the parent and plugin first, resolve compile errors tier by tier |
| 3, 5 Guava call sites | Medium — removed/deprecated APIs with silent behavioural nuances (charset handling in `toMediaType`) | The `'should return content type in response'` spec pins the observable behaviour |
| 5 `ExistingFile` | Medium — caching semantics are subtle | 7 of the 12 specs target caching directly |
| 6 Controller binding | Medium — `Optional<Date>` header conversion is framework-dependent | Two specs exercise `If-Modified-Since` with real RFC 1123 strings |
| 8 Test stack | Medium — Spock/Groovy/JUnit platform alignment | Migrate the build wiring before touching spec code |
| 2, 4, 7 | Low | Minimal surface |

## Parallelization

Steps 2–4 (interfaces, pointer, storage) can proceed in parallel once Step 1 lands, since they share no edit sites. Steps 5–7 must follow their dependencies. Step 8 requires everything else to compile.
