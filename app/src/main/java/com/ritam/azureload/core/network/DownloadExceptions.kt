package com.ritam.azureload.core.network

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

enum class ErrorCategory {
    DNS,
    SSL,
    TIMEOUT,
    HTTP_CLIENT,
    HTTP_SERVER,
    STORAGE_FULL,
    DISK_IO,
    PERMISSION_DENIED,
    INTEGRITY_MISMATCH,
    NETWORK_OFFLINE,
    UNKNOWN
}

data class ClassifiedDownloadError(
    val category: ErrorCategory,
    val title: String,
    val userMessage: String,
    val recoveryGuidance: String,
    val isRetryable: Boolean,
    val httpStatusCode: Int? = null,
    val rawMessage: String? = null
) {
    fun toDisplayString(): String {
        val statusPart = if (httpStatusCode != null) " [HTTP $httpStatusCode]" else ""
        val detailPart = if (!rawMessage.isNullOrBlank()) " ($rawMessage)" else ""
        return "[$title$statusPart] $userMessage$detailPart — $recoveryGuidance"
    }
}

open class DownloadException(
    message: String,
    val category: ErrorCategory = ErrorCategory.UNKNOWN,
    val statusCode: Int? = null,
    cause: Throwable? = null
) : IOException(message, cause)

class FatalDownloadException(
    message: String,
    statusCode: Int? = null,
    cause: Throwable? = null
) : DownloadException(message, ErrorCategory.HTTP_CLIENT, statusCode, cause)

class TransientDownloadException(
    message: String,
    statusCode: Int? = null,
    val retryAfterSeconds: Long? = null,
    cause: Throwable? = null
) : DownloadException(message, ErrorCategory.HTTP_SERVER, statusCode, cause)

class RangeNotSupportedException(
    message: String
) : IOException(message)

class StorageFullException(
    message: String,
    cause: Throwable? = null
) : DownloadException(message, ErrorCategory.STORAGE_FULL, null, cause)

class DiskIoException(
    message: String,
    cause: Throwable? = null
) : DownloadException(message, ErrorCategory.DISK_IO, null, cause)

class StoragePermissionDeniedException(
    message: String,
    cause: Throwable? = null
) : DownloadException(message, ErrorCategory.PERMISSION_DENIED, null, cause)

class IntegrityVerificationException(
    message: String,
    cause: Throwable? = null
) : DownloadException(message, ErrorCategory.INTEGRITY_MISMATCH, null, cause)

class NetworkOfflineException(
    message: String = "No internet connection detected",
    cause: Throwable? = null
) : DownloadException(message, ErrorCategory.NETWORK_OFFLINE, null, cause)

object ErrorClassifier {

