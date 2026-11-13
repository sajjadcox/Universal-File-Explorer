package com.example.domain.security

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.data.db.LocalDataRepository
import com.example.data.db.VaultEntryEntity
import com.example.data.storage.AdvancedStorageOperations
import com.example.domain.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class VaultManager(
    private val context: Context,
    private val localRepo: LocalDataRepository
) {
    private val vaultDir: File
        get() = File(context.filesDir, "private_vault").apply {
            if (!exists()) mkdirs()
            val noMedia = File(this, ".nomedia")
            if (!noMedia.exists()) runCatching { noMedia.createNewFile() }
        }

    private val vaultPreviewCacheDir: File
        get() = File(context.cacheDir, "vault_preview_tmp").apply { mkdirs() }

    suspend fun importFileIntoVault(
        fileItem: FileItem,
        folderName: String = "General",
        deleteOriginal: Boolean = false,
        customPassword: String? = null
    ): Result<VaultEntryEntity> = withContext(Dispatchers.IO) {
        try {
            val input = openInput(fileItem.uri)
                ?: return@withContext Result.failure(IllegalStateException("Cannot read source file"))

            // Compute SHA-256 of original & encrypt directly via temporary stream pass
            val tempPlain = File(vaultPreviewCacheDir, "imp_${UUID.randomUUID()}.tmp")
            val sha256Hex: String
            var actualSize = 0L
            input.use { src ->
                val md = MessageDigest.getInstance("SHA-256")
                val buf = ByteArray(16 * 1024)
                tempPlain.outputStream().use { dst ->
                    var read: Int
                    while (src.read(buf).also { read = it } != -1) {
                        md.update(buf, 0, read)
                        dst.write(buf, 0, read)
                        actualSize += read
                    }
                }
                sha256Hex = md.digest().joinToString("") { "%02x".format(it) }
            }

            val vaultFileName = "${UUID.randomUUID()}.ufev"
            val outVaultFile = File(vaultDir, vaultFileName)
            val keyMode = if (!customPassword.isNullOrBlank()) "PASSWORD" else "KEYSTORE"

            val encRes = tempPlain.inputStream().use { plainStream ->
                if (!customPassword.isNullOrBlank()) {
                    SecurityEngine.encryptFileWithPassword(plainStream, outVaultFile, customPassword)
                } else {
                    SecurityEngine.encryptFileWithKeystore(context, plainStream, outVaultFile)
                }
            }
            tempPlain.delete()

            if (encRes.isFailure) {
                return@withContext Result.failure(encRes.exceptionOrNull() ?: Exception("Encryption failed"))
            }

            if (deleteOriginal) {
                deleteSource(fileItem.uri)
            }

            val entry = VaultEntryEntity(
                vaultFileName = vaultFileName,
                originalName = fileItem.name,
                originalUri = fileItem.uri.toString(),
                folderName = folderName.ifBlank { "General" },
                mimeType = fileItem.mimeType,
                sizeBytes = if (actualSize > 0) actualSize else fileItem.size,
                sha256Original = sha256Hex,
                isHidden = false,
                keyMode = keyMode
            )
            val id = localRepo.addVaultEntry(entry)
            localRepo.logActivity("VAULT_IMPORT", fileItem.name, outVaultFile.absolutePath, "Encrypted ($keyMode) into Vault/$folderName")
            Result.success(entry.copy(id = id))
        } catch (e: Exception) {
            clearSensitiveTempFiles()
            Result.failure(e)
        }
    }

    suspend fun decryptEntryToPreviewFile(
        entry: VaultEntryEntity,
        customPassword: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val encryptedFile = File(vaultDir, entry.vaultFileName)
        if (!encryptedFile.exists()) {
            return@withContext Result.failure(IllegalStateException("Encrypted vault file missing on disk"))
        }
        val outFile = File(vaultPreviewCacheDir, "dec_${entry.id}_${entry.originalName.replace("/", "_")}")
        val res = if (entry.keyMode == "PASSWORD") {
            if (customPassword.isNullOrBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Password required to decrypt this file"))
            }
            SecurityEngine.decryptFileWithPassword(encryptedFile, outFile, customPassword)
        } else {
            SecurityEngine.decryptFileWithKeystore(context, encryptedFile, outFile)
        }
        if (res.isSuccess) {
            localRepo.touchVaultEntryAccessed(entry.id)
            localRepo.logActivity("DECRYPTED", entry.originalName, outFile.absolutePath, "Decrypted for authenticated preview")
        }
        res
    }

    suspend fun exportEntryFromVault(
        entry: VaultEntryEntity,
        destTreeUri: Uri?,
        removeFromVault: Boolean = false,
        customPassword: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val decResult = decryptEntryToPreviewFile(entry, customPassword)
        val decFile = decResult.getOrNull()
            ?: return@withContext Result.failure(decResult.exceptionOrNull() ?: Exception("Decryption failed"))

        try {
            val storageOps = AdvancedStorageOperations(context)
            val targetName = entry.originalName
            val ok = if (destTreeUri == null) {
                val exportDir = File(context.filesDir, "VaultExports").apply { mkdirs() }
                val dest = File(exportDir, targetName)
                decFile.copyTo(dest, overwrite = true)
                true
            } else {
                storageOps.copyDocument(Uri.fromFile(decFile), destTreeUri, targetName, entry.mimeType)
            }
            decFile.delete()
            if (!ok) return@withContext Result.failure(IllegalStateException("Failed to write exported file"))

            if (removeFromVault) {
                File(vaultDir, entry.vaultFileName).delete()
                localRepo.removeVaultEntry(entry.id)
            }
            localRepo.logActivity("VAULT_EXPORT", entry.originalName, destTreeUri?.toString() ?: "AppStorage/VaultExports", "Exported after authentication")
            Result.success(targetName)
        } catch (e: Exception) {
            decFile.delete()
            Result.failure(e)
        }
    }

    suspend fun deleteVaultEntry(entry: VaultEntryEntity) = withContext(Dispatchers.IO) {
        File(vaultDir, entry.vaultFileName).delete()
        localRepo.removeVaultEntry(entry.id)
        localRepo.logActivity("DELETED", entry.originalName, entry.vaultFileName, "Removed encrypted entry from Vault")
    }

    fun getVaultStorageBytes(): Long =
        vaultDir.listFiles()?.filter { it.isFile && it.name != ".nomedia" }?.sumOf { it.length() } ?: 0L

    fun getSensitiveTempBytes(): Long {
        val vpBytes = vaultPreviewCacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val tmpBytes = File(context.cacheDir, "viewer_tmp").let { dir ->
            if (dir.exists()) dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
        }
        return vpBytes + tmpBytes
    }

    fun clearSensitiveTempFiles(): Int {
        var deleted = 0
        vaultPreviewCacheDir.listFiles()?.forEach {
            if (it.deleteRecursively()) deleted++
        }
        File(context.cacheDir, "viewer_tmp").let { dir ->
            if (dir.exists()) {
                dir.listFiles()?.forEach { if (it.deleteRecursively()) deleted++ }
            }
        }
        return deleted
    }

    private fun openInput(uri: Uri) = if (uri.scheme == "file") {
        val f = File(uri.path ?: "")
        if (f.exists() && f.canRead()) f.inputStream() else null
    } else {
        context.contentResolver.openInputStream(uri)
    }

    private fun deleteSource(uri: Uri) {
        try {
            if (uri.scheme == "file") {
                File(uri.path ?: "").delete()
            } else {
                val doc = DocumentFile.fromSingleUri(context, uri)
                doc?.delete()
            }
        } catch (_: Exception) {}
    }
}
