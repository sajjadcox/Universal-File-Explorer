package com.example.domain.office

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipFile

sealed class WordBlock {
    data class Paragraph(
        val text: String,
        val headingLevel: Int = 0, // 0=normal, 1..3=heading
        val isBold: Boolean = false
    ) : WordBlock()

    data class Table(val rows: List<List<String>>) : WordBlock()
    data class EmbeddedImage(val name: String, val bytes: ByteArray) : WordBlock()
}

data class WordDocumentModel(
    val title: String,
    val formatLabel: String,
    val blocks: List<WordBlock>,
    val isPartialFormat: Boolean = false,
    val notes: String = ""
) {
    val fullPlainText: String
        get() = buildString {
            for (b in blocks) {
                when (b) {
                    is WordBlock.Paragraph -> appendLine(b.text)
                    is WordBlock.Table -> b.rows.forEach { appendLine(it.joinToString(" | ")) }
                    is WordBlock.EmbeddedImage -> appendLine("[Image: ${b.name}]")
                }
            }
        }
}

data class CellData(
    val colIndex: Int,
    val value: String,
    val formula: String? = null
)

data class SheetModel(
    val name: String,
    val rows: List<List<CellData>>
)

data class SpreadsheetModel(
    val title: String,
    val formatLabel: String,
    val sheets: List<SheetModel>,
    val isPartialFormat: Boolean = false,
    val notes: String = ""
)

data class SlideModel(
    val index: Int,
    val title: String,
    val bodyLines: List<String>,
    val images: List<ByteArray> = emptyList()
)

data class PresentationModel(
    val title: String,
    val formatLabel: String,
    val slides: List<SlideModel>,
    val isPartialFormat: Boolean = false,
    val notes: String = ""
)

object OfficeEngine {

    suspend fun parseWordDocument(file: File, extension: String): WordDocumentModel =
        withContext(Dispatchers.IO) {
            val ext = extension.lowercase()
            when {
                ext in setOf("docx", "docm", "dotx") -> parseDocx(file)
                ext == "odt" -> parseOdt(file)
                ext == "rtf" -> parseRtf(file)
                ext == "doc" -> parseLegacyOleText(file, "Legacy DOC (OLE2)")
                else -> parsePlainTextAsWord(file, ext.uppercase())
            }
        }

