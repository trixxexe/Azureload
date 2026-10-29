package com.ritam.azureload.core.network

import com.ritam.azureload.core.model.ServerCapability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

class CapabilityDetector(
    private val client: OkHttpClient = HttpClientProvider.client
) {
    suspend fun detect(
        url: String,
        customHeaders: Map<String, String> = emptyMap()
    ): ServerCapability = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-0")
            .header("User-Agent", "Azureload/1.0 (Android; Mobile)")

        for ((key, value) in customHeaders) {
            if (!key.equals("Range", ignoreCase = true)) {
                requestBuilder.header(key, value)
            }
        }

        val request = requestBuilder.build()
        var response: Response? = null
        try {
            response = client.newCall(request).execute()
            val code = response.code

            // Handle HTTP error codes
            if (code == 429) {
                val retryAfterSec = parseRetryAfter(response.header("Retry-After"))
                throw TransientDownloadException("Server returned HTTP 429 Too Many Requests", statusCode = 429, retryAfterSeconds = retryAfterSec)
            } else if (code in 500..599) {
                val retryAfterSec = parseRetryAfter(response.header("Retry-After"))
                throw TransientDownloadException("Server returned HTTP $code: ${response.message}", statusCode = code, retryAfterSeconds = retryAfterSec)
            } else if (code in 400..499 && code != 416) {
                throw FatalDownloadException("Server returned HTTP $code: ${response.message}", statusCode = code)
            }

            val finalUrl = response.request.url.toString()
            val eTag = response.header("ETag")
            val lastModified = response.header("Last-Modified")
            val contentType = response.header("Content-Type")
            val contentEncoding = response.header("Content-Encoding")
            val acceptRanges = response.header("Accept-Ranges")
            val contentDisposition = response.header("Content-Disposition")
            val contentRange = response.header("Content-Range")

            val isCompressed = contentEncoding != null && !contentEncoding.equals("identity", ignoreCase = true)
            var supportsRange = code == 206
            if (acceptRanges.equals("none", ignoreCase = true)) {
                supportsRange = false
            } else if (!supportsRange && acceptRanges.equals("bytes", ignoreCase = true) && code == 200) {
                // Some servers declare Accept-Ranges but return 200 on bytes=0-0.
                // Per spec: only treat as true if 206 was verified.
                supportsRange = false
            }

            // Determine total content length
            var totalLength: Long = -1L
            if (contentRange != null) {
                // Format: bytes 0-0/146515 or bytes 0-0/* or bytes */0
                val slashIdx = contentRange.lastIndexOf('/')
                if (slashIdx != -1) {
                    val totalPart = contentRange.substring(slashIdx + 1).trim()
                    totalLength = totalPart.toLongOrNull() ?: -1L
                }
            }

            if (totalLength < 0) {
                val cl = response.header("Content-Length")?.toLongOrNull() ?: -1L
                if (code == 200 && cl >= 0) {
                    totalLength = cl
                }
            }

            // Dynamic compressed streams (gzip) cannot be byte-split reliably
            if (isCompressed) {
                supportsRange = false
            }

            val suggestedFileName = extractFileName(contentDisposition, finalUrl)

            ServerCapability(
                url = finalUrl,
                supportsRange = supportsRange,
                contentLength = totalLength,
                eTag = eTag,
                lastModified = lastModified,
                contentType = contentType,
                suggestedFileName = suggestedFileName,
                isCompressed = isCompressed,
                responseCode = code
            )
        } catch (e: Exception) {
            throw e
        } finally {
            response?.close()
        }
    }

    companion object {
        fun parseRetryAfter(raw: String?): Long? {
            if (raw.isNullOrBlank()) return null
            raw.trim().toLongOrNull()?.let { return it.coerceAtLeast(0) }
            val formats = arrayOf(
                "EEE, dd MMM yyyy HH:mm:ss zzz",
                "EEEE, dd-MMM-yy HH:mm:ss zzz",
                "EEE MMM d HH:mm:ss yyyy"
            )
            for (pattern in formats) {
                try {
                    val sdf = SimpleDateFormat(pattern, Locale.US)
                    sdf.timeZone = TimeZone.getTimeZone("GMT")
                    val date = sdf.parse(raw.trim())
                    if (date != null) {
                        val delaySec = (date.time - System.currentTimeMillis()) / 1000L
                        return delaySec.coerceAtLeast(0)
                    }
                } catch (_: Exception) {}
            }
            return null
        }

        fun extractFileName(contentDisposition: String?, url: String): String {
            if (!contentDisposition.isNullOrBlank()) {
                // RFC 5987 / RFC 6266 filename*=UTF-8''...
                val utf8Pattern = Pattern.compile("filename\\*=(?:UTF-8|utf-8)''([^;\\r\\n]+)")
                val utf8Matcher = utf8Pattern.matcher(contentDisposition)
                if (utf8Matcher.find()) {
                    val raw = utf8Matcher.group(1).orEmpty().trim('"', '\'')
                    try {
                        return URLDecoder.decode(raw, "UTF-8")
                    } catch (_: Exception) {
                        return raw
                    }
                }

                // Standard quoted filename="..."
                val quotedPattern = Pattern.compile("filename=\"([^\"]+)\"")
                val quotedMatcher = quotedPattern.matcher(contentDisposition)
                if (quotedMatcher.find()) {
                    val raw = quotedMatcher.group(1).orEmpty().trim()
                    try {
                        return URLDecoder.decode(raw, "UTF-8")
                    } catch (_: Exception) {
                        return raw
                    }
                }

                // Standard unquoted filename=...
                val unquotedPattern = Pattern.compile("filename=([^;\\s]+)")
                val unquotedMatcher = unquotedPattern.matcher(contentDisposition)
                if (unquotedMatcher.find()) {
                    val raw = unquotedMatcher.group(1).orEmpty().trim('"', '\'')
                    try {
                        return URLDecoder.decode(raw, "UTF-8")
                    } catch (_: Exception) {
                        return raw
                    }
                }
            }

            // Fallback to URL path segment
            try {
                val cleanUrl = url.substringBefore('?').substringBefore('#')
                val lastSegment = cleanUrl.substringAfterLast('/')
                if (lastSegment.isNotBlank()) {
                    return URLDecoder.decode(lastSegment, "UTF-8")
                }
            } catch (_: Exception) {}

            return "download_${System.currentTimeMillis()}"
        }
    }
}
