package com.ritam.azureload

import com.ritam.azureload.core.storage.IntegrityVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class IntegrityVerifierTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testComputeAndVerifySha256() {
        val testFile = tempFolder.newFile("hello.txt")
        testFile.writeText("Azureload Multi-Connection Accelerator")

        // Known SHA-256 for "Azureload Multi-Connection Accelerator"
        val hash = IntegrityVerifier.computeSha256(testFile)
        assertTrue(hash.isNotEmpty())

        assertTrue(IntegrityVerifier.verifySha256(testFile, hash))
        assertFalse(IntegrityVerifier.verifySha256(testFile, "0000000000000000000000000000000000000000000000000000000000000000"))
    }

    @Test
    fun testVerifySize() {
        val testFile = tempFolder.newFile("size_test.bin")
        val data = ByteArray(1024)
        testFile.writeBytes(data)

        assertTrue(IntegrityVerifier.verifySize(testFile, 1024))
        assertFalse(IntegrityVerifier.verifySize(testFile, 2048))
    }

    @Test
    fun testComputeAndVerifyMd5() {
        val testFile = tempFolder.newFile("md5_test.txt")
        testFile.writeText("Azureload Multi-Connection Accelerator")

        val md5 = IntegrityVerifier.computeMd5(testFile)
        assertTrue(md5.isNotEmpty())
        assertEquals(32, md5.length)

        assertTrue(IntegrityVerifier.verifyMd5(testFile, md5))
        assertFalse(IntegrityVerifier.verifyMd5(testFile, "d41d8cd98f00b204e9800998ecf8427e"))
    }

    @Test
    fun testDualComputeChecksums() = kotlinx.coroutines.runBlocking {
        val testFile = tempFolder.newFile("dual_test.txt")
        testFile.writeText("Checksum verification payload")

        val res = IntegrityVerifier.computeChecksums(testFile)
        assertEquals(64, res.sha256.length)
        assertEquals(32, res.md5.length)

        assertEquals(IntegrityVerifier.computeSha256(testFile), res.sha256)
        assertEquals(IntegrityVerifier.computeMd5(testFile), res.md5)
    }
}
