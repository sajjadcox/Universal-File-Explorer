package com.example.ui.screens.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.FileItem
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.BrandBluePrimaryLight
import com.example.viewmodel.FileManagerUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

enum class ToolRoute {
    STORAGE_ANALYZER,
    FILE_CONVERTER,
    QR_STUDIO,
    OCR_TEXT_TOOLS,
    DIFF_TOOLS,
    SPLIT_MERGE,
    BATCH_TOOLS,
    INSTALLED_APPS,
    PRIVATE_VAULT,
    ADVANCED_MANAGEMENT
}

@Composable
fun ToolsScreen(
    uiState: FileManagerUiState,
    onOpenTool: (ToolRoute) -> Unit,
    onOpenFileInViewer: (FileItem) -> Unit,
    onSmartAnalyzeFile: (FileItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    var showHashDialog by remember { mutableStateOf(false) }

    val openSmartInspectPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = resolveFileName(context, uri)
            onSmartAnalyzeFile(
                FileItem(
                    uri = uri,
                    name = name,
                    path = uri.toString(),
                    extension = name.substringAfterLast('.', ""),
                    mimeType = context.contentResolver.getType(uri) ?: "*/*"
                )
            )
        }
    }

    val openAnyFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = resolveFileName(context, uri)
            onOpenFileInViewer(
                FileItem(
                    uri = uri,
                    name = name,
                    path = uri.toString(),
                    extension = name.substringAfterLast('.', ""),
                    mimeType = context.contentResolver.getType(uri) ?: "*/*"
                )
            )
        }
    }

    val openPdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = resolveFileName(context, uri)
            onOpenFileInViewer(
                FileItem(
                    uri = uri,
                    name = name,
                    path = uri.toString(),
                    extension = "pdf",
                    mimeType = "application/pdf"
                )
            )
        }
    }

    val openImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val name = resolveFileName(context, uri)
            onOpenFileInViewer(
                FileItem(
                    uri = uri,
                    name = name,
                    path = uri.toString(),
                    extension = name.substringAfterLast('.', "jpg"),
                    mimeType = context.contentResolver.getType(uri) ?: "image/*"
                )
            )
        }
    }

    val toolsList = remember(uiState.trashCount, strings) {
        listOf(
            ToolItem(
                id = "private_vault",
                title = strings.privateVaultTitle,
                description = strings.privateVaultDesc,
                icon = Icons.Default.EnhancedEncryption,
                badge = "Vault"
            ),
            ToolItem(
                id = "smart_assistant",
                title = strings.smartAssistantTitle,
                description = strings.smartAssistantDesc,
                icon = Icons.Default.AutoAwesome,
                badge = "Smart"
            ),
            ToolItem(
                id = "advanced_management",
                title = strings.managementHubTitle,
                description = strings.managementHubDesc,
                icon = Icons.Default.FolderCopy,
                badge = "Suite"
            ),
            ToolItem(
                id = "storage_analyzer",
                title = "Storage Analyzer & Trash",
                description = "Large files, duplicate finder, empty folders, breakdown & App Trash (${uiState.trashCount})",
                icon = Icons.Default.PieChart,
                badge = "Active"
            ),
            ToolItem(
                id = "file_converter",
                title = "Universal File Converter",
                description = "Background queue for PDF, Office, Image, CSV/JSON, M4A & PCM WAV conversions",
                icon = Icons.Default.Transform,
                badge = "Active"
            ),
            ToolItem(
                id = "pdf_tools",
                title = "PDF Reader & Editor",
                description = "Pick a PDF to annotate, sign, rotate, extract, merge, split, compress or export",
                icon = Icons.Default.PictureAsPdf,
                badge = "Viewer"
            ),
            ToolItem(
                id = "image_tools",
                title = "Image Studio & EXIF",
                description = "Pick a photo to inspect EXIF metadata, rotate, flip, crop, compress or convert to PDF",
                icon = Icons.Default.Image,
                badge = "Viewer"
            ),
            ToolItem(
                id = "audio_tools",
                title = "Audio & Video Players",
                description = "Open audio (Media3 background, sleep timer, M4A trim) or video (subtitles, PiP, frame capture)",
                icon = Icons.Default.Audiotrack,
                badge = "Viewer"
            ),
            ToolItem(
                id = "archive_manager",
                title = "Archive & CBZ Reader",
                description = "Browse ZIP, 7Z, TAR, GZ, BZ2, XZ, CBZ archives or extract with ZIP-Slip guard",
                icon = Icons.Default.Archive,
                badge = "Viewer"
            ),
            ToolItem(
                id = "batch_tools",
                title = "Batch Operations",
                description = "Batch rename, resize images, create password-protected ZIPs & batch convert",
                icon = Icons.Default.Build,
                badge = "Active"
            ),
            ToolItem(
                id = "apk_inspector",
                title = "APK & Installed Apps Analyzer",
                description = "Inspect installed apps, package manifest, signing cert SHA-256 & permissions",
                icon = Icons.Default.Android,
                badge = "Active"
            ),
            ToolItem(
                id = "file_analyzer",
                title = "Universal Inspector & Hex Mode",
                description = "Open any file for magic-byte inspection, SQLite tables, code syntax or raw hex dump",
                icon = Icons.Default.Analytics,
                badge = "Viewer"
            ),
            ToolItem(
                id = "hash_generator",
                title = "Hash Checksum Generator",
                description = "Compute MD5, SHA-1, and SHA-256 checksums for files and strings",
                icon = Icons.Default.Fingerprint,
                badge = "Active"
            ),
            ToolItem(
                id = "ocr_tools",
                title = "OCR & PDF Text Extractor",
                description = "On-device ML Kit text recognition from photos and PDF text extraction",
                icon = Icons.Default.DocumentScanner,
                badge = "Active"
            ),
            ToolItem(
                id = "diff_tools",
                title = "Text Diff, Encoder & Split/Merge",
                description = "Compare file diffs (LCS), Base64/URL encode, word counter, and split/merge files",
                icon = Icons.Default.CompareArrows,
                badge = "Active"
            ),
            ToolItem(
                id = "qr_scanner",
                title = "QR & Barcode Studio",
                description = "Live CameraX scanner, Photo Picker scan, and ZXing QR Code generator",
                icon = Icons.Default.QrCodeScanner,
                badge = "Active"
            )
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("tools_screen"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                Text(
                    text = strings.toolsTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = strings.toolsSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(toolsList, key = { it.id }) { tool ->
            ToolDashboardCard(
                tool = tool,
                onClick = {
                    when (tool.id) {
                        "private_vault" -> onOpenTool(ToolRoute.PRIVATE_VAULT)
                        "smart_assistant" -> openSmartInspectPicker.launch(arrayOf("*/*"))
                        "advanced_management" -> onOpenTool(ToolRoute.ADVANCED_MANAGEMENT)
                        "storage_analyzer" -> onOpenTool(ToolRoute.STORAGE_ANALYZER)
                        "file_converter" -> onOpenTool(ToolRoute.FILE_CONVERTER)
                        "qr_scanner" -> onOpenTool(ToolRoute.QR_STUDIO)
                        "ocr_tools" -> onOpenTool(ToolRoute.OCR_TEXT_TOOLS)
                        "diff_tools" -> onOpenTool(ToolRoute.DIFF_TOOLS)
                        "batch_tools" -> onOpenTool(ToolRoute.BATCH_TOOLS)
                        "apk_inspector" -> onOpenTool(ToolRoute.INSTALLED_APPS)
                        "hash_generator" -> showHashDialog = true
                        "pdf_tools" -> openPdfPicker.launch(arrayOf("application/pdf"))
                        "image_tools" -> openImagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        "audio_tools" -> openAnyFilePicker.launch(arrayOf("audio/*", "video/*"))
                        "archive_manager" -> openAnyFilePicker.launch(arrayOf("application/zip", "application/x-7z-compressed", "application/x-tar", "application/gzip", "*/*"))
                        "file_analyzer" -> openAnyFilePicker.launch(arrayOf("*/*"))
                    }
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(84.dp))
        }
    }

    if (showHashDialog) {
        FunctionalHashGeneratorDialog(onDismiss = { showHashDialog = false })
    }
}

data class ToolItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val badge: String
)

@Composable
private fun ToolDashboardCard(
    tool: ToolItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .testTag("tool_card_${tool.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = tool.title,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tool.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = BrandBluePrimaryLight.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = tool.badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = BrandBluePrimaryLight,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FunctionalHashGeneratorDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var algorithm by remember { mutableStateOf("SHA-256") }
    var hashResult by remember { mutableStateOf<String?>(null) }
    var isCalculating by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            selectedFileName = resolveFileName(context, uri)
            inputText = ""
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Hash Generator",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Calculate cryptographic hash checksums for plain text or any selected file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("SHA-256", "MD5", "SHA-1").forEach { algo ->
                        OutlinedButton(
                            onClick = { algorithm = algo },
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                containerColor = if (algorithm == algo) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = algo, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(selectedFileName ?: "Pick a File...")
                    }
                    if (selectedFileUri != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        TextButton(onClick = {
                            selectedFileUri = null
                            selectedFileName = null
                        }) {
                            Text("Clear")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "OR enter text below:", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it
                        selectedFileUri = null
                        selectedFileName = null
                    },
                    placeholder = { Text("Enter string to hash...") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isCalculating = true
                            hashResult = withContext(Dispatchers.IO) {
                                try {
                                    val digest = MessageDigest.getInstance(algorithm)
                                    if (selectedFileUri != null) {
                                        context.contentResolver.openInputStream(selectedFileUri!!)?.use { stream ->
                                            val buffer = ByteArray(8192)
                                            var read: Int
                                            while (stream.read(buffer).also { read = it } != -1) {
                                                digest.update(buffer, 0, read)
                                            }
                                        }
                                    } else {
                                        digest.update(inputText.toByteArray())
                                    }
                                    val bytes = digest.digest()
                                    bytes.joinToString("") { "%02x".format(it) }
                                } catch (e: Exception) {
                                    "Error: ${e.message}"
                                }
                            }
                            isCalculating = false
                        }
                    },
                    enabled = (inputText.isNotBlank() || selectedFileUri != null) && !isCalculating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isCalculating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                    } else {
                        Text("Compute $algorithm")
                    }
                }

                if (hashResult != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "$algorithm Result:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = hashResult!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("$algorithm Hash", hashResult)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
