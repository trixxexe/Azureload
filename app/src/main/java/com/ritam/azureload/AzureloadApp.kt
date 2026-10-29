package com.ritam.azureload

import android.app.Application
import com.ritam.azureload.core.background.AzureloadNotificationManager
import com.ritam.azureload.core.database.AzureloadDatabase
import com.ritam.azureload.core.database.DownloadRepository
import com.ritam.azureload.core.engine.DownloadCoordinator
import com.ritam.azureload.core.storage.StagingFileManager

class AzureloadApp : Application() {

    lateinit var database: AzureloadDatabase
        private set

    lateinit var repository: DownloadRepository
        private set

    lateinit var stagingFileManager: StagingFileManager
        private set

    lateinit var downloadCoordinator: DownloadCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        database = AzureloadDatabase.getInstance(this)
        repository = DownloadRepository(database.downloadDao(), database.chunkDao())
        stagingFileManager = StagingFileManager(this)
        downloadCoordinator = DownloadCoordinator(this, repository, stagingFileManager)

        AzureloadNotificationManager.createNotificationChannel(this)
    }
}
