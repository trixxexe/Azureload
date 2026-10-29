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
}
