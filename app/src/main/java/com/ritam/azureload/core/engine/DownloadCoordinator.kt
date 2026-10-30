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
    private val activeRunners = ConcurrentHashMap<Long, DownloadJobRunner>()
    private val lastServiceUpdate = ConcurrentHashMap<Long, Long>()
    private val lastTotalHistorySample = java.util.concurrent.atomic.AtomicLong(0L)

    private val _speeds = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val speeds: StateFlow<Map<Long, Long>> = _speeds.asStateFlow()

    private val _speedHistory = MutableStateFlow<Map<Long, List<Long>>>(emptyMap())
    val speedHistory: StateFlow<Map<Long, List<Long>>> = _speedHistory.asStateFlow()

    private val _totalSpeedHistory = MutableStateFlow<List<Long>>(emptyList())
    val totalSpeedHistory: StateFlow<List<Long>> = _totalSpeedHistory.asStateFlow()

    init {
        scope.launch {
            recoverPendingDownloads()
        }

        // React dynamically to Wi-Fi only preference changes
        scope.launch {
            settingsManager.wifiOnlyFlow.collect { wifiOnly ->
                if (wifiOnly && !SettingsManager.isWifiConnected(context)) {
                    Log.i(tag, "Wi-Fi only enabled while on cellular data: pausing active downloads.")
                    for ((id, job) in activeJobs) {
                        job.cancel()
                        repository.updateDownloadStatus(id, DownloadStatus.PAUSED, "Waiting for Wi-Fi connection")
                    }
                    activeJobs.clear()
                    _speeds.value = emptyMap()
                    DownloadForegroundService.stopService(context)
                } else {
                    checkNextQueuedDownload()
                }
            }
        }

        // React dynamically to max concurrent active transfers changes
        scope.launch {
            settingsManager.maxActiveTransfersFlow.collect {
                checkNextQueuedDownload()
            }
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
        checkNextQueuedDownload()
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
                    settingsManager = settingsManager,
                    onProgress = { id, downloaded, total, speed ->
                        val updatedSpeeds = _speeds.value.toMutableMap().apply { put(id, speed) }
                        _speeds.value = updatedSpeeds

                        // Record per-download speed history (keep last 30 samples)
                        val prevHistory = _speedHistory.value[id] ?: emptyList()
                        val nextHistory = (prevHistory + speed).takeLast(30)
                        _speedHistory.value = _speedHistory.value.toMutableMap().apply { put(id, nextHistory) }

                        // Record total aggregate speed history (throttled to at most once per 750ms)
                        val now = System.currentTimeMillis()
                        val lastSample = lastTotalHistorySample.get()
                        if (now - lastSample >= 750L) {
                            lastTotalHistorySample.set(now)
                            val aggregateSpeed = updatedSpeeds.values.sum()
                            _totalSpeedHistory.value = (_totalSpeedHistory.value + aggregateSpeed).takeLast(30)
                        }

                        val lastUpdate = lastServiceUpdate[id] ?: 0L
                        if (now - lastUpdate >= 750L) {
                            lastServiceUpdate[id] = now
                            DownloadForegroundService.updateProgress(context, id, downloaded, total, speed)
                        }
                    }
                )
                activeRunners[downloadId] = runner
                runner.run()
            } catch (ce: CancellationException) {
                Log.i(tag, "Job for download $downloadId cancelled")
            } catch (e: Exception) {
                Log.e(tag, "Error running download $downloadId", e)
            } finally {
                activeJobs.remove(downloadId)
                activeRunners.remove(downloadId)
                lastServiceUpdate.remove(downloadId)
                val updatedSpeeds = _speeds.value.toMutableMap().apply { remove(downloadId) }
                _speeds.value = updatedSpeeds
                val aggregateSpeed = updatedSpeeds.values.sum()
                _totalSpeedHistory.value = (_totalSpeedHistory.value + aggregateSpeed).takeLast(30)
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
            // Respect Wi-Fi only setting
            if (settingsManager.getWifiOnly() && !SettingsManager.isWifiConnected(context)) {
                return@launch
            }

            val maxActive = settingsManager.getMaxActiveTransfers()
            while (activeJobs.size < maxActive) {
                val pending = repository.getActiveOrPendingDownloads()
                val nextQueued = pending.firstOrNull { it.status == DownloadStatus.QUEUED && !activeJobs.containsKey(it.id) }
                if (nextQueued != null) {
                    startDownload(nextQueued.id)
                } else {
                    break
                }
            }
        }
    }

    fun pauseDownload(downloadId: Long) {
        val job = activeJobs.remove(downloadId)
        activeRunners.remove(downloadId)
        lastServiceUpdate.remove(downloadId)
        job?.cancel()
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
        _speedHistory.value = _speedHistory.value.toMutableMap().apply { remove(downloadId) }
        scope.launch {
            val item = repository.getDownloadById(downloadId)
            if (item != null && item.stagingPath.isNotBlank()) {
                stagingFileManager.cleanupStaging(File(item.stagingPath))
            }
            repository.deleteDownload(downloadId)
            checkNextQueuedDownload()
        }
    }

    fun pauseAll() {
        val jobsToCancel = activeJobs.values.toList()
        activeJobs.clear()
        activeRunners.clear()
        lastServiceUpdate.clear()
        _speeds.value = emptyMap()
        jobsToCancel.forEach { it.cancel() }
        scope.launch {
            val pending = repository.getActiveOrPendingDownloads()
            for (item in pending) {
                repository.updateDownloadStatus(item.id, DownloadStatus.PAUSED)
            }
            DownloadForegroundService.stopService(context)
        }
    }

    fun resumeAll() {
        scope.launch {
            repository.resumeAllPausedOrFailed()
            checkNextQueuedDownload()
        }
    }

    fun clearCompleted(deleteFilesFromDisk: Boolean = false) {
        scope.launch {
            repository.clearCompleted(deleteFilesFromDisk)
        }
    }

    fun updateSpeedLimit(downloadId: Long, limitBytesPerSec: Long) {
        activeRunners[downloadId]?.setSpeedLimit(limitBytesPerSec)
        scope.launch {
            repository.updateSpeedLimit(downloadId, limitBytesPerSec)
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
