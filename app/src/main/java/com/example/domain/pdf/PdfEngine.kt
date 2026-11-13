package com.example.domain.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.multipdf.Splitter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

enum class PdfAnnotationTool {
    NONE,
    HIGHLIGHT,
    UNDERLINE,
    STRIKETHROUGH,
    FREEHAND,
    TEXT_NOTE,
    SIGNATURE
}

data class NormalizedPoint(val x: Float, val y: Float)

data class PdfAnnotation(
    val id: String = java.util.UUID.randomUUID().toString(),
    val pageIndex: Int,
    val tool: PdfAnnotationTool,
    val colorArgb: Int,
    val strokeWidth: Float = 4f,
    val points: List<NormalizedPoint> = emptyList(),
    val rect: RectF? = null, // Normalized 0..1 coordinates
    val text: String = "",
    val signaturePoints: List<List<NormalizedPoint>> = emptyList()
)

data class PdfMetadataInfo(
    val pageCount: Int,
    val title: String,
    val author: String,
    val subject: String,
    val creator: String,
    val producer: String,
    val isEncrypted: Boolean,
    val needsPassword: Boolean = false
)

data class PdfSearchMatch(
    val pageIndex: Int,
    val snippet: String
)

class PdfEngine(private val context: Context) {

    suspend fun inspectMetadata(pdfFile: File, password: String? = null): Result<PdfMetadataInfo> =
        withContext(Dispatchers.IO) {
            try {
                val doc = if (password != null) {
                    PDDocument.load(pdfFile, password)
                } else {
                    PDDocument.load(pdfFile)
                }
                doc.use { pd ->
                    val info = pd.documentInformation
                    Result.success(
                        PdfMetadataInfo(
                            pageCount = pd.numberOfPages,
                            title = info.title ?: "",
                            author = info.author ?: "",
                            subject = info.subject ?: "",
                            creator = info.creator ?: "",
                            producer = info.producer ?: "",
                            isEncrypted = pd.isEncrypted,
                            needsPassword = false
                        )
                    )
                }
            } catch (e: InvalidPasswordException) {
                Result.success(
                    PdfMetadataInfo(
                        pageCount = 0,
                        title = pdfFile.name,
                        author = "",
                        subject = "",
                        creator = "",
                        producer = "",
                        isEncrypted = true,
                        needsPassword = true
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun unlockPdfToCache(sourceFile: File, password: String): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val unlocked = File(context.cacheDir, "unlocked_${System.currentTimeMillis()}.pdf")
                PDDocument.load(sourceFile, password).use { doc ->
                    doc.isAllSecurityToBeRemoved = true
                    doc.save(unlocked)
                }
                Result.success(unlocked)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun renderPageBitmap(
        pdfFile: File,
        pageIndex: Int,
        targetWidthPx: Int = 1080
    ): Bitmap? = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null
            renderer.openPage(pageIndex).use { page ->
                val aspect = page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)
                val safeWidth = targetWidthPx.coerceIn(120, 2048)
                val safeHeight = (safeWidth * aspect).toInt().coerceIn(120, 2800)
                val bitmap = Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        } catch (_: Exception) {
            null
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    suspend fun getPageCountNative(pdfFile: File): Int = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            renderer.pageCount
        } catch (_: Exception) {
            0
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    suspend fun extractPageText(pdfFile: File, pageIndex: Int, password: String? = null): String =
        withContext(Dispatchers.IO) {
            try {
                val doc = if (password != null) PDDocument.load(pdfFile, password) else PDDocument.load(pdfFile)
                doc.use { pd ->
                    if (pageIndex < 0 || pageIndex >= pd.numberOfPages) return@withContext ""
                    val stripper = PDFTextStripper().apply {
                        startPage = pageIndex + 1
                        endPage = pageIndex + 1
                    }
                    stripper.getText(pd).trim()
                }
            } catch (_: Exception) {
                ""
            }
        }

    suspend fun extractAllText(pdfFile: File, password: String? = null): String =
        withContext(Dispatchers.IO) {
            try {
                val doc = if (password != null) PDDocument.load(pdfFile, password) else PDDocument.load(pdfFile)
                doc.use { pd ->
                    PDFTextStripper().getText(pd).trim()
                }
            } catch (e: Exception) {
                "Error extracting text: ${e.message}"
            }
        }

    suspend fun searchText(pdfFile: File, query: String, password: String? = null): List<PdfSearchMatch> =
        withContext(Dispatchers.IO) {
            val matches = mutableListOf<PdfSearchMatch>()
            val q = query.trim()
            if (q.isEmpty()) return@withContext matches
            try {
                val doc = if (password != null) PDDocument.load(pdfFile, password) else PDDocument.load(pdfFile)
                doc.use { pd ->
                    val stripper = PDFTextStripper()
                    for (i in 1..pd.numberOfPages) {
                        stripper.startPage = i
                        stripper.endPage = i
                        val pageText = stripper.getText(pd)
                        val idx = pageText.indexOf(q, ignoreCase = true)
                        if (idx >= 0) {
                            val start = (idx - 35).coerceAtLeast(0)
                            val end = (idx + q.length + 45).coerceAtMost(pageText.length)
                            val snippet = pageText.substring(start, end).replace("\n", " ").trim()
                            matches.add(PdfSearchMatch(pageIndex = i - 1, snippet = "...$snippet..."))
                        }
                    }
                }
            } catch (_: Exception) {
            }
            matches
        }

    suspend fun saveAnnotatedPdf(
        sourceFile: File,
        outputFile: File,
        annotations: List<PdfAnnotation>,
        password: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val doc = if (password != null) PDDocument.load(sourceFile, password) else PDDocument.load(sourceFile)
            doc.use { pd ->
                val grouped = annotations.groupBy { it.pageIndex }
                for ((pageIndex, pageAnnotations) in grouped) {
                    if (pageIndex !in 0 until pd.numberOfPages) continue
                    val page = pd.getPage(pageIndex)
                    val mediaBox = page.mediaBox
                    val pageWidth = mediaBox.width
                    val pageHeight = mediaBox.height

                    PDPageContentStream(
                        pd,
                        page,
                        PDPageContentStream.AppendMode.APPEND,
                        true,
                        true
                    ).use { cs ->
                        for (ann in pageAnnotations) {
                            val r = Color.red(ann.colorArgb)
                            val g = Color.green(ann.colorArgb)
                            val b = Color.blue(ann.colorArgb)

                            when (ann.tool) {
                                PdfAnnotationTool.FREEHAND -> {
                                    if (ann.points.size >= 2) {
                                        cs.setStrokingColor(r, g, b)
                                        cs.setLineWidth((ann.strokeWidth * 0.5f).coerceAtLeast(1f))
                                        val first = ann.points.first()
                                        cs.moveTo(first.x * pageWidth, (1f - first.y) * pageHeight)
                                        for (pt in ann.points.drop(1)) {
                                            cs.lineTo(pt.x * pageWidth, (1f - pt.y) * pageHeight)
                                        }
                                        cs.stroke()
                                    }
                                }
                                PdfAnnotationTool.HIGHLIGHT -> {
                                    ann.rect?.let { rect ->
                                        // Draw translucent-looking yellow/colored highlight bar
                                        cs.setNonStrokingColor(255, 241, 118)
                                        val x = rect.left * pageWidth
                                        val y = (1f - rect.bottom) * pageHeight
                                        val w = (rect.right - rect.left) * pageWidth
                                        val h = (rect.bottom - rect.top) * pageHeight
                                        cs.addRect(x, y, w, h)
                                        cs.fill()
                                    }
                                }
                                PdfAnnotationTool.UNDERLINE, PdfAnnotationTool.STRIKETHROUGH -> {
                                    ann.rect?.let { rect ->
                                        cs.setStrokingColor(r, g, b)
                                        cs.setLineWidth(2f)
                                        val yNorm = if (ann.tool == PdfAnnotationTool.UNDERLINE) rect.bottom else (rect.top + rect.bottom) / 2f
                                        val y = (1f - yNorm) * pageHeight
                                        cs.moveTo(rect.left * pageWidth, y)
                                        cs.lineTo(rect.right * pageWidth, y)
                                        cs.stroke()
                                    }
                                }
                                PdfAnnotationTool.TEXT_NOTE -> {
                                    val pt = ann.points.firstOrNull() ?: NormalizedPoint(0.2f, 0.2f)
                                    val cleanText = ann.text.replace(Regex("[^\\x20-\\x7E]"), " ")
                                    cs.beginText()
                                    cs.setFont(PDType1Font.HELVETICA_BOLD, 12f)
                                    cs.setNonStrokingColor(r, g, b)
                                    cs.newLineAtOffset(pt.x * pageWidth, (1f - pt.y) * pageHeight)
                                    cs.showText(cleanText.take(120))
                                    cs.endText()
                                }
                                PdfAnnotationTool.SIGNATURE -> {
                                    cs.setStrokingColor(r, g, b)
                                    cs.setLineWidth(2f)
                                    val anchor = ann.points.firstOrNull() ?: NormalizedPoint(0.5f, 0.8f)
                                    val sigBoxW = pageWidth * 0.28f
                                    val sigBoxH = pageHeight * 0.10f
                                    val originX = anchor.x * pageWidth - sigBoxW / 2f
                                    val originY = (1f - anchor.y) * pageHeight - sigBoxH / 2f
                                    for (stroke in ann.signaturePoints) {
                                        if (stroke.size >= 2) {
                                            val f = stroke.first()
                                            cs.moveTo(originX + f.x * sigBoxW, originY + (1f - f.y) * sigBoxH)
                                            for (p in stroke.drop(1)) {
                                                cs.lineTo(originX + p.x * sigBoxW, originY + (1f - p.y) * sigBoxH)
                                            }
                                            cs.stroke()
                                        }
                                    }
                                }
                                PdfAnnotationTool.NONE -> Unit
                            }
                        }
                    }
                }
                pd.save(outputFile)
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rotatePage(sourceFile: File, outputFile: File, pageIndex: Int, deltaDegrees: Int = 90): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                PDDocument.load(sourceFile).use { pd ->
                    if (pageIndex in 0 until pd.numberOfPages) {
                        val page = pd.getPage(pageIndex)
                        page.rotation = (page.rotation + deltaDegrees) % 360
                    }
                    pd.save(outputFile)
                }
                Result.success(outputFile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun deletePages(sourceFile: File, outputFile: File, pageIndicesToDelete: Set<Int>): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                PDDocument.load(sourceFile).use { pd ->
                    if (pd.numberOfPages - pageIndicesToDelete.size < 1) {
                        return@withContext Result.failure(IllegalArgumentException("Cannot delete all pages from PDF"))
                    }
                    for (idx in pageIndicesToDelete.sortedDescending()) {
                        if (idx in 0 until pd.numberOfPages) {
                            pd.removePage(idx)
                        }
                    }
                    pd.save(outputFile)
                }
                Result.success(outputFile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun reorderPages(sourceFile: File, outputFile: File, newOrderIndices: List<Int>): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                PDDocument.load(sourceFile).use { src ->
                    PDDocument().use { dst ->
                        for (idx in newOrderIndices) {
                            if (idx in 0 until src.numberOfPages) {
                                dst.importPage(src.getPage(idx))
                            }
                        }
                        dst.save(outputFile)
                    }
                }
                Result.success(outputFile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun extractPages(sourceFile: File, outputFile: File, pageIndices: List<Int>): Result<File> =
        reorderPages(sourceFile, outputFile, pageIndices)

    suspend fun splitPdf(sourceFile: File, outputDir: File, splitAtPages: Int = 1): Result<List<File>> =
        withContext(Dispatchers.IO) {
            try {
                outputDir.mkdirs()
                val outFiles = mutableListOf<File>()
                PDDocument.load(sourceFile).use { pd ->
                    val splitter = Splitter().apply { setSplitAtPage(splitAtPages.coerceAtLeast(1)) }
                    val parts = splitter.split(pd)
                    val base = sourceFile.nameWithoutExtension
                    parts.forEachIndexed { idx, partDoc ->
                        val outFile = File(outputDir, "${base}_part${idx + 1}.pdf")
                        partDoc.use { it.save(outFile) }
                        outFiles.add(outFile)
                    }
                }
                Result.success(outFiles)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun mergePdfs(sourceFiles: List<File>, outputFile: File): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val merger = PDFMergerUtility()
                merger.destinationFileName = outputFile.absolutePath
                for (f in sourceFiles) {
                    merger.addSource(f)
                }
                merger.mergeDocuments(null)
                Result.success(outputFile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun compressPdf(sourceFile: File, outputFile: File, quality: Int = 65): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val pageCount = getPageCountNative(sourceFile)
                if (pageCount <= 0) return@withContext Result.failure(IllegalStateException("Invalid PDF"))
                PDDocument().use { outDoc ->
                    for (i in 0 until pageCount) {
                        val bmp = renderPageBitmap(sourceFile, i, targetWidthPx = 1000) ?: continue
                        val page = PDPage(PDRectangle(bmp.width.toFloat(), bmp.height.toFloat()))
                        outDoc.addPage(page)
                        val pdImg = JPEGFactory.createFromImage(outDoc, bmp, quality / 100f)
                        PDPageContentStream(outDoc, page).use { cs ->
                            cs.drawImage(pdImg, 0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
                        }
                        bmp.recycle()
                    }
                    outDoc.save(outputFile)
                }
                Result.success(outputFile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun protectPdfWithPassword(
        sourceFile: File,
        outputFile: File,
        userPassword: String,
        ownerPassword: String = userPassword
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(sourceFile).use { pd ->
                val ap = AccessPermission()
                val policy = StandardProtectionPolicy(ownerPassword, userPassword, ap).apply {
                    encryptionKeyLength = 128
                }
                pd.protect(policy)
                pd.save(outputFile)
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportPdfToImages(
        sourceFile: File,
        outputDir: File,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        try {
            outputDir.mkdirs()
            val count = getPageCountNative(sourceFile)
            val ext = if (format == Bitmap.CompressFormat.JPEG) "jpg" else "png"
            val base = sourceFile.nameWithoutExtension
            val outList = mutableListOf<File>()
            for (i in 0 until count) {
                val bmp = renderPageBitmap(sourceFile, i, 1440) ?: continue
                val outFile = File(outputDir, "${base}_page_${i + 1}.$ext")
                FileOutputStream(outFile).use { fos ->
                    bmp.compress(format, 92, fos)
                }
                bmp.recycle()
                outList.add(outFile)
            }
            Result.success(outList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun imagesToPdf(imageUris: List<Uri>, outputFile: File): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
                PDDocument().use { pdDoc ->
                    var addedPages = 0
                    for (uri in imageUris) {
                        val bmp = if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
                            BitmapFactory.decodeFile(uri.path)
                        } else {
                            context.contentResolver.openInputStream(uri)?.use {
                                BitmapFactory.decodeStream(it)
                            }
                        } ?: continue
                        try {
                            val w = bmp.width.coerceAtLeast(1).toFloat()
                            val h = bmp.height.coerceAtLeast(1).toFloat()
                            val page = PDPage(PDRectangle(w, h))
                            pdDoc.addPage(page)
                            val pdImage = if (bmp.hasAlpha()) {
                                LosslessFactory.createFromImage(pdDoc, bmp)
                            } else {
                                JPEGFactory.createFromImage(pdDoc, bmp, 0.90f)
                            }
                            PDPageContentStream(pdDoc, page).use { cs ->
                                cs.drawImage(pdImage, 0f, 0f, w, h)
                            }
                            addedPages++
                        } finally {
                            bmp.recycle()
                        }
                    }
                    if (addedPages == 0) {
                        return@withContext Result.failure(IllegalArgumentException("No readable images to convert to PDF"))
                    }
                    pdDoc.save(outputFile)
                }
                Result.success(outputFile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun printPdf(pdfFile: File, jobName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(jobName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                try {
                    FileInputStream(pdfFile).use { input ->
                        FileOutputStream(destination?.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        }
        printManager.print(jobName, adapter, PrintAttributes.Builder().build())
    }
}
