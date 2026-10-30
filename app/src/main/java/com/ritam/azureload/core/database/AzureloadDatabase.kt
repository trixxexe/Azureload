package com.ritam.azureload.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ritam.azureload.core.database.dao.ChunkDao
import com.ritam.azureload.core.database.dao.DownloadDao
import com.ritam.azureload.core.database.entity.ChunkEntity
import com.ritam.azureload.core.database.entity.DownloadEntity

@Database(
    entities = [DownloadEntity::class, ChunkEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AzureloadDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun chunkDao(): ChunkDao

    companion object {
        @Volatile
        private var INSTANCE: AzureloadDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN speedLimitBytesPerSec INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AzureloadDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AzureloadDatabase::class.java,
                    "azureload_db"
                ).addMigrations(MIGRATION_1_2)
                 .fallbackToDestructiveMigration(true)
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
