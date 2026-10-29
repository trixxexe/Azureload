# Azureload Architecture Overview

## 1. Architectural Philosophy
Azureload is designed according to Clean Architecture and Modern Android Architecture principles:
- **Unidirectional Data Flow (UDF)**: UI observing persistent Room flows and StateFlows.
- **Isolated Domain Layer**: Download and chunk models decoupled from network or storage implementations.
- **Fail-Safe I/O & Memory Safety**: Streaming I/O with fixed 32KB buffers; zero full-file in-memory buffering.
- **Durable Crash-Resistant State**: All state transitions (Queued, Detecting, Downloading, Paused, Finalizing, Completed, Failed) are backed by SQLite transactions via Room.

---

## 2. Layered Component Architecture

```
[ UI Layer: Jetpack Compose + Material 3 ]
  ├── DashboardScreen
  ├── AddDownloadScreen
  ├── BrowserScreen (WebView + Interceptor)
  ├── DetailScreen (Real-time Chunk Grid)
  ├── SettingsScreen
  └── DiagnosticsScreen
         │
         ▼
[ Engine & Coordination Layer ]
  ├── DownloadCoordinator (Concurrency limiter, queue drainer, network listener)
  ├── DownloadJobRunner (Transfer orchestrator, fallback controller, integrity verifier)
  └── ChunkScheduler (Byte-range partitioner, minimum 1MB chunk clamp)
         │
         ├───────────────────────────────┬──────────────────────────────┐
         ▼                               ▼                              ▼
[ Network Layer: OkHttp ]     [ Storage Layer ]              [ Data Layer: Room ]
  ├── HttpClientProvider        ├── StagingFileManager (RAF)   ├── AzureloadDatabase
  ├── CapabilityDetector        ├── IntegrityVerifier (SHA256) ├── DownloadDao / ChunkDao
  └── DownloadExceptions        └── FileNameSanitizer          └── DownloadRepository
         │                               │
         └───────────────┬───────────────┘
                         ▼
        [ Android Background Services ]
          ├── DownloadForegroundService (dataSync)
          ├── AzureloadNotificationManager
          ├── DownloadNotificationActionReceiver
          └── DownloadRecoveryWorker (WorkManager)
```

---

## 3. Concurrency & Transfer Mechanics

### A. Capability Detection
Before transferring byte ranges, Azureload executes a lightweight probe:
1. Azureload issues an HTTP GET with `Range: bytes=0-0`.
2. If the server responds with `HTTP 206 Partial Content` and a valid `Content-Range: bytes 0-0/<total>` header:
   - Byte-range support is confirmed.
   - Resource validators (`ETag`, `Last-Modified`) and `Content-Length` are captured.
3. If the server responds with `HTTP 200 OK`:
   - The server ignores Range headers.
   - Range capability is flagged as `false`, triggering single-connection download.
4. If dynamic compression (e.g., `Content-Encoding: gzip`) is detected:
   - Byte ranges are disabled because decompressed streams cannot be safely partitioned.

### B. Multi-Connection Byte-Range Scheduling
When range capability is confirmed and requested concurrency > 1:
1. `ChunkScheduler` partitions the file into `N` non-overlapping chunks:
   - Chunk $i$ has byte range $[start_i, end_i]$.
   - Each chunk has minimum size of 1 MB.
   - Concurrency is bounded between 1 and 16.
2. The staging file is pre-allocated on disk to total file size using `RandomAccessFile.setLength`.
3. Chunks are launched as concurrent coroutines under structured concurrency (`coroutineScope + async`).
4. Each chunk streams data into its designated file offset using `RandomAccessFile.seek(currentOffset)` guarded by path-level locks.
5. If any chunk encounters `HTTP 200 OK` (server ignored Range header mid-flight):
   - The chunk raises `RangeNotSupportedException`.
   - The orchestrator cleanly cancels chunk workers, clears the sparse staging file, resets chunk entities, and falls back to single-stream download.
6. Short reads and premature EOFs are detected: if a connection terminates before reading all expected chunk bytes, it throws an `IOException` and triggers exponential backoff retry from the current byte offset.

### C. Single-Connection Fallback & Resumption
When range capability is unavailable or concurrency is 1:
1. If resuming a download on a range-capable server:
   - `Range: bytes=<downloadedBytes>-` is requested.
   - If server responds with 206, bytes are appended to the staging file.
   - If server responds with 200, the server ignored the resume request; the staging file is reset to 0 to prevent byte corruption.
2. If server does not support ranges:
   - The download streams sequentially from byte 0.

### D. Crash & Process Death Recovery
1. Every 500ms and upon chunk completion or coroutine cancellation, downloaded byte offsets are committed to Room database.
2. If the Android OS terminates the process, `DownloadCoordinator` upon startup queries incomplete downloads and marks them as `PAUSED`.
3. When resumed, `DownloadJobRunner` checks remote `ETag` and `Last-Modified` headers against persisted values.
4. If remote headers match, resumption proceeds from the exact recorded byte offsets.
5. If remote headers differ, stale bytes are discarded and the download restarts cleanly from byte 0.

---

## 4. Storage & Security Model
- **Scoped Storage Compatibility**: Downloads stage in internal app storage (`context.filesDir/staging`) where no permissions are required. Upon completion, files are finalized to the public `Downloads` directory, falling back gracefully to app external files or internal storage.
- **Path Traversal Protection**: `FileNameSanitizer` strips illegal characters (`/ \ : * ? " < > |`) and removes leading dots to prevent directory traversal.
- **File Collision Handling**: Existing files are never overwritten; incrementing counters (e.g. `file(1).zip`) are applied automatically.
- **Cryptographic Validation**: Optional SHA-256 verification is computed via streaming `MessageDigest` after transfer completion.
