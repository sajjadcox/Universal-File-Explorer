package com.example.domain.model

enum class FileCategory(
    val title: String,
    val description: String,
    val extensions: Set<String>
) {
    DOCUMENTS(
        title = "Documents",
        description = "PDFs, Word docs, spreadsheets, slides, text",
        extensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "csv")
    ),
    IMAGES(
        title = "Images",
        description = "Photos, screenshots, graphics, wallpapers",
        extensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "svg")
    ),
    VIDEOS(
        title = "Videos",
        description = "Movies, clips, screen recordings",
        extensions = setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "wmv")
    ),
    AUDIO(
        title = "Audio",
        description = "Music, voice recordings, podcasts",
        extensions = setOf("mp3", "m4a", "wav", "aac", "flac", "ogg", "opus", "wma")
    ),
    ARCHIVES(
        title = "Archives",
        description = "ZIP, RAR, 7Z, TAR compressed bundles",
        extensions = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
    ),
    APPLICATIONS(
        title = "Applications",
        description = "Android APK installation packages",
        extensions = setOf("apk", "xapk", "apks")
    ),
    EBOOKS(
        title = "eBooks",
        description = "EPUB, PDF books, MOBI digital publications",
        extensions = setOf("epub", "mobi", "azw", "azw3", "fb2", "djvu", "pdf")
    ),
    DOWNLOADS(
        title = "Downloads",
        description = "Files received from browser and transfers",
        extensions = emptySet()
    ),
    OTHER(
        title = "Other Files",
        description = "Miscellaneous documents and binary files",
        extensions = emptySet()
    );

    fun matches(extension: String, mimeType: String): Boolean {
        val lowerExt = extension.lowercase()
        val lowerMime = mimeType.lowercase()
        return when (this) {
            DOCUMENTS -> extensions.contains(lowerExt) || lowerMime.startsWith("text/") || lowerMime.contains("pdf") || lowerMime.contains("document") || lowerMime.contains("sheet")
            IMAGES -> extensions.contains(lowerExt) || lowerMime.startsWith("image/")
            VIDEOS -> extensions.contains(lowerExt) || lowerMime.startsWith("video/")
            AUDIO -> extensions.contains(lowerExt) || lowerMime.startsWith("audio/")
            ARCHIVES -> extensions.contains(lowerExt) || lowerMime.contains("zip") || lowerMime.contains("compressed") || lowerMime.contains("tar")
            APPLICATIONS -> extensions.contains(lowerExt) || lowerMime.contains("vnd.android.package-archive")
            EBOOKS -> extensions.contains(lowerExt) || lowerMime.contains("epub")
            DOWNLOADS -> false // Identified primarily by directory path or download provider
            OTHER -> true
        }
    }
}
