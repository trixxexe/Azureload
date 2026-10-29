package com.ritam.azureload

import com.ritam.azureload.core.network.CapabilityDetector
import com.ritam.azureload.core.network.FatalDownloadException
import com.ritam.azureload.core.network.TransientDownloadException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class CapabilityDetectorServerTest {

    private lateinit var server: MockWebServer
    private lateinit var detector: CapabilityDetector

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        detector = CapabilityDetector()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun testDetectsRangeSupportedOn206() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 0-0/1048576")
                .setHeader("ETag", "\"test-etag-123\"")
                .setHeader("Last-Modified", "Wed, 21 Oct 2025 07:28:00 GMT")
                .setHeader("Content-Type", "application/octet-stream")
                .setBody("x")
        )

        val capability = detector.detect(server.url("/testfile.bin").toString())

        assertTrue(capability.supportsRange)
        assertEquals(1048576L, capability.contentLength)
        assertEquals("\"test-etag-123\"", capability.eTag)
        assertEquals("Wed, 21 Oct 2025 07:28:00 GMT", capability.lastModified)
        assertEquals(206, capability.responseCode)
    }

    @Test
    fun testDetectsSingleStreamWhenServerReturns200() = runBlocking {
        val payload = "dummy content"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Accept-Ranges", "bytes")
                .setHeader("Content-Type", "application/zip")
                .setBody(payload)
        )

        val capability = detector.detect(server.url("/archive.zip").toString())

        assertFalse(capability.supportsRange)
        assertEquals(payload.length.toLong(), capability.contentLength)
        assertEquals(200, capability.responseCode)
    }

    @Test
    fun testThrowsTransientExceptionOn429WithRetryAfter() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "12")
                .setBody("Too Many Requests")
        )

        try {
            detector.detect(server.url("/rate-limited").toString())
            fail("Expected TransientDownloadException")
        } catch (e: TransientDownloadException) {
            assertEquals(429, e.statusCode)
            assertEquals(12L, e.retryAfterSeconds)
        }
    }

    @Test
    fun testThrowsTransientExceptionOn503() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setHeader("Retry-After", "45")
                .setBody("Service Temporarily Unavailable")
        )

        try {
            detector.detect(server.url("/unavailable").toString())
            fail("Expected TransientDownloadException")
        } catch (e: TransientDownloadException) {
            assertEquals(503, e.statusCode)
            assertEquals(45L, e.retryAfterSeconds)
        }
    }

    @Test
    fun testParsesHttpDateRetryAfter() = runBlocking {
        // Date 60 seconds into the future
        val futureDate = Date(System.currentTimeMillis() + 60_000L)
        val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }
        val dateHeader = sdf.format(futureDate)

        server.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setHeader("Retry-After", dateHeader)
                .setBody("Service Unavailable with HTTP date")
        )

        try {
            detector.detect(server.url("/date-retry").toString())
            fail("Expected TransientDownloadException")
        } catch (e: TransientDownloadException) {
            assertEquals(503, e.statusCode)
            assertTrue("Expected retryAfterSeconds between 40 and 70, got ${e.retryAfterSeconds}", (e.retryAfterSeconds ?: 0L) in 40L..70L)
        }
    }

    @Test
    fun testZeroByteFileWith416RangeNotSatisfiable() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(416)
                .setHeader("Content-Range", "bytes */0")
                .setHeader("Content-Type", "text/plain")
        )

        val capability = detector.detect(server.url("/empty.txt").toString())
        assertFalse(capability.supportsRange)
        assertEquals(0L, capability.contentLength)
    }

    @Test
    fun testThrowsFatalExceptionOn404() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("Not Found")
        )

        try {
            detector.detect(server.url("/missing").toString())
            fail("Expected FatalDownloadException")
        } catch (e: FatalDownloadException) {
            assertEquals(404, e.statusCode)
        }
    }

    @Test
    fun testGzipStreamDisablesRangeSupport() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 0-0/1000")
                .setHeader("Content-Encoding", "gzip")
                .setBody("x")
        )

        val capability = detector.detect(server.url("/compressed").toString())
        assertFalse(capability.supportsRange)
    }
}
