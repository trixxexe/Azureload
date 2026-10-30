package com.ritam.azureload.core.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class ChecksumResult(
    val sha256: String,
    val md5: String
)

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

    fun computeMd5(file: File): String {
        if (!file.exists()) return ""
        val digest = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun computeChecksums(file: File): ChecksumResult = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext ChecksumResult("", "")
        val sha256Digest = MessageDigest.getInstance("SHA-256")
        val md5Digest = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                sha256Digest.update(buffer, 0, bytesRead)
                md5Digest.update(buffer, 0, bytesRead)
            }
        }
        ChecksumResult(
            sha256 = sha256Digest.digest().joinToString("") { "%02x".format(it) },
            md5 = md5Digest.digest().joinToString("") { "%02x".format(it) }
        )
    }

    fun verifySha256(file: File, expectedHash: String): Boolean {
        if (expectedHash.isBlank()) return true
        val actual = computeSha256(file)
        return actual.equals(expectedHash.trim(), ignoreCase = true)
    }

    fun verifyMd5(file: File, expectedHash: String): Boolean {
        if (expectedHash.isBlank()) return true
        val actual = computeMd5(file)
        return actual.equals(expectedHash.trim(), ignoreCase = true)
    }
}
