package com.ritam.azureload.core.storage

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

class StagingFileManager(private val context: Context) {

    private val fileLocks = ConcurrentHashMap<String, Any>()

    private fun getLockFor(path: String): Any {
        return fileLocks.computeIfAbsent(path) { Any() }
    }

    fun getStagingDirectory(): File {
        val dir = File(context.filesDir, "staging")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun createStagingFile(downloadId: Long): File {
        val dir = getStagingDirectory()
        return File(dir, "azureload_$downloadId.part")
    }

    fun allocateStagingFile(stagingFile: File, totalBytes: Long) {
        if (totalBytes <= 0) return
        synchronized(getLockFor(stagingFile.absolutePath)) {
            RandomAccessFile(stagingFile, "rw").use { raf ->
                if (raf.length() < totalBytes) {
                    raf.setLength(totalBytes)
                }
            }
        }
    }

    fun writeChunkData(stagingFile: File, offset: Long, data: ByteArray, length: Int) {
        synchronized(getLockFor(stagingFile.absolutePath)) {
            RandomAccessFile(stagingFile, "rw").use { raf ->
                raf.seek(offset)
                raf.write(data, 0, length)
            }
        }
    }

    fun checkFreeSpace(requiredBytes: Long): Boolean {
        if (requiredBytes <= 0) return true
        val dir = getStagingDirectory()
        val usable = dir.usableSpace
        // Require at least requiredBytes + 50MB safety buffer
        return usable >= (requiredBytes + 50L * 1024 * 1024)
    }

    fun finalizeDownload(stagingFile: File, destinationFileName: String, customDestDir: File? = null): File {
        val targetDir = customDestDir ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        var destination = File(targetDir, destinationFileName)
        // Ensure no overwrite of existing completed file by appending index
        if (destination.exists()) {
            val baseName = destinationFileName.substringBeforeLast('.')
            val extension = destinationFileName.substringAfterLast('.', "")
            var counter = 1
            while (destination.exists()) {
                val newName = if (extension.isNotEmpty()) "$baseName($counter).$extension" else "$baseName($counter)"
                destination = File(targetDir, newName)
                counter++
            }
        }

        synchronized(getLockFor(stagingFile.absolutePath)) {
            val renamed = stagingFile.renameTo(destination)
            if (!renamed) {
                // Fallback copy
                FileInputStream(stagingFile).use { input ->
                    FileOutputStream(destination).use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                }
                stagingFile.delete()
            }
            fileLocks.remove(stagingFile.absolutePath)
        }
        return destination
    }

    fun cleanupStaging(stagingFile: File) {
        synchronized(getLockFor(stagingFile.absolutePath)) {
            if (stagingFile.exists()) {
                stagingFile.delete()
            }
            fileLocks.remove(stagingFile.absolutePath)
        }
    }
}
