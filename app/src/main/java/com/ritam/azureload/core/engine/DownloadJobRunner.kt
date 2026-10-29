package com.ritam.azureload.core.engine

import android.util.Log
import com.ritam.azureload.core.database.DownloadRepository
import com.ritam.azureload.core.model.ChunkItem
import com.ritam.azureload.core.model.ChunkStatus
import com.ritam.azureload.core.model.DownloadItem
import com.ritam.azureload.core.model.DownloadStatus
import com.ritam.azureload.core.network.CapabilityDetector
import com.ritam.azureload.core.network.FatalDownloadException
import com.ritam.azureload.core.network.HttpClientProvider
import com.ritam.azureload.core.network.RangeNotSupportedException
import com.ritam.azureload.core.network.TransientDownloadException
import com.ritam.azureload.core.storage.IntegrityVerifier
import com.ritam.azureload.core.storage.StagingFileManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlin.random.Random

class DownloadJobRunner(
    private val downloadId: Long,
    private val repository: DownloadRepository,
    private val stagingFileManager: StagingFileManager,
    private val capabilityDetector: CapabilityDetector = CapabilityDetector(),
    private val httpClient: OkHttpClient = HttpClientProvider.client,
    private val onProgress: (Long, Long, Long) -> Unit = { _, _, _ -> } // (id, downloaded, speed)
) {
    private val tag = "DownloadJobRunner"
    private val lastBytesSample = AtomicLong(0L)
    private val lastTimeSample = AtomicLong(System.currentTimeMillis())
    private val currentSpeed = AtomicLong(0L)

    suspend fun run() = withContext(Dispatchers.IO) {
        var currentDownload = repository.getDownloadById(downloadId) ?: return@withContext
        try {
            repository.updateDownloadStatus(downloadId, DownloadStatus.DETECTING)

            // Step 1: Detect server capabilities or re-verify if resuming
            val capability = capabilityDetector.detect(currentDownload.url, currentDownload.customHeaders)

            // ETag / Last-Modified change detection for existing downloads
            if (currentDownload.downloadedBytes > 0) {
                val etagChanged = currentDownload.eTag != null && capability.eTag != null && currentDownload.eTag != capability.eTag
                val lmChanged = currentDownload.lastModified != null && capability.lastModified != null && currentDownload.lastModified != capability.lastModified

                if (etagChanged || lmChanged) {
                    Log.w(tag, "Remote file changed (ETag/Last-Modified mismatch). Restarting from scratch.")
                    if (currentDownload.stagingPath.isNotBlank()) {
                        stagingFileManager.cleanupStaging(File(currentDownload.stagingPath))
                    }
                    repository.saveChunks(downloadId, emptyList())
                    currentDownload = currentDownload.copy(
                        downloadedBytes = 0,
                        eTag = capability.eTag,
                        lastModified = capability.lastModified,
                        totalBytes = capability.contentLength
                    )
                    repository.updateDownload(currentDownload)
                }
            }

            val totalBytes = if (capability.contentLength > 0) capability.contentLength else currentDownload.totalBytes
            var supportsRange = capability.supportsRange

            // Step 2: Storage space validation
            val bytesNeeded = if (totalBytes > 0) (totalBytes - currentDownload.downloadedBytes).coerceAtLeast(0) else 10L * 1024 * 1024
            if (!stagingFileManager.checkFreeSpace(bytesNeeded)) {
                repository.updateDownloadStatus(downloadId, DownloadStatus.FAILED, "Insufficient storage space on device")
                return@withContext
            }

            // Step 3: Ensure staging file exists
            val stagingFile = if (currentDownload.stagingPath.isNotBlank()) {
                File(currentDownload.stagingPath)
            } else {
                stagingFileManager.createStagingFile(downloadId)
            }

            currentDownload = currentDownload.copy(
                stagingPath = stagingFile.absolutePath,
                totalBytes = totalBytes,
                supportsRanges = supportsRange,
                eTag = capability.eTag ?: currentDownload.eTag,
                lastModified = capability.lastModified ?: currentDownload.lastModified,
                status = DownloadStatus.DOWNLOADING
            )
            repository.updateDownload(currentDownload)

            // Step 4: Execute transfer (Parallel chunk or single stream with fallback)
            if (supportsRange && totalBytes > 0 && currentDownload.concurrency > 1) {
                stagingFileManager.allocateStagingFile(stagingFile, totalBytes)
                try {
                    executeMultiRangeDownload(currentDownload, stagingFile, totalBytes)
                } catch (rne: RangeNotSupportedException) {
                    Log.w(tag, "Server rejected range chunks. Falling back to single-stream download: ${rne.message}")
                    supportsRange = false
                    stagingFileManager.truncateStagingFile(stagingFile, 0L)
                    repository.saveChunks(downloadId, emptyList())
                    currentDownload = currentDownload.copy(supportsRanges = false, downloadedBytes = 0L)
                    repository.updateDownload(currentDownload)
                    executeSingleStreamDownload(currentDownload, stagingFile, totalBytes, supportsRange = false)
                }
            } else {
                executeSingleStreamDownload(currentDownload, stagingFile, totalBytes, supportsRange)
            }

            // Step 5: Finalization & Verification
            repository.updateDownloadStatus(downloadId, DownloadStatus.FINALIZING)

            if (totalBytes > 0 && stagingFile.length() != totalBytes) {
                throw IllegalStateException("Integrity check failed: downloaded size (${stagingFile.length()}) != expected size ($totalBytes)")
            }

            if (!currentDownload.expectedSha256.isNullOrBlank()) {
                val matches = IntegrityVerifier.verifySha256(stagingFile, currentDownload.expectedSha256)
                if (!matches) {
                    throw IllegalStateException("Integrity check failed: SHA-256 checksum mismatch")
                }
            }

            val finalFile = stagingFileManager.finalizeDownload(stagingFile, currentDownload.fileName)
            repository.updateDownload(
                currentDownload.copy(
                    status = DownloadStatus.COMPLETED,
                    destinationPath = finalFile.absolutePath,
                    downloadedBytes = finalFile.length(),
                    totalBytes = finalFile.length(),
                    errorMessage = null
                )
            )
            onProgress(downloadId, finalFile.length(), 0L)
            Log.i(tag, "Download $downloadId completed successfully: ${finalFile.absolutePath}")

        } catch (ce: CancellationException) {
            Log.i(tag, "Download $downloadId cancelled/paused")
            repository.updateDownloadStatus(downloadId, DownloadStatus.PAUSED)
            throw ce
        } catch (e: Exception) {
            Log.e(tag, "Download $downloadId failed", e)
            val msg = e.message ?: "Download error"
            repository.updateDownloadStatus(downloadId, DownloadStatus.FAILED, msg)
        }
    }

    private suspend fun executeMultiRangeDownload(
        download: DownloadItem,
        stagingFile: File,
        totalBytes: Long
    ) = coroutineScope {
        var chunks = repository.getChunksForDownload(downloadId)
        if (chunks.isEmpty()) {
            chunks = ChunkScheduler.partition(downloadId, totalBytes, download.concurrency)
            repository.saveChunks(downloadId, chunks)
        }

        lastBytesSample.set(chunks.sumOf { it.downloadedBytes })
        lastTimeSample.set(System.currentTimeMillis())

        val chunkJobs = chunks.map { chunk ->
            async(Dispatchers.IO) {
                if (chunk.status == ChunkStatus.COMPLETED && chunk.downloadedBytes >= chunk.totalSize) {
                    return@async
                }
                downloadChunkWithRetry(download, chunk, stagingFile)
            }
        }

        chunkJobs.awaitAll()
    }

    private suspend fun downloadChunkWithRetry(
        download: DownloadItem,
        initialChunk: ChunkItem,
        stagingFile: File
    ) {
        var currentChunk = initialChunk
        var attempt = 0
        val maxAttempts = 6

        while (attempt < maxAttempts) {
            currentCoroutineContext().ensureActive()
            try {
                repository.updateChunkProgress(currentChunk.id, currentChunk.downloadedBytes, ChunkStatus.DOWNLOADING)
                downloadChunkStream(download, currentChunk, stagingFile)
                repository.updateChunkProgress(currentChunk.id, currentChunk.totalSize, ChunkStatus.COMPLETED)
                return
            } catch (ce: CancellationException) {
                throw ce
            } catch (rne: RangeNotSupportedException) {
                throw rne
            } catch (fe: FatalDownloadException) {
                repository.updateChunkProgress(currentChunk.id, currentChunk.downloadedBytes, ChunkStatus.FAILED)
                throw fe
            } catch (e: Exception) {
                attempt++
                Log.w(tag, "Chunk ${currentChunk.chunkIndex} of download $downloadId attempt $attempt failed: ${e.message}")
                if (attempt >= maxAttempts) {
                    repository.updateChunkProgress(currentChunk.id, currentChunk.downloadedBytes, ChunkStatus.FAILED)
                    throw e
                }

                var delayMs = (1000L * (1 shl min(attempt, 5))) + Random.nextLong(100, 500)
                if (e is TransientDownloadException && e.retryAfterSeconds != null) {
                    delayMs = (e.retryAfterSeconds * 1000L).coerceIn(500L, 60_000L)
                }
                delay(delayMs)

                val refreshed = repository.getChunksForDownload(downloadId).firstOrNull { it.id == currentChunk.id }
                if (refreshed != null) currentChunk = refreshed
            }
        }
    }

    private suspend fun downloadChunkStream(
        download: DownloadItem,
        chunk: ChunkItem,
        stagingFile: File
    ) {
        val startByte = chunk.startOffset + chunk.downloadedBytes
        val endByte = chunk.endOffset

        if (startByte > endByte) {
            return
        }

        val requestBuilder = Request.Builder()
            .url(download.url)
            .header("Range", "bytes=$startByte-$endByte")
            .header("User-Agent", "Azureload/1.0 (Android; Mobile)")

        for ((k, v) in download.customHeaders) {
            requestBuilder.header(k, v)
        }

        var response: Response? = null
        try {
            response = httpClient.newCall(requestBuilder.build()).execute()
            val code = response.code

            if (code == 429) {
                val retryAfterSec = response.header("Retry-After")?.toLongOrNull()
                throw TransientDownloadException("Server returned HTTP 429 Too Many Requests", statusCode = 429, retryAfterSeconds = retryAfterSec)
            } else if (code in 500..599) {
                val retryAfterSec = response.header("Retry-After")?.toLongOrNull()
                throw TransientDownloadException("Server returned HTTP $code: ${response.message}", statusCode = code, retryAfterSeconds = retryAfterSec)
            } else if (code == 200) {
                throw RangeNotSupportedException("Server returned HTTP 200 OK for byte range request. Server ignores byte ranges.")
            } else if (code in 400..499 && code != 206) {
                throw FatalDownloadException("Server returned HTTP $code for chunk range $startByte-$endByte", statusCode = code)
            } else if (code != 206) {
                throw IOException("Unexpected HTTP status code $code for chunk range $startByte-$endByte")
            }

            val contentRange = response.header("Content-Range")
            if (contentRange != null && !contentRange.contains("bytes $startByte-")) {
                throw IOException("Mismatched Content-Range header: $contentRange, expected start $startByte")
            }

            val body = response.body ?: throw FatalDownloadException("Empty response body for chunk $startByte-$endByte")
            body.byteStream().use { input ->
                val buffer = ByteArray(32 * 1024)
                var currentWriteOffset = startByte
                var totalChunkDownloaded = chunk.downloadedBytes
                val expectedChunkSize = chunk.totalSize
                var bytesRead: Int
                var lastDbPersist = System.currentTimeMillis()

                try {
                    while (totalChunkDownloaded < expectedChunkSize) {
                        val maxToRead = min(buffer.size.toLong(), expectedChunkSize - totalChunkDownloaded).toInt()
                        bytesRead = input.read(buffer, 0, maxToRead)
                        if (bytesRead == -1) break

                        currentCoroutineContext().ensureActive()
                        stagingFileManager.writeChunkData(stagingFile, currentWriteOffset, buffer, bytesRead)
                        currentWriteOffset += bytesRead
                        totalChunkDownloaded += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastDbPersist >= 500) {
                            repository.updateChunkProgress(chunk.id, totalChunkDownloaded, ChunkStatus.DOWNLOADING)
                            updateGlobalProgress()
                            lastDbPersist = now
                        }
                    }
                } finally {
                    repository.updateChunkProgress(
                        chunk.id,
                        totalChunkDownloaded,
                        if (totalChunkDownloaded >= expectedChunkSize) ChunkStatus.COMPLETED else ChunkStatus.DOWNLOADING
                    )
                    updateGlobalProgress()
                }

                if (totalChunkDownloaded < expectedChunkSize) {
                    throw IOException("Unexpected EOF in chunk ${chunk.chunkIndex}: expected $expectedChunkSize bytes, got $totalChunkDownloaded bytes")
                }
            }
        } finally {
            response?.close()
        }
    }

    private suspend fun executeSingleStreamDownload(
        download: DownloadItem,
        stagingFile: File,
        totalBytes: Long,
        supportsRange: Boolean
    ) {
        var startOffset = 0L
        if (supportsRange && stagingFile.exists() && download.downloadedBytes > 0 && (totalBytes <= 0 || download.downloadedBytes < totalBytes)) {
            startOffset = download.downloadedBytes
        }

        val requestBuilder = Request.Builder()
            .url(download.url)
            .header("User-Agent", "Azureload/1.0 (Android; Mobile)")

        if (startOffset > 0) {
            requestBuilder.header("Range", "bytes=$startOffset-")
        }

        for ((k, v) in download.customHeaders) {
            requestBuilder.header(k, v)
        }

        var response: Response? = null
        try {
            response = httpClient.newCall(requestBuilder.build()).execute()
            val code = response.code

            if (code == 429) {
                val retryAfterSec = response.header("Retry-After")?.toLongOrNull()
                throw TransientDownloadException("Server returned HTTP 429 Too Many Requests", statusCode = 429, retryAfterSeconds = retryAfterSec)
            } else if (code in 500..599) {
                val retryAfterSec = response.header("Retry-After")?.toLongOrNull()
                throw TransientDownloadException("Server returned HTTP $code: ${response.message}", statusCode = code, retryAfterSeconds = retryAfterSec)
            } else if (code in 400..499 && code != 416) {
                throw FatalDownloadException("Server returned HTTP $code: ${response.message}", statusCode = code)
            }

            var currentWriteOffset = startOffset
            if (startOffset > 0) {
                if (code == 200) {
                    Log.w(tag, "Server ignored Range header on resume (HTTP 200). Restarting single stream from byte 0.")
                    currentWriteOffset = 0L
                    stagingFileManager.truncateStagingFile(stagingFile, 0L)
                } else if (code == 206) {
                    val contentRange = response.header("Content-Range")
                    if (contentRange != null && !contentRange.contains("bytes $startOffset-")) {
                        Log.w(tag, "Unexpected Content-Range: $contentRange, expected start $startOffset. Restarting.")
                        currentWriteOffset = 0L
                        stagingFileManager.truncateStagingFile(stagingFile, 0L)
                    }
                }
            } else {
                if (!response.isSuccessful) {
                    throw FatalDownloadException("HTTP $code: ${response.message}", statusCode = code)
                }
                stagingFileManager.truncateStagingFile(stagingFile, 0L)
            }

            val body = response.body ?: throw FatalDownloadException("Empty response body from server")
            body.byteStream().use { input ->
                val buffer = ByteArray(32 * 1024)
                var bytesRead: Int
                var lastDbPersist = System.currentTimeMillis()

                try {
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        currentCoroutineContext().ensureActive()
                        stagingFileManager.writeChunkData(stagingFile, currentWriteOffset, buffer, bytesRead)
                        currentWriteOffset += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastDbPersist >= 500) {
                            calculateSpeed(currentWriteOffset)
                            repository.updateDownloadProgress(downloadId, currentWriteOffset)
                            onProgress(downloadId, currentWriteOffset, currentSpeed.get())
                            lastDbPersist = now
                        }
                    }
                } finally {
                    repository.updateDownloadProgress(downloadId, currentWriteOffset)
                }

                if (totalBytes > 0 && currentWriteOffset < totalBytes) {
                    throw IOException("Unexpected EOF: received $currentWriteOffset bytes, expected $totalBytes bytes")
                }

                repository.updateDownloadProgress(downloadId, currentWriteOffset)
                onProgress(downloadId, currentWriteOffset, 0L)
            }
        } finally {
            response?.close()
        }
    }

    private suspend fun updateGlobalProgress() {
        val allChunks = repository.getChunksForDownload(downloadId)
        val sumDownloaded = allChunks.sumOf { it.downloadedBytes }
        calculateSpeed(sumDownloaded)
        repository.updateDownloadProgress(downloadId, sumDownloaded)
        onProgress(downloadId, sumDownloaded, currentSpeed.get())
    }

    private fun calculateSpeed(totalBytesNow: Long) {
        val now = System.currentTimeMillis()
        val prevTime = lastTimeSample.get()
        val deltaTime = now - prevTime
        if (deltaTime >= 800) {
            val prevBytes = lastBytesSample.get()
            val deltaBytes = (totalBytesNow - prevBytes).coerceAtLeast(0)
            val speed = (deltaBytes * 1000L) / deltaTime
            currentSpeed.set(speed)
            lastBytesSample.set(totalBytesNow)
            lastTimeSample.set(now)
        }
    }
}
