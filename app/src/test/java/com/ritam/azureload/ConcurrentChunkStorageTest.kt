package com.ritam.azureload

import com.ritam.azureload.core.engine.ChunkScheduler
import com.ritam.azureload.core.storage.IntegrityVerifier
import com.ritam.azureload.core.storage.StagingFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileInputStream
import kotlin.random.Random

class ConcurrentChunkStorageTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testConcurrentChunkWritesAssembleExactPayload() = runBlocking {
        val baseDir = tempFolder.newFolder("staging_test")
        val stagingManager = StagingFileManager(customBaseDir = baseDir)

        val totalSize = 4 * 1024 * 1024 + 777 // ~4MB with odd bytes
        val testPayload = ByteArray(totalSize)
        Random.nextBytes(testPayload)

        val stagingFile = stagingManager.createStagingFile(downloadId = 100L)
        stagingManager.allocateStagingFile(stagingFile, totalSize.toLong())

        val chunks = ChunkScheduler.partition(downloadId = 100L, totalBytes = totalSize.toLong(), requestedConcurrency = 4)
        assertEquals(4, chunks.size)

        // Write each chunk concurrently from separate threads
        val jobs = chunks.map { chunk ->
            async(Dispatchers.IO) {
                val start = chunk.startOffset.toInt()
                val length = chunk.totalSize.toInt()
                val chunkData = testPayload.copyOfRange(start, start + length)
                stagingManager.writeChunkData(stagingFile, chunk.startOffset, chunkData, length)
            }
        }
        jobs.awaitAll()

        // Verify assembled staging file
        assertEquals(totalSize.toLong(), stagingFile.length())
        val writtenBytes = stagingFile.readBytes()
        assertArrayEquals(testPayload, writtenBytes)

        // Test finalize moves and preserves exact bytes
        val finalizedFile = stagingManager.finalizeDownload(stagingFile, "assembled_output.bin")
        assertTrue(finalizedFile.exists())
        assertEquals(totalSize.toLong(), finalizedFile.length())
        assertArrayEquals(testPayload, finalizedFile.readBytes())

        // Test SHA-256 verification on finalized file
        val expectedSha = IntegrityVerifier.computeSha256(finalizedFile)
        assertTrue(IntegrityVerifier.verifySha256(finalizedFile, expectedSha))
    }
}
