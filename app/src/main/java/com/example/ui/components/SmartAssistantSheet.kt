package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.assistant.SmartAssistantReport
import com.example.domain.assistant.SmartFileAssistant
import com.example.domain.model.FileItem
import com.example.domain.security.AllHashesResult
import com.example.domain.security.SecurityEngine
import com.example.domain.security.SecurityRiskLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartAssistantBottomSheet(
    fileItem: FileItem,
    onDismiss: () -> Unit,
    onOpenInViewer: (FileItem) -> Unit,
    onMoveToVault: ((FileItem) -> Unit)? = null,
    onOpenConverter: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var report by remember(fileItem.uri) { mutableStateOf<SmartAssistantReport?>(null) }
    var hashes by remember(fileItem.uri) { mutableStateOf<AllHashesResult?>(null) }
    var isLoading by remember(fileItem.uri) { mutableStateOf(true) }

    LaunchedEffect(fileItem.uri) {
        isLoading = true
        report = SmartFileAssistant.analyze(context, fileItem)
        if (!fileItem.isDirectory && fileItem.size <= 32L * 1024L * 1024L) {
            hashes = SecurityEngine.computeAllHashes(context, fileItem.uri).getOrNull()
        }
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp)
                .testTag("smart_assistant_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Smart File Assistant", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(fileItem.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (report != null) {
                val r = report!!
                val badgeColor = when (r.securityReport.riskLevel) {
                    SecurityRiskLevel.SAFE -> MaterialTheme.colorScheme.primaryContainer
                    SecurityRiskLevel.CAUTION -> MaterialTheme.colorScheme.tertiaryContainer
                    SecurityRiskLevel.WARNING -> MaterialTheme.colorScheme.errorContainer
                }
                val badgeIcon = when (r.securityReport.riskLevel) {
                    SecurityRiskLevel.SAFE -> Icons.Default.CheckCircle
                    SecurityRiskLevel.CAUTION -> Icons.Default.Security
                    SecurityRiskLevel.WARNING -> Icons.Default.Warning
                }

                // Health & Risk Banner
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = badgeColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(badgeIcon, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(r.healthSummary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                }

                // Format Meaning & Signature
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Format Overview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(r.extensionExplanation, style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text("Magic Bytes: ${r.inspection.signatureMatch} (${r.inspection.magicBytesHex})", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                        Text("MIME: ${r.inspection.detectedMimeType}", style = MaterialTheme.typography.labelSmall)
                    }
                }

                // Security Warnings
                if (r.securityReport.warnings.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Security & Compatibility Alerts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            r.securityReport.warnings.forEach { warn ->
                                Text("• $warn", style = MaterialTheme.typography.bodySmall)
                            }
                            if (r.securityReport.dangerousPermissions.isNotEmpty()) {
                                Text(
                                    text = "Sensitive Permissions: ${r.securityReport.dangerousPermissions.joinToString { it.substringAfterLast('.') }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // Diagnostics: Why couldn't it open / Reader Advice
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Recommended Viewer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(r.recommendedViewerLabel, style = MaterialTheme.typography.bodySmall)
                        r.whyCannotOpenExplanation?.let { why ->
                            Spacer(Modifier.height(4.dp))
                            Text("Diagnostic Note: $why", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                // Suggested Conversions
                if (r.suggestedConversions.isNotEmpty()) {
                    Column {
                        Text("Conversion Options", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            r.suggestedConversions.forEach { target ->
                                AssistChip(
                                    onClick = {
                                        onDismiss()
                                        onOpenConverter?.invoke()
                                    },
                                    label = { Text("Convert → $target") },
                                    leadingIcon = { Icon(Icons.Default.Transform, null, modifier = Modifier.size(16.dp)) }
                                )
                            }
                        }
                    }
                }

                // Cryptographic Checksum Preview
                hashes?.let { h ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("SHA-256 Digest", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(h.sha256, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                // Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onDismiss()
                            onOpenInViewer(fileItem)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open Viewer")
                    }
                    if (!fileItem.isDirectory && onMoveToVault != null) {
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onMoveToVault(fileItem)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Encrypt in Vault")
                        }
                    }
                }
            }
        }
    }
}
