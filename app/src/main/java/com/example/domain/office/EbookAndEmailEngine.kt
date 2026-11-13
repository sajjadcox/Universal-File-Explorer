package com.example.domain.office

import android.text.Html
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile

data class EbookChapter(
    val title: String,
    val content: String
)

data class EbookModel(
    val title: String,
    val author: String,
    val language: String,
    val formatLabel: String,
    val chapters: List<EbookChapter>,
    val isDrmProtected: Boolean = false,
    val notes: String = ""
)

data class EmailModel(
    val subject: String,
    val from: String,
    val to: String,
    val date: String,
    val bodyText: String,
    val htmlBody: String?,
    val attachments: List<String>,
    val headers: Map<String, String>,
    val isPartial: Boolean = false
)

object EbookAndEmailEngine {

    suspend fun parseEbook(file: File, extension: String): EbookModel = withContext(Dispatchers.IO) {
        when (extension.lowercase()) {
            "epub" -> parseEpub(file)
            "mobi", "azw", "azw3", "pdb" -> parsePalmMobi(file, extension.uppercase())
            else -> EbookModel(file.name, "Unknown", "", extension.uppercase(), emptyList(), notes = "Unsupported eBook format")
        }
    }

    private fun parseEpub(file: File): EbookModel {
        var title = file.nameWithoutExtension
        var author = "Unknown Author"
        var lang = "en"
        val chapters = mutableListOf<EbookChapter>()

        try {
            ZipFile(file).use { zip ->
                // 1. Locate OPF path from META-INF/container.xml
                var opfPath = ""
                zip.getEntry("META-INF/container.xml")?.let { contEntry ->
                    zip.getInputStream(contEntry).use { s ->
                        val parser = Xml.newPullParser()
                        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                        parser.setInput(s, "UTF-8")
                        var ev = parser.eventType
                        while (ev != XmlPullParser.END_DOCUMENT) {
                            if (ev == XmlPullParser.START_TAG && parser.name == "rootfile") {
                                opfPath = parser.getAttributeValue(null, "full-path") ?: ""
                                break
                            }
                            ev = parser.next()
                        }
                    }
                }

                val opfDir = if (opfPath.contains("/")) opfPath.substringBeforeLast("/") + "/" else ""
                val manifest = mutableMapOf<String, String>() // id -> href
                val spineIds = mutableListOf<String>()

                if (opfPath.isNotEmpty()) {
                    zip.getEntry(opfPath)?.let { opfEntry ->
                        zip.getInputStream(opfEntry).use { s ->
                            val parser = Xml.newPullParser()
                            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                            parser.setInput(s, "UTF-8")
                            var ev = parser.eventType
                            var curTag = ""
                            while (ev != XmlPullParser.END_DOCUMENT) {
                                when (ev) {
                                    XmlPullParser.START_TAG -> {
                                        curTag = parser.name ?: ""
                                        if (curTag == "item") {
                                            val id = parser.getAttributeValue(null, "id") ?: ""
                                            val href = parser.getAttributeValue(null, "href") ?: ""
                                            if (id.isNotEmpty() && href.isNotEmpty()) manifest[id] = href
                                        } else if (curTag == "itemref") {
                                            val idref = parser.getAttributeValue(null, "idref") ?: ""
                                            if (idref.isNotEmpty()) spineIds.add(idref)
                                        }
                                    }
                                    XmlPullParser.TEXT -> {
                                        val txt = parser.text?.trim() ?: ""
                                        if (txt.isNotEmpty()) {
                                            when {
                                                curTag.endsWith("title") -> title = txt
                                                curTag.endsWith("creator") -> author = txt
                                                curTag.endsWith("language") -> lang = txt
                                            }
                                        }
                                    }
                                    XmlPullParser.END_TAG -> curTag = ""
                                }
                                ev = parser.next()
                            }
                        }
                    }
                }

                val htmlPaths = if (spineIds.isNotEmpty()) {
                    spineIds.mapNotNull { manifest[it] }.map { opfDir + it }
                } else {
                    zip.entries().toList()
                        .filter { it.name.endsWith(".xhtml") || it.name.endsWith(".html") || it.name.endsWith(".htm") }
                        .map { it.name }
                        .sorted()
                }

                htmlPaths.take(80).forEachIndexed { idx, path ->
                    val entry = zip.getEntry(path) ?: zip.getEntry(path.removePrefix("/"))
                    if (entry != null) {
                        val rawHtml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                        val titleMatch = Regex("<title[^>]*>(.*?)</title>", RegexOption.IGNORE_CASE).find(rawHtml)
                        val chapTitle = titleMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
                            ?: "Chapter ${idx + 1}"
                        val cleanBody = rawHtml
                            .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
                            .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
                        val plain = Html.fromHtml(cleanBody, Html.FROM_HTML_MODE_COMPACT).toString().trim()
                        if (plain.isNotBlank()) {
                            chapters.add(EbookChapter(chapTitle, plain))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return EbookModel(title, author, lang, "EPUB", chapters, notes = "Error parsing EPUB: ${e.message}")
        }

        return EbookModel(title, author, lang, "EPUB", chapters)
    }

    private fun parsePalmMobi(file: File, label: String): EbookModel {
        return try {
            val bytes = file.inputStream().use { s ->
                val cap = minOf(file.length(), 4L * 1024L * 1024L).toInt()
                val buf = ByteArray(cap)
                val read = s.read(buf)
                if (read > 0) buf.copyOf(read) else ByteArray(0)
            }
            if (bytes.size < 90) {
                return EbookModel(file.name, "Unknown", "", label, emptyList(), notes = "File too small or corrupted.")
            }

            // PalmDB header: title in first 32 bytes
            val pdbTitle = String(bytes.sliceArray(0 until 32), Charsets.ISO_8859_1)
                .trim('\u0000', ' ')
                .ifBlank { file.nameWithoutExtension }

            val numRecords = ((bytes[76].toInt() and 0xFF) shl 8) or (bytes[77].toInt() and 0xFF)
            if (numRecords < 2 || bytes.size < 78 + numRecords * 8) {
                return EbookModel(pdbTitle, "Unknown", "", label, emptyList(), notes = "Invalid PalmDOC record table.")
            }

            val offsets = IntArray(numRecords) { idx ->
                val pos = 78 + idx * 8
                ((bytes[pos].toInt() and 0xFF) shl 24) or
                    ((bytes[pos + 1].toInt() and 0xFF) shl 16) or
                    ((bytes[pos + 2].toInt() and 0xFF) shl 8) or
                    (bytes[pos + 3].toInt() and 0xFF)
            }

            val rec0 = offsets[0]
            if (rec0 + 16 > bytes.size) {
                return EbookModel(pdbTitle, "Unknown", "", label, emptyList(), notes = "Header truncated.")
            }
            val compression = ((bytes[rec0].toInt() and 0xFF) shl 8) or (bytes[rec0 + 1].toInt() and 0xFF)
            val textRecordCount = ((bytes[rec0 + 8].toInt() and 0xFF) shl 8) or (bytes[rec0 + 9].toInt() and 0xFF)
            val encryptionType = ((bytes[rec0 + 12].toInt() and 0xFF) shl 8) or (bytes[rec0 + 13].toInt() and 0xFF)

            if (encryptionType != 0) {
                return EbookModel(
                    title = pdbTitle,
                    author = "Protected",
                    language = "",
                    formatLabel = label,
                    chapters = emptyList(),
                    isDrmProtected = true,
                    notes = "This $label file is DRM/encryption protected (encryption=$encryptionType) and cannot be decrypted."
                )
            }

            if (compression == 17480) {
                return EbookModel(
                    title = pdbTitle,
                    author = "Kindle",
                    language = "",
                    formatLabel = label,
                    chapters = emptyList(),
                    notes = "Uses HUFF/CDIC compression (partially supported: metadata inspected, decompression requires HUFF tables)."
                )
            }

            val sb = StringBuilder()
            val maxRec = minOf(textRecordCount, numRecords - 1)
            for (r in 1..maxRec) {
                val start = offsets[r]
                val end = if (r + 1 < numRecords) offsets[r + 1] else bytes.size
                if (start < 0 || end > bytes.size || start >= end) continue
                val chunk = bytes.sliceArray(start until end)
                val decoded = if (compression == 2) decompressPalmDoc(chunk) else chunk
                sb.append(String(decoded, Charsets.UTF_8))
            }

            val plain = Html.fromHtml(sb.toString(), Html.FROM_HTML_MODE_COMPACT).toString().trim()
            val chapterChunks = plain.chunked(8000).mapIndexed { i, part ->
                EbookChapter("Section ${i + 1}", part)
            }
            EbookModel(pdbTitle, "eBook", "", label, chapterChunks)
        } catch (e: Exception) {
            EbookModel(file.name, "Unknown", "", label, emptyList(), notes = "Error reading $label: ${e.message}")
        }
    }

    private fun decompressPalmDoc(data: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < data.size) {
            val c = data[i++].toInt() and 0xFF
            when {
                c == 0 -> out.write(0)
                c in 1..8 -> {
                    val count = minOf(c, data.size - i)
                    out.write(data, i, count)
                    i += count
                }
                c <= 0x7F -> out.write(c)
                c >= 0xC0 -> {
                    out.write(' '.code)
                    out.write(c xor 0x80)
                }
                else -> {
                    if (i >= data.size) break
                    val next = data[i++].toInt() and 0xFF
                    val pair = (c shl 8) or next
                    val distance = (pair shr 3) and 0x07FF
                    val length = (pair and 0x07) + 3
                    val buf = out.toByteArray()
                    for (k in 0 until length) {
                        val srcIdx = buf.size - distance + k
                        if (srcIdx in buf.indices) {
                            out.write(buf[srcIdx].toInt())
                        }
                    }
                }
            }
        }
        return out.toByteArray()
    }

    suspend fun parseEmail(file: File, extension: String): EmailModel = withContext(Dispatchers.IO) {
        if (extension.lowercase() == "msg") {
            val bytes = file.inputStream().use { s ->
                val cap = minOf(file.length(), 1024L * 1024L).toInt()
                val buf = ByteArray(cap)
                val read = s.read(buf)
                if (read > 0) buf.copyOf(read) else ByteArray(0)
            }
            val text = String(bytes, Charsets.ISO_8859_1)
                .replace(Regex("[^\\x20-\\x7E\\r\\n]"), " ")
                .replace(Regex(" {3,}"), "\n")
            return@withContext EmailModel(
                subject = file.nameWithoutExtension,
                from = "Outlook MSG (OLE2 Binary)",
                to = "See extracted content",
                date = "",
                bodyText = text.take(40000),
                htmlBody = null,
                attachments = emptyList(),
                headers = mapOf("Format" to "OLE2 MSG (Partial extraction)"),
                isPartial = true
            )
        }

        val lines = file.bufferedReader().useLines { it.take(5000).toList() }
        val headers = linkedMapOf<String, String>()
        var headerEndIndex = lines.size
        var lastHeaderKey: String? = null

        for ((idx, line) in lines.withIndex()) {
            if (line.isBlank()) {
                headerEndIndex = idx + 1
                break
            }
            if ((line.startsWith(" ") || line.startsWith("\t")) && lastHeaderKey != null) {
                headers[lastHeaderKey] = (headers[lastHeaderKey] ?: "") + " " + line.trim()
            } else if (line.contains(":")) {
                val key = line.substringBefore(":").trim()
                val value = line.substringAfter(":").trim()
                headers[key] = value
                lastHeaderKey = key
            }
        }

        val bodyLines = if (headerEndIndex < lines.size) lines.subList(headerEndIndex, lines.size) else emptyList()
        val rawBody = bodyLines.joinToString("\n")
        val attachments = Regex("filename=\"?([^\"\\r\\n;]+)\"?", RegexOption.IGNORE_CASE)
            .findAll(rawBody).map { it.groupValues[1] }.distinct().toList()

        val isHtml = rawBody.contains("<html", ignoreCase = true) ||
            (headers["Content-Type"]?.contains("text/html", ignoreCase = true) == true)

        EmailModel(
            subject = headers["Subject"] ?: file.nameWithoutExtension,
            from = headers["From"] ?: "Unknown Sender",
            to = headers["To"] ?: "Unknown Recipient",
            date = headers["Date"] ?: "",
            bodyText = if (isHtml) Html.fromHtml(rawBody, Html.FROM_HTML_MODE_COMPACT).toString().trim() else rawBody,
            htmlBody = if (isHtml) rawBody else null,
            attachments = attachments,
            headers = headers
        )
    }
}
