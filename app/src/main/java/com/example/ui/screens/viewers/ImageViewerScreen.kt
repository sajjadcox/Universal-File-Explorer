package com.example.ui.screens.viewers

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.storage.AdvancedStorageOperations
import com.example.domain.image.ImageInfoResult
import com.example.domain.image.ImageToolsEngine
import com.example.domain.model.FileItem
import com.example.domain.pdf.PdfEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Timer
import com.example.data.storage.ImageAlbumSummary
import com.example.data.storage.Phase2FileOperations
import androidx.compose.material3.ListItem

@Composable
fun ImageViewerScreen(
    fileItem: FileItem,
    galleryList: List<FileItem>,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storageOps = remember { AdvancedStorageOperations(context) }
    val pdfEngine = remember { PdfEngine(context) }

    var activeAlbumItems by remember(galleryList, fileItem) {
        mutableStateOf(
            if (galleryList.isNotEmpty() && galleryList.any { it.uri == fileItem.uri }) galleryList else listOf(fileItem)
        )
    }
    var currentIndex by remember(fileItem, activeAlbumItems) {
        mutableIntStateOf(activeAlbumItems.indexOfFirst { it.uri == fileItem.uri }.coerceAtLeast(0))
    }
    val items = activeAlbumItems
    val currentItem = items[currentIndex.coerceIn(0, items.lastIndex)]

    var scale by remember(currentIndex) { mutableFloatStateOf(1f) }
    var offset by remember(currentIndex) { mutableStateOf(Offset.Zero) }
    var rotationDeg by remember(currentIndex) { mutableFloatStateOf(0f) }
    var flipH by remember(currentIndex) { mutableStateOf(false) }

    var isSlideshow by remember { mutableStateOf(false) }
    var slideshowIntervalSec by remember { mutableIntStateOf(3) }
    var showExifDialog by remember { mutableStateOf(false) }
    var showEditSaveDialog by remember { mutableStateOf(false) }
    var showAlbumsDialog by remember { mutableStateOf(false) }
    var albums by remember { mutableStateOf<List<ImageAlbumSummary>>(emptyList()) }
    var imageInfo by remember { mutableStateOf<ImageInfoResult?>(null) }

    LaunchedEffect(currentItem.uri) {
        val file = storageOps.copyUriToTempCache(currentItem.uri, currentItem.extension.ifBlank { "jpg" })
        imageInfo = if (file != null) ImageToolsEngine.inspectImage(context, file) else null
    }

    LaunchedEffect(isSlideshow, slideshowIntervalSec, items.size) {
        while (isSlideshow && items.size > 1) {
            delay(slideshowIntervalSec * 1000L)
            currentIndex = (currentIndex + 1) % items.size
        }
    }

    Column(modifier = modifier.fillMaxSize().background(Color.Black).testTag("image_viewer")) {
        // Image Viewport
        val transformState = rememberTransformableState { zoomChange, panChange, _ ->
            scale = (scale * zoomChange).coerceIn(1f, 6f)
            if (scale > 1f) offset += panChange else offset = Offset.Zero
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .transformable(transformState),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(currentItem.uri)
                    .crossfade(true)
                    .build(),
                contentDescription = currentItem.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale * (if (flipH) -1f else 1f),
                        scaleY = scale,
                        rotationZ = rotationDeg,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )

            // Image Info Overlay Pill
            imageInfo?.let { info ->
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    val tiffText = if (info.tiffPageCount > 1) " • ${info.tiffPageCount} TIFF pages" else ""
                    Text(
                        text = "${info.width}×${info.height} • ${currentItem.formattedSize}$tiffText (${currentIndex + 1}/${items.size})",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            if (items.size > 1) {
                IconButton(
                    onClick = { currentIndex = if (currentIndex > 0) currentIndex - 1 else items.lastIndex },
                    modifier = Modifier.align(Alignment.CenterStart).padding(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous", tint = Color.White)
                }
                IconButton(
                    onClick = { currentIndex = (currentIndex + 1) % items.size },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(8.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next", tint = Color.White)
                }
            }
        }

        // Bottom Controls & Image Tools
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    scope.launch {
                        albums = Phase2FileOperations.queryImageAlbums(context)
                        showAlbumsDialog = true
                    }
                }) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Browse Photo Albums")
                }
                IconButton(onClick = { rotationDeg = (rotationDeg + 90f) % 360f }) {
                    Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90°")
                }
                IconButton(onClick = { flipH = !flipH }) {
                    Icon(Icons.Default.Flip, contentDescription = "Flip Horizontal")
                }
                if (items.size > 1) {
                    IconButton(onClick = { isSlideshow = !isSlideshow }) {
                        Icon(
                            if (isSlideshow) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Slideshow"
                        )
                    }
                    FilterChip(
                        selected = isSlideshow,
                        onClick = {
                            slideshowIntervalSec = when (slideshowIntervalSec) {
                                2 -> 3
                                3 -> 5
                                else -> 2
                            }
                        },
                        leadingIcon = { Icon(Icons.Default.Timer, null, modifier = Modifier.width(16.dp)) },
                        label = { Text("${slideshowIntervalSec}s") }
                    )
                }
                IconButton(onClick = { showExifDialog = true }) {
                    Icon(Icons.Default.Camera, contentDescription = "EXIF Metadata")
                }
                IconButton(onClick = { showEditSaveDialog = true }) {
                    Icon(Icons.Default.Tune, contentDescription = "Crop / Resize / Compress / Convert")
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        val outDir = File(context.filesDir, "PDF_Output").apply { mkdirs() }
                        val outPdf = File(outDir, "${currentItem.name.substringBeforeLast('.')}.pdf")
                        pdfEngine.imagesToPdf(listOf(currentItem.uri), outPdf)
                            .onSuccess { onMessage("Saved PDF: ${it.name}") }
                            .onFailure { onMessage("Image to PDF failed: ${it.message}") }
                    }
                }) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("To PDF")
                }
            }
        }
    }

    if (showAlbumsDialog) {
        AlertDialog(
            onDismissRequest = { showAlbumsDialog = false },
            title = { Text("Device Photo Albums", fontWeight = FontWeight.Bold) },
            text = {
                if (albums.isEmpty()) {
                    Text("No accessible MediaStore photo albums found on this device.")
                } else {
                    LazyColumn(modifier = Modifier.height(280.dp)) {
                        items(albums, key = { it.bucketName }) { album ->
                            ListItem(
                                headlineContent = { Text(album.bucketName, fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text("${album.count} photo(s)") },
                                modifier = Modifier.clickable {
                                    activeAlbumItems = album.items
                                    currentIndex = 0
                                    showAlbumsDialog = false
                                    onMessage("Opened album: ${album.bucketName}")
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAlbumsDialog = false }) { Text("Close") } }
        )
    }

    // EXIF Inspector
    if (showExifDialog) {
        AlertDialog(
            onDismissRequest = { showExifDialog = false },
            title = { Text("Image & EXIF Inspector", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.height(280.dp).verticalScroll(rememberScrollState())) {
                    val info = imageInfo
                    if (info == null) {
                        Text("No EXIF/metadata available.")
                    } else {
                        Text("Dimensions: ${info.width} × ${info.height} px", fontWeight = FontWeight.SemiBold)
                        Text("Color Space: ${info.colorSpace}")
                        if (info.tiffPageCount > 1) Text("TIFF Pages: ${info.tiffPageCount}")
                        Spacer(Modifier.height(8.dp))
                        if (info.exifTags.isEmpty()) {
                            Text("No EXIF camera tags embedded.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            info.exifTags.forEach { (k, v) ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(v, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showExifDialog = false }) { Text("Close") } }
        )
    }

    // Crop / Resize / Compress / Convert Dialog
    if (showEditSaveDialog) {
        var quality by remember { mutableFloatStateOf(88f) }
        var targetFormat by remember { mutableStateOf("JPG") }
        var cropPreset by remember { mutableStateOf("Original") }
        var widthStr by remember { mutableStateOf((imageInfo?.width ?: 1080).toString()) }
        var heightStr by remember { mutableStateOf((imageInfo?.height ?: 1080).toString()) }

        AlertDialog(
            onDismissRequest = { showEditSaveDialog = false },
            title = { Text("Edit & Export Image", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Output Format", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("JPG", "PNG", "WEBP").forEach { fmt ->
                            FilterChip(selected = targetFormat == fmt, onClick = { targetFormat = fmt }, label = { Text(fmt) })
                        }
                    }
                    Text("Crop Preset", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Original", "Square 1:1", "Center 80%").forEach { preset ->
                            FilterChip(selected = cropPreset == preset, onClick = { cropPreset = preset }, label = { Text(preset) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = widthStr,
                            onValueChange = { widthStr = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Width") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = heightStr,
                            onValueChange = { heightStr = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Height") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text("Compression Quality: ${quality.toInt()}%", style = MaterialTheme.typography.labelMedium)
                    Slider(value = quality, onValueChange = { quality = it }, valueRange = 15f..100f)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val outDir = File(context.filesDir, "Edited_Images").apply { mkdirs() }
                    val ext = targetFormat.lowercase()
                    val outFile = File(outDir, "${currentItem.name.substringBeforeLast('.')}_edited_${System.currentTimeMillis()}.$ext")
                    val fmt = when (targetFormat) {
                        "PNG" -> Bitmap.CompressFormat.PNG
                        "WEBP" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                            Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                        else -> Bitmap.CompressFormat.JPEG
                    }
                    val cropRect = when (cropPreset) {
                        "Square 1:1" -> RectF(0.125f, 0.125f, 0.875f, 0.875f)
                        "Center 80%" -> RectF(0.1f, 0.1f, 0.9f, 0.9f)
                        else -> null
                    }
                    scope.launch {
                        ImageToolsEngine.transformAndSaveImage(
                            context = context,
                            sourceUri = currentItem.uri,
                            outputFile = outFile,
                            rotateDegrees = rotationDeg,
                            flipHorizontal = flipH,
                            cropRectNorm = cropRect,
                            targetWidth = widthStr.toIntOrNull(),
                            targetHeight = heightStr.toIntOrNull(),
                            format = fmt,
                            quality = quality.toInt()
                        ).onSuccess {
                            onMessage("Saved ${it.name} (${it.length() / 1024} KB)")
                        }.onFailure {
                            onMessage("Save failed: ${it.message}")
                        }
                    }
                    showEditSaveDialog = false
                }) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Save Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditSaveDialog = false }) { Text("Cancel") }
            }
        )
    }
}
