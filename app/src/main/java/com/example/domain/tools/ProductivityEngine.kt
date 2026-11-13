package com.example.domain.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Xml
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.io.StringReader
import java.nio.ByteBuffer
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.coroutines.resume

enum class DiffOp { EQUAL, ADDED, REMOVED }

data class DiffLine(
    val op: DiffOp,
    val leftLine: Int?,
    val rightLine: Int?,
    val text: String
)

object ProductivityEngine {

    fun generateQrCodeBitmap(content: String, sizePx: Int = 512): Result<Bitmap> {
        return try {
            val hints = mapOf(EncodeHintType.MARGIN to 2)
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bmp.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            Result.success(bmp)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recognizeTextFromImageUri(context: Context, uri: Uri): Result<String> =
        suspendCancellableCoroutine { cont ->
            try {
                val image = InputImage.fromFilePath(context, uri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        if (cont.isActive) cont.resume(Result.success(result.text))
                    }
                    .addOnFailureListener { e ->
                        if (cont.isActive) cont.resume(Result.failure(e))
                    }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(Result.failure(e))
            }
        }

    suspend fun scanBarcodesFromImageUri(context: Context, uri: Uri): Result<List<String>> =
        suspendCancellableCoroutine { cont ->
            try {
                val image = InputImage.fromFilePath(context, uri)
                val scanner = BarcodeScanning.getClient()
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val values = barcodes.mapNotNull { it.rawValue }
                        if (cont.isActive) cont.resume(Result.success(values))
                    }
                    .addOnFailureListener { e ->
                        if (cont.isActive) cont.resume(Result.failure(e))
                    }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(Result.failure(e))
            }
        }

    fun formatAndValidateJson(input: String, indentSpaces: Int = 2): Result<String> {
        return try {
            val trimmed = input.trim()
            val formatted = when {
                trimmed.startsWith("{") -> JSONObject(trimmed).toString(indentSpaces)
                trimmed.startsWith("[") -> JSONArray(trimmed).toString(indentSpaces)
                else -> return Result.failure(IllegalArgumentException("JSON must start with '{' or '['"))
            }
            Result.success(formatted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun formatAndValidateXml(input: String): Result<String> {
        return try {
            // First validate via XmlPullParser
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(input))
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                parser.next()
            }

            // Pretty print with XXE disabled
            val dbf = DocumentBuilderFactory.newInstance().apply {
                try {
                    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                    setFeature("http://xml.org/sax/features/external-general-entities", false)
                    setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                } catch (_: Exception) {}
            }
            val doc = dbf.newDocumentBuilder().parse(org.xml.sax.InputSource(StringReader(input)))
            val tf = TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(OutputKeys.INDENT, "yes")
                setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            }
            val writer = java.io.StringWriter()
            tf.transform(DOMSource(doc), StreamResult(writer))
            Result.success(writer.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun computeLineDiff(leftText: String, rightText: String): List<DiffLine> {
        val a = leftText.lines().take(2000)
        val b = rightText.lines().take(2000)
        val m = a.size
        val n = b.size

        // Standard LCS DP table
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in m - 1 downTo 0) {
            for (j in n - 1 downTo 0) {
                dp[i][j] = if (a[i] == b[j]) {
                    1 + dp[i + 1][j + 1]
                } else {
                    maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }

        val out = mutableListOf<DiffLine>()
        var i = 0
        var j = 0
        while (i < m && j < n) {
            when {
                a[i] == b[j] -> {
                    out.add(DiffLine(DiffOp.EQUAL, i + 1, j + 1, a[i]))
                    i++
                    j++
                }
                dp[i + 1][j] >= dp[i][j + 1] -> {
                    out.add(DiffLine(DiffOp.REMOVED, i + 1, null, a[i]))
                    i++
                }
                else -> {
                    out.add(DiffLine(DiffOp.ADDED, null, j + 1, b[j]))
                    j++
                }
            }
        }
        while (i < m) {
            out.add(DiffLine(DiffOp.REMOVED, i + 1, null, a[i]))
            i++
        }
        while (j < n) {
            out.add(DiffLine(DiffOp.ADDED, null, j + 1, b[j]))
            j++
        }
        return out
    }

    suspend fun splitFile(sourceFile: File, outputDir: File, chunkSizeBytes: Long): Result<List<File>> =
        withContext(Dispatchers.IO) {
            try {
                outputDir.mkdirs()
                val safeChunk = chunkSizeBytes.coerceAtLeast(1024L)
                val outFiles = mutableListOf<File>()
                sourceFile.inputStream().use { input ->
                    val buf = ByteArray(16 * 1024)
                    var partIdx = 1
                    var bytesInCurrentPart = 0L
                    var currentOutFile = File(outputDir, "${sourceFile.name}.part%03d".format(partIdx))
                    var currentFos = FileOutputStream(currentOutFile)
                    outFiles.add(currentOutFile)

                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        var offset = 0
                        while (offset < read) {
                            val remainingInChunk = (safeChunk - bytesInCurrentPart).toInt()
                            val toWrite = minOf(read - offset, remainingInChunk)
                            currentFos.write(buf, offset, toWrite)
                            offset += toWrite
                            bytesInCurrentPart += toWrite
                            if (bytesInCurrentPart >= safeChunk && offset < read) {
                                currentFos.close()
                                partIdx++
                                bytesInCurrentPart = 0L
                                currentOutFile = File(outputDir, "${sourceFile.name}.part%03d".format(partIdx))
                                currentFos = FileOutputStream(currentOutFile)
                                outFiles.add(currentOutFile)
                            }
                        }
                    }
                    currentFos.close()
                }
                Result.success(outFiles)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun mergeFiles(partFiles: List<File>, outputFile: File): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                outputFile.parentFile?.mkdirs()
                FileOutputStream(outputFile).use { fos ->
                    val buf = ByteArray(32 * 1024)
                    for (part in partFiles) {
                        part.inputStream().use { input ->
                            var read: Int
                            while (input.read(buf).also { read = it } != -1) {
                                fos.write(buf, 0, read)
                            }
                        }
                    }
                }
                Result.success(outputFile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun trimAudioToM4a(
        sourceFile: File,
        outputFile: File,
        startMs: Long,
        endMs: Long
    ): Result<File> = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(sourceFile.absolutePath)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            if (trackIndex < 0 || format == null) {
                return@withContext Result.failure(IllegalStateException("No audio track found"))
            }
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (!mime.contains("mp4a") && !mime.contains("aac")) {
                return@withContext Result.failure(
                    UnsupportedOperationException("Direct mux trimming supports AAC/M4A tracks ($mime requires transcoding).")
                )
            }
            extractor.selectTrack(trackIndex)
            extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val dstTrack = muxer.addTrack(format)
            muxer.start()

            val buffer = ByteBuffer.allocate(128 * 1024)
            val info = MediaCodec.BufferInfo()
            val endUs = endMs * 1000L

            while (true) {
                info.offset = 0
                info.size = extractor.readSampleData(buffer, 0)
                if (info.size < 0) break
                val sampleTime = extractor.sampleTime
                if (sampleTime > endUs) break
                info.presentationTimeUs = sampleTime - (startMs * 1000L)
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(dstTrack, buffer, info)
                extractor.advance()
            }
            muxer.stop()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }
}
