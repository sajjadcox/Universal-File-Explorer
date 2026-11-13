package com.example.domain.model

import android.net.Uri

data class FileItem(
    val uri: Uri,
    val name: String,
    val path: String = "",
    val extension: String = "",
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val mimeType: String = "*/*",
    val isDirectory: Boolean = false,
    val isFavorite: Boolean = false,
    val childrenCount: Int? = null,
    val canWrite: Boolean = true,
    val canDelete: Boolean = true
) {
    val formattedSize: String
        get() {
            if (isDirectory) {
                return childrenCount?.let { "$it items" } ?: "Folder"
            }
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            if (digitGroups >= units.size) digitGroups = units.size - 1
            val value = size / Math.pow(1024.0, digitGroups.toDouble())
            return String.format("%.1f %s", value, units[digitGroups])
        }

    val formattedDate: String
        get() {
            if (lastModified <= 0) return "Unknown"
            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(lastModified))
        }
}
