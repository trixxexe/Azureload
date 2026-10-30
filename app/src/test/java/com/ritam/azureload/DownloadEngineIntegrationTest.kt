package com.ritam.azureload

import com.ritam.azureload.core.database.DownloadRepository
import com.ritam.azureload.core.engine.DownloadJobRunner
import com.ritam.azureload.core.model.ChunkItem
import com.ritam.azureload.core.model.ChunkStatus
import com.ritam.azureload.core.model.DownloadItem
import com.ritam.azureload.core.model.DownloadStatus
import com.ritam.azureload.core.network.CapabilityDetector
import com.ritam.azureload.core.storage.IntegrityVerifier
import com.ritam.azureload.core.storage.StagingFileManager
import com.ritam.azureload.fakes.FakeChunkDao
import com.ritam.azureload.fakes.FakeDownloadDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class DownloadEngineIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var repository: DownloadRepository
    private lateinit var stagingManager: StagingFileManager
    private val client = OkHttpClient()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val fakeDownloadDao = FakeDownloadDao()
        val fakeChunkDao = FakeChunkDao()
        repository = DownloadRepository(fakeDownloadDao, fakeChunkDao)
        val baseDir = tempFolder.newFolder("engine_test")
        stagingManager = StagingFileManager(customBaseDir = baseDir)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun testMultiConnectionRangeDownloadSuccess() = runBlocking {
        val totalSize = 2 * 1024 * 1024 // 2 MB
        val payload = ByteArray(totalSize)
        Random.nextBytes(payload)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range") ?: return MockResponse().setResponseCode(200).setBody(Buffer().write(payload))
                // Format: bytes=start-end
                val clean = range.removePrefix("bytes=").trim()
                val parts = clean.split("-")
                val start = parts[0].toInt()
                val end = if (parts.size > 1 && parts[1].isNotBlank()) parts[1].toInt() else totalSize - 1

                val length = end - start + 1
                val slice = payload.copyOfRange(start, start + length)
                return MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes $start-$end/$totalSize")
                    .setHeader("Content-Length", length.toString())
                    .setHeader("ETag", "\"tag-2026\"")
                    .setBody(Buffer().write(slice))
            }
        }

        val url = server.url("/multi_test.bin").toString()
        val downloadId = repository.insertDownload(
            DownloadItem(
                url = url,
                fileName = "multi_test.bin",
                concurrency = 2,
                destinationPath = "",
                stagingPath = "",
                status = DownloadStatus.QUEUED
            )
        )

        val runner = DownloadJobRunner(
            downloadId = downloadId,
            repository = repository,
            stagingFileManager = stagingManager,
            capabilityDetector = CapabilityDetector(client),
            httpClient = client
        )

        runner.run()

        val finalItem = repository.getDownloadById(downloadId)!!
        assertEquals(DownloadStatus.COMPLETED, finalItem.status)
        assertEquals(totalSize.toLong(), finalItem.downloadedBytes)

        val destinationFile = File(finalItem.destinationPath)
        assertTrue(destinationFile.exists())
        assertEquals(totalSize.toLong(), destinationFile.length())
        assertArrayEquals(payload, destinationFile.readBytes())
    }

    @Test
    fun testServerIgnoresRangeFallsBackToSingleStream() = runBlocking {
        val totalSize = 1024 * 1024 // 1 MB
        val payload = ByteArray(totalSize)
        Random.nextBytes(payload)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range")
                if (range == "bytes=0-0") {
                    // Claim range support during initial probe
                    return MockResponse()
                        .setResponseCode(206)
                        .setHeader("Content-Range", "bytes 0-0/$totalSize")
                        .setHeader("Content-Length", "1")
                        .setBody("x")
                }
                // When chunk request arrives with range, return HTTP 200 OK (server ignores Range)
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", totalSize.toString())
                    .setBody(Buffer().write(payload))
            }
        }

        val url = server.url("/fallback_test.bin").toString()
        val downloadId = repository.insertDownload(
            DownloadItem(
                url = url,
                fileName = "fallback_test.bin",
                concurrency = 2,
                destinationPath = "",
                stagingPath = "",
                status = DownloadStatus.QUEUED
            )
        )

        val runner = DownloadJobRunner(
            downloadId = downloadId,
            repository = repository,
            stagingFileManager = stagingManager,
            capabilityDetector = CapabilityDetector(client),
            httpClient = client
        )

        runner.run()

        val finalItem = repository.getDownloadById(downloadId)!!
        assertEquals(DownloadStatus.COMPLETED, finalItem.status)
        val destinationFile = File(finalItem.destinationPath)
        assertTrue(destinationFile.exists())
        assertEquals(totalSize.toLong(), destinationFile.length())
        assertArrayEquals(payload, destinationFile.readBytes())
    }

    @Test
    fun testShortReadThrowsAndDoesNotMarkCompleted() = runBlocking {
        val totalSize = 100 * 1024 // 100 KB
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range")
                if (range == "bytes=0-0") {
                    // Initial capability probe returns total size = 100KB
                    return MockResponse()
                        .setResponseCode(206)
                        .setHeader("Content-Range", "bytes 0-0/$totalSize")
                        .setHeader("Content-Length", "1")
                        .setBody("x")
                }
                // Download transfer streams only 10 KB and ends (short read)
                val shortPayload = ByteArray(10 * 1024)
                return MockResponse()
                    .setResponseCode(200)
                    .setBody(Buffer().write(shortPayload))
            }
        }

        val url = server.url("/short_read.bin").toString()
        val downloadId = repository.insertDownload(
            DownloadItem(
                url = url,
                fileName = "short_read.bin",
                concurrency = 1,
                destinationPath = "",
                stagingPath = "",
                status = DownloadStatus.QUEUED
            )
        )

        val runner = DownloadJobRunner(
            downloadId = downloadId,
            repository = repository,
            stagingFileManager = stagingManager,
            capabilityDetector = CapabilityDetector(client),
            httpClient = client
        )

        runner.run()

        val item = repository.getDownloadById(downloadId)!!
        assertEquals(DownloadStatus.FAILED, item.status)
        assertTrue(item.errorMessage?.contains("Unexpected EOF") == true)
        assertNotEquals(DownloadStatus.COMPLETED, item.status)
    }

    @Test
    fun testSha256IntegrityFailureMarksDownloadFailed() = runBlocking {
        val payload = "Hello Azureload World".toByteArray()

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", payload.size.toString())
                    .setBody(Buffer().write(payload))
            }
        }

        val invalidExpectedSha = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
        val url = server.url("/sha_fail.bin").toString()
        val downloadId = repository.insertDownload(
            DownloadItem(
                url = url,
                fileName = "sha_fail.bin",
                concurrency = 1,
                expectedSha256 = invalidExpectedSha,
                destinationPath = "",
                stagingPath = "",
                status = DownloadStatus.QUEUED
            )
        )

        val runner = DownloadJobRunner(
            downloadId = downloadId,
            repository = repository,
            stagingFileManager = stagingManager,
            capabilityDetector = CapabilityDetector(client),
            httpClient = client
        )

        runner.run()

        val item = repository.getDownloadById(downloadId)!!
        assertEquals(DownloadStatus.FAILED, item.status)
        assertTrue(item.errorMessage?.contains("SHA-256") == true)
    }

    @Test
    fun testETagMismatchRestartsDownloadCleanly() = runBlocking {
        val newPayload = "Brand New Content After Change".toByteArray()

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("ETag", "\"v2-new-etag\"")
                    .setHeader("Content-Length", newPayload.size.toString())
                    .setBody(Buffer().write(newPayload))
            }
        }

        val url = server.url("/etag_change.bin").toString()
        val stagingFile = stagingManager.createStagingFile(300L)
        stagingFile.writeBytes("stale old bytes".toByteArray())

        val downloadId = repository.insertDownload(
            DownloadItem(
                id = 300L,
                url = url,
                fileName = "etag_change.bin",
                concurrency = 1,
                eTag = "\"v1-old-etag\"",
                downloadedBytes = 15L,
                stagingPath = stagingFile.absolutePath,
                destinationPath = "",
                status = DownloadStatus.PAUSED
            )
        )

        val runner = DownloadJobRunner(
            downloadId = downloadId,
            repository = repository,
            stagingFileManager = stagingManager,
            capabilityDetector = CapabilityDetector(client),
            httpClient = client
        )

        runner.run()

        val item = repository.getDownloadById(downloadId)!!
        assertEquals(DownloadStatus.COMPLETED, item.status)
        assertEquals("\"v2-new-etag\"", item.eTag)
        val file = File(item.destinationPath)
        assertArrayEquals(newPayload, file.readBytes())
    }

    @Test
    fun testSingleStreamTransientRetrySucceeds() = runBlocking {
        val payload = "Transient Error Recovered Content".toByteArray()
        val attempts = AtomicInteger(0)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range")
                if (range == "bytes=0-0") {
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Length", payload.size.toString())
                        .setBody(Buffer().write(payload))
                }
                val count = attempts.incrementAndGet()
                if (count == 1) {
                    // First attempt returns 503 transient failure
                    return MockResponse()
                        .setResponseCode(503)
                        .setHeader("Retry-After", "1")
                        .setBody("Service Temporarily Overloaded")
                }
                // Second attempt succeeds
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", payload.size.toString())
                    .setBody(Buffer().write(payload))
            }
        }

        val url = server.url("/retry_stream.bin").toString()
        val downloadId = repository.insertDownload(
            DownloadItem(
                url = url,
                fileName = "retry_stream.bin",
                concurrency = 1,
                destinationPath = "",
                stagingPath = "",
                status = DownloadStatus.QUEUED
            )
        )

        val runner = DownloadJobRunner(
            downloadId = downloadId,
            repository = repository,
            stagingFileManager = stagingManager,
            capabilityDetector = CapabilityDetector(client),
            httpClient = client
        )

        runner.run()

        val finalItem = repository.getDownloadById(downloadId)!!
        assertEquals(DownloadStatus.COMPLETED, finalItem.status)
        val destinationFile = File(finalItem.destinationPath)
        assertTrue(destinationFile.exists())
        assertArrayEquals(payload, destinationFile.readBytes())
        assertTrue("Expected at least 2 requests (probe + fail + retry)", attempts.get() >= 2)
    }

    @Test
    fun testDynamicStreamUnknownContentLength() = runBlocking {
        val payload = "Dynamic Stream Payload Without Content-Length".toByteArray()

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                // Chunked transfer without Content-Length
                return MockResponse()
                    .setResponseCode(200)
                    .setChunkedBody(Buffer().write(payload), 16)
            }
        }

        val url = server.url("/dynamic.bin").toString()
        val downloadId = repository.insertDownload(
            DownloadItem(
                url = url,
                fileName = "dynamic.bin",
                concurrency = 1,
                destinationPath = "",
                stagingPath = "",
                status = DownloadStatus.QUEUED
            )
        )

        val runner = DownloadJobRunner(
            downloadId = downloadId,
            repository = repository,
            stagingFileManager = stagingManager,
            capabilityDetector = CapabilityDetector(client),
            httpClient = client
        )

        runner.run()

        val item = repository.getDownloadById(downloadId)!!
        assertEquals(DownloadStatus.COMPLETED, item.status)
        org.junit.Assert.assertFalse(item.isParallelTransfer)
        assertEquals("Single Stream", item.transferModeTitle)
        assertEquals(1, item.effectiveConnectionCount())
        val file = File(item.destinationPath)
        assertTrue(file.exists())
        assertEquals(payload.size.toLong(), file.length())
        assertArrayEquals(payload, file.readBytes())
    }

    @Test
    fun testFinalizeFileCollisionAppendsCounter() {
        val destDir = stagingManager.getResolvedDestinationDirectory()
        val existingFile = File(destDir, "collision.bin")
        existingFile.writeText("existing version")

        val stagingFile = stagingManager.createStagingFile(999L)
        stagingFile.writeText("new version")

        val finalFile = stagingManager.finalizeDownload(stagingFile, "collision.bin")

        assertEquals("collision(1).bin", finalFile.name)
        assertTrue(existingFile.exists())
        assertEquals("existing version", existingFile.readText())
        assertTrue(finalFile.exists())
        assertEquals("new version", finalFile.readText())
    }

    @Test
    fun testRedirectUpdatesEffectiveUrl() = runBlocking {
        val payload = "Redirected Content".toByteArray()

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path == "/start_redirect") {
                    return MockResponse()
                        .setResponseCode(302)
                        .setHeader("Location", server.url("/cdn_target.bin").toString())
                }
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", payload.size.toString())
                    .setBody(Buffer().write(payload))
            }
        }

        val initialUrl = server.url("/start_redirect").toString()
        val downloadId = repository.insertDownload(
            DownloadItem(
                url = initialUrl,
                originalUrl = initialUrl,
                fileName = "redirect_target.bin",
                concurrency = 1,
                destinationPath = "",
                stagingPath = "",
                status = DownloadStatus.QUEUED
            )
        )

        val runner = DownloadJobRunner(
            downloadId = downloadId,
            repository = repository,
            stagingFileManager = stagingManager,
            capabilityDetector = CapabilityDetector(client),
            httpClient = client
        )

        runner.run()

        val item = repository.getDownloadById(downloadId)!!
        assertEquals(DownloadStatus.COMPLETED, item.status)
        assertEquals(initialUrl, item.originalUrl)
        assertTrue(item.url.contains("/cdn_target.bin"))
        val file = File(item.destinationPath)
        assertArrayEquals(payload, file.readBytes())
    }

    @Test
    fun testGenuineFourAndEightConnectionRangeDownloads() = runBlocking {
        for (concurrency in listOf(4, 8)) {
            val totalSize = concurrency * 1024 * 1024 // 4MB or 8MB
            val payload = ByteArray(totalSize)
            Random.nextBytes(payload)

            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val range = request.getHeader("Range") ?: return MockResponse().setResponseCode(200).setBody(Buffer().write(payload))
                    val clean = range.removePrefix("bytes=").trim()
                    val parts = clean.split("-")
                    val start = parts[0].toInt()
                    val end = if (parts.size > 1 && parts[1].isNotBlank()) parts[1].toInt() else totalSize - 1

                    val length = end - start + 1
                    val slice = payload.copyOfRange(start, start + length)
                    return MockResponse()
                        .setResponseCode(206)
                        .setHeader("Content-Range", "bytes $start-$end/$totalSize")
                        .setHeader("Content-Length", length.toString())
                        .setBody(Buffer().write(slice))
                }
            }

            val url = server.url("/genuine_${concurrency}.bin").toString()
            val downloadId = repository.insertDownload(
                DownloadItem(
                    url = url,
                    fileName = "genuine_${concurrency}.bin",
                    concurrency = concurrency,
                    destinationPath = "",
                    stagingPath = "",
                    status = DownloadStatus.QUEUED
                )
            )

            val runner = DownloadJobRunner(
                downloadId = downloadId,
                repository = repository,
                stagingFileManager = stagingManager,
                capabilityDetector = CapabilityDetector(client),
                httpClient = client
            )

            runner.run()

            val finalItem = repository.getDownloadById(downloadId)!!
            assertEquals(DownloadStatus.COMPLETED, finalItem.status)
            assertTrue(finalItem.supportsRanges)
            assertTrue(finalItem.isParallelTransfer)
            assertEquals("Multi-Connection Parallel", finalItem.transferModeTitle)

            val chunks = repository.getChunksForDownload(downloadId)
            assertEquals(concurrency, chunks.size)
            assertEquals(concurrency, finalItem.effectiveConnectionCount(chunks))

            val destinationFile = File(finalItem.destinationPath)
            assertTrue(destinationFile.exists())
            assertEquals(totalSize.toLong(), destinationFile.length())
            assertArrayEquals(payload, destinationFile.readBytes())
        }
    }

    @Test
    fun testUnsupportedByteRangeServerForcesSingleStreamAndReportsAccurately() = runBlocking {
        val totalSize = 2 * 1024 * 1024 // 2 MB
        val payload = ByteArray(totalSize)
        Random.nextBytes(payload)

        // Server refuses ranges (like HEXAQA): returns 200 OK regardless of Range header
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Accept-Ranges", "none")
                    .setHeader("Content-Length", totalSize.toString())
                    .setBody(Buffer().write(payload))
            }
        }

        val url = server.url("/hexaqa_sim.bin").toString()
        val downloadId = repository.insertDownload(
            DownloadItem(
                url = url,
                fileName = "hexaqa_sim.bin",
                concurrency = 10, // User requested 10 threads
                destinationPath = "",
                stagingPath = "",
                status = DownloadStatus.QUEUED
            )
        )

        val runner = DownloadJobRunner(
            downloadId = downloadId,
            repository = repository,
            stagingFileManager = stagingManager,
            capabilityDetector = CapabilityDetector(client),
            httpClient = client
        )

        runner.run()

        val finalItem = repository.getDownloadById(downloadId)!!
        assertEquals(DownloadStatus.COMPLETED, finalItem.status)
        // Range support must be detected as false
        assertEquals(false, finalItem.supportsRanges)
        // Must report Single Stream mode, not parallel
        assertEquals(false, finalItem.isParallelTransfer)
        assertEquals("Single Stream", finalItem.transferModeTitle)
        // Effective connection count must be 1, not 10
        assertEquals(1, finalItem.effectiveConnectionCount())

        // Chunks table must be empty (no parallel chunks created)
        val chunks = repository.getChunksForDownload(downloadId)
        assertTrue(chunks.isEmpty())

        val destinationFile = File(finalItem.destinationPath)
        assertTrue(destinationFile.exists())
        assertEquals(totalSize.toLong(), destinationFile.length())
        assertArrayEquals(payload, destinationFile.readBytes())
    }

    @Test
    fun testMultiConnectionPauseAndResumeReconstructsFile() = runBlocking {
        val totalSize = 4 * 1024 * 1024 // 4 MB, 4 chunks of 1MB each
        val payload = ByteArray(totalSize)
        Random.nextBytes(payload)

        val resumeRangesRequested = Collections.synchronizedList(mutableListOf<String>())
        val isFirstRun = AtomicBoolean(true)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range") ?: return MockResponse().setResponseCode(200).setBody(Buffer().write(payload))
                if (!isFirstRun.get()) {
                    resumeRangesRequested.add(range)
                }
                val clean = range.removePrefix("bytes=").trim()
                val parts = clean.split("-")
                val start = parts[0].toInt()
                val end = if (parts.size > 1 && parts[1].isNotBlank()) parts[1].toInt() else totalSize - 1

                val length = end - start + 1
                val slice = payload.copyOfRange(start, start + length)

                // In first run, introduce a delay on chunk downloads so cancellation catches it in mid-flight
                if (isFirstRun.get() && length > 100) {
                    Thread.sleep(60)
                }

                return MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes $start-$end/$totalSize")
                    .setHeader("Content-Length", length.toString())
                    .setBody(Buffer().write(slice))
            }
        }

        val url = server.url("/pause_resume.bin").toString()
        val downloadId = repository.insertDownload(
            DownloadItem(
                url = url,
                fileName = "pause_resume.bin",
                concurrency = 4,
                destinationPath = "",
                stagingPath = "",
                status = DownloadStatus.QUEUED
            )
        )

        // Phase 1: Launch runner in coroutine job and cancel midway (simulating user tapping Pause)
        val runner1 = DownloadJobRunner(
            downloadId = downloadId,
            repository = repository,
            stagingFileManager = stagingManager,
            capabilityDetector = CapabilityDetector(client),
            httpClient = client
        )

        val job = launch(Dispatchers.IO) {
            runner1.run()
        }

        // Wait until chunks exist in database and at least one chunk has begun downloading
        while (repository.getChunksForDownload(downloadId).isEmpty()) {
            delay(10)
        }
        delay(80)
        job.cancelAndJoin()

        // Verify paused state
        val pausedItem = repository.getDownloadById(downloadId)!!
        assertEquals(DownloadStatus.PAUSED, pausedItem.status)
        val pausedChunks = repository.getChunksForDownload(downloadId)
        assertEquals(4, pausedChunks.size)
        // Incomplete chunks must be PENDING (not DOWNLOADING) while paused
        assertTrue("Incomplete chunks must be PENDING while paused",
            pausedChunks.all { it.status == ChunkStatus.PENDING || it.status == ChunkStatus.COMPLETED })

        // Phase 2: Resume transfer with second runner
        isFirstRun.set(false)
        val runner2 = DownloadJobRunner(
            downloadId = downloadId,
            repository = repository,
            stagingFileManager = stagingManager,
            capabilityDetector = CapabilityDetector(client),
            httpClient = client
        )
        runner2.run()

        val finalItem = repository.getDownloadById(downloadId)!!
        assertEquals(DownloadStatus.COMPLETED, finalItem.status)
        val finalChunks = repository.getChunksForDownload(downloadId)
        assertEquals(4, finalChunks.size)
        assertTrue(finalChunks.all { it.status == ChunkStatus.COMPLETED })

        val destinationFile = File(finalItem.destinationPath)
        assertTrue(destinationFile.exists())
        assertEquals(totalSize.toLong(), destinationFile.length())
        assertArrayEquals(payload, destinationFile.readBytes())
    }

    @Test
    fun testProcessDeathRecoveryRestartsFromStaging() = runBlocking {
        val totalSize = 4 * 1024 * 1024 // 4 MB, 4 chunks of 1MB each
        val payload = ByteArray(totalSize)
        Random.nextBytes(payload)

        val resumeRangesRequested = Collections.synchronizedList(mutableListOf<String>())

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range") ?: return MockResponse().setResponseCode(200).setBody(Buffer().write(payload))
                resumeRangesRequested.add(range)
                val clean = range.removePrefix("bytes=").trim()
                val parts = clean.split("-")
                val start = parts[0].toInt()
                val end = if (parts.size > 1 && parts[1].isNotBlank()) parts[1].toInt() else totalSize - 1

                val length = end - start + 1
                val slice = payload.copyOfRange(start, start + length)
                return MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes $start-$end/$totalSize")
                    .setHeader("Content-Length", length.toString())
                    .setBody(Buffer().write(slice))
            }
        }

        val url = server.url("/recovery_test.bin").toString()
        val stagingFile = stagingManager.createStagingFile(777L)

        // Pre-allocate staging file and write:
        // Chunk 0: 100% written (bytes 0 until 1MB)
        // Chunk 1: 50% written (512 KB written, from 1MB until 1MB + 512KB)
        // Chunk 2 & 3: 0 bytes written
        stagingManager.allocateStagingFile(stagingFile, totalSize.toLong())
        java.io.RandomAccessFile(stagingFile, "rw").use { raf ->
            raf.seek(0L)
            raf.write(payload, 0, 1024 * 1024)
            raf.seek(1024 * 1024L)
            raf.write(payload, 1024 * 1024, 512 * 1024)
        }

        val downloadId = repository.insertDownload(
            DownloadItem(
                id = 777L,
                url = url,
                fileName = "recovery_test.bin",
                concurrency = 4,
                totalBytes = totalSize.toLong(),
                stagingPath = stagingFile.absolutePath,
                destinationPath = "",
                supportsRanges = true,
                status = DownloadStatus.DOWNLOADING // was interrupted mid-flight by process death
            )
        )

        // Seed chunks in database matching staging file state
        val chunk0 = ChunkItem(downloadId = downloadId, chunkIndex = 0, startOffset = 0, endOffset = 1024 * 1024L - 1, downloadedBytes = 1024 * 1024L, status = ChunkStatus.COMPLETED)
        val chunk1 = ChunkItem(downloadId = downloadId, chunkIndex = 1, startOffset = 1024 * 1024L, endOffset = 2 * 1024 * 1024L - 1, downloadedBytes = 512 * 1024L, status = ChunkStatus.DOWNLOADING)
        val chunk2 = ChunkItem(downloadId = downloadId, chunkIndex = 2, startOffset = 2 * 1024 * 1024L, endOffset = 3 * 1024 * 1024L - 1, downloadedBytes = 0L, status = ChunkStatus.DOWNLOADING)
        val chunk3 = ChunkItem(downloadId = downloadId, chunkIndex = 3, startOffset = 3 * 1024 * 1024L, endOffset = 4 * 1024 * 1024L - 1, downloadedBytes = 0L, status = ChunkStatus.DOWNLOADING)
        repository.saveChunks(downloadId, listOf(chunk0, chunk1, chunk2, chunk3))

        // Step 1: Process death recovery transitions active DOWNLOADING items to PAUSED
        val pending = repository.getActiveOrPendingDownloads()
        for (item in pending) {
            if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.DETECTING) {
                repository.updateDownloadStatus(item.id, DownloadStatus.PAUSED)
            }
        }

        val recoveredItem = repository.getDownloadById(downloadId)!!
        assertEquals(DownloadStatus.PAUSED, recoveredItem.status)

        // Step 2: Resume download runner
        val runner = DownloadJobRunner(
            downloadId = downloadId,
            repository = repository,
            stagingFileManager = stagingManager,
            capabilityDetector = CapabilityDetector(client),
            httpClient = client
        )
        runner.run()

        val finalItem = repository.getDownloadById(downloadId)!!
        assertEquals(DownloadStatus.COMPLETED, finalItem.status)

        // Verify that completed chunk 0 was NEVER requested from the server
        assertTrue("Chunk 0 range must not be re-requested", resumeRangesRequested.none { it == "bytes=0-${1024 * 1024L - 1}" })

        // Verify that chunk 1 resumed from offset 1024*1024 + 512*1024
        val expectedChunk1ResumeStart = 1024 * 1024L + 512 * 1024L
        val expectedChunk1ResumeEnd = 2 * 1024 * 1024L - 1
        assertTrue("Chunk 1 must resume from byte offset $expectedChunk1ResumeStart",
            resumeRangesRequested.any { it == "bytes=$expectedChunk1ResumeStart-$expectedChunk1ResumeEnd" })

        val destinationFile = File(finalItem.destinationPath)
        assertTrue(destinationFile.exists())
        assertEquals(totalSize.toLong(), destinationFile.length())
        assertArrayEquals(payload, destinationFile.readBytes())
    }

    @Test
    fun testSmallFileSub1MBWithRangeSupportUsesSingleStream() = runBlocking {
        val totalSize = 500 * 1024 // 500 KB < 1MB
        val payload = ByteArray(totalSize)
        Random.nextBytes(payload)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val range = request.getHeader("Range")
                if (range != null && range == "bytes=0-0") {
                    return MockResponse()
                        .setResponseCode(206)
                        .setHeader("Content-Range", "bytes 0-0/$totalSize")
                        .setHeader("Accept-Ranges", "bytes")
                        .setHeader("Content-Length", "1")
                        .setBody(Buffer().write(payload.copyOfRange(0, 1)))
                }
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", totalSize.toString())
                    .setHeader("Accept-Ranges", "bytes")
                    .setBody(Buffer().write(payload))
            }
        }

        val url = server.url("/small_range.bin").toString()
        val downloadId = repository.insertDownload(
            DownloadItem(
                url = url,
                fileName = "small_range.bin",
                concurrency = 4,
                destinationPath = "",
                stagingPath = "",
                status = DownloadStatus.QUEUED
            )
        )

        val runner = DownloadJobRunner(
            downloadId = downloadId,
            repository = repository,
            stagingFileManager = stagingManager,
            capabilityDetector = CapabilityDetector(client),
            httpClient = client
        )
        runner.run()

        val finalItem = repository.getDownloadById(downloadId)!!
        assertEquals(DownloadStatus.COMPLETED, finalItem.status)
        assertTrue(finalItem.supportsRanges)
        // File < 1MB must not be treated as parallel transfer
        org.junit.Assert.assertFalse(finalItem.isParallelTransfer)
        assertEquals("Single Stream", finalItem.transferModeTitle)
        assertEquals(1, finalItem.effectiveConnectionCount())

        // Chunks table must be empty
        val chunks = repository.getChunksForDownload(downloadId)
        assertTrue(chunks.isEmpty())

        val destinationFile = File(finalItem.destinationPath)
        assertTrue(destinationFile.exists())
        assertArrayEquals(payload, destinationFile.readBytes())
    }
}
