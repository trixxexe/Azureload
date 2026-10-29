package com.ritam.azureload.core.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ritam.azureload.AzureloadApp
import com.ritam.azureload.core.model.DownloadStatus

class DownloadRecoveryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? AzureloadApp ?: return Result.failure()
        val repository = app.repository
        val coordinator = app.downloadCoordinator

        val pending = repository.getActiveOrPendingDownloads()
        for (item in pending) {
            if (item.status == DownloadStatus.QUEUED) {
                coordinator.startDownload(item.id)
            }
        }
        return Result.success()
    }
}
