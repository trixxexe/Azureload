package com.ritam.azureload

import com.ritam.azureload.core.network.CapabilityDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CapabilityDetectorParsingTest {

    @Test
    fun testExtractFileNameStandard() {
        val disposition = "attachment; filename=\"ubuntu-24.04-desktop.iso\""
        val name = CapabilityDetector.extractFileName(disposition, "https://releases.ubuntu.com/download")
        assertEquals("ubuntu-24.04-desktop.iso", name)
    }

    @Test
    fun testExtractFileNameQuotedWithSemicolon() {
        val disposition = "attachment; filename=\"report; 2026-financials.pdf\""
        val name = CapabilityDetector.extractFileName(disposition, "https://example.com/download")
        assertEquals("report; 2026-financials.pdf", name)
    }

    @Test
    fun testExtractFileNameRfc5987Utf8() {
        val disposition = "attachment; filename*=UTF-8''my%20archive%202026.zip"
        val name = CapabilityDetector.extractFileName(disposition, "https://example.com/file")
        assertEquals("my archive 2026.zip", name)
    }

    @Test
    fun testExtractFileNameMalformedPercentDoesNotCrash() {
        val disposition = "attachment; filename*=UTF-8''100%_working.zip"
        val name = CapabilityDetector.extractFileName(disposition, "https://example.com/file")
        assertNotNull(name)
        assertEquals("100%_working.zip", name)
    }

    @Test
    fun testExtractFileNameFallbackUrl() {
        val url = "https://cdn.example.org/packages/v1.2.3/azureload-core.apk?token=xyz123#dl"
        val name = CapabilityDetector.extractFileName(null, url)
        assertEquals("azureload-core.apk", name)
    }
}
