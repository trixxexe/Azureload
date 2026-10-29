# Azureload Testing Strategy & Verification Record

## 1. Testing Philosophy
Azureload relies on deterministic, reproducible automated tests. Core transfer logic, chunk partitioning, capability detection, error classification, retry backoff, and integrity verification are tested using local mock HTTP servers (`MockWebServer`) rather than unpredictable public endpoints.

---

## 2. Test Matrix & Coverage

| Test Case | Verification Target | Test Method |
| :--- | :--- | :--- |
| **Multi-connection Range Download** | Verify non-overlapping chunk downloads against an HTTP 206 server and accurate assembly on disk. | `MockWebServer` + `DownloadJobRunner` |
| **Single-stream Fallback** | Verify that a server returning HTTP 200 OK to range requests triggers graceful fallback without corrupting files. | `MockWebServer` + `DownloadJobRunner` |
| **Short Read / Premature EOF** | Verify that an interrupted connection does not falsely mark a chunk or download complete, and triggers retry. | `MockWebServer` + `DownloadJobRunner` |
| **HTTP 429 Too Many Requests** | Verify that `Retry-After` headers are parsed and respected during exponential backoff. | `MockWebServer` + `DownloadJobRunner` |
| **HTTP 5xx Transient Errors** | Verify retry logic with jittered backoff on transient server failures (500, 502, 503). | `MockWebServer` + `DownloadJobRunner` |
| **Fatal HTTP 4xx Errors** | Verify immediate termination without useless retries on 401, 403, 404 client errors. | `MockWebServer` + `DownloadJobRunner` |
| **Remote Resource Change on Resume** | Verify that altered `ETag` or `Last-Modified` triggers a clean restart from byte 0. | `MockWebServer` + `DownloadJobRunner` |
| **SHA-256 Checksum Validation** | Verify matching hashes pass and altered hashes fail download finalization. | Unit test + `IntegrityVerifier` |
| **Chunk Partition Boundaries** | Verify edge cases: 1 byte, odd remainders, files smaller than 1MB, maximum concurrency clamps. | Unit test + `ChunkSchedulerTest` |
| **Filename Sanitization** | Verify illegal characters, directory traversal (`../../etc/passwd`), and extreme filename lengths are neutralized. | Unit test + `FileNameSanitizerTest` |
| **Header & Disposition Extraction** | Verify standard and RFC 5987 UTF-8 encoded `Content-Disposition` filenames. | Unit test + `CapabilityDetectorParsingTest` |

---

## 3. Running Automated Tests

Tests are executed via Gradle Kotlin DSL in the local environment or CI:

```bash
./gradlew testDebugUnitTest --stacktrace --no-daemon
```

### GitHub Actions CI
The CI workflow (`.github/workflows/android-build.yml`) automatically executes `testDebugUnitTest` on every push or pull request to `main`, collects test reports, and uploads them as build artifacts (`Azureload-reports-<run_number>`).
