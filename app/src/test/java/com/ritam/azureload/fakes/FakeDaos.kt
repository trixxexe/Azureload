package com.ritam.azureload.fakes

import com.ritam.azureload.core.database.dao.ChunkDao
import com.ritam.azureload.core.database.dao.DownloadDao
import com.ritam.azureload.core.database.entity.ChunkEntity
import com.ritam.azureload.core.database.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class FakeDownloadDao : DownloadDao {
    private val storage = ConcurrentHashMap<Long, DownloadEntity>()
    private val nextId = AtomicLong(1L)
    private val stateFlow = MutableStateFlow<List<DownloadEntity>>(emptyList())

    private fun emitUpdate() {
        stateFlow.value = storage.values.sortedByDescending { it.createdAt }
    }

    override suspend fun insert(download: DownloadEntity): Long {
        val id = if (download.id > 0) download.id else nextId.getAndIncrement()
        val entity = download.copy(id = id)
        storage[id] = entity
        emitUpdate()
        return id
    }

    override suspend fun update(download: DownloadEntity) {
        storage[download.id] = download
        emitUpdate()
    }

    override fun getAllDownloads(): Flow<List<DownloadEntity>> = stateFlow

    override suspend fun getDownloadById(id: Long): DownloadEntity? = storage[id]

    override fun observeDownloadById(id: Long): Flow<DownloadEntity?> {
        return stateFlow.map { list -> list.firstOrNull { it.id == id } }
    }

    override suspend fun getActiveOrPendingDownloads(): List<DownloadEntity> {
        return storage.values.filter {
            it.status == "DOWNLOADING" || it.status == "QUEUED" || it.status == "DETECTING" || it.status == "RETRYING"
        }
    }

    override suspend fun updateProgress(id: Long, downloadedBytes: Long, updatedAt: Long) {
        val existing = storage[id] ?: return
        storage[id] = existing.copy(downloadedBytes = downloadedBytes, updatedAt = updatedAt)
        emitUpdate()
    }

    override suspend fun updateStatus(id: Long, status: String, errorMessage: String?, updatedAt: Long) {
        val existing = storage[id] ?: return
        storage[id] = existing.copy(status = status, errorMessage = errorMessage, updatedAt = updatedAt)
        emitUpdate()
    }

    override suspend fun updateSpeedLimit(id: Long, speedLimit: Long, updatedAt: Long) {
        val existing = storage[id] ?: return
        storage[id] = existing.copy(speedLimitBytesPerSec = speedLimit, updatedAt = updatedAt)
        emitUpdate()
    }

    override suspend fun getCompletedDownloads(): List<DownloadEntity> {
        return storage.values.filter { it.status == "COMPLETED" }
    }

    override suspend fun deleteCompleted(): Int {
        val toRemove = storage.values.filter { it.status == "COMPLETED" }.map { it.id }
        for (id in toRemove) {
            storage.remove(id)
        }
        emitUpdate()
        return toRemove.size
    }

    override suspend fun resumeAllPausedOrFailed(updatedAt: Long): Int {
        var count = 0
        for ((id, item) in storage) {
            if (item.status == "PAUSED" || item.status == "FAILED") {
                storage[id] = item.copy(status = "QUEUED", errorMessage = null, updatedAt = updatedAt)
                count++
            }
        }
        if (count > 0) emitUpdate()
        return count
    }

    override suspend fun deleteById(id: Long) {
        storage.remove(id)
        emitUpdate()
    }

    override suspend fun deleteAll() {
        storage.clear()
        emitUpdate()
    }
}

class FakeChunkDao : ChunkDao {
    private val storage = ConcurrentHashMap<Long, ChunkEntity>()
    private val nextId = AtomicLong(1L)
    private val stateFlow = MutableStateFlow<List<ChunkEntity>>(emptyList())

    private fun emitUpdate() {
        stateFlow.value = storage.values.sortedBy { it.chunkIndex }
    }

    override suspend fun insertAll(chunks: List<ChunkEntity>) {
        for (chunk in chunks) {
            val id = if (chunk.id > 0) chunk.id else nextId.getAndIncrement()
            storage[id] = chunk.copy(id = id)
        }
        emitUpdate()
    }

    override suspend fun update(chunk: ChunkEntity) {
        storage[chunk.id] = chunk
        emitUpdate()
    }

    override suspend fun getChunksForDownload(downloadId: Long): List<ChunkEntity> {
        return storage.values.filter { it.downloadId == downloadId }.sortedBy { it.chunkIndex }
    }

    override fun observeChunksForDownload(downloadId: Long): Flow<List<ChunkEntity>> {
        return stateFlow.map { list -> list.filter { it.downloadId == downloadId }.sortedBy { it.chunkIndex } }
    }

    override suspend fun updateChunkProgress(id: Long, downloadedBytes: Long, status: String) {
        val existing = storage[id] ?: return
        storage[id] = existing.copy(downloadedBytes = downloadedBytes, status = status)
        emitUpdate()
    }

    override suspend fun deleteForDownload(downloadId: Long) {
        storage.entries.removeIf { it.value.downloadId == downloadId }
        emitUpdate()
    }
}
