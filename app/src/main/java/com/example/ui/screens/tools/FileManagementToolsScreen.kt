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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.db.LocalDataRepository
import com.example.data.db.OrganizationRuleEntity
import com.example.data.storage.AdvancedStorageOperations
import com.example.data.storage.FolderComparisonEntry
import com.example.data.storage.FolderComparisonResult
import com.example.data.storage.Phase2FileOperations
import com.example.data.storage.SimilarImageGroup
import com.example.domain.model.FileItem
import com.example.domain.model.StorageStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagementToolsScreen(
    localRepo: LocalDataRepository,
    initialTab: Int = 0,
    onOpenFile: (FileItem) -> Unit,
    onMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(enabled = true) { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storageOps = remember { AdvancedStorageOperations(context) }

    var selectedTab by remember { mutableIntStateOf(initialTab) }
    val tabs = listOf("Compare", "Similar Img", "Auto-Rules", "Timeline")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Smart File Management", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("mgmt_back")) {
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
                .testTag("file_management_tools_screen")
        ) {
            SecondaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { idx, label ->
                    Tab(selected = selectedTab == idx, onClick = { selectedTab = idx }, text = { Text(label, maxLines = 1) })
                }
            }

            when (selectedTab) {
                0 -> FolderCompareTab(onMessage = onMessage)
                1 -> SimilarImagesTab(storageOps = storageOps, onOpenFile = onOpenFile, onMessage = onMessage)
                2 -> AutoOrganizeRulesTab(localRepo = localRepo, storageOps = storageOps, onMessage = onMessage)
                3 -> ActivityTimelineTab(localRepo = localRepo)
            }
        }
    }
}

