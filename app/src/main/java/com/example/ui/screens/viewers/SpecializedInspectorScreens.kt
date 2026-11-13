package com.example.ui.screens.viewers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.storage.AdvancedStorageOperations
import com.example.domain.analyzer.ApkAndFileAnalyzer
import com.example.domain.analyzer.ApkInspectionInfo
import com.example.domain.engine.FileInspectionResult
import com.example.domain.model.FileItem
import com.example.domain.model.StorageStats
import com.example.domain.tools.DatabaseReader
import com.example.domain.tools.DbTablePage
import com.example.domain.tools.DbTableSummary
import java.io.File

@Composable
fun ApkViewerScreen(
    fileItem: FileItem,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val storageOps = remember { AdvancedStorageOperations(context) }
    val analyzer = remember { ApkAndFileAnalyzer(context) }

    var info by remember { mutableStateOf<ApkInspectionInfo?>(null) }
    var cachedFile by remember { mutableStateOf<File?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(fileItem.uri) {
        isLoading = true
        val file = storageOps.copyUriToTempCache(fileItem.uri, fileItem.extension.ifBlank { "apk" })
        cachedFile = file
        if (file == null) {
            errorMsg = "Cannot read APK/bundle file"
        } else {
            analyzer.inspectApkPackage(file, fileItem.extension).fold(
                onSuccess = { info = it },
                onFailure = { errorMsg = it.message ?: "Unable to parse APK manifest" }
            )
        }
        isLoading = false
    }

    if (isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (errorMsg != null || info == null) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(errorMsg ?: "APK inspection failed", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    val apk = info!!
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("apk_viewer_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    val iconBmp = remember(apk.icon) { apk.icon?.toBitmapOrNull() }
                    if (iconBmp != null) {
                        Image(
                            bitmap = iconBmp.asImageBitmap(),
                            contentDescription = apk.appName,
                            modifier = Modifier.size(56.dp)
                        )
                    } else {
                        Icon(Icons.Default.Android, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(apk.appName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(apk.packageName, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        Text(
                            text = "${apk.bundleFormat} • v${apk.versionName} (${apk.versionCode})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        if (apk.bundleFormat == "APK" && cachedFile != null) {
            item {
                Button(
                    onClick = {
                        val launched = analyzer.launchSystemApkInstaller(cachedFile!!)
                        if (!launched) {
                            onMessage("System installer could not be launched for this APK.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.InstallMobile, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Install APK via Android Package Installer")
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InfoPair("File Size", StorageStats.formatBytes(apk.fileSizeBytes))
                    InfoPair("Min SDK / Target SDK", "${apk.minSdk} / ${apk.targetSdk}")
                    InfoPair("APK SHA-256", apk.sha256Checksum, mono = true)
                    InfoPair("Signing Cert SHA-256", apk.signingCertSha256, mono = true)
                    if (apk.extraNotes.isNotBlank()) {
                        InfoPair("Bundle Details", apk.extraNotes)
                    }
                }
            }
        }

        item {
            Text(
                text = if (apk.bundleFormat == "APK") "Requested Permissions (${apk.permissions.size})" else "Archive / Split Entries (${apk.permissions.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(apk.permissions) { perm ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = perm,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun SqliteViewerScreen(
    fileItem: FileItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val storageOps = remember { AdvancedStorageOperations(context) }

    var dbFile by remember { mutableStateOf<File?>(null) }
    var tables by remember { mutableStateOf<List<DbTableSummary>>(emptyList()) }
    var selectedTable by remember { mutableStateOf<DbTableSummary?>(null) }
    var tablePage by remember { mutableStateOf<DbTablePage?>(null) }
    var offset by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(fileItem.uri) {
        isLoading = true
        val file = storageOps.copyUriToTempCache(fileItem.uri, "db")
        dbFile = file
        if (file == null) {
            errorMsg = "Cannot read database file"
        } else {
            DatabaseReader.inspectTables(file).fold(
                onSuccess = { list ->
                    tables = list
                    selectedTable = list.firstOrNull()
                },
                onFailure = { errorMsg = it.message ?: "Not a valid SQLite database" }
            )
        }
        isLoading = false
    }

    LaunchedEffect(dbFile, selectedTable, offset) {
        val file = dbFile ?: return@LaunchedEffect
        val tbl = selectedTable ?: return@LaunchedEffect
        DatabaseReader.queryTablePage(file, tbl.name, limit = 40, offset = offset).onSuccess {
            tablePage = it
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
            .testTag("sqlite_viewer_screen")
    ) {
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }
        if (errorMsg != null) {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
            }
            return
        }
        if (tables.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Database contains no user tables.")
            }
            return
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tables) { tbl ->
                FilterChip(
                    selected = selectedTable?.name == tbl.name,
                    onClick = {
                        selectedTable = tbl
                        offset = 0
                    },
                    label = { Text("${tbl.name} (${tbl.rowCount})") },
                    leadingIcon = { Icon(Icons.Default.Storage, null, modifier = Modifier.size(16.dp)) }
                )
            }
        }

        selectedTable?.let { tbl ->
            if (tbl.createSql.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = tbl.createSql,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Rows ${offset + 1} - ${offset + (tablePage?.rows?.size ?: 0)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Row {
                IconButton(onClick = { offset = (offset - 40).coerceAtLeast(0) }, enabled = offset > 0) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous rows")
                }
                IconButton(
                    onClick = { offset += 40 },
                    enabled = (tablePage?.rows?.size ?: 0) >= 40
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next rows")
                }
            }
        }

        tablePage?.let { page ->
            val hScroll = rememberScrollState()
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(hScroll)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .padding(vertical = 6.dp)
                    ) {
                        page.columns.forEach { col ->
                            Text(
                                text = col,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .width(130.dp)
                                    .padding(horizontal = 6.dp)
                            )
                        }
                    }
                    HorizontalDivider()
                }
                items(page.rows) { row ->
                    Row(modifier = Modifier.padding(vertical = 5.dp)) {
                        row.forEach { cell ->
                            Text(
                                text = cell,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 2,
                                modifier = Modifier
                                    .width(130.dp)
                                    .padding(horizontal = 6.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
    }
}

@Composable
fun FontViewerScreen(
    fileItem: FileItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val storageOps = remember { AdvancedStorageOperations(context) }
    val ext = fileItem.extension.lowercase()

    var typeface by remember { mutableStateOf<Typeface?>(null) }
    var sampleText by remember { mutableStateOf("The quick brown fox jumps over the lazy dog.\n0123456789 !@#$%^&*()") }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(fileItem.uri) {
        isLoading = true
        if (ext in setOf("woff", "woff2")) {
            errorMsg = "Web font (${ext.uppercase()}): Android Typeface renders TTF/OTF natively; WOFF/WOFF2 is inspect-only without decompression."
        } else {
            val file = storageOps.copyUriToTempCache(fileItem.uri, ext.ifBlank { "ttf" })
            if (file == null) {
                errorMsg = "Unable to read font file"
            } else {
                try {
                    typeface = Typeface.createFromFile(file)
                } catch (e: Exception) {
                    errorMsg = "Failed to load font: ${e.message}"
                }
            }
        }
        isLoading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("font_viewer_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator()
            return
        }
        if (errorMsg != null) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FontDownload, null)
                    Spacer(Modifier.width(12.dp))
                    Text(errorMsg!!, style = MaterialTheme.typography.bodyMedium)
                }
            }
            return
        }

        OutlinedTextField(
            value = sampleText,
            onValueChange = { sampleText = it },
            label = { Text("Custom Specimen Text") },
            modifier = Modifier.fillMaxWidth()
        )

        listOf(14, 18, 24, 32).forEach { spSize ->
            Card(
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("${spSize}sp", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    AndroidView(
                        factory = { ctx ->
                            android.widget.TextView(ctx).apply {
                                typeface?.let { setTypeface(it) }
                                textSize = spSize.toFloat()
                            }
                        },
                        update = { tv ->
                            tv.typeface = typeface
                            tv.text = sampleText
                            tv.textSize = spSize.toFloat()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun HexBinaryViewerScreen(
    fileItem: FileItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val storageOps = remember { AdvancedStorageOperations(context) }

    val pageSize = 512
    var offset by remember { mutableLongStateOf(0L) }
    var chunk by remember { mutableStateOf(ByteArray(0)) }
    var showBinary by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(fileItem.uri, offset) {
        isLoading = true
        chunk = storageOps.readHexChunk(fileItem.uri, offset, pageSize)
        isLoading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
            .testTag("hex_viewer_screen")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Offset: 0x%08X (${offset} / ${StorageStats.formatBytes(fileItem.size)})".format(offset),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace
            )
            FilterChip(
                selected = showBinary,
                onClick = { showBinary = !showBinary },
                label = { Text(if (showBinary) "Binary" else "Hex + ASCII") }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { offset = (offset - pageSize).coerceAtLeast(0L) },
                enabled = offset > 0L,
                modifier = Modifier.weight(1f)
            ) { Text("Prev 512B") }
            OutlinedButton(
                onClick = { offset += pageSize },
                enabled = chunk.size == pageSize,
                modifier = Modifier.weight(1f)
            ) { Text("Next 512B") }
        }

        Spacer(Modifier.height(8.dp))

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (chunk.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("End of file or empty stream.")
            }
        } else {
            val bytesPerRow = if (showBinary) 4 else 8
            val rows = remember(chunk, bytesPerRow) {
                (0 until chunk.size step bytesPerRow).map { start ->
                    start to chunk.copyOfRange(start, minOf(start + bytesPerRow, chunk.size))
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
            ) {
                items(rows) { (relOffset, rowBytes) ->
                    val absOff = offset + relOffset
                    val encoded = if (showBinary) {
                        rowBytes.joinToString(" ") { b ->
                            Integer.toBinaryString(b.toInt() and 0xFF).padStart(8, '0')
                        }
                    } else {
                        rowBytes.joinToString(" ") { "%02X".format(it) }.padEnd(bytesPerRow * 3 - 1, ' ')
                    }
                    val ascii = rowBytes.map { b ->
                        val c = b.toInt() and 0xFF
                        if (c in 32..126) c.toChar() else '.'
                    }.joinToString("")

                    Text(
                        text = "%06X:  %s   |%s|".format(absOff, encoded, ascii),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun UnsupportedFallbackScreen(
    fileItem: FileItem,
    inspection: FileInspectionResult?,
    onOpenHex: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = inspection?.formatName ?: "Unsupported Format",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = inspection?.supportNotes
                        ?: "No native renderer is available for .${fileItem.extension}. You can inspect raw bytes in Hex Mode or open externally.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (inspection != null) {
                    Text(
                        text = "Signature: ${inspection.signatureMatch}\nHeader: ${inspection.magicBytesHex}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onOpenHex) {
                        Icon(Icons.Default.Code, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Inspect Hex")
                    }
                    OutlinedButton(onClick = onShare) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open Externally")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoPair(label: String, value: String, mono: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun Drawable.toBitmapOrNull(): Bitmap? {
    return try {
        val w = intrinsicWidth.coerceAtLeast(64)
        val h = intrinsicHeight.coerceAtLeast(64)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bmp
    } catch (_: Exception) {
        null
    }
}
