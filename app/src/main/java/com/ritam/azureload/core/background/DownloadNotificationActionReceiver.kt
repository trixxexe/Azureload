package com.ritam.azureload.core.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ritam.azureload.AzureloadApp

class DownloadNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val downloadId = intent.getLongExtra(AzureloadNotificationManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId <= 0) return

        val app = context.applicationContext as? AzureloadApp ?: return
        val coordinator = app.downloadCoordinator

        when (intent.action) {
            AzureloadNotificationManager.ACTION_PAUSE -> {
                coordinator.pauseDownload(downloadId)
            }
            AzureloadNotificationManager.ACTION_RESUME -> {
                coordinator.resumeDownload(downloadId)
            }
            AzureloadNotificationManager.ACTION_CANCEL -> {
                coordinator.cancelDownload(downloadId)
            }
        }
    }
}
