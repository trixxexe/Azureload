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

    @Test
    fun testEffectiveConnectionCountAndParallelStatus() {
        // HEXAQA scenario: No range support, 10 requested streams, 2GB
        val hexaqaItem = com.ritam.azureload.core.model.DownloadItem(
            url = "https://gen.hexaqa.com/gen?type=bin&size=2gb",
            destinationPath = "",
            stagingPath = "",
            fileName = "generated.bin",
            totalBytes = 2L * 1024 * 1024 * 1024,
            supportsRanges = false,
            concurrency = 10
        )
        org.junit.Assert.assertFalse(hexaqaItem.isParallelTransfer)
        assertEquals("Single Stream", hexaqaItem.transferModeTitle)
        assertEquals(1, hexaqaItem.effectiveConnectionCount())

        // Dynamic chunked stream without Content-Length (-1L)
        val unknownSizeItem = com.ritam.azureload.core.model.DownloadItem(
            url = "https://example.com/dynamic",
            destinationPath = "",
            stagingPath = "",
            fileName = "dynamic.bin",
            totalBytes = -1L,
            supportsRanges = true,
            concurrency = 8
        )
        org.junit.Assert.assertFalse(unknownSizeItem.isParallelTransfer)
        assertEquals("Single Stream", unknownSizeItem.transferModeTitle)
        assertEquals(1, unknownSizeItem.effectiveConnectionCount())

        // Small file under 1MB (500 KB) with range support
        val smallFileItem = com.ritam.azureload.core.model.DownloadItem(
            url = "https://example.com/small.bin",
            destinationPath = "",
            stagingPath = "",
            fileName = "small.bin",
            totalBytes = 500 * 1024L,
            supportsRanges = true,
            concurrency = 4
        )
        org.junit.Assert.assertFalse(smallFileItem.isParallelTransfer)
        assertEquals("Single Stream", smallFileItem.transferModeTitle)
        assertEquals(1, smallFileItem.effectiveConnectionCount())

        // 2MB file with 8 requested streams bounded by MIN_CHUNK_SIZE (1MB) -> max 2 chunks
        val twoMbItem = com.ritam.azureload.core.model.DownloadItem(
            url = "https://example.com/2mb.bin",
            destinationPath = "",
            stagingPath = "",
            fileName = "2mb.bin",
            totalBytes = 2 * 1024 * 1024L,
            supportsRanges = true,
            concurrency = 8
        )
        assertTrue(twoMbItem.isParallelTransfer)
        assertEquals("Multi-Connection Parallel", twoMbItem.transferModeTitle)
        assertEquals(2, twoMbItem.effectiveConnectionCount())

        // 16MB file with 4 requested streams -> 4 chunks
        val sixteenMbItem = com.ritam.azureload.core.model.DownloadItem(
            url = "https://example.com/16mb.bin",
            destinationPath = "",
            stagingPath = "",
            fileName = "16mb.bin",
            totalBytes = 16 * 1024 * 1024L,
            supportsRanges = true,
            concurrency = 4
        )
        assertTrue(sixteenMbItem.isParallelTransfer)
        assertEquals("Multi-Connection Parallel", sixteenMbItem.transferModeTitle)
        assertEquals(4, sixteenMbItem.effectiveConnectionCount())
    }
}
