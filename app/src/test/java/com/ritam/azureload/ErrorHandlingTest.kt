package com.ritam.azureload

import com.ritam.azureload.core.network.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class ErrorHandlingTest {

    @Test
    fun testDnsErrorClassification() {
        val dnsException = UnknownHostException("Unable to resolve host \"files.example.com\": No address associated with hostname")
        val classified = ErrorClassifier.classify(dnsException)

        assertEquals(ErrorCategory.DNS, classified.category)
        assertEquals("DNS Lookup Failed", classified.title)
        assertTrue(classified.isRetryable)
        assertTrue(classified.recoveryGuidance.contains("internet connection", ignoreCase = true))
        assertTrue(classified.toDisplayString().contains("DNS Lookup Failed"))
    }

    @Test
    fun testSslHandshakeErrorClassification() {
        val sslException = SSLHandshakeException("CertPathValidatorException: Trust anchor for certification path not found")
        val classified = ErrorClassifier.classify(sslException)

        assertEquals(ErrorCategory.SSL, classified.category)
        assertTrue(classified.title.contains("SSL"))
        assertTrue(classified.recoveryGuidance.contains("date", ignoreCase = true) || classified.recoveryGuidance.contains("VPN", ignoreCase = true))
    }

    @Test
    fun testSocketTimeoutClassification() {
        val timeoutException = SocketTimeoutException("Read timed out after 30000ms")
        val classified = ErrorClassifier.classify(timeoutException)

        assertEquals(ErrorCategory.TIMEOUT, classified.category)
        assertTrue(classified.title.contains("Timed Out"))
        assertTrue(classified.isRetryable)

        val connectException = ConnectException("Failed to connect to /192.168.1.1:443: Connection refused")
        val classifiedConnect = ErrorClassifier.classify(connectException)
        assertEquals(ErrorCategory.TIMEOUT, classifiedConnect.category)
        assertTrue(classifiedConnect.isRetryable)
    }

    @Test
    fun testHttpClientErrorClassification() {
        // HTTP 404
        val notFound = FatalDownloadException("Server returned HTTP 404: Not Found", statusCode = 404)
        val classified404 = ErrorClassifier.classify(notFound)
        assertEquals(ErrorCategory.HTTP_CLIENT, classified404.category)
        assertEquals(404, classified404.httpStatusCode)
        assertFalse(classified404.isRetryable)
        assertTrue(classified404.recoveryGuidance.contains("expired", ignoreCase = true) || classified404.recoveryGuidance.contains("link", ignoreCase = true))

        // HTTP 401 & 403
        val authError = FatalDownloadException("Server returned HTTP 403: Forbidden", statusCode = 403)
        val classified403 = ErrorClassifier.classify(authError)
        assertEquals(ErrorCategory.HTTP_CLIENT, classified403.category)
        assertEquals(403, classified403.httpStatusCode)
        assertFalse(classified403.isRetryable)
        assertTrue(classified403.recoveryGuidance.contains("cookies", ignoreCase = true) || classified403.recoveryGuidance.contains("authentication", ignoreCase = true))

        // HTTP 429
        val rateLimited = TransientDownloadException("Server returned HTTP 429 Too Many Requests", statusCode = 429, retryAfterSeconds = 60)
        val classified429 = ErrorClassifier.classify(rateLimited)
        assertEquals(ErrorCategory.HTTP_CLIENT, classified429.category)
        assertEquals(429, classified429.httpStatusCode)
        assertTrue(classified429.isRetryable)
    }

    @Test
    fun testHttpServerErrorClassification() {
        val server503 = TransientDownloadException("Server returned HTTP 503: Service Unavailable", statusCode = 503)
        val classified503 = ErrorClassifier.classify(server503)
        assertEquals(ErrorCategory.HTTP_SERVER, classified503.category)
        assertEquals(503, classified503.httpStatusCode)
        assertTrue(classified503.isRetryable)

        val server500 = TransientDownloadException("Server returned HTTP 500: Internal Server Error", statusCode = 500)
        val classified500 = ErrorClassifier.classify(server500)
        assertEquals(ErrorCategory.HTTP_SERVER, classified500.category)
        assertTrue(classified500.isRetryable)
    }

    @Test
    fun testStorageFullAndPermissionClassification() {
        val storageException = StorageFullException("Insufficient storage space: requires 500000000 bytes")
        val classifiedStorage = ErrorClassifier.classify(storageException)
        assertEquals(ErrorCategory.STORAGE_FULL, classifiedStorage.category)
        assertTrue(classifiedStorage.recoveryGuidance.contains("Free up storage", ignoreCase = true))

        val enospcException = IOException("write failed: ENOSPC (No space left on device)")
        val classifiedEnospc = ErrorClassifier.classify(enospcException)
        assertEquals(ErrorCategory.STORAGE_FULL, classifiedEnospc.category)

        val permissionException = StoragePermissionDeniedException("Access denied to destination folder")
        val classifiedPerm = ErrorClassifier.classify(permissionException)
        assertEquals(ErrorCategory.PERMISSION_DENIED, classifiedPerm.category)
        assertTrue(classifiedPerm.recoveryGuidance.contains("permission", ignoreCase = true))
    }

    @Test
    fun testIntegrityChecksumClassification() {
        val integrityEx = IntegrityVerificationException("Integrity check failed: SHA-256 checksum mismatch")
        val classified = ErrorClassifier.classify(integrityEx)
        assertEquals(ErrorCategory.INTEGRITY_MISMATCH, classified.category)
        assertTrue(classified.isRetryable)
        assertTrue(classified.recoveryGuidance.contains("Redownload", ignoreCase = true))
    }

    @Test
    fun testParseOrClassifyFromStoredDbString() {
        val rawDns = "[DNS Lookup Failed] Could not resolve server domain address. — Verify your connection."
        val parsedDns = ErrorClassifier.parseOrClassify(rawDns)
        assertEquals(ErrorCategory.DNS, parsedDns.category)

        val raw404 = "Server returned HTTP 404: Not Found"
        val parsed404 = ErrorClassifier.parseOrClassify(raw404)
        assertEquals(ErrorCategory.HTTP_CLIENT, parsed404.category)
        assertEquals(404, parsed404.httpStatusCode)

        val rawStorage = "Disk Storage Full: No space left on device"
        val parsedStorage = ErrorClassifier.parseOrClassify(rawStorage)
        assertEquals(ErrorCategory.STORAGE_FULL, parsedStorage.category)
    }
}
