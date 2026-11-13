package com.example.ui.screens.tools

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.db.LocalDataRepository
import com.example.domain.converter.ConversionEngine
import com.example.domain.model.FileItem
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileConverterScreen(
    conversionEngine: ConversionEngine,
    localRepo: LocalDataRepository,
    onOpenFile: (FileItem) -> Unit,
    onMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(enabled = true) { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val jobs by localRepo.conversionJobs.collectAsState(initial = emptyList())

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf("") }
    var selectedExt by remember { mutableStateOf("") }
    var selectedTarget by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            val resolvedName = resolveFileName(context, uri)
            selectedName = resolvedName
            selectedExt = resolvedName.substringAfterLast('.', "").lowercase()
            val options = ConversionEngine.getOptionsForExtension(selectedExt)
            selectedTarget = options.firstOrNull { it.isSupported }?.targetFormat
                ?: options.firstOrNull()?.targetFormat ?: ""
        }
    }

    val availableOptions = remember(selectedExt) {
        if (selectedExt.isBlank()) emptyList() else ConversionEngine.getOptionsForExtension(selectedExt)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Universal File Converter", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (jobs.any { it.status in setOf("COMPLETED", "FAILED", "CANCELLED") }) {
                        IconButton(onClick = { scope.launch { localRepo.clearFinishedConversionJobs() } }) {
                            Icon(Icons.Default.DeleteSweep, "Clear Finished")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("file_converter_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("1. Select Source File", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        OutlinedButton(
                            onClick = { filePicker.launch(arrayOf("*/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FileOpen, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (selectedName.isBlank()) "Pick File to Convert..." else "$selectedName (.${selectedExt.uppercase()})")
                        }

                        if (selectedUri != null) {
                            if (availableOptions.isEmpty()) {
                                Text(
                                    text = "No conversion codecs registered for .$selectedExt files.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text("2. Choose Target Format", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    availableOptions.forEach { opt ->
                                        FilterChip(
                                            selected = selectedTarget == opt.targetFormat,
                                            onClick = { selectedTarget = opt.targetFormat },
                                            label = { Text(opt.targetFormat) }
                                        )
                                    }
                                }
                                val chosenOpt = availableOptions.firstOrNull { it.targetFormat == selectedTarget }
                                if (chosenOpt != null && !chosenOpt.isSupported) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Unsupported: ${chosenOpt.note}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                                Button(
                                    onClick = {
                                        conversionEngine.enqueueConversion(
                                            sourceUri = selectedUri!!,
                                            sourceName = selectedName,
                                            sourceExt = selectedExt,
                                            targetFormat = selectedTarget
                                        )
                                        onMessage("Queued $selectedName -> $selectedTarget")
                                    },
                                    enabled = selectedTarget.isNotBlank() && (chosenOpt?.isSupported == true),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Transform, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Convert to $selectedTarget")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text("Conversion Queue (${jobs.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (jobs.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No conversion jobs yet. Converted files are saved to App Storage/Converted.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(jobs, key = { it.id }) { job ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${job.sourceName} -> ${job.targetFormat}", fontWeight = FontWeight.Bold)
                                    Text("Status: ${job.status}", style = MaterialTheme.typography.labelSmall)
                                }
                                when (job.status) {
                                    "RUNNING", "PENDING" -> {
                                        IconButton(onClick = { conversionEngine.cancelJob(job.id) }) {
                                            Icon(Icons.Default.Cancel, "Cancel", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                    "COMPLETED" -> Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                    else -> Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (job.status == "RUNNING") {
                                LinearProgressIndicator(
                                    progress = { job.progress / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (!job.errorMessage.isNullOrBlank()) {
                                Text(job.errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                            if (job.status == "COMPLETED" && !job.outputPath.isNullOrBlank()) {
                                val outFile = File(job.outputPath)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = {
                                        if (outFile.exists()) {
                                            onOpenFile(
                                                FileItem(
                                                    uri = Uri.fromFile(outFile),
                                                    name = outFile.name,
                                                    path = outFile.absolutePath,
                                                    extension = outFile.extension,
                                                    size = outFile.length(),
                                                    lastModified = outFile.lastModified()
                                                )
                                            )
                                        } else {
                                            onMessage("Output file missing: ${outFile.name}")
                                        }
                                    }) {
                                        Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Open Output")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                Text("Supported Format Matrix", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            items(ConversionEngine.ALL_CONVERSIONS) { opt ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(opt.label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${opt.sourceExts.joinToString(", ") { it.uppercase() }} -> ${opt.targetFormat}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (opt.note.isNotBlank()) {
                                Text(opt.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Text(
                            text = if (opt.isSupported) "Supported" else "Unsupported",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (opt.isSupported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

internal fun resolveFileName(context: android.content.Context, uri: Uri): String {
    try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val n = c.getString(0)
                if (!n.isNullOrBlank()) return n
            }
        }
    } catch (_: Exception) {}
    return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
}
