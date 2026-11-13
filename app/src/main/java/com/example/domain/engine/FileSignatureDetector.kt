package com.example.domain.engine

import android.content.Context
import android.net.Uri
import com.example.domain.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream

object FileSignatureDetector {

    private const val LARGE_FILE_THRESHOLD_BYTES = 100L * 1024L * 1024L // 100 MB

    suspend fun inspectFile(context: Context, fileItem: FileItem): FileInspectionResult =
        withContext(Dispatchers.IO) {
            val ext = fileItem.extension.ifBlank {
                fileItem.name.substringAfterLast('.', "")
            }.lowercase(Locale.ROOT)
            val declaredMime = fileItem.mimeType.lowercase(Locale.ROOT)

            val headerBytes = readHeaderBytes(context, fileItem.uri, 512)
            val magicHex = headerBytes.take(16).joinToString(" ") { "%02X".format(it) }
            val isEmpty = fileItem.size == 0L && headerBytes.isEmpty() && !fileItem.isDirectory
            val isOversized = fileItem.size > LARGE_FILE_THRESHOLD_BYTES

            val (sigMatch, detectedFormat, baseMime) = identifyMagicSignature(headerBytes, ext, declaredMime)
            val (viewerType, supportStatus, notes) = resolveViewerAndSupport(
                ext = ext,
                sigMatch = sigMatch,
                detectedMime = baseMime,
                headerBytes = headerBytes
            )

            // Quick corruption/encryption heuristics without loading whole file
            var isEncrypted = false
            var isCorrupted = isEmpty
            if (viewerType == ViewerType.PDF && headerBytes.isNotEmpty()) {
                val headStr = String(headerBytes, Charsets.ISO_8859_1)
                if (!headStr.contains("%PDF-")) {
                    isCorrupted = true
                }
            } else if (sigMatch == "ZIP/Container" && (ext == "zip" || ext == "cbz")) {
                // Check ZIP general purpose bit flag bit 0 (encrypted) in local file header offset +6
                if (headerBytes.size >= 8) {
                    val flags = (headerBytes[6].toInt() and 0xFF) or ((headerBytes[7].toInt() and 0xFF) shl 8)
                    if ((flags and 0x01) != 0) {
                        isEncrypted = true
                    }
                }
            }

            // Refine OOXML/ODF/APK/EPUB inside ZIP containers
            var refinedViewer = viewerType
            var refinedFormat = detectedFormat
            var refinedStatus = supportStatus
            var refinedNotes = notes
            if (sigMatch == "ZIP/Container" && ext.isBlank()) {
                val zipKind = peekZipContainerType(context, fileItem.uri)
                if (zipKind != null) {
                    refinedViewer = zipKind.first
                    refinedFormat = zipKind.second
                    refinedStatus = FormatSupportStatus.FULL
                    refinedNotes = "Detected inside ZIP container structure."
                }
            }

            FileInspectionResult(
                formatName = refinedFormat,
                detectedExtension = ext,
                detectedMimeType = baseMime,
                magicBytesHex = magicHex.ifBlank { "N/A (empty or unreadable)" },
                signatureMatch = sigMatch,
                viewerType = refinedViewer,
                supportStatus = refinedStatus,
                supportNotes = refinedNotes,
                isPasswordProtected = isEncrypted,
                isPotentiallyCorrupted = isCorrupted,
                isOversizedWarning = isOversized,
                fileSizeBytes = fileItem.size
            )
        }

    fun readHeaderBytes(context: Context, uri: Uri, maxBytes: Int = 512): ByteArray {
        return try {
            val stream: InputStream? = if (uri.scheme == "file") {
                val file = File(uri.path ?: "")
                if (file.exists() && file.canRead()) file.inputStream() else null
            } else {
                context.contentResolver.openInputStream(uri)
            }
            stream?.use { input ->
                val buf = ByteArray(maxBytes)
                val read = input.read(buf)
                if (read > 0) buf.copyOf(read) else ByteArray(0)
            } ?: ByteArray(0)
        } catch (_: Exception) {
            ByteArray(0)
        }
    }

