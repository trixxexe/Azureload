# Azureload (BOLT Mobile)

A high-performance native Android download manager and multi-connection accelerator inspired by desktop download engines. Built from scratch with Kotlin, Jetpack Compose, Material 3, Room, OkHttp, and Android Foreground Services.

---

## Key Features

1. **Multi-Connection Acceleration**: Slices known-size files into non-overlapping byte ranges and downloads concurrently with up to 16 configurable connections.
2. **Reliable Capability Detection**: Actively probes byte-range compatibility, MIME types, stable validators (ETags), and compression before chunking.
3. **Single-Stream Fallback**: Automatically and transparently falls back to reliable single-stream downloads when byte ranges are unsupported, proxies strip headers, or file sizes are dynamic.
4. **Resumable & Crash-Proof**: Persistent staging file updates using direct `RandomAccessFile` offset writes. Survives application death, network interruptions, and system reboots.
5. **Three Download Entry Points**:
   - **Built-in WebView Browser**: Web navigation with automated download interception, cookie capture, and filename inference.
   - **Paste URL**: Clipboard detection, connection testing, header customization, and capability inspection.
   - **System Share Sheet**: Direct link sharing from Chrome and any third-party app.
6. **Background Resilience**: Dedicated Foreground Service with ongoing notifications, real-time byte counters, and Pause/Resume/Cancel quick actions.
7. **Integrity Validation**: Zero silent file corruption. Pre-allocation boundary checks, size matching, and optional SHA-256 verification.
8. **File Provider & Direct Launch**: Open completed downloads directly in external apps via Android `FileProvider`.
9. **Wi-Fi Only & Queue Management**: Automatic network-aware scheduling preventing unwanted mobile data consumption.

---

## Documentation

- [Implementation Plan](IMPLEMENTATION_PLAN.md) — Product vision, modules, milestones, and production checklist.
- [Architecture](ARCHITECTURE.md) — Layered design, concurrency model, chunk scheduling, and recovery mechanics.
- [Testing Guide](TESTING.md) — Automated testing matrix, MockWebServer integration tests, and CI instructions.
- [Third Party Notices](THIRD_PARTY_NOTICES.md) — Open-source inventory, licenses, and attributions.

---

## Architecture Overview

```
app/src/main/java/com/ritam/azureload/
├── core/
│   ├── model/          # Download & Chunk domain entities, Enums, State machine
│   ├── network/        # OkHttp client, CapabilityDetector, Error classification
│   ├── database/       # Room database, DownloadDao, ChunkDao, Repository
│   ├── storage/        # StagingFileManager (RandomAccessFile), File finalizer, IntegrityVerifier
│   ├── settings/       # SettingsManager (SharedPreferences, Wi-Fi checks)
│   ├── engine/         # DownloadCoordinator, DownloadJobRunner, ChunkScheduler
│   └── background/     # ForegroundService, NotificationManager, WorkManager
├── feature/
│   ├── dashboard/      # Download queue, active cards, filtering, Open/Retry actions
│   ├── detail/         # Real-time chunk visualization, speed & ETA graphs
│   ├── add/            # Paste URL intake, capability tester, header configuration
│   ├── browser/        # WebView browser with download listener & navigation
│   ├── settings/       # Concurrency, Wi-Fi only, active transfer limits
│   └── diagnostics/    # Internal storage health, active jobs, troubleshooting
└── ui/
    ├── navigation/     # Jetpack Compose navigation routing
    └── theme/          # Material 3 colors, typography, shapes
```

---

## Building and Continuous Integration

Builds and test suites run continuously via GitHub Actions:
- **Workflow**: `.github/workflows/android-build.yml`
- **Trigger**: Every push or PR to `main`.
- **Output**: Unit test reports, `Azureload-debug-<run_number>.apk`, and signed `Azureload-release-<run_number>.apk`.
