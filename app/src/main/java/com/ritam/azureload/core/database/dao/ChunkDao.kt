package com.ritam.azureload.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ritam.azureload.core.database.entity.ChunkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChunkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<ChunkEntity>)

    @Update
    suspend fun update(chunk: ChunkEntity)

    @Query("SELECT * FROM chunks WHERE downloadId = :downloadId ORDER BY chunkIndex ASC")
    suspend fun getChunksForDownload(downloadId: Long): List<ChunkEntity>

    @Query("SELECT * FROM chunks WHERE downloadId = :downloadId ORDER BY chunkIndex ASC")
    fun observeChunksForDownload(downloadId: Long): Flow<List<ChunkEntity>>

    @Query("UPDATE chunks SET downloadedBytes = :downloadedBytes, status = :status WHERE id = :id")
    suspend fun updateChunkProgress(id: Long, downloadedBytes: Long, status: String)

    @Query("DELETE FROM chunks WHERE downloadId = :downloadId")
    suspend fun deleteForDownload(downloadId: Long)
}
