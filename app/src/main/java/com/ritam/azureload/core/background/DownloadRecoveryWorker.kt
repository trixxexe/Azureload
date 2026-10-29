package com.ritam.azureload.core.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ritam.azureload.AzureloadApp
import com.ritam.azureload.core.model.DownloadStatus
import java.util.concurrent.TimeUnit

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

    companion object {
        private const val WORK_NAME = "azureload_recovery_work"

        fun schedule(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val workRequest = PeriodicWorkRequestBuilder<DownloadRecoveryWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
            } catch (_: Exception) {}
        }
    }
}
