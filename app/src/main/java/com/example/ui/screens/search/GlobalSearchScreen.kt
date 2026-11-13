package com.example.ui.screens.search

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.data.db.LocalDataRepository
import com.example.data.storage.AdvancedStorageOperations
import com.example.data.storage.SearchFilter
import com.example.domain.model.FileCategory
import com.example.domain.model.FileItem
import com.example.ui.components.FileListItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    initialQuery: String = "",
    localRepo: LocalDataRepository,
    onOpenFile: (FileItem) -> Unit,
    onToggleFavorite: (FileItem) -> Unit,
    onShowDetails: (FileItem) -> Unit,
    onRenameFile: (FileItem) -> Unit,
    onDeleteFile: (FileItem) -> Unit,
    onShareFile: (FileItem) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(enabled = true) { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storageOps = remember { AdvancedStorageOperations(context) }

    val searchHistory by localRepo.searchHistory.collectAsState(initial = emptyList())

    var query by remember { mutableStateOf(initialQuery) }
    var extensionFilter by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<FileCategory?>(null) }
    var minSizeBytes by remember { mutableLongStateOf(0L) }
    var searchInsideText by remember { mutableStateOf(false) }

    var results by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var hasExecuted by remember { mutableStateOf(false) }

    fun executeSearch(recordHistory: Boolean = true) {
        scope.launch {
            isSearching = true
            hasExecuted = true
            if (recordHistory && query.isNotBlank()) {
                localRepo.recordSearch(query)
            }
            val filter = SearchFilter(
                query = query,
                extension = extensionFilter,
                category = selectedCategory,
                minSizeBytes = if (minSizeBytes > 0L) minSizeBytes else null,
                searchInsideTextContent = searchInsideText
            )
            results = storageOps.searchAccessibleFiles(filter, limit = 200)
            isSearching = false
        }
    }

    LaunchedEffect(selectedCategory, minSizeBytes, searchInsideText) {
        if (hasExecuted || query.isNotBlank() || selectedCategory != null || minSizeBytes > 0L) {
            executeSearch(recordHistory = false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Global File Search", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("search_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("global_search_screen")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Filename or keyword inside text...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { executeSearch(true) }),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { executeSearch(true) }) {
                    Icon(Icons.Default.Search, "Run Search", tint = MaterialTheme.colorScheme.primary)
                }
            }

            // Extension & text-content filter row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = extensionFilter,
                    onValueChange = { extensionFilter = it },
                    placeholder = { Text("Ext (e.g. pdf)") },
                    singleLine = true,
                    modifier = Modifier.width(125.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Inside text", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(6.dp))
                    Switch(checked = searchInsideText, onCheckedChange = { searchInsideText = it })
                }
            }

            // Category chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("All Types") }
                )
                listOf(
                    FileCategory.DOCUMENTS,
                    FileCategory.IMAGES,
                    FileCategory.VIDEOS,
                    FileCategory.AUDIO,
                    FileCategory.ARCHIVES,
                    FileCategory.APPLICATIONS
                ).forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                        label = { Text(cat.title) }
                    )
                }
            }

            // Size presets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    0L to "Any size",
                    1024L * 1024L to "> 1 MB",
                    10L * 1024L * 1024L to "> 10 MB",
                    50L * 1024L * 1024L to "> 50 MB"
                ).forEach { (bytes, lbl) ->
                    FilterChip(
                        selected = minSizeBytes == bytes,
                        onClick = { minSizeBytes = bytes },
                        label = { Text(lbl) }
                    )
                }
            }

            // Recent searches
            if (searchHistory.isNotEmpty() && !hasExecuted) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recent Searches", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { scope.launch { localRepo.clearSearchHistory() } }) {
                            Text("Clear All")
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(searchHistory, key = { it.query }) { entry ->
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable {
                                    query = entry.query
                                    executeSearch(false)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.History, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(entry.query, style = MaterialTheme.typography.labelMedium)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.Close,
                                        "Remove",
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { scope.launch { localRepo.deleteSearch(entry.query) } }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            if (isSearching) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (hasExecuted && results.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No matching files found in accessible storage.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(results, key = { it.uri.toString() + it.name }) { item ->
                        FileListItem(
                            fileItem = item,
                            isSelected = false,
                            isMultiSelecting = false,
                            onItemClick = { onOpenFile(item) },
                            onItemLongClick = {},
                            onToggleSelect = {},
                            onToggleFavorite = { onToggleFavorite(item) },
                            onShowDetails = { onShowDetails(item) },
                            onRename = { onRenameFile(item) },
                            onDelete = { onDeleteFile(item) },
                            onShare = { onShareFile(item) }
                        )
                    }
                }
            }
        }
    }
}
