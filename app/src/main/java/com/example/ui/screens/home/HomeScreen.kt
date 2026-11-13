package com.example.ui.screens.home

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.FileCategory
import com.example.domain.model.FileItem
import com.example.domain.model.StorageStats
import com.example.ui.components.FileListItem
import com.example.ui.components.StorageDonutChart
import com.example.ui.components.StorageUsageCard
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.screens.tools.ToolRoute
import com.example.ui.theme.ColorApk
import com.example.ui.theme.ColorArchives
import com.example.ui.theme.ColorAudio
import com.example.ui.theme.ColorDocuments
import com.example.ui.theme.ColorImages
import com.example.ui.theme.ColorVideos
import com.example.viewmodel.FileManagerUiState

@Composable
fun HomeScreen(
    uiState: FileManagerUiState,
    onOpenGlobalSearch: () -> Unit,
    onCategoryClick: (FileCategory) -> Unit,
    onNavigateToBrowse: () -> Unit,
    onNavigateToTools: () -> Unit,
    onOpenTool: (ToolRoute) -> Unit,
    onNavigateToHashTool: () -> Unit,
    onFileClick: (FileItem) -> Unit,
    onFileLongClick: (FileItem) -> Unit,
    onToggleFavorite: (FileItem) -> Unit,
    onShowFileDetails: (FileItem) -> Unit,
    onRenameFile: (FileItem) -> Unit,
    onDeleteFile: (FileItem) -> Unit,
    onShareFile: (FileItem) -> Unit,
    onSmartAnalyze: (FileItem) -> Unit = {},
    onMoveToVault: (FileItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val smartInspectPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
            onSmartAnalyze(
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
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        // App Branding Toolbar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "App Logo",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = strings.appTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = strings.appSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Global Search Trigger Bar
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onOpenGlobalSearch() }
                    .testTag("global_search_field"),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = strings.searchPlaceholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Storage Usage Card
        item {
            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                StorageUsageCard(
                    storageStats = uiState.storageStats,
                    onInternalStorageClick = onNavigateToBrowse,
                    onSdCardClick = onNavigateToBrowse,
                    onStorageAnalyzerClick = { onOpenTool(ToolRoute.STORAGE_ANALYZER) }
                )
            }
        }

        // Category Storage Breakdown Chart
        if (uiState.storageBreakdown.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = strings.quickCategories,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        StorageDonutChart(breakdown = uiState.storageBreakdown)
                    }
                }
            }
        }

        // Quick Access Categories
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = strings.quickCategories,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                val categories = listOf(
                    QuickCategoryItem(strings.catDocuments, Icons.Default.Description, ColorDocuments, FileCategory.DOCUMENTS),
                    QuickCategoryItem(strings.catImages, Icons.Default.Image, ColorImages, FileCategory.IMAGES),
                    QuickCategoryItem(strings.catVideos, Icons.Default.VideoFile, ColorVideos, FileCategory.VIDEOS),
                    QuickCategoryItem(strings.catAudio, Icons.Default.AudioFile, ColorAudio, FileCategory.AUDIO),
                    QuickCategoryItem(strings.catArchives, Icons.Default.Archive, ColorArchives, FileCategory.ARCHIVES),
                    QuickCategoryItem(strings.catApks, Icons.Default.Android, ColorApk, FileCategory.APPLICATIONS)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    categories.take(3).forEach { item ->
                        CategoryCard(
                            item = item,
                            onClick = { onCategoryClick(item.category) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    categories.takeLast(3).forEach { item ->
                        CategoryCard(
                            item = item,
                            onClick = { onCategoryClick(item.category) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Quick Tools Shortcuts
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = strings.toolsShortcuts,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = strings.viewAll,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { onNavigateToTools() }
                            .padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickToolChip(
                        title = strings.shortcutVault,
                        subtitle = "PIN / AES-256",
                        icon = Icons.Default.EnhancedEncryption,
                        onClick = { onOpenTool(ToolRoute.PRIVATE_VAULT) },
                        modifier = Modifier.testTag("tool_shortcut_vault")
                    )
                    QuickToolChip(
                        title = strings.shortcutAssistant,
                        subtitle = "Diagnose file",
                        icon = Icons.Default.AutoAwesome,
                        onClick = { smartInspectPicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.testTag("tool_shortcut_assistant")
                    )
                    QuickToolChip(
                        title = strings.shortcutTimeline,
                        subtitle = "Rules & Compare",
                        icon = Icons.Default.Timeline,
                        onClick = { onOpenTool(ToolRoute.ADVANCED_MANAGEMENT) },
                        modifier = Modifier.testTag("tool_shortcut_timeline")
                    )
                    QuickToolChip(
                        title = strings.shortcutAnalyzer,
                        subtitle = "Cleanup & Trash",
                        icon = Icons.Default.PieChart,
                        onClick = { onOpenTool(ToolRoute.STORAGE_ANALYZER) },
                        modifier = Modifier.testTag("tool_shortcut_storage_analyzer")
                    )
                    QuickToolChip(
                        title = strings.shortcutConverter,
                        subtitle = "PDF, Img, Audio",
                        icon = Icons.Default.Transform,
                        onClick = { onOpenTool(ToolRoute.FILE_CONVERTER) },
                        modifier = Modifier.testTag("tool_shortcut_converter")
                    )
                    QuickToolChip(
                        title = "QR & Scan",
                        subtitle = "Camera & Image",
                        icon = Icons.Default.QrCodeScanner,
                        onClick = { onOpenTool(ToolRoute.QR_STUDIO) },
                        modifier = Modifier.testTag("tool_shortcut_qr")
                    )
                    QuickToolChip(
                        title = "Hash Check",
                        subtitle = "SHA-256 / MD5",
                        icon = Icons.Default.Fingerprint,
                        onClick = onNavigateToHashTool,
                        modifier = Modifier.testTag("tool_shortcut_hash")
                    )
                }
            }
        }

        // Saved Bookmarks Section
        if (uiState.bookmarks.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bookmark, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.viewerBookmarks, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        uiState.bookmarks.take(10).forEach { bm ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                modifier = Modifier.clickable {
                                    val uri = Uri.parse(bm.uri)
                                    onFileClick(
                                        FileItem(
                                            uri = uri,
                                            name = bm.fileName,
                                            path = bm.uri,
                                            extension = bm.fileName.substringAfterLast('.', "")
                                        )
                                    )
                                }
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    Text(bm.fileName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(bm.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Reading History Section
        if (uiState.readingHistory.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.continueViewing, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        uiState.readingHistory.take(10).forEach { entry ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier.clickable {
                                    val uri = Uri.parse(entry.uri)
                                    onFileClick(
                                        FileItem(
                                            uri = uri,
                                            name = entry.name,
                                            path = entry.uri,
                                            extension = entry.name.substringAfterLast('.', ""),
                                            size = entry.size,
                                            lastModified = entry.lastOpenedAt,
                                            mimeType = entry.mimeType
                                        )
                                    )
                                }
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    Text(entry.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(
                                        text = "${entry.viewerType} • ${StorageStats.formatBytes(entry.size)}",
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

        // Favorite Files Section
        if (uiState.favoriteFiles.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Favorites",
                            tint = ColorArchives,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.favoriteFiles,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    uiState.favoriteFiles.forEach { file ->
                        FileListItem(
                            fileItem = file,
                            isSelected = false,
                            isMultiSelecting = false,
                            onItemClick = { onFileClick(file) },
                            onItemLongClick = { onFileLongClick(file) },
                            onToggleSelect = { },
                            onToggleFavorite = { onToggleFavorite(file) },
                            onShowDetails = { onShowFileDetails(file) },
                            onRename = { onRenameFile(file) },
                            onDelete = { onDeleteFile(file) },
                            onShare = { onShareFile(file) },
                            onSmartAnalyze = { onSmartAnalyze(file) },
                            onMoveToVault = { onMoveToVault(file) }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }

        // Recent Files Section
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = strings.recentFiles,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (uiState.recentFiles.isEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = strings.noRecentFiles,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = strings.grantPermissionHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    uiState.recentFiles.take(8).forEach { file ->
                        FileListItem(
                            fileItem = file,
                            isSelected = false,
                            isMultiSelecting = false,
                            onItemClick = { onFileClick(file) },
                            onItemLongClick = { onFileLongClick(file) },
                            onToggleSelect = { },
                            onToggleFavorite = { onToggleFavorite(file) },
                            onShowDetails = { onShowFileDetails(file) },
                            onRename = { onRenameFile(file) },
                            onDelete = { onDeleteFile(file) },
                            onShare = { onShareFile(file) },
                            onSmartAnalyze = { onSmartAnalyze(file) },
                            onMoveToVault = { onMoveToVault(file) }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickToolChip(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, title, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private data class QuickCategoryItem(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val category: FileCategory
)

@Composable
private fun CategoryCard(
    item: QuickCategoryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("category_shortcut_${item.title.lowercase()}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
                color = item.color.copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = item.color,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