    fun classify(throwable: Throwable): ClassifiedDownloadError {
        val root = findRootCause(throwable)
        val raw = root.message ?: throwable.message ?: "Unknown error"

        val offlineEx = findInChain<NetworkOfflineException>(throwable)
        val dnsEx = findInChain<UnknownHostException>(throwable)
        val sslEx = findInChain<SSLException>(throwable) ?: findInChain<CertificateException>(throwable)
        val permEx = findInChain<StoragePermissionDeniedException>(throwable) ?: findInChain<SecurityException>(throwable)
        val storageFullEx = findInChain<StorageFullException>(throwable)
        val diskIoEx = findInChain<DiskIoException>(throwable)
        val integrityEx = findInChain<IntegrityVerificationException>(throwable)
        val timeoutEx = findInChain<SocketTimeoutException>(throwable)
            ?: findInChain<ConnectException>(throwable)
            ?: findInChain<NoRouteToHostException>(throwable)
            ?: findInChain<PortUnreachableException>(throwable)
        val dlEx = findInChain<DownloadException>(throwable)

        // 1. Network Offline
        if (offlineEx != null || raw.contains("Waiting for Wi-Fi", ignoreCase = true) || raw.contains("No internet connection", ignoreCase = true)) {
            return ClassifiedDownloadError(
                category = ErrorCategory.NETWORK_OFFLINE,
                title = "Network Offline",
                userMessage = "Transfer paused waiting for required network connectivity.",
                recoveryGuidance = "Connect to Wi-Fi or active mobile network to continue downloading.",
                isRetryable = true,
                rawMessage = raw
            )
        }

        // 2. DNS Resolution
        if (dnsEx != null || raw.contains("Unable to resolve host", ignoreCase = true) || raw.contains("No address associated with hostname", ignoreCase = true)) {
            return ClassifiedDownloadError(
                category = ErrorCategory.DNS,
                title = "DNS Lookup Failed",
                userMessage = "Could not resolve server domain address.",
                recoveryGuidance = "Verify your internet connection and check if the download URL domain is spelled correctly.",
                isRetryable = true,
                rawMessage = raw
            )
        }

        // 3. SSL / TLS Security Handshake
        if (sslEx != null || raw.contains("SSL", ignoreCase = true) || raw.contains("TLS", ignoreCase = true) || raw.contains("CertPathValidatorException", ignoreCase = true)) {
            return ClassifiedDownloadError(
                category = ErrorCategory.SSL,
                title = "SSL / TLS Handshake Error",
                userMessage = "Secure cryptographic connection could not be established with the server.",
                recoveryGuidance = "Ensure device date and time are accurate, disable conflicting VPN/proxy apps, or verify server certificates.",
                isRetryable = true,
                rawMessage = raw
            )
        }

        // 4. Permission Denied (Checked BEFORE storage full so permission errors mentioning "storage" are not misclassified)
        if (permEx != null || raw.contains("EACCES", ignoreCase = true) || raw.contains("Permission denied", ignoreCase = true) || raw.contains("Access denied", ignoreCase = true)) {
            return ClassifiedDownloadError(
                category = ErrorCategory.PERMISSION_DENIED,
                title = "Storage Permission Denied",
                userMessage = "Azureload lacks permission to write to the requested directory.",
                recoveryGuidance = "Grant storage access permissions in system settings or select internal app storage.",
                isRetryable = true,
                rawMessage = raw
            )
        }

        // 5. Storage Full
        if (storageFullEx != null || raw.contains("ENOSPC", ignoreCase = true) || raw.contains("No space left on device", ignoreCase = true) || raw.contains("Insufficient storage", ignoreCase = true) || raw.contains("Storage Full", ignoreCase = true)) {
            return ClassifiedDownloadError(
                category = ErrorCategory.STORAGE_FULL,
                title = "Disk Storage Full",
                userMessage = "Device storage ran out during download or staging allocation.",
                recoveryGuidance = "Free up storage space on your device and tap Resume.",
                isRetryable = true,
                rawMessage = raw
            )
        }

        // 6. Disk I/O Failure
        if (diskIoEx != null || raw.contains("EIO", ignoreCase = true) || raw.contains("Disk I/O error", ignoreCase = true) || raw.contains("I/O error", ignoreCase = true) || raw.contains("EROFS", ignoreCase = true) || raw.contains("Read-only file system", ignoreCase = true)) {
            return ClassifiedDownloadError(
                category = ErrorCategory.DISK_IO,
                title = "Disk I/O Failure",
                userMessage = "An input/output error occurred while reading or writing device storage.",
                recoveryGuidance = "Verify that your storage medium is mounted, not corrupted or full, and restart the transfer.",
                isRetryable = true,
                rawMessage = raw
            )
        }

        // 7. Integrity / Checksum Mismatch
        if (integrityEx != null || raw.contains("SHA-256", ignoreCase = true) || raw.contains("checksum mismatch", ignoreCase = true) || raw.contains("Integrity check failed", ignoreCase = true)) {
            return ClassifiedDownloadError(
                category = ErrorCategory.INTEGRITY_MISMATCH,
                title = "SHA-256 Checksum Mismatch",
                userMessage = "Downloaded file hash does not match expected integrity checksum.",
                recoveryGuidance = "The file was corrupted during transit. Redownload the file from source.",
                isRetryable = true,
                rawMessage = raw
            )
        }

        // 8. Socket / Connection Timeouts
        if (timeoutEx != null || (root is InterruptedIOException && !raw.contains("cancel", ignoreCase = true)) ||
            raw.contains("timed out", ignoreCase = true) || raw.contains("timeout", ignoreCase = true) || raw.contains("Connection refused", ignoreCase = true)
        ) {
            return ClassifiedDownloadError(
                category = ErrorCategory.TIMEOUT,
                title = "Connection Timed Out",
                userMessage = "Remote server did not respond within the allocated time.",
                recoveryGuidance = "The server or connection is slow. Azureload will automatically retry or you can resume later.",
                isRetryable = true,
                rawMessage = raw
            )
        }

        // 9. HTTP Status Codes
        val statusCode = dlEx?.statusCode ?: extractStatusCode(raw)
        if (statusCode != null) {
            return classifyHttpCode(statusCode, raw)
        }

        // 10. Unexpected EOF or truncated stream
        if (raw.contains("Unexpected EOF", ignoreCase = true) || raw.contains("premature EOF", ignoreCase = true)) {
            return ClassifiedDownloadError(
                category = ErrorCategory.TIMEOUT,
                title = "Transfer Incomplete (Unexpected EOF)",
                userMessage = "Connection closed before all bytes were transferred.",
                recoveryGuidance = "Server disconnected prematurely. Tap Retry to resume remaining bytes.",
                isRetryable = true,
                rawMessage = raw
            )
        }

        // Fallback Unknown
        return ClassifiedDownloadError(
            category = ErrorCategory.UNKNOWN,
            title = "Download Error",
            userMessage = raw,
            recoveryGuidance = "Review URL and network connection, then try resuming.",
            isRetryable = true,
            rawMessage = raw
        )
    }

