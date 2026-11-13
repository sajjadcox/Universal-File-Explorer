package com.example.ui.screens.viewers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.storage.AdvancedStorageOperations
import com.example.domain.model.FileItem
import com.example.domain.pdf.NormalizedPoint
import com.example.domain.pdf.PdfAnnotation
import com.example.domain.pdf.PdfAnnotationTool
import com.example.domain.pdf.PdfEngine
import com.example.domain.pdf.PdfMetadataInfo
import com.example.domain.pdf.PdfSearchMatch
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PdfViewerScreen(
    fileItem: FileItem,
    initialPage: Int = 0,
    onPageChanged: (Int, Int) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    keepAwakeDefault: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pdfEngine = remember { PdfEngine(context) }
    val storageOps = remember { AdvancedStorageOperations(context) }

    var workingPdfFile by remember { mutableStateOf<File?>(null) }
    var metadata by remember { mutableStateOf<PdfMetadataInfo?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableIntStateOf(initialPage.coerceAtLeast(0)) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var needsPassword by remember { mutableStateOf(false) }
    var activePassword by remember { mutableStateOf<String?>(null) }
    var passwordInput by remember { mutableStateOf("") }

    var isContinuousMode by remember { mutableStateOf(false) }
    var showThumbnails by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showPageToolsSheet by remember { mutableStateOf(false) }
    var showMetadataDialog by remember { mutableStateOf(false) }
    var extractedTextDialog by remember { mutableStateOf<String?>(null) }
    var showTextNotePrompt by remember { mutableStateOf<NormalizedPoint?>(null) }
    var textNoteInput by remember { mutableStateOf("") }
    var showSignaturePad by remember { mutableStateOf(false) }
    var showSaveOptionsDialog by remember { mutableStateOf(false) }
    var showPasswordProtectDialog by remember { mutableStateOf(false) }
    var protectPwdInput by remember { mutableStateOf("") }
    var showReorderDialog by remember { mutableStateOf(false) }
    var nightFilter by remember { mutableStateOf(false) }
    var keepAwake by remember(keepAwakeDefault) { mutableStateOf(keepAwakeDefault) }
    var isSpeaking by remember { mutableStateOf(false) }
    val ttsHelper = remember { ReaderTtsHelper(context) }

    DisposableEffect(Unit) {
        onDispose { ttsHelper.shutdown() }
    }
    KeepScreenAwakeEffect(keepAwake)

    var activeTool by remember { mutableStateOf(PdfAnnotationTool.NONE) }
    val annotations = remember { mutableStateListOf<PdfAnnotation>() }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Merge PDF picker
    val mergePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        val base = workingPdfFile ?: return@rememberLauncherForActivityResult
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val extraFiles = uris.mapIndexedNotNull { idx, u ->
                storageOps.copyUriToTempCache(u, "merge_$idx.pdf")
            }
            val outDir = File(context.filesDir, "PDF_Output").apply { mkdirs() }
            val outFile = File(outDir, "Merged_${System.currentTimeMillis()}.pdf")
            val res = pdfEngine.mergePdfs(listOf(base) + extraFiles, outFile)
            res.onSuccess {
                onMessage("Merged PDF saved to ${it.name}")
            }.onFailure {
                onMessage("Merge failed: ${it.message}")
            }
        }
    }

    // Load PDF into working temp file
    LaunchedEffect(fileItem.uri, refreshTrigger) {
        isLoading = true
        errorMsg = null
        val file = storageOps.copyUriToTempCache(fileItem.uri, "pdf")
        if (file == null) {
            errorMsg = "Unable to open PDF stream."
            isLoading = false
            return@LaunchedEffect
        }
        val metaRes = pdfEngine.inspectMetadata(file, activePassword)
        metaRes.onSuccess { info ->
            metadata = info
            if (info.needsPassword) {
                needsPassword = true
                workingPdfFile = file
            } else {
                needsPassword = false
                if (activePassword != null) {
                    val unlocked = pdfEngine.unlockPdfToCache(file, activePassword!!).getOrNull() ?: file
                    workingPdfFile = unlocked
                } else {
                    workingPdfFile = file
                }
                val cnt = pdfEngine.getPageCountNative(workingPdfFile!!).takeIf { it > 0 } ?: info.pageCount
                pageCount = cnt
                if (currentPage >= cnt && cnt > 0) currentPage = cnt - 1
                onPageChanged(currentPage, cnt)
            }
        }.onFailure { err ->
            errorMsg = "PDF open error: ${err.message}"
        }
        isLoading = false
    }

    // Password prompt for encrypted PDF
    if (needsPassword) {
        AlertDialog(
            onDismissRequest = { needsPassword = false },
            title = { Text("Password-Protected PDF", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("This PDF requires a password to decrypt and render.")
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    activePassword = passwordInput
                    refreshTrigger++
                }) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = { needsPassword = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = modifier.fillMaxSize().testTag("pdf_viewer_screen")) {
        // PDF Top Control Strip
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { isContinuousMode = !isContinuousMode }) {
                    Icon(
                        if (isContinuousMode) Icons.Default.ViewCarousel else Icons.Default.ViewStream,
                        contentDescription = "Toggle continuous/single page"
                    )
                }
                IconButton(onClick = { showThumbnails = !showThumbnails }) {
                    Icon(Icons.Default.Image, contentDescription = "Toggle thumbnails")
                }
                IconButton(onClick = { showSearchDialog = true }) {
                    Icon(Icons.Default.Search, contentDescription = "Search text")
                }
                IconButton(onClick = {
                    val file = workingPdfFile ?: return@IconButton
                    scope.launch {
                        val txt = pdfEngine.extractPageText(file, currentPage, activePassword)
                        extractedTextDialog = txt.ifBlank { "(No selectable text on this page — scanned/image PDF?)" }
                    }
                }) {
                    Icon(Icons.Default.SelectAll, contentDescription = "Extract & copy page text")
                }
                IconButton(onClick = {
                    activeTool = if (activeTool == PdfAnnotationTool.FREEHAND) PdfAnnotationTool.NONE else PdfAnnotationTool.FREEHAND
                }) {
                    Icon(
                        Icons.Default.Draw,
                        contentDescription = "Freehand Draw",
                        tint = if (activeTool == PdfAnnotationTool.FREEHAND) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    activeTool = if (activeTool == PdfAnnotationTool.HIGHLIGHT) PdfAnnotationTool.NONE else PdfAnnotationTool.HIGHLIGHT
                }) {
                    Icon(
                        Icons.Default.Highlight,
                        contentDescription = "Highlight",
                        tint = if (activeTool == PdfAnnotationTool.HIGHLIGHT) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    activeTool = if (activeTool == PdfAnnotationTool.UNDERLINE) PdfAnnotationTool.NONE else PdfAnnotationTool.UNDERLINE
                }) {
                    Icon(
                        Icons.Default.FormatUnderlined,
                        contentDescription = "Underline",
                        tint = if (activeTool == PdfAnnotationTool.UNDERLINE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    activeTool = if (activeTool == PdfAnnotationTool.STRIKETHROUGH) PdfAnnotationTool.NONE else PdfAnnotationTool.STRIKETHROUGH
                }) {
                    Icon(
                        Icons.Default.FormatStrikethrough,
                        contentDescription = "Strikethrough",
                        tint = if (activeTool == PdfAnnotationTool.STRIKETHROUGH) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    activeTool = if (activeTool == PdfAnnotationTool.TEXT_NOTE) PdfAnnotationTool.NONE else PdfAnnotationTool.TEXT_NOTE
                }) {
                    Icon(
                        Icons.Default.NoteAdd,
                        contentDescription = "Add Text Note",
                        tint = if (activeTool == PdfAnnotationTool.TEXT_NOTE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showSignaturePad = true }) {
                    Icon(Icons.Default.Gesture, contentDescription = "Signature placement")
                }
                if (annotations.isNotEmpty()) {
                    IconButton(onClick = { annotations.removeAt(annotations.lastIndex) }) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo annotation")
                    }
                    IconButton(onClick = { showSaveOptionsDialog = true }) {
                        Icon(Icons.Default.Save, contentDescription = "Save annotated PDF", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                IconButton(onClick = { showPageToolsSheet = true }) {
                    Icon(Icons.Default.Build, contentDescription = "PDF Page & File Tools")
                }
                IconButton(onClick = { nightFilter = !nightFilter }) {
                    Icon(
                        Icons.Default.Brightness4,
                        contentDescription = "Night Reading Filter",
                        tint = if (nightFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { keepAwake = !keepAwake }) {
                    Icon(
                        Icons.Default.BrightnessHigh,
                        contentDescription = "Keep Screen Awake",
                        tint = if (keepAwake) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    val file = workingPdfFile ?: return@IconButton
                    if (isSpeaking) {
                        ttsHelper.stop()
                        isSpeaking = false
                    } else {
                        scope.launch {
                            val txt = pdfEngine.extractPageText(file, currentPage, activePassword)
                            if (txt.isNotBlank() && ttsHelper.speak(txt)) {
                                isSpeaking = true
                            } else {
                                onMessage("No selectable text on this page for Read Aloud")
                            }
                        }
                    }
                }) {
                    Icon(
                        Icons.Default.RecordVoiceOver,
                        contentDescription = "Read Page Aloud (TTS)",
                        tint = if (isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = {
                    workingPdfFile?.let { pdfEngine.printPdf(it, fileItem.name) }
                }) {
                    Icon(Icons.Default.Print, contentDescription = "Print PDF")
                }
            }
        }

        if (activeTool != PdfAnnotationTool.NONE) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Annotation Mode: ${activeTool.name} (draw or tap on page)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(onClick = { activeTool = PdfAnnotationTool.NONE }) {
                        Text("Done")
                    }
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (errorMsg != null) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
            }
        } else if (workingPdfFile != null && pageCount > 0) {
            // Thumbnail Strip
            if (showThumbnails) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pageCount) { idx ->
                        var thumb by remember(workingPdfFile, idx, refreshTrigger) { mutableStateOf<Bitmap?>(null) }
                        LaunchedEffect(workingPdfFile, idx, refreshTrigger) {
                            thumb = pdfEngine.renderPageBitmap(workingPdfFile!!, idx, 160)
                        }
                        Box(
                            modifier = Modifier
                                .width(72.dp)
                                .height(94.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (idx == currentPage) 2.dp else 1.dp,
                                    color = if (idx == currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    currentPage = idx
                                    onPageChanged(idx, pageCount)
                                }
                        ) {
                            if (thumb != null) {
                                Image(
                                    bitmap = thumb!!.asImageBitmap(),
                                    contentDescription = "Page ${idx + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Surface(
                                color = Color.Black.copy(alpha = 0.6f),
                                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Main Page Viewport
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (isContinuousMode) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(pageCount) { idx ->
                            PdfInteractivePageCanvas(
                                pdfFile = workingPdfFile!!,
                                pdfEngine = pdfEngine,
                                pageIndex = idx,
                                refreshTrigger = refreshTrigger,
                                activeTool = activeTool,
                                pageAnnotations = annotations.filter { it.pageIndex == idx },
                                onAddAnnotation = { annotations.add(it) },
                                onRequestTextNote = { pt ->
                                    currentPage = idx
                                    showTextNotePrompt = pt
                                },
                                scale = scale,
                                onScaleChange = { scale = it },
                                nightFilter = nightFilter
                            )
                        }
                    }
                } else {
                    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                        if (activeTool == PdfAnnotationTool.NONE) {
                            scale = (scale * zoomChange).coerceIn(1f, 4.5f)
                            if (scale > 1f) offset += panChange else offset = Offset.Zero
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (activeTool == PdfAnnotationTool.NONE)
                                    Modifier.transformable(transformState)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y
                                )
                        ) {
                            PdfInteractivePageCanvas(
                                pdfFile = workingPdfFile!!,
                                pdfEngine = pdfEngine,
                                pageIndex = currentPage,
                                refreshTrigger = refreshTrigger,
                                activeTool = activeTool,
                                pageAnnotations = annotations.filter { it.pageIndex == currentPage },
                                onAddAnnotation = { annotations.add(it) },
                                onRequestTextNote = { pt -> showTextNotePrompt = pt },
                                scale = scale,
                                onScaleChange = { scale = it },
                                nightFilter = nightFilter
                            )
                        }
                    }
                }
            }

            // Bottom Page Navigation Bar
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (currentPage > 0) {
                                currentPage--
                                scale = 1f; offset = Offset.Zero
                                onPageChanged(currentPage, pageCount)
                            }
                        },
                        enabled = currentPage > 0
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page")
                    }

                    Text(
                        text = "Page ${currentPage + 1} of $pageCount  •  ${(scale * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            scale = 1f; offset = Offset.Zero
                        }
                    )

                    IconButton(
                        onClick = {
                            if (currentPage < pageCount - 1) {
                                currentPage++
                                scale = 1f; offset = Offset.Zero
                                onPageChanged(currentPage, pageCount)
                            }
                        },
                        enabled = currentPage < pageCount - 1
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page")
                    }
                }
            }
        }
    }

    // Text Search Dialog
    if (showSearchDialog && workingPdfFile != null) {
        var query by remember { mutableStateOf("") }
        var matches by remember { mutableStateOf<List<PdfSearchMatch>>(emptyList()) }
        var searching by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Search inside PDF", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Keyword") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            scope.launch {
                                searching = true
                                matches = pdfEngine.searchText(workingPdfFile!!, query, activePassword)
                                searching = false
                            }
                        }) { Text("Find") }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (searching) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else if (matches.isEmpty() && query.isNotBlank()) {
                        Text("No matches found.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Column(modifier = Modifier.height(220.dp).verticalScroll(rememberScrollState())) {
                            matches.forEach { m ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clickable {
                                            currentPage = m.pageIndex
                                            onPageChanged(m.pageIndex, pageCount)
                                            showSearchDialog = false
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text("Page ${m.pageIndex + 1}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                        Text(m.snippet, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearchDialog = false }) { Text("Close") }
            }
        )
    }

    // Extracted Page Text Selection & Copy Dialog
    extractedTextDialog?.let { txt ->
        AlertDialog(
            onDismissRequest = { extractedTextDialog = null },
            title = { Text("Page ${currentPage + 1} Text", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.height(280.dp).verticalScroll(rememberScrollState())) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(text = txt, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("PDF Text", txt))
                    Toast.makeText(context, "Copied page text", Toast.LENGTH_SHORT).show()
                    extractedTextDialog = null
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy All")
                }
            },
            dismissButton = {
                TextButton(onClick = { extractedTextDialog = null }) { Text("Close") }
            }
        )
    }

    // Add Text Note Prompt
    showTextNotePrompt?.let { pt ->
        AlertDialog(
            onDismissRequest = { showTextNotePrompt = null },
            title = { Text("Add Text Annotation", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = textNoteInput,
                    onValueChange = { textNoteInput = it },
                    label = { Text("Annotation text") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (textNoteInput.isNotBlank()) {
                        annotations.add(
                            PdfAnnotation(
                                pageIndex = currentPage,
                                tool = PdfAnnotationTool.TEXT_NOTE,
                                colorArgb = AndroidColor.rgb(21, 101, 192),
                                points = listOf(pt),
                                text = textNoteInput
                            )
                        )
                    }
                    textNoteInput = ""
                    showTextNotePrompt = null
                }) { Text("Place") }
            },
            dismissButton = {
                TextButton(onClick = { showTextNotePrompt = null }) { Text("Cancel") }
            }
        )
    }

    // Signature Pad Dialog
    if (showSignaturePad) {
        val sigStrokes = remember { mutableStateListOf<List<NormalizedPoint>>() }
        var currentStroke by remember { mutableStateOf<List<NormalizedPoint>>(emptyList()) }
        var padSize by remember { mutableStateOf(IntSize(1, 1)) }

        AlertDialog(
            onDismissRequest = { showSignaturePad = false },
            title = { Text("Draw Signature", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Draw your signature below. It will be placed on page ${currentPage + 1}.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .onSizeChanged { padSize = it }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { pos ->
                                        currentStroke = listOf(
                                            NormalizedPoint(
                                                (pos.x / padSize.width).coerceIn(0f, 1f),
                                                (pos.y / padSize.height).coerceIn(0f, 1f)
                                            )
                                        )
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentStroke = currentStroke + NormalizedPoint(
                                            (change.position.x / padSize.width).coerceIn(0f, 1f),
                                            (change.position.y / padSize.height).coerceIn(0f, 1f)
                                        )
                                    },
                                    onDragEnd = {
                                        if (currentStroke.size >= 2) sigStrokes.add(currentStroke)
                                        currentStroke = emptyList()
                                    }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            (sigStrokes + listOf(currentStroke)).forEach { stroke ->
                                if (stroke.size >= 2) {
                                    val path = Path().apply {
                                        moveTo(stroke.first().x * size.width, stroke.first().y * size.height)
                                        stroke.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) }
                                    }
                                    drawPath(path, Color.Black, style = Stroke(width = 5f))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (sigStrokes.isNotEmpty()) {
                            annotations.add(
                                PdfAnnotation(
                                    pageIndex = currentPage,
                                    tool = PdfAnnotationTool.SIGNATURE,
                                    colorArgb = AndroidColor.BLACK,
                                    points = listOf(NormalizedPoint(0.72f, 0.85f)),
                                    signaturePoints = sigStrokes.toList()
                                )
                            )
                        }
                        showSignaturePad = false
                    }
                ) { Text("Place Signature") }
            },
            dismissButton = {
                TextButton(onClick = { showSignaturePad = false }) { Text("Cancel") }
            }
        )
    }

    // Save Annotated Copy vs Overwrite Dialog
    if (showSaveOptionsDialog && workingPdfFile != null) {
        AlertDialog(
            onDismissRequest = { showSaveOptionsDialog = false },
            title = { Text("Save Annotated PDF", fontWeight = FontWeight.Bold) },
            text = {
                Text("Preserve the original document by saving a new copy, or overwrite if writable.")
            },
            confirmButton = {
                Button(onClick = {
                    val outDir = File(context.filesDir, "PDF_Output").apply { mkdirs() }
                    val outFile = File(outDir, "${fileItem.name.substringBeforeLast('.')}_annotated_${System.currentTimeMillis()}.pdf")
                    scope.launch {
                        pdfEngine.saveAnnotatedPdf(workingPdfFile!!, outFile, annotations.toList(), activePassword)
                            .onSuccess {
                                annotations.clear()
                                onMessage("Saved annotated copy: ${it.name}")
                            }.onFailure {
                                onMessage("Save failed: ${it.message}")
                            }
                    }
                    showSaveOptionsDialog = false
                }) { Text("Save New Copy") }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (fileItem.uri.scheme == "file" && fileItem.canWrite) {
                        val target = File(fileItem.uri.path ?: "")
                        scope.launch {
                            val tempOut = File(context.cacheDir, "overwrite_${System.currentTimeMillis()}.pdf")
                            pdfEngine.saveAnnotatedPdf(workingPdfFile!!, tempOut, annotations.toList(), activePassword)
                                .onSuccess {
                                    tempOut.copyTo(target, overwrite = true)
                                    tempOut.delete()
                                    annotations.clear()
                                    refreshTrigger++
                                    onMessage("Overwrote original PDF")
                                }.onFailure { onMessage("Overwrite failed: ${it.message}") }
                        }
                    } else {
                        onMessage("Original URI is read-only; please save as a new copy.")
                    }
                    showSaveOptionsDialog = false
                }) { Text("Overwrite Original") }
            }
        )
    }

    // PDF Page & File Tools Dialog
    if (showPageToolsSheet && workingPdfFile != null) {
        val outDir = remember { File(context.filesDir, "PDF_Output").apply { mkdirs() } }
        val base = fileItem.name.substringBeforeLast('.')

        AlertDialog(
            onDismissRequest = { showPageToolsSheet = false },
            title = { Text("PDF Tools & Page Manager", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolRowButton(Icons.Default.RotateRight, "Rotate Current Page 90°") {
                        scope.launch {
                            val out = File(outDir, "${base}_rotated_${System.currentTimeMillis()}.pdf")
                            pdfEngine.rotatePage(workingPdfFile!!, out, currentPage, 90)
                                .onSuccess { workingPdfFile = it; refreshTrigger++; onMessage("Rotated & saved ${it.name}") }
                                .onFailure { onMessage("Rotate failed: ${it.message}") }
                        }
                        showPageToolsSheet = false
                    }
                    ToolRowButton(Icons.Default.ContentCopy, "Extract Current Page as PDF") {
                        scope.launch {
                            val out = File(outDir, "${base}_page${currentPage + 1}.pdf")
                            pdfEngine.extractPages(workingPdfFile!!, out, listOf(currentPage))
                                .onSuccess { onMessage("Extracted to ${it.name}") }
                                .onFailure { onMessage("Extract failed: ${it.message}") }
                        }
                        showPageToolsSheet = false
                    }
                    ToolRowButton(Icons.Default.Delete, "Delete Current Page") {
                        scope.launch {
                            val out = File(outDir, "${base}_trimmed_${System.currentTimeMillis()}.pdf")
                            pdfEngine.deletePages(workingPdfFile!!, out, setOf(currentPage))
                                .onSuccess { workingPdfFile = it; refreshTrigger++; onMessage("Page deleted in ${it.name}") }
                                .onFailure { onMessage("${it.message}") }
                        }
                        showPageToolsSheet = false
                    }
                    ToolRowButton(Icons.Default.SwapVert, "Reorder Pages") {
                        showPageToolsSheet = false
                        showReorderDialog = true
                    }
                    ToolRowButton(Icons.Default.Merge, "Merge with Other PDFs...") {
                        showPageToolsSheet = false
                        mergePicker.launch(arrayOf("application/pdf"))
                    }
                    ToolRowButton(Icons.Default.Splitscreen, "Split PDF (1 page per file)") {
                        scope.launch {
                            val splitDir = File(outDir, "${base}_split_${System.currentTimeMillis()}")
                            pdfEngine.splitPdf(workingPdfFile!!, splitDir, 1)
                                .onSuccess { onMessage("Split into ${it.size} PDF files in ${splitDir.name}") }
                                .onFailure { onMessage("Split failed: ${it.message}") }
                        }
                        showPageToolsSheet = false
                    }
                    ToolRowButton(Icons.Default.Compress, "Compress PDF") {
                        scope.launch {
                            val out = File(outDir, "${base}_compressed.pdf")
                            pdfEngine.compressPdf(workingPdfFile!!, out, 65)
                                .onSuccess { onMessage("Compressed PDF saved: ${it.name} (${it.length() / 1024} KB)") }
                                .onFailure { onMessage("Compress failed: ${it.message}") }
                        }
                        showPageToolsSheet = false
                    }
                    ToolRowButton(Icons.Default.Image, "Export All Pages to PNG") {
                        scope.launch {
                            val imgDir = File(outDir, "${base}_pngs_${System.currentTimeMillis()}")
                            pdfEngine.exportPdfToImages(workingPdfFile!!, imgDir, Bitmap.CompressFormat.PNG)
                                .onSuccess { onMessage("Exported ${it.size} PNGs to ${imgDir.name}") }
                                .onFailure { onMessage("Export failed: ${it.message}") }
                        }
                        showPageToolsSheet = false
                    }
                    ToolRowButton(Icons.Default.Lock, "Protect PDF with Password") {
                        showPageToolsSheet = false
                        showPasswordProtectDialog = true
                    }
                    ToolRowButton(Icons.Default.SelectAll, "View PDF Metadata") {
                        showPageToolsSheet = false
                        showMetadataDialog = true
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPageToolsSheet = false }) { Text("Close") }
            }
        )
    }

    if (showPasswordProtectDialog && workingPdfFile != null) {
        AlertDialog(
            onDismissRequest = { showPasswordProtectDialog = false },
            title = { Text("Encrypt PDF with Password", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = protectPwdInput,
                    onValueChange = { protectPwdInput = it },
                    label = { Text("New Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val pwd = protectPwdInput
                    if (pwd.isNotEmpty()) {
                        scope.launch {
                            val outDir = File(context.filesDir, "PDF_Output").apply { mkdirs() }
                            val out = File(outDir, "${fileItem.name.substringBeforeLast('.')}_protected.pdf")
                            pdfEngine.protectPdfWithPassword(workingPdfFile!!, out, pwd)
                                .onSuccess { onMessage("Encrypted PDF saved: ${it.name}") }
                                .onFailure { onMessage("Encryption failed: ${it.message}") }
                        }
                    }
                    protectPwdInput = ""
                    showPasswordProtectDialog = false
                }) { Text("Protect") }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordProtectDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showReorderDialog && workingPdfFile != null) {
        val pageOrder = remember(pageCount) { mutableStateListOf<Int>().apply { addAll(0 until pageCount) } }
        AlertDialog(
            onDismissRequest = { showReorderDialog = false },
            title = { Text("Reorder PDF Pages", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.height(260.dp).verticalScroll(rememberScrollState())) {
                    pageOrder.forEachIndexed { pos, origIdx ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Slot ${pos + 1}: Original Page ${origIdx + 1}", style = MaterialTheme.typography.bodyMedium)
                            Row {
                                TextButton(
                                    onClick = {
                                        if (pos > 0) {
                                            val tmp = pageOrder[pos - 1]
                                            pageOrder[pos - 1] = pageOrder[pos]
                                            pageOrder[pos] = tmp
                                        }
                                    },
                                    enabled = pos > 0
                                ) { Text("Up") }
                                TextButton(
                                    onClick = {
                                        if (pos < pageOrder.lastIndex) {
                                            val tmp = pageOrder[pos + 1]
                                            pageOrder[pos + 1] = pageOrder[pos]
                                            pageOrder[pos] = tmp
                                        }
                                    },
                                    enabled = pos < pageOrder.lastIndex
                                ) { Text("Down") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val outDir = File(context.filesDir, "PDF_Output").apply { mkdirs() }
                        val out = File(outDir, "${fileItem.name.substringBeforeLast('.')}_reordered.pdf")
                        pdfEngine.reorderPages(workingPdfFile!!, out, pageOrder.toList())
                            .onSuccess { workingPdfFile = it; refreshTrigger++; onMessage("Reordered PDF saved: ${it.name}") }
                            .onFailure { onMessage("Reorder failed: ${it.message}") }
                    }
                    showReorderDialog = false
                }) { Text("Save Order") }
            },
            dismissButton = {
                TextButton(onClick = { showReorderDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showMetadataDialog && metadata != null) {
        val m = metadata!!
        AlertDialog(
            onDismissRequest = { showMetadataDialog = false },
            title = { Text("PDF Metadata", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Pages: ${m.pageCount}")
                    Text("Title: ${m.title.ifBlank { "N/A" }}")
                    Text("Author: ${m.author.ifBlank { "N/A" }}")
                    Text("Subject: ${m.subject.ifBlank { "N/A" }}")
                    Text("Creator: ${m.creator.ifBlank { "N/A" }}")
                    Text("Producer: ${m.producer.ifBlank { "N/A" }}")
                    Text("Encrypted: ${if (m.isEncrypted) "Yes" else "No"}")
                }
            },
            confirmButton = { TextButton(onClick = { showMetadataDialog = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun PdfInteractivePageCanvas(
    pdfFile: File,
    pdfEngine: PdfEngine,
    pageIndex: Int,
    refreshTrigger: Int,
    activeTool: PdfAnnotationTool,
    pageAnnotations: List<PdfAnnotation>,
    onAddAnnotation: (PdfAnnotation) -> Unit,
    onRequestTextNote: (NormalizedPoint) -> Unit,
    scale: Float,
    onScaleChange: (Float) -> Unit,
    nightFilter: Boolean = false
) {
    var bitmap by remember(pdfFile, pageIndex, refreshTrigger) { mutableStateOf<Bitmap?>(null) }
    var boxSize by remember { mutableStateOf(IntSize(1, 1)) }
    var draftPoints by remember { mutableStateOf<List<NormalizedPoint>>(emptyList()) }
    var draftRect by remember { mutableStateOf<RectF?>(null) }

    LaunchedEffect(pdfFile, pageIndex, refreshTrigger) {
        bitmap = pdfEngine.renderPageBitmap(pdfFile, pageIndex, 1080)
    }

    val aspectRatio = remember(bitmap) {
        bitmap?.let { it.width.toFloat() / it.height.toFloat().coerceAtLeast(1f) } ?: 0.75f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .background(Color.White, RoundedCornerShape(4.dp))
            .onSizeChanged { boxSize = it }
            .pointerInput(activeTool, pageIndex) {
                if (activeTool == PdfAnnotationTool.TEXT_NOTE) {
                    detectTapGestures { tap ->
                        onRequestTextNote(
                            NormalizedPoint(
                                (tap.x / boxSize.width).coerceIn(0f, 1f),
                                (tap.y / boxSize.height).coerceIn(0f, 1f)
                            )
                        )
                    }
                } else if (activeTool == PdfAnnotationTool.FREEHAND) {
                    detectDragGestures(
                        onDragStart = { start ->
                            draftPoints = listOf(
                                NormalizedPoint(
                                    (start.x / boxSize.width).coerceIn(0f, 1f),
                                    (start.y / boxSize.height).coerceIn(0f, 1f)
                                )
                            )
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            draftPoints = draftPoints + NormalizedPoint(
                                (change.position.x / boxSize.width).coerceIn(0f, 1f),
                                (change.position.y / boxSize.height).coerceIn(0f, 1f)
                            )
                        },
                        onDragEnd = {
                            if (draftPoints.size >= 2) {
                                onAddAnnotation(
                                    PdfAnnotation(
                                        pageIndex = pageIndex,
                                        tool = PdfAnnotationTool.FREEHAND,
                                        colorArgb = AndroidColor.rgb(229, 57, 53),
                                        strokeWidth = 6f,
                                        points = draftPoints
                                    )
                                )
                            }
                            draftPoints = emptyList()
                        }
                    )
                } else if (activeTool in setOf(
                        PdfAnnotationTool.HIGHLIGHT,
                        PdfAnnotationTool.UNDERLINE,
                        PdfAnnotationTool.STRIKETHROUGH
                    )
                ) {
                    var startPt = Offset.Zero
                    detectDragGestures(
                        onDragStart = { start ->
                            startPt = start
                            val nx = (start.x / boxSize.width).coerceIn(0f, 1f)
                            val ny = (start.y / boxSize.height).coerceIn(0f, 1f)
                            draftRect = RectF(nx, ny, nx, ny)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val x1 = (startPt.x / boxSize.width).coerceIn(0f, 1f)
                            val y1 = (startPt.y / boxSize.height).coerceIn(0f, 1f)
                            val x2 = (change.position.x / boxSize.width).coerceIn(0f, 1f)
                            val y2 = (change.position.y / boxSize.height).coerceIn(0f, 1f)
                            draftRect = RectF(minOf(x1, x2), minOf(y1, y2), maxOf(x1, x2), maxOf(y1, y2))
                        },
                        onDragEnd = {
                            draftRect?.let { r ->
                                val col = when (activeTool) {
                                    PdfAnnotationTool.HIGHLIGHT -> AndroidColor.argb(110, 255, 235, 59)
                                    PdfAnnotationTool.UNDERLINE -> AndroidColor.rgb(33, 150, 243)
                                    else -> AndroidColor.rgb(229, 57, 53)
                                }
                                onAddAnnotation(
                                    PdfAnnotation(
                                        pageIndex = pageIndex,
                                        tool = activeTool,
                                        colorArgb = col,
                                        rect = RectF(r.left, r.top, r.right, (r.bottom).coerceAtLeast(r.top + 0.025f))
                                    )
                                )
                            }
                            draftRect = null
                        }
                    )
                }
            }
    ) {
        if (bitmap != null) {
            val invertFilter = remember(nightFilter) {
                if (nightFilter) {
                    ColorFilter.colorMatrix(
                        ColorMatrix(
                            floatArrayOf(
                                -1f, 0f, 0f, 0f, 255f,
                                0f, -1f, 0f, 0f, 255f,
                                0f, 0f, -1f, 0f, 255f,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                    )
                } else null
            }
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "PDF Page ${pageIndex + 1}",
                contentScale = ContentScale.Fit,
                colorFilter = invertFilter,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        // Annotation Overlay Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            fun drawAnn(ann: PdfAnnotation) {
                val c = Color(ann.colorArgb)
                when (ann.tool) {
                    PdfAnnotationTool.FREEHAND -> {
                        if (ann.points.size >= 2) {
                            val p = Path().apply {
                                moveTo(ann.points.first().x * size.width, ann.points.first().y * size.height)
                                ann.points.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) }
                            }
                            drawPath(p, c, style = Stroke(width = ann.strokeWidth))
                        }
                    }
                    PdfAnnotationTool.HIGHLIGHT -> {
                        ann.rect?.let { r ->
                            drawRect(
                                color = Color(0x66FFEB3B),
                                topLeft = Offset(r.left * size.width, r.top * size.height),
                                size = Size((r.right - r.left) * size.width, (r.bottom - r.top) * size.height)
                            )
                        }
                    }
                    PdfAnnotationTool.UNDERLINE -> {
                        ann.rect?.let { r ->
                            val y = r.bottom * size.height
                            drawLine(c, Offset(r.left * size.width, y), Offset(r.right * size.width, y), strokeWidth = 4f)
                        }
                    }
                    PdfAnnotationTool.STRIKETHROUGH -> {
                        ann.rect?.let { r ->
                            val y = ((r.top + r.bottom) / 2f) * size.height
                            drawLine(c, Offset(r.left * size.width, y), Offset(r.right * size.width, y), strokeWidth = 4f)
                        }
                    }
                    PdfAnnotationTool.SIGNATURE -> {
                        val anchor = ann.points.firstOrNull() ?: NormalizedPoint(0.7f, 0.85f)
                        val boxW = size.width * 0.28f
                        val boxH = size.height * 0.10f
                        val ox = anchor.x * size.width - boxW / 2f
                        val oy = anchor.y * size.height - boxH / 2f
                        ann.signaturePoints.forEach { stroke ->
                            if (stroke.size >= 2) {
                                val p = Path().apply {
                                    moveTo(ox + stroke.first().x * boxW, oy + stroke.first().y * boxH)
                                    stroke.drop(1).forEach { lineTo(ox + it.x * boxW, oy + it.y * boxH) }
                                }
                                drawPath(p, Color.Black, style = Stroke(width = 3.5f))
                            }
                        }
                    }
                    else -> Unit
                }
            }

            pageAnnotations.forEach { drawAnn(it) }
            if (draftPoints.size >= 2) {
                val p = Path().apply {
                    moveTo(draftPoints.first().x * size.width, draftPoints.first().y * size.height)
                    draftPoints.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) }
                }
                drawPath(p, Color.Red, style = Stroke(width = 6f))
            }
            draftRect?.let { r ->
                drawRect(
                    color = Color(0x55FFEB3B),
                    topLeft = Offset(r.left * size.width, r.top * size.height),
                    size = Size((r.right - r.left) * size.width, (r.bottom - r.top) * size.height.coerceAtLeast(18f))
                )
            }
        }

        // Render Text Note Markers
        pageAnnotations.filter { it.tool == PdfAnnotationTool.TEXT_NOTE }.forEach { note ->
            val pt = note.points.firstOrNull() ?: NormalizedPoint(0.2f, 0.2f)
            Surface(
                color = Color(0xFFFFF9C4),
                shape = RoundedCornerShape(6.dp),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .padding(
                        start = ((pt.x * 240).toInt().coerceIn(8, 220)).dp,
                        top = ((pt.y * 320).toInt().coerceIn(8, 300)).dp
                    )
            ) {
                Text(
                    text = note.text,
                    color = Color(0xFF1565C0),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun ToolRowButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(label)
        }
    }
}
