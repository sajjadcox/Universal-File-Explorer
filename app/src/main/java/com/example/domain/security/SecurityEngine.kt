package com.example.domain.security

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.domain.analyzer.ApkInspectionInfo
import com.example.domain.engine.FileInspectionResult
import com.example.domain.engine.FormatSupportStatus
import com.example.domain.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.coroutineContext

data class AllHashesResult(
    val md5: String,
    val sha1: String,
    val sha256: String,
    val sha512: String,
    val bytesProcessed: Long
)

enum class SecurityRiskLevel(val label: String) {
    SAFE("Low Risk"),
    CAUTION("Review Advised"),
    WARNING("Security Warning")
}

data class FileSecurityReport(
    val riskLevel: SecurityRiskLevel,
    val warnings: List<String>,
    val dangerousPermissions: List<String> = emptyList(),
    val isExtensionMismatch: Boolean = false,
    val isExecutableOrInstaller: Boolean = false,
    val isUnknownFormat: Boolean = false,
    val recommendations: List<String> = emptyList()
)

object SecurityEngine {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEYSTORE_ALIAS = "ufe_vault_master_key_v1"
    private const val MAGIC_HEADER = "UFEV1" // 5 bytes
    private const val MODE_KEYSTORE: Byte = 1
    private const val MODE_PASSWORD: Byte = 2
    private const val CHUNK_SIZE = 64 * 1024 // 64 KB constant-memory AEAD chunks
    private const val GCM_IV_LEN = 12
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 65_536

    private val secureRandom = SecureRandom()

    val DANGEROUS_APK_PERMISSIONS = setOf(
        "android.permission.READ_SMS",
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.READ_CALL_LOG",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.BIND_ACCESSIBILITY_SERVICE"
    )

    // ==================== PIN AUTHENTICATION ====================

    fun generateSaltBase64(): String {
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hashPin(pin: String, saltBase64: String): String {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = skf.generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun verifyPin(pin: String, expectedHash: String, saltBase64: String): Boolean {
        if (pin.isEmpty() || expectedHash.isBlank() || saltBase64.isBlank()) return false
        val computed = hashPin(pin, saltBase64)
        return MessageDigest.isEqual(computed.toByteArray(), expectedHash.toByteArray())
    }

    fun computeLockoutDurationMs(failedAttempts: Int): Long {
        return when {
            failedAttempts >= 10 -> 5 * 60_000L
            failedAttempts >= 7 -> 2 * 60_000L
            failedAttempts >= 5 -> 30_000L
            else -> 0L
        }
    }

    // ==================== KEY MANAGEMENT ====================

    fun getOrCreateVaultMasterKey(context: Context): SecretKey {
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGen.init(spec)
            return keyGen.generateKey()
        } catch (_: Exception) {
            // Fallback for Robolectric JVM or rare devices lacking AndroidKeyStore provider
            val fallbackFile = File(context.noBackupFilesDir ?: context.filesDir, ".vault_local_key")
            return if (fallbackFile.exists() && fallbackFile.length() == 32L) {
                SecretKeySpec(fallbackFile.readBytes(), "AES")
            } else {
                val raw = ByteArray(32).also { secureRandom.nextBytes(it) }
                fallbackFile.writeBytes(raw)
                SecretKeySpec(raw, "AES")
            }
        }
    }

    fun deriveAesKeyFromPassword(password: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(skf.generateSecret(spec).encoded, "AES")
    }

    // ==================== AES-256-GCM STREAMING ENCRYPT / DECRYPT ====================

    suspend fun encryptFileWithKeystore(
        context: Context,
        input: InputStream,
        outputFile: File,
        onProgress: (Long) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val key = getOrCreateVaultMasterKey(context)
        encryptStreamChunked(input, outputFile, key, MODE_KEYSTORE, ByteArray(0), onProgress)
    }

    suspend fun decryptFileWithKeystore(
        context: Context,
        encryptedFile: File,
        outputFile: File,
        onProgress: (Long) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val key = getOrCreateVaultMasterKey(context)
        decryptStreamChunked(encryptedFile, outputFile, { mode, _ ->
            if (mode != MODE_KEYSTORE) throw IllegalStateException("File was encrypted with a custom password")
            key
        }, onProgress)
    }

    suspend fun encryptFileWithPassword(
        input: InputStream,
        outputFile: File,
        password: String,
        onProgress: (Long) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val key = deriveAesKeyFromPassword(password, salt)
        encryptStreamChunked(input, outputFile, key, MODE_PASSWORD, salt, onProgress)
    }

    suspend fun decryptFileWithPassword(
        encryptedFile: File,
        outputFile: File,
        password: String,
        onProgress: (Long) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        decryptStreamChunked(encryptedFile, outputFile, { mode, salt ->
            if (mode != MODE_PASSWORD) throw IllegalStateException("File is protected with device Keystore key, not password")
            deriveAesKeyFromPassword(password, salt)
        }, onProgress)
    }

    private suspend fun encryptStreamChunked(
        input: InputStream,
        outputFile: File,
        key: SecretKey,
        mode: Byte,
        salt: ByteArray,
        onProgress: (Long) -> Unit
    ): Result<File> {
        return try {
            outputFile.parentFile?.mkdirs()
            val buf = ByteArray(CHUNK_SIZE)
            var totalRead = 0L
            var chunkIdx = 0L
            DataOutputStream(BufferedOutputStream(FileOutputStream(outputFile), CHUNK_SIZE)).use { dos ->
                dos.write(MAGIC_HEADER.toByteArray(Charsets.US_ASCII))
                dos.writeByte(mode.toInt())
                dos.writeShort(salt.size)
                if (salt.isNotEmpty()) dos.write(salt)

                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buf)
                    if (read == -1) break
                    totalRead += read

                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    val iv = ByteArray(GCM_IV_LEN)
                    val gcmCipher: ByteArray
                    if (mode == MODE_KEYSTORE) {
                        // AndroidKeyStore with randomizedEncryptionRequired generates its own secure IV
                        cipher.init(Cipher.ENCRYPT_MODE, key)
                        System.arraycopy(cipher.iv, 0, iv, 0, GCM_IV_LEN)
                    } else {
                        secureRandom.nextBytes(iv)
                        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                    }
                    cipher.updateAAD(ByteBuffer.allocate(9).putLong(chunkIdx).put(mode).array())
                    gcmCipher = cipher.doFinal(buf, 0, read)

                    dos.writeInt(gcmCipher.size)
                    dos.write(iv)
                    dos.write(gcmCipher)
                    chunkIdx++
                    onProgress(totalRead)
                }
                dos.writeInt(-1) // EOF sentinel verified against truncation
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            outputFile.delete()
            Result.failure(e)
        }
    }

