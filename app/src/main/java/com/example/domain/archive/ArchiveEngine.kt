package com.example.domain.archive

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile as Zip4jFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

data class ArchiveEntryInfo(
    val name: String,
    val size: Long,
    val compressedSize: Long,
    val isDirectory: Boolean,
    val isEncrypted: Boolean,
    val lastModified: Long
)

data class ArchiveListingResult(
    val formatLabel: String,
    val entries: List<ArchiveEntryInfo>,
    val isPasswordProtected: Boolean = false,
    val isSplitArchive: Boolean = false,
    val isIntegrityValid: Boolean = true,
    val notes: String = ""
)

class ArchiveEngine(private val context: Context) {

    companion object {
        private const val MAX_ENTRIES = 10_000
        private const val MAX_EXTRACT_BYTES = 512L * 1024L * 1024L // 512 MB bomb cap
    }

    suspend fun listArchive(file: File, extension: String): Result<ArchiveListingResult> =
        withContext(Dispatchers.IO) {
            val ext = extension.lowercase()
            try {
                when {
                    ext in setOf("zip", "cbz", "apk", "xapk", "apks", "aab", "docx", "xlsx", "pptx", "epub", "jar") -> {
                        val zf = Zip4jFile(file)
                        val valid = zf.isValidZipFile
                        val headers = if (valid) zf.fileHeaders else emptyList()
                        val entries = headers.take(MAX_ENTRIES).map { fh ->
                            ArchiveEntryInfo(
                                name = fh.fileName,
                                size = fh.uncompressedSize,
                                compressedSize = fh.compressedSize,
                                isDirectory = fh.isDirectory,
                                isEncrypted = fh.isEncrypted,
                                lastModified = fh.lastModifiedTimeEpoch
                            )
                        }
                        Result.success(
                            ArchiveListingResult(
                                formatLabel = ext.uppercase(),
                                entries = entries,
                                isPasswordProtected = zf.isEncrypted,
                                isSplitArchive = zf.isSplitArchive,
                                isIntegrityValid = valid
                            )
                        )
                    }
                    ext == "7z" -> {
                        val entries = mutableListOf<ArchiveEntryInfo>()
                        SevenZFile.builder().setFile(file).get().use { sz ->
                            var count = 0
                            for (e in sz.entries) {
                                if (count++ >= MAX_ENTRIES) break
                                entries.add(
                                    ArchiveEntryInfo(
                                        name = e.name ?: "entry",
                                        size = e.size,
                                        compressedSize = -1L,
                                        isDirectory = e.isDirectory,
                                        isEncrypted = false,
                                        lastModified = if (e.hasLastModifiedDate) e.lastModifiedDate.time else 0L
                                    )
                                )
                            }
                        }
                        Result.success(ArchiveListingResult("7Z", entries))
                    }
                    ext in setOf("tar", "tgz", "gz", "bz2", "xz") -> {
                        val entries = listTarOrCompressed(file, ext)
                        Result.success(ArchiveListingResult(ext.uppercase(), entries))
                    }
                    ext in setOf("rar", "cbr") -> {
                        val header = ByteArray(16)
                        FileInputStream(file).use { it.read(header) }
                        val isRar5 = header.size >= 7 && header[6] == 0x01.toByte()
                        val ver = if (isRar5) "RAR5" else "RAR4"
                        Result.success(
                            ArchiveListingResult(
                                formatLabel = "$ver (${ext.uppercase()})",
                                entries = emptyList(),
                                isIntegrityValid = String(header.take(4).toByteArray()) == "Rar!",
                                notes = "Detected $ver signature. Extraction of RAR archives is not included because the UnRAR license is incompatible with permissive OSI distribution."
                            )
                        )
                    }
                    else -> Result.failure(UnsupportedOperationException("Unsupported archive format: $ext"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun listTarOrCompressed(file: File, ext: String): List<ArchiveEntryInfo> {
        val entries = mutableListOf<ArchiveEntryInfo>()
        val isTar = ext == "tar" || ext == "tgz" || file.name.lowercase().contains(".tar.")
        val raw = BufferedInputStream(FileInputStream(file))
        val decompressed: InputStream = when {
            ext == "gz" || ext == "tgz" || file.name.endsWith(".tar.gz", true) -> GzipCompressorInputStream(raw)
            ext == "bz2" || file.name.endsWith(".tar.bz2", true) -> BZip2CompressorInputStream(raw)
            ext == "xz" || file.name.endsWith(".tar.xz", true) -> XZCompressorInputStream(raw)
            else -> raw
        }

        decompressed.use { stream ->
            if (isTar) {
                TarArchiveInputStream(stream).use { tis ->
                    var count = 0
                    while (count < MAX_ENTRIES) {
                        val entry = tis.nextEntry ?: break
                        entries.add(
                            ArchiveEntryInfo(
                                name = entry.name,
                                size = entry.size,
                                compressedSize = -1L,
                                isDirectory = entry.isDirectory,
                                isEncrypted = false,
                                lastModified = entry.lastModifiedDate?.time ?: 0L
                            )
                        )
                        count++
                    }
                }
            } else {
                val innerName = file.name.substringBeforeLast('.')
                entries.add(
                    ArchiveEntryInfo(
                        name = innerName.ifBlank { "decompressed_file" },
                        size = -1L,
                        compressedSize = file.length(),
                        isDirectory = false,
                        isEncrypted = false,
                        lastModified = file.lastModified()
                    )
                )
            }
        }
        return entries
    }

    suspend fun extractToDirectory(
        archiveFile: File,
        extension: String,
        destDir: File,
        selectedEntryNames: Set<String>? = null,
        password: String? = null,
        onProgress: (Int) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        val ext = extension.lowercase()
        try {
            destDir.mkdirs()
            val canonicalDest = destDir.canonicalPath
            var extractedCount = 0
            var totalBytesExtracted = 0L

            when {
                ext in setOf("zip", "cbz") -> {
                    val zf = Zip4jFile(archiveFile)
                    if (zf.isEncrypted) {
                        if (password.isNullOrEmpty()) {
                            return@withContext Result.failure(SecurityException("Password required for encrypted ZIP"))
                        }
                        zf.setPassword(password.toCharArray())
                    }
                    val headers = zf.fileHeaders
                    val targets = if (selectedEntryNames.isNullOrEmpty()) headers
                    else headers.filter { selectedEntryNames.contains(it.fileName) }

                    targets.forEachIndexed { index, fh ->
                        if (extractedCount >= MAX_ENTRIES) throw SecurityException("Archive entry limit exceeded (Bomb protection)")
                        val safeOut = resolveSafeDestination(destDir, canonicalDest, fh.fileName)
                        if (fh.isDirectory) {
                            safeOut.mkdirs()
                        } else {
                            safeOut.parentFile?.mkdirs()
                            zf.getInputStream(fh).use { input ->
                                totalBytesExtracted += copyWithBombGuard(input, safeOut, totalBytesExtracted)
                            }
                            extractedCount++
                        }
                        if (targets.isNotEmpty()) onProgress(((index + 1) * 100) / targets.size)
                    }
                }
                ext == "7z" -> {
                    val szBuilder = SevenZFile.builder().setFile(archiveFile)
                    if (!password.isNullOrEmpty()) szBuilder.setPassword(password.toCharArray())
                    szBuilder.get().use { sz ->
                        var entry = sz.nextEntry
                        while (entry != null) {
                            if (selectedEntryNames.isNullOrEmpty() || selectedEntryNames.contains(entry.name)) {
                                if (extractedCount >= MAX_ENTRIES) throw SecurityException("Archive entry limit exceeded")
                                val safeOut = resolveSafeDestination(destDir, canonicalDest, entry.name)
                                if (entry.isDirectory) {
                                    safeOut.mkdirs()
                                } else {
                                    safeOut.parentFile?.mkdirs()
                                    FileOutputStream(safeOut).use { fos ->
                                        val buf = ByteArray(16 * 1024)
                                        var read: Int
                                        while (sz.read(buf).also { read = it } > 0) {
                                            totalBytesExtracted += read
                                            if (totalBytesExtracted > MAX_EXTRACT_BYTES) {
                                                throw SecurityException("Archive bomb protection: max extraction limit 512MB exceeded")
                                            }
                                            fos.write(buf, 0, read)
                                        }
                                    }
                                    extractedCount++
                                }
                            }
                            entry = sz.nextEntry
                        }
                    }
                    onProgress(100)
                }
                ext in setOf("tar", "tgz", "gz", "bz2", "xz") -> {
                    val isTar = ext == "tar" || ext == "tgz" || archiveFile.name.lowercase().contains(".tar.")
                    val raw = BufferedInputStream(FileInputStream(archiveFile))
                    val decompressed: InputStream = when {
                        ext == "gz" || ext == "tgz" || archiveFile.name.endsWith(".tar.gz", true) -> GzipCompressorInputStream(raw)
                        ext == "bz2" || archiveFile.name.endsWith(".tar.bz2", true) -> BZip2CompressorInputStream(raw)
                        ext == "xz" || archiveFile.name.endsWith(".tar.xz", true) -> XZCompressorInputStream(raw)
                        else -> raw
                    }
                    decompressed.use { stream ->
                        if (isTar) {
                            TarArchiveInputStream(stream).use { tis ->
                                var entry = tis.nextEntry
                                while (entry != null) {
                                    if (selectedEntryNames.isNullOrEmpty() || selectedEntryNames.contains(entry.name)) {
                                        if (extractedCount >= MAX_ENTRIES) throw SecurityException("Archive entry limit exceeded")
                                        val safeOut = resolveSafeDestination(destDir, canonicalDest, entry.name)
                                        if (entry.isDirectory) {
                                            safeOut.mkdirs()
                                        } else {
                                            safeOut.parentFile?.mkdirs()
                                            totalBytesExtracted += copyWithBombGuard(tis, safeOut, totalBytesExtracted)
                                            extractedCount++
                                        }
                                    }
                                    entry = tis.nextEntry
                                }
                            }
                        } else {
                            val outName = archiveFile.name.substringBeforeLast('.').ifBlank { "extracted_file" }
                            val safeOut = resolveSafeDestination(destDir, canonicalDest, outName)
                            copyWithBombGuard(stream, safeOut, totalBytesExtracted)
                            extractedCount = 1
                        }
                    }
                    onProgress(100)
                }
                else -> return@withContext Result.failure(UnsupportedOperationException("Cannot extract $ext"))
            }
            Result.success(extractedCount)
        } catch (zex: ZipException) {
            Result.failure(zex)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun extractToSafDirectory(
        archiveFile: File,
        extension: String,
        destTreeUri: Uri,
        selectedEntryNames: Set<String>? = null,
        password: String? = null,
        onProgress: (Int) -> Unit = {}
    ): Result<Int> = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "archive_extract_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val res = extractToDirectory(archiveFile, extension, tempDir, selectedEntryNames, password, onProgress)
            if (res.isFailure) return@withContext res
            val rootDoc = DocumentFile.fromTreeUri(context, destTreeUri)
                ?: return@withContext Result.failure(IllegalStateException("Invalid destination SAF folder"))
            val folderName = archiveFile.nameWithoutExtension.ifBlank { "Extracted" }
            val targetDoc = rootDoc.createDirectory(folderName) ?: rootDoc

            var copied = 0
            copyDirToSaf(tempDir, targetDoc) { copied++ }
            Result.success(copied.coerceAtLeast(res.getOrDefault(0)))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun copyDirToSaf(srcDir: File, destDoc: DocumentFile, onFileCopied: () -> Unit) {
        srcDir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                val sub = destDoc.findFile(child.name) ?: destDoc.createDirectory(child.name)
                if (sub != null) copyDirToSaf(child, sub, onFileCopied)
            } else {
                val fileDoc = destDoc.createFile("application/octet-stream", child.name) ?: return@forEach
                context.contentResolver.openOutputStream(fileDoc.uri)?.use { out ->
                    child.inputStream().use { inp -> inp.copyTo(out, 16 * 1024) }
                }
                onFileCopied()
            }
        }
    }

    suspend fun createZipArchive(
        sourceFiles: List<File>,
        outputZip: File,
        password: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (outputZip.exists()) outputZip.delete()
            val zf = if (!password.isNullOrEmpty()) {
                Zip4jFile(outputZip, password.toCharArray())
            } else {
                Zip4jFile(outputZip)
            }
            val params = ZipParameters().apply {
                if (!password.isNullOrEmpty()) {
                    isEncryptFiles = true
                    encryptionMethod = EncryptionMethod.AES
                    aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                }
            }
            for (f in sourceFiles) {
                if (f.isDirectory) zf.addFolder(f, params) else zf.addFile(f, params)
            }
            Result.success(outputZip)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun extractCbzPagesToCache(cbzFile: File): Result<List<File>> = withContext(Dispatchers.IO) {
        val outDir = File(context.cacheDir, "cbz_${cbzFile.name.hashCode()}_${cbzFile.lastModified()}")
        if (outDir.exists() && (outDir.listFiles()?.isNotEmpty() == true)) {
            val imgs = collectImageFilesSorted(outDir)
            if (imgs.isNotEmpty()) return@withContext Result.success(imgs)
        }
        val res = extractToDirectory(cbzFile, "cbz", outDir)
        if (res.isFailure) return@withContext Result.failure(res.exceptionOrNull()!!)
        val imgs = collectImageFilesSorted(outDir)
        Result.success(imgs)
    }

    private fun collectImageFilesSorted(dir: File): List<File> {
        val exts = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
        return dir.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in exts }
            .sortedBy { it.name.lowercase() }
            .toList()
    }

    private fun resolveSafeDestination(destDir: File, canonicalDest: String, entryName: String): File {
        val cleaned = entryName.replace("\\", "/")
        val candidate = File(destDir, cleaned)
        val canonicalCandidate = candidate.canonicalPath
        if (!canonicalCandidate.startsWith(canonicalDest + File.separator) && canonicalCandidate != canonicalDest) {
            throw SecurityException("ZIP Slip vulnerability blocked for entry: $entryName")
        }
        return candidate
    }

    private fun copyWithBombGuard(input: InputStream, destFile: File, currentTotal: Long): Long {
        var written = 0L
        FileOutputStream(destFile).use { fos ->
            val buf = ByteArray(16 * 1024)
            var read: Int
            while (input.read(buf).also { read = it } != -1) {
                written += read
                if (currentTotal + written > MAX_EXTRACT_BYTES) {
                    throw SecurityException("Archive bomb protection: max extracted size 512MB exceeded")
                }
                fos.write(buf, 0, read)
            }
        }
        return written
    }
}
