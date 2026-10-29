package com.ritam.azureload.core.engine

import android.content.Context
import android.util.Log
import com.ritam.azureload.core.background.DownloadForegroundService
import com.ritam.azureload.core.database.DownloadRepository
import com.ritam.azureload.core.model.DownloadItem
import com.ritam.azureload.core.model.DownloadStatus
import com.ritam.azureload.core.settings.SettingsManager
import com.ritam.azureload.core.storage.StagingFileManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class DownloadCoordinator(
    private val context: Context,
    private val repository: DownloadRepository,
    private val stagingFileManager: StagingFileManager,
    private val settingsManager: SettingsManager = SettingsManager(context)
) {
    private val tag = "DownloadCoordinator"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    private val _speeds = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val speeds: StateFlow<Map<Long, Long>> = _speeds.asStateFlow()

    init {
        scope.launch {
            recoverPendingDownloads()
        }
    }

    private suspend fun recoverPendingDownloads() {
        val pending = repository.getActiveOrPendingDownloads()
        for (item in pending) {
            if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.DETECTING) {
                // Interrupted by process death; set to PAUSED so user or coordinator can resume cleanly
                repository.updateDownloadStatus(item.id, DownloadStatus.PAUSED)
            }
        }
    }

    fun startDownload(downloadId: Long) {
        if (activeJobs.containsKey(downloadId)) {
            Log.d(tag, "Download $downloadId is already running.")
            return
        }

        // Check Wi-Fi constraint
        if (settingsManager.getWifiOnly() && !SettingsManager.isWifiConnected(context)) {
            Log.i(tag, "Download $downloadId postponed: Wi-Fi only mode active and not connected to Wi-Fi.")
            scope.launch {
                repository.updateDownloadStatus(downloadId, DownloadStatus.PAUSED, "Waiting for Wi-Fi connection")
            }
            return
        }

        // Check Max Active Transfers constraint
        val maxActive = settingsManager.getMaxActiveTransfers()
        if (activeJobs.size >= maxActive) {
            Log.i(tag, "Download $downloadId queued (active limit: $maxActive reached)")
            scope.launch {
                repository.updateDownloadStatus(downloadId, DownloadStatus.QUEUED)
            }
            return
        }

        DownloadForegroundService.startService(context)

        val job = scope.launch {
            try {
                val runner = DownloadJobRunner(
                    downloadId = downloadId,
                    repository = repository,
                    stagingFileManager = stagingFileManager,
                    onProgress = { id, downloaded, speed ->
                        _speeds.value = _speeds.value.toMutableMap().apply { put(id, speed) }
                        DownloadForegroundService.updateProgress(context, id, downloaded, speed)
                    }
                )
                runner.run()
            } catch (ce: CancellationException) {
                Log.i(tag, "Job for download $downloadId cancelled")
            } catch (e: Exception) {
                Log.e(tag, "Error running download $downloadId", e)
            } finally {
                activeJobs.remove(downloadId)
                _speeds.value = _speeds.value.toMutableMap().apply { remove(downloadId) }
                if (activeJobs.isEmpty()) {
                    DownloadForegroundService.stopService(context)
                }
                checkNextQueuedDownload()
            }
        }
        activeJobs[downloadId] = job
    }

    private fun checkNextQueuedDownload() {
        scope.launch {
            val maxActive = settingsManager.getMaxActiveTransfers()
            if (activeJobs.size < maxActive) {
                val pending = repository.getActiveOrPendingDownloads()
                val nextQueued = pending.firstOrNull { it.status == DownloadStatus.QUEUED && !activeJobs.containsKey(it.id) }
                if (nextQueued != null) {
                    startDownload(nextQueued.id)
                }
            }
        }
    }

    fun pauseDownload(downloadId: Long) {
        val job = activeJobs[downloadId]
        if (job != null) {
            job.cancel()
            activeJobs.remove(downloadId)
        }
        scope.launch {
            repository.updateDownloadStatus(downloadId, DownloadStatus.PAUSED)
            _speeds.value = _speeds.value.toMutableMap().apply { remove(downloadId) }
            if (activeJobs.isEmpty()) {
                DownloadForegroundService.stopService(context)
            }
            checkNextQueuedDownload()
        }
    }

    fun resumeDownload(downloadId: Long) {
        scope.launch {
            repository.updateDownloadStatus(downloadId, DownloadStatus.QUEUED)
            startDownload(downloadId)
        }
    }

    fun retryDownload(downloadId: Long) {
        resumeDownload(downloadId)
    }

    fun cancelDownload(downloadId: Long) {
        pauseDownload(downloadId)
        scope.launch {
            val item = repository.getDownloadById(downloadId)
            if (item != null && item.stagingPath.isNotBlank()) {
                stagingFileManager.cleanupStaging(File(item.stagingPath))
            }
            repository.deleteDownload(downloadId)
            checkNextQueuedDownload()
        }
    }

    fun onNetworkStateChanged() {
        scope.launch {
            if (settingsManager.getWifiOnly() && SettingsManager.isWifiConnected(context)) {
                // Resume queued transfers
                checkNextQueuedDownload()
            }
        }
    }

    fun isRunning(downloadId: Long): Boolean {
        return activeJobs[downloadId]?.isActive == true
    }

    fun getActiveJobCount(): Int = activeJobs.size
}
