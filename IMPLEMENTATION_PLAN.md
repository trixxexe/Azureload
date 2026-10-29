# Azureload Implementation Plan

## 1. Product Mission & Goals
Azureload is a native Android download accelerator and manager built to provide desktop-grade transfer speeds, rock-solid resumption, and complete corruption resistance. It leverages Kotlin, Jetpack Compose, Material 3, Room, OkHttp, and Android Foreground Services.

### Primary Goals
1. **Multi-Connection Acceleration**: Safely segment byte-range-supported files into concurrent connections (up to 16 threads) for maximum bandwidth utilization.
2. **Transparent Fallback**: Seamlessly fallback to reliable single-stream downloading when servers ignore range headers or serve dynamic/chunked content.
3. **Robust Resumption**: Support persistent resume across process death, device reboots, and network dropouts via direct-offset staging files.
4. **Zero Silent Corruption**: Validate boundaries, response headers, byte counts, and optional SHA-256 hashes. Incomplete files are never marked completed.
5. **Versatile Intake**: Support URL pasting, Android system share sheet (e.g. from Chrome), and an integrated WebView browser with cookie and header interception.

---

## 2. System Architecture & Modules

```
com.ritam.azureload
├── core
│   ├── model          # Domain models (DownloadItem, ChunkItem, ServerCapability, Enums)
│   ├── network        # OkHttp client, CapabilityDetector, Error classification
│   ├── database       # Room DB, DownloadDao, ChunkDao, DownloadRepository
│   ├── storage        # StagingFileManager (RandomAccessFile), IntegrityVerifier, Sanitizer
│   ├── settings       # SettingsManager (SharedPreferences, Wi-Fi checks)
│   ├── engine         # DownloadCoordinator, DownloadJobRunner, ChunkScheduler
│   └── background     # DownloadForegroundService, NotificationManager, WorkManager
├── feature
│   ├── dashboard      # Filterable download dashboard (All, Active, Completed, Failed)
│   ├── add            # Paste URL screen with capability probing & header config
│   ├── browser        # WebView browser with automated download interception
│   ├── detail         # Download inspection, real-time chunk progress, metrics
│   ├── settings       # Network and concurrency configuration
│   └── diagnostics    # Storage health, active jobs, troubleshooting
└── ui
    ├── navigation     # Type-safe Jetpack Compose navigation
    └── theme          # Material 3 colors, shapes, typography
```

---

## 3. Implementation Milestones

### Milestone 1: Workspace & Toolchain Validation
- Android API 35 (minSdk 29, targetSdk 35) with Java 17.
- Reproducible Gradle builds with GitHub Actions CI workflow producing debug and release APKs.

### Milestone 2: Core Data & Storage Engine
- Room database schema (`downloads` and `chunks` tables with foreign key cascade).
- `StagingFileManager` using bounded buffers and `RandomAccessFile` with concurrent lock isolation.
- `FileNameSanitizer` preventing directory traversal and filesystem-illegal characters.
- `IntegrityVerifier` for size verification and cryptographic SHA-256 validation.

### Milestone 3: Capability Detection & Range Engine
- Probe byte-range capability via `Range: bytes=0-0` request.
- Parse `Content-Range`, `Content-Length`, `ETag`, `Last-Modified`, `Content-Encoding`, `Content-Disposition`.
- Classify HTTP errors into `FatalDownloadException` (4xx client errors) and `TransientDownloadException` (429, 5xx server errors).
- Implement `ChunkScheduler` to slice files into non-overlapping byte ranges of at least 1MB each up to 16 threads.
- Concurrent chunk download with streaming bounded I/O, offset writes, and short-read detection.
- Single-connection fallback when remote servers return HTTP 200 OK to range requests.

### Milestone 4: Android Lifecycle & Background Execution
- Foreground Service with ongoing system notification displaying real-time speed, progress, and Pause/Cancel actions.
- Notification action broadcast receiver dispatching directly to `DownloadCoordinator`.
- Process recovery on application startup, restoring unfinished downloads to paused state.
- Wi-Fi only constraint enforcement with automatic resumption upon network availability.

### Milestone 5: UI & Native Intake Points
- Downloads Dashboard with state filtering, progress bars, real-time transfer speeds, and Open/Retry/Delete actions.
- Add Download screen with URL clipboard detection, real-time server capability inspection, and header configuration.
- WebView Browser with download listener intercepting cookies, User-Agent, and suggested filenames.
- Transfer Detail screen displaying individual chunk progress bars, byte offsets, speed, and ETA.
- Settings screen with persistent preferences for Wi-Fi only mode, default concurrency, and transfer limits.
- Diagnostics screen reporting internal storage health and active engine jobs.

### Milestone 6: Automated Testing & Production Delivery
- Deterministic JVM unit tests using `MockWebServer` testing multi-connection downloads, single-stream fallback, short-read detection, 429 Retry-After, 5xx errors, ETag changes, and SHA-256 verification.
- Continuous verification on GitHub Actions CI.
