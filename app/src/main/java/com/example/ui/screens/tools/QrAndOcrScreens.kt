package com.example.ui.screens.tools

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.storage.AdvancedStorageOperations
import com.example.domain.pdf.PdfEngine
import com.example.domain.tools.DiffOp
import com.example.domain.tools.ProductivityEngine
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.Executors

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerAndGeneratorScreen(
    onMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(enabled = true) { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Live Camera", "From Image", "Generate QR")

    var scannedValues by remember { mutableStateOf<List<String>>(emptyList()) }
    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPerm = granted }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                ProductivityEngine.scanBarcodesFromImageUri(context, uri).fold(
                    onSuccess = { vals ->
                        scannedValues = vals
                        if (vals.isEmpty()) onMessage("No QR or barcodes detected in image")
                    },
                    onFailure = { onMessage("Scan failed: ${it.message}") }
                )
            }
        }
    }

    var qrInputText by remember { mutableStateOf("https://") }
    var generatedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QR & Barcode Studio", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("qr_studio_screen")
        ) {
            SecondaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { idx, label ->
                    Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }, text = { Text(label) })
                }
            }

            when (selectedTab) {
                0 -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!hasCameraPerm) {
                            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(40.dp))
                                    Text("Camera permission is needed to scan live QR & barcodes.")
                                    Button(onClick = { cameraPermLauncher.launch(Manifest.permission.CAMERA) }) {
                                        Text("Grant Camera Permission")
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                CameraBarcodePreview(
                                    onBarcodesDetected = { codes ->
                                        if (codes.isNotEmpty() && codes != scannedValues) {
                                            scannedValues = codes
                                        }
                                    }
                                )
                            }
                        }
                        ScannedResultsCard(scannedValues, context)
                    }
                }

                1 -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Image, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Pick Image from Gallery (Photo Picker)")
                        }
                        ScannedResultsCard(scannedValues, context)
                    }
                }

                2 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = qrInputText,
                            onValueChange = { qrInputText = it },
                            label = { Text("Text, URL, or Wi-Fi payload") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                ProductivityEngine.generateQrCodeBitmap(qrInputText).fold(
                                    onSuccess = { generatedBitmap = it },
                                    onFailure = { onMessage("QR error: ${it.message}") }
                                )
                            },
                            enabled = qrInputText.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.QrCode2, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Generate QR Code")
                        }
                        generatedBitmap?.let { bmp ->
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Generated QR Code",
                                    modifier = Modifier.size(220.dp)
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        val dir = File(context.filesDir, "QR_Codes").apply { mkdirs() }
                                        val out = File(dir, "qr_${System.currentTimeMillis()}.png")
                                        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                        withContext(Dispatchers.Main) {
                                            onMessage("Saved to App Storage/QR_Codes/${out.name}")
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Save, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Save QR PNG to App Storage")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraBarcodePreview(onBarcodesDetected: (List<String>) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }

    DisposableEffect(Unit) {
        onDispose {
            analyzerExecutor.shutdown()
            scanner.close()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val provider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val img = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        scanner.process(img)
                            .addOnSuccessListener { barcodes ->
                                val values = barcodes.mapNotNull { it.rawValue }
                                if (values.isNotEmpty()) onBarcodesDetected(values)
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    } else {
                        imageProxy.close()
                    }
                }
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (_: Exception) {
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun ScannedResultsCard(values: List<String>, context: Context) {
    if (values.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Detected (${values.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            values.forEach { v ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SelectionContainer(modifier = Modifier.weight(1f)) {
                        Text(v, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                    }
                    IconButton(onClick = {
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cb.setPrimaryClip(ClipData.newPlainText("Scanned Code", v))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, "Copy")
                    }
                }
            }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrAndTextToolsScreen(
    initialSection: Int = 0,
    onMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(enabled = true) { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storageOps = remember { AdvancedStorageOperations(context) }
    val pdfEngine = remember { PdfEngine(context) }

    var selectedTab by remember { mutableIntStateOf(initialSection) }
    val tabs = listOf("OCR & PDF", "File Diff", "Encode & Count", "Split & Merge")

    // Tab 0: OCR & PDF
    var extractedText by remember { mutableStateOf("") }
    var isProcessingOcr by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isProcessingOcr = true
                ProductivityEngine.recognizeTextFromImageUri(context, uri).fold(
                    onSuccess = { extractedText = it.ifBlank { "No text recognized in image." } },
                    onFailure = { onMessage("OCR error: ${it.message}") }
                )
                isProcessingOcr = false
            }
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isProcessingOcr = true
                val file = storageOps.copyUriToTempCache(uri, "pdf")
                if (file != null) {
                    extractedText = pdfEngine.extractAllText(file).ifBlank { "No extractable text layer found in PDF (try image OCR on rendered page)." }
                } else {
                    onMessage("Unable to read PDF")
                }
                isProcessingOcr = false
            }
        }
    }

    // Tab 1: Diff
    var leftText by remember { mutableStateOf("") }
    var rightText by remember { mutableStateOf("") }
    val pickLeftLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) leftText = storageOps.readTextPreview(uri, 128 * 1024)
    }
    val pickRightLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) rightText = storageOps.readTextPreview(uri, 128 * 1024)
    }
    var diffLines by remember { mutableStateOf<List<com.example.domain.tools.DiffLine>>(emptyList()) }

    // Tab 2: Encoder & Word Counter
    var utilText by remember { mutableStateOf("") }
    var utilOutput by remember { mutableStateOf("") }

    // Tab 3: Split & Merge
    var splitSourceUri by remember { mutableStateOf<Uri?>(null) }
    var splitSourceName by remember { mutableStateOf("") }
    var chunkSizeBytes by remember { mutableLongStateOf(1024L * 1024L) }
    var splitMergeResult by remember { mutableStateOf("") }

    val pickSplitSource = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            splitSourceUri = uri
            splitSourceName = resolveFileName(context, uri)
        }
    }
    val pickMergeParts = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val files = uris.mapIndexedNotNull { idx, u ->
                    storageOps.copyUriToTempCache(u, "part$idx")
                }
                val outDir = File(context.filesDir, "Merged").apply { mkdirs() }
                val outFile = File(outDir, "merged_${System.currentTimeMillis()}.bin")
                ProductivityEngine.mergeFiles(files, outFile).fold(
                    onSuccess = { splitMergeResult = "Merged ${files.size} parts -> App Storage/Merged/${it.name}" },
                    onFailure = { splitMergeResult = "Merge failed: ${it.message}" }
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OCR, Diff & Text Tools", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("ocr_text_tools_screen")
        ) {
            SecondaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { idx, label ->
                    Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }, text = { Text(label, maxLines = 1) })
                }
            }

            when (selectedTab) {
                0 -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.DocumentScanner, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("OCR Image")
                            }
                            OutlinedButton(
                                onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("PDF Text")
                            }
                        }
                        if (isProcessingOcr) {
                            CircularProgressIndicator()
                        }
                        if (extractedText.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                OutlinedButton(onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cb.setPrimaryClip(ClipData.newPlainText("Extracted Text", extractedText))
                                    onMessage("Copied extracted text")
                                }) {
                                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Copy All")
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = extractedText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.verticalScroll(rememberScrollState()).padding(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                1 -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { pickLeftLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                                Text("Load Left File")
                            }
                            OutlinedButton(onClick = { pickRightLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                                Text("Load Right File")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = leftText,
                                onValueChange = { leftText = it },
                                label = { Text("Original") },
                                maxLines = 4,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = rightText,
                                onValueChange = { rightText = it },
                                label = { Text("Modified") },
                                maxLines = 4,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Button(
                            onClick = { diffLines = ProductivityEngine.computeLineDiff(leftText, rightText) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CompareArrows, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Compare Diff")
                        }
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(diffLines) { dl ->
                                val bg = when (dl.op) {
                                    DiffOp.ADDED -> Color(0x284CAF50)
                                    DiffOp.REMOVED -> Color(0x28F44336)
                                    DiffOp.EQUAL -> Color.Transparent
                                }
                                val prefix = when (dl.op) {
                                    DiffOp.ADDED -> "+"
                                    DiffOp.REMOVED -> "-"
                                    DiffOp.EQUAL -> " "
                                }
                                Text(
                                    text = "$prefix ${dl.text}",
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.fillMaxWidth().background(bg).padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                2 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = utilText,
                            onValueChange = { utilText = it },
                            label = { Text("Input text") },
                            minLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val wordCount = remember(utilText) {
                            if (utilText.isBlank()) 0 else utilText.trim().split(Regex("\\s+")).size
                        }
                        val lineCount = remember(utilText) { if (utilText.isEmpty()) 0 else utilText.lines().size }
                        Text(
                            text = "Words: $wordCount • Characters: ${utilText.length} • Lines: $lineCount • UTF-8 Bytes: ${utilText.toByteArray().size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { utilOutput = Base64.encodeToString(utilText.toByteArray(), Base64.NO_WRAP) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Base64 Enc") }
                            OutlinedButton(
                                onClick = {
                                    utilOutput = try {
                                        String(Base64.decode(utilText.trim(), Base64.DEFAULT))
                                    } catch (e: Exception) { "Invalid Base64: ${e.message}" }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Base64 Dec") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { utilOutput = URLEncoder.encode(utilText, "UTF-8") },
                                modifier = Modifier.weight(1f)
                            ) { Text("URL Enc") }
                            OutlinedButton(
                                onClick = {
                                    utilOutput = try {
                                        URLDecoder.decode(utilText, "UTF-8")
                                    } catch (e: Exception) { "Invalid URL: ${e.message}" }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("URL Dec") }
                        }
                        if (utilOutput.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = utilOutput,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                3 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(shape = RoundedCornerShape(16.dp)) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Split File into Parts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                OutlinedButton(onClick = { pickSplitSource.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (splitSourceName.isBlank()) "Pick File to Split..." else splitSourceName)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(100 * 1024L to "100 KB", 1024L * 1024L to "1 MB", 5L * 1024 * 1024 to "5 MB").forEach { (sz, lbl) ->
                                        FilterChip(
                                            selected = chunkSizeBytes == sz,
                                            onClick = { chunkSizeBytes = sz },
                                            label = { Text(lbl) }
                                        )
                                    }
                                }
                                Button(
                                    onClick = {
                                        val src = splitSourceUri ?: return@Button
                                        scope.launch {
                                            val f = storageOps.copyUriToTempCache(src, splitSourceName.substringAfterLast('.', "bin"))
                                            if (f != null) {
                                                val outDir = File(context.filesDir, "SplitParts").apply { mkdirs() }
                                                ProductivityEngine.splitFile(f, outDir, chunkSizeBytes).fold(
                                                    onSuccess = { parts ->
                                                        splitMergeResult = "Created ${parts.size} part(s) in App Storage/SplitParts"
                                                    },
                                                    onFailure = { splitMergeResult = "Split failed: ${it.message}" }
                                                )
                                            }
                                        }
                                    },
                                    enabled = splitSourceUri != null,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Split File") }
                            }
                        }

                        Card(shape = RoundedCornerShape(16.dp)) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Merge File Parts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                OutlinedButton(onClick = { pickMergeParts.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Select Part Files in Order (.part001, .part002...)")
                                }
                            }
                        }

                        if (splitMergeResult.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(splitMergeResult, modifier = Modifier.padding(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
