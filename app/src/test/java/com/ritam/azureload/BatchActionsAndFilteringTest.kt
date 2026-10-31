package com.ritam.azureload

import com.ritam.azureload.core.database.DownloadRepository
import com.ritam.azureload.core.model.DownloadItem
import com.ritam.azureload.core.model.DownloadStatus
import com.ritam.azureload.core.model.FileCategory
import com.ritam.azureload.core.model.getFileCategory
import com.ritam.azureload.fakes.FakeChunkDao
import com.ritam.azureload.fakes.FakeDownloadDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.ritam.azureload.feature.dashboard.formatBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BatchActionsAndFilteringTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testFileCategoryClassification() {
        val videoItem = DownloadItem(url = "https://example.com/video.mp4", destinationPath = "", stagingPath = "", fileName = "movie.mp4")
        assertEquals(FileCategory.MEDIA, videoItem.getFileCategory())

        val docItem = DownloadItem(url = "https://example.com/spec.pdf", destinationPath = "", stagingPath = "", fileName = "spec.pdf")
        assertEquals(FileCategory.DOCUMENTS, docItem.getFileCategory())

        val zipItem = DownloadItem(url = "https://example.com/archive.zip", destinationPath = "", stagingPath = "", fileName = "bundle.zip")
        assertEquals(FileCategory.ARCHIVES, zipItem.getFileCategory())

        val apkItem = DownloadItem(url = "https://example.com/app.apk", destinationPath = "", stagingPath = "", fileName = "app.apk")
        assertEquals(FileCategory.ARCHIVES, apkItem.getFileCategory())

        val otherItem = DownloadItem(url = "https://example.com/raw.bin", destinationPath = "", stagingPath = "", fileName = "firmware.bin")
        assertEquals(FileCategory.OTHER, otherItem.getFileCategory())
    }

    @Test
    fun testResumeAllPausedOrFailed() = runBlocking {
        val fakeDownloadDao = FakeDownloadDao()
        val fakeChunkDao = FakeChunkDao()
        val repository = DownloadRepository(fakeDownloadDao, fakeChunkDao)

        val id1 = repository.insertDownload(DownloadItem(url = "https://example.com/1", destinationPath = "", stagingPath = "", fileName = "1.bin", status = DownloadStatus.PAUSED))
        val id2 = repository.insertDownload(DownloadItem(url = "https://example.com/2", destinationPath = "", stagingPath = "", fileName = "2.bin", status = DownloadStatus.FAILED, errorMessage = "Network timeout"))
        val id3 = repository.insertDownload(DownloadItem(url = "https://example.com/3", destinationPath = "", stagingPath = "", fileName = "3.bin", status = DownloadStatus.COMPLETED))

        val resumedCount = repository.resumeAllPausedOrFailed()
        assertEquals(2, resumedCount)

        val item1 = repository.getDownloadById(id1)
        val item2 = repository.getDownloadById(id2)
        val item3 = repository.getDownloadById(id3)

        assertEquals(DownloadStatus.QUEUED, item1?.status)
        assertEquals(DownloadStatus.QUEUED, item2?.status)
        assertEquals(null, item2?.errorMessage)
        assertEquals(DownloadStatus.COMPLETED, item3?.status)
    }

    @Test
    fun testClearCompletedWithFileDeletion() = runBlocking {
        val fakeDownloadDao = FakeDownloadDao()
        val fakeChunkDao = FakeChunkDao()
        val repository = DownloadRepository(fakeDownloadDao, fakeChunkDao)

        val dummyFile = tempFolder.newFile("completed.zip")
        dummyFile.writeText("Completed payload")
        assertTrue(dummyFile.exists())

        val id1 = repository.insertDownload(
            DownloadItem(
                url = "https://example.com/completed.zip",
                destinationPath = dummyFile.absolutePath,
                stagingPath = "",
                fileName = "completed.zip",
                status = DownloadStatus.COMPLETED
            )
        )
        val id2 = repository.insertDownload(
            DownloadItem(
                url = "https://example.com/active.bin",
                destinationPath = "",
                stagingPath = "",
                fileName = "active.bin",
                status = DownloadStatus.DOWNLOADING
            )
        )

        val cleared = repository.clearCompleted(deleteFilesFromDisk = true)
        assertEquals(1, cleared)
        assertFalse("File should be deleted on disk", dummyFile.exists())

        val downloads = repository.getAllDownloads().first()
        assertEquals(1, downloads.size)
        assertEquals(id2, downloads[0].id)
    }

    @Test
    fun testUpdateSpeedLimit() = runBlocking {
        val fakeDownloadDao = FakeDownloadDao()
        val fakeChunkDao = FakeChunkDao()
        val repository = DownloadRepository(fakeDownloadDao, fakeChunkDao)

        val id = repository.insertDownload(
            DownloadItem(
                url = "https://example.com/file.iso",
                destinationPath = "",
                stagingPath = "",
                fileName = "file.iso",
                speedLimitBytesPerSec = 0L
            )
        )

        repository.updateSpeedLimit(id, 2L * 1024L * 1024L) // 2 MB/s
        val updated = repository.getDownloadById(id)
        assertEquals(2L * 1024L * 1024L, updated?.speedLimitBytesPerSec)
    }

    @Test
    fun testSearchFilteringByFilenameMimeAndStatus() {
        val list = listOf(
            DownloadItem(id = 1, url = "https://example.com/movie.mp4", destinationPath = "", stagingPath = "", fileName = "movie.mp4", mimeType = "video/mp4", status = DownloadStatus.DOWNLOADING),
            DownloadItem(id = 2, url = "https://example.com/document.pdf", destinationPath = "", stagingPath = "", fileName = "document.pdf", mimeType = "application/pdf", status = DownloadStatus.COMPLETED),
            DownloadItem(id = 3, url = "https://example.com/data.json", destinationPath = "", stagingPath = "", fileName = "data.json", mimeType = "application/json", status = DownloadStatus.FAILED),
            DownloadItem(id = 4, url = "https://example.com/music.mp3", destinationPath = "", stagingPath = "", fileName = "song.mp3", mimeType = "audio/mpeg", status = DownloadStatus.PAUSED)
        )

        fun search(query: String): List<DownloadItem> {
            val q = query.trim()
            if (q.isBlank()) return list
            return list.filter {
                it.fileName.contains(q, ignoreCase = true) ||
                it.url.contains(q, ignoreCase = true) ||
                (it.mimeType?.contains(q, ignoreCase = true) == true) ||
                it.status.name.contains(q, ignoreCase = true)
            }
        }

        // Search by MIME
        assertEquals(listOf(2L), search("application/pdf").map { it.id })
        assertEquals(listOf(1L), search("video").map { it.id })

        // Search by Status
        assertEquals(listOf(1L), search("DOWNLOADING").map { it.id })
        assertEquals(listOf(3L), search("failed").map { it.id })
        assertEquals(listOf(4L), search("paused").map { it.id })

        // Search by Filename
        assertEquals(listOf(4L), search("song").map { it.id })
    }

    @Test
    fun testPauseAllPendingDownloadsTransition() = runBlocking {
        val fakeDownloadDao = FakeDownloadDao()
        val fakeChunkDao = FakeChunkDao()
        val repository = DownloadRepository(fakeDownloadDao, fakeChunkDao)

        val id1 = repository.insertDownload(DownloadItem(url = "https://example.com/1", destinationPath = "", stagingPath = "", fileName = "1.bin", status = DownloadStatus.DOWNLOADING))
        val id2 = repository.insertDownload(DownloadItem(url = "https://example.com/2", destinationPath = "", stagingPath = "", fileName = "2.bin", status = DownloadStatus.QUEUED))
        val id3 = repository.insertDownload(DownloadItem(url = "https://example.com/3", destinationPath = "", stagingPath = "", fileName = "3.bin", status = DownloadStatus.DETECTING))

        // When coordinator pauses all, all active or pending must transition to PAUSED
        val pending = repository.getActiveOrPendingDownloads()
        assertEquals(3, pending.size)
        for (item in pending) {
            repository.updateDownloadStatus(item.id, DownloadStatus.PAUSED)
        }

        assertEquals(DownloadStatus.PAUSED, repository.getDownloadById(id1)?.status)
        assertEquals(DownloadStatus.PAUSED, repository.getDownloadById(id2)?.status)
        assertEquals(DownloadStatus.PAUSED, repository.getDownloadById(id3)?.status)

        // Verifies no queued jobs remain to be erroneously picked up
        val remainingPending = repository.getActiveOrPendingDownloads()
        assertEquals(0, remainingPending.size)
    }

    @Test
    fun testFormatBytesDefensiveHandling() {
        assertEquals("0 B", formatBytes(-1L))
        assertEquals("0 B", formatBytes(0L))
        assertEquals("512 B", formatBytes(512L))
        assertEquals("1.0 KB", formatBytes(1024L))
        assertEquals("1.5 MB", formatBytes((1.5 * 1024 * 1024).toLong()))
        assertEquals("2.00 GB", formatBytes((2.0 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun testSortingBySizeWithUnknownTotalBytes() {
        val item1 = DownloadItem(id = 1, url = "https://example.com/stream.bin", destinationPath = "", stagingPath = "", fileName = "stream.bin", totalBytes = -1L, downloadedBytes = 500L * 1024 * 1024)
        val item2 = DownloadItem(id = 2, url = "https://example.com/small.zip", destinationPath = "", stagingPath = "", fileName = "small.zip", totalBytes = 50L * 1024 * 1024, downloadedBytes = 50L * 1024 * 1024)
        val item3 = DownloadItem(id = 3, url = "https://example.com/tiny.txt", destinationPath = "", stagingPath = "", fileName = "tiny.txt", totalBytes = 1024L, downloadedBytes = 1024L)

        val list = listOf(item2, item1, item3)

        val sortedDesc = list.sortedWith { a, b ->
            val aSize = if (a.totalBytes > 0) a.totalBytes else a.downloadedBytes
            val bSize = if (b.totalBytes > 0) b.totalBytes else b.downloadedBytes
            bSize.compareTo(aSize)
        }

        assertEquals(listOf(1L, 2L, 3L), sortedDesc.map { it.id })
    }
}
