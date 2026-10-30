package com.ritam.azureload.core.database

import com.ritam.azureload.core.database.dao.ChunkDao
import com.ritam.azureload.core.database.dao.DownloadDao
import com.ritam.azureload.core.database.entity.ChunkEntity
import com.ritam.azureload.core.database.entity.DownloadEntity
import com.ritam.azureload.core.model.ChunkItem
import com.ritam.azureload.core.model.ChunkStatus
import com.ritam.azureload.core.model.DownloadItem
import com.ritam.azureload.core.model.DownloadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DownloadRepository(
    private val downloadDao: DownloadDao,
    private val chunkDao: ChunkDao
) {
    fun getAllDownloads(): Flow<List<DownloadItem>> {
        return downloadDao.getAllDownloads().map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun getDownloadById(id: Long): DownloadItem? {
        return downloadDao.getDownloadById(id)?.toDomain()
    }

    fun observeDownloadById(id: Long): Flow<DownloadItem?> {
        return downloadDao.observeDownloadById(id).map { it?.toDomain() }
    }

    suspend fun insertDownload(item: DownloadItem): Long {
        return downloadDao.insert(DownloadEntity.fromDomain(item))
    }

    suspend fun updateDownload(item: DownloadItem) {
        downloadDao.update(DownloadEntity.fromDomain(item))
    }

    suspend fun updateDownloadStatus(id: Long, status: DownloadStatus, errorMessage: String? = null) {
        downloadDao.updateStatus(id, status.name, errorMessage)
    }

    suspend fun updateDownloadProgress(id: Long, downloadedBytes: Long) {
        downloadDao.updateProgress(id, downloadedBytes)
    }

    suspend fun updateSpeedLimit(id: Long, speedLimit: Long) {
        downloadDao.updateSpeedLimit(id, speedLimit)
    }

    suspend fun clearCompleted(deleteFilesFromDisk: Boolean = false): Int {
        val completed = downloadDao.getCompletedDownloads()
        for (item in completed) {
            if (deleteFilesFromDisk && !item.destinationPath.isNullOrBlank()) {
                try {
                    val file = java.io.File(item.destinationPath)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (_: Exception) {}
            }
            chunkDao.deleteForDownload(item.id)
        }
        return downloadDao.deleteCompleted()
    }

    suspend fun resumeAllPausedOrFailed(): Int {
        return downloadDao.resumeAllPausedOrFailed()
    }

    suspend fun deleteDownload(id: Long) {
        chunkDao.deleteForDownload(id)
        downloadDao.deleteById(id)
    }

    suspend fun saveChunks(downloadId: Long, chunks: List<ChunkItem>) {
        chunkDao.deleteForDownload(downloadId)
        chunkDao.insertAll(chunks.map { ChunkEntity.fromDomain(it) })
    }

    suspend fun getChunksForDownload(downloadId: Long): List<ChunkItem> {
        return chunkDao.getChunksForDownload(downloadId).map { it.toDomain() }
    }

    fun observeChunksForDownload(downloadId: Long): Flow<List<ChunkItem>> {
        return chunkDao.observeChunksForDownload(downloadId).map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun updateChunkProgress(chunkId: Long, downloadedBytes: Long, status: ChunkStatus) {
        chunkDao.updateChunkProgress(chunkId, downloadedBytes, status.name)
    }

    suspend fun getActiveOrPendingDownloads(): List<DownloadItem> {
        return downloadDao.getActiveOrPendingDownloads().map { it.toDomain() }
    }
}
