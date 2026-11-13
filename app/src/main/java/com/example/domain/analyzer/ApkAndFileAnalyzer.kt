package com.example.domain.analyzer

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

data class ApkInspectionInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val fileSizeBytes: Long,
    val sha256Checksum: String,
    val signingCertSha256: String,
    val permissions: List<String>,
    val icon: Drawable?,
    val bundleFormat: String,
    val extraNotes: String = ""
)

data class InstalledAppSummary(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val apkPath: String,
    val apkSizeBytes: Long,
    val icon: Drawable?
)

data class MediaDetailsInfo(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val bitrate: String,
    val sampleRate: String,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val albumArt: Bitmap?
)

class ApkAndFileAnalyzer(private val context: Context) {

    @Suppress("DEPRECATION")
    suspend fun inspectApkPackage(file: File, extension: String): Result<ApkInspectionInfo> =
        withContext(Dispatchers.IO) {
            val ext = extension.lowercase()
            try {
                val sha256 = computeSha256(file)

                if (ext in setOf("xapk", "apks", "aab")) {
                    // Inspect archive container entries & manifest.json if present
                    var manifestJsonText = ""
                    var entriesCount = 0
                    val splitFiles = mutableListOf<String>()
                    ZipFile(file).use { zip ->
                        val entries = zip.entries()
                        while (entries.hasMoreElements()) {
                            val e = entries.nextElement()
                            entriesCount++
                            if (e.name.endsWith(".apk")) splitFiles.add(e.name)
                            if (e.name == "manifest.json" && e.size < 100_000) {
                                manifestJsonText = zip.getInputStream(e).bufferedReader().use { it.readText() }
                            }
                        }
                    }
                    val pkgMatch = Regex("\"package_name\"\\s*:\\s*\"([^\"]+)\"").find(manifestJsonText)?.groupValues?.get(1)
                    val nameMatch = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(manifestJsonText)?.groupValues?.get(1)
                    val verMatch = Regex("\"version_name\"\\s*:\\s*\"([^\"]+)\"").find(manifestJsonText)?.groupValues?.get(1)
                    val minSdkMatch = Regex("\"min_sdk_version\"\\s*:\\s*\"?(\\d+)\"?").find(manifestJsonText)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val targetSdkMatch = Regex("\"target_sdk_version\"\\s*:\\s*\"?(\\d+)\"?").find(manifestJsonText)?.groupValues?.get(1)?.toIntOrNull() ?: 0

                    return@withContext Result.success(
                        ApkInspectionInfo(
                            packageName = pkgMatch ?: "Container (${entriesCount} entries)",
                            appName = nameMatch ?: file.nameWithoutExtension,
                            versionName = verMatch ?: "Bundle",
                            versionCode = 0L,
                            minSdk = minSdkMatch,
                            targetSdk = targetSdkMatch,
                            fileSizeBytes = file.length(),
                            sha256Checksum = sha256,
                            signingCertSha256 = "Inspect base.apk inside bundle",
                            permissions = splitFiles.take(30),
                            icon = null,
                            bundleFormat = ext.uppercase(),
                            extraNotes = "Contains ${splitFiles.size} APK splits / $entriesCount archive entries."
                        )
                    )
                }

                val pm = context.packageManager
                val flags = PackageManager.GET_PERMISSIONS or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        PackageManager.GET_SIGNING_CERTIFICATES
                    } else {
                        PackageManager.GET_SIGNATURES
                    }

                val pkgInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
                } else {
                    pm.getPackageArchiveInfo(file.absolutePath, flags)
                } ?: return@withContext Result.failure(IllegalStateException("Could not parse AndroidManifest in APK"))

                pkgInfo.applicationInfo?.apply {
                    sourceDir = file.absolutePath
                    publicSourceDir = file.absolutePath
                }

