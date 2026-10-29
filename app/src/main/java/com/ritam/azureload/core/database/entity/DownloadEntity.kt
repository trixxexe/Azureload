package com.ritam.azureload.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ritam.azureload.core.model.DownloadItem
import com.ritam.azureload.core.model.DownloadStatus

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val originalUrl: String,
    val destinationPath: String,
    val stagingPath: String,
    val fileName: String,
    val mimeType: String?,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: String,
    val supportsRanges: Boolean,
    val eTag: String?,
    val lastModified: String?,
    val concurrency: Int,
    val expectedSha256: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val errorMessage: String?,
    val headersJson: String?
) {
    fun toDomain(speed: Long = 0L): DownloadItem {
        return DownloadItem(
            id = id,
            url = url,
            originalUrl = originalUrl,
            destinationPath = destinationPath,
            stagingPath = stagingPath,
            fileName = fileName,
            mimeType = mimeType,
            totalBytes = totalBytes,
            downloadedBytes = downloadedBytes,
            status = try { DownloadStatus.valueOf(status) } catch (_: Exception) { DownloadStatus.FAILED },
            supportsRanges = supportsRanges,
            eTag = eTag,
            lastModified = lastModified,
            concurrency = concurrency,
            expectedSha256 = expectedSha256,
            createdAt = createdAt,
            updatedAt = updatedAt,
            errorMessage = errorMessage,
            customHeaders = parseHeaders(headersJson),
            currentSpeedBytesPerSec = speed
        )
    }

    companion object {
        fun fromDomain(item: DownloadItem): DownloadEntity {
            return DownloadEntity(
                id = item.id,
                url = item.url,
                originalUrl = item.originalUrl,
                destinationPath = item.destinationPath,
                stagingPath = item.stagingPath,
                fileName = item.fileName,
                mimeType = item.mimeType,
                totalBytes = item.totalBytes,
                downloadedBytes = item.downloadedBytes,
                status = item.status.name,
                supportsRanges = item.supportsRanges,
                eTag = item.eTag,
                lastModified = item.lastModified,
                concurrency = item.concurrency,
                expectedSha256 = item.expectedSha256,
                createdAt = item.createdAt,
                updatedAt = item.updatedAt,
                errorMessage = item.errorMessage,
                headersJson = serializeHeaders(item.customHeaders)
            )
        }

        private fun serializeHeaders(headers: Map<String, String>): String? {
            if (headers.isEmpty()) return null
            return headers.entries.joinToString("\n") { "${it.key}:${it.value}" }
        }

        private fun parseHeaders(raw: String?): Map<String, String> {
            if (raw.isNullOrBlank()) return emptyMap()
            return raw.lines()
                .mapNotNull { line ->
                    val idx = line.indexOf(':')
                    if (idx > 0) line.substring(0, idx).trim() to line.substring(idx + 1).trim()
                    else null
                }.toMap()
        }
    }
}
