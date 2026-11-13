package com.example.domain.converter

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.example.data.db.LocalDataRepository
import com.example.data.storage.AdvancedStorageOperations
import com.example.domain.image.ImageToolsEngine
import com.example.domain.office.OfficeEngine
import com.example.domain.pdf.PdfEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

data class ConversionOption(
    val sourceExts: Set<String>,
    val targetFormat: String,
    val label: String,
    val isSupported: Boolean = true,
    val note: String = ""
)

class ConversionEngine(
    private val context: Context,
    private val localRepo: LocalDataRepository,
    private val storageOps: AdvancedStorageOperations
) {
    private val pdfEngine = PdfEngine(context)
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        val ALL_CONVERSIONS = listOf(
            ConversionOption(setOf("docx", "docm", "dotx", "odt", "rtf"), "PDF", "Document to PDF"),
            ConversionOption(setOf("pptx", "ppsx", "odp"), "PDF", "Presentation to PDF"),
            ConversionOption(setOf("xlsx", "xlsm", "ods", "csv", "tsv"), "PDF", "Spreadsheet to PDF"),
            ConversionOption(setOf("xlsx", "xlsm", "ods"), "CSV", "Spreadsheet to CSV"),
            ConversionOption(setOf("txt", "log", "md", "json", "xml"), "PDF", "Text to PDF"),
            ConversionOption(setOf("pdf"), "PNG", "PDF Pages to PNG"),
            ConversionOption(setOf("pdf"), "JPG", "PDF Pages to JPG"),
            ConversionOption(setOf("png", "webp", "bmp", "gif", "heic", "heif"), "JPG", "Image to JPG"),
            ConversionOption(setOf("jpg", "jpeg", "webp", "bmp", "gif", "heic", "heif"), "PNG", "Image to PNG"),
            ConversionOption(setOf("png", "jpg", "jpeg", "bmp"), "WEBP", "Image to WEBP"),
            ConversionOption(setOf("png", "jpg", "jpeg", "webp", "bmp", "heic"), "PDF", "Image to PDF"),
            ConversionOption(setOf("csv"), "JSON", "CSV to JSON"),
            ConversionOption(setOf("json"), "CSV", "JSON Array to CSV"),
            ConversionOption(setOf("mp4", "mkv", "mov", "3gp", "webm"), "M4A", "Video to Audio (M4A)"),
            ConversionOption(setOf("flac", "mp3", "m4a", "aac", "ogg", "opus"), "WAV", "Audio to PCM WAV"),
            ConversionOption(
                sourceExts = setOf("wav"),
                targetFormat = "MP3",
                label = "WAV to MP3",
                isSupported = false,
                note = "Android MediaCodec has no native MP3 encoder and GPL LAME is excluded for license compatibility. Use WAV/M4A."
            )
        )

        fun getOptionsForExtension(ext: String): List<ConversionOption> {
            val lower = ext.lowercase()
            return ALL_CONVERSIONS.filter { lower in it.sourceExts }
        }
    }

    fun getOutputDir(): File {
        return File(context.filesDir, "Converted").apply { mkdirs() }
    }

    fun enqueueConversion(
        sourceUri: Uri,
        sourceName: String,
        sourceExt: String,
        targetFormat: String
    ) {
        scope.launch {
            val jobId = localRepo.createConversionJob(sourceName, sourceUri.toString(), targetFormat)
            val coroutineJob = scope.launch {
                runConversionJob(jobId, sourceUri, sourceName, sourceExt.lowercase(), targetFormat.uppercase())
            }
            activeJobs[jobId] = coroutineJob
            coroutineJob.invokeOnCompletion { activeJobs.remove(jobId) }
        }
    }

    fun cancelJob(jobId: Long) {
        activeJobs[jobId]?.cancel()
        scope.launch {
            localRepo.updateConversionJob(jobId, "CANCELLED", 0, error = "Cancelled by user")
        }
    }

    private suspend fun runConversionJob(
        jobId: Long,
        sourceUri: Uri,
        sourceName: String,
        ext: String,
        target: String
    ) {
        try {
            localRepo.updateConversionJob(jobId, "RUNNING", 10)
            val cacheFile = storageOps.copyUriToTempCache(sourceUri, ext)
                ?: throw IllegalStateException("Cannot read source file")
            localRepo.updateConversionJob(jobId, "RUNNING", 25)

            val outDir = getOutputDir()
            val baseName = sourceName.substringBeforeLast('.', sourceName)

            val outputPath: String = when {
                target == "MP3" && ext == "wav" -> {
                    throw UnsupportedOperationException(
                        "WAV to MP3 encoding is not supported natively on Android without GPL LAME encoder."
                    )
                }
                ext in setOf("docx", "docm", "dotx", "odt", "rtf") && target == "PDF" -> {
                    val doc = OfficeEngine.parseWordDocument(cacheFile, ext)
                    val outFile = uniqueFile(outDir, "$baseName.pdf")
                    val lines = doc.fullPlainText.lines()
                    localRepo.updateConversionJob(jobId, "RUNNING", 65)
                    OfficeEngine.exportLinesToPdf(doc.title, lines, outFile).getOrThrow()
                    outFile.absolutePath
                }
                ext in setOf("pptx", "ppsx", "odp") && target == "PDF" -> {
                    val pres = OfficeEngine.parsePresentation(cacheFile, ext)
                    val outFile = uniqueFile(outDir, "$baseName.pdf")
                    val lines = pres.slides.flatMap { s ->
                        listOf("--- Slide ${s.index}: ${s.title} ---") + s.bodyLines + listOf("")
                    }
                    localRepo.updateConversionJob(jobId, "RUNNING", 65)
                    OfficeEngine.exportLinesToPdf(pres.title, lines, outFile).getOrThrow()
                    outFile.absolutePath
                }
                ext in setOf("xlsx", "xlsm", "ods", "csv", "tsv") && target == "PDF" -> {
                    val wb = OfficeEngine.parseSpreadsheet(cacheFile, ext)
                    val outFile = uniqueFile(outDir, "$baseName.pdf")
                    val lines = wb.sheets.flatMap { sheet ->
                        listOf("=== ${sheet.name} ===") + sheet.rows.map { r -> r.joinToString(" | ") { it.value } }
                    }
                    localRepo.updateConversionJob(jobId, "RUNNING", 65)
                    OfficeEngine.exportLinesToPdf(wb.title, lines, outFile).getOrThrow()
                    outFile.absolutePath
                }
                ext in setOf("xlsx", "xlsm", "ods") && target == "CSV" -> {
                    val wb = OfficeEngine.parseSpreadsheet(cacheFile, ext)
                    val firstSheet = wb.sheets.firstOrNull() ?: throw IllegalStateException("No sheets found")
                    val outFile = uniqueFile(outDir, "$baseName.csv")
                    OfficeEngine.exportSheetToCsv(firstSheet, outFile).getOrThrow()
                    outFile.absolutePath
                }
                ext in setOf("txt", "log", "md", "json", "xml") && target == "PDF" -> {
                    val lines = cacheFile.bufferedReader().useLines { it.take(5000).toList() }
                    val outFile = uniqueFile(outDir, "$baseName.pdf")
                    localRepo.updateConversionJob(jobId, "RUNNING", 65)
                    OfficeEngine.exportLinesToPdf(sourceName, lines, outFile).getOrThrow()
                    outFile.absolutePath
                }
                ext == "pdf" && (target == "PNG" || target == "JPG") -> {
                    val fmt = if (target == "JPG") Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
                    val pageOutDir = File(outDir, "${baseName}_pages_${System.currentTimeMillis()}").apply { mkdirs() }
                    localRepo.updateConversionJob(jobId, "RUNNING", 55)
                    val imgs = pdfEngine.exportPdfToImages(cacheFile, pageOutDir, fmt).getOrThrow()
                    imgs.firstOrNull()?.absolutePath ?: pageOutDir.absolutePath
                }
                ext in setOf("png", "jpg", "jpeg", "webp", "bmp", "gif", "heic", "heif") && target == "PDF" -> {
                    val outFile = uniqueFile(outDir, "$baseName.pdf")
                    localRepo.updateConversionJob(jobId, "RUNNING", 60)
                    pdfEngine.imagesToPdf(listOf(Uri.fromFile(cacheFile)), outFile).getOrThrow()
                    outFile.absolutePath
                }
                ext in setOf("png", "jpg", "jpeg", "webp", "bmp", "gif", "heic", "heif") && target in setOf("PNG", "JPG", "WEBP") -> {
                    val fmt = when (target) {
                        "PNG" -> Bitmap.CompressFormat.PNG
                        "WEBP" -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
                            Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                        else -> Bitmap.CompressFormat.JPEG
                    }
                    val outFile = uniqueFile(outDir, "$baseName.${target.lowercase()}")
                    localRepo.updateConversionJob(jobId, "RUNNING", 60)
                    ImageToolsEngine.transformAndSaveImage(
                        context = context,
                        sourceUri = Uri.fromFile(cacheFile),
                        outputFile = outFile,
                        format = fmt,
                        quality = 92
                    ).getOrThrow()
                    outFile.absolutePath
                }
                ext == "csv" && target == "JSON" -> {
                    val wb = OfficeEngine.parseSpreadsheet(cacheFile, "csv")
                    val rows = wb.sheets.firstOrNull()?.rows ?: emptyList()
                    val jsonArr = JSONArray()
                    if (rows.isNotEmpty()) {
                        val headers = rows.first().map { it.value.ifBlank { "col_${it.colIndex}" } }
                        for (row in rows.drop(1)) {
                            val obj = JSONObject()
                            headers.forEachIndexed { idx, h ->
                                obj.put(h, row.getOrNull(idx)?.value ?: "")
                            }
                            jsonArr.put(obj)
                        }
                    }
                    val outFile = uniqueFile(outDir, "$baseName.json")
                    outFile.writeText(jsonArr.toString(2))
                    outFile.absolutePath
                }
                ext == "json" && target == "CSV" -> {
                    val raw = cacheFile.readText()
                    val arr = JSONArray(raw.trim())
                    val keys = linkedSetOf<String>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val it = obj.keys()
                        while (it.hasNext()) keys.add(it.next())
                    }
                    val headerList = keys.toList()
                    val outFile = uniqueFile(outDir, "$baseName.csv")
                    outFile.bufferedWriter().use { w ->
                        w.appendLine(headerList.joinToString(","))
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            val line = headerList.joinToString(",") { k ->
                                val v = obj.optString(k, "").replace("\"", "\"\"")
                                if (v.contains(",") || v.contains("\"")) "\"$v\"" else v
                            }
                            w.appendLine(line)
                        }
                    }
                    outFile.absolutePath
                }
                ext in setOf("mp4", "mkv", "mov", "3gp", "webm") && target == "M4A" -> {
                    val outFile = uniqueFile(outDir, "$baseName.m4a")
                    extractAudioTrackFromVideo(cacheFile, outFile) { pct ->
                        scope.launch { localRepo.updateConversionJob(jobId, "RUNNING", 25 + (pct * 70 / 100)) }
                    }
                    outFile.absolutePath
                }
                ext in setOf("flac", "mp3", "m4a", "aac", "ogg", "opus") && target == "WAV" -> {
                    val outFile = uniqueFile(outDir, "$baseName.wav")
                    decodeAudioToWav(cacheFile, outFile)
                    outFile.absolutePath
                }
                else -> throw UnsupportedOperationException("Conversion from $ext to $target is not supported")
            }

            localRepo.updateConversionJob(
                id = jobId,
                status = "COMPLETED",
                progress = 100,
                outputUri = Uri.fromFile(File(outputPath)).toString(),
                outputPath = outputPath
            )
        } catch (e: Exception) {
            localRepo.updateConversionJob(
                id = jobId,
                status = "FAILED",
                progress = 0,
                error = e.message ?: "Conversion failed"
            )
        }
    }

    private fun extractAudioTrackFromVideo(videoFile: File, outM4a: File, onPercent: (Int) -> Unit) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(videoFile.absolutePath)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = fmt
                    break
                }
            }
            if (audioTrackIndex < 0 || audioFormat == null) {
                throw IllegalStateException("No audio track found in video")
            }
            extractor.selectTrack(audioTrackIndex)
            val durationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1L)
            } else 1L

            muxer = MediaMuxer(outM4a.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val writeTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val bufSize = if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(64 * 1024)
            } else 256 * 1024
            val buffer = ByteBuffer.allocate(bufSize)
            val info = MediaCodec.BufferInfo()

            while (true) {
                info.offset = 0
                info.size = extractor.readSampleData(buffer, 0)
                if (info.size < 0) break
                info.presentationTimeUs = extractor.sampleTime
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(writeTrack, buffer, info)
                onPercent(((info.presentationTimeUs * 100) / durationUs).toInt().coerceIn(0, 99))
                extractor.advance()
            }
            muxer.stop()
        } finally {
            try { muxer?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private fun decodeAudioToWav(inputFile: File, outWav: File) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(inputFile.absolutePath)
            var audioTrack = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                    audioTrack = i
                    format = f
                    break
                }
            }
            if (audioTrack < 0 || format == null) throw IllegalStateException("No audio stream found")
            extractor.selectTrack(audioTrack)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            FileOutputStream(outWav).use { fos ->
                // Write 44-byte placeholder WAV header
                fos.write(ByteArray(44))
                var totalPcmBytes = 0L
                val info = MediaCodec.BufferInfo()
                var sawInputEos = false
                var sawOutputEos = false

                while (!sawOutputEos) {
                    if (!sawInputEos) {
                        val inIdx = codec.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val inBuf = codec.getInputBuffer(inIdx)!!
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEos = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                    if (outIdx >= 0) {
                        val outBuf = codec.getOutputBuffer(outIdx)!!
                        val chunk = ByteArray(info.size)
                        outBuf.get(chunk)
                        outBuf.clear()
                        if (chunk.isNotEmpty()) {
                            fos.write(chunk)
                            totalPcmBytes += chunk.size
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEos = true
                        }
                    } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val outFmt = codec.outputFormat
                        if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }

                // Patch WAV header
                RandomAccessFile(outWav, "rw").use { raf ->
                    raf.seek(0)
                    raf.write(buildWavHeader(totalPcmBytes, sampleRate, channels, 16))
                }
            }
        } finally {
            try { codec?.stop(); codec?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private fun buildWavHeader(pcmBytes: Long, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val totalDataLen = pcmBytes + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val bb = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray())
        bb.putInt(totalDataLen.toInt())
        bb.put("WAVEfmt ".toByteArray())
        bb.putInt(16)
        bb.putShort(1.toShort()) // PCM
        bb.putShort(channels.toShort())
        bb.putInt(sampleRate)
        bb.putInt(byteRate)
        bb.putShort((channels * bitsPerSample / 8).toShort())
        bb.putShort(bitsPerSample.toShort())
        bb.put("data".toByteArray())
        bb.putInt(pcmBytes.toInt())
        return bb.array()
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var idx = 1
        while (f.exists()) {
            f = File(dir, if (ext.isNotEmpty()) "${base}_$idx.$ext" else "${base}_$idx")
            idx++
        }
        return f
    }
}
