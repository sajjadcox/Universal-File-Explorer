package com.example.ui.screens.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.storage.Phase2FileOperations
import com.example.domain.model.AccentColor
import com.example.domain.model.FileOpenMode
import com.example.domain.model.ReaderFontChoice
import com.example.domain.model.ReaderTheme
import com.example.domain.model.SortConfig
import com.example.domain.model.SortField
import com.example.domain.model.SortOrder
import com.example.domain.model.StorageStats
import com.example.domain.model.ThemeMode
import com.example.domain.model.ViewMode
import com.example.ui.components.LicensesDialog
import com.example.ui.i18n.LocalAppStrings
import com.example.viewmodel.FileManagerUiState

@Composable
fun SettingsScreen(
    uiState: FileManagerUiState,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetAccentColor: (AccentColor) -> Unit = {},
    onSetViewMode: (ViewMode) -> Unit,
    onSetSortConfig: (SortConfig) -> Unit,
    onSetShowHiddenFiles: (Boolean) -> Unit,
    onSetThumbnailsEnabled: (Boolean) -> Unit = {},
    onSetFileOpenMode: (FileOpenMode) -> Unit = {},
    onSetAppLanguage: (String) -> Unit,
    onSetReaderTheme: (ReaderTheme) -> Unit = {},
    onSetReaderFont: (ReaderFontChoice) -> Unit = {},
    onSetReaderKeepAwake: (Boolean) -> Unit = {},
    onSetPlaybackResumeEnabled: (Boolean) -> Unit = {},
    onSetVaultBiometricEnabled: (Boolean) -> Unit = {},
    onSetVaultAutoLockMinutes: (Int) -> Unit = {},
    onSetTrashRetentionDays: (Int) -> Unit = {},
    onSetLogFileActivity: (Boolean) -> Unit = {},
    onOpenVault: () -> Unit = {},
    onMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current

    var showLicensesDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showCompatDialog by remember { mutableStateOf(false) }
    var showSortPrefDialog by remember { mutableStateOf(false) }
    var showFileOpenModeDialog by remember { mutableStateOf(false) }
    var showTrashRetentionDialog by remember { mutableStateOf(false) }
    var showAutoLockDialog by remember { mutableStateOf(false) }
    var cacheRefreshTrigger by remember { mutableIntStateOf(0) }

    val cacheSummary = remember(cacheRefreshTrigger) {
        Phase2FileOperations.computeCacheSummary(context)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                Text(
                    text = strings.settingsTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = strings.settingsSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Appearance Section
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = strings.appearanceSection,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Theme selector chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeChip(
                            title = "Light",
                            isSelected = uiState.themeMode == ThemeMode.LIGHT,
                            onClick = { onSetThemeMode(ThemeMode.LIGHT) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeChip(
                            title = "Dark",
                            isSelected = uiState.themeMode == ThemeMode.DARK,
                            onClick = { onSetThemeMode(ThemeMode.DARK) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeChip(
                            title = "AMOLED",
                            isSelected = uiState.themeMode == ThemeMode.AMOLED,
                            onClick = { onSetThemeMode(ThemeMode.AMOLED) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeChip(
                            title = "System",
                            isSelected = uiState.themeMode == ThemeMode.SYSTEM,
                            onClick = { onSetThemeMode(ThemeMode.SYSTEM) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "${strings.accentColorLabel}${if (uiState.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) " (Used when Dynamic Color is Off)" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AccentColor.entries.forEach { accent ->
                            val selected = uiState.accentColor == accent
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(accent.primaryHex))
                                    .then(
                                        if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        else Modifier
                                    )
                                    .clickable {
                                        onSetAccentColor(accent)
                                        if (uiState.dynamicColor) {
                                            onSetDynamicColor(false)
                                        }
                                    }
                                    .testTag("accent_swatch_${accent.name.lowercase()}"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(Icons.Default.Check, accent.label, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Dynamic Color Toggle
                    SettingSwitchRow(
                        icon = Icons.Default.Palette,
                        title = strings.dynamicColorTitle,
                        subtitle = strings.dynamicColorDesc,
                        checked = uiState.dynamicColor,
                        onCheckedChange = onSetDynamicColor,
                        testTag = "switch_dynamic_color"
                    )
                }
            }
        }

        // File Browser & Language Preferences
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = strings.browserPrefsSection,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (uiState.viewMode == ViewMode.LIST) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = strings.defaultView,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (uiState.viewMode == ViewMode.LIST) "List" else "Grid",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = uiState.viewMode == ViewMode.LIST,
                                onClick = { onSetViewMode(ViewMode.LIST) },
                                label = { Text("List") }
                            )
                            FilterChip(
                                selected = uiState.viewMode == ViewMode.GRID,
                                onClick = { onSetViewMode(ViewMode.GRID) },
                                label = { Text("Grid") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))

                    SettingClickableRow(
                        icon = Icons.Default.Sort,
                        title = strings.defaultSort,
                        subtitle = "${uiState.sortConfig.field.name} (${if (uiState.sortConfig.order == SortOrder.ASCENDING) "Ascending" else "Descending"})",
                        onClick = { showSortPrefDialog = true }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))

                    SettingClickableRow(
                        icon = Icons.Default.OpenInNew,
                        title = strings.fileOpenModeLabel,
                        subtitle = uiState.fileOpenMode.label,
                        onClick = { showFileOpenModeDialog = true }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))

                    SettingSwitchRow(
                        icon = Icons.Default.Visibility,
                        title = strings.showHidden,
                        subtitle = strings.showHiddenDesc,
                        checked = uiState.showHiddenFiles,
                        onCheckedChange = onSetShowHiddenFiles,
                        testTag = "switch_show_hidden"
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))

                    SettingSwitchRow(
                        icon = Icons.Default.Image,
                        title = strings.showThumbnails,
                        subtitle = strings.showThumbnailsDesc,
                        checked = uiState.thumbnailsEnabled,
                        onCheckedChange = onSetThumbnailsEnabled,
                        testTag = "switch_thumbnails"
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))

                    val langName = when (uiState.appLanguage) {
                        "bn" -> "বাংলা (Bengali)"
                        "es" -> "Español (Spanish)"
                        "fr" -> "Français (French)"
                        "de" -> "Deutsch (German)"
                        "ja" -> "日本語 (Japanese)"
                        "zh" -> "中文 (Chinese)"
                        else -> "English (Default)"
                    }
                    SettingClickableRow(
                        icon = Icons.Default.Language,
                        title = strings.languageLabel,
                        subtitle = langName,
                        onClick = { showLanguageDialog = true },
                        modifier = Modifier.testTag("setting_language_row")
                    )
                }
            }
        }

        // Reading & Media Playback Section
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = strings.readingMediaSection,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoStories, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.readerThemeLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReaderTheme.entries.forEach { rt ->
                            FilterChip(
                                selected = uiState.readerTheme == rt,
                                onClick = { onSetReaderTheme(rt) },
                                label = { Text(rt.label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FontDownload, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(strings.readerFontLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReaderFontChoice.entries.forEach { rf ->
                            FilterChip(
                                selected = uiState.readerFont == rf,
                                onClick = { onSetReaderFont(rf) },
                                label = { Text(rf.label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))

                    SettingSwitchRow(
                        icon = Icons.Default.LightMode,
                        title = strings.keepScreenAwakeLabel,
                        subtitle = "Prevent screen sleep in PDF, Word & EPUB viewers",
                        checked = uiState.readerKeepAwake,
                        onCheckedChange = onSetReaderKeepAwake,
                        testTag = "switch_keep_awake"
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))

                    SettingSwitchRow(
                        icon = Icons.Default.PlayCircle,
                        title = strings.resumeMediaLabel,
                        subtitle = "Remember playback timestamp across sessions",
                        checked = uiState.playbackResumeEnabled,
                        onCheckedChange = onSetPlaybackResumeEnabled,
                        testTag = "switch_playback_resume"
                    )
                }
            }
        }

        // Security, Vault, Trash & Cache Cleanup
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = strings.vaultPrivacySection,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SettingClickableRow(
                        icon = Icons.Default.EnhancedEncryption,
                        title = strings.openVaultLabel,
                        subtitle = if (uiState.vaultPinHash.isBlank()) "PIN not configured yet" else if (uiState.isVaultUnlocked) "Unlocked" else "Locked with PIN & AES-256-GCM",
                        onClick = onOpenVault
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))

                    SettingSwitchRow(
                        icon = Icons.Default.Fingerprint,
                        title = strings.biometricUnlockLabel,
                        subtitle = "Allow BiometricPrompt fingerprint/face unlock",
                        checked = uiState.vaultBiometricEnabled,
                        onCheckedChange = onSetVaultBiometricEnabled,
                        testTag = "switch_vault_biometric"
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))

                    val autoLockLabel = when (uiState.vaultAutoLockMinutes) {
                        0 -> "Immediately when app backgrounds"
                        else -> "After ${uiState.vaultAutoLockMinutes} minute(s)"
                    }
                    SettingClickableRow(
                        icon = Icons.Default.Timer,
                        title = "Vault Auto-Lock Timeout",
                        subtitle = autoLockLabel,
                        onClick = { showAutoLockDialog = true }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))

                    val retentionLabel = when (uiState.trashRetentionDays) {
                        -1 -> "Never auto-delete"
                        else -> "Auto-delete after ${uiState.trashRetentionDays} days"
                    }
                    SettingClickableRow(
                        icon = Icons.Default.DeleteSweep,
                        title = strings.trashRetentionLabel,
                        subtitle = retentionLabel,
                        onClick = { showTrashRetentionDialog = true }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))

                    SettingSwitchRow(
                        icon = Icons.Default.VerifiedUser,
                        title = "Record File Access & Operation History",
                        subtitle = "Store local audit trail for timeline & privacy dashboard",
                        checked = uiState.logFileActivity,
                        onCheckedChange = onSetLogFileActivity,
                        testTag = "switch_log_activity"
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.CleaningServices, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(strings.cacheStorageLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                val totalBytes = cacheSummary.appCacheBytes + cacheSummary.viewerTempBytes + cacheSummary.vaultTempBytes
                                Text(
                                    text = "Total: ${StorageStats.formatBytes(totalBytes)} (Vault temp: ${StorageStats.formatBytes(cacheSummary.vaultTempBytes)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedButton(onClick = {
                            val freed = Phase2FileOperations.clearAppCaches(context, includeVaultTemp = true)
                            cacheRefreshTrigger++
                            onMessage("Cleared ${StorageStats.formatBytes(freed)} from app cache & sensitive temp files")
                        }) {
                            Text(strings.clearCacheBtn)
                        }
                    }
                }
            }
        }

        // About, Compatibility & Legal
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = strings.aboutSection,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    SettingClickableRow(
                        icon = Icons.Default.Info,
                        title = strings.appInfoTitle,
                        subtitle = "Universal File Explorer v2.0.0 (Phase 2 Production)",
                        onClick = { }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))

                    SettingClickableRow(
                        icon = Icons.Default.VerifiedUser,
                        title = strings.permissionsCompatTitle,
                        subtitle = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) • SAF + Scoped Storage",
                        onClick = { showCompatDialog = true }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))

                    SettingClickableRow(
                        icon = Icons.Default.Security,
                        title = strings.privacyPolicyTitle,
                        subtitle = "100% offline-first, AES-256-GCM local vault, zero telemetry",
                        onClick = { showPrivacyDialog = true }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))

                    SettingClickableRow(
                        icon = Icons.Default.Lock,
                        title = strings.licensesTitle,
                        subtitle = "Apache 2.0, MIT & BSD open-source notices",
                        onClick = { showLicensesDialog = true }
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(84.dp))
        }
    }

    if (showLicensesDialog) {
        LicensesDialog(onDismiss = { showLicensesDialog = false })
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text(text = strings.privacyPolicyTitle, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Universal File Explorer File Manager is 100% offline-first and privacy-respecting:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Zero analytics, crash telemetry, ads, or remote cloud servers.\n• Private Vault encrypts files using AES-256-GCM (Android Keystore + PBKDF2-HMAC-SHA256 PIN).\n• Flash storage advisory: Android flash controllers use wear-leveling, so overwriting/deleting files removes filesystem links and temp decrypts rather than guaranteeing physical NAND block erasure.\n• File access follows Android Storage Access Framework (SAF) & Scoped Storage permissions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text(strings.close) }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showCompatDialog) {
        AlertDialog(
            onDismissRequest = { showCompatDialog = false },
            title = { Text(text = strings.permissionsCompatTitle, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Device API: ${Build.VERSION.SDK_INT} (Android ${Build.VERSION.RELEASE})\nMin SDK: Android 9 (API 28) • Target SDK: 36\n\nPermissions Requested:\n• Media Read (READ_MEDIA_IMAGES/VIDEO/AUDIO on Android 13+ or READ_EXTERNAL_STORAGE on Android 9–12)\n• SAF Document Tree for SD cards, USB OTG & external directories (zero broad MANAGE_EXTERNAL_STORAGE needed)\n• Camera (QR Scanner only)\n• Biometric (BiometricPrompt Private Vault unlock)\n• Notifications & Media Playback Foreground Service (Audio Player queue)",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                TextButton(onClick = { showCompatDialog = false }) { Text(strings.close) }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showLanguageDialog) {
        val languages = listOf(
            "en" to "English (Default)",
            "bn" to "বাংলা (Bengali)",
            "es" to "Español (Spanish)",
            "fr" to "Français (French)",
            "de" to "Deutsch (German)",
            "ja" to "日本語 (Japanese)",
            "zh" to "中文 (Chinese)"
        )
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(strings.languageLabel, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    languages.forEach { (code, name) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetAppLanguage(code)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = uiState.appLanguage == code,
                                onClick = {
                                    onSetAppLanguage(code)
                                    showLanguageDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text(strings.cancel) }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showSortPrefDialog) {
        AlertDialog(
            onDismissRequest = { showSortPrefDialog = false },
            title = { Text(strings.defaultSort, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf(
                        SortConfig(SortField.NAME, SortOrder.ASCENDING) to "Name (A to Z)",
                        SortConfig(SortField.NAME, SortOrder.DESCENDING) to "Name (Z to A)",
                        SortConfig(SortField.DATE, SortOrder.DESCENDING) to "Date (Newest first)",
                        SortConfig(SortField.DATE, SortOrder.ASCENDING) to "Date (Oldest first)",
                        SortConfig(SortField.SIZE, SortOrder.DESCENDING) to "Size (Largest first)",
                        SortConfig(SortField.SIZE, SortOrder.ASCENDING) to "Size (Smallest first)"
                    ).forEach { (config, label) ->
                        val isSelected = uiState.sortConfig.field == config.field && uiState.sortConfig.order == config.order
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetSortConfig(config)
                                    showSortPrefDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onSetSortConfig(config)
                                    showSortPrefDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortPrefDialog = false }) { Text(strings.close) }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showFileOpenModeDialog) {
        AlertDialog(
            onDismissRequest = { showFileOpenModeDialog = false },
            title = { Text(strings.fileOpenModeLabel, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    FileOpenMode.entries.forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetFileOpenMode(mode)
                                    showFileOpenModeDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = uiState.fileOpenMode == mode,
                                onClick = {
                                    onSetFileOpenMode(mode)
                                    showFileOpenModeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(mode.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFileOpenModeDialog = false }) { Text(strings.close) }
            }
        )
    }

    if (showAutoLockDialog) {
        AlertDialog(
            onDismissRequest = { showAutoLockDialog = false },
            title = { Text("Vault Auto-Lock Timeout", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf(0 to "Immediate", 1 to "1 minute", 5 to "5 minutes", 15 to "15 minutes").forEach { (mins, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetVaultAutoLockMinutes(mins)
                                    showAutoLockDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(selected = uiState.vaultAutoLockMinutes == mins, onClick = {
                                onSetVaultAutoLockMinutes(mins)
                                showAutoLockDialog = false
                            })
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAutoLockDialog = false }) { Text(strings.close) } }
        )
    }

    if (showTrashRetentionDialog) {
        AlertDialog(
            onDismissRequest = { showTrashRetentionDialog = false },
            title = { Text(strings.trashRetentionLabel, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf(7 to "7 days", 30 to "30 days (Default)", -1 to "Never auto-delete").forEach { (days, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetTrashRetentionDays(days)
                                    showTrashRetentionDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(selected = uiState.trashRetentionDays == days, onClick = {
                                onSetTrashRetentionDays(days)
                                showTrashRetentionDialog = false
                            })
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTrashRetentionDialog = false }) { Text(strings.close) } }
        )
    }
}

@Composable
private fun ThemeChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(title, maxLines = 1) },
        modifier = modifier
    )
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun SettingClickableRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}
