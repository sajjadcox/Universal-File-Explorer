package com.example.data.storage

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.example.data.db.OrganizationRuleEntity
import com.example.domain.model.FileCategory
import com.example.domain.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs

data class FolderComparisonEntry(
    val relativeName: String,
    val leftItem: FileItem?,
    val rightItem: FileItem?,
    val status: DiffStatus
) {
    enum class DiffStatus { ONLY_LEFT, ONLY_RIGHT, DIFFERENT, IDENTICAL }
}

data class FolderComparisonResult(
    val leftLabel: String,
    val rightLabel: String,
    val entries: List<FolderComparisonEntry>,
    val onlyLeftCount: Int,
    val onlyRightCount: Int,
    val differentCount: Int,
    val identicalCount: Int
)

data class SimilarImageGroup(
    val groupId: String,
    val averageHash: Long,
    val files: List<FileItem>
)

data class OrganizationRunReport(
    val scannedCount: Int,
    val movedCount: Int,
    val errors: List<String>
)

data class AudioLibraryTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val fileItem: FileItem
)

data class ImageAlbumSummary(
    val bucketName: String,
    val count: Int,
    val coverUri: Uri,
    val items: List<FileItem>
)

data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class CacheSizeSummary(
    val appCacheBytes: Long,
    val viewerTempBytes: Long,
    val vaultTempBytes: Long
) {
    val totalBytes: Long get() = appCacheBytes + viewerTempBytes + vaultTempBytes
}

object Phase2FileOperations {

    suspend fun compareFolders(
        context: Context,
        leftTreeUri: Uri?,
        rightTreeUri: Uri?,
        computeSha256: Boolean = false
    ): FolderComparisonResult = withContext(Dispatchers.IO) {
        val leftMap = collectRelativeFiles(context, leftTreeUri)
        val rightMap = collectRelativeFiles(context, rightTreeUri)
        val allKeys = (leftMap.keys + rightMap.keys).sorted()
        val entries = mutableListOf<FolderComparisonEntry>()
        var onlyL = 0
        var onlyR = 0
        var diff = 0
        var same = 0

        for (key in allKeys) {
            val l = leftMap[key]
            val r = rightMap[key]
            val status = when {
                l != null && r == null -> {
                    onlyL++; FolderComparisonEntry.DiffStatus.ONLY_LEFT
                }
                l == null && r != null -> {
                    onlyR++; FolderComparisonEntry.DiffStatus.ONLY_RIGHT
                }
                l != null && r != null -> {
                    val quickSame = l.size == r.size && (
                        !computeSha256 || (
                            sha256Short(context, l.uri) == sha256Short(context, r.uri)
                        )
                    )
                    if (quickSame) {
                        same++; FolderComparisonEntry.DiffStatus.IDENTICAL
                    } else {
                        diff++; FolderComparisonEntry.DiffStatus.DIFFERENT
                    }
                }
                else -> FolderComparisonEntry.DiffStatus.IDENTICAL
            }
            entries.add(FolderComparisonEntry(key, l, r, status))
        }

        FolderComparisonResult(
            leftLabel = leftTreeUri?.lastPathSegment?.substringAfterLast(':') ?: "App Storage",
            rightLabel = rightTreeUri?.lastPathSegment?.substringAfterLast(':') ?: "App Storage",
            entries = entries,
            onlyLeftCount = onlyL,
            onlyRightCount = onlyR,
            differentCount = diff,
            identicalCount = same
        )
    }

