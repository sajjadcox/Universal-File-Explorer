package com.example.data.storage

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.example.domain.model.FileCategory
import com.example.domain.model.FileItem
import com.example.domain.model.StorageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

class StorageRepository(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    suspend fun getStorageStats(): List<StorageStats> = withContext(Dispatchers.IO) {
        val list = mutableListOf<StorageStats>()

        // 1. Internal Primary Storage
        try {
            val internalPath = Environment.getDataDirectory()
            val stat = StatFs(internalPath.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val freeBytes = availableBlocks * blockSize
            val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)

            list.add(
                StorageStats(
                    name = "Internal Storage",
                    path = internalPath.absolutePath,
                    totalBytes = totalBytes,
                    freeBytes = freeBytes,
                    usedBytes = usedBytes,
                    isRemovable = false
                )
            )
        } catch (_: Exception) {
            // Fallback for internal storage
        }

        // 2. Secondary Storage / SD Card detection
        try {
            val externalDirs = context.getExternalFilesDirs(null)
            if (externalDirs.size > 1 && externalDirs[1] != null) {
                val sdCardFile = externalDirs[1]
                val stat = StatFs(sdCardFile.path)
                val blockSize = stat.blockSizeLong
                val totalBlocks = stat.blockCountLong
                val availableBlocks = stat.availableBlocksLong

                val totalBytes = totalBlocks * blockSize
                val freeBytes = availableBlocks * blockSize
                val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)

                list.add(
                    StorageStats(
                        name = "SD Card",
                        path = sdCardFile.absolutePath,
                        totalBytes = totalBytes,
                        freeBytes = freeBytes,
                        usedBytes = usedBytes,
                        isRemovable = true
                    )
                )
            }
        } catch (_: Exception) {
            // SD Card error or not mounted
        }

        list
    }

    suspend fun getRecentFiles(limit: Int = 20): List<FileItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FileItem>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE
        )
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        try {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Files.getContentUri("external")
            }

            contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)

                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val size = cursor.getLong(sizeCol)
                    val dateModified = cursor.getLong(dateCol) * 1000 // Convert sec to ms
                    val mimeType = cursor.getString(mimeCol) ?: "application/octet-stream"
                    val contentUri = Uri.withAppendedPath(collection, id.toString())
                    val ext = name.substringAfterLast('.', "")

                    results.add(
                        FileItem(
                            uri = contentUri,
                            name = name,
                            path = contentUri.toString(),
                            extension = ext,
                            size = size,
                            lastModified = dateModified,
                            mimeType = mimeType,
                            isDirectory = false
                        )
                    )
                    count++
                }
            }
        } catch (_: Exception) {
            // MediaStore query error or restricted access
        }

        results
    }

    suspend fun getFilesForCategory(category: FileCategory): List<FileItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FileItem>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE
        )

        try {
            val (collection, selection, selectionArgs) = when (category) {
                FileCategory.IMAGES -> {
                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    } else {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    Triple(uri, null, null)
                }
                FileCategory.VIDEOS -> {
                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    } else {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    }
                    Triple(uri, null, null)
                }
                FileCategory.AUDIO -> {
                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    } else {
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
                    Triple(uri, null, null)
                }
                FileCategory.DOWNLOADS -> {
                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    } else {
                        MediaStore.Files.getContentUri("external")
                    }
                    Triple(uri, null, null)
                }
                else -> {
                    // Documents, Archives, Apps, eBooks, Other
                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    } else {
                        MediaStore.Files.getContentUri("external")
                    }
                    Triple(uri, null, null)
                }
            }

            contentResolver.query(collection, projection, selection, selectionArgs, "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: continue
                    val ext = name.substringAfterLast('.', "")
                    val mimeType = cursor.getString(mimeCol) ?: getMimeTypeFromExtension(ext)

                    if (category == FileCategory.IMAGES || category == FileCategory.VIDEOS ||
                        category == FileCategory.AUDIO || category == FileCategory.DOWNLOADS ||
                        category.matches(ext, mimeType)
                    ) {
                        val id = cursor.getLong(idCol)
                        val size = cursor.getLong(sizeCol)
                        val dateModified = cursor.getLong(dateCol) * 1000
                        val contentUri = Uri.withAppendedPath(collection, id.toString())

                        results.add(
                            FileItem(
                                uri = contentUri,
                                name = name,
                                path = contentUri.toString(),
                                extension = ext,
                                size = size,
                                lastModified = dateModified,
                                mimeType = mimeType,
                                isDirectory = false
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // MediaStore query error or restricted access
        }

        results
    }

    suspend fun listDocumentTree(treeUri: Uri): List<FileItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<FileItem>()
        try {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
            if (rootDoc.isDirectory) {
                val files = rootDoc.listFiles()
                for (doc in files) {
                    val name = doc.name ?: "Unknown"
                    val isDir = doc.isDirectory
                    val size = if (isDir) 0L else doc.length()
                    val lastModified = doc.lastModified()
                    val ext = name.substringAfterLast('.', "")
                    val mime = doc.type ?: getMimeTypeFromExtension(ext)
                    val childrenCount = if (isDir) {
                        try { doc.listFiles().size } catch (_: Exception) { null }
                    } else null

                    list.add(
                        FileItem(
                            uri = doc.uri,
                            name = name,
                            path = doc.uri.toString(),
                            extension = ext,
                            size = size,
                            lastModified = lastModified,
                            mimeType = mime,
                            isDirectory = isDir,
                            childrenCount = childrenCount,
                            canWrite = doc.canWrite(),
                            canDelete = doc.canWrite()
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Handle inaccessible document tree
        }
        list
    }

    suspend fun listDirectoryDocument(dirDocUri: Uri): List<FileItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<FileItem>()
        try {
            val dirDoc = DocumentFile.fromSingleUri(context, dirDocUri)
                ?: DocumentFile.fromTreeUri(context, dirDocUri)
                ?: return@withContext emptyList()

            if (dirDoc.isDirectory) {
                val files = dirDoc.listFiles()
                for (doc in files) {
                    val name = doc.name ?: "Unknown"
                    val isDir = doc.isDirectory
                    val size = if (isDir) 0L else doc.length()
                    val lastModified = doc.lastModified()
                    val ext = name.substringAfterLast('.', "")
                    val mime = doc.type ?: getMimeTypeFromExtension(ext)
                    val childrenCount = if (isDir) {
                        try { doc.listFiles().size } catch (_: Exception) { null }
                    } else null

                    list.add(
                        FileItem(
                            uri = doc.uri,
                            name = name,
                            path = doc.uri.toString(),
                            extension = ext,
                            size = size,
                            lastModified = lastModified,
                            mimeType = mime,
                            isDirectory = isDir,
                            childrenCount = childrenCount,
                            canWrite = doc.canWrite(),
                            canDelete = doc.canWrite()
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Exception listing directory
        }
        list
    }

    suspend fun listInternalAppFiles(): List<FileItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<FileItem>()
        try {
            val internalDir = context.filesDir
            internalDir.listFiles()?.forEach { file ->
                list.add(fileToFileItem(file))
            }
            context.getExternalFilesDir(null)?.listFiles()?.forEach { file ->
                list.add(fileToFileItem(file))
            }
        } catch (_: Exception) {
            // Error reading internal app storage
        }
        list
    }

    private fun fileToFileItem(file: File): FileItem {
        val ext = file.extension
        val isDir = file.isDirectory
        return FileItem(
            uri = Uri.fromFile(file),
            name = file.name,
            path = file.absolutePath,
            extension = ext,
            size = if (isDir) 0L else file.length(),
            lastModified = file.lastModified(),
            mimeType = getMimeTypeFromExtension(ext),
            isDirectory = isDir,
            childrenCount = if (isDir) file.listFiles()?.size else null,
            canWrite = file.canWrite(),
            canDelete = file.canWrite()
        )
    }

    suspend fun createFolder(parentTreeUri: Uri, folderName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val parent = DocumentFile.fromTreeUri(context, parentTreeUri)
                ?: DocumentFile.fromSingleUri(context, parentTreeUri)
            val newDir = parent?.createDirectory(folderName)
            newDir != null
        } catch (_: Exception) {
            false
        }
    }

    suspend fun deleteDocument(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val doc = DocumentFile.fromSingleUri(context, uri)
                ?: DocumentFile.fromTreeUri(context, uri)
            doc?.delete() == true
        } catch (_: Exception) {
            try {
                if (uri.scheme == "file") {
                    val file = File(uri.path ?: "")
                    file.delete()
                } else {
                    contentResolver.delete(uri, null, null) > 0
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    suspend fun renameDocument(uri: Uri, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val doc = DocumentFile.fromSingleUri(context, uri)
                ?: DocumentFile.fromTreeUri(context, uri)
            doc?.renameTo(newName) == true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun calculateFileHash(uri: Uri, algorithm: String): String = withContext(Dispatchers.IO) {
        try {
            val digest = MessageDigest.getInstance(algorithm)
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
                ?: if (uri.scheme == "file") File(uri.path ?: "").inputStream() else null

            inputStream?.use { stream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            } ?: return@withContext "Unable to open file"

            val hashBytes = digest.digest()
            val sb = java.lang.StringBuilder()
            for (b in hashBytes) {
                sb.append(String.format("%02x", b))
            }
            sb.toString()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    suspend fun getStorageCategoryBreakdown(): Map<FileCategory, Long> = withContext(Dispatchers.IO) {
        val breakdown = mutableMapOf<FileCategory, Long>()
        FileCategory.values().forEach { breakdown[it] = 0L }

        try {
            val projection = arrayOf(
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.MIME_TYPE
            )
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Files.getContentUri("external")
            }

            contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: continue
                    val size = cursor.getLong(sizeCol)
                    val ext = name.substringAfterLast('.', "")
                    val mime = cursor.getString(mimeCol) ?: getMimeTypeFromExtension(ext)

                    for (cat in FileCategory.values()) {
                        if (cat.matches(ext, mime)) {
                            breakdown[cat] = (breakdown[cat] ?: 0L) + size
                            break
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // MediaStore not accessible or permission denied
        }

        breakdown
    }

    fun takePersistableUriPermission(uri: Uri) {
        try {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (_: Exception) {
            // Best effort for SAF permissions
        }
    }

    private fun getMimeTypeFromExtension(extension: String): String {
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase(Locale.ROOT))
            ?: "application/octet-stream"
    }
}
