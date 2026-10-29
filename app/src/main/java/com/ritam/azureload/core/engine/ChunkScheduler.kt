package com.ritam.azureload.core.engine

import com.ritam.azureload.core.model.ChunkItem
import com.ritam.azureload.core.model.ChunkStatus
import kotlin.math.max
import kotlin.math.min

object ChunkScheduler {
    const val MIN_CHUNK_SIZE: Long = 1024 * 1024 // 1 MB minimum chunk size
    const val MAX_CONCURRENCY: Int = 16

    fun partition(
        downloadId: Long,
        totalBytes: Long,
        requestedConcurrency: Int
    ): List<ChunkItem> {
        if (totalBytes <= 0) {
            return listOf(
                ChunkItem(
                    downloadId = downloadId,
                    chunkIndex = 0,
                    startOffset = 0,
                    endOffset = -1,
                    downloadedBytes = 0,
                    status = ChunkStatus.PENDING
                )
            )
        }

        val clampedConcurrency = requestedConcurrency.coerceIn(1, MAX_CONCURRENCY)

        // If file is smaller than MIN_CHUNK_SIZE, use 1 chunk
        if (totalBytes < MIN_CHUNK_SIZE || clampedConcurrency == 1) {
            return listOf(
                ChunkItem(
                    downloadId = downloadId,
                    chunkIndex = 0,
                    startOffset = 0,
                    endOffset = totalBytes - 1,
                    downloadedBytes = 0,
                    status = ChunkStatus.PENDING
                )
            )
        }

        // Determine effective chunk count so each chunk is at least MIN_CHUNK_SIZE
        val maxPossibleChunks = (totalBytes / MIN_CHUNK_SIZE).toInt().coerceAtLeast(1)
        val numChunks = min(clampedConcurrency, maxPossibleChunks)

        val baseChunkSize = totalBytes / numChunks
        var remainder = totalBytes % numChunks

        val chunks = ArrayList<ChunkItem>(numChunks)
        var currentStart = 0L

        for (i in 0 until numChunks) {
            var chunkSize = baseChunkSize
            if (remainder > 0) {
                chunkSize += 1
                remainder -= 1
            }
            val currentEnd = currentStart + chunkSize - 1

            chunks.add(
                ChunkItem(
                    downloadId = downloadId,
                    chunkIndex = i,
                    startOffset = currentStart,
                    endOffset = currentEnd,
                    downloadedBytes = 0,
                    status = ChunkStatus.PENDING
                )
            )
            currentStart = currentEnd + 1
        }

        return chunks
    }
}
