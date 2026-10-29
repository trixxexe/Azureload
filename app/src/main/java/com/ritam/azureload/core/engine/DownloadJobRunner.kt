package com.ritam.azureload.core.engine

import android.util.Log
import com.ritam.azureload.core.database.DownloadRepository
import com.ritam.azureload.core.model.ChunkItem
import com.ritam.azureload.core.model.ChunkStatus
import com.ritam.azureload.core.model.DownloadItem
import com.ritam.azureload.core.model.DownloadStatus
import com.ritam.azureload.core.network.CapabilityDetector
import com.ritam.azureload.core.network.HttpClientProvider
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.InputStream
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
        val initialDownload = repository.getDownloadById(downloadId) ?: return@withContext
        try {
            repository.updateDownloadStatus(downloadId, DownloadStatus.DETECTING)

            // Step 1: Detect server capabilities or re-verify if resuming
            val capability = capabilityDetector.detect(initialDownload.url, initialDownload.customHeaders)

            // ETag / Last-Modified change detection for existing downloads
            if (initialDownload.downloadedBytes > 0) {
                val etagChanged = initialDownload.eTag != null && capability.eTag != null && initialDownload.eTag != capability.eTag
                val lmChanged = initialDownload.lastModified != null && capability.lastModified != null && initialDownload.lastModified != capability.lastModified

                if (etagChanged || lmChanged) {
                    Log.w(tag, "Remote file changed (ETag/Last-Modified mismatch). Restarting from scratch.")
                    val stagingFile = File(initialDownload.stagingPath)
                    stagingFileManager.cleanupStaging(stagingFile)
                    repository.updateDownload(
                        initialDownload.copy(
                            downloadedBytes = 0,
                            eTag = capability.eTag,
                            lastModified = capability.lastModified,
                            totalBytes = capability.contentLength
                        )
                    )
                }
            }

            val totalBytes = if (capability.contentLength > 0) capability.contentLength else initialDownload.totalBytes
            val supportsRange = capability.supportsRange

            // Step 2: Storage space validation
            val bytesNeeded = if (totalBytes > 0) (totalBytes - initialDownload.downloadedBytes).coerceAtLeast(0) else 10L * 1024 * 1024
            if (!stagingFileManager.checkFreeSpace(bytesNeeded)) {
                repository.updateDownloadStatus(downloadId, DownloadStatus.FAILED, "Insufficient storage space on device")
                return@withContext
            }

            // Step 3: Ensure staging file is allocated
            val stagingFile = if (initialDownload.stagingPath.isNotBlank()) {
                File(initialDownload.stagingPath)
            } else {
                stagingFileManager.createStagingFile(downloadId)
            }

            if (supportsRange && totalBytes > 0) {
                stagingFileManager.allocateStagingFile(stagingFile, totalBytes)
            }

            repository.updateDownload(
                initialDownload.copy(
                    stagingPath = stagingFile.absolutePath,
                    totalBytes = totalBytes,
                    supportsRanges = supportsRange,
                    eTag = capability.eTag ?: initialDownload.eTag,
                    lastModified = capability.lastModified ?: initialDownload.lastModified,
                    status = DownloadStatus.DOWNLOADING
                )
            )

            // Step 4: Execute transfer (Parallel chunk or single stream)
            if (supportsRange && totalBytes > 0 && initialDownload.concurrency > 1) {
                executeMultiRangeDownload(initialDownload, stagingFile, totalBytes)
            } else {
                executeSingleStreamDownload(initialDownload, stagingFile, totalBytes, supportsRange)
            }

            // Step 5: Finalization & Verification
            repository.updateDownloadStatus(downloadId, DownloadStatus.FINALIZING)

            if (totalBytes > 0 && stagingFile.length() != totalBytes) {
                throw IllegalStateException("Integrity check failed: downloaded size (${stagingFile.length()}) != expected size ($totalBytes)")
            }

            if (!initialDownload.expectedSha256.isNullOrBlank()) {
                val matches = IntegrityVerifier.verifySha256(stagingFile, initialDownload.expectedSha256)
                if (!matches) {
                    throw IllegalStateException("Integrity check failed: SHA-256 checksum mismatch")
                }
            }

            val finalFile = stagingFileManager.finalizeDownload(stagingFile, initialDownload.fileName)
            repository.updateDownload(
                initialDownload.copy(
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
            // Preserve paused state
            repository.updateDownloadStatus(downloadId, DownloadStatus.PAUSED)
            throw ce
        } catch (e: Exception) {
            Log.e(tag, "Download $downloadId failed", e)
            repository.updateDownloadStatus(downloadId, DownloadStatus.FAILED, e.message ?: "Download error")
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
            } catch (e: Exception) {
                attempt++
                Log.w(tag, "Chunk ${currentChunk.chunkIndex} of download $downloadId attempt $attempt failed: ${e.message}")
                if (attempt >= maxAttempts) {
                    repository.updateChunkProgress(currentChunk.id, currentChunk.downloadedBytes, ChunkStatus.FAILED)
                    throw e
                }
                val jitter = Random.nextLong(100, 500)
                val backoffMs = (1000L * (1 shl min(attempt, 5))) + jitter
                delay(backoffMs)
                // Reload chunk state before next attempt
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
            return // already completed
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
            if (response.code != 206 && response.code != 200) {
                throw IllegalStateException("Server returned HTTP ${response.code} for chunk byte range $startByte-$endByte")
            }

            val body = response.body ?: throw IllegalStateException("Empty body for chunk $startByte-$endByte")
            body.byteStream().use { input ->
                val buffer = ByteArray(32 * 1024)
                var currentWriteOffset = startByte
                var totalChunkDownloaded = chunk.downloadedBytes
                var bytesRead: Int
                var lastDbPersist = System.currentTimeMillis()

                while (input.read(buffer).also { bytesRead = it } != -1) {
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

                repository.updateChunkProgress(chunk.id, totalChunkDownloaded, ChunkStatus.COMPLETED)
                updateGlobalProgress()
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
        if (supportsRange && stagingFile.exists() && stagingFile.length() > 0) {
            startOffset = stagingFile.length()
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
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: ${response.message}")
            }

            val body = response.body ?: throw IllegalStateException("Empty response body")
            body.byteStream().use { input ->
                val buffer = ByteArray(32 * 1024)
                var currentWriteOffset = startOffset
                var bytesRead: Int
                var lastDbPersist = System.currentTimeMillis()

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
