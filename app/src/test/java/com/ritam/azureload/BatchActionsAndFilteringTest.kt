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
}