    private fun peekZipContainerType(context: Context, uri: Uri): Pair<ViewerType, String>? {
        return try {
            val stream = if (uri.scheme == "file") File(uri.path ?: "").inputStream()
            else context.contentResolver.openInputStream(uri)
            stream?.use { raw ->
                ZipInputStream(raw).use { zis ->
                    var count = 0
                    while (count < 25) {
                        val entry = zis.nextEntry ?: break
                        val n = entry.name
                        when {
                            n == "AndroidManifest.xml" -> return ViewerType.APK to "Android Package (APK)"
                            n == "word/document.xml" -> return ViewerType.OFFICE_WORD to "Word Document (DOCX)"
                            n == "xl/workbook.xml" -> return ViewerType.OFFICE_SHEET to "Excel Workbook (XLSX)"
                            n == "ppt/presentation.xml" -> return ViewerType.OFFICE_SLIDE to "PowerPoint (PPTX)"
                            n == "META-INF/container.xml" -> return ViewerType.EBOOK to "EPUB eBook"
                        }
                        count++
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun identifyMagicSignature(
        bytes: ByteArray,
        ext: String,
        declaredMime: String
    ): Triple<String, String, String> {
        if (bytes.isEmpty()) {
            return Triple("Empty/Unreadable", ext.uppercase().ifBlank { "Unknown" }, declaredMime)
        }

        val asciiPrefix = String(bytes.take(32).toByteArray(), Charsets.ISO_8859_1)

        // PDF
        if (asciiPrefix.startsWith("%PDF-")) {
            return Triple("PDF (%PDF-)", "PDF Document", "application/pdf")
        }
        // PNG
        if (bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) {
            return Triple("PNG", "PNG Image", "image/png")
        }
        // JPEG
        if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
            return Triple("JPEG (FFD8FF)", "JPEG Image", "image/jpeg")
        }
        // GIF
        if (asciiPrefix.startsWith("GIF87a") || asciiPrefix.startsWith("GIF89a")) {
            return Triple("GIF89a/87a", "Animated GIF Image", "image/gif")
        }
        // BMP
        if (bytes.size >= 2 && bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte()) {
            return Triple("BMP (BM)", "Bitmap Image", "image/bmp")
        }
        // WEBP / WAV / AVI (RIFF)
        if (asciiPrefix.startsWith("RIFF") && bytes.size >= 12) {
            val sub = String(bytes.sliceArray(8..11), Charsets.ISO_8859_1)
            return when (sub) {
                "WEBP" -> Triple("RIFF/WEBP", "WebP Image", "image/webp")
                "WAVE" -> Triple("RIFF/WAVE", "WAV Audio", "audio/wav")
                "AVI " -> Triple("RIFF/AVI", "AVI Video", "video/x-msvideo")
                else -> Triple("RIFF/$sub", "RIFF Container ($sub)", declaredMime)
            }
        }
        // TIFF (II* or MM*)
        if (bytes.size >= 4 && (
                    (bytes[0] == 0x49.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x2A.toByte() && bytes[3] == 0x00.toByte()) ||
                    (bytes[0] == 0x4D.toByte() && bytes[1] == 0x4D.toByte() && bytes[2] == 0x00.toByte() && bytes[3] == 0x2A.toByte())
                )
        ) {
            val isRaw = ext in setOf("dng", "cr2", "nef", "arw", "raf")
            return if (isRaw) {
                Triple("TIFF/RAW", "Camera RAW (${ext.uppercase()})", "image/x-${ext}")
            } else {
                Triple("TIFF", "TIFF Multi-page Image", "image/tiff")
            }
        }
        // ISO BMFF (MP4 / MOV / HEIC / AVIF / CR3)
        if (bytes.size >= 12 && String(bytes.sliceArray(4..7), Charsets.ISO_8859_1) == "ftyp") {
            val brand = String(bytes.sliceArray(8..11), Charsets.ISO_8859_1).trim()
            return when {
                brand.startsWith("heic") || brand.startsWith("heix") || brand.startsWith("mif1") ->
                    Triple("ISOBMFF ($brand)", "HEIC/HEIF Image", "image/heic")
                brand.startsWith("avif") ->
                    Triple("ISOBMFF (avif)", "AVIF Image", "image/avif")
                brand.startsWith("crx") ->
                    Triple("ISOBMFF (CR3)", "Canon CR3 RAW", "image/x-canon-cr3")
                brand.startsWith("M4A") ->
                    Triple("ISOBMFF (M4A)", "M4A Audio", "audio/mp4")
                brand.startsWith("qt") ->
                    Triple("ISOBMFF (QuickTime)", "QuickTime MOV Video", "video/quicktime")
                else ->
                    Triple("ISOBMFF ($brand)", "MP4/BMFF Media", if (ext == "m4a") "audio/mp4" else "video/mp4")
            }
        }
        // ZIP-based (ZIP, DOCX, XLSX, PPTX, ODT, ODS, ODP, EPUB, APK, XAPK, APKS, AAB, CBZ)
        if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            ((bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()) ||
                    (bytes[2] == 0x05.toByte() && bytes[3] == 0x06.toByte()))
        ) {
            val desc = when (ext) {
                "docx", "docm", "dotx" -> "Word OOXML (${ext.uppercase()})"
                "xlsx", "xlsm" -> "Excel OOXML (${ext.uppercase()})"
                "pptx", "ppsx" -> "PowerPoint OOXML (${ext.uppercase()})"
                "odt" -> "OpenDocument Text (ODT)"
                "ods" -> "OpenDocument Spreadsheet (ODS)"
                "odp" -> "OpenDocument Presentation (ODP)"
                "epub" -> "EPUB eBook"
                "apk" -> "Android APK"
                "xapk", "apks" -> "Split APK Bundle (${ext.uppercase()})"
                "aab" -> "Android App Bundle (AAB)"
                "cbz" -> "Comic Book ZIP (CBZ)"
                else -> "ZIP Archive"
            }
            return Triple("ZIP/Container", desc, declaredMime)
        }
        // RAR
        if (bytes.size >= 6 && asciiPrefix.startsWith("Rar!")) {
            val isRar5 = bytes.size >= 7 && bytes[6] == 0x01.toByte()
            return Triple(if (isRar5) "RAR5" else "RAR4", if (isRar5) "RAR5 Archive" else "RAR4 Archive", "application/vnd.rar")
        }
        // 7Z
        if (bytes.size >= 6 && bytes[0] == 0x37.toByte() && bytes[1] == 0x7A.toByte() &&
            bytes[2] == 0xBC.toByte() && bytes[3] == 0xAF.toByte() &&
            bytes[4] == 0x27.toByte() && bytes[5] == 0x1C.toByte()
        ) {
            return Triple("7Z", "7-Zip Archive", "application/x-7z-compressed")
        }
        // GZIP
        if (bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) {
            return Triple("GZIP", "GZIP Compressed Archive", "application/gzip")
        }
        // BZIP2
        if (asciiPrefix.startsWith("BZh")) {
            return Triple("BZIP2", "BZIP2 Compressed Archive", "application/x-bzip2")
        }
        // XZ
        if (bytes.size >= 6 && bytes[0] == 0xFD.toByte() && bytes[1] == 0x37.toByte() &&
            bytes[2] == 0x7A.toByte() && bytes[3] == 0x58.toByte() && bytes[4] == 0x5A.toByte()
        ) {
            return Triple("XZ", "XZ Compressed Archive", "application/x-xz")
        }
        // TAR (ustar at offset 257)
        if (bytes.size >= 263 && String(bytes.sliceArray(257..261), Charsets.ISO_8859_1) == "ustar") {
            return Triple("TAR (ustar)", "POSIX TAR Archive", "application/x-tar")
        }
        // MKV / WEBM (EBML)
        if (bytes.size >= 4 && bytes[0] == 0x1A.toByte() && bytes[1] == 0x45.toByte() &&
            bytes[2] == 0xDF.toByte() && bytes[3] == 0xA3.toByte()
        ) {
            return Triple("EBML (MKV/WEBM)", if (ext == "webm") "WebM Video" else "Matroska MKV Video", if (ext == "webm") "video/webm" else "video/x-matroska")
        }
        // FLV
        if (asciiPrefix.startsWith("FLV")) {
            return Triple("FLV", "Flash Video", "video/x-flv")
        }
        // MP3 (ID3 or sync frame)
        if (asciiPrefix.startsWith("ID3") || (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && (bytes[1].toInt() and 0xE0) == 0xE0)) {
            return Triple("MP3/MPEG", "MP3 Audio", "audio/mpeg")
        }
        // FLAC
        if (asciiPrefix.startsWith("fLaC")) {
            return Triple("FLAC", "FLAC Lossless Audio", "audio/flac")
        }
        // OGG / OPUS
        if (asciiPrefix.startsWith("OggS")) {
            return Triple("OggS", if (ext == "opus") "Opus Audio" else "Ogg Vorbis/Audio", "audio/ogg")
        }
        // SQLite
        if (asciiPrefix.startsWith("SQLite format 3")) {
            return Triple("SQLite 3", "SQLite Database", "application/vnd.sqlite3")
        }
        // OLE2 Compound Document (DOC, XLS, PPT, MSG, WMA)
        if (bytes.size >= 8 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() &&
            bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()
        ) {
            return Triple("OLE2 Compound", "Legacy Office / OLE2 (${ext.uppercase()})", declaredMime)
        }
        // RTF
        if (asciiPrefix.startsWith("{\\rtf")) {
            return Triple("RTF", "Rich Text Format", "application/rtf")
        }
        // Fonts
        if (bytes.size >= 4 && bytes[0] == 0x00.toByte() && bytes[1] == 0x01.toByte() && bytes[2] == 0x00.toByte() && bytes[3] == 0x00.toByte()) {
            return Triple("TrueType (TTF)", "TrueType Font", "font/ttf")
        }
        if (asciiPrefix.startsWith("OTTO")) {
            return Triple("OpenType (OTF)", "OpenType Font", "font/otf")
        }
        if (asciiPrefix.startsWith("wOFF")) {
            return Triple("WOFF", "Web Open Font Format", "font/woff")
        }
        if (asciiPrefix.startsWith("wOF2")) {
            return Triple("WOFF2", "Web Open Font Format 2", "font/woff2")
        }
        // PalmDOC / MOBI
        if (bytes.size >= 68 && String(bytes.sliceArray(60..67), Charsets.ISO_8859_1).contains("BOOKMOBI")) {
            return Triple("PalmDOC/MOBI", "Kindle MOBI/AZW eBook", "application/x-mobipocket-ebook")
        }
        // SVG / XML / HTML
        val trimmedText = asciiPrefix.trimStart()
        if (trimmedText.startsWith("<svg", ignoreCase = true) ||
            (trimmedText.startsWith("<?xml") && ext == "svg")
        ) {
            return Triple("SVG/XML", "Scalable Vector Graphic", "image/svg+xml")
        }
        if (trimmedText.startsWith("<!DOCTYPE html", ignoreCase = true) || trimmedText.startsWith("<html", ignoreCase = true)) {
            return Triple("HTML", "HTML Document", "text/html")
        }
        if (trimmedText.startsWith("<?xml", ignoreCase = true)) {
            return Triple("XML", "XML Document", "application/xml")
        }
        // Plain UTF-8 / ASCII text check
        if (isMostlyPrintableText(bytes)) {
            return Triple("UTF-8/ASCII Text", "${ext.uppercase().ifBlank { "Plain" }} Text", declaredMime.ifBlank { "text/plain" })
        }

        return Triple("Binary/Unknown", ext.uppercase().ifBlank { "Binary File" }, declaredMime.ifBlank { "application/octet-stream" })
    }

    private fun isMostlyPrintableText(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return true
        var controlCount = 0
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v == 0) return false // NUL byte strongly indicates binary
            if (v < 0x09 || (v in 0x0E..0x1F && v != 0x1B)) {
                controlCount++
            }
        }
        return controlCount.toFloat() / bytes.size < 0.05f
    }

    private fun resolveViewerAndSupport(
        ext: String,
        sigMatch: String,
        detectedMime: String,
        headerBytes: ByteArray
    ): Triple<ViewerType, FormatSupportStatus, String> {
        return when {
            sigMatch.startsWith("PDF") || ext == "pdf" ->
                Triple(ViewerType.PDF, FormatSupportStatus.FULL, "Full rendering, search, annotations, and page tools via PdfRenderer & PDFBox.")

            ext in setOf("docx", "docm", "dotx", "odt") ->
                Triple(ViewerType.OFFICE_WORD, FormatSupportStatus.FULL, "Streaming OOXML/ODF parser with tables, headings, embedded images, search & PDF export.")

            ext == "rtf" || sigMatch == "RTF" ->
                Triple(ViewerType.OFFICE_WORD, FormatSupportStatus.PARTIAL, "Extracts text and basic structure from RTF documents.")

            ext == "doc" || (sigMatch == "OLE2 Compound" && ext in setOf("doc", "xls", "ppt")) -> {
                val type = when (ext) {
                    "xls" -> ViewerType.OFFICE_SHEET
                    "ppt" -> ViewerType.OFFICE_SLIDE
                    else -> ViewerType.OFFICE_WORD
                }
                Triple(type, FormatSupportStatus.PARTIAL, "Legacy OLE2 binary format: best-effort text stream extraction. Full binary layout requires conversion to OOXML.")
            }

            ext in setOf("xlsx", "xlsm", "ods", "csv", "tsv") ->
                Triple(ViewerType.OFFICE_SHEET, FormatSupportStatus.FULL, "Multi-sheet navigation, formula display, sorting, filtering, CSV & PDF export.")

            ext in setOf("pptx", "ppsx", "odp") ->
                Triple(ViewerType.OFFICE_SLIDE, FormatSupportStatus.FULL, "Slide deck rendering, thumbnails, full-screen presentation & PDF export.")

            ext in setOf("epub") ->
                Triple(ViewerType.EBOOK, FormatSupportStatus.FULL, "EPUB chapter reader with Table of Contents, metadata & reading progress.")

            ext in setOf("mobi", "azw", "azw3", "pdb") || sigMatch == "PalmDOC/MOBI" ->
                Triple(ViewerType.EBOOK, FormatSupportStatus.PARTIAL, "PalmDOC header & unencrypted text inspection. DRM-protected Kindle formats are locked.")

            ext in setOf("eml", "emlx") ->
                Triple(ViewerType.EMAIL, FormatSupportStatus.FULL, "RFC-822 header, body, and attachment parser.")

            ext == "msg" ->
                Triple(ViewerType.EMAIL, FormatSupportStatus.PARTIAL, "Outlook MSG uses OLE2 compound storage; extracts readable UTF-16LE/ASCII text & headers.")

            ext in setOf("jpg", "jpeg", "png", "gif", "webp", "svg", "bmp", "heic", "heif", "avif") ||
                    detectedMime.startsWith("image/") && ext !in setOf("tiff", "tif", "dng", "cr2", "cr3", "nef", "arw", "raf") ->
                Triple(ViewerType.IMAGE, FormatSupportStatus.FULL, "High-res zoom, GIF/SVG/HEIC/AVIF decoding, EXIF inspection, crop/rotate/resize/convert.")

            ext in setOf("tiff", "tif") || sigMatch == "TIFF" ->
                Triple(ViewerType.IMAGE, FormatSupportStatus.PARTIAL, "Reads TIFF IFD metadata & page count; decodes device-supported TIFF streams.")

            ext in setOf("dng", "cr2", "cr3", "nef", "arw", "raf") ->
                Triple(ViewerType.IMAGE, FormatSupportStatus.PARTIAL, "DNG and embedded JPEG/EXIF preview supported; proprietary RAW demosaicing depends on Android OEM codecs.")

            ext in setOf("mp3", "wav", "flac", "aac", "m4a", "ogg", "opus") || detectedMime.startsWith("audio/") ->
                Triple(ViewerType.AUDIO, FormatSupportStatus.FULL, "Media3 ExoPlayer audio playback, background MediaSession, speed, trim & metadata.")

            ext == "wma" ->
                Triple(ViewerType.AUDIO, FormatSupportStatus.PARTIAL, "WMA relies on device FFmpeg/OEM codecs; inspectable if hardware decoder is missing.")

            ext in setOf("mp4", "mkv", "avi", "mov", "webm", "3gp") || detectedMime.startsWith("video/") ->
                Triple(ViewerType.VIDEO, FormatSupportStatus.FULL, "Media3 hardware-accelerated playback, subtitles, speed, PiP, gestures & screenshot capture.")

            ext == "flv" || sigMatch == "FLV" ->
                Triple(ViewerType.VIDEO, FormatSupportStatus.PARTIAL, "FLV container compatibility depends on device codec support.")

            ext in setOf("zip", "7z", "tar", "gz", "tgz", "bz2", "xz", "cbz") ->
                Triple(ViewerType.ARCHIVE, FormatSupportStatus.FULL, "Preview, selective extraction with ZIP Slip & Bomb protection, password ZIP & CBZ comic reader.")

            ext in setOf("rar", "cbr") || sigMatch.startsWith("RAR") ->
                Triple(ViewerType.ARCHIVE, FormatSupportStatus.INSPECT_ONLY, "Identifies RAR4/RAR5 headers; full RAR extraction requires proprietary UnRAR-licensed code excluded for license compliance.")

            ext in setOf("apk", "xapk", "apks", "aab") ->
                Triple(ViewerType.APK, FormatSupportStatus.FULL, "Manifest, SDK levels, permissions, certificates, SHA-256 & Android installer handoff.")

            ext in setOf("db", "sqlite", "sqlite3") || sigMatch == "SQLite 3" ->
                Triple(ViewerType.SQLITE_DB, FormatSupportStatus.FULL, "Read-only SQLite table schema & paginated row browser.")

            ext in setOf("ttf", "otf") || sigMatch in setOf("TrueType (TTF)", "OpenType (OTF)") ->
                Triple(ViewerType.FONT, FormatSupportStatus.FULL, "Live font specimen preview at multiple weights & sizes.")

            ext in setOf("woff", "woff2") ->
                Triple(ViewerType.FONT, FormatSupportStatus.INSPECT_ONLY, "Web font metadata inspection; Android Typeface renders TTF/OTF natively.")

            ext in setOf(
                "txt", "log", "json", "xml", "html", "htm", "yaml", "yml", "toml", "ini", "cfg",
                "py", "js", "ts", "jsx", "tsx", "java", "kt", "kts", "c", "cpp", "h", "hpp",
                "php", "sql", "css", "scss", "md", "sh", "gradle", "properties"
            ) || sigMatch == "UTF-8/ASCII Text" ->
                Triple(ViewerType.CODE_TEXT, FormatSupportStatus.FULL, "Syntax highlighting, line numbers, search, JSON/XML validation, Markdown & safe HTML preview, text edit & diff.")

            else ->
                Triple(ViewerType.HEX_BINARY, FormatSupportStatus.INSPECT_ONLY, "Unknown format opened safely in streaming Hex & Binary Inspector.")
        }
    }
}
