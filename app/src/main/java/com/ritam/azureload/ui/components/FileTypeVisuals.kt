package com.ritam.azureload.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritam.azureload.core.model.ChunkItem
import com.ritam.azureload.core.model.ChunkStatus
import com.ritam.azureload.ui.theme.AzureError
import com.ritam.azureload.ui.theme.AzureSuccess
import com.ritam.azureload.ui.theme.AzureWarning

data class FileVisuals(
    val icon: ImageVector,
    val accentColor: Color,
    val categoryLabel: String
)

fun getFileVisuals(fileName: String, mimeType: String?): FileVisuals {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    val mime = mimeType?.lowercase().orEmpty()

    return when {
        mime.startsWith("video/") || ext in listOf("mp4", "mkv", "avi", "mov", "webm", "flv", "wmv", "3gp") -> {
            FileVisuals(Icons.Default.VideoFile, Color(0xFFE11D48), "Video")
        }
        mime.startsWith("audio/") || ext in listOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus") -> {
            FileVisuals(Icons.Default.AudioFile, Color(0xFF8B5CF6), "Audio")
        }
        mime.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "svg") -> {
            FileVisuals(Icons.Default.Image, Color(0xFFEC4899), "Image")
        }
        ext == "apk" || mime.contains("android.package-archive") -> {
            FileVisuals(Icons.Default.Android, Color(0xFF10B981), "Android App")
        }
        mime.contains("zip") || mime.contains("tar") || mime.contains("compressed") ||
        ext in listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso", "dmg") -> {
            FileVisuals(Icons.Default.FolderZip, Color(0xFFF59E0B), "Archive")
        }
        mime.contains("pdf") || ext == "pdf" -> {
            FileVisuals(Icons.Default.Description, Color(0xFFEF4444), "PDF Document")
        }
        mime.contains("document") || mime.contains("text") ||
        ext in listOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub", "csv") -> {
            FileVisuals(Icons.Default.Description, Color(0xFF3B82F6), "Document")
        }
        ext in listOf("kt", "java", "py", "c", "cpp", "js", "ts", "html", "css", "json", "xml", "sh") -> {
            FileVisuals(Icons.Default.Code, Color(0xFF06B6D4), "Source Code")
        }
        else -> {
            FileVisuals(Icons.Default.InsertDriveFile, Color(0xFF64748B), "File")
        }
    }
}

/**
 * Graphical segmented breakdown representing the byte range allocation of multi-connection chunks.
 */
@Composable
fun SegmentedChunkBar(
    chunks: List<ChunkItem>,
    modifier: Modifier = Modifier
) {
    if (chunks.isEmpty()) return

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
        ) {
            val totalSpan = chunks.sumOf { it.totalSize }.coerceAtLeast(1L).toFloat()
            var currentX = 0f
            val spacingPx = 2.dp.toPx()
            val totalSpacing = spacingPx * (chunks.size - 1).coerceAtLeast(0)
            val availableWidth = (size.width - totalSpacing).coerceAtLeast(0f)

            for (chunk in chunks) {
                val chunkRatio = (chunk.totalSize.toFloat() / totalSpan).coerceAtLeast(0.02f)
                val chunkWidth = availableWidth * chunkRatio
                val progress = chunk.progress.coerceIn(0f, 1f)

                // Background track of chunk
                drawRoundRect(
                    color = Color.DarkGray.copy(alpha = 0.35f),
                    topLeft = Offset(currentX, 0f),
                    size = Size(chunkWidth, size.height),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )

                // Filled portion of chunk
                if (progress > 0f) {
                    val fillWidth = chunkWidth * progress
                    val fillColor = when (chunk.status) {
                        ChunkStatus.COMPLETED -> AzureSuccess
                        ChunkStatus.FAILED -> AzureError
                        ChunkStatus.DOWNLOADING -> Color(0xFF00B4D8)
                        else -> AzureWarning
                    }
                    drawRoundRect(
                        color = fillColor,
                        topLeft = Offset(currentX, 0f),
                        size = Size(fillWidth, size.height),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }

                currentX += chunkWidth + spacingPx
            }
        }
    }
}
