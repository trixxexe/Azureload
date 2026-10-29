# Azureload (BOLT Mobile)

A high-performance native Android download manager and multi-connection accelerator inspired by desktop download engines. Built from scratch with Kotlin, Jetpack Compose, Material 3, and Room.

---

## Key Features

1. **Multi-Connection Acceleration**: Slices large files into non-overlapping byte ranges and downloads concurrently with up to 16 configurable connections.
2. **Reliable Capability Detection**: Actively probes byte-range compatibility, MIME types, stable validators (ETags), and compression before chunking.
3. **Single-Stream Fallback**: Automatically and transparently falls back to reliable single-stream downloads when byte ranges are unsupported or file sizes are dynamic.
4. **Resumable & Crash-Proof**: Persistent staging file updates using direct `RandomAccessFile` offset writes. Survives application death, network interruptions, and system reboots.
5. **Three Download Entry Points**:
   - **Built-in WebView Browser**: Web navigation with automated download interception, cookie capture, and filename inference.
   - **Paste URL**: Clipboard detection, connection testing, header customization, and capability inspection.
   - **System Share Sheet**: Direct link sharing from Chrome and any third-party app.
6. **Background Resilience**: Dedicated Foreground Service with ongoing notifications, real-time byte counters, and Pause/Resume/Cancel quick actions.
7. **Integrity Validation**: Zero silent file corruption. Pre-allocation boundary checks, size matching, and optional SHA-256 verification.

---

## Architecture Overview

```
app/src/main/java/com/ritam/azureload/
├── core/
│   ├── model/          # Download & Chunk domain entities, Enums, State machine
│   ├── network/        # OkHttp client, CapabilityDetector, RangeDownloader
│   ├── database/       # Room database, DownloadDao, ChunkDao
│   ├── storage/        # StagingFileManager (RandomAccessFile), File finalizer
│   ├── engine/         # DownloadCoordinator, ChunkScheduler, Downloader engine
│   └── background/     # ForegroundService, NotificationManager, WorkManager
├── feature/
│   ├── dashboard/      # Download queue, active cards, filtering (Active/Queued/Done)
│   ├── detail/         # Real-time chunk visualization, speed & ETA graphs
│   ├── add/            # Paste URL intake, capability tester, header configuration
│   ├── browser/        # WebView browser with download listener & navigation
│   ├── settings/       # Concurrency, Wi-Fi only, buffer sizes
│   └── diagnostics/    # Network logs, error inspection, troubleshooting
└── ui/
    └── theme/          # Material 3 colors, typography, shapes
```

---

## Building and Continuous Integration

All APK builds are handled cleanly via GitHub Actions:
- **Push or PR to `main`**: Automatically runs unit tests, generates debug and release APKs, and uploads installable artifacts.
- **Workflow**: `.github/workflows/android-build.yml`

---

## License

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for licenses of third-party dependencies.
