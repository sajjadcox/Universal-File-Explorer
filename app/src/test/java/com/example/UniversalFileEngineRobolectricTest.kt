package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.LocalDataRepository
import com.example.data.db.TrashEntryEntity
import com.example.domain.archive.ArchiveEngine
import com.example.domain.engine.FileSignatureDetector
import com.example.domain.engine.FormatSupportStatus
import com.example.domain.engine.ViewerType
import com.example.domain.model.FileItem
import com.example.domain.office.OfficeEngine
import com.example.domain.pdf.PdfEngine
import com.example.domain.tools.DatabaseReader
import com.example.domain.tools.DiffOp
import com.example.domain.tools.ProductivityEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UniversalFileEngineRobolectricTest {

    private lateinit var context: Context
    private lateinit var tempDir: File
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        tempDir = File(context.cacheDir, "engine_test_${System.nanoTime()}").apply { mkdirs() }
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun `magic byte signature detector identifies PDF PNG SQLite and RAR`() = runBlocking {
        val pdfFile = File(tempDir, "sample.bin").apply {
            writeBytes("%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".toByteArray())
        }
        val pdfRes = FileSignatureDetector.inspectFile(
            context,
            FileItem(Uri.fromFile(pdfFile), pdfFile.name, pdfFile.absolutePath, "bin", pdfFile.length())
        )
        assertEquals(ViewerType.PDF, pdfRes.viewerType)
        assertEquals(FormatSupportStatus.FULL, pdfRes.supportStatus)

        val pngFile = File(tempDir, "img.unknown").apply {
            writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00))
        }
        val pngRes = FileSignatureDetector.inspectFile(
            context,
            FileItem(Uri.fromFile(pngFile), pngFile.name, pngFile.absolutePath, "unknown", pngFile.length())
        )
        assertEquals(ViewerType.IMAGE, pngRes.viewerType)

        val rarFile = File(tempDir, "pack.rar").apply {
            writeBytes(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00))
        }
        val rarRes = FileSignatureDetector.inspectFile(
            context,
            FileItem(Uri.fromFile(rarFile), rarFile.name, rarFile.absolutePath, "rar", rarFile.length())
        )
        assertEquals(ViewerType.ARCHIVE, rarRes.viewerType)
        assertEquals(FormatSupportStatus.INSPECT_ONLY, rarRes.supportStatus)
    }

    @Test
    fun `archive engine creates password zip lists entries and prevents zip slip`() = runBlocking {
        val archiveEngine = ArchiveEngine(context)
        val txtFile = File(tempDir, "hello.txt").apply { writeText("Hello Archive World") }
        val outZip = File(tempDir, "secured.zip")

        val created = archiveEngine.createZipArchive(
            sourceFiles = listOf(txtFile),
            outputZip = outZip,
            password = "secret"
        )
        assertTrue(created.isSuccess)

        val listing = archiveEngine.listArchive(outZip, "zip").getOrThrow()
        assertTrue(listing.isPasswordProtected)
        assertEquals(1, listing.entries.size)
        assertEquals("hello.txt", listing.entries.first().name)

        // Test ZIP Slip guard against malicious "../../evil.txt" entry
        val evilZip = File(tempDir, "evil.zip")
        ZipOutputStream(FileOutputStream(evilZip)).use { zos ->
            zos.putNextEntry(ZipEntry("../../evil.txt"))
            zos.write("malicious".toByteArray())
            zos.closeEntry()
        }
        val extractTarget = File(tempDir, "extract_out").apply { mkdirs() }
        val extractResult = archiveEngine.extractToDirectory(evilZip, "zip", extractTarget)
        assertTrue(extractResult.isFailure)
        assertFalse(File(tempDir, "evil.txt").exists())
    }

    @Test
    fun `office engine parses csv and docx xml structures`() = runBlocking {
        val csvFile = File(tempDir, "data.csv").apply {
            writeText("Name,Score\nAlice,95\nBob,88\n")
        }
        val sheetModel = OfficeEngine.parseSpreadsheet(csvFile, "csv")
        assertEquals(1, sheetModel.sheets.size)
        assertEquals(3, sheetModel.sheets.first().rows.size)
        assertEquals("Alice", sheetModel.sheets.first().rows[1][0].value)

        // Minimal valid DOCX zip
        val docxFile = File(tempDir, "test.docx")
        ZipOutputStream(FileOutputStream(docxFile)).use { zos ->
            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Project Title</w:t></w:r></w:p>
                    <w:p><w:r><w:t>Body content line.</w:t></w:r></w:p>
                  </w:body>
                </w:document>
                """.trimIndent().toByteArray()
            )
            zos.closeEntry()
        }
        val doc = OfficeEngine.parseWordDocument(docxFile, "docx")
        assertTrue(doc.fullPlainText.contains("Project Title"))
        assertTrue(doc.fullPlainText.contains("Body content line."))
    }

    @Test
    fun `productivity engine formats json xml computes diff generates qr and splits merges files`() = runBlocking {
        val prettyJson = ProductivityEngine.formatAndValidateJson("""{"b":1,"a":[2,3]}""").getOrThrow()
        assertTrue(prettyJson.contains("\n"))

        val badJson = ProductivityEngine.formatAndValidateJson("""{broken""")
        assertTrue(badJson.isFailure)

        val prettyXml = ProductivityEngine.formatAndValidateXml("<root><item>ok</item></root>").getOrThrow()
        assertTrue(prettyXml.contains("<item>ok</item>"))

        val diff = ProductivityEngine.computeLineDiff("a\nb\nc", "a\nx\nc")
        assertTrue(diff.any { it.op == DiffOp.REMOVED && it.text == "b" })
        assertTrue(diff.any { it.op == DiffOp.ADDED && it.text == "x" })

        val qr = ProductivityEngine.generateQrCodeBitmap("https://example.org", 128).getOrThrow()
        assertEquals(128, qr.width)

        val bigFile = File(tempDir, "big.bin").apply { writeBytes(ByteArray(3500) { (it % 251).toByte() }) }
        val partsDir = File(tempDir, "parts")
        val parts = ProductivityEngine.splitFile(bigFile, partsDir, 1024L).getOrThrow()
        assertEquals(4, parts.size)

        val mergedFile = File(tempDir, "merged.bin")
        ProductivityEngine.mergeFiles(parts, mergedFile).getOrThrow()
        assertTrue(bigFile.readBytes().contentEquals(mergedFile.readBytes()))
    }

    @Test
    fun `sqlite database reader inspects tables and rows`() = runBlocking {
        val dbFile = File(tempDir, "sample.sqlite")
        val sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqlite.execSQL("CREATE TABLE items (id INTEGER PRIMARY KEY, name TEXT)")
        sqlite.execSQL("INSERT INTO items (name) VALUES ('Alpha'), ('Beta')")
        sqlite.close()

        val tables = DatabaseReader.inspectTables(dbFile).getOrThrow()
        assertEquals(1, tables.size)
        assertEquals("items", tables.first().name)
        assertEquals(2L, tables.first().rowCount)

        val page = DatabaseReader.queryTablePage(dbFile, "items").getOrThrow()
        assertEquals(listOf("id", "name"), page.columns)
        assertEquals(2, page.rows.size)
    }

    @Test
    fun `pdf engine creates pdf from image and exports lines to pdf`() = runBlocking {
        val imgFile = File(tempDir, "pic.png")
        val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        FileOutputStream(imgFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val outPdf = File(tempDir, "from_img.pdf")
        val pdfEngine = PdfEngine(context)
        val res = pdfEngine.imagesToPdf(listOf(Uri.fromFile(imgFile)), outPdf)
        assertTrue(res.isSuccess)
        assertTrue(outPdf.exists() && outPdf.length() > 100)

        val textPdf = File(tempDir, "from_lines.pdf")
        val linesRes = OfficeEngine.exportLinesToPdf("Report", listOf("Line 1", "Line 2"), textPdf)
        assertTrue(linesRes.isSuccess)
        assertTrue(textPdf.exists() && textPdf.length() > 100)
    }

    @Test
    fun `room local data repository persists history bookmarks search and trash`() = runBlocking {
        val repo = LocalDataRepository(db)
        repo.recordFileOpened("file:///a.pdf", "a.pdf", "application/pdf", "PDF", 1024L, 3L, 10L)
        val hist = repo.readingHistory.first()
        assertEquals(1, hist.size)
        assertEquals(3L, hist.first().lastPosition)

        repo.addBookmark("file:///a.pdf", "a.pdf", "Page 4", 3L)
        assertEquals(1, repo.allBookmarks.first().size)

        repo.recordSearch("invoice")
        assertEquals("invoice", repo.searchHistory.first().first().query)

        repo.addTrashEntry(TrashEntryEntity(originalName = "old.txt", originalUri = "file:///old.txt", trashFilePath = "/tmp/old.txt", mimeType = "text/plain", size = 50L))
        assertEquals(1, repo.trashEntries.first().size)
    }
}
