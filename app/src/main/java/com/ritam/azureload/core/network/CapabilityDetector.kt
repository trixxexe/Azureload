package com.ritam.azureload.core.network

import com.ritam.azureload.core.model.ServerCapability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.URLDecoder
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
            requestBuilder.header(key, value)
        }

        val request = requestBuilder.build()
        var response: Response? = null
        try {
            response = client.newCall(request).execute()
            val code = response.code
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
            if (!supportsRange && acceptRanges.equals("bytes", ignoreCase = true) && code == 200) {
                // Some servers declare Accept-Ranges but return 200 on bytes=0-0
                // Still, per spec: "Never assume that sending a Range header guarantees range support."
                // Only treat as true if 206 was actually returned or verified.
                supportsRange = false
            }

            // Determine total content length
            var totalLength: Long = -1L
            if (contentRange != null) {
                // Format: bytes 0-0/146515 or bytes 0-0/*
                val slashIdx = contentRange.lastIndexOf('/')
                if (slashIdx != -1) {
                    val totalPart = contentRange.substring(slashIdx + 1).trim()
                    totalLength = totalPart.toLongOrNull() ?: -1L
                }
            }

            if (totalLength <= 0) {
                val cl = response.header("Content-Length")?.toLongOrNull() ?: -1L
                if (code == 200 && cl > 0) {
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
        fun extractFileName(contentDisposition: String?, url: String): String {
            if (!contentDisposition.isNullOrBlank()) {
                // filename*=UTF-8''filename or filename="filename"
                val utf8Pattern = Pattern.compile("filename\\*=(?:UTF-8|utf-8)''([^;]+)")
                val utf8Matcher = utf8Pattern.matcher(contentDisposition)
                if (utf8Matcher.find()) {
                    return URLDecoder.decode(utf8Matcher.group(1).orEmpty(), "UTF-8").trim('"', '\'')
                }

                val standardPattern = Pattern.compile("filename=\"?([^\";]+)\"?")
                val standardMatcher = standardPattern.matcher(contentDisposition)
                if (standardMatcher.find()) {
                    return standardMatcher.group(1).orEmpty().trim('"', '\'')
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
