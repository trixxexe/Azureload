package com.ritam.azureload.core.model

enum class DownloadStatus {
    QUEUED,
    DETECTING,
    DOWNLOADING,
    PAUSED,
    RETRYING,
    FINALIZING,
    COMPLETED,
    FAILED
}

enum class ChunkStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

data class ServerCapability(
    val url: String,
    val supportsRange: Boolean,
    val contentLength: Long,
    val eTag: String? = null,
    val lastModified: String? = null,
    val contentType: String? = null,
    val suggestedFileName: String? = null,
    val isCompressed: Boolean = false,
    val responseCode: Int = 200
)

data class DownloadItem(
    val id: Long = 0,
    val url: String,
    val originalUrl: String = url,
    val destinationPath: String,
    val stagingPath: String,
    val fileName: String,
    val mimeType: String? = null,
    val totalBytes: Long = -1L,
    val downloadedBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val supportsRanges: Boolean = false,
    val eTag: String? = null,
    val lastModified: String? = null,
    val concurrency: Int = 4,
    val expectedSha256: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val errorMessage: String? = null,
    val customHeaders: Map<String, String> = emptyMap(),
    val currentSpeedBytesPerSec: Long = 0L
) {
    val isParallelTransfer: Boolean
        get() = supportsRanges && concurrency > 1 && totalBytes >= 1024 * 1024L

    val transferModeTitle: String
        get() = if (isParallelTransfer) "Multi-Connection Parallel" else "Single Stream"

    fun effectiveConnectionCount(chunks: List<ChunkItem> = emptyList()): Int {
        if (!isParallelTransfer) return 1
        if (chunks.isNotEmpty()) return chunks.size
        if (totalBytes >= 1024 * 1024L) {
            val maxChunks = (totalBytes / (1024 * 1024L)).toInt().coerceAtLeast(1)
            return concurrency.coerceIn(1, 16).coerceAtMost(maxChunks)
        }
        return 1
    }
}

data class ChunkItem(
    val id: Long = 0,
    val downloadId: Long,
    val chunkIndex: Int,
    val startOffset: Long,
    val endOffset: Long,
    val downloadedBytes: Long = 0L,
    val status: ChunkStatus = ChunkStatus.PENDING,
    val retryCount: Int = 0
) {
    val totalSize: Long get() = (endOffset - startOffset + 1).coerceAtLeast(0)
    val progress: Float get() = if (totalSize > 0) downloadedBytes.toFloat() / totalSize.toFloat() else 0f
}
