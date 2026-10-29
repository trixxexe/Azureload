package com.ritam.azureload.core.background

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.ritam.azureload.R

class DownloadForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AzureloadNotificationManager.createNotificationChannel(this)
        val initialNotification = AzureloadNotificationManager.buildNotification(
            context = this,
            title = "Azureload active",
            progressPercent = 0,
            speedText = "Preparing transfers..."
        )

        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            AzureloadNotificationManager.NOTIFICATION_ID,
            initialNotification,
            foregroundType
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_STICKY

        val action = intent.action
        if (action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
        val downloaded = intent.getLongExtra(EXTRA_DOWNLOADED, 0L)
        val speed = intent.getLongExtra(EXTRA_SPEED, 0L)

        if (downloadId > 0) {
            val speedMb = speed.toDouble() / (1024.0 * 1024.0)
            val speedText = if (speedMb >= 0.1) String.format("%.2f MB/s", speedMb) else String.format("%d KB/s", speed / 1024)
            val notification = AzureloadNotificationManager.buildNotification(
                context = this,
                title = "Downloading file...",
                progressPercent = -1,
                speedText = speedText,
                downloadId = downloadId
            )
            try {
                NotificationManagerCompat.from(this).notify(AzureloadNotificationManager.NOTIFICATION_ID, notification)
            } catch (_: SecurityException) {}
        }

        return START_STICKY
    }

    companion object {
        private const val ACTION_UPDATE = "com.ritam.azureload.ACTION_UPDATE_PROGRESS"
        private const val ACTION_STOP = "com.ritam.azureload.ACTION_STOP_SERVICE"
        private const val EXTRA_DOWNLOAD_ID = "extra_download_id"
        private const val EXTRA_DOWNLOADED = "extra_downloaded"
        private const val EXTRA_SPEED = "extra_speed"

        fun startService(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateProgress(context: Context, downloadId: Long, downloadedBytes: Long, speedBytesPerSec: Long) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                putExtra(EXTRA_DOWNLOADED, downloadedBytes)
                putExtra(EXTRA_SPEED, speedBytesPerSec)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
