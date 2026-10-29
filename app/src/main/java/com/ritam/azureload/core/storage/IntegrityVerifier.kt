package com.ritam.azureload.core.storage

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object IntegrityVerifier {
    fun verifySize(file: File, expectedSize: Long): Boolean {
        if (expectedSize <= 0) return true
        return file.exists() && file.length() == expectedSize
    }

    fun computeSha256(file: File): String {
        if (!file.exists()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verifySha256(file: File, expectedHash: String): Boolean {
        if (expectedHash.isBlank()) return true
        val actual = computeSha256(file)
        return actual.equals(expectedHash.trim(), ignoreCase = true)
    }
}
