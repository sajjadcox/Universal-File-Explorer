package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Archive
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.ColorApk
import com.example.ui.theme.ColorArchives
import com.example.ui.theme.ColorAudio
import com.example.ui.theme.ColorDocuments
import com.example.ui.theme.ColorEbooks
import com.example.ui.theme.ColorImages
import com.example.ui.theme.ColorOthers
import com.example.ui.theme.ColorVideos

object FileIconHelper {

    fun getIconForFile(isDirectory: Boolean, extension: String, mimeType: String): ImageVector {
        if (isDirectory) return Icons.Default.Folder

        val ext = extension.lowercase()
        val mime = mimeType.lowercase()

        return when {
            mime.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "svg") ->
                Icons.Default.Image
            mime.startsWith("video/") || ext in listOf("mp4", "mkv", "avi", "mov", "webm", "3gp") ->
                Icons.Default.VideoFile
            mime.startsWith("audio/") || ext in listOf("mp3", "wav", "m4a", "aac", "flac", "ogg", "opus") ->
                Icons.Default.AudioFile
            ext in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt") ||
                    mime.contains("pdf") || mime.contains("document") || mime.contains("sheet") || mime.startsWith("text/") ->
                Icons.Default.Description
            ext in listOf("zip", "rar", "7z", "tar", "gz", "bz2") || mime.contains("zip") || mime.contains("compressed") ->
                Icons.Default.Archive
            ext in listOf("apk", "xapk") || mime.contains("android.package-archive") ->
                Icons.Default.Android
            ext in listOf("epub", "mobi") || mime.contains("epub") ->
                Icons.Default.MenuBook
            else ->
                Icons.Default.InsertDriveFile
        }
    }

    fun getColorForFile(isDirectory: Boolean, extension: String, mimeType: String): Color {
        if (isDirectory) return Color(0xFFFFB300) // Folder Amber Gold

        val ext = extension.lowercase()
        val mime = mimeType.lowercase()

        return when {
            mime.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "svg") ->
                ColorImages
            mime.startsWith("video/") || ext in listOf("mp4", "mkv", "avi", "mov", "webm", "3gp") ->
                ColorVideos
            mime.startsWith("audio/") || ext in listOf("mp3", "wav", "m4a", "aac", "flac", "ogg", "opus") ->
                ColorAudio
            ext in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt") ||
                    mime.contains("pdf") || mime.contains("document") || mime.contains("sheet") || mime.startsWith("text/") ->
                ColorDocuments
            ext in listOf("zip", "rar", "7z", "tar", "gz", "bz2") || mime.contains("zip") || mime.contains("compressed") ->
                ColorArchives
            ext in listOf("apk", "xapk") || mime.contains("android.package-archive") ->
                ColorApk
            ext in listOf("epub", "mobi") || mime.contains("epub") ->
                ColorEbooks
            else ->
                ColorOthers
        }
    }
}