    private fun collectRelativeFiles(context: Context, treeUri: Uri?): Map<String, FileItem> {
        val out = linkedMapOf<String, FileItem>()
        if (treeUri == null || treeUri.scheme == "file") {
            val root = if (treeUri?.path != null) File(treeUri.path!!) else context.filesDir
            if (!root.exists()) return out
            root.walkTopDown().maxDepth(4).forEach { f ->
                if (f.isFile && !f.absolutePath.contains("app_trash") && !f.absolutePath.contains("private_vault")) {
                    val rel = f.relativeTo(root).path
                    out[rel] = FileItem(
                        uri = Uri.fromFile(f),
                        name = f.name,
                        path = f.absolutePath,
                        extension = f.extension,
                        size = f.length(),
                        lastModified = f.lastModified(),
                        isDirectory = false
                    )
                }
            }
        } else {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return out
            fun walk(doc: DocumentFile, prefix: String, depth: Int) {
                if (depth > 4) return
                for (c in doc.listFiles()) {
                    val name = c.name ?: continue
                    val rel = if (prefix.isEmpty()) name else "$prefix/$name"
                    if (c.isDirectory) {
                        walk(c, rel, depth + 1)
                    } else {
                        out[rel] = FileItem(
                            uri = c.uri,
                            name = name,
                            path = c.uri.toString(),
                            extension = name.substringAfterLast('.', ""),
                            size = c.length(),
                            lastModified = c.lastModified(),
                            mimeType = c.type ?: "*/*",
                            isDirectory = false,
                            canWrite = c.canWrite(),
                            canDelete = c.canWrite()
                        )
                    }
                }
            }
            walk(root, "", 0)
        }
        return out
    }

    private fun sha256Short(context: Context, uri: Uri): String? {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val stream = if (uri.scheme == "file") File(uri.path ?: "").inputStream()
            else context.contentResolver.openInputStream(uri)
            stream?.use { inp ->
                val buf = ByteArray(16 * 1024)
                var r: Int
                while (inp.read(buf).also { r = it } != -1) {
                    md.update(buf, 0, r)
                }
            } ?: return null
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun findSimilarImages(
        context: Context,
        storageOps: AdvancedStorageOperations,
        hammingThreshold: Int = 8,
        limit: Int = 180
    ): List<SimilarImageGroup> = withContext(Dispatchers.IO) {
        val candidates = storageOps.searchAccessibleFiles(
            SearchFilter(category = FileCategory.IMAGES),
            limit = limit
        )
        val hashed = mutableListOf<Pair<FileItem, Long>>()
        for (item in candidates) {
            if (!isActive) break
            val h = computeImageAverageHash(context, item.uri) ?: continue
            hashed.add(item to h)
        }
        val visited = BooleanArray(hashed.size)
        val groups = mutableListOf<SimilarImageGroup>()
        for (i in hashed.indices) {
            if (visited[i]) continue
            val (anchorItem, anchorHash) = hashed[i]
            val groupMembers = mutableListOf(anchorItem)
            visited[i] = true
            for (j in (i + 1) until hashed.size) {
                if (visited[j]) continue
                val dist = java.lang.Long.bitCount(anchorHash xor hashed[j].second)
                if (dist <= hammingThreshold) {
                    visited[j] = true
                    groupMembers.add(hashed[j].first)
                }
            }
            if (groupMembers.size > 1) {
                groups.add(
                    SimilarImageGroup(
                        groupId = "sim_${i}_${anchorHash}",
                        averageHash = anchorHash,
                        files = groupMembers
                    )
                )
            }
        }
        groups
    }

    fun computeImageAverageHash(context: Context, uri: Uri): Long? {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null
            val maxDim = maxOf(boundsOpts.outWidth, boundsOpts.outHeight)
            val sample = (maxDim / 64).coerceAtLeast(1)
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = openStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
                ?: return null
            val hash = computeAverageHashFromBitmap(bmp)
            bmp.recycle()
            hash
        } catch (_: Exception) {
            null
        }
    }

