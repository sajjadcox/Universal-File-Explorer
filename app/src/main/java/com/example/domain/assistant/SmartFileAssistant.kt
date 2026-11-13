package com.example.domain.assistant

import android.content.Context
import com.example.domain.analyzer.ApkAndFileAnalyzer
import com.example.domain.engine.FileInspectionResult
import com.example.domain.engine.FileSignatureDetector
import com.example.domain.engine.FormatSupportStatus
import com.example.domain.engine.ViewerType
import com.example.domain.model.FileItem
import com.example.domain.model.StorageStats
import com.example.domain.security.FileSecurityReport
import com.example.domain.security.SecurityEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class SmartAssistantReport(
    val fileItem: FileItem,
    val inspection: FileInspectionResult,
    val securityReport: FileSecurityReport,
    val extensionExplanation: String,
    val recommendedViewerLabel: String,
    val whyCannotOpenExplanation: String?,
    val suggestedConversions: List<String>,
    val healthSummary: String
)

object SmartFileAssistant {

    suspend fun analyze(context: Context, fileItem: FileItem): SmartAssistantReport =
        withContext(Dispatchers.IO) {
            val inspection = FileSignatureDetector.inspectFile(context, fileItem)
            var apkInfo: com.example.domain.analyzer.ApkInspectionInfo? = null
            if (inspection.viewerType == ViewerType.APK) {
                val tmp = runCatching {
                    val f = File(context.cacheDir, "assist_${System.currentTimeMillis()}.${fileItem.extension.ifBlank { "apk" }}")
                    val inp = if (fileItem.uri.scheme == "file") File(fileItem.uri.path ?: "").inputStream()
                    else context.contentResolver.openInputStream(fileItem.uri)
                    inp?.use { src -> f.outputStream().use { dst -> src.copyTo(dst) } }
                    f
                }.getOrNull()
                if (tmp != null && tmp.exists()) {
                    apkInfo = ApkAndFileAnalyzer(context).inspectApkPackage(tmp, fileItem.extension).getOrNull()
                    tmp.delete()
                }
            }
            val securityReport = SecurityEngine.analyzeFileSecurity(fileItem, inspection, apkInfo)
            val ext = fileItem.extension.lowercase()

            val extDesc = explainExtension(ext, inspection)
            val viewerRec = recommendViewer(inspection)
            val openIssue = explainOpenIssue(inspection, securityReport)
            val conversions = suggestConversions(ext, inspection.viewerType)
            val health = buildHealthSummary(fileItem, inspection, securityReport)

            SmartAssistantReport(
                fileItem = fileItem,
                inspection = inspection,
                securityReport = securityReport,
                extensionExplanation = extDesc,
                recommendedViewerLabel = viewerRec,
                whyCannotOpenExplanation = openIssue,
                suggestedConversions = conversions,
                healthSummary = health
            )
        }

    fun explainExtension(ext: String, inspection: FileInspectionResult): String = when (ext) {
        "pdf" -> "Portable Document Format (.pdf): Fixed-layout vector & text document standard."
        "docx", "docm", "dotx" -> "Office Open XML Word document (.$ext): ZIP-packaged XML document containing paragraphs, tables, and embedded media."
        "doc" -> "Legacy Microsoft Word Binary (.doc): OLE2 compound binary document (pre-2007 format)."
        "xlsx", "xlsm" -> "Office Open XML Excel workbook (.$ext): Multi-sheet spreadsheet storing cells, formulas, and shared strings in XML."
        "xls" -> "Legacy Excel Binary (.xls): OLE2 binary spreadsheet format."
        "pptx", "ppsx" -> "PowerPoint Presentation (.$ext): OOXML slide deck with layouts, text, notes, and images."
        "odt", "ods", "odp" -> "OpenDocument Format (.$ext): OASIS open standard office archive."
        "csv", "tsv" -> "Delimited Tabular Data (.$ext): Plain-text rows separated by commas or tabs."
        "epub" -> "Electronic Publication (.epub): Standard reflowable ZIP/XHTML eBook container."
        "mobi", "azw", "azw3" -> "Kindle/PalmDOC eBook (.$ext): Legacy Amazon eBook container; DRM-encrypted variants are locked."
        "eml", "emlx", "msg" -> "Email Message (.$ext): Electronic mail message storing headers, body, and attachments."
        "jpg", "jpeg", "png", "webp", "heic", "avif", "gif", "bmp" -> "Raster Image (.$ext): Compressed or lossless bitmap photography/graphics."
        "svg" -> "Scalable Vector Graphics (.svg): XML-based resolution-independent 2D vector illustration."
        "dng", "cr2", "cr3", "nef", "arw", "raf" -> "Digital Camera RAW (.$ext): Unprocessed sensor image data paired with EXIF/TIFF metadata."
        "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma" -> "Digital Audio (.$ext): Lossy or lossless compressed/PCM audio stream."
        "mp4", "mkv", "mov", "webm", "avi", "3gp", "flv" -> "Digital Video Container (.$ext): Multiplexed video, audio, and optional subtitle tracks."
        "zip", "7z", "tar", "gz", "bz2", "xz", "cbz" -> "Compressed Archive (.$ext): Multi-file archive or comic book bundle."
        "rar", "cbr" -> "RAR Archive (.$ext): Proprietary Roshal archive format (header inspection only in offline FOSS builds)."
        "apk", "xapk", "apks", "aab" -> "Android App Package/Bundle (.$ext): Compiled Android application container and manifest."
        "db", "sqlite", "sqlite3" -> "SQLite Database (.$ext): Self-contained transactional relational SQL database file."
        "ttf", "otf" -> "Vector Font File (.$ext): Scalable TrueType or OpenType glyph typeface."
        "woff", "woff2" -> "Web Open Font Format (.$ext): Compressed font package intended for web browsers."
        "json", "xml", "yaml", "yml", "toml", "ini", "cfg" -> "Structured Configuration/Data (.$ext): Human-readable key-value or hierarchical text."
        "py", "js", "ts", "kt", "java", "c", "cpp", "h", "sql", "html", "md", "css" -> "Developer Source/Markup (.$ext): Plain-text source code or documentation."
        "ufev" -> "Universal Vault Encrypted File (.ufev): AES-256-GCM authenticated encrypted container created by this app."
        else -> "Format '${inspection.formatName}' (signature: ${inspection.signatureMatch})."
    }

