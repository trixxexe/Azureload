# Azureload Testing Strategy & Verification Record

## 1. Testing Philosophy
Azureload relies on deterministic, reproducible automated tests. Core transfer logic, chunk partitioning, capability detection, error classification, retry backoff, single-stream recovery, file collisions, and integrity verification are tested using local mock HTTP servers (`MockWebServer`) rather than unpredictable public endpoints.

---

## 2. Test Matrix & Coverage

| Test Case | Verification Target | Test Method |
| :--- | :--- | :--- |
| **Multi-connection Range Download** | Verify non-overlapping chunk downloads against an HTTP 206 server and accurate assembly on disk. | `MockWebServer` + `DownloadJobRunner` |
| **Single-stream Fallback** | Verify that a server returning HTTP 200 OK to range requests triggers graceful fallback without corrupting files. | `MockWebServer` + `DownloadJobRunner` |
| **Single-Stream Transient Retry** | Verify that transient 503 / 429 errors during single-stream transfer retry with backoff and complete. | `MockWebServer` + `DownloadJobRunner` |
| **Dynamic Stream Unknown Length** | Verify that chunked responses without `Content-Length` stream to EOF and finalize with exact size. | `MockWebServer` + `DownloadJobRunner` |
| **Short Read / Premature EOF** | Verify that an interrupted connection does not falsely mark a chunk or download complete, and triggers retry. | `MockWebServer` + `DownloadJobRunner` |
| **HTTP 429 & 5xx Backoff** | Verify numeric seconds and RFC HTTP-date `Retry-After` headers are parsed and respected. | `MockWebServer` + `CapabilityDetector` |
| **Fatal HTTP 4xx Errors** | Verify immediate termination without useless retries on 401, 403, 404 client errors. | `MockWebServer` + `CapabilityDetector` |
| **Zero-Byte File 416 Handling** | Verify that empty files returning 416 with `bytes */0` are recognized as 0-length files. | `MockWebServer` + `CapabilityDetector` |
| **Redirect Effective URL** | Verify that redirects update `currentDownload.url` to the effective endpoint for subsequent requests. | `MockWebServer` + `DownloadJobRunner` |
| **Remote Resource Change on Resume** | Verify that altered `ETag` or `Last-Modified` triggers a clean restart from byte 0. | `MockWebServer` + `DownloadJobRunner` |
| **File Collision Prevention** | Verify that existing files are never overwritten; incrementing counters (`file(1).bin`) are applied. | `StagingFileManagerTest` |
| **SHA-256 Checksum Validation** | Verify matching hashes pass and altered hashes fail download finalization. | Unit test + `IntegrityVerifier` |
| **Chunk Partition Boundaries** | Verify edge cases: 1 byte, odd remainders, files smaller than 1MB, maximum concurrency clamps. | Unit test + `ChunkSchedulerTest` |
| **Filename Sanitization** | Verify illegal characters, directory traversal (`../../etc/passwd`), and extreme filename lengths are neutralized. | Unit test + `FileNameSanitizerTest` |
| **Header & Disposition Extraction** | Verify standard, quoted with semicolons, and RFC 5987 UTF-8 encoded `Content-Disposition` filenames. | Unit test + `CapabilityDetectorParsingTest` |
| **Genuine 4 & 8 Connection Parallel Transfers** | Verify that transfers with 4 and 8 concurrency spawn distinct non-overlapping chunk ranges, execute in parallel, and accurately report active connections. | `MockWebServer` + `DownloadEngineIntegrationTest` |
| **Unsupported Range / HEXAQA Simulation** | Verify that servers returning HTTP 200 to Range requests are detected as non-range, force single stream, clear chunks, and report Single Stream / 1 connection without misleading concurrency. | `MockWebServer` + `DownloadEngineIntegrationTest` |
| **Pause & Resume File Reconstruction** | Verify that pausing midway and resuming incomplete chunks reconstructs the exact original file without data loss. | `MockWebServer` + `DownloadEngineIntegrationTest` |
| **Process-Death Recovery from Staging** | Verify that abrupt process kill marks transfers PAUSED and recovers cleanly from partial staging data without re-fetching completed chunks. | `MockWebServer` + `DownloadEngineIntegrationTest` |
| **Sub-1MB Range Support Single Stream** | Verify that files < 1MB with range support do not allocate multiple chunks, execute cleanly as single-stream, and report 1 connection. | `MockWebServer` + `DownloadEngineIntegrationTest` |
| **Effective Connection Calculation** | Verify mathematical bounds on parallel transfers across unknown sizes (-1L), clamped concurrency, and server capabilities. | Unit test + `ChunkSchedulerTest` |

---

## 3. Local Controlled HTTP Range Test Server & Benchmark Suite

A standalone, controlled HTTP server and benchmark suite are provided in `tools/`:

- `tools/range_test_server.py`: RFC 7233 / RFC 9110 compliant HTTP test server providing:
  - Byte range chunking (`HTTP 206 Partial Content`, `Content-Range`) on `/range-test.bin`.
  - Non-range simulation (`HTTP 200 OK` ignoring Range headers, replicating HEXAQA) on `/no-range.bin`.
  - Simulated per-connection bandwidth limits (`?rate_limit_kbps=...`).
  - Real-time statistics reporting on `/stats`.
- `tools/test_benchmark.py`: Benchmark suite measuring throughput and integrity across 1 vs 4 vs 8 connections, pause/resume chunk reassembly, and unaccelerated fallback detection.

### Running the Local Benchmark:

```bash
# Terminal 1: Run local range server
python3 tools/range_test_server.py 18080

# Terminal 2: Execute benchmark
python3 tools/test_benchmark.py
```

Benchmark output demonstrates linear throughput scaling across 1 vs 4 vs 8 parallel connections under per-connection rate limits, 100% SHA-256 integrity verification, and byte-range fallback handling.

---

## 4. Running Automated Tests

Tests are executed via Gradle Kotlin DSL in the local environment or CI:

```bash
./gradlew testDebugUnitTest --stacktrace --no-daemon
```

### GitHub Actions CI
The CI workflow (`.github/workflows/android-build.yml`) automatically executes `testDebugUnitTest` on every push or pull request to `main`, collects test reports, and uploads them as build artifacts (`Azureload-reports-<run_number>`).