    fun computeAverageHashFromBitmap(bitmap: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
        val lum = IntArray(64)
        var sum = 0L
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val px = scaled.getPixel(x, y)
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                val gray = (r * 299 + g * 587 + b * 114) / 1000
                val idx = y * 8 + x
                lum[idx] = gray
                sum += gray
            }
        }
        if (scaled !== bitmap) scaled.recycle()
        val avg = sum / 64L
        var bits = 0L
        for (i in 0 until 64) {
            if (lum[i] >= avg) {
                bits = bits or (1L shl i)
            }
        }
        return bits
    }

    suspend fun runOrganizationRules(
        context: Context,
        rules: List<OrganizationRuleEntity>,
        storageOps: AdvancedStorageOperations,
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }
    ): OrganizationRunReport = withContext(Dispatchers.IO) {
        val enabled = rules.filter { it.enabled }
        if (enabled.isEmpty()) return@withContext OrganizationRunReport(0, 0, emptyList())

        val sourceFiles = context.filesDir.listFiles()?.filter {
            it.isFile && !it.name.startsWith(".")
        } ?: emptyList()

        var moved = 0
        val errors = mutableListOf<String>()

        sourceFiles.forEachIndexed { idx, file ->
            if (!isActive) return@withContext OrganizationRunReport(idx, moved, errors)
            onProgress(idx + 1, sourceFiles.size, file.name)
            val ext = file.extension.lowercase()
            val matchedRule = enabled.firstOrNull { rule ->
                rule.extensionsCsv.split(",")
                    .map { it.trim().lowercase().removePrefix(".") }
                    .filter { it.isNotBlank() }
                    .any { pat -> pat == ext }
            }
            if (matchedRule != null) {
                val destDir = File(context.filesDir, matchedRule.targetSubfolder.ifBlank { "Organized" }).apply { mkdirs() }
                val dest = File(destDir, file.name)
                try {
                    if (!file.renameTo(dest)) {
                        val item = FileItem(
                            uri = Uri.fromFile(file),
                            name = file.name,
                            path = file.absolutePath,
                            extension = ext,
                            size = file.length()
                        )
                        val ok = storageOps.moveDocument(item, Uri.fromFile(destDir))
                        if (ok) moved++ else errors.add("Could not move ${file.name}")
                    } else {
                        moved++
                    }
                } catch (e: Exception) {
                    errors.add("${file.name}: ${e.message}")
                }
            }
        }
        OrganizationRunReport(sourceFiles.size, moved, errors)
    }

    fun shareMultipleFiles(context: Context, items: List<FileItem>): Boolean {
        val filesOnly = items.filter { !it.isDirectory }
        if (filesOnly.isEmpty()) return false
        return try {
            val uris = ArrayList<Uri>()
            for (item in filesOnly) {
                val u = if (item.uri.scheme == "file") {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        File(item.uri.path ?: "")
                    )
                } else {
                    item.uri
                }
                uris.add(u)
            }
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share ${uris.size} file(s)"))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun computeCacheSummary(context: Context): CacheSizeSummary {
        val viewerTemp = File(context.cacheDir, "viewer_temp")
        val vaultTemp = File(context.cacheDir, "vault_preview_temp")
        val vTempBytes = dirBytes(viewerTemp)
        val vaTempBytes = dirBytes(vaultTemp)
        val totalCache = dirBytes(context.cacheDir)
        return CacheSizeSummary(
            appCacheBytes = (totalCache - vTempBytes - vaTempBytes).coerceAtLeast(0L),
            viewerTempBytes = vTempBytes,
            vaultTempBytes = vaTempBytes
        )
    }

    fun clearAppCaches(context: Context, includeVaultTemp: Boolean = true): Long {
        var freed = 0L
        context.cacheDir.listFiles()?.forEach { child ->
            if (!includeVaultTemp && child.name == "vault_preview_temp") return@forEach
            freed += dirBytes(child)
            child.deleteRecursively()
        }
        return freed
    }

    private fun dirBytes(f: File): Long {
        if (!f.exists()) return 0L
        if (f.isFile) return f.length()
        return f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    suspend fun queryDeviceAudioLibrary(context: Context, limit: Int = 250): List<AudioLibraryTrack> =
        withContext(Dispatchers.IO) {
            val list = mutableListOf<AudioLibraryTrack>()
            try {
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.SIZE,
                    MediaStore.Audio.Media.DATE_MODIFIED,
                    MediaStore.Audio.Media.MIME_TYPE
                )
                context.contentResolver.query(
                    collection,
                    projection,
                    "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                    null,
                    "${MediaStore.Audio.Media.TITLE} ASC"
                )?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                    val dateCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                    val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                    while (c.moveToNext() && list.size < limit) {
                        val id = c.getLong(idCol)
                        val name = c.getString(nameCol) ?: "track_$id.mp3"
                        val uri = Uri.withAppendedPath(collection, id.toString())
                        list.add(
                            AudioLibraryTrack(
                                id = id,
                                title = c.getString(titleCol)?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.'),
                                artist = c.getString(artistCol) ?: "Unknown Artist",
                                album = c.getString(albumCol) ?: "Unknown Album",
                                durationMs = c.getLong(durCol),
                                fileItem = FileItem(
                                    uri = uri,
                                    name = name,
                                    path = uri.toString(),
                                    extension = name.substringAfterLast('.', "mp3"),
                                    size = c.getLong(sizeCol),
                                    lastModified = c.getLong(dateCol) * 1000L,
                                    mimeType = c.getString(mimeCol) ?: "audio/*"
                                )
                            )
                        )
                    }
                }
            } catch (_: Exception) {
            }
            list
        }

    suspend fun queryImageAlbums(context: Context, limit: Int = 400): List<ImageAlbumSummary> =
        withContext(Dispatchers.IO) {
            val buckets = linkedMapOf<String, MutableList<FileItem>>()
            try {
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
                val projection = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                    MediaStore.Images.Media.SIZE,
                    MediaStore.Images.Media.DATE_MODIFIED,
                    MediaStore.Images.Media.MIME_TYPE
                )
                context.contentResolver.query(
                    collection,
                    projection,
                    null,
                    null,
                    "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
                )?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val bucketCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    val sizeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                    val dateCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                    val mimeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                    var count = 0
                    while (c.moveToNext() && count < limit) {
                        val id = c.getLong(idCol)
                        val name = c.getString(nameCol) ?: "img_$id.jpg"
                        val bucket = c.getString(bucketCol) ?: "Camera"
                        val uri = Uri.withAppendedPath(collection, id.toString())
                        val item = FileItem(
                            uri = uri,
                            name = name,
                            path = uri.toString(),
                            extension = name.substringAfterLast('.', "jpg"),
                            size = c.getLong(sizeCol),
                            lastModified = c.getLong(dateCol) * 1000L,
                            mimeType = c.getString(mimeCol) ?: "image/*"
                        )
                        buckets.getOrPut(bucket) { mutableListOf() }.add(item)
                        count++
                    }
                }
            } catch (_: Exception) {
            }
            buckets.entries.map { (bucket, items) ->
                ImageAlbumSummary(bucket, items.size, items.first().uri, items)
            }.sortedByDescending { it.count }
        }

    fun parseSrtOrVtt(raw: String): List<SubtitleCue> {
        val normalized = raw.replace("\r\n", "\n").removePrefix("\uFEFF")
        val blocks = normalized.split(Regex("\n\\s*\n"))
        val cues = mutableListOf<SubtitleCue>()
        val tsRegex = Regex("""(\d{1,2}:)?(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{1,2}:)?(\d{2}):(\d{2})[,.](\d{3})""")
        for (block in blocks) {
            val lines = block.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) continue
            val tsLineIdx = lines.indexOfFirst { tsRegex.containsMatchIn(it) }
            if (tsLineIdx == -1) continue
            val match = tsRegex.find(lines[tsLineIdx]) ?: continue
            val startMs = parseTs(match.groupValues[1], match.groupValues[2], match.groupValues[3], match.groupValues[4])
            val endMs = parseTs(match.groupValues[5], match.groupValues[6], match.groupValues[7], match.groupValues[8])
            val text = lines.drop(tsLineIdx + 1).joinToString("\n").replace(Regex("<[^>]+>"), "")
            if (text.isNotBlank()) {
                cues.add(SubtitleCue(startMs, endMs, text))
            }
        }
        return cues
    }

    fun activeSubtitleForPosition(cues: List<SubtitleCue>, positionMs: Long, offsetMs: Long): String? {
        val shifted = positionMs - offsetMs
        return cues.firstOrNull { shifted in it.startMs..it.endMs }?.text
    }

    private fun parseTs(hGroup: String, mStr: String, sStr: String, msStr: String): Long {
        val h = hGroup.removeSuffix(":").toLongOrNull() ?: 0L
        val m = mStr.toLongOrNull() ?: 0L
        val s = sStr.toLongOrNull() ?: 0L
        val ms = msStr.toLongOrNull() ?: 0L
        return ((h * 3600L) + (m * 60L) + s) * 1000L + ms
    }

    private fun openStream(context: Context, uri: Uri) = try {
        if (uri.scheme == "file") File(uri.path ?: "").inputStream()
        else context.contentResolver.openInputStream(uri)
    } catch (_: Exception) {
        null
    }

    fun hammingDistance(a: Long, b: Long): Int = abs(java.lang.Long.bitCount(a xor b))
}
