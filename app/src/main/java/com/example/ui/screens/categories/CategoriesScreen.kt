package com.example.ui.screens.categories

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.db.CustomCategoryEntity
import com.example.domain.model.FileCategory
import com.example.domain.model.FileItem
import com.example.domain.model.SortConfig
import com.example.domain.model.SortField
import com.example.domain.model.SortOrder
import com.example.domain.model.ViewMode
import com.example.ui.components.FileGridItem
import com.example.ui.components.FileListItem
import com.example.ui.components.SkeletonFileListPlaceholder
import com.example.ui.i18n.LocalAppStrings
import com.example.viewmodel.FileManagerUiState

@Composable
fun CategoriesScreen(
    uiState: FileManagerUiState,
    onSelectCategory: (FileCategory) -> Unit,
    onRefreshCategory: () -> Unit,
    onFileClick: (FileItem) -> Unit,
    onToggleFavorite: (FileItem) -> Unit,
    onShowFileDetails: (FileItem) -> Unit,
    onRenameFile: (FileItem) -> Unit,
    onDeleteFile: (FileItem) -> Unit,
    onShareFile: (FileItem) -> Unit,
    onToggleViewMode: (ViewMode) -> Unit,
    onUpdateSortConfig: (SortConfig) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onAddCustomCategory: (String, String) -> Unit = { _, _ -> },
    onDeleteCustomCategory: (Long) -> Unit = {},
    onSmartAnalyze: (FileItem) -> Unit = {},
    onMoveToVault: (FileItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    var showSortMenu by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var selectedCustomCategory by remember { mutableStateOf<CustomCategoryEntity?>(null) }
    var showAddCustomDialog by remember { mutableStateOf(false) }

    val displayedFiles = remember(
        selectedCustomCategory,
        uiState.filteredCategoryFiles,
        uiState.recentFiles,
        uiState.browseFiles,
        uiState.searchQuery,
        uiState.sortConfig,
        uiState.showHiddenFiles
    ) {
        val custom = selectedCustomCategory
        if (custom == null) {
            uiState.filteredCategoryFiles
        } else {
            val exts = custom.extensionsCsv.split(",")
                .map { it.trim().lowercase().removePrefix(".") }
                .filter { it.isNotBlank() }
                .toSet()
            val pool = (uiState.recentFiles + uiState.browseFiles + uiState.filteredCategoryFiles)
                .distinctBy { it.uri.toString() }
                .filter { !it.isDirectory && it.extension.lowercase() in exts }
            FileManagerUiState.applySearchAndSort(pool, uiState.searchQuery, uiState.sortConfig, uiState.showHiddenFiles)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("categories_screen")
    ) {
        // Top Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selectedCustomCategory?.name ?: uiState.selectedCategory.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${displayedFiles.size} ${strings.itemsCountSuffix}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { isSearching = !isSearching },
                    modifier = Modifier.testTag("category_toggle_search")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search category",
                        tint = if (isSearching) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        val next = if (uiState.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
                        onToggleViewMode(next)
                    },
                    modifier = Modifier.testTag("category_toggle_view_mode")
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
                        modifier = Modifier.testTag("category_sort_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort",
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
                            text = { Text("Date (Newest first)") },
                            onClick = {
                                onUpdateSortConfig(SortConfig(SortField.DATE, SortOrder.DESCENDING))
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
                    }
                }

                IconButton(
                    onClick = onRefreshCategory,
                    modifier = Modifier.testTag("category_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // In-line Search
        AnimatedVisibility(visible = isSearching) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Filter ${uiState.selectedCategory.title}...") },
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
                    .testTag("category_search_input")
            )
        }

        // Horizontal Category Tabs
        val categories = listOf(
            FileCategory.DOCUMENTS to Icons.Default.Description,
            FileCategory.IMAGES to Icons.Default.Image,
            FileCategory.VIDEOS to Icons.Default.VideoFile,
            FileCategory.AUDIO to Icons.Default.AudioFile,
            FileCategory.ARCHIVES to Icons.Default.Archive,
            FileCategory.APPLICATIONS to Icons.Default.Android,
            FileCategory.EBOOKS to Icons.Default.MenuBook,
            FileCategory.DOWNLOADS to Icons.Default.Download,
            FileCategory.OTHER to Icons.Default.InsertDriveFile
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { (cat, icon) ->
                val isSelected = selectedCustomCategory == null && uiState.selectedCategory == cat

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedCustomCategory = null
                        onSelectCategory(cat)
                    },
                    label = { Text(cat.title) },
                    leadingIcon = {
                        Icon(
                            imageVector = icon,
                            contentDescription = cat.title,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("filter_chip_${cat.name.lowercase()}")
                )
            }
        }

        // Custom Categories Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.customCategories + ":",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            uiState.customCategories.forEach { custom ->
                val isSelected = selectedCustomCategory?.id == custom.id
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        selectedCustomCategory = if (isSelected) null else custom
                    },
                    label = { Text(custom.name) },
                    leadingIcon = { Icon(Icons.Default.Label, null, modifier = Modifier.size(14.dp)) },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (isSelected) selectedCustomCategory = null
                                onDeleteCustomCategory(custom.id)
                            },
                            modifier = Modifier.size(18.dp)
                        ) {
                            Icon(Icons.Default.Close, "Remove custom category", modifier = Modifier.size(14.dp))
                        }
                    }
                )
            }
            AssistChip(
                onClick = { showAddCustomDialog = true },
                label = { Text(strings.addCustomCategory) },
                leadingIcon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)) }
            )
        }

        // Category Content
        if (uiState.isLoadingCategory && selectedCustomCategory == null) {
            SkeletonFileListPlaceholder(modifier = Modifier.testTag("category_loading"))
        } else if (displayedFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
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
                                imageVector = getCategoryVector(uiState.selectedCategory),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "No ${uiState.selectedCategory.title} found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = uiState.selectedCategory.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            if (uiState.viewMode == ViewMode.LIST) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("category_list"),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    items(displayedFiles, key = { it.uri.toString() }) { item ->
                        FileListItem(
                            fileItem = item,
                            isSelected = false,
                            isMultiSelecting = false,
                            onItemClick = { onFileClick(item) },
                            onItemLongClick = { },
                            onToggleSelect = { },
                            onToggleFavorite = { onToggleFavorite(item) },
                            onShowDetails = { onShowFileDetails(item) },
                            onRename = { onRenameFile(item) },
                            onDelete = { onDeleteFile(item) },
                            onShare = { onShareFile(item) },
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
                        .testTag("category_grid"),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayedFiles, key = { it.uri.toString() }) { item ->
                        FileGridItem(
                            fileItem = item,
                            isSelected = false,
                            isMultiSelecting = false,
                            onItemClick = { onFileClick(item) },
                            onItemLongClick = { },
                            onToggleSelect = { }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(84.dp))
                    }
                }
            }
        }
    }

    if (showAddCustomDialog) {
        var catName by remember { mutableStateOf("") }
        var extCsv by remember { mutableStateOf("kt,java,xml") }
        AlertDialog(
            onDismissRequest = { showAddCustomDialog = false },
            title = { Text(strings.addCustomCategory, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = catName,
                        onValueChange = { catName = it },
                        label = { Text("Category Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = extCsv,
                        onValueChange = { extCsv = it },
                        label = { Text("Extensions (e.g. pdf,epub,md)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddCustomCategory(catName.trim(), extCsv.trim())
                        showAddCustomDialog = false
                    },
                    enabled = catName.isNotBlank() && extCsv.isNotBlank()
                ) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustomDialog = false }) { Text(strings.cancel) }
            }
        )
    }
}

private fun getCategoryVector(category: FileCategory): ImageVector {
    return when (category) {
        FileCategory.DOCUMENTS -> Icons.Default.Description
        FileCategory.IMAGES -> Icons.Default.Image
        FileCategory.VIDEOS -> Icons.Default.VideoFile
        FileCategory.AUDIO -> Icons.Default.AudioFile
        FileCategory.ARCHIVES -> Icons.Default.Archive
        FileCategory.APPLICATIONS -> Icons.Default.Android
        FileCategory.EBOOKS -> Icons.Default.MenuBook
        FileCategory.DOWNLOADS -> Icons.Default.Download
        FileCategory.OTHER -> Icons.Default.InsertDriveFile
    }
}