    private suspend fun decryptStreamChunked(
        encryptedFile: File,
        outputFile: File,
        keyProvider: (mode: Byte, salt: ByteArray) -> SecretKey,
        onProgress: (Long) -> Unit
    ): Result<File> {
        return try {
            outputFile.parentFile?.mkdirs()
            var totalWritten = 0L
            var chunkIdx = 0L
            DataInputStream(BufferedInputStream(FileInputStream(encryptedFile), CHUNK_SIZE)).use { dis ->
                val magic = ByteArray(5)
                dis.readFully(magic)
                if (String(magic, Charsets.US_ASCII) != MAGIC_HEADER) {
                    return Result.failure(IllegalArgumentException("Not a valid UFE encrypted vault container"))
                }
                val mode = dis.readByte()
                val saltLen = dis.readUnsignedShort()
                if (saltLen > 128) return Result.failure(IllegalArgumentException("Invalid salt header"))
                val salt = ByteArray(saltLen).also { if (saltLen > 0) dis.readFully(it) }
                val key = keyProvider(mode, salt)

                BufferedOutputStream(FileOutputStream(outputFile), CHUNK_SIZE).use { bos ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val cipherLen = dis.readInt()
                        if (cipherLen == -1) break
                        if (cipherLen <= 0 || cipherLen > CHUNK_SIZE + 1024) {
                            throw IllegalStateException("Corrupt encrypted chunk size: $cipherLen")
                        }
                        val iv = ByteArray(GCM_IV_LEN)
                        dis.readFully(iv)
                        val cipherBytes = ByteArray(cipherLen)
                        dis.readFully(cipherBytes)

                        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                        cipher.updateAAD(ByteBuffer.allocate(9).putLong(chunkIdx).put(mode).array())
                        val plain = cipher.doFinal(cipherBytes)
                        bos.write(plain)
                        totalWritten += plain.size
                        chunkIdx++
                        onProgress(totalWritten)
                    }
                }
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            outputFile.delete()
            Result.failure(e)
        }
    }

    // ==================== MULTI-HASH INTEGRITY ====================

