package com.ritam.azureload

import com.ritam.azureload.core.database.DownloadRepository
import com.ritam.azureload.core.engine.DownloadJobRunner
import com.ritam.azureload.core.model.DownloadItem
import com.ritam.azureload.core.model.DownloadStatus
import com.ritam.azureload.core.network.CapabilityDetector
import com.ritam.azureload.core.storage.IntegrityVerifier
import com.ritam.azureload.core.storage.StagingFileManager
import com.ritam.azureload.fakes.FakeChunkDao
import com.ritam.azureload.fakes.FakeDownloadDao
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
                // Send only 10 KB then close connection prematurely
                val shortPayload = ByteArray(10 * 1024)
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Length", totalSize.toString())
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
}
