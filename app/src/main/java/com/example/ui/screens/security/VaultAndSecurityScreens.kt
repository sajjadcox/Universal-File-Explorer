package com.example.ui.screens.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.data.db.LocalDataRepository
import com.example.data.db.VaultEntryEntity
import com.example.domain.model.FileItem
import com.example.domain.model.StorageStats
import com.example.domain.security.AllHashesResult
import com.example.domain.security.SecurityEngine
import com.example.domain.security.VaultManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateVaultAndSecurityScreen(
    activity: FragmentActivity?,
    localRepo: LocalDataRepository,
    vaultPinHash: String,
    vaultPinSalt: String,
    biometricEnabled: Boolean,
    autoLockMinutes: Int,
    failedAttempts: Int,
    lockoutUntilMs: Long,
    isVaultUnlocked: Boolean,
    pendingImportFile: FileItem?,
    onUnlockVault: () -> Unit,
    onLockVault: () -> Unit,
    onTouchVaultActivity: () -> Unit,
    onSaveNewPin: (String, String) -> Unit,
    onRecordFailedAttempt: (Int, Long) -> Unit,
    onClearPendingImport: () -> Unit,
    onOpenFileInViewer: (FileItem) -> Unit,
    onMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(enabled = true) { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vaultManager = remember { VaultManager(context, localRepo) }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Private Vault", "Encrypt & Hash", "Privacy Audit")

    // Import dialog state
    var importCandidate by remember(pendingImportFile) { mutableStateOf(pendingImportFile) }
    var importFolder by remember { mutableStateOf("General") }
    var deleteOriginalOnImport by remember { mutableStateOf(false) }

    var activeFolderFilter by remember { mutableStateOf("All") }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var customFolders by remember { mutableStateOf(listOf("General", "Personal", "Documents", "Media")) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var confirmDeleteEntry by remember { mutableStateOf<VaultEntryEntity?>(null) }
    var pendingExportEntry by remember { mutableStateOf<VaultEntryEntity?>(null) }

    val vaultEntries by localRepo.vaultEntries.collectAsState(initial = emptyList())
    val activityFeed by localRepo.recentActivity.collectAsState(initial = emptyList())

    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = resolveDisplayName(context, uri)
            importCandidate = FileItem(
                uri = uri,
                name = name,
                path = uri.toString(),
                extension = name.substringAfterLast('.', ""),
                mimeType = context.contentResolver.getType(uri) ?: "*/*"
            )
        }
    }

    val exportTreePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        val entry = pendingExportEntry
        pendingExportEntry = null
        if (treeUri != null && entry != null) {
            scope.launch {
                vaultManager.exportEntryFromVault(entry, treeUri, removeFromVault = false)
                    .onSuccess { onMessage("Exported '$it' safely") }
                    .onFailure { onMessage("Export failed: ${it.message}") }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security Center & Vault", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("vault_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (isVaultUnlocked) {
                        IconButton(onClick = { showChangePinDialog = true }) {
                            Icon(Icons.Default.EnhancedEncryption, "Change PIN")
                        }
                        IconButton(onClick = {
                            vaultManager.clearSensitiveTempFiles()
                            onLockVault()
                            onMessage("Vault locked & temporary previews cleared")
                        }) {
                            Icon(Icons.Default.Lock, "Lock Vault Now")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("private_vault_screen")
        ) {
            SecondaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = {
                            onTouchVaultActivity()
                            selectedTab = idx
                        },
                        text = { Text(label, maxLines = 1) }
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    if (!isVaultUnlocked) {
                        VaultLockGate(
                            hasPinConfigured = vaultPinHash.isNotBlank() && vaultPinSalt.isNotBlank(),
                            biometricEnabled = biometricEnabled,
                            biometricAvailable = SecurityEngine.isBiometricAvailable(context),
                            failedAttempts = failedAttempts,
                            lockoutUntilMs = lockoutUntilMs,
                            onSetupPin = { pin ->
                                val salt = SecurityEngine.generateSaltBase64()
                                val hash = SecurityEngine.hashPin(pin, salt)
                                onSaveNewPin(hash, salt)
                                onUnlockVault()
                                onMessage("Vault PIN configured & unlocked")
                            },
                            onVerifyPin = { pin ->
                                val ok = SecurityEngine.verifyPin(pin, vaultPinHash, vaultPinSalt)
                                if (ok) {
                                    onRecordFailedAttempt(0, 0L)
                                    onUnlockVault()
                                    scope.launch { localRepo.logActivity("VAULT_UNLOCK", "Vault", "PIN", "Unlocked via PIN") }
                                } else {
                                    val nextFails = failedAttempts + 1
                                    val lockMs = SecurityEngine.computeLockoutDurationMs(nextFails)
                                    val until = if (lockMs > 0L) System.currentTimeMillis() + lockMs else 0L
                                    onRecordFailedAttempt(nextFails, until)
                                }
                                ok
                            },
                            onBiometricClick = {
                                if (activity != null && SecurityEngine.isBiometricAvailable(context)) {
                                    SecurityEngine.authenticateBiometric(
                                        activity = activity,
                                        onSuccess = {
                                            onRecordFailedAttempt(0, 0L)
                                            onUnlockVault()
                                            scope.launch { localRepo.logActivity("VAULT_UNLOCK", "Vault", "Biometric", "Unlocked via BiometricPrompt") }
                                        },
                                        onError = { err -> onMessage(err) }
                                    )
                                } else {
                                    onMessage("Biometric authentication unavailable on this device")
                                }
                            }
                        )
                    } else {
                        LaunchedEffect(vaultEntries) {
                            val dbFolders = vaultEntries.map { it.folderName }.distinct()
                            customFolders = (customFolders + dbFolders).distinct()
                        }
                        val filtered = remember(vaultEntries, activeFolderFilter) {
                            if (activeFolderFilter == "All") vaultEntries
                            else vaultEntries.filter { it.folderName == activeFolderFilter }
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        onTouchVaultActivity()
                                        importPicker.launch(arrayOf("*/*"))
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Import File")
                                }
                                OutlinedButton(onClick = { showCreateFolderDialog = true }) {
                                    Icon(Icons.Default.CreateNewFolder, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Folder")
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                (listOf("All") + customFolders).forEach { folder ->
                                    FilterChip(
                                        selected = activeFolderFilter == folder,
                                        onClick = {
                                            onTouchVaultActivity()
                                            activeFolderFilter = folder
                                        },
                                        label = { Text(folder) }
                                    )
                                }
                            }

                            if (filtered.isEmpty()) {
                                Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.LockOpen, null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.height(10.dp))
                                        Text("Vault folder is empty", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            "Files imported here are AES-256-GCM encrypted using Android Keystore & hidden via .nomedia.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(filtered, key = { it.id }) { entry ->
                                        Card(
                                            shape = RoundedCornerShape(14.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                onTouchVaultActivity()
                                                scope.launch {
                                                    vaultManager.decryptEntryToPreviewFile(entry).fold(
                                                        onSuccess = { decFile ->
                                                            onOpenFileInViewer(
                                                                FileItem(
                                                                    uri = Uri.fromFile(decFile),
                                                                    name = entry.originalName,
                                                                    path = decFile.absolutePath,
                                                                    extension = entry.originalName.substringAfterLast('.', ""),
                                                                    size = entry.sizeBytes,
                                                                    mimeType = entry.mimeType
                                                                )
                                                            )
                                                        },
                                                        onFailure = { onMessage("Decrypt failed: ${it.message}") }
                                                    )
                                                }
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary)
                                                Spacer(Modifier.width(12.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(entry.originalName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text(
                                                        "${entry.folderName} • ${StorageStats.formatBytes(entry.sizeBytes)} • ${if (entry.isHidden) "Hidden" else "Visible"}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                IconButton(onClick = {
                                                    scope.launch { localRepo.setVaultEntryHidden(entry.id, !entry.isHidden) }
                                                }) {
                                                    Icon(
                                                        if (entry.isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                        "Toggle Hide"
                                                    )
                                                }
                                                IconButton(onClick = {
                                                    val exportAction = {
                                                        pendingExportEntry = entry
                                                        exportTreePicker.launch(null)
                                                    }
                                                    if (activity != null && biometricEnabled && SecurityEngine.isBiometricAvailable(context)) {
                                                        SecurityEngine.authenticateBiometric(
                                                            activity = activity,
                                                            title = "Authenticate Export",
                                                            subtitle = "Verify identity before exporting decrypted file",
                                                            onSuccess = exportAction,
                                                            onError = { onMessage(it) }
                                                        )
                                                    } else {
                                                        exportAction()
                                                    }
                                                }) {
                                                    Icon(Icons.Default.FileUpload, "Export Decrypted")
                                                }
                                                IconButton(onClick = { confirmDeleteEntry = entry }) {
                                                    Icon(Icons.Default.DeleteForever, "Delete from Vault", tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> EncryptAndHashTab(onMessage = onMessage)

                2 -> PrivacyAuditTab(
                    vaultManager = vaultManager,
                    autoLockMinutes = autoLockMinutes,
                    activityFeed = activityFeed,
                    onClearActivity = {
                        scope.launch {
                            localRepo.clearActivityLog()
                            onMessage("Cleared activity log")
                        }
                    },
                    onMessage = onMessage
                )
            }
        }
    }

    importCandidate?.let { item ->
        AlertDialog(
            onDismissRequest = {
                importCandidate = null
                onClearPendingImport()
            },
            title = { Text("Encrypt into Private Vault", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("File: ${item.name} (${item.formattedSize})")
                    OutlinedTextField(
                        value = importFolder,
                        onValueChange = { importFolder = it },
                        label = { Text("Vault Folder") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = deleteOriginalOnImport, onCheckedChange = { deleteOriginalOnImport = it })
                        Text("Remove original file after encryption", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val delOrig = deleteOriginalOnImport
                    val folder = importFolder
                    importCandidate = null
                    onClearPendingImport()
                    scope.launch {
                        vaultManager.importFileIntoVault(item, folderName = folder, deleteOriginal = delOrig).fold(
                            onSuccess = { onMessage("Encrypted '${item.name}' into Vault/$folder") },
                            onFailure = { onMessage("Import error: ${it.message}") }
                        )
                    }
                }) { Text("Encrypt & Import") }
            },
            dismissButton = {
                TextButton(onClick = {
                    importCandidate = null
                    onClearPendingImport()
                }) { Text("Cancel") }
            }
        )
    }

    if (showCreateFolderDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("New Vault Folder") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Folder Name") }, singleLine = true)
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        val trimmed = name.trim()
                        customFolders = (customFolders + trimmed).distinct()
                        activeFolderFilter = trimmed
                    }
                    showCreateFolderDialog = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") } }
        )
    }

    if (showChangePinDialog) {
        var newPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showChangePinDialog = false },
            title = { Text("Set New Vault PIN") },
            text = {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 12) newPin = it },
                    label = { Text("New PIN (4-12 digits)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newPin.length >= 4) {
                        val salt = SecurityEngine.generateSaltBase64()
                        val hash = SecurityEngine.hashPin(newPin, salt)
                        onSaveNewPin(hash, salt)
                        showChangePinDialog = false
                        onMessage("Vault PIN updated")
                    } else onMessage("PIN must be at least 4 characters")
                }) { Text("Save PIN") }
            },
            dismissButton = { TextButton(onClick = { showChangePinDialog = false }) { Text("Cancel") } }
        )
    }

    confirmDeleteEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirmDeleteEntry = null },
            title = { Text("Delete from Vault?") },
            text = { Text("Permanently remove '${entry.originalName}' from Private Vault?") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDeleteEntry = null
                        scope.launch {
                            vaultManager.deleteVaultEntry(entry)
                            onMessage("Removed '${entry.originalName}' from Vault")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteEntry = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun VaultLockGate(
    hasPinConfigured: Boolean,
    biometricEnabled: Boolean,
    biometricAvailable: Boolean,
    failedAttempts: Int,
    lockoutUntilMs: Long,
    onSetupPin: (String) -> Unit,
    onVerifyPin: (String) -> Boolean,
    onBiometricClick: () -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(lockoutUntilMs) {
        while (lockoutUntilMs > System.currentTimeMillis()) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
        nowMs = System.currentTimeMillis()
    }
    val remainingSec = ((lockoutUntilMs - nowMs) / 1000L).coerceAtLeast(0L)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (hasPinConfigured) "Private Vault Locked" else "Create Vault PIN",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (hasPinConfigured) "Enter PIN or use Biometric authentication"
            else "Files stored here are AES-256-GCM encrypted using Android Keystore.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = pinInput,
            onValueChange = { if (it.length <= 12) pinInput = it; errorText = null },
            label = { Text(if (hasPinConfigured) "Enter PIN" else "New PIN (min 4 chars)") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("vault_pin_input")
        )
        if (!hasPinConfigured) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = confirmPinInput,
                onValueChange = { if (it.length <= 12) confirmPinInput = it; errorText = null },
                label = { Text("Confirm PIN") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (remainingSec > 0) {
            Spacer(Modifier.height(8.dp))
            Text("Too many failed attempts. Try again in ${remainingSec}s.", color = MaterialTheme.colorScheme.error)
        } else if (errorText != null) {
            Spacer(Modifier.height(8.dp))
            Text(errorText!!, color = MaterialTheme.colorScheme.error)
        } else if (failedAttempts > 0 && hasPinConfigured) {
            Spacer(Modifier.height(6.dp))
            Text("Failed attempts: $failedAttempts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (!hasPinConfigured) {
                    if (pinInput.length < 4) errorText = "PIN must be at least 4 characters"
                    else if (pinInput != confirmPinInput) errorText = "PINs do not match"
                    else onSetupPin(pinInput)
                } else {
                    if (!onVerifyPin(pinInput)) {
                        errorText = "Incorrect PIN"
                        pinInput = ""
                    }
                }
            },
            enabled = remainingSec == 0L,
            modifier = Modifier.fillMaxWidth().testTag("vault_unlock_button")
        ) {
            Text(if (hasPinConfigured) "Unlock Vault" else "Create PIN & Open Vault")
        }

        if (hasPinConfigured && biometricEnabled && biometricAvailable) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onBiometricClick,
                enabled = remainingSec == 0L,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Fingerprint, null)
                Spacer(Modifier.width(8.dp))
                Text("Unlock with Fingerprint / Face")
            }
        }
    }
}

@Composable
private fun EncryptAndHashTab(onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var pickedName by remember { mutableStateOf("No file selected") }
    var password by remember { mutableStateOf("") }
    var expectedHash by remember { mutableStateOf("") }
    var hashes by remember { mutableStateOf<AllHashesResult?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pickedUri = uri
            pickedName = resolveDisplayName(context, uri)
            hashes = null
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text("Pick File: $pickedName", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("AES-256-GCM File Encryption", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Encryption / Decryption Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val uri = pickedUri ?: return@Button
                            scope.launch {
                                isBusy = true
                                val outDir = File(context.filesDir, "EncryptedExports").apply { mkdirs() }
                                val outFile = File(outDir, "$pickedName.ufev")
                                context.contentResolver.openInputStream(uri)?.use { src ->
                                    SecurityEngine.encryptFileWithPassword(src, outFile, password)
                                        .onSuccess { onMessage("Encrypted → App Storage/EncryptedExports/${it.name}") }
                                        .onFailure { onMessage("Encrypt error: ${it.message}") }
                                }
                                isBusy = false
                            }
                        },
                        enabled = pickedUri != null && password.isNotBlank() && !isBusy,
                        modifier = Modifier.weight(1f)
                    ) { Text("Encrypt (.ufev)") }

                    OutlinedButton(
                        onClick = {
                            val uri = pickedUri ?: return@OutlinedButton
                            scope.launch {
                                isBusy = true
                                val tmp = File(context.cacheDir, "tmp_dec_${System.currentTimeMillis()}.ufev")
                                context.contentResolver.openInputStream(uri)?.use { src ->
                                    tmp.outputStream().use { src.copyTo(it) }
                                }
                                val outDir = File(context.filesDir, "EncryptedExports").apply { mkdirs() }
                                val cleanName = pickedName.removeSuffix(".ufev").ifBlank { "decrypted.bin" }
                                SecurityEngine.decryptFileWithPassword(tmp, File(outDir, cleanName), password)
                                    .onSuccess { onMessage("Decrypted → App Storage/EncryptedExports/${it.name}") }
                                    .onFailure { onMessage("Decrypt failed (wrong password or corrupt file)") }
                                tmp.delete()
                                isBusy = false
                            }
                        },
                        enabled = pickedUri != null && password.isNotBlank() && !isBusy,
                        modifier = Modifier.weight(1f)
                    ) { Text("Decrypt") }
                }
            }
        }

        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("File Integrity & Checksum Verifier", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Button(
                    onClick = {
                        val uri = pickedUri ?: return@Button
                        scope.launch {
                            isBusy = true
                            hashes = SecurityEngine.computeAllHashes(context, uri).getOrNull()
                            isBusy = false
                        }
                    },
                    enabled = pickedUri != null && !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    else Text("Compute MD5, SHA-1, SHA-256 & SHA-512")
                }

                hashes?.let { h ->
                    HashLine("MD5", h.md5, context)
                    HashLine("SHA-1", h.sha1, context)
                    HashLine("SHA-256", h.sha256, context)
                    HashLine("SHA-512", h.sha512, context)

                    OutlinedTextField(
                        value = expectedHash,
                        onValueChange = { expectedHash = it },
                        label = { Text("Compare against expected hash...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (expectedHash.isNotBlank()) {
                        val match = listOf(h.md5, h.sha1, h.sha256, h.sha512).any {
                            SecurityEngine.verifyChecksum(it, expectedHash)
                        }
                        Surface(
                            color = if (match) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (match) "MATCH CONFIRMED: File integrity verified" else "MISMATCH: Checksum does not match")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HashLine(label: String, value: String, context: Context) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(value, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, maxLines = 2)
        }
        IconButton(onClick = {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(label, value))
        }) {
            Icon(Icons.Default.ContentCopy, "Copy $label", modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun PrivacyAuditTab(
    vaultManager: VaultManager,
    autoLockMinutes: Int,
    activityFeed: List<com.example.data.db.FileActivityEntity>,
    onClearActivity: () -> Unit,
    onMessage: (String) -> Unit
) {
    var vaultBytes by remember { mutableStateOf(vaultManager.getVaultStorageBytes()) }
    var tempBytes by remember { mutableStateOf(vaultManager.getSensitiveTempBytes()) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Privacy Dashboard", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Encrypted Vault Usage: ${StorageStats.formatBytes(vaultBytes)}", style = MaterialTheme.typography.bodySmall)
                    Text("Sensitive Temp Previews: ${StorageStats.formatBytes(tempBytes)}", style = MaterialTheme.typography.bodySmall)
                    Text("Auto-Lock Timeout: ${if (autoLockMinutes == 0) "Disabled" else "$autoLockMinutes min"}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = {
                        val cleaned = vaultManager.clearSensitiveTempFiles()
                        vaultBytes = vaultManager.getVaultStorageBytes()
                        tempBytes = vaultManager.getSensitiveTempBytes()
                        onMessage("Cleared $cleaned temporary decrypted preview file(s)")
                    }) {
                        Icon(Icons.Default.CleaningServices, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Clear Sensitive Temp Files")
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Android Flash Storage Advisory", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Android uses NAND/UFS flash storage with wear-leveling and copy-on-write layers. No app can guarantee physical sector erasure via file overwrite alone. Protect sensitive files by encrypting them inside the Private Vault before storing.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Security & File Access History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (activityFeed.isNotEmpty()) {
                    TextButton(onClick = onClearActivity) { Text("Clear Log") }
                }
            }
        }

        items(activityFeed, key = { it.id }) { act ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("${act.action} • ${act.fileName}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    if (act.details.isNotBlank()) Text(act.details, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun resolveDisplayName(context: Context, uri: Uri): String {
    try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: "file"
        }
    } catch (_: Exception) {}
    return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
}