    private fun recommendViewer(inspection: FileInspectionResult): String = when (inspection.viewerType) {
        ViewerType.PDF -> "Built-in Advanced PDF Reader & Editor (render, search, annotate, print)"
        ViewerType.OFFICE_WORD -> "Built-in Word Document Viewer (structure, tables, PDF export, TTS)"
        ViewerType.OFFICE_SHEET -> "Built-in Spreadsheet Grid Viewer (multi-sheet, formulas, CSV export)"
        ViewerType.OFFICE_SLIDE -> "Built-in Slide Presentation Viewer (cards, speaker notes, fullscreen)"
        ViewerType.EBOOK -> "Built-in eBook Reader (TOC, Sepia/Night themes, TTS, auto-scroll)"
        ViewerType.EMAIL -> "Built-in Email Parser (headers, body & attachment list)"
        ViewerType.IMAGE -> "Built-in Image Studio (zoom, EXIF, crop, rotate, convert to PDF)"
        ViewerType.AUDIO -> "Media3 Audio Player (background service, equalizer, sleep timer)"
        ViewerType.VIDEO -> "Media3 Video Player (subtitles, audio tracks, PiP, frame capture)"
        ViewerType.ARCHIVE -> "Built-in Archive Manager (tree browse, ZIP-Slip safe extract)"
        ViewerType.CODE_TEXT -> "Built-in Code & Text Editor (syntax highlight, JSON/XML/MD/HTML preview)"
        ViewerType.APK -> "Built-in APK Package & Permission Inspector"
        ViewerType.SQLITE_DB -> "Built-in Read-Only SQLite Table & Row Browser"
        ViewerType.FONT -> "Built-in Font Specimen Previewer"
        ViewerType.HEX_BINARY, ViewerType.UNSUPPORTED -> "Streaming Hex & Binary Inspector"
    }

    private fun explainOpenIssue(
        inspection: FileInspectionResult,
        securityReport: FileSecurityReport
    ): String? = when {
        securityReport.isExtensionMismatch ->
            "The file extension does not match its internal magic bytes (${inspection.signatureMatch}). Renaming to the true extension or inspecting in Hex mode is recommended."
        inspection.isPotentiallyCorrupted ->
            "The file is 0 bytes or its header signature appears corrupted/incomplete, which can prevent standard viewers from decoding it."
        inspection.isPasswordProtected ->
            "This file is encrypted or password-protected; enter the correct password when prompted."
        inspection.viewerType == ViewerType.ARCHIVE && inspection.supportStatus == FormatSupportStatus.INSPECT_ONLY ->
            "RAR/CBR decompression requires UnRAR-licensed code that is incompatible with permissive open-source distribution; header metadata is shown instead."
        inspection.supportStatus == FormatSupportStatus.PARTIAL ->
            inspection.supportNotes
        inspection.supportStatus == FormatSupportStatus.UNSUPPORTED || inspection.viewerType == ViewerType.HEX_BINARY ->
            "No specialized parser is registered for this binary format. You can inspect raw bytes in Hex mode or verify its checksum."
        else -> null
    }

    private fun suggestConversions(ext: String, viewerType: ViewerType): List<String> = when (viewerType) {
        ViewerType.IMAGE -> listOf("PNG", "JPG", "WEBP", "PDF")
        ViewerType.PDF -> listOf("PNG Images", "JPG Images", "Extracted Text / OCR")
        ViewerType.OFFICE_WORD, ViewerType.OFFICE_SLIDE -> listOf("PDF")
        ViewerType.OFFICE_SHEET -> listOf("CSV", "PDF")
        ViewerType.CODE_TEXT -> listOf("PDF", if (ext == "csv") "JSON" else "TXT")
        ViewerType.ARCHIVE -> if (ext in setOf("tar", "gz", "tgz", "bz2", "xz", "7z")) listOf("Repack to ZIP") else emptyList()
        ViewerType.AUDIO -> listOf("WAV (PCM)", "M4A (AAC)")
        else -> emptyList()
    }

    private fun buildHealthSummary(
        fileItem: FileItem,
        inspection: FileInspectionResult,
        security: FileSecurityReport
    ): String {
        val status = when {
            fileItem.size == 0L && !fileItem.isDirectory -> "Empty (0 bytes)"
            inspection.isPotentiallyCorrupted -> "Damaged Header"
            security.isExtensionMismatch -> "Extension Mismatch"
            inspection.isPasswordProtected -> "Encrypted / Locked"
            else -> "Healthy (${StorageStats.formatBytes(fileItem.size)})"
        }
        return "$status • ${inspection.supportStatus.label} • ${security.riskLevel.label}"
    }
}
