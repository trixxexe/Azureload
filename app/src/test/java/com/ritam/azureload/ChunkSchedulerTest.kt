package com.ritam.azureload

import com.ritam.azureload.core.engine.ChunkScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkSchedulerTest {

    @Test
    fun testSmallFileYieldsSingleChunk() {
        val totalBytes = 500 * 1024L // 500 KB < 1MB MIN_CHUNK_SIZE
        val chunks = ChunkScheduler.partition(downloadId = 1L, totalBytes = totalBytes, requestedConcurrency = 4)

        assertEquals(1, chunks.size)
        assertEquals(0L, chunks[0].startOffset)
        assertEquals(totalBytes - 1, chunks[0].endOffset)
        assertEquals(totalBytes, chunks[0].totalSize)
    }

    @Test
    fun testPartitionNonOverlappingAndComplete() {
        val totalBytes = 10L * 1024 * 1024 + 12345L // ~10 MB with odd byte remainder
        val requestedConcurrency = 8

        val chunks = ChunkScheduler.partition(downloadId = 1L, totalBytes = totalBytes, requestedConcurrency = requestedConcurrency)

        assertTrue(chunks.size in 1..requestedConcurrency)
        assertEquals(0L, chunks.first().startOffset)
        assertEquals(totalBytes - 1, chunks.last().endOffset)

        var totalCalculated = 0L
        for (i in chunks.indices) {
            val chunk = chunks[i]
            assertTrue(chunk.startOffset <= chunk.endOffset)
            if (i > 0) {
                assertEquals(chunks[i - 1].endOffset + 1, chunk.startOffset)
            }
            totalCalculated += chunk.totalSize
        }

        assertEquals(totalBytes, totalCalculated)
    }

    @Test
    fun testSingleConcurrencyYieldsSingleChunk() {
        val totalBytes = 100L * 1024 * 1024
        val chunks = ChunkScheduler.partition(downloadId = 2L, totalBytes = totalBytes, requestedConcurrency = 1)

        assertEquals(1, chunks.size)
        assertEquals(0L, chunks[0].startOffset)
        assertEquals(totalBytes - 1, chunks[0].endOffset)
    }

    @Test
    fun testLargeFileBoundedByMaxConcurrency() {
        val totalBytes = 2L * 1024 * 1024 * 1024 // 2 GB
        val chunks = ChunkScheduler.partition(downloadId = 3L, totalBytes = totalBytes, requestedConcurrency = 32)

        // Should clamp to MAX_CONCURRENCY = 16
        assertEquals(16, chunks.size)
        assertEquals(0L, chunks.first().startOffset)
        assertEquals(totalBytes - 1, chunks.last().endOffset)
        assertEquals(totalBytes, chunks.sumOf { it.totalSize })
    }
}
