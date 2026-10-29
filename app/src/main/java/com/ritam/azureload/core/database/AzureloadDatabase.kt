package com.ritam.azureload.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ritam.azureload.core.database.dao.ChunkDao
import com.ritam.azureload.core.database.dao.DownloadDao
import com.ritam.azureload.core.database.entity.ChunkEntity
import com.ritam.azureload.core.database.entity.DownloadEntity

@Database(
    entities = [DownloadEntity::class, ChunkEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AzureloadDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun chunkDao(): ChunkDao

    companion object {
        @Volatile
        private var INSTANCE: AzureloadDatabase? = null

        fun getInstance(context: Context): AzureloadDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AzureloadDatabase::class.java,
                    "azureload_db"
                ).fallbackToDestructiveMigration(false)
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
