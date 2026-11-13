package com.example.ui.screens.tools

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.db.LocalDataRepository
import com.example.data.db.TrashEntryEntity
import com.example.data.storage.AdvancedStorageOperations
import com.example.data.storage.DuplicateGroup
import com.example.domain.model.FileCategory
import com.example.domain.model.FileItem
import com.example.domain.model.StorageStats
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageAnalyzerScreen(
    storageStats: List<StorageStats>,
    breakdown: Map<FileCategory, Long>,
    currentTreeUri: Uri?,
    localRepo: LocalDataRepository,
    onOpenFile: (FileItem) -> Unit,
    onCategoryClick: (FileCategory) -> Unit,
    onMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(enabled = true) { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storageOps = remember { AdvancedStorageOperations(context) }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Large Files", "Duplicates", "Folders", "Trash")

    var isScanning by remember { mutableStateOf(false) }
    var largeFiles by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var duplicateGroups by remember { mutableStateOf<List<DuplicateGroup>>(emptyList()) }
    var emptyFolders by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var appStorageStats by remember { mutableStateOf<Pair<Long, Int>?>(null) }

    val trashEntries by localRepo.trashEntries.collectAsState(initial = emptyList())
    var confirmTrashMove by remember { mutableStateOf<FileItem?>(null) }
    var confirmPermDeleteTrash by remember { mutableStateOf<TrashEntryEntity?>(null) }
    var confirmEmptyTrash by remember { mutableStateOf(false) }
    var pendingRestoreEntry by remember { mutableStateOf<TrashEntryEntity?>(null) }

    val restoreTreePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { destTreeUri: Uri? ->
        val entry = pendingRestoreEntry
        pendingRestoreEntry = null
        if (destTreeUri != null && entry != null) {
            scope.launch {
                val ok = storageOps.restoreFromTrash(entry, destTreeUri)
                if (ok) {
                    localRepo.removeTrashEntry(entry.id)
                    onMessage("Restored '${entry.originalName}'")
                } else {
                    onMessage("Failed to restore '${entry.originalName}'")
                }
            }
        }
    }

    fun runScan() {
        scope.launch {
            isScanning = true
            largeFiles = storageOps.findLargeFiles(5L * 1024L * 1024L)
            duplicateGroups = storageOps.findDuplicateFiles()
            emptyFolders = storageOps.findEmptyFolders(currentTreeUri)
            appStorageStats = storageOps.calculateFolderSize(Uri.fromFile(context.filesDir))
            isScanning = false
        }
    }

    LaunchedEffect(Unit) { runScan() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage Analyzer & Cleaner", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("analyzer_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { runScan() }) {
                        Icon(Icons.Default.Refresh, "Rescan")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("storage_analyzer_screen")
        ) {
            SecondaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = {
                            val badge = if (idx == 4 && trashEntries.isNotEmpty()) " (${trashEntries.size})" else ""
                            Text("$title$badge", maxLines = 1)
                        }
                    )
                }
            }

            if (isScanning && selectedTab in listOf(1, 2, 3)) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when (selectedTab) {
                0 -> {
                    val primary = storageStats.firstOrNull { !it.isRemovable } ?: storageStats.firstOrNull()
                    val totalTrashBytes = trashEntries.sumOf { it.size }
                    val duplicateReclaimable = duplicateGroups.sumOf { g -> g.sizeBytes * (g.files.size - 1) }

                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (primary != null) {
                            item {
                                Card(shape = RoundedCornerShape(18.dp)) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(primary.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("${primary.formattedUsed} used of ${primary.formattedTotal} (${primary.formattedFree} free)")
                                        LinearProgressIndicator(
                                            progress = { primary.usedPercentage },
                                            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                                            strokeCap = StrokeCap.Round
                                        )
                                        appStorageStats?.let { (bytes, count) ->
                                            Text(
                                                "App Internal Workspace: ${StorageStats.formatBytes(bytes)} across $count files",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Category Donut Chart Card
                        item {
                            Card(shape = RoundedCornerShape(18.dp)) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Category Storage Distribution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    com.example.ui.components.StorageDonutChart(breakdown = breakdown)
                                }
                            }
                        }

                        // Cleanup & Trash Retention Insights Card
                        item {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CleaningServices, null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Cleanup & Recovery Insights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    }
                                    Text("• ${largeFiles.size} large file(s) >= 5 MB detected", style = MaterialTheme.typography.bodySmall)
                                    Text("• ${duplicateGroups.size} duplicate group(s) (~${StorageStats.formatBytes(duplicateReclaimable)} reclaimable)", style = MaterialTheme.typography.bodySmall)
                                    Text("• ${emptyFolders.size} empty folder(s) found", style = MaterialTheme.typography.bodySmall)
                                    Text("• App Trash holds ${trashEntries.size} file(s) (${StorageStats.formatBytes(totalTrashBytes)})", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "Recovery Eligibility: Only files moved into App Trash (via Move to Trash) can be restored to any SAF folder. Permanent deletions bypass recovery.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        item {
                            Text("Storage Breakdown by Category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        val totalCategorized = breakdown.values.sum().coerceAtLeast(1L)
                        items(FileCategory.entries) { cat ->
                            val size = breakdown[cat] ?: 0L
                            val ratio = (size.toFloat() / totalCategorized.toFloat()).coerceIn(0f, 1f)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCategoryClick(cat) }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(cat.title, fontWeight = FontWeight.SemiBold)
                                        Text(StorageStats.formatBytes(size), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { ratio },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                                    )
                                }
                            }
                        }
                    }
                }

                1 -> {
                    if (largeFiles.isEmpty() && !isScanning) {
                        EmptyAnalyzerState("No files >= 5 MB found in accessible storage.")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(largeFiles, key = { it.uri.toString() }) { item ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.fillMaxWidth().clickable { onOpenFile(item) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                            Text("${item.formattedSize} • ${item.formattedDate}", style = MaterialTheme.typography.labelSmall)
                                        }
                                        IconButton(onClick = { confirmTrashMove = item }) {
                                            Icon(Icons.Default.Delete, "Move to Trash", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    if (duplicateGroups.isEmpty() && !isScanning) {
                        EmptyAnalyzerState("No exact duplicate files found.")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(duplicateGroups, key = { it.hash + it.sizeBytes }) { group ->
                                Card(shape = RoundedCornerShape(14.dp)) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = "${group.files.size} copies • ${StorageStats.formatBytes(group.sizeBytes)} each",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "SHA-256: ${group.hash.take(16)}...",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        group.files.forEachIndexed { idx, file ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onOpenFile(file) }
                                                    .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                                    Text(file.path, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                                }
                                                if (idx > 0) {
                                                    IconButton(onClick = { confirmTrashMove = file }) {
                                                        Icon(Icons.Default.Delete, "Trash duplicate", tint = MaterialTheme.colorScheme.error)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> {
                    if (emptyFolders.isEmpty() && !isScanning) {
                        EmptyAnalyzerState("No empty folders detected in scanned workspace/tree.")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(emptyFolders, key = { it.uri.toString() }) { folder ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.FolderOff, null, tint = MaterialTheme.colorScheme.secondary)
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(folder.name, fontWeight = FontWeight.SemiBold)
                                            Text(folder.path, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                4 -> {
                    val totalTrashBytes = remember(trashEntries) { trashEntries.sumOf { it.size } }
                    Column(modifier = Modifier.fillMaxSize()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "App Trash (${trashEntries.size} items)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Storage used: ${StorageStats.formatBytes(totalTrashBytes)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (trashEntries.isNotEmpty()) {
                                        OutlinedButton(
                                            onClick = { confirmEmptyTrash = true },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Empty Trash")
                                        }
                                    }
                                }
                                Text(
                                    text = "Recovery eligibility: Files moved via 'Move to Trash' are stored inside app-private Trash until retention expires and can be restored to any SAF folder or App Storage. Files deleted via 'Delete Permanently' or external SAF 'Delete' cannot be recovered.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (trashEntries.isEmpty()) {
                            EmptyAnalyzerState("App Trash is empty.")
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(trashEntries, key = { it.id }) { entry ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(entry.originalName, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                                Text(StorageStats.formatBytes(entry.size), style = MaterialTheme.typography.labelSmall)
                                            }
                                            IconButton(onClick = {
                                                scope.launch {
                                                    // Restore directly to App Storage if no SAF tree picked, or let user pick SAF dir
                                                    pendingRestoreEntry = entry
                                                    restoreTreePicker.launch(null)
                                                }
                                            }) {
                                                Icon(Icons.Default.RestoreFromTrash, "Restore to folder", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = { confirmPermDeleteTrash = entry }) {
                                                Icon(Icons.Default.DeleteForever, "Delete permanently", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmTrashMove?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmTrashMove = null },
            title = { Text("Move to App Trash?", fontWeight = FontWeight.Bold) },
            text = { Text("'${target.name}' (${target.formattedSize}) will be backed up to App Trash and removed from its current location.") },
            confirmButton = {
                Button(onClick = {
                    val file = target
                    confirmTrashMove = null
                    scope.launch {
                        val entry = storageOps.moveToTrash(file)
                        if (entry != null) {
                            localRepo.addTrashEntry(entry)
                            onMessage("Moved '${file.name}' to App Trash")
                            runScan()
                        } else {
                            onMessage("Unable to move '${file.name}' (read-only permission)")
                        }
                    }
                }) { Text("Move to Trash") }
            },
            dismissButton = { TextButton(onClick = { confirmTrashMove = null }) { Text("Cancel") } }
        )
    }

    confirmPermDeleteTrash?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirmPermDeleteTrash = null },
            title = { Text("Delete Permanently?", fontWeight = FontWeight.Bold) },
            text = { Text("Permanently erase '${entry.originalName}'? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmPermDeleteTrash = null
                        scope.launch {
                            storageOps.deleteTrashFilePermanently(entry)
                            localRepo.removeTrashEntry(entry.id)
                            onMessage("Permanently deleted '${entry.originalName}'")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete Forever") }
            },
            dismissButton = { TextButton(onClick = { confirmPermDeleteTrash = null }) { Text("Cancel") } }
        )
    }

    if (confirmEmptyTrash) {
        AlertDialog(
            onDismissRequest = { confirmEmptyTrash = false },
            title = { Text("Empty App Trash?", fontWeight = FontWeight.Bold) },
            text = { Text("Permanently erase all ${trashEntries.size} file(s) in App Trash? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmEmptyTrash = false
                        scope.launch {
                            trashEntries.forEach { storageOps.deleteTrashFilePermanently(it) }
                            localRepo.clearAllTrash()
                            onMessage("App Trash emptied")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Empty Trash") }
            },
            dismissButton = { TextButton(onClick = { confirmEmptyTrash = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EmptyAnalyzerState(msg: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.PieChart, null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(10.dp))
            Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