    private fun parseDocx(file: File): WordDocumentModel {
        val blocks = mutableListOf<WordBlock>()
        ZipFile(file).use { zip ->
            val docEntry = zip.getEntry("word/document.xml")
                ?: return WordDocumentModel(file.name, "DOCX", emptyList(), true, "Missing word/document.xml")

            zip.getInputStream(docEntry).use { stream ->
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(stream, "UTF-8")

                var event = parser.eventType
                var inParagraph = false
                var inTable = false
                var inRow = false
                var inCell = false
                var inText = false
                var inStyle = false

                val paraBuf = StringBuilder()
                var headingLevel = 0
                var isBold = false
                val currentTable = mutableListOf<List<String>>()
                val currentRow = mutableListOf<String>()
                val cellBuf = StringBuilder()

                while (event != XmlPullParser.END_DOCUMENT) {
                    val tag = parser.name ?: ""
                    when (event) {
                        XmlPullParser.START_TAG -> {
                            when {
                                tag.endsWith("tbl") -> {
                                    inTable = true
                                    currentTable.clear()
                                }
                                tag.endsWith("tr") && inTable -> {
                                    inRow = true
                                    currentRow.clear()
                                }
                                tag.endsWith("tc") && inRow -> {
                                    inCell = true
                                    cellBuf.clear()
                                }
                                tag == "w:p" -> {
                                    inParagraph = true
                                    paraBuf.clear()
                                    headingLevel = 0
                                    isBold = false
                                }
                                tag == "w:pStyle" && inParagraph -> {
                                    val v = parser.getAttributeValue(null, "w:val") ?: ""
                                    headingLevel = when {
                                        v.contains("Title", true) || v.contains("Heading1", true) -> 1
                                        v.contains("Heading2", true) -> 2
                                        v.contains("Heading", true) -> 3
                                        else -> 0
                                    }
                                    inStyle = true
                                }
                                tag == "w:b" && inParagraph -> isBold = true
                                tag == "w:t" -> inText = true
                                tag == "w:br" -> if (inCell) cellBuf.append("\n") else paraBuf.append("\n")
                                tag == "w:tab" -> if (inCell) cellBuf.append("    ") else paraBuf.append("    ")
                            }
                        }
                        XmlPullParser.TEXT -> {
                            if (inText) {
                                val t = parser.text ?: ""
                                if (inCell) cellBuf.append(t) else if (inParagraph) paraBuf.append(t)
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            when {
                                tag == "w:t" -> inText = false
                                tag == "w:pStyle" -> inStyle = false
                                tag == "w:p" -> {
                                    if (inCell) {
                                        if (cellBuf.isNotEmpty() && !cellBuf.endsWith(" ")) cellBuf.append(" ")
                                    } else {
                                        val txt = paraBuf.toString().trim()
                                        if (txt.isNotEmpty()) {
                                            blocks.add(WordBlock.Paragraph(txt, headingLevel, isBold))
                                        }
                                    }
                                    inParagraph = false
                                }
                                tag.endsWith("tc") && inRow -> {
                                    currentRow.add(cellBuf.toString().trim())
                                    inCell = false
                                }
                                tag.endsWith("tr") && inTable -> {
                                    if (currentRow.isNotEmpty()) currentTable.add(currentRow.toList())
                                    inRow = false
                                }
                                tag.endsWith("tbl") && inTable -> {
                                    if (currentTable.isNotEmpty()) blocks.add(WordBlock.Table(currentTable.toList()))
                                    inTable = false
                                }
                            }
                        }
                    }
                    event = parser.next()
                }
            }

            // Load up to 12 embedded images from word/media/ safely
            var imgCount = 0
            val entries = zip.entries()
            while (entries.hasMoreElements() && imgCount < 12) {
                val entry = entries.nextElement()
                if (entry.name.startsWith("word/media/") && !entry.isDirectory && entry.size in 1..(4 * 1024 * 1024)) {
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    blocks.add(WordBlock.EmbeddedImage(entry.name.substringAfterLast('/'), bytes))
                    imgCount++
                }
            }
        }
        return WordDocumentModel(file.name, "DOCX", blocks)
    }

    private fun parseOdt(file: File): WordDocumentModel {
        val blocks = mutableListOf<WordBlock>()
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("content.xml")
                ?: return WordDocumentModel(file.name, "ODT", emptyList(), true, "Missing content.xml")
            zip.getInputStream(entry).use { stream ->
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(stream, "UTF-8")
                var event = parser.eventType
                var inTextBlock = false
                var headingLevel = 0
                val buf = StringBuilder()

                while (event != XmlPullParser.END_DOCUMENT) {
                    val tag = parser.name ?: ""
                    when (event) {
                        XmlPullParser.START_TAG -> {
                            if (tag == "text:p" || tag == "text:h") {
                                inTextBlock = true
                                buf.clear()
                                headingLevel = if (tag == "text:h") {
                                    parser.getAttributeValue(null, "text:outline-level")?.toIntOrNull()?.coerceIn(1, 3) ?: 1
                                } else 0
                            }
                        }
                        XmlPullParser.TEXT -> if (inTextBlock) buf.append(parser.text ?: "")
                        XmlPullParser.END_TAG -> {
                            if (tag == "text:p" || tag == "text:h") {
                                val t = buf.toString().trim()
                                if (t.isNotEmpty()) blocks.add(WordBlock.Paragraph(t, headingLevel))
                                inTextBlock = false
                            }
                        }
                    }
                    event = parser.next()
                }
            }
        }
        return WordDocumentModel(file.name, "ODT", blocks)
    }