    suspend fun computeAllHashes(context: Context, uri: Uri): Result<AllHashesResult> =
        withContext(Dispatchers.IO) {
            try {
                val input = openStream(context, uri)
                    ?: return@withContext Result.failure(IllegalStateException("Unable to open file stream"))
                input.use { stream ->
                    val md5 = MessageDigest.getInstance("MD5")
                    val sha1 = MessageDigest.getInstance("SHA-1")
                    val sha256 = MessageDigest.getInstance("SHA-256")
                    val sha512 = MessageDigest.getInstance("SHA-512")
                    val buf = ByteArray(16 * 1024)
                    var total = 0L
                    var read: Int
                    while (stream.read(buf).also { read = it } != -1) {
                        coroutineContext.ensureActive()
                        md5.update(buf, 0, read)
                        sha1.update(buf, 0, read)
                        sha256.update(buf, 0, read)
                        sha512.update(buf, 0, read)
                        total += read
                    }
                    Result.success(
                        AllHashesResult(
                            md5 = md5.digest().toHex(),
                            sha1 = sha1.digest().toHex(),
                            sha256 = sha256.digest().toHex(),
                            sha512 = sha512.digest().toHex(),
                            bytesProcessed = total
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun verifyChecksum(computed: String, expected: String): Boolean {
        val a = computed.trim().lowercase()
        val b = expected.trim().lowercase()
        return a.isNotEmpty() && a == b
    }

    // ==================== SECURITY & MISMATCH ANALYSIS ====================

    fun analyzeFileSecurity(
        fileItem: FileItem,
        inspection: FileInspectionResult,
        apkInfo: ApkInspectionInfo? = null
    ): FileSecurityReport {
        val warnings = mutableListOf<String>()
        val recommendations = mutableListOf<String>()
        val ext = fileItem.extension.lowercase()
        val sig = inspection.signatureMatch

        // 1. Extension vs Magic-Byte Mismatch Detection
        val isMismatch = when {
            ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp") &&
                (sig.startsWith("PDF") || sig == "ZIP/Container" || sig.startsWith("RAR") || sig == "SQLite 3") -> true
            ext == "pdf" && sig != "Empty/Unreadable" && !sig.startsWith("PDF") -> true
            ext in setOf("txt", "csv", "json", "xml", "md", "log") &&
                (sig == "ZIP/Container" || sig.startsWith("PDF") || sig == "PNG" || sig.startsWith("JPEG")) -> true
            else -> false
        }
        if (isMismatch) {
            warnings.add("Extension mismatch: File claims '.$ext' but magic bytes identify '$sig' (${inspection.formatName}).")
            recommendations.add("Inspect the header in Hex mode or verify the sender before opening.")
        }

        // 2. Executable / Installer / Script warning
        val isExec = ext in setOf("apk", "xapk", "apks", "aab", "sh", "dex", "so", "exe", "bat", "jar")
        if (isExec) {
            warnings.add("Executable or package file (.$ext): Only install or execute files from trusted sources.")
        }

        // 3. APK dangerous permissions
        val dangerousPerms = apkInfo?.permissions?.filter { it in DANGEROUS_APK_PERMISSIONS } ?: emptyList()
        if (dangerousPerms.isNotEmpty()) {
            warnings.add("APK requests ${dangerousPerms.size} sensitive permission(s) such as SMS, Location, Camera, Contacts, or Overlay.")
        }

        // 4. Corruption or Unknown format
        val isUnknown = inspection.supportStatus == FormatSupportStatus.UNSUPPORTED || sig == "Binary/Unknown"
        if (inspection.isPotentiallyCorrupted) {
            warnings.add("File header appears damaged or truncated.")
        }
        if (isUnknown) {
            warnings.add("Unrecognized binary format: open safely via Hex/Binary Inspector.")
        }
        if (inspection.isPasswordProtected) {
            recommendations.add("Encrypted or password-protected container detected.")
        }

        recommendations.add("Note: On Android flash/NAND storage (eMMC/UFS), file deletion removes filesystem pointers; wear-leveling prevents guaranteed sector overwrite. Keep sensitive files encrypted in the Private Vault.")

        val level = when {
            isMismatch || dangerousPerms.size >= 3 || inspection.isPotentiallyCorrupted -> SecurityRiskLevel.WARNING
            isExec || isUnknown || dangerousPerms.isNotEmpty() -> SecurityRiskLevel.CAUTION
            else -> SecurityRiskLevel.SAFE
        }

        return FileSecurityReport(
            riskLevel = level,
            warnings = warnings,
            dangerousPermissions = dangerousPerms,
            isExtensionMismatch = isMismatch,
            isExecutableOrInstaller = isExec,
            isUnknownFormat = isUnknown,
            recommendations = recommendations
        )
    }

    // ==================== BIOMETRIC AUTHENTICATION ====================

    fun isBiometricAvailable(context: Context): Boolean {
        val bm = BiometricManager.from(context)
        val status = bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        return status == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticateBiometric(
        activity: FragmentActivity,
        title: String = "Unlock Private Vault",
        subtitle: String = "Verify fingerprint, face, or device credential",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("Biometric not recognized. Try again.")
            }
        }
        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(info)
    }

    private fun openStream(context: Context, uri: Uri): InputStream? {
        return if (uri.scheme == "file") {
            val f = File(uri.path ?: "")
            if (f.exists() && f.canRead()) f.inputStream() else null
        } else {
            context.contentResolver.openInputStream(uri)
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
