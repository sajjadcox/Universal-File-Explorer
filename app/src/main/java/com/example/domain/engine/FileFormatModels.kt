package com.example.domain.engine

enum class ViewerType {
    PDF,
    OFFICE_WORD,
    OFFICE_SHEET,
    OFFICE_SLIDE,
    IMAGE,
    AUDIO,
    VIDEO,
    ARCHIVE,
    CODE_TEXT,
    EBOOK,
    EMAIL,
    APK,
    SQLITE_DB,
    FONT,
    HEX_BINARY,
    UNSUPPORTED
}

enum class FormatSupportStatus(val label: String) {
    FULL("Full Support"),
    PARTIAL("Partial Support"),
    INSPECT_ONLY("Inspect Only"),
    UNSUPPORTED("Unsupported")
}

data class FileInspectionResult(
    val formatName: String,
    val detectedExtension: String,
    val detectedMimeType: String,
    val magicBytesHex: String,
    val signatureMatch: String,
    val viewerType: ViewerType,
    val supportStatus: FormatSupportStatus,
    val supportNotes: String,
    val isPasswordProtected: Boolean = false,
    val isPotentiallyCorrupted: Boolean = false,
    val isOversizedWarning: Boolean = false,
    val fileSizeBytes: Long = 0L
)