    fun parseOrClassify(raw: String?): ClassifiedDownloadError {
        if (raw.isNullOrBlank()) {
            return ClassifiedDownloadError(
                category = ErrorCategory.UNKNOWN,
                title = "Download Failed",
                userMessage = "An unknown error interrupted this download.",
                recoveryGuidance = "Tap Retry to attempt restarting the transfer.",
                isRetryable = true
            )
        }

        // If an explicit HTTP status code is present in the stored string (e.g., "[HTTP 504 Gateway Timeout [HTTP 504]]"),
        // prioritize it over generic words like "timeout"
        val code = extractStatusCode(raw)
        if (code != null) {
            return classifyHttpCode(code, raw)
        }

        return when {
            // Permission checked first so messages mentioning "Storage Permission Denied" are not classified as storage full
            raw.contains("Permission", ignoreCase = true) || raw.contains("EACCES", ignoreCase = true) || raw.contains("Access denied", ignoreCase = true) ->
                classify(StoragePermissionDeniedException(raw))
            raw.contains("DNS", ignoreCase = true) || raw.contains("resolve host", ignoreCase = true) || raw.contains("No address associated", ignoreCase = true) ->
                classify(UnknownHostException(raw))
            raw.contains("SSL", ignoreCase = true) || raw.contains("TLS", ignoreCase = true) || raw.contains("CertPath", ignoreCase = true) ->
                classify(SSLException(raw))
            raw.contains("Storage Full", ignoreCase = true) || raw.contains("Disk Storage Full", ignoreCase = true) || raw.contains("ENOSPC", ignoreCase = true) || raw.contains("No space", ignoreCase = true) || raw.contains("Insufficient storage", ignoreCase = true) ->
                classify(StorageFullException(raw))
            raw.contains("Disk I/O", ignoreCase = true) || raw.contains("EIO", ignoreCase = true) || raw.contains("I/O error", ignoreCase = true) || raw.contains("EROFS", ignoreCase = true) ->
                classify(DiskIoException(raw))
            raw.contains("SHA-256", ignoreCase = true) || raw.contains("MD5", ignoreCase = true) || raw.contains("Integrity", ignoreCase = true) || raw.contains("checksum mismatch", ignoreCase = true) ->
                classify(IntegrityVerificationException(raw))
            raw.contains("Wi-Fi", ignoreCase = true) || raw.contains("Offline", ignoreCase = true) ->
                classify(NetworkOfflineException(raw))
            raw.contains("timed out", ignoreCase = true) || raw.contains("timeout", ignoreCase = true) || raw.contains("Connection refused", ignoreCase = true) ->
                classify(SocketTimeoutException(raw))
            raw.contains("Unexpected EOF", ignoreCase = true) ->
                classify(IOException(raw))
            else ->
                classify(IOException(raw))
        }
    }