    private fun parseRtf(file: File): WordDocumentModel {
        val raw = file.inputStream().bufferedReader().use { r ->
            val buf = CharArray(512 * 1024)
            val len = r.read(buf)
            if (len > 0) String(buf, 0, len) else ""
        }
        // Strip RTF groups/control words into readable text
        val out = StringBuilder()
        var i = 0
        var skipGroup = 0
        while (i < raw.length) {
            val c = raw[i]
            when (c) {
                '{' -> {
                    val lookahead = raw.substring(i, minOf(i + 20, raw.length))
                    if (lookahead.startsWith("{\\fonttbl") || lookahead.startsWith("{\\colortbl") ||
                        lookahead.startsWith("{\\stylesheet") || lookahead.startsWith("{\\*")
                    ) {
                        skipGroup++
                    } else if (skipGroup > 0) {
                        skipGroup++
                    }
                    i++
                }
                '}' -> {
                    if (skipGroup > 0) skipGroup--
                    i++
                }
                '\\' -> {
                    if (skipGroup > 0) {
                        i++
                        continue
                    }
                    if (i + 1 < raw.length && (raw[i + 1] == '\\' || raw[i + 1] == '{' || raw[i + 1] == '}')) {
                        out.append(raw[i + 1])
                        i += 2
                    } else if (i + 1 < raw.length && raw[i + 1] == '\'') {
                        val hex = raw.substring(minOf(i + 2, raw.length), minOf(i + 4, raw.length))
                        hex.toIntOrNull(16)?.let { out.append(it.toChar()) }
                        i += 4
                    } else {
                        var j = i + 1
                        while (j < raw.length && raw[j].isLetter()) j++
                        val word = raw.substring(i + 1, j)
                        if (word == "par" || word == "line") out.append('\n')
                        if (word == "tab") out.append("    ")
                        while (j < raw.length && (raw[j].isDigit() || raw[j] == '-')) j++
                        if (j < raw.length && raw[j] == ' ') j++
                        i = j
                    }
                }
                '\r', '\n' -> i++
                else -> {
                    if (skipGroup == 0) out.append(c)
                    i++
                }
            }
        }
        val blocks = out.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }
            .map { WordBlock.Paragraph(it) }
        return WordDocumentModel(file.name, "RTF", blocks, true, "Extracted plain text & paragraphs from RTF.")
    }

    private fun parseLegacyOleText(file: File, label: String): WordDocumentModel {
        val bytes = file.inputStream().use { s ->
            val buf = ByteArray(minOf(file.length(), 1024L * 1024L).toInt())
            val read = s.read(buf)
            if (read > 0) buf.copyOf(read) else ByteArray(0)
        }
        val extracted = extractReadableStringsFromBinary(bytes)
        val blocks = extracted.map { WordBlock.Paragraph(it) }
        return WordDocumentModel(
            title = file.name,
            formatLabel = label,
            blocks = blocks,
            isPartialFormat = true,
            notes = "Legacy binary OLE2 format: showing extracted printable text streams."
        )
    }

    private fun parsePlainTextAsWord(file: File, label: String): WordDocumentModel {
        val lines = file.bufferedReader().useLines { seq -> seq.take(1500).toList() }
        return WordDocumentModel(file.name, label, lines.map { WordBlock.Paragraph(it) })
    }

    suspend fun parseSpreadsheet(file: File, extension: String): SpreadsheetModel =
        withContext(Dispatchers.IO) {
            when (val ext = extension.lowercase()) {
                "csv" -> parseDelimited(file, ',', "CSV")
                "tsv" -> parseDelimited(file, '\t', "TSV")
                "xlsx", "xlsm" -> parseXlsx(file)
                "ods" -> parseOds(file)
                "xls" -> {
                    val ole = parseLegacyOleText(file, "Legacy XLS")
                    val rows = ole.blocks.filterIsInstance<WordBlock.Paragraph>()
                        .map { listOf(CellData(0, it.text)) }
                    SpreadsheetModel(file.name, "XLS (Partial)", listOf(SheetModel("Extracted Strings", rows)), true, ole.notes)
                }
                else -> parseDelimited(file, ',', ext.uppercase())
            }
        }

    private fun parseDelimited(file: File, delimiter: Char, label: String): SpreadsheetModel {
        val rows = mutableListOf<List<CellData>>()
        file.bufferedReader().useLines { lines ->
            lines.take(3000).forEach { line ->
                val fields = parseCsvLine(line, delimiter)
                rows.add(fields.mapIndexed { idx, v -> CellData(idx, v) })
            }
        }
        return SpreadsheetModel(file.name, label, listOf(SheetModel("Sheet 1", rows)))
    }

    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    cur.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (ch == delimiter && !inQuotes) {
                result.add(cur.toString())
                cur.clear()
            } else {
                cur.append(ch)
            }
            i++
        }
        result.add(cur.toString())
        return result
    }

    private fun parseXlsx(file: File): SpreadsheetModel {
        val sheets = mutableListOf<SheetModel>()
        ZipFile(file).use { zip ->
            val sharedStrings = mutableListOf<String>()
            zip.getEntry("xl/sharedStrings.xml")?.let { ssEntry ->
                zip.getInputStream(ssEntry).use { s ->
                    val parser = Xml.newPullParser()
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    parser.setInput(s, "UTF-8")
                    var ev = parser.eventType
                    var inSi = false
                    var inT = false
                    val buf = StringBuilder()
                    while (ev != XmlPullParser.END_DOCUMENT) {
                        val tag = parser.name ?: ""
                        when (ev) {
                            XmlPullParser.START_TAG -> {
                                if (tag == "si") { inSi = true; buf.clear() }
                                if (tag == "t" && inSi) inT = true
                            }
                            XmlPullParser.TEXT -> if (inT) buf.append(parser.text ?: "")
                            XmlPullParser.END_TAG -> {
                                if (tag == "t") inT = false
                                if (tag == "si") {
                                    sharedStrings.add(buf.toString())
                                    inSi = false
                                }
                            }
                        }
                        ev = parser.next()
                    }
                }
            }

            // Sheet names from workbook.xml
            val sheetNames = mutableListOf<String>()
            zip.getEntry("xl/workbook.xml")?.let { wbEntry ->
                zip.getInputStream(wbEntry).use { s ->
                    val parser = Xml.newPullParser()
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    parser.setInput(s, "UTF-8")
                    var ev = parser.eventType
                    while (ev != XmlPullParser.END_DOCUMENT) {
                        if (ev == XmlPullParser.START_TAG && parser.name == "sheet") {
                            sheetNames.add(parser.getAttributeValue(null, "name") ?: "Sheet ${sheetNames.size + 1}")
                        }
                        ev = parser.next()
                    }
                }
            }

            val sheetEntries = zip.entries().toList()
                .filter { it.name.startsWith("xl/worksheets/sheet") && it.name.endsWith(".xml") }
                .sortedBy { entry ->
                    entry.name.substringAfter("sheet").substringBefore(".").toIntOrNull() ?: 999
                }

            sheetEntries.forEachIndexed { idx, entry ->
                val rows = mutableListOf<List<CellData>>()
                zip.getInputStream(entry).use { s ->
                    val parser = Xml.newPullParser()
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    parser.setInput(s, "UTF-8")
                    var ev = parser.eventType
                    var currentRow = mutableListOf<CellData>()
                    var cellType = ""
                    var cellCol = 0
                    var inV = false
                    var inF = false
                    var inIs = false
                    val valBuf = StringBuilder()
                    val formulaBuf = StringBuilder()

                    while (ev != XmlPullParser.END_DOCUMENT) {
                        val tag = parser.name ?: ""
                        when (ev) {
                            XmlPullParser.START_TAG -> when (tag) {
                                "row" -> currentRow = mutableListOf()
                                "c" -> {
                                    cellType = parser.getAttributeValue(null, "t") ?: ""
                                    val ref = parser.getAttributeValue(null, "r") ?: ""
                                    cellCol = colRefToIndex(ref, currentRow.size)
                                    valBuf.clear()
                                    formulaBuf.clear()
                                }
                                "v" -> inV = true
                                "f" -> inF = true
                                "t" -> if (cellType == "inlineStr") inIs = true
                            }
                            XmlPullParser.TEXT -> {
                                val t = parser.text ?: ""
                                if (inV || inIs) valBuf.append(t)
                                if (inF) formulaBuf.append(t)
                            }
                            XmlPullParser.END_TAG -> when (tag) {
                                "v" -> inV = false
                                "f" -> inF = false
                                "t" -> inIs = false
                                "c" -> {
                                    val rawVal = valBuf.toString()
                                    val resolved = if (cellType == "s") {
                                        val sIdx = rawVal.toIntOrNull()
                                        if (sIdx != null && sIdx in sharedStrings.indices) sharedStrings[sIdx] else rawVal
                                    } else {
                                        rawVal
                                    }
                                    val formula = if (formulaBuf.isNotEmpty()) "=${formulaBuf}" else null
                                    // Pad skipped empty columns
                                    while (currentRow.size < cellCol) {
                                        currentRow.add(CellData(currentRow.size, ""))
                                    }
                                    currentRow.add(CellData(cellCol, resolved, formula))
                                }
                                "row" -> if (currentRow.isNotEmpty()) rows.add(currentRow)
                            }
                        }
                        ev = parser.next()
                    }
                }
                val name = sheetNames.getOrNull(idx) ?: "Sheet ${idx + 1}"
                sheets.add(SheetModel(name, rows))
            }
        }
        return SpreadsheetModel(file.name, "XLSX", sheets)
    }

    private fun parseOds(file: File): SpreadsheetModel {
        val sheets = mutableListOf<SheetModel>()
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("content.xml")
                ?: return SpreadsheetModel(file.name, "ODS", emptyList(), true, "Missing content.xml")
            zip.getInputStream(entry).use { s ->
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(s, "UTF-8")
                var ev = parser.eventType
                var sheetName = "Sheet 1"
                var rows = mutableListOf<List<CellData>>()
                var currentRow = mutableListOf<CellData>()
                var inP = false
                val buf = StringBuilder()

                while (ev != XmlPullParser.END_DOCUMENT) {
                    val tag = parser.name ?: ""
                    when (ev) {
                        XmlPullParser.START_TAG -> when (tag) {
                            "table:table" -> {
                                sheetName = parser.getAttributeValue(null, "table:name") ?: "Sheet ${sheets.size + 1}"
                                rows = mutableListOf()
                            }
                            "table:table-row" -> currentRow = mutableListOf()
                            "table:table-cell" -> buf.clear()
                            "text:p" -> inP = true
                        }
                        XmlPullParser.TEXT -> if (inP) buf.append(parser.text ?: "")
                        XmlPullParser.END_TAG -> when (tag) {
                            "text:p" -> inP = false
                            "table:table-cell" -> currentRow.add(CellData(currentRow.size, buf.toString()))
                            "table:table-row" -> if (currentRow.any { it.value.isNotBlank() }) rows.add(currentRow)
                            "table:table" -> sheets.add(SheetModel(sheetName, rows))
                        }
                    }
                    ev = parser.next()
                }
            }
        }
        return SpreadsheetModel(file.name, "ODS", sheets)
    }

    suspend fun parsePresentation(file: File, extension: String): PresentationModel =
        withContext(Dispatchers.IO) {
            when (extension.lowercase()) {
                "pptx", "ppsx" -> parsePptx(file)
                "odp" -> parseOdp(file)
                "ppt" -> {
                    val ole = parseLegacyOleText(file, "Legacy PPT")
                    val lines = ole.blocks.filterIsInstance<WordBlock.Paragraph>().map { it.text }
                    val chunked = lines.chunked(8).mapIndexed { i, chunk ->
                        SlideModel(i + 1, chunk.firstOrNull() ?: "Slide ${i + 1}", chunk.drop(1))
                    }
                    PresentationModel(file.name, "PPT (Partial)", chunked, true, ole.notes)
                }
                else -> PresentationModel(file.name, extension.uppercase(), emptyList(), true, "Unsupported presentation format.")
            }
        }

    private fun parsePptx(file: File): PresentationModel {
        val slides = mutableListOf<SlideModel>()
        ZipFile(file).use { zip ->
            val slideEntries = zip.entries().toList()
                .filter { it.name.startsWith("ppt/slides/slide") && it.name.endsWith(".xml") }
                .sortedBy { it.name.substringAfter("slide").substringBefore(".").toIntOrNull() ?: 999 }

            val mediaBytes = zip.entries().toList()
                .filter { it.name.startsWith("ppt/media/") && !it.isDirectory && it.size in 1..(3 * 1024 * 1024) }
                .take(15)
                .map { zip.getInputStream(it).use { s -> s.readBytes() } }

            slideEntries.forEachIndexed { idx, entry ->
                val lines = mutableListOf<String>()
                zip.getInputStream(entry).use { s ->
                    val parser = Xml.newPullParser()
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    parser.setInput(s, "UTF-8")
                    var ev = parser.eventType
                    var inP = false
                    var inT = false
                    val buf = StringBuilder()

                    while (ev != XmlPullParser.END_DOCUMENT) {
                        val tag = parser.name ?: ""
                        when (ev) {
                            XmlPullParser.START_TAG -> {
                                if (tag == "a:p") { inP = true; buf.clear() }
                                if (tag == "a:t" && inP) inT = true
                            }
                            XmlPullParser.TEXT -> if (inT) buf.append(parser.text ?: "")
                            XmlPullParser.END_TAG -> {
                                if (tag == "a:t") inT = false
                                if (tag == "a:p") {
                                    val t = buf.toString().trim()
                                    if (t.isNotEmpty()) lines.add(t)
                                    inP = false
                                }
                            }
                        }
                        ev = parser.next()
                    }
                }
                val title = lines.firstOrNull() ?: "Slide ${idx + 1}"
                val body = if (lines.size > 1) lines.drop(1) else emptyList()
                val attachedImg = mediaBytes.getOrNull(idx)?.let { listOf(it) } ?: emptyList()
                slides.add(SlideModel(idx + 1, title, body, attachedImg))
            }
        }
        return PresentationModel(file.name, "PPTX", slides)
    }

    private fun parseOdp(file: File): PresentationModel {
        val slides = mutableListOf<SlideModel>()
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("content.xml")
                ?: return PresentationModel(file.name, "ODP", emptyList(), true, "Missing content.xml")
            zip.getInputStream(entry).use { s ->
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(s, "UTF-8")
                var ev = parser.eventType
                var inPage = false
                var inP = false
                val lines = mutableListOf<String>()
                val buf = StringBuilder()
                while (ev != XmlPullParser.END_DOCUMENT) {
                    val tag = parser.name ?: ""
                    when (ev) {
                        XmlPullParser.START_TAG -> {
                            if (tag == "draw:page") { inPage = true; lines.clear() }
                            if (tag == "text:p" && inPage) { inP = true; buf.clear() }
                        }
                        XmlPullParser.TEXT -> if (inP) buf.append(parser.text ?: "")
                        XmlPullParser.END_TAG -> {
                            if (tag == "text:p") {
                                val t = buf.toString().trim()
                                if (t.isNotEmpty()) lines.add(t)
                                inP = false
                            }
                            if (tag == "draw:page") {
                                slides.add(
                                    SlideModel(
                                        slides.size + 1,
                                        lines.firstOrNull() ?: "Slide ${slides.size + 1}",
                                        lines.drop(1)
                                    )
                                )
                                inPage = false
                            }
                        }
                    }
                    ev = parser.next()
                }
            }
        }
        return PresentationModel(file.name, "ODP", slides)
    }

    suspend fun exportLinesToPdf(title: String, lines: List<String>, outputFile: File): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                com.tom_roush.pdfbox.pdmodel.PDDocument().use { pd ->
                    val pageRect = com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4
                    val pageW = pageRect.width
                    val pageH = pageRect.height
                    val margin = 44f
                    val maxLineWidth = pageW - margin * 2f
                    val titleFont = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD
                    val bodyFont = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA
                    val bodySize = 11f
                    val lineStep = 15f

                    fun sanitizeWinAnsi(raw: String): String {
                        val sb = StringBuilder(raw.length)
                        for (ch in raw) {
                            when {
                                ch == '\t' -> sb.append("    ")
                                ch.code in 32..126 -> sb.append(ch)
                                ch.code in 160..255 -> sb.append(ch)
                                else -> sb.append(' ')
                            }
                        }
                        return sb.toString()
                    }

                    fun wrapLine(text: String, font: com.tom_roush.pdfbox.pdmodel.font.PDFont, size: Float): List<String> {
                        val clean = sanitizeWinAnsi(text)
                        if (clean.isEmpty()) return listOf("")
                        val words = clean.split(' ')
                        val wrapped = mutableListOf<String>()
                        var current = StringBuilder()
                        for (word in words) {
                            val candidate = if (current.isEmpty()) word else "${current} $word"
                            val width = try {
                                font.getStringWidth(candidate) / 1000f * size
                            } catch (_: Exception) {
                                candidate.length * size * 0.55f
                            }
                            if (width <= maxLineWidth) {
                                current = StringBuilder(candidate)
                            } else {
                                if (current.isNotEmpty()) {
                                    wrapped.add(current.toString())
                                    current = StringBuilder(word)
                                } else {
                                    // Single very long token: chunk safely
                                    wrapped.add(word.take(80))
                                    val rest = word.drop(80)
                                    current = StringBuilder(rest)
                                }
                            }
                        }
                        if (current.isNotEmpty()) wrapped.add(current.toString())
                        return wrapped
                    }

                    var page = com.tom_roush.pdfbox.pdmodel.PDPage(pageRect)
                    pd.addPage(page)
                    var cs = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(pd, page)
                    var y = pageH - margin

                    // Draw title
                    val safeTitle = sanitizeWinAnsi(title).take(70)
                    cs.beginText()
                    cs.setFont(titleFont, 15f)
                    cs.setNonStrokingColor(0, 97, 164)
                    cs.newLineAtOffset(margin, y)
                    cs.showText(safeTitle)
                    cs.endText()
                    y -= 26f

                    cs.setNonStrokingColor(20, 20, 20)
                    for (rawLine in lines) {
                        if (rawLine.isBlank()) {
                            y -= 10f
                            continue
                        }
                        val wrapped = wrapLine(rawLine, bodyFont, bodySize)
                        for (wl in wrapped) {
                            if (y < margin + lineStep) {
                                cs.close()
                                page = com.tom_roush.pdfbox.pdmodel.PDPage(pageRect)
                                pd.addPage(page)
                                cs = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(pd, page)
                                cs.setNonStrokingColor(20, 20, 20)
                                y = pageH - margin
                            }
                            cs.beginText()
                            cs.setFont(bodyFont, bodySize)
                            cs.newLineAtOffset(margin, y)
                            cs.showText(wl)
                            cs.endText()
                            y -= lineStep
                        }
                        y -= 3f
                    }
                    cs.close()
                    pd.save(outputFile)
                }
                Result.success(outputFile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun exportSheetToCsv(sheet: SheetModel, outputFile: File): Result<File> {
        return try {
            outputFile.bufferedWriter().use { writer ->
                for (row in sheet.rows) {
                    val line = row.joinToString(",") { cell ->
                        val escaped = cell.value.replace("\"", "\"\"")
                        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
                            "\"$escaped\""
                        } else escaped
                    }
                    writer.appendLine(line)
                }
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun colRefToIndex(ref: String, fallback: Int): Int {
        val letters = ref.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return fallback
        var col = 0
        for (ch in letters) {
            col = col * 26 + (ch - 'A' + 1)
        }
        return (col - 1).coerceAtLeast(0)
    }

    private fun extractReadableStringsFromBinary(bytes: ByteArray): List<String> {
        val results = mutableListOf<String>()
        val asciiBuf = StringBuilder()
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v in 32..126 || v == 10 || v == 13) {
                asciiBuf.append(v.toChar())
            } else {
                if (asciiBuf.length >= 6) {
                    val s = asciiBuf.toString().trim()
                    if (s.any { it.isLetterOrDigit() }) results.add(s)
                }
                asciiBuf.clear()
            }
        }
        return results.distinct().take(400)
    }
}
