package com.example.ui.screens.tools

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.example.data.storage.AdvancedStorageOperations
import com.example.domain.analyzer.ApkAndFileAnalyzer
import com.example.domain.analyzer.InstalledAppSummary
import com.example.domain.archive.ArchiveEngine
import com.example.domain.converter.ConversionEngine
import com.example.domain.image.ImageToolsEngine
import com.example.domain.model.FileItem
import com.example.domain.model.StorageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchOperationsScreen(
    initialFiles: List<FileItem>,
    conversionEngine: ConversionEngine,
    onMessage: (String) -> Unit,
    onRefreshBrowse: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(enabled = true) { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storageOps = remember { AdvancedStorageOperations(context) }
    val archiveEngine = remember { ArchiveEngine(context) }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Rename", "Resize Img", "ZIP Archive", "Batch Convert")

    var pickedFiles by remember { mutableStateOf(initialFiles) }

    val multiFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            pickedFiles = uris.map { uri ->
                val name = resolveFileName(context, uri)
                FileItem(uri = uri, name = name, path = uri.toString(), extension = name.substringAfterLast('.', ""))
            }
        }
    }

    val multiImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            pickedFiles = uris.mapIndexed { idx, uri ->
                val name = resolveFileName(context, uri).ifBlank { "image_$idx.jpg" }
                FileItem(uri = uri, name = name, path = uri.toString(), extension = name.substringAfterLast('.', "jpg"))
            }
        }
    }

    // Rename state
    var prefix by remember { mutableStateOf("") }
    var suffix by remember { mutableStateOf("") }
    var findStr by remember { mutableStateOf("") }
    var replaceStr by remember { mutableStateOf("") }
    var addNumbering by remember { mutableStateOf(false) }

    // Resize Image state
    var maxDim by remember { mutableIntStateOf(1280) }
    var imgQuality by remember { mutableFloatStateOf(85f) }
    var outImgFormat by remember { mutableStateOf("JPG") }

    // ZIP state
    var zipName by remember { mutableStateOf("batch_archive.zip") }
    var zipPassword by remember { mutableStateOf("") }

    // Convert state
    var batchConvertTarget by remember { mutableStateOf("PDF") }

    // Operation progress & cancellation state
    var isBusy by remember { mutableStateOf(false) }
    var progressValue by remember { mutableFloatStateOf(0f) }
    var progressLabel by remember { mutableStateOf("") }
    var lastErrors by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var pendingTransferIsMove by remember { mutableStateOf(false) }
    var showConfirmBatchTrash by remember { mutableStateOf(false) }

    val destFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { destUri ->
        if (destUri != null && pickedFiles.isNotEmpty()) {
            activeJob = scope.launch {
                isBusy = true
                lastErrors = emptyList()
                val errs = mutableListOf<String>()
                var okCount = 0
                val total = pickedFiles.size
                pickedFiles.forEachIndexed { idx, item ->
                    progressValue = (idx + 1).toFloat() / total.toFloat()
                    progressLabel = "${if (pendingTransferIsMove) "Moving" else "Copying"} ${idx + 1}/$total: ${item.name}"
                    val ok = if (pendingTransferIsMove) {
                        storageOps.moveDocument(item, destUri)
                    } else {
                        storageOps.copyDocument(item.uri, destUri, item.name, item.mimeType)
                    }
                    if (ok) okCount++ else errs.add("Failed: ${item.name}")
                }
                isBusy = false
                lastErrors = errs
                onRefreshBrowse()
                onMessage("${if (pendingTransferIsMove) "Moved" else "Copied"} $okCount of $total file(s)")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Batch Operations", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("batch_operations_screen")
        ) {
            SecondaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { idx, label ->
                    Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }, text = { Text(label, maxLines = 1) })
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { multiFilePicker.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                        Text("Pick Any Files (${pickedFiles.size})")
                    }
                    OutlinedButton(
                        onClick = {
                            multiImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Pick Photos")
                    }
                }

                // Quick Batch Copy, Move, Share, Delete row
                if (pickedFiles.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                pendingTransferIsMove = false
                                destFolderPicker.launch(null)
                            },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f)
                        ) { Text("Copy To") }
                        OutlinedButton(
                            onClick = {
                                pendingTransferIsMove = true
                                destFolderPicker.launch(null)
                            },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f)
                        ) { Text("Move To") }
                        OutlinedButton(
                            onClick = {
                                val shared = com.example.data.storage.Phase2FileOperations.shareMultipleFiles(context, pickedFiles)
                                if (!shared) onMessage("Unable to launch multi-file share")
                            },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f)
                        ) { Text("Share") }
                    }
                    OutlinedButton(
                        onClick = { showConfirmBatchTrash = true },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Batch Delete (${pickedFiles.size} selected)", color = MaterialTheme.colorScheme.error)
                    }
                }

                if (pickedFiles.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Selected (${pickedFiles.size}):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            pickedFiles.take(5).forEach { f ->
                                Text("• ${f.name}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                            if (pickedFiles.size > 5) {
                                Text("... and ${pickedFiles.size - 5} more", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                when (selectedTab) {
                    0 -> {
                        OutlinedTextField(value = prefix, onValueChange = { prefix = it }, label = { Text("Prefix") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = suffix, onValueChange = { suffix = it }, label = { Text("Suffix (before extension)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = findStr, onValueChange = { findStr = it }, label = { Text("Find text") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = replaceStr, onValueChange = { replaceStr = it }, label = { Text("Replace with") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Append sequential index (_01, _02...)")
                            Switch(checked = addNumbering, onCheckedChange = { addNumbering = it })
                        }
                        Button(
                            onClick = {
                                activeJob = scope.launch {
                                    isBusy = true
                                    lastErrors = emptyList()
                                    val errs = mutableListOf<String>()
                                    var okCount = 0
                                    withContext(Dispatchers.IO) {
                                        val total = pickedFiles.size.coerceAtLeast(1)
                                        pickedFiles.forEachIndexed { idx, item ->
                                            progressValue = (idx + 1).toFloat() / total.toFloat()
                                            progressLabel = "Renaming ${idx + 1}/$total: ${item.name}"
                                            if (item.isDirectory) return@forEachIndexed
                                            val base = item.name.substringBeforeLast('.', item.name)
                                            val ext = item.name.substringAfterLast('.', "")
                                            var newBase = if (findStr.isNotEmpty()) base.replace(findStr, replaceStr) else base
                                            val numPart = if (addNumbering) "_%02d".format(idx + 1) else ""
                                            newBase = "$prefix$newBase$suffix$numPart"
                                            val newName = if (ext.isNotEmpty()) "$newBase.$ext" else newBase
                                            val renamed = if (item.uri.scheme == "file") {
                                                val f = File(item.uri.path ?: "")
                                                f.renameTo(File(f.parentFile, newName))
                                            } else {
                                                DocumentFile.fromSingleUri(context, item.uri)?.renameTo(newName) == true
                                            }
                                            if (renamed) okCount++ else errs.add("Rename blocked: ${item.name}")
                                        }
                                    }
                                    isBusy = false
                                    lastErrors = errs
                                    onRefreshBrowse()
                                    onMessage("Batch renamed $okCount of ${pickedFiles.size} file(s)")
                                }
                            },
                            enabled = pickedFiles.isNotEmpty() && !isBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.DriveFileRenameOutline, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Run Batch Rename")
                        }
                    }

                    1 -> {
                        Text("Max Width / Height: $maxDim px", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(800, 1280, 1920, 2560).forEach { d ->
                                FilterChip(selected = maxDim == d, onClick = { maxDim = d }, label = { Text("${d}px") })
                            }
                        }
                        Text("Compression Quality: ${imgQuality.toInt()}%")
                        Slider(value = imgQuality, onValueChange = { imgQuality = it }, valueRange = 30f..100f)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("JPG", "PNG", "WEBP").forEach { fmt ->
                                FilterChip(selected = outImgFormat == fmt, onClick = { outImgFormat = fmt }, label = { Text(fmt) })
                            }
                        }
                        Button(
                            onClick = {
                                activeJob = scope.launch {
                                    isBusy = true
                                    lastErrors = emptyList()
                                    val errs = mutableListOf<String>()
                                    val outDir = File(context.filesDir, "BatchImages").apply { mkdirs() }
                                    val compressFmt = when (outImgFormat) {
                                        "PNG" -> Bitmap.CompressFormat.PNG
                                        "WEBP" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                                            Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                                        else -> Bitmap.CompressFormat.JPEG
                                    }
                                    var done = 0
                                    val total = pickedFiles.size.coerceAtLeast(1)
                                    pickedFiles.forEachIndexed { idx, item ->
                                        progressValue = (idx + 1).toFloat() / total.toFloat()
                                        progressLabel = "Resizing ${idx + 1}/$total: ${item.name}"
                                        val base = item.name.substringBeforeLast('.', "img_$idx")
                                        val outFile = File(outDir, "${base}_${maxDim}px.${outImgFormat.lowercase()}")
                                        ImageToolsEngine.transformAndSaveImage(
                                            context = context,
                                            sourceUri = item.uri,
                                            outputFile = outFile,
                                            targetWidth = maxDim,
                                            targetHeight = maxDim,
                                            format = compressFmt,
                                            quality = imgQuality.toInt()
                                        ).onSuccess { done++ }.onFailure { errs.add("${item.name}: ${it.message}") }
                                    }
                                    isBusy = false
                                    lastErrors = errs
                                    onMessage("Resized $done image(s) into App Storage/BatchImages")
                                }
                            },
                            enabled = pickedFiles.isNotEmpty() && !isBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Image, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Resize & Compress Batch")
                        }
                    }

                    2 -> {
                        OutlinedTextField(value = zipName, onValueChange = { zipName = it }, label = { Text("Archive Filename") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = zipPassword, onValueChange = { zipPassword = it }, label = { Text("Optional AES Password") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    activeJob = scope.launch {
                                        isBusy = true
                                        progressValue = 0.5f
                                        progressLabel = "Compressing ${pickedFiles.size} files..."
                                        val outDir = File(context.filesDir, "Archives").apply { mkdirs() }
                                        val finalName = if (zipName.endsWith(".zip", true)) zipName else "$zipName.zip"
                                        val outFile = File(outDir, finalName)
                                        val items = pickedFiles.mapNotNull { item ->
                                            storageOps.copyUriToTempCache(item.uri, item.extension.ifBlank { "bin" })
                                        }
                                        archiveEngine.createZipArchive(
                                            sourceFiles = items,
                                            outputZip = outFile,
                                            password = zipPassword.ifBlank { null }
                                        ).fold(
                                            onSuccess = { onMessage("Created ${it.name} in App Storage/Archives") },
                                            onFailure = {
                                                lastErrors = listOf(it.message ?: "ZIP failed")
                                                onMessage("ZIP failed: ${it.message}")
                                            }
                                        )
                                        isBusy = false
                                    }
                                },
                                enabled = pickedFiles.isNotEmpty() && !isBusy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Archive, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Compress ZIP")
                            }
                            OutlinedButton(
                                onClick = {
                                    activeJob = scope.launch {
                                        isBusy = true
                                        val errs = mutableListOf<String>()
                                        var ok = 0
                                        val total = pickedFiles.size.coerceAtLeast(1)
                                        val outRoot = File(context.filesDir, "ExtractedBatch").apply { mkdirs() }
                                        pickedFiles.forEachIndexed { idx, item ->
                                            progressValue = (idx + 1).toFloat() / total.toFloat()
                                            progressLabel = "Extracting ${item.name}..."
                                            val tmp = storageOps.copyUriToTempCache(item.uri, item.extension)
                                            if (tmp != null) {
                                                val dest = File(outRoot, item.name.substringBeforeLast('.')).apply { mkdirs() }
                                                archiveEngine.extractToDirectory(
                                                    archiveFile = tmp,
                                                    extension = item.extension,
                                                    destDir = dest,
                                                    password = zipPassword.ifBlank { null }
                                                )
                                                    .onSuccess { ok++ }
                                                    .onFailure { errs.add("${item.name}: ${it.message}") }
                                            } else errs.add("Unreadable: ${item.name}")
                                        }
                                        isBusy = false
                                        lastErrors = errs
                                        onMessage("Extracted $ok archive(s) → App Storage/ExtractedBatch")
                                    }
                                },
                                enabled = pickedFiles.isNotEmpty() && !isBusy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Batch Extract")
                            }
                        }
                    }

                    3 -> {
                        Text("Target Output Format", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("PDF", "PNG", "JPG", "WEBP", "WAV", "M4A", "CSV").forEach { fmt ->
                                FilterChip(
                                    selected = batchConvertTarget == fmt,
                                    onClick = { batchConvertTarget = fmt },
                                    label = { Text(fmt) }
                                )
                            }
                        }
                        Button(
                            onClick = {
                                var queued = 0
                                val errs = mutableListOf<String>()
                                pickedFiles.forEach { file ->
                                    val ext = file.extension.lowercase()
                                    val supported = ConversionEngine.getOptionsForExtension(ext)
                                        .any { it.targetFormat == batchConvertTarget && it.isSupported }
                                    if (supported) {
                                        conversionEngine.enqueueConversion(file.uri, file.name, ext, batchConvertTarget)
                                        queued++
                                    } else {
                                        errs.add("Incompatible for $batchConvertTarget: ${file.name}")
                                    }
                                }
                                lastErrors = errs
                                onMessage("Queued $queued compatible file(s) for $batchConvertTarget conversion")
                            },
                            enabled = pickedFiles.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Transform, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Queue Batch Conversion to $batchConvertTarget")
                        }
                    }
                }

                if (isBusy) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(progressLabel.ifBlank { "Processing..." }, style = MaterialTheme.typography.labelMedium)
                            androidx.compose.material3.LinearProgressIndicator(progress = { progressValue }, modifier = Modifier.fillMaxWidth())
                            OutlinedButton(onClick = {
                                activeJob?.cancel()
                                isBusy = false
                                onMessage("Batch operation cancelled")
                            }) { Text("Cancel Operation") }
                        }
                    }
                }

                if (lastErrors.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Warnings / Errors (${lastErrors.size}):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            lastErrors.take(6).forEach { e -> Text("• $e", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
        }
    }

    if (showConfirmBatchTrash) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showConfirmBatchTrash = false },
            title = { Text("Delete ${pickedFiles.size} file(s)?", fontWeight = FontWeight.Bold) },
            text = { Text("Selected files will be permanently deleted where permissions allow.") },
            confirmButton = {
                Button(onClick = {
                    showConfirmBatchTrash = false
                    activeJob = scope.launch {
                        isBusy = true
                        var ok = 0
                        val errs = mutableListOf<String>()
                        pickedFiles.forEachIndexed { idx, item ->
                            progressValue = (idx + 1).toFloat() / pickedFiles.size.coerceAtLeast(1)
                            progressLabel = "Deleting ${item.name}..."
                            val deleted = withContext(Dispatchers.IO) {
                                if (item.uri.scheme == "file") File(item.uri.path ?: "").delete()
                                else DocumentFile.fromSingleUri(context, item.uri)?.delete() == true
                            }
                            if (deleted) ok++ else errs.add("Protected / read-only: ${item.name}")
                        }
                        isBusy = false
                        lastErrors = errs
                        onRefreshBrowse()
                        onMessage("Deleted $ok of ${pickedFiles.size} file(s)")
                    }
                }) { Text("Delete") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { showConfirmBatchTrash = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstalledAppsScreen(
    onInspectApk: (FileItem) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(enabled = true) { onBack() }

    val context = LocalContext.current
    val analyzer = remember { ApkAndFileAnalyzer(context) }

    var apps by remember { mutableStateOf<List<InstalledAppSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var includeSystem by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(includeSystem) {
        isLoading = true
        apps = analyzer.getInstalledApps(includeSystem)
        isLoading = false
    }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else {
            val q = query.trim().lowercase()
            apps.filter { it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Installed Apps & APK Inspector", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("installed_apps_screen")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Filter apps or package...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = includeSystem,
                    onClick = { includeSystem = !includeSystem },
                    label = { Text("System") }
                )
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (app.apkPath.isNotBlank()) {
                                        val file = File(app.apkPath)
                                        onInspectApk(
                                            FileItem(
                                                uri = Uri.fromFile(file),
                                                name = "${app.appName}.apk",
                                                path = file.absolutePath,
                                                extension = "apk",
                                                size = app.apkSizeBytes,
                                                lastModified = if (file.exists()) file.lastModified() else 0L,
                                                mimeType = "application/vnd.android.package-archive"
                                            )
                                        )
                                    }
                                }
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Android, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(app.appName, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(app.packageName, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, maxLines = 1)
                                    Text(
                                        text = "v${app.versionName} (${app.versionCode}) • ${StorageStats.formatBytes(app.apkSizeBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
