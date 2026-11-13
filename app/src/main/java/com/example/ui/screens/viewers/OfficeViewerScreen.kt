package com.example.ui.screens.viewers

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.TableView
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.storage.AdvancedStorageOperations
import com.example.domain.model.FileItem
import com.example.domain.office.CellData
import com.example.domain.office.EbookAndEmailEngine
import com.example.domain.office.EbookModel
import com.example.domain.office.EmailModel
import com.example.domain.office.OfficeEngine
import com.example.domain.office.PresentationModel
import com.example.domain.office.SpreadsheetModel
import com.example.domain.office.WordBlock
import com.example.domain.office.WordDocumentModel
import kotlinx.coroutines.launch
import java.io.File

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.DisposableEffect
import com.example.domain.model.ReaderFontChoice
import com.example.domain.model.ReaderTheme
import kotlinx.coroutines.delay

@Composable
fun WordDocumentViewer(
    fileItem: FileItem,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialReaderTheme: ReaderTheme = ReaderTheme.LIGHT,
    initialReaderFont: ReaderFontChoice = ReaderFontChoice.SERIF,
    keepAwakeDefault: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storageOps = remember { AdvancedStorageOperations(context) }

    var model by remember { mutableStateOf<WordDocumentModel?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    var readerTheme by remember(initialReaderTheme) { mutableStateOf(initialReaderTheme) }
    var readerFont by remember(initialReaderFont) { mutableStateOf(initialReaderFont) }
    var keepAwake by remember(keepAwakeDefault) { mutableStateOf(keepAwakeDefault) }
    var autoScroll by remember { mutableStateOf(false) }
    var landscapeMode by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val ttsHelper = remember { ReaderTtsHelper(context) }

    DisposableEffect(Unit) {
        onDispose { ttsHelper.shutdown() }
    }
    KeepScreenAwakeEffect(keepAwake)
    ReaderOrientationEffect(landscapeMode)

    LaunchedEffect(autoScroll) {
        while (autoScroll) {
            delay(50)
            listState.scrollBy(2.5f)
        }
    }

    LaunchedEffect(fileItem.uri) {
        isLoading = true
        val file = storageOps.copyUriToTempCache(fileItem.uri, fileItem.extension.ifBlank { "docx" })
        model = if (file != null) OfficeEngine.parseWordDocument(file, fileItem.extension) else null
        isLoading = false
    }

    val (surfaceBg, surfaceFg) = readerSurfaceColors(
        readerTheme,
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.onSurface
    )

    Column(modifier = modifier.fillMaxSize().background(surfaceBg).testTag("word_viewer")) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = model?.formatLabel ?: "Document",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    OutlinedButton(
                        onClick = {
                            val doc = model ?: return@OutlinedButton
                            scope.launch {
                                val outDir = File(context.filesDir, "Converted").apply { mkdirs() }
                                val outFile = File(outDir, "${fileItem.name.substringBeforeLast('.')}.pdf")
                                OfficeEngine.exportLinesToPdf(doc.title, doc.fullPlainText.lines(), outFile)
                                    .onSuccess { onMessage("Exported PDF: ${it.name}") }
                                    .onFailure { onMessage("Export failed: ${it.message}") }
                            }
                        }
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export PDF", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        ReaderEnhancementBar(
            readerTheme = readerTheme,
            onCycleTheme = {
                val vals = ReaderTheme.entries
                readerTheme = vals[(vals.indexOf(readerTheme) + 1) % vals.size]
            },
            readerFont = readerFont,
            onCycleFont = {
                val vals = ReaderFontChoice.entries
                readerFont = vals[(vals.indexOf(readerFont) + 1) % vals.size]
            },
            autoScrollEnabled = autoScroll,
            onToggleAutoScroll = { autoScroll = !autoScroll },
            keepAwake = keepAwake,
            onToggleKeepAwake = { keepAwake = !keepAwake },
            landscapeMode = landscapeMode,
            onToggleLandscape = { landscapeMode = !landscapeMode },
            isSpeaking = isSpeaking,
            onToggleSpeak = {
                val txt = model?.fullPlainText ?: ""
                if (isSpeaking) {
                    ttsHelper.stop()
                    isSpeaking = false
                } else if (ttsHelper.speak(txt)) {
                    isSpeaking = true
                } else {
                    onMessage("Text-to-Speech unavailable or empty document")
                }
            }
        )

        if (showSearch) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search document...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (model == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Unable to parse document.", color = MaterialTheme.colorScheme.error)
            }
        } else {
            val doc = model!!
            if (doc.notes.isNotBlank()) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(doc.notes, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(10.dp))
                }
            }
            val activeFont = readerFont.toComposeFontFamily()
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val filtered = if (searchQuery.isBlank()) doc.blocks else doc.blocks.filter { block ->
                        when (block) {
                            is WordBlock.Paragraph -> block.text.contains(searchQuery, ignoreCase = true)
                            is WordBlock.Table -> block.rows.flatten().any { it.contains(searchQuery, ignoreCase = true) }
                            is WordBlock.EmbeddedImage -> false
                        }
                    }
                    items(filtered) { block ->
                        when (block) {
                            is WordBlock.Paragraph -> {
                                val style = when (block.headingLevel) {
                                    1 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontFamily = activeFont)
                                    2 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontFamily = activeFont)
                                    3 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontFamily = activeFont)
                                    else -> MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (block.isBold) FontWeight.Bold else FontWeight.Normal,
                                        fontFamily = activeFont
                                    )
                                }
                                Text(text = block.text, style = style, color = surfaceFg)
                            }
                            is WordBlock.Table -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                ) {
                                    block.rows.forEachIndexed { rIdx, row ->
                                        Row(
                                            modifier = Modifier.background(
                                                if (rIdx == 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                                else Color.Transparent
                                            )
                                        ) {
                                            row.forEach { cell ->
                                                Text(
                                                    text = cell,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontFamily = activeFont,
                                                    color = surfaceFg,
                                                    fontWeight = if (rIdx == 0) FontWeight.Bold else FontWeight.Normal,
                                                    modifier = Modifier
                                                        .width(120.dp)
                                                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                                        .padding(8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            is WordBlock.EmbeddedImage -> {
                                val bmp = remember(block.name) {
                                    BitmapFactory.decodeByteArray(block.bytes, 0, block.bytes.size)
                                }
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = block.name,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(10.dp))
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

@Composable
fun SpreadsheetViewer(
    fileItem: FileItem,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storageOps = remember { AdvancedStorageOperations(context) }

    var workbook by remember { mutableStateOf<SpreadsheetModel?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var activeSheetIndex by remember { mutableIntStateOf(0) }
    var filterQuery by remember { mutableStateOf("") }
    var sortAsc by remember { mutableStateOf<Boolean?>(null) }
    var selectedCell by remember { mutableStateOf<CellData?>(null) }

    LaunchedEffect(fileItem.uri) {
        isLoading = true
        val file = storageOps.copyUriToTempCache(fileItem.uri, fileItem.extension.ifBlank { "xlsx" })
        workbook = if (file != null) OfficeEngine.parseSpreadsheet(file, fileItem.extension) else null
        isLoading = false
    }

    Column(modifier = modifier.fillMaxSize().testTag("spreadsheet_viewer")) {
        val currentSheet = workbook?.sheets?.getOrNull(activeSheetIndex)

        // Action Bar
        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = {
                    sortAsc = when (sortAsc) {
                        null -> true
                        true -> false
                        false -> null
                    }
                }) {
                    Icon(Icons.Default.SortByAlpha, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(when (sortAsc) { true -> "Sort A-Z"; false -> "Sort Z-A"; null -> "Unsorted" })
                }
                OutlinedButton(onClick = {
                    val sheet = currentSheet ?: return@OutlinedButton
                    val outDir = File(context.filesDir, "Converted").apply { mkdirs() }
                    val out = File(outDir, "${fileItem.name.substringBeforeLast('.')}_${sheet.name}.csv")
                    OfficeEngine.exportSheetToCsv(sheet, out)
                        .onSuccess { onMessage("Exported CSV: ${it.name}") }
                        .onFailure { onMessage("CSV export failed: ${it.message}") }
                }) {
                    Icon(Icons.Default.TableView, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Export CSV")
                }
                OutlinedButton(onClick = {
                    val sheet = currentSheet ?: return@OutlinedButton
                    scope.launch {
                        val outDir = File(context.filesDir, "Converted").apply { mkdirs() }
                        val out = File(outDir, "${fileItem.name.substringBeforeLast('.')}.pdf")
                        val lines = sheet.rows.map { r -> r.joinToString(" | ") { it.value } }
                        OfficeEngine.exportLinesToPdf("${fileItem.name} - ${sheet.name}", lines, out)
                            .onSuccess { onMessage("Exported PDF: ${it.name}") }
                            .onFailure { onMessage("PDF export failed: ${it.message}") }
                    }
                }) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Export PDF")
                }
            }
        }

        // Filter & Formula Bar
        OutlinedTextField(
            value = filterQuery,
            onValueChange = { filterQuery = it },
            placeholder = { Text("Filter rows...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
        )

        selectedCell?.let { cell ->
            Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (cell.formula != null) "Formula: ${cell.formula}   |   Value: ${cell.value}"
                    else "Cell Value: ${cell.value}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Sheet Tabs
        if ((workbook?.sheets?.size ?: 0) > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                workbook!!.sheets.forEachIndexed { idx, s ->
                    FilterChip(
                        selected = idx == activeSheetIndex,
                        onClick = { activeSheetIndex = idx },
                        label = { Text(s.name) }
                    )
                }
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (currentSheet == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No spreadsheet data available.")
            }
        } else {
            val baseRows = if (filterQuery.isBlank()) currentSheet.rows else currentSheet.rows.filter { row ->
                row.any { it.value.contains(filterQuery, ignoreCase = true) }
            }
            val displayRows = when (sortAsc) {
                true -> if (baseRows.size > 1) listOf(baseRows.first()) + baseRows.drop(1).sortedBy { it.firstOrNull()?.value?.lowercase() ?: "" } else baseRows
                false -> if (baseRows.size > 1) listOf(baseRows.first()) + baseRows.drop(1).sortedByDescending { it.firstOrNull()?.value?.lowercase() ?: "" } else baseRows
                null -> baseRows
            }

            val hScroll = rememberScrollState()
            LazyColumn(
                modifier = Modifier.fillMaxSize().horizontalScroll(hScroll).padding(8.dp)
            ) {
                itemsIndexed(displayRows) { rIdx, row ->
                    Row(
                        modifier = Modifier.background(
                            if (rIdx == 0) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else if (rIdx % 2 == 0) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .width(44.dp)
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                .padding(8.dp)
                        ) {
                            Text("${rIdx + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        row.forEach { cell ->
                            Box(
                                modifier = Modifier
                                    .width(120.dp)
                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                    .clickable { selectedCell = cell }
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = cell.value.ifBlank { " " },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (rIdx == 0) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PresentationViewer(
    fileItem: FileItem,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storageOps = remember { AdvancedStorageOperations(context) }

    var presentation by remember { mutableStateOf<PresentationModel?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var currentSlideIdx by remember { mutableIntStateOf(0) }
    var isFullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(fileItem.uri) {
        isLoading = true
        val file = storageOps.copyUriToTempCache(fileItem.uri, fileItem.extension.ifBlank { "pptx" })
        presentation = if (file != null) OfficeEngine.parsePresentation(file, fileItem.extension) else null
        isLoading = false
    }

    Column(modifier = modifier.fillMaxSize().testTag("presentation_viewer")) {
        if (!isFullscreen) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${presentation?.slides?.size ?: 0} Slides",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        IconButton(onClick = { isFullscreen = true }) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Present Fullscreen")
                        }
                        OutlinedButton(onClick = {
                            val pres = presentation ?: return@OutlinedButton
                            scope.launch {
                                val outDir = File(context.filesDir, "Converted").apply { mkdirs() }
                                val out = File(outDir, "${fileItem.name.substringBeforeLast('.')}.pdf")
                                val lines = pres.slides.flatMap { s ->
                                    listOf("--- Slide ${s.index}: ${s.title} ---") + s.bodyLines + listOf("")
                                }
                                OfficeEngine.exportLinesToPdf(pres.title, lines, out)
                                    .onSuccess { onMessage("Exported PDF: ${it.name}") }
                                    .onFailure { onMessage("Export failed: ${it.message}") }
                            }
                        }) {
                            Text("Export PDF")
                        }
                    }
                }
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (presentation == null || presentation!!.slides.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(presentation?.notes?.ifBlank { "No slides found in deck." } ?: "Unable to open presentation.")
            }
        } else {
            val slides = presentation!!.slides
            val activeSlide = slides[currentSlideIdx.coerceIn(0, slides.lastIndex)]

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(if (isFullscreen) Color.Black else MaterialTheme.colorScheme.background)
                    .clickable(enabled = isFullscreen) {
                        if (currentSlideIdx < slides.lastIndex) currentSlideIdx++ else isFullscreen = false
                    }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "SLIDE ${activeSlide.index}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = activeSlide.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        HorizontalDivider()
                        activeSlide.bodyLines.forEach { bullet ->
                            Text("• $bullet", style = MaterialTheme.typography.bodyLarge)
                        }
                        activeSlide.images.forEach { bytes ->
                            val bmp = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Slide Graphic",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth().height(180.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (!isFullscreen) {
                // Slide Thumbnail Strip
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(slides) { idx, s ->
                        Surface(
                            modifier = Modifier
                                .width(110.dp)
                                .height(68.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = if (idx == currentSlideIdx) 2.dp else 1.dp,
                                    color = if (idx == currentSlideIdx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { currentSlideIdx = idx },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("${s.index}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text(s.title, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color.Black).padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { if (currentSlideIdx > 0) currentSlideIdx-- }) { Text("Prev") }
                    TextButton(onClick = { isFullscreen = false }) { Text("Exit Fullscreen") }
                    TextButton(onClick = { if (currentSlideIdx < slides.lastIndex) currentSlideIdx++ }) { Text("Next") }
                }
            }
        }
    }
}

@Composable
fun EbookViewer(
    fileItem: FileItem,
    initialChapter: Int = 0,
    onChapterChanged: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    initialReaderTheme: ReaderTheme = ReaderTheme.LIGHT,
    initialReaderFont: ReaderFontChoice = ReaderFontChoice.SERIF,
    keepAwakeDefault: Boolean = false
) {
    val context = LocalContext.current
    val storageOps = remember { AdvancedStorageOperations(context) }

    var ebook by remember { mutableStateOf<EbookModel?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var chapterIndex by remember { mutableIntStateOf(initialChapter.coerceAtLeast(0)) }
    var fontSizeSp by remember { mutableIntStateOf(16) }

    var readerTheme by remember(initialReaderTheme) { mutableStateOf(initialReaderTheme) }
    var readerFont by remember(initialReaderFont) { mutableStateOf(initialReaderFont) }
    var keepAwake by remember(keepAwakeDefault) { mutableStateOf(keepAwakeDefault) }
    var autoScroll by remember { mutableStateOf(false) }
    var landscapeMode by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var showTocDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val ttsHelper = remember { ReaderTtsHelper(context) }

    DisposableEffect(Unit) {
        onDispose { ttsHelper.shutdown() }
    }
    KeepScreenAwakeEffect(keepAwake)
    ReaderOrientationEffect(landscapeMode)

    LaunchedEffect(autoScroll, chapterIndex) {
        while (autoScroll) {
            delay(50)
            scrollState.scrollBy(2.0f)
        }
    }

    LaunchedEffect(fileItem.uri) {
        isLoading = true
        val file = storageOps.copyUriToTempCache(fileItem.uri, fileItem.extension.ifBlank { "epub" })
        ebook = if (file != null) EbookAndEmailEngine.parseEbook(file, fileItem.extension) else null
        ebook?.let {
            if (it.chapters.isNotEmpty()) {
                val safe = chapterIndex.coerceIn(0, it.chapters.lastIndex)
                chapterIndex = safe
                onChapterChanged(safe, it.chapters.size)
            }
        }
        isLoading = false
    }

    val (surfaceBg, surfaceFg) = readerSurfaceColors(
        readerTheme,
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.onSurface
    )

    Column(modifier = modifier.fillMaxSize().background(surfaceBg).testTag("ebook_viewer")) {
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (ebook == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Unable to open eBook.") }
        } else {
            val book = ebook!!
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(book.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${book.author} • ${book.formatLabel}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (book.chapters.size > 1) {
                            IconButton(onClick = { showTocDialog = true }) {
                                Icon(Icons.Default.List, contentDescription = "Table of Contents")
                            }
                        }
                        IconButton(onClick = { if (fontSizeSp > 12) fontSizeSp -= 2 }) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease font size")
                        }
                        Text("${fontSizeSp}sp", style = MaterialTheme.typography.labelMedium)
                        IconButton(onClick = { if (fontSizeSp < 28) fontSizeSp += 2 }) {
                            Icon(Icons.Default.Add, contentDescription = "Increase font size")
                        }
                    }
                }
            }

            ReaderEnhancementBar(
                readerTheme = readerTheme,
                onCycleTheme = {
                    val vals = ReaderTheme.entries
                    readerTheme = vals[(vals.indexOf(readerTheme) + 1) % vals.size]
                },
                readerFont = readerFont,
                onCycleFont = {
                    val vals = ReaderFontChoice.entries
                    readerFont = vals[(vals.indexOf(readerFont) + 1) % vals.size]
                },
                autoScrollEnabled = autoScroll,
                onToggleAutoScroll = { autoScroll = !autoScroll },
                keepAwake = keepAwake,
                onToggleKeepAwake = { keepAwake = !keepAwake },
                landscapeMode = landscapeMode,
                onToggleLandscape = { landscapeMode = !landscapeMode },
                isSpeaking = isSpeaking,
                onToggleSpeak = {
                    val ch = book.chapters.getOrNull(chapterIndex)
                    if (isSpeaking) {
                        ttsHelper.stop()
                        isSpeaking = false
                    } else if (ch != null && ttsHelper.speak("${ch.title}. ${ch.content}")) {
                        isSpeaking = true
                    }
                }
            )

            if (book.isDrmProtected || book.chapters.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(book.notes.ifBlank { "No readable chapters found." }, color = surfaceFg)
                }
            } else {
                val chapter = book.chapters[chapterIndex.coerceIn(0, book.chapters.lastIndex)]
                val activeFont = readerFont.toComposeFontFamily()
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(20.dp)
                    ) {
                        Text(
                            chapter.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = activeFont,
                            color = surfaceFg
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = chapter.content,
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.55f).sp,
                            fontFamily = activeFont,
                            color = surfaceFg
                        )
                    }
                }
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (chapterIndex > 0) {
                                    ttsHelper.stop(); isSpeaking = false
                                    chapterIndex--
                                    onChapterChanged(chapterIndex, book.chapters.size)
                                }
                            },
                            enabled = chapterIndex > 0
                        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous chapter") }
                        Text("Chapter ${chapterIndex + 1} / ${book.chapters.size}", style = MaterialTheme.typography.labelLarge)
                        IconButton(
                            onClick = {
                                if (chapterIndex < book.chapters.lastIndex) {
                                    ttsHelper.stop(); isSpeaking = false
                                    chapterIndex++
                                    onChapterChanged(chapterIndex, book.chapters.size)
                                }
                            },
                            enabled = chapterIndex < book.chapters.lastIndex
                        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next chapter") }
                    }
                }
            }

            if (showTocDialog) {
                AlertDialog(
                    onDismissRequest = { showTocDialog = false },
                    title = { Text("Table of Contents (${book.chapters.size})", fontWeight = FontWeight.Bold) },
                    text = {
                        LazyColumn(modifier = Modifier.height(300.dp)) {
                            itemsIndexed(book.chapters) { idx, ch ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            ttsHelper.stop(); isSpeaking = false
                                            chapterIndex = idx
                                            onChapterChanged(idx, book.chapters.size)
                                            showTocDialog = false
                                        }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${idx + 1}. ${ch.title}",
                                        fontWeight = if (idx == chapterIndex) FontWeight.Bold else FontWeight.Normal,
                                        color = if (idx == chapterIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showTocDialog = false }) { Text("Close") }
                    }
                )
            }
        }
    }
}

@Composable
fun EmailViewer(
    fileItem: FileItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val storageOps = remember { AdvancedStorageOperations(context) }

    var email by remember { mutableStateOf<EmailModel?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(fileItem.uri) {
        isLoading = true
        val file = storageOps.copyUriToTempCache(fileItem.uri, fileItem.extension.ifBlank { "eml" })
        email = if (file != null) EbookAndEmailEngine.parseEmail(file, fileItem.extension) else null
        isLoading = false
    }

    if (isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else if (email == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Unable to parse email.") }
    } else {
        val m = email!!
        SelectionContainer(modifier = modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(m.subject, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("From: ${m.from}", style = MaterialTheme.typography.bodySmall)
                        Text("To: ${m.to}", style = MaterialTheme.typography.bodySmall)
                        if (m.date.isNotBlank()) Text("Date: ${m.date}", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (m.attachments.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        m.attachments.forEach { att ->
                            FilterChip(
                                selected = false,
                                onClick = {},
                                label = { Text(att) },
                                leadingIcon = { Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                }
                HorizontalDivider()
                Text(m.bodyText, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
