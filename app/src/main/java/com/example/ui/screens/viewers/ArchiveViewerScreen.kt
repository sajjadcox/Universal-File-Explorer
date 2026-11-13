package com.example.ui.screens.viewers

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.storage.AdvancedStorageOperations
import com.example.domain.archive.ArchiveEngine
import com.example.domain.archive.ArchiveListingResult
import com.example.domain.model.FileItem
import com.example.domain.model.StorageStats
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ArchiveViewerScreen(
    fileItem: FileItem,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storageOps = remember { AdvancedStorageOperations(context) }
    val archiveEngine = remember { ArchiveEngine(context) }

    var archiveFile by remember { mutableStateOf<File?>(null) }
    var listing by remember { mutableStateOf<ArchiveListingResult?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var isExtracting by remember { mutableStateOf(false) }
    var extractProgress by remember { mutableIntStateOf(0) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    val selectedEntries = remember { mutableStateListOf<String>() }

    // Comic Book (CBZ) Reader State
    var isComicReaderMode by remember { mutableStateOf(fileItem.extension.equals("cbz", true)) }
    var comicPages by remember { mutableStateOf<List<File>>(emptyList()) }
    var comicPageIndex by remember { mutableIntStateOf(0) }

    // Custom extraction directory via SAF
    val extractSafPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        val file = archiveFile ?: return@rememberLauncherForActivityResult
        if (treeUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isExtracting = true
            extractProgress = 0
            val targets = selectedEntries.toSet().takeIf { it.isNotEmpty() }
            val res = archiveEngine.extractToSafDirectory(
                archiveFile = file,
                extension = fileItem.extension,
                destTreeUri = treeUri,
                selectedEntryNames = targets,
                password = passwordInput.ifBlank { null },
                onProgress = { extractProgress = it }
            )
            isExtracting = false
            res.onSuccess { count ->
                onMessage("Extracted $count item(s) to selected folder")
            }.onFailure { err ->
                if (err.message?.contains("password", true) == true) {
                    showPasswordDialog = true
                }
                onMessage("Extraction failed: ${err.message}")
            }
        }
    }

    LaunchedEffect(fileItem.uri) {
        isLoading = true
        errorMsg = null
        val file = storageOps.copyUriToTempCache(fileItem.uri, fileItem.extension.ifBlank { "zip" })
        archiveFile = file
        if (file == null) {
            errorMsg = "Cannot open archive stream."
        } else {
            val res = archiveEngine.listArchive(file, fileItem.extension)
            res.onSuccess {
                listing = it
                if (it.isPasswordProtected) showPasswordDialog = true
            }.onFailure { errorMsg = it.message }

            if (fileItem.extension.equals("cbz", true)) {
                archiveEngine.extractCbzPagesToCache(file).onSuccess { pages ->
                    comicPages = pages
                }
            }
        }
        isLoading = false
    }

    Column(modifier = modifier.fillMaxSize().testTag("archive_viewer_screen")) {
        // Archive Action Bar
        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (listing?.isIntegrityValid == true) Icons.Default.VerifiedUser else Icons.Default.Archive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${listing?.formatLabel ?: fileItem.extension.uppercase()} • ${listing?.entries?.size ?: 0} items",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (fileItem.extension.equals("cbz", true) && comicPages.isNotEmpty()) {
                        OutlinedButton(onClick = { isComicReaderMode = !isComicReaderMode }) {
                            Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isComicReaderMode) "Files" else "Comic")
                        }
                    }
                    if (listing?.entries?.isNotEmpty() == true) {
                        Button(
                            onClick = { extractSafPicker.launch(null) },
                            enabled = !isExtracting
                        ) {
                            Icon(Icons.Default.Unarchive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (selectedEntries.isEmpty()) "Extract All" else "Extract (${selectedEntries.size})")
                        }
                    }
                }
            }
        }

        if (isExtracting) {
            LinearProgressIndicator(
                progress = { extractProgress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (errorMsg != null) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
            }
        } else if (isComicReaderMode && comicPages.isNotEmpty()) {
            // CBZ Comic Book Page Reader
            val pageFile = comicPages[comicPageIndex.coerceIn(0, comicPages.lastIndex)]
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AsyncImage(
                    model = pageFile,
                    contentDescription = "Comic page ${comicPageIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { if (comicPageIndex > 0) comicPageIndex-- }, enabled = comicPageIndex > 0) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Prev")
                    }
                    Text("Page ${comicPageIndex + 1} / ${comicPages.size}", style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { if (comicPageIndex < comicPages.lastIndex) comicPageIndex++ }, enabled = comicPageIndex < comicPages.lastIndex) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next")
                    }
                }
            }
        } else {
            val info = listing!!
            if (info.notes.isNotBlank()) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(info.notes, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                }
            }
            if (info.entries.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(info.notes.ifBlank { "Archive is empty." })
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(info.entries, key = { it.name }) { entry ->
                        val isChecked = selectedEntries.contains(entry.name)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) selectedEntries.remove(entry.name) else selectedEntries.add(entry.name)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        if (isChecked) selectedEntries.remove(entry.name) else selectedEntries.add(entry.name)
                                    }
                                )
                                Icon(
                                    imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                    contentDescription = null,
                                    tint = if (entry.isDirectory) Color(0xFFFFB300) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val sizeTxt = if (entry.isDirectory) "Folder" else StorageStats.formatBytes(entry.size.coerceAtLeast(0L))
                                    Text(sizeTxt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (entry.isEncrypted) {
                                    Icon(Icons.Default.Lock, contentDescription = "Encrypted", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("Archive Password", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Enter password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = { showPasswordDialog = false }) { Text("Set Password") }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) { Text("Cancel") }
            }
        )
    }
}
