package com.ritam.azureload

import com.ritam.azureload.core.storage.FileNameSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNameSanitizerTest {

    @Test
    fun testReplacesIllegalCharacters() {
        val input = "my/file\\name:test*one?two\"three<four>five|six.zip"
        val output = FileNameSanitizer.sanitize(input)

        assertFalse(output.contains("/"))
        assertFalse(output.contains("\\"))
        assertFalse(output.contains(":"))
        assertFalse(output.contains("*"))
        assertFalse(output.contains("?"))
        assertFalse(output.contains("\""))
        assertFalse(output.contains("<"))
        assertFalse(output.contains(">"))
        assertFalse(output.contains("|"))
        assertTrue(output.endsWith(".zip"))
    }

    @Test
    fun testPreventsRelativePathTraversal() {
        val input = "../../etc/passwd"
        val output = FileNameSanitizer.sanitize(input)

        assertFalse(output.startsWith("."))
        assertFalse(output.contains("/"))
    }

    @Test
    fun testEmptyFallback() {
        val input = "   "
        val output = FileNameSanitizer.sanitize(input, defaultName = "fallback.bin")
        assertEquals("fallback.bin", output)
    }

    @Test
    fun testLimitsExtremeLength() {
        val longBase = "a".repeat(300)
        val input = "$longBase.tar.gz"
        val output = FileNameSanitizer.sanitize(input)

        assertTrue(output.length <= 200)
        assertTrue(output.endsWith(".gz"))
    }
}
