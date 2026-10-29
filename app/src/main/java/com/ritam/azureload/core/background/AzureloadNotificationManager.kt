package com.ritam.azureload.core.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ritam.azureload.MainActivity
import com.ritam.azureload.R

object AzureloadNotificationManager {
    const val CHANNEL_ID = "azureload_active_downloads"
    const val NOTIFICATION_ID = 4040

    const val ACTION_PAUSE = "com.ritam.azureload.ACTION_PAUSE"
    const val ACTION_RESUME = "com.ritam.azureload.ACTION_RESUME"
    const val ACTION_CANCEL = "com.ritam.azureload.ACTION_CANCEL"
    const val EXTRA_DOWNLOAD_ID = "extra_download_id"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_downloads_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_downloads_desc)
                setShowBadge(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(
        context: Context,
        title: String = "Downloading files...",
        progressPercent: Int = 0,
        speedText: String = "",
        downloadId: Long = -1L
    ): Notification {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(if (speedText.isNotBlank()) "$progressPercent% • $speedText" else "$progressPercent%")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (progressPercent >= 0) {
            builder.setProgress(100, progressPercent, progressPercent == 0 && speedText.isBlank())
        }

        if (downloadId > 0) {
            // Pause action
            val pauseIntent = Intent(context, DownloadNotificationActionReceiver::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            val pausePendingIntent = PendingIntent.getBroadcast(
                context,
                (downloadId * 10 + 1).toInt(),
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)

            // Cancel action
            val cancelIntent = Intent(context, DownloadNotificationActionReceiver::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            val cancelPendingIntent = PendingIntent.getBroadcast(
                context,
                (downloadId * 10 + 2).toInt(),
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
        }

        return builder.build()
    }
}
