# Third Party Notices and Open Source Inventory

Azureload uses and adapts reputable open source libraries under standardized, permissive open source licenses. This document inventories all direct third-party dependencies, open-source references, and license declarations.

---

## 1. Direct Dependencies & Libraries

### 1. Android Jetpack (Compose, Room, WorkManager, Navigation, Lifecycle)
- **Copyright**: The Android Open Source Project (Google LLC)
- **License**: Apache License, Version 2.0
- **URL**: https://android.googlesource.com/platform/frameworks/support
- **Usage**: Reactive Material 3 UI, persistent SQLite storage, background task scheduling, lifecycle management.

### 2. OkHttp & MockWebServer
- **Copyright**: 2019 Square, Inc.
- **License**: Apache License, Version 2.0
- **URL**: https://github.com/square/okhttp
- **Usage**: HTTP connection pooling, Range header handling, streaming byte transfers, and local mock testing.

### 3. Kotlin & Kotlinx Coroutines
- **Copyright**: 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
- **License**: Apache License, Version 2.0
- **URL**: https://github.com/Kotlin/kotlinx.coroutines
- **Usage**: Structured concurrency, asynchronous background streaming, Mutex synchronization, StateFlows.

### 4. JUnit 4
- **Copyright**: JUnit team
- **License**: Eclipse Public License 1.0 / Common Public License
- **URL**: https://github.com/junit-team/junit4
- **Usage**: JVM unit testing runner.

---

## 2. Open-Source Architectural References & Patterns

During the research phase (Phase 2), well-established download engines and utilities were studied:
- **Aria2** (C++ high-performance multi-source download utility): Byte-range segmenting, non-overlapping chunk allocation, and staging file write mechanics.
- **Android DownloadManager** (AOSP Core Platform): Content-Disposition filename parsing, MIME-type resolution, and notification lifecycle.
- **OkDownload / PRDownloader**: SQLite status tracking and breakpoint resumption patterns.

All Azureload code is natively authored in Kotlin for modern structured concurrency and Room persistence.

---

## 3. Apache License 2.0 Full Text Summary

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at:

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
