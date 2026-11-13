package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.LocalDataRepository
import com.example.data.storage.FolderComparisonEntry
import com.example.data.storage.Phase2FileOperations
import com.example.domain.analyzer.ApkInspectionInfo
import com.example.domain.assistant.SmartFileAssistant
import com.example.domain.engine.FileInspectionResult
import com.example.domain.engine.FileSignatureDetector
import com.example.domain.engine.FormatSupportStatus
import com.example.domain.engine.ViewerType
import com.example.domain.model.FileItem
import com.example.domain.security.SecurityEngine
import com.example.domain.security.SecurityRiskLevel
import com.example.domain.security.VaultManager
import com.example.ui.i18n.stringsForLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import android.content.Intent
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase2ComprehensiveRobolectricTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `security engine pin hashing and lockout escalation`() {
        val salt = SecurityEngine.generateSaltBase64()
        val hash = SecurityEngine.hashPin("4829", salt)
        assertTrue(SecurityEngine.verifyPin("4829", hash, salt))
        assertFalse(SecurityEngine.verifyPin("0000", hash, salt))
        assertEquals(0L, SecurityEngine.computeLockoutDurationMs(4))
        assertEquals(30_000L, SecurityEngine.computeLockoutDurationMs(5))
        assertEquals(120_000L, SecurityEngine.computeLockoutDurationMs(7))
        assertEquals(300_000L, SecurityEngine.computeLockoutDurationMs(10))
    }

    @Test
    fun `security engine password aes-gcm encrypt decrypt and tamper rejection`(): Unit = runBlocking<Unit> {
        val tempDir = File(context.cacheDir, "sec_test_${System.currentTimeMillis()}").apply { mkdirs() }
        val plain = File(tempDir, "secret.txt").apply { writeText("Phase 2 Confidential Vault Payload 1234567890") }
        val enc = File(tempDir, "secret.ufev")
        val dec = File(tempDir, "dec.txt")

        val encRes = plain.inputStream().use {
            SecurityEngine.encryptFileWithPassword(it, enc, "StrongPass#2026")
        }
        assertTrue(encRes.isSuccess)
        assertTrue(enc.exists())
        assertNotEquals(plain.readText(), enc.readText())

        // Wrong password fails
        val badDec = SecurityEngine.decryptFileWithPassword(enc, dec, "WrongPass")
        assertTrue(badDec.isFailure)

        // Correct password succeeds
        val goodDec = SecurityEngine.decryptFileWithPassword(enc, dec, "StrongPass#2026")
        assertTrue(goodDec.isSuccess)
        assertEquals(plain.readText(), dec.readText())

        // Tamper ciphertext byte near end -> AEAD GCM tag verification must fail
        val bytes = enc.readBytes()
        val tamperIdx = bytes.size - 10
        bytes[tamperIdx] = (bytes[tamperIdx].toInt() xor 0xFF).toByte()
        enc.writeBytes(bytes)
        val tamperedRes = SecurityEngine.decryptFileWithPassword(enc, dec, "StrongPass#2026")
        assertTrue(tamperedRes.isFailure)

        tempDir.deleteRecursively()
    }

    @Test
    fun `security engine multi-hash and mismatch plus apk dangerous permissions warnings`(): Unit = runBlocking<Unit> {
        val tempDir = File(context.cacheDir, "hash_test_${System.currentTimeMillis()}").apply { mkdirs() }
        val fakePng = File(tempDir, "disguised.png").apply { writeBytes("%PDF-1.7\nfake".toByteArray()) }
        val hashes = SecurityEngine.computeAllHashes(context, Uri.fromFile(fakePng)).getOrThrow()

        assertEquals(32, hashes.md5.length)
        assertEquals(40, hashes.sha1.length)
        assertEquals(64, hashes.sha256.length)
        assertEquals(128, hashes.sha512.length)
        assertTrue(SecurityEngine.verifyChecksum(hashes.sha256, hashes.sha256.uppercase()))
        assertFalse(SecurityEngine.verifyChecksum(hashes.sha256, "deadbeef"))

        val item = FileItem(
            uri = Uri.fromFile(fakePng),
            name = fakePng.name,
            path = fakePng.absolutePath,
            extension = "png",
            size = fakePng.length(),
            mimeType = "image/png"
        )
        val inspection = FileSignatureDetector.inspectFile(context, item)
        val report = SecurityEngine.analyzeFileSecurity(item, inspection)
        assertTrue(report.isExtensionMismatch)
        assertEquals(SecurityRiskLevel.WARNING, report.riskLevel)
        assertTrue(report.recommendations.any { it.contains("wear-leveling", ignoreCase = true) })

        val apkInfo = ApkInspectionInfo(
            packageName = "com.test.sample",
            appName = "Sample App",
            versionName = "1.0",
            versionCode = 1L,
            minSdk = 28,
            targetSdk = 36,
            fileSizeBytes = fakePng.length(),
            sha256Checksum = hashes.sha256,
            signingCertSha256 = "abc",
            permissions = listOf(
                "android.permission.INTERNET",
                "android.permission.READ_SMS",
                "android.permission.CAMERA",
                "android.permission.RECORD_AUDIO"
            ),
            icon = null,
            bundleFormat = "APK"
        )
        val apkItem = item.copy(name = "sample.apk", extension = "apk", mimeType = "application/vnd.android.package-archive")
        val apkInspection = FileInspectionResult(
            formatName = "APK",
            detectedExtension = "apk",
            detectedMimeType = apkItem.mimeType,
            magicBytesHex = "50 4B 03 04",
            signatureMatch = "ZIP/Container",
            viewerType = ViewerType.APK,
            supportStatus = FormatSupportStatus.FULL,
            supportNotes = "OK"
        )
        val apkReport = SecurityEngine.analyzeFileSecurity(apkItem, apkInspection, apkInfo)
        assertEquals(3, apkReport.dangerousPermissions.size)
        assertEquals(SecurityRiskLevel.WARNING, apkReport.riskLevel)

        tempDir.deleteRecursively()
    }

    @Test
    fun `vault manager import decrypt export and temp wipe`(): Unit = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val repo = LocalDataRepository(db)
        val vault = VaultManager(context, repo)

        val tempDir = File(context.cacheDir, "vault_src_${System.currentTimeMillis()}").apply { mkdirs() }
        val srcFile = File(tempDir, "notes_secret.txt").apply { writeText("Top secret vault text") }
        val item = FileItem(
            uri = Uri.fromFile(srcFile),
            name = srcFile.name,
            path = srcFile.absolutePath,
            extension = "txt",
            size = srcFile.length(),
            mimeType = "text/plain"
        )

        val imported = vault.importFileIntoVault(item, folderName = "Work", deleteOriginal = false).getOrThrow()
        assertEquals("Work", imported.folderName)
        assertTrue(File(context.filesDir, "private_vault/.nomedia").exists())
        assertTrue(vault.getVaultStorageBytes() > 0L)

        val previewFile = vault.decryptEntryToPreviewFile(imported).getOrThrow()
        assertTrue(previewFile.exists())
        assertEquals("Top secret vault text", previewFile.readText())

        val wipedCount = vault.clearSensitiveTempFiles()
        assertTrue(wipedCount >= 1)
        assertFalse(previewFile.exists())

        val exportResult = vault.exportEntryFromVault(imported, destTreeUri = null, removeFromVault = true)
        assertTrue(exportResult.isSuccess)
        val exported = File(context.filesDir, "VaultExports/notes_secret.txt")
        assertTrue(exported.exists())
        assertEquals("Top secret vault text", exported.readText())
        assertTrue(repo.vaultEntries.first().isEmpty())

        exported.delete()
        tempDir.deleteRecursively()
        db.close()
    }

    @Test
    fun `smart file assistant analyzes mismatch and provides suggestions`(): Unit = runBlocking<Unit> {
        val tempDir = File(context.cacheDir, "assist_test_${System.currentTimeMillis()}").apply { mkdirs() }
        val fakeCsv = File(tempDir, "report.csv").apply { writeBytes("%PDF-1.4\n1,2,3".toByteArray()) }
        val item = FileItem(
            uri = Uri.fromFile(fakeCsv),
            name = fakeCsv.name,
            path = fakeCsv.absolutePath,
            extension = "csv",
            size = fakeCsv.length(),
            mimeType = "text/csv"
        )
        val report = SmartFileAssistant.analyze(context, item)
        assertTrue(report.securityReport.isExtensionMismatch)
        assertNotNull(report.whyCannotOpenExplanation)
        assertTrue(report.extensionExplanation.contains("Tabular Data"))
        assertTrue(report.recommendedViewerLabel.isNotBlank())

        tempDir.deleteRecursively()
    }

    @Test
    fun `room migration 1 to 2 preserves phase 1 rows and initializes phase 2 tables`(): Unit = runBlocking<Unit> {
        val dbName = "migration_test_${System.currentTimeMillis()}.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // Step 1: Create v1 schema manually and insert Phase 1 rows
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `reading_history` (`uri` TEXT NOT NULL PRIMARY KEY, `name` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `viewerType` TEXT NOT NULL, `size` INTEGER NOT NULL, `lastOpenedAt` INTEGER NOT NULL, `lastPosition` INTEGER NOT NULL, `totalLength` INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `bookmarks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uri` TEXT NOT NULL, `fileName` TEXT NOT NULL, `label` TEXT NOT NULL, `position` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `search_history` (`query` TEXT NOT NULL PRIMARY KEY, `timestamp` INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `trash_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `originalName` TEXT NOT NULL, `originalUri` TEXT NOT NULL, `trashFilePath` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `size` INTEGER NOT NULL, `deletedAt` INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `conversion_jobs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sourceName` TEXT NOT NULL, `sourceUri` TEXT NOT NULL, `targetFormat` TEXT NOT NULL, `outputUri` TEXT, `outputPath` TEXT, `status` TEXT NOT NULL, `progress` INTEGER NOT NULL, `errorMessage` TEXT, `createdAt` INTEGER NOT NULL)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val v1Db = helper.writableDatabase
        v1Db.execSQL("INSERT INTO reading_history VALUES ('file:///sample.pdf', 'sample.pdf', 'application/pdf', 'PDF', 2048, 1000, 3, 12)")
        v1Db.execSQL("INSERT INTO bookmarks (uri, fileName, label, position, createdAt) VALUES ('file:///sample.pdf', 'sample.pdf', 'Page 4', 3, 1000)")
        helper.close()

        // Step 2: Open with Room v2 + MIGRATION_1_2 and verify preservation & new tables
        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        val repo = LocalDataRepository(migratedDb)
        val history = repo.readingHistory.first()
        assertEquals(1, history.size)
        assertEquals("sample.pdf", history.first().name)
        assertEquals(3L, history.first().lastPosition)

        val bms = repo.allBookmarks.first()
        assertEquals(1, bms.size)
        assertEquals("Page 4", bms.first().label)

        repo.saveCustomCategory("Kotlin Code", "kt,kts")
        assertEquals(1, repo.customCategories.first().size)
        repo.seedDefaultRulesIfEmpty()
        assertTrue(repo.organizationRules.first().isNotEmpty())
        repo.createPlaylist("Study Mix")
        assertEquals(1, repo.playlists.first().size)

        migratedDb.close()
        dbFile.delete()
    }

    @Test
    fun `folder comparison similar image hash and srt subtitle sync offset`(): Unit = runBlocking<Unit> {
        val dirA = File(context.cacheDir, "cmpA_${System.currentTimeMillis()}").apply { mkdirs() }
        val dirB = File(context.cacheDir, "cmpB_${System.currentTimeMillis()}").apply { mkdirs() }
        File(dirA, "same.txt").writeText("abc")
        File(dirB, "same.txt").writeText("abc")
        File(dirA, "diff.txt").writeText("old")
        File(dirB, "diff.txt").writeText("newer-content")
        File(dirA, "onlyA.txt").writeText("1")
        File(dirB, "onlyB.txt").writeText("2")

        val cmp = Phase2FileOperations.compareFolders(context, Uri.fromFile(dirA), Uri.fromFile(dirB))
        assertEquals(1, cmp.identicalCount)
        assertEquals(1, cmp.differentCount)
        assertEquals(1, cmp.onlyLeftCount)
        assertEquals(1, cmp.onlyRightCount)
        assertTrue(cmp.entries.any { it.relativeName == "diff.txt" && it.status == FolderComparisonEntry.DiffStatus.DIFFERENT })

        val bmp1 = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val bmp2 = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val h1 = Phase2FileOperations.computeAverageHashFromBitmap(bmp1)
        val h2 = Phase2FileOperations.computeAverageHashFromBitmap(bmp2)
        assertEquals(0, Phase2FileOperations.hammingDistance(h1, h2))

        val srt = """
            1
            00:00:02,000 --> 00:00:05,000
            Hello Explorer!

            2
            00:00:06,000 --> 00:00:09,000
            <i>Second Cue</i>
        """.trimIndent()
        val cues = Phase2FileOperations.parseSrtOrVtt(srt)
        assertEquals(2, cues.size)
        assertEquals("Hello Explorer!", Phase2FileOperations.activeSubtitleForPosition(cues, 3000L, 0L))
        assertNull(Phase2FileOperations.activeSubtitleForPosition(cues, 5500L, 0L))
        // Sync offset +1000ms delays cue to appear at 3500ms -> shifted = 3500 - 1000 = 2500ms
        assertEquals("Hello Explorer!", Phase2FileOperations.activeSubtitleForPosition(cues, 3500L, 1000L))
        assertEquals("Second Cue", Phase2FileOperations.activeSubtitleForPosition(cues, 7000L, 0L))

        dirA.deleteRecursively()
        dirB.deleteRecursively()
    }

    @Test
    fun `bilingual english and bengali app strings are distinct`() {
        val en = stringsForLanguage("en")
        val bn = stringsForLanguage("bn")
        assertEquals("en", en.languageCode)
        assertEquals("bn", bn.languageCode)
        assertNotEquals(en.navHome, bn.navHome)
        assertNotEquals(en.settingsTitle, bn.settingsTitle)
        assertNotEquals(en.privateVaultTitle, bn.privateVaultTitle)
        assertNotEquals(en.smartAssistantTitle, bn.smartAssistantTitle)
        assertTrue(bn.navSettings.isNotBlank())
    }

    @Test
    fun `manifest launcher activity and view intent resolve cleanly`() {
        val pm = context.packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        val launchMatches = pm.queryIntentActivities(launchIntent, 0)
        assertTrue(launchMatches.any { it.activityInfo.name.endsWith("MainActivity") })

        val viewIntent = Intent(Intent.ACTION_VIEW).setType("application/pdf").setPackage(context.packageName)
        val viewMatches = pm.queryIntentActivities(viewIntent, 0)
        assertTrue(viewMatches.any { it.activityInfo.name.endsWith("MainActivity") })
    }
}