                val appLabel = pkgInfo.applicationInfo?.loadLabel(pm)?.toString() ?: file.nameWithoutExtension
                val appIcon = try { pkgInfo.applicationInfo?.loadIcon(pm) } catch (_: Exception) { null }
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkgInfo.longVersionCode else pkgInfo.versionCode.toLong()
                val minSdk = pkgInfo.applicationInfo?.minSdkVersion ?: 0
                val targetSdk = pkgInfo.applicationInfo?.targetSdkVersion ?: 0
                val perms = pkgInfo.requestedPermissions?.toList() ?: emptyList()

                val certSha = extractCertSha256(pkgInfo)

                Result.success(
                    ApkInspectionInfo(
                        packageName = pkgInfo.packageName ?: "unknown",
                        appName = appLabel,
                        versionName = pkgInfo.versionName ?: "1.0",
                        versionCode = versionCode,
                        minSdk = minSdk,
                        targetSdk = targetSdk,
                        fileSizeBytes = file.length(),
                        sha256Checksum = sha256,
                        signingCertSha256 = certSha,
                        permissions = perms,
                        icon = appIcon,
                        bundleFormat = "APK"
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    @Suppress("DEPRECATION")
    private fun extractCertSha256(pkgInfo: PackageInfo): String {
        return try {
            val sigBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            } else {
                pkgInfo.signatures?.firstOrNull()?.toByteArray()
            } ?: return "Unsigned / Not Available"
            val md = MessageDigest.getInstance("SHA-256")
            md.digest(sigBytes).joinToString(":") { "%02X".format(it) }
        } catch (_: Exception) {
            "Unavailable"
        }
    }

    @Suppress("DEPRECATION")
    suspend fun getInstalledApps(includeSystem: Boolean = false): List<InstalledAppSummary> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
            } else {
                pm.getInstalledPackages(0)
            }
            packages.mapNotNull { info ->
                val appInfo = info.applicationInfo ?: return@mapNotNull null
                val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (!includeSystem && isSys) return@mapNotNull null
                val srcFile = File(appInfo.sourceDir ?: "")
                val verCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
                InstalledAppSummary(
                    packageName = info.packageName,
                    appName = appInfo.loadLabel(pm).toString(),
                    versionName = info.versionName ?: "1.0",
                    versionCode = verCode,
                    isSystemApp = isSys,
                    apkPath = appInfo.sourceDir ?: "",
                    apkSizeBytes = if (srcFile.exists()) srcFile.length() else 0L,
                    icon = try { appInfo.loadIcon(pm) } catch (_: Exception) { null }
                )
            }.sortedBy { it.appName.lowercase() }
        }

    fun launchSystemApkInstaller(apkFile: File): Boolean {
        return try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun extractMediaMetadata(uri: Uri): MediaDetailsInfo = withContext(Dispatchers.IO) {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, uri)
            val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""
            val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
            val album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
            val duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val bitrate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.let {
                "${it.toLongOrNull()?.div(1000) ?: 0} kbps"
            } ?: "Unknown"
            val sampleRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.let { "$it Hz" } ?: "Unknown"
            } else "Unknown"
            val width = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val mime = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
            val artBytes = mmr.embeddedPicture
            val artBmp = artBytes?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }

            MediaDetailsInfo(title, artist, album, duration, bitrate, sampleRate, width, height, mime, artBmp)
        } catch (_: Exception) {
            MediaDetailsInfo("", "", "", 0L, "Unknown", "Unknown", 0, 0, "", null)
        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }
    }

    suspend fun extractVideoThumbnail(uri: Uri, timeUs: Long = 1_000_000L): Bitmap? =
        withContext(Dispatchers.IO) {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(context, uri)
                mmr.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (_: Exception) {
                null
            } finally {
                try { mmr.release() } catch (_: Exception) {}
            }
        }

    private fun computeSha256(file: File): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { s ->
                val buf = ByteArray(16 * 1024)
                var r: Int
                while (s.read(buf).also { r = it } != -1) md.update(buf, 0, r)
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            "Unavailable"
        }
    }
}
