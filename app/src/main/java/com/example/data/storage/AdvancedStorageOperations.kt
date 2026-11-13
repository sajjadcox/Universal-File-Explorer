package com.example.data.storage

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.example.data.db.TrashEntryEntity
import com.example.domain.model.FileCategory
import com.example.domain.model.FileItem
import com.example.domain.model.StorageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Locale

data class DuplicateGroup(
    val hash: String,
    val sizeBytes: Long,
    val files: List<FileItem>
)

data class SearchFilter(
    val query: String = "",
    val extension: String = "",
    val mimePrefix: String = "",
    val category: FileCategory? = null,
    val minSizeBytes: Long? = null,
    val maxSizeBytes: Long? = null,
    val modifiedAfterMs: Long? = null,
    val searchInsideTextContent: Boolean = false
)

class AdvancedStorageOperations(private val context: Context) {

    private val contentResolver = context.contentResolver

    fun getAvailableStorageVolumes(): List<StorageStats> {
        val list = mutableListOf<StorageStats>()
        try {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            for (vol in sm.storageVolumes) {
                val desc = vol.getDescription(context) ?: if (vol.isRemovable) "Removable Storage" else "Internal Storage"
                val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) vol.directory else null
                if (dir != null && dir.exists()) {
                    val stat = android.os.StatFs(dir.path)
                    val total = stat.blockCountLong * stat.blockSizeLong
                    val free = stat.availableBlocksLong * stat.blockSizeLong
                    list.add(
                        StorageStats(
                            name = desc,
                            path = dir.absolutePath,
                            totalBytes = total,
                            freeBytes = free,
                            usedBytes = (total - free).coerceAtLeast(0L),
                            isRemovable = vol.isRemovable
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return list
    }

    suspend fun createFile(parentTreeUri: Uri?, fileName: String, mimeType: String = "text/plain"): FileItem? =
        withContext(Dispatchers.IO) {
            try {
                if (parentTreeUri == null) {
                    val file = File(context.filesDir, fileName)
                    if (file.createNewFile()) {
                        return@withContext FileItem(
                            uri = Uri.fromFile(file),
                            name = file.name,
                            path = file.absolutePath,
                            extension = file.extension,
                            size = 0L,
                            lastModified = file.lastModified(),
                            mimeType = mimeType,
                            isDirectory = false,
                            canWrite = true,
                            canDelete = true
                        )
                    }
                    return@withContext null
                }
                val parent = DocumentFile.fromTreeUri(context, parentTreeUri)
                    ?: DocumentFile.fromSingleUri(context, parentTreeUri)
                val created = parent?.createFile(mimeType, fileName) ?: return@withContext null
                FileItem(
                    uri = created.uri,
                    name = created.name ?: fileName,
                    path = created.uri.toString(),
                    extension = (created.name ?: fileName).substringAfterLast('.', ""),
                    size = 0L,
                    lastModified = created.lastModified(),
                    mimeType = created.type ?: mimeType,
                    isDirectory = false,
                    canWrite = created.canWrite(),
                    canDelete = created.canWrite()
                )
            } catch (_: Exception) {
                null
            }
        }

    suspend fun copyDocument(
        sourceUri: Uri,
        destParentTreeUri: Uri?,
        fileName: String,
        mimeType: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val input = openInputStream(sourceUri) ?: return@withContext false
            input.use { src ->
                if (destParentTreeUri == null) {
                    val destFile = resolveUniqueFile(context.filesDir, fileName)
                    destFile.outputStream().use { dst ->
                        src.copyTo(dst, bufferSize = 16 * 1024)
                    }
                    return@withContext true
                }
                val parent = DocumentFile.fromTreeUri(context, destParentTreeUri)
                    ?: DocumentFile.fromSingleUri(context, destParentTreeUri)
                    ?: return@withContext false
                val target = parent.createFile(mimeType, fileName) ?: return@withContext false
                contentResolver.openOutputStream(target.uri)?.use { dst ->
                    src.copyTo(dst, bufferSize = 16 * 1024)
                } ?: return@withContext false
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun moveDocument(
        sourceItem: FileItem,
        destParentTreeUri: Uri?
    ): Boolean = withContext(Dispatchers.IO) {
        if (sourceItem.isDirectory) return@withContext false
        val copied = copyDocument(sourceItem.uri, destParentTreeUri, sourceItem.name, sourceItem.mimeType)
        if (!copied) return@withContext false
        try {
            if (sourceItem.uri.scheme == "file") {
                File(sourceItem.uri.path ?: "").delete()
            } else {
                val doc = DocumentFile.fromSingleUri(context, sourceItem.uri)
                    ?: DocumentFile.fromTreeUri(context, sourceItem.uri)
                doc?.delete() == true || contentResolver.delete(sourceItem.uri, null, null) > 0
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun moveToTrash(item: FileItem): TrashEntryEntity? = withContext(Dispatchers.IO) {
        if (item.isDirectory) return@withContext null
        try {
            val trashDir = File(context.filesDir, "app_trash").apply { mkdirs() }
            val safeName = "${System.currentTimeMillis()}_${item.name.replace("/", "_")}"
            val trashFile = File(trashDir, safeName)

            val input = openInputStream(item.uri) ?: return@withContext null
            input.use { src ->
                trashFile.outputStream().use { dst ->
                    src.copyTo(dst, bufferSize = 16 * 1024)
                }
            }

            // Delete original now that backup in trash succeeded
            val deleted = if (item.uri.scheme == "file") {
                File(item.uri.path ?: "").delete()
            } else {
                val doc = DocumentFile.fromSingleUri(context, item.uri)
                    ?: DocumentFile.fromTreeUri(context, item.uri)
                doc?.delete() == true || contentResolver.delete(item.uri, null, null) > 0
            }
            if (!deleted) {
                trashFile.delete()
                return@withContext null
            }

            TrashEntryEntity(
                originalName = item.name,
                originalUri = item.uri.toString(),
                trashFilePath = trashFile.absolutePath,
                mimeType = item.mimeType,
                size = item.size
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun restoreFromTrash(entry: TrashEntryEntity, destParentTreeUri: Uri?): Boolean =
        withContext(Dispatchers.IO) {
            val trashFile = File(entry.trashFilePath)
            if (!trashFile.exists()) return@withContext false
            val restored = copyDocument(Uri.fromFile(trashFile), destParentTreeUri, entry.originalName, entry.mimeType)
            if (restored) {
                trashFile.delete()
            }
            restored
        }

    fun deleteTrashFilePermanently(entry: TrashEntryEntity): Boolean {
        return try {
            val f = File(entry.trashFilePath)
            !f.exists() || f.delete()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun searchAccessibleFiles(filter: SearchFilter, limit: Int = 200): List<FileItem> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<FileItem>()
            val queryLower = filter.query.trim().lowercase(Locale.ROOT)
            val extLower = filter.extension.trim().lowercase(Locale.ROOT).removePrefix(".")
            val mimeLower = filter.mimePrefix.trim().lowercase(Locale.ROOT)

            // 1. Search App Storage files
            val appDirs = listOfNotNull(context.filesDir, context.getExternalFilesDir(null))
            for (root in appDirs) {
                root.walkTopDown().maxDepth(6).forEach { file ->
                    if (results.size >= limit) return@forEach
                    if (!file.isFile || file.parentFile?.name == "app_trash") return@forEach
                    val item = FileItem(
                        uri = Uri.fromFile(file),
                        name = file.name,
                        path = file.absolutePath,
                        extension = file.extension,
                        size = file.length(),
                        lastModified = file.lastModified(),
                        mimeType = getMimeTypeFromExtension(file.extension),
                        isDirectory = false
                    )
                    if (matchesFilter(item, filter, queryLower, extLower, mimeLower)) {
                        results.add(item)
                    }
                }
            }

            // 2. Search MediaStore
            try {
                val projection = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.SIZE,
                    MediaStore.Files.FileColumns.DATE_MODIFIED,
                    MediaStore.Files.FileColumns.MIME_TYPE
                )
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    MediaStore.Files.getContentUri("external")
                }

                contentResolver.query(
                    collection,
                    projection,
                    null,
                    null,
                    "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)

                    while (cursor.moveToNext() && results.size < limit) {
                        val name = cursor.getString(nameCol) ?: continue
                        val ext = name.substringAfterLast('.', "")
                        val mime = cursor.getString(mimeCol) ?: getMimeTypeFromExtension(ext)
                        val size = cursor.getLong(sizeCol)
                        val dateMs = cursor.getLong(dateCol) * 1000L
                        val uri = Uri.withAppendedPath(collection, cursor.getLong(idCol).toString())

                        val item = FileItem(
                            uri = uri,
                            name = name,
                            path = uri.toString(),
                            extension = ext,
                            size = size,
                            lastModified = dateMs,
                            mimeType = mime,
                            isDirectory = false
                        )
                        if (matchesFilter(item, filter, queryLower, extLower, mimeLower)) {
                            results.add(item)
                        }
                    }
                }
            } catch (_: Exception) {
            }

            results
        }

    private fun matchesFilter(
        item: FileItem,
        filter: SearchFilter,
        queryLower: String,
        extLower: String,
        mimeLower: String
    ): Boolean {
        if (extLower.isNotEmpty() && item.extension.lowercase(Locale.ROOT) != extLower) return false
        if (mimeLower.isNotEmpty() && !item.mimeType.lowercase(Locale.ROOT).contains(mimeLower)) return false
        if (filter.category != null && !filter.category.matches(item.extension, item.mimeType)) return false
        if (filter.minSizeBytes != null && item.size < filter.minSizeBytes) return false
        if (filter.maxSizeBytes != null && item.size > filter.maxSizeBytes) return false
        if (filter.modifiedAfterMs != null && item.lastModified < filter.modifiedAfterMs) return false

        if (queryLower.isNotEmpty()) {
            val nameMatch = item.name.lowercase(Locale.ROOT).contains(queryLower)
            if (nameMatch) return true
            if (filter.searchInsideTextContent && item.size in 1..(512 * 1024)) {
                val isText = item.mimeType.startsWith("text/") || item.extension.lowercase() in setOf(
                    "txt", "log", "md", "json", "xml", "csv", "kt", "java", "py", "js", "html", "sql"
                )
                if (isText) {
                    val snippet = readTextPreview(item.uri, 256 * 1024)
                    if (snippet.lowercase(Locale.ROOT).contains(queryLower)) return true
                }
            }
            return false
        }
        return true
    }

    suspend fun findLargeFiles(minSizeBytes: Long = 5L * 1024L * 1024L): List<FileItem> =
        withContext(Dispatchers.IO) {
            searchAccessibleFiles(SearchFilter(minSizeBytes = minSizeBytes), limit = 100)
                .sortedByDescending { it.size }
        }

    suspend fun findDuplicateFiles(): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val allFiles = searchAccessibleFiles(SearchFilter(minSizeBytes = 1024L), limit = 500)
        val bySize = allFiles.groupBy { it.size }.filterValues { it.size > 1 }
        val groups = mutableListOf<DuplicateGroup>()

        for ((size, candidates) in bySize) {
            val byHash = mutableMapOf<String, MutableList<FileItem>>()
            for (item in candidates) {
                val hash = calculatePartialOrFullHash(item.uri, "SHA-256") ?: continue
                byHash.getOrPut(hash) { mutableListOf() }.add(item)
            }
            for ((hash, dupes) in byHash) {
                if (dupes.size > 1) {
                    groups.add(DuplicateGroup(hash = hash, sizeBytes = size, files = dupes))
                }
            }
        }
        groups.sortedByDescending { it.sizeBytes * it.files.size }
    }

    suspend fun findEmptyFolders(treeUri: Uri?): List<FileItem> = withContext(Dispatchers.IO) {
        val empty = mutableListOf<FileItem>()
        try {
            if (treeUri == null) {
                context.filesDir.walkTopDown().maxDepth(5).forEach { dir ->
                    if (dir.isDirectory && dir.name != "app_trash" && (dir.listFiles()?.isEmpty() == true)) {
                        empty.add(
                            FileItem(
                                uri = Uri.fromFile(dir),
                                name = dir.name,
                                path = dir.absolutePath,
                                isDirectory = true,
                                lastModified = dir.lastModified(),
                                childrenCount = 0
                            )
                        )
                    }
                }
            } else {
                val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext empty
                collectEmptyDocumentFolders(root, 0, empty)
            }
        } catch (_: Exception) {
        }
        empty
    }

    private fun collectEmptyDocumentFolders(doc: DocumentFile, depth: Int, out: MutableList<FileItem>) {
        if (depth > 4 || !doc.isDirectory) return
        val children = doc.listFiles()
        if (children.isEmpty() && depth > 0) {
            out.add(
                FileItem(
                    uri = doc.uri,
                    name = doc.name ?: "Folder",
                    path = doc.uri.toString(),
                    isDirectory = true,
                    lastModified = doc.lastModified(),
                    childrenCount = 0,
                    canWrite = doc.canWrite(),
                    canDelete = doc.canWrite()
                )
            )
        } else {
            for (child in children) {
                if (child.isDirectory) collectEmptyDocumentFolders(child, depth + 1, out)
            }
        }
    }

    suspend fun calculateFolderSize(uri: Uri): Pair<Long, Int> = withContext(Dispatchers.IO) {
        var totalBytes = 0L
        var fileCount = 0
        try {
            if (uri.scheme == "file") {
                val dir = File(uri.path ?: "")
                dir.walkTopDown().forEach { f ->
                    if (f.isFile) {
                        totalBytes += f.length()
                        fileCount++
                    }
                }
            } else {
                val doc = DocumentFile.fromTreeUri(context, uri) ?: DocumentFile.fromSingleUri(context, uri)
                if (doc != null) {
                    fun walk(d: DocumentFile, depth: Int) {
                        if (depth > 8) return
                        if (d.isDirectory) {
                            d.listFiles().forEach { walk(it, depth + 1) }
                        } else {
                            totalBytes += d.length()
                            fileCount++
                        }
                    }
                    walk(doc, 0)
                }
            }
        } catch (_: Exception) {
        }
        totalBytes to fileCount
    }

    suspend fun calculateHashes(uri: Uri): Map<String, String> = withContext(Dispatchers.IO) {
        val result = linkedMapOf<String, String>()
        val algos = listOf("MD5", "SHA-1", "SHA-256", "SHA-512")
        try {
            val digests = algos.associateWith { MessageDigest.getInstance(it) }
            val input = openInputStream(uri) ?: return@withContext mapOf("Error" to "Unable to open file")
            input.use { stream ->
                val buffer = ByteArray(16 * 1024)
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    digests.values.forEach { it.update(buffer, 0, read) }
                }
            }
            for ((name, d) in digests) {
                result[name] = d.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (e: Exception) {
            result["Error"] = e.message ?: "Hash calculation failed"
        }
        result
    }

    private fun calculatePartialOrFullHash(uri: Uri, algorithm: String): String? {
        return try {
            val digest = MessageDigest.getInstance(algorithm)
            val input = openInputStream(uri) ?: return null
            input.use { stream ->
                val buffer = ByteArray(16 * 1024)
                var read: Int
                var totalRead = 0L
                while (stream.read(buffer).also { read = it } != -1 && totalRead < 2L * 1024L * 1024L) {
                    digest.update(buffer, 0, read)
                    totalRead += read
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun readHexChunk(uri: Uri, offset: Long, length: Int = 2048): ByteArray =
        withContext(Dispatchers.IO) {
            try {
                if (uri.scheme == "file") {
                    val file = File(uri.path ?: "")
                    if (!file.exists()) return@withContext ByteArray(0)
                    RandomAccessFile(file, "r").use { raf ->
                        if (offset >= raf.length()) return@withContext ByteArray(0)
                        raf.seek(offset)
                        val toRead = minOf(length.toLong(), raf.length() - offset).toInt()
                        val buf = ByteArray(toRead)
                        val read = raf.read(buf)
                        if (read > 0) buf.copyOf(read) else ByteArray(0)
                    }
                } else {
                    openInputStream(uri)?.use { stream ->
                        var skipped = 0L
                        while (skipped < offset) {
                            val s = stream.skip(offset - skipped)
                            if (s <= 0) {
                                if (stream.read() == -1) break else skipped++
                            } else {
                                skipped += s
                            }
                        }
                        val buf = ByteArray(length)
                        val read = stream.read(buf)
                        if (read > 0) buf.copyOf(read) else ByteArray(0)
                    } ?: ByteArray(0)
                }
            } catch (_: Exception) {
                ByteArray(0)
            }
        }

    fun readTextPreview(uri: Uri, maxBytes: Int = 512 * 1024): String {
        return try {
            openInputStream(uri)?.use { stream ->
                val buf = ByteArray(maxBytes)
                val read = stream.read(buf)
                if (read > 0) String(buf, 0, read, Charsets.UTF_8) else ""
            } ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun copyUriToTempCache(uri: Uri, suffix: String): File? = withContext(Dispatchers.IO) {
        try {
            if (uri.scheme == "file") {
                val f = File(uri.path ?: "")
                if (f.exists() && f.canRead()) return@withContext f
            }
            val cacheDir = File(context.cacheDir, "viewer_temp").apply { mkdirs() }
            // Clean old temp files > 15 mins to keep cache responsible
            val cutoff = System.currentTimeMillis() - 15 * 60 * 1000L
            cacheDir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }

            val outFile = File(cacheDir, "doc_${System.currentTimeMillis()}.${suffix.ifBlank { "bin" }}")
            openInputStream(uri)?.use { src ->
                outFile.outputStream().use { dst ->
                    src.copyTo(dst, bufferSize = 32 * 1024)
                }
            } ?: return@withContext null
            outFile
        } catch (_: Exception) {
            null
        }
    }

    fun openInputStream(uri: Uri): InputStream? {
        return try {
            if (uri.scheme == "file") {
                val f = File(uri.path ?: "")
                if (f.exists() && f.canRead()) f.inputStream() else null
            } else {
                contentResolver.openInputStream(uri)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveUniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var idx = 1
        while (candidate.exists()) {
            val newName = if (ext.isNotEmpty()) "${base}_$idx.$ext" else "${base}_$idx"
            candidate = File(dir, newName)
            idx++
        }
        return candidate
    }

    private fun getMimeTypeFromExtension(extension: String): String {
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase(Locale.ROOT))
            ?: "application/octet-stream"
    }
}