    private fun classifyHttpCode(statusCode: Int, raw: String): ClassifiedDownloadError {
        return when (statusCode) {
            401 -> ClassifiedDownloadError(
                category = ErrorCategory.HTTP_CLIENT,
                title = "HTTP 401 Unauthorized",
                userMessage = "Authentication is required to access this file.",
                recoveryGuidance = "Log in via the built-in Browser or supply required session cookies.",
                isRetryable = false,
                httpStatusCode = 401,
                rawMessage = raw
            )
            403 -> ClassifiedDownloadError(
                category = ErrorCategory.HTTP_CLIENT,
                title = "HTTP 403 Forbidden",
                userMessage = "Access to this download was rejected by the server.",
                recoveryGuidance = "The server requires authentication or IP permissions. Check cookies or headers.",
                isRetryable = false,
                httpStatusCode = 403,
                rawMessage = raw
            )
            404 -> ClassifiedDownloadError(
                category = ErrorCategory.HTTP_CLIENT,
                title = "HTTP 404 Not Found",
                userMessage = "The requested file was not found on the remote server.",
                recoveryGuidance = "The download link has expired, moved, or was removed by the host.",
                isRetryable = false,
                httpStatusCode = 404,
                rawMessage = raw
            )
            410 -> ClassifiedDownloadError(
                category = ErrorCategory.HTTP_CLIENT,
                title = "HTTP 410 Gone",
                userMessage = "This download resource has been permanently removed.",
                recoveryGuidance = "This link is no longer valid. Obtain a new download link.",
                isRetryable = false,
                httpStatusCode = 410,
                rawMessage = raw
            )
            416 -> ClassifiedDownloadError(
                category = ErrorCategory.HTTP_CLIENT,
                title = "HTTP 416 Range Not Satisfiable",
                userMessage = "Server rejected requested byte offset.",
                recoveryGuidance = "Azureload will reset offset and resume from byte 0.",
                isRetryable = true,
                httpStatusCode = 416,
                rawMessage = raw
            )
            429 -> ClassifiedDownloadError(
                category = ErrorCategory.HTTP_CLIENT,
                title = "HTTP 429 Too Many Requests",
                userMessage = "Server rate limit was exceeded.",
                recoveryGuidance = "Wait before retrying or reduce concurrent connection threads.",
                isRetryable = true,
                httpStatusCode = 429,
                rawMessage = raw
            )
            in 400..499 -> ClassifiedDownloadError(
                category = ErrorCategory.HTTP_CLIENT,
                title = "HTTP $statusCode Client Error",
                userMessage = "Request was rejected by the remote server.",
                recoveryGuidance = "Verify download URL and request headers.",
                isRetryable = false,
                httpStatusCode = statusCode,
                rawMessage = raw
            )
            500 -> ClassifiedDownloadError(
                category = ErrorCategory.HTTP_SERVER,
                title = "HTTP 500 Internal Server Error",
                userMessage = "The remote server crashed or threw an internal error.",
                recoveryGuidance = "Temporary remote host glitch. Retry in a few minutes.",
                isRetryable = true,
                httpStatusCode = 500,
                rawMessage = raw
            )
            502 -> ClassifiedDownloadError(
                category = ErrorCategory.HTTP_SERVER,
                title = "HTTP 502 Bad Gateway",
                userMessage = "Upstream server or reverse proxy returned invalid response.",
                recoveryGuidance = "The host gateway is failing. Try again later.",
                isRetryable = true,
                httpStatusCode = 502,
                rawMessage = raw
            )
            503 -> ClassifiedDownloadError(
                category = ErrorCategory.HTTP_SERVER,
                title = "HTTP 503 Service Unavailable",
                userMessage = "Remote server is overloaded or down for maintenance.",
                recoveryGuidance = "Server is under high load. Azureload will back off and retry.",
                isRetryable = true,
                httpStatusCode = 503,
                rawMessage = raw
            )
            504 -> ClassifiedDownloadError(
                category = ErrorCategory.HTTP_SERVER,
                title = "HTTP 504 Gateway Timeout",
                userMessage = "Upstream server did not send response in time.",
                recoveryGuidance = "Network or gateway congested. Retry transfer.",
                isRetryable = true,
                httpStatusCode = 504,
                rawMessage = raw
            )
            in 500..599 -> ClassifiedDownloadError(
                category = ErrorCategory.HTTP_SERVER,
                title = "HTTP $statusCode Server Error",
                userMessage = "Remote server reported a temporary failure.",
                recoveryGuidance = "The server is temporarily having issues. Try again shortly.",
                isRetryable = true,
                httpStatusCode = statusCode,
                rawMessage = raw
            )
            else -> ClassifiedDownloadError(
                category = ErrorCategory.UNKNOWN,
                title = "HTTP $statusCode Response",
                userMessage = raw,
                recoveryGuidance = "Review transfer details and retry.",
                isRetryable = true,
                httpStatusCode = statusCode,
                rawMessage = raw
            )
        }
    }

    private fun extractStatusCode(text: String): Int? {
        val regex = Regex("""\b(?:HTTP|status code|code)\s*[:=]?\s*([45]\d{2})\b""", RegexOption.IGNORE_CASE)
        val match = regex.find(text)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    fun findRootCause(throwable: Throwable): Throwable {
        var current: Throwable = throwable
        val seen = mutableSetOf<Throwable>()
        while (current.cause != null && current.cause !== current && seen.add(current)) {
            current = current.cause!!
        }
        return current
    }

    inline fun <reified T : Throwable> findInChain(throwable: Throwable): T? {
        var current: Throwable? = throwable
        val seen = mutableSetOf<Throwable>()
        while (current != null && seen.add(current)) {
            if (current is T) return current
            current = current.cause
        }
        return null
    }
}
