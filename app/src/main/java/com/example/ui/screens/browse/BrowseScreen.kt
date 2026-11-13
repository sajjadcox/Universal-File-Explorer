package com.example.ui.screens.browse

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.FileItem
import com.example.domain.model.SortConfig
import com.example.domain.model.SortField
import com.example.domain.model.SortOrder
import com.example.domain.model.ViewMode
import com.example.ui.components.BreadcrumbBar
import com.example.ui.components.FileGridItem
import com.example.ui.components.FileListItem
import com.example.ui.components.MultiSelectActionBar
import com.example.ui.components.SkeletonFileListPlaceholder
import com.example.ui.i18n.LocalAppStrings
import com.example.viewmodel.FileManagerUiState

@Composable
fun BrowseScreen(
    uiState: FileManagerUiState,
    onRootFolderSelected: (Uri, String) -> Unit,
    onNavigateIntoFolder: (FileItem) -> Unit,
    onBreadcrumbClick: (Int) -> Unit,
    onRefreshFolder: () -> Unit,
    onToggleSelect: (Uri) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onCopySelected: () -> Unit,
    onMoveSelected: () -> Unit,
    onBatchSelected: () -> Unit,
    onShareSelected: () -> Unit = {},
    onCopyFile: (FileItem) -> Unit,
    onMoveFile: (FileItem) -> Unit,
    onMoveToTrash: (FileItem) -> Unit,
    onPasteClipboard: () -> Unit,
    onClearClipboard: () -> Unit,
    onDeleteFile: (FileItem) -> Unit,
    onRenameFile: (FileItem) -> Unit,
    onToggleFavorite: (FileItem) -> Unit,
    onShowFileDetails: (FileItem) -> Unit,
    onShareFile: (FileItem) -> Unit,
    onSmartAnalyze: (FileItem) -> Unit = {},
    onMoveToVault: (FileItem) -> Unit = {},
    onFileClick: (FileItem) -> Unit,
    onCreateFolderClick: () -> Unit,
    onCreateFileClick: () -> Unit,
    onToggleViewMode: (ViewMode) -> Unit,
    onUpdateSortConfig: (SortConfig) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var showSortMenu by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }

    val directoryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val folderName = uri.lastPathSegment?.substringAfterLast(':') ?: "Selected Folder"
            onRootFolderSelected(uri, folderName)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("browse_screen"),
        floatingActionButton = {
            if (!uiState.isMultiSelecting) {
                Column(
                    modifier = Modifier.padding(bottom = 76.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SmallFloatingActionButton(
                        onClick = onCreateFileClick,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.testTag("fab_create_file")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = "Create new file")
                    }
                    FloatingActionButton(
                        onClick = onCreateFolderClick,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("fab_create_folder")
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "Create new folder")
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = uiState.isMultiSelecting) {
                MultiSelectActionBar(
                    selectedCount = uiState.selectedUris.size,
                    onClearSelection = onClearSelection,
                    onSelectAll = onSelectAll,
                    onDeleteSelected = onDeleteSelected,
                    onCopySelected = onCopySelected,
                    onMoveSelected = onMoveSelected,
                    onBatchSelected = onBatchSelected,
                    onShareSelected = onShareSelected
                )
            }

            if (!uiState.isMultiSelecting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.currentDirectoryName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${uiState.filteredBrowseFiles.size} ${strings.itemsCountSuffix}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { directoryPicker.launch(null) },
                            modifier = Modifier.testTag("button_open_saf_folder")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Open folder with SAF",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = { isSearching = !isSearching },
                            modifier = Modifier.testTag("button_toggle_search")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = if (isSearching) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = {
                                val next = if (uiState.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
                                onToggleViewMode(next)
                            },
                            modifier = Modifier.testTag("button_toggle_view_mode")
                        ) {
                            Icon(
                                imageVector = if (uiState.viewMode == ViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList,
                                contentDescription = "Switch view mode",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.testTag("button_sort_menu")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Sort files",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Name (A-Z)") },
                                    onClick = {
                                        onUpdateSortConfig(SortConfig(SortField.NAME, SortOrder.ASCENDING))
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Name (Z-A)") },
                                    onClick = {
                                        onUpdateSortConfig(SortConfig(SortField.NAME, SortOrder.DESCENDING))
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Date (Newest first)") },
                                    onClick = {
                                        onUpdateSortConfig(SortConfig(SortField.DATE, SortOrder.DESCENDING))
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Date (Oldest first)") },
                                    onClick = {
                                        onUpdateSortConfig(SortConfig(SortField.DATE, SortOrder.ASCENDING))
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Size (Largest first)") },
                                    onClick = {
                                        onUpdateSortConfig(SortConfig(SortField.SIZE, SortOrder.DESCENDING))
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Size (Smallest first)") },
                                    onClick = {
                                        onUpdateSortConfig(SortConfig(SortField.SIZE, SortOrder.ASCENDING))
                                        showSortMenu = false
                                    }
                                )
                            }
                        }

                        IconButton(
                            onClick = onRefreshFolder,
                            modifier = Modifier.testTag("button_refresh")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = isSearching) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search files in this folder...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .testTag("browse_search_input")
                    )
                }

                BreadcrumbBar(
                    breadcrumbs = uiState.browseBreadcrumbs,
                    onBreadcrumbClick = onBreadcrumbClick
                )
            }

            // Clipboard Paste Banner
            if (uiState.clipboardItems.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ContentPaste, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${if (uiState.clipboardIsMove) "Move" else "Copy"} ${uiState.clipboardItems.size} file(s)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onPasteClipboard) {
                            Text(strings.pasteHere)
                        }
                        IconButton(onClick = onClearClipboard) {
                            Icon(Icons.Default.Close, "Cancel clipboard", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            if (uiState.isLoadingBrowse) {
                SkeletonFileListPlaceholder(modifier = Modifier.testTag("browse_loading"))
            } else if (uiState.filteredBrowseFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(72.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (uiState.searchQuery.isNotBlank()) "No files match '${uiState.searchQuery}'"
                            else strings.emptyFolderTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = strings.emptyFolderDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { directoryPicker.launch(null) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("button_pick_folder_saf")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.openSafFolder)
                        }
                    }
                }
            } else {
                if (uiState.viewMode == ViewMode.LIST) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("browse_list"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        items(uiState.filteredBrowseFiles, key = { it.uri.toString() }) { item ->
                            val isSelected = uiState.selectedUris.contains(item.uri)

                            FileListItem(
                                fileItem = item,
                                isSelected = isSelected,
                                isMultiSelecting = uiState.isMultiSelecting,
                                onItemClick = {
                                    if (item.isDirectory) {
                                        onNavigateIntoFolder(item)
                                    } else {
                                        onFileClick(item)
                                    }
                                },
                                onItemLongClick = { onToggleSelect(item.uri) },
                                onToggleSelect = { onToggleSelect(item.uri) },
                                onToggleFavorite = { onToggleFavorite(item) },
                                onShowDetails = { onShowFileDetails(item) },
                                onRename = { onRenameFile(item) },
                                onDelete = { onDeleteFile(item) },
                                onShare = { onShareFile(item) },
                                onCopy = { onCopyFile(item) },
                                onMove = { onMoveFile(item) },
                                onMoveToTrash = { onMoveToTrash(item) },
                                onSmartAnalyze = { onSmartAnalyze(item) },
                                onMoveToVault = { onMoveToVault(item) }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        item {
                            Spacer(modifier = Modifier.height(84.dp))
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("browse_grid"),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(uiState.filteredBrowseFiles, key = { it.uri.toString() }) { item ->
                            val isSelected = uiState.selectedUris.contains(item.uri)

                            FileGridItem(
                                fileItem = item,
                                isSelected = isSelected,
                                isMultiSelecting = uiState.isMultiSelecting,
                                onItemClick = {
                                    if (item.isDirectory) {
                                        onNavigateIntoFolder(item)
                                    } else {
                                        onFileClick(item)
                                    }
                                },
                                onItemLongClick = { onToggleSelect(item.uri) },
                                onToggleSelect = { onToggleSelect(item.uri) }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(84.dp))
                        }
                    }
                }
            }
        }
    }
}
