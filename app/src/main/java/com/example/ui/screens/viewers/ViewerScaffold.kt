package com.example.ui.screens.viewers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.engine.FileInspectionResult
import com.example.domain.engine.FormatSupportStatus
import com.example.domain.model.FileItem
import com.example.domain.model.StorageStats

import androidx.compose.material.icons.filled.AutoAwesome

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScaffold(
    fileItem: FileItem,
    inspection: FileInspectionResult?,
    isBookmarked: Boolean,
    isHexMode: Boolean,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleHexMode: () -> Unit,
    onShare: () -> Unit,
    onSmartAnalyze: (() -> Unit)? = null,
    extraActions: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit
) {
    var showInspectorDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = fileItem.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val badgeColor = when (inspection?.supportStatus) {
                                FormatSupportStatus.FULL -> MaterialTheme.colorScheme.primary
                                FormatSupportStatus.PARTIAL -> Color(0xFFFF9800)
                                FormatSupportStatus.INSPECT_ONLY -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.error
                            }
                            Text(
                                text = inspection?.formatName ?: fileItem.extension.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor,
                                maxLines = 1
                            )
                            if (inspection != null) {
                                Text(
                                    text = " • ${inspection.supportStatus.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("viewer_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    extraActions()
                    if (onSmartAnalyze != null) {
                        IconButton(
                            onClick = onSmartAnalyze,
                            modifier = Modifier.testTag("viewer_smart_assistant_button")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Smart File Assistant")
                        }
                    }
                    IconButton(
                        onClick = onToggleBookmark,
                        modifier = Modifier.testTag("viewer_bookmark_button")
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onToggleHexMode,
                        modifier = Modifier.testTag("viewer_hex_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "Toggle Hex Inspector",
                            tint = if (isHexMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { showInspectorDialog = true },
                        modifier = Modifier.testTag("viewer_info_button")
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Format Inspector")
                    }
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.testTag("viewer_share_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (inspection != null && (inspection.isOversizedWarning || inspection.isPotentiallyCorrupted)) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                inspection.isPotentiallyCorrupted -> "File header looks corrupted or empty."
                                else -> "Large file (${StorageStats.formatBytes(fileItem.size)}): using streaming preview."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            content(Modifier.weight(1f))
        }
    }

    if (showInspectorDialog && inspection != null) {
        AlertDialog(
            onDismissRequest = { showInspectorDialog = false },
            title = { Text("Format & Signature Analysis", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    InspectRow("Format", inspection.formatName)
                    InspectRow("Support Status", inspection.supportStatus.label)
                    InspectRow("Notes", inspection.supportNotes)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    InspectRow("Magic Signature", inspection.signatureMatch)
                    InspectRow("Header Hex", inspection.magicBytesHex, monospace = true)
                    InspectRow("Detected MIME", inspection.detectedMimeType)
                    InspectRow("Extension", inspection.detectedExtension.ifBlank { "(none)" })
                    InspectRow("Size", "${fileItem.formattedSize} (${fileItem.size} bytes)")
                    InspectRow("Password Flag", if (inspection.isPasswordProtected) "Yes" else "No")
                }
            },
            confirmButton = {
                TextButton(onClick = { showInspectorDialog = false }) { Text("Close") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun InspectRow(label: String, value: String, monospace: Boolean = false) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            fontWeight = FontWeight.Medium
        )
    }
}
