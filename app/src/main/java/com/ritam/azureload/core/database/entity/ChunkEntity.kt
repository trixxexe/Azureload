package com.ritam.azureload.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ritam.azureload.core.model.ChunkItem
import com.ritam.azureload.core.model.ChunkStatus

@Entity(
    tableName = "chunks",
    foreignKeys = [
        ForeignKey(
            entity = DownloadEntity::class,
            parentColumns = ["id"],
            childColumns = ["downloadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["downloadId", "chunkIndex"], unique = true)]
)
data class ChunkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val downloadId: Long,
    val chunkIndex: Int,
    val startOffset: Long,
    val endOffset: Long,
    val downloadedBytes: Long,
    val status: String,
    val retryCount: Int
) {
    fun toDomain(): ChunkItem {
        return ChunkItem(
            id = id,
            downloadId = downloadId,
            chunkIndex = chunkIndex,
            startOffset = startOffset,
            endOffset = endOffset,
            downloadedBytes = downloadedBytes,
            status = try { ChunkStatus.valueOf(status) } catch (_: Exception) { ChunkStatus.FAILED },
            retryCount = retryCount
        )
    }

    companion object {
        fun fromDomain(item: ChunkItem): ChunkEntity {
            return ChunkEntity(
                id = item.id,
                downloadId = item.downloadId,
                chunkIndex = item.chunkIndex,
                startOffset = item.startOffset,
                endOffset = item.endOffset,
                downloadedBytes = item.downloadedBytes,
                status = item.status.name,
                retryCount = item.retryCount
            )
        }
    }
}