@Composable
private fun FolderCompareTab(onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var leftUri by remember { mutableStateOf<Uri?>(null) }
    var rightUri by remember { mutableStateOf<Uri?>(null) }
    var useSha256 by remember { mutableStateOf(false) }
    var isComparing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<FolderComparisonResult?>(null) }

    val pickLeft = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) leftUri = uri
    }
    val pickRight = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) rightUri = uri
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Compare Two Folders", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickLeft.launch(null) }, modifier = Modifier.weight(1f)) {
                            Text(leftUri?.lastPathSegment?.substringAfterLast(':') ?: "Left: App Storage", maxLines = 1)
                        }
                        OutlinedButton(onClick = { pickRight.launch(null) }, modifier = Modifier.weight(1f)) {
                            Text(rightUri?.lastPathSegment?.substringAfterLast(':') ?: "Right: App Storage", maxLines = 1)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Deep SHA-256 content compare", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = useSha256, onCheckedChange = { useSha256 = it })
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                isComparing = true
                                result = Phase2FileOperations.compareFolders(context, leftUri, rightUri, useSha256)
                                isComparing = false
                            }
                        },
                        enabled = !isComparing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Compare, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isComparing) "Comparing..." else "Run Folder Comparison")
                    }
                }
            }
        }

        if (isComparing) {
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        }

        result?.let { res ->
            item {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Only Left: ${res.onlyLeftCount} • Only Right: ${res.onlyRightCount} • Different: ${res.differentCount} • Identical: ${res.identicalCount}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            items(res.entries, key = { it.relativeName + it.status.name }) { entry ->
                val color = when (entry.status) {
                    FolderComparisonEntry.DiffStatus.IDENTICAL -> MaterialTheme.colorScheme.primary
                    FolderComparisonEntry.DiffStatus.DIFFERENT -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.tertiary
                }
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.relativeName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(entry.status.name, style = MaterialTheme.typography.labelSmall, color = color)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SimilarImagesTab(
    storageOps: AdvancedStorageOperations,
    onOpenFile: (FileItem) -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isScanning by remember { mutableStateOf(false) }
    var groups by remember { mutableStateOf<List<SimilarImageGroup>>(emptyList()) }
    var scanJob by remember { mutableStateOf<Job?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    scanJob = scope.launch {
                        isScanning = true
                        groups = Phase2FileOperations.findSimilarImages(context, storageOps)
                        isScanning = false
                        onMessage("Found ${groups.size} similar photo group(s)")
                    }
                },
                enabled = !isScanning,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isScanning) "Scanning..." else "Scan Similar Photos")
            }
            if (isScanning) {
                OutlinedButton(onClick = { scanJob?.cancel(); isScanning = false }) { Text("Cancel") }
            }
        }
        if (isScanning) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        if (groups.isEmpty() && !isScanning) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Tap Scan to group visually similar photos via perceptual hash.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(groups, key = { it.groupId }) { grp ->
                    Card(shape = RoundedCornerShape(14.dp)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${grp.files.size} visually similar photos", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            grp.files.forEach { file ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { onOpenFile(file) }.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = file.uri,
                                        contentDescription = file.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                        Text(StorageStats.formatBytes(file.size), style = MaterialTheme.typography.labelSmall)
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

@Composable
private fun AutoOrganizeRulesTab(
    localRepo: LocalDataRepository,
    storageOps: AdvancedStorageOperations,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rules by localRepo.organizationRules.collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var isRunning by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Rule")
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        isRunning = true
                        val rep = Phase2FileOperations.runOrganizationRules(context, rules, storageOps) { idx, total, name ->
                            progressText = "($idx/$total) $name"
                        }
                        localRepo.logActivity("ORGANIZE", "AppStorage", "Rules", "Organized ${rep.movedCount} of ${rep.scannedCount} files")
                        isRunning = false
                        onMessage("Organized ${rep.movedCount} file(s) (${rep.errors.size} errors)")
                    }
                },
                enabled = rules.any { it.enabled } && !isRunning
            ) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Run Now")
            }
        }
        if (isRunning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(progressText, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }

        if (rules.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text("No organization rules yet", fontWeight = FontWeight.Bold)
                    Text("Example: move 'pdf,docx' -> 'Documents_Organized'", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rules, key = { it.id }) { rule ->
                    Card(shape = RoundedCornerShape(14.dp)) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(rule.name, fontWeight = FontWeight.Bold)
                                Text("Ext: ${rule.extensionsCsv} → ${rule.targetSubfolder}", style = MaterialTheme.typography.labelSmall)
                            }
                            Switch(checked = rule.enabled, onCheckedChange = { enabled ->
                                scope.launch { localRepo.setOrganizationRuleEnabled(rule.id, enabled) }
                            })
                            IconButton(onClick = { scope.launch { localRepo.removeOrganizationRule(rule.id) } }) {
                                Icon(Icons.Default.Delete, "Delete rule", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("Sort PDFs") }
        var extPattern by remember { mutableStateOf("pdf,epub") }
        var targetFolder by remember { mutableStateOf("Organized/Documents") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Organization Rule", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Rule Name") }, singleLine = true)
                    OutlinedTextField(value = extPattern, onValueChange = { extPattern = it }, label = { Text("Extensions (comma-separated)") }, singleLine = true)
                    OutlinedTextField(value = targetFolder, onValueChange = { targetFolder = it }, label = { Text("Target App Folder") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        localRepo.saveOrganizationRule(
                            name = name.trim().ifBlank { "Rule" },
                            extensionsCsv = extPattern.trim(),
                            targetSubfolder = targetFolder.trim().ifBlank { "Organized" }
                        )
                    }
                    showAddDialog = false
                }) { Text("Save Rule") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ActivityTimelineTab(localRepo: LocalDataRepository) {
    val scope = rememberCoroutineScope()
    val history by localRepo.readingHistory.collectAsState(initial = emptyList())
    val activity by localRepo.recentActivity.collectAsState(initial = emptyList())
    val fmt = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Recent Operations & Viewing Timeline", fontWeight = FontWeight.Bold)
                }
                if (activity.isNotEmpty()) {
                    TextButton(onClick = { scope.launch { localRepo.clearActivityLog() } }) { Text("Clear") }
                }
            }
        }
        items(activity, key = { "act_${it.id}" }) { act ->
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${act.action} • ${act.fileName}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(fmt.format(Date(act.timestamp)), style = MaterialTheme.typography.labelSmall)
                    }
                    if (act.details.isNotBlank()) Text(act.details, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(history, key = { "hist_${it.uri}" }) { h ->
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("VIEWED • ${h.name}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(fmt.format(Date(h.lastOpenedAt)), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
