package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.FileItem
import com.example.domain.model.FileOpenMode
import com.example.domain.security.VaultManager
import com.example.media.AudioPlayerManager
import com.example.ui.components.CreateFileDialog
import com.example.ui.components.CreateFolderDialog
import com.example.ui.components.FileDetailBottomSheet
import com.example.ui.components.RenameDialog
import com.example.ui.components.SmartAssistantBottomSheet
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.stringsForLanguage
import com.example.ui.navigation.AppBottomNavigationBar
import com.example.ui.navigation.AppNavigationRail
import com.example.ui.navigation.NavigationDestination
import com.example.ui.screens.browse.BrowseScreen
import com.example.ui.screens.categories.CategoriesScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.search.GlobalSearchScreen
import com.example.ui.screens.security.PrivateVaultAndSecurityScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.tools.BatchOperationsScreen
import com.example.ui.screens.tools.FileConverterScreen
import com.example.ui.screens.tools.FileManagementToolsScreen
import com.example.ui.screens.tools.InstalledAppsScreen
import com.example.ui.screens.tools.OcrAndTextToolsScreen
import com.example.ui.screens.tools.QrScannerAndGeneratorScreen
import com.example.ui.screens.tools.StorageAnalyzerScreen
import com.example.ui.screens.tools.ToolRoute
import com.example.ui.screens.tools.ToolsScreen
import com.example.ui.screens.viewers.UniversalViewerHost
import com.example.ui.theme.UniversalFileManagerTheme
import com.example.viewmodel.FileManagerViewModel
import java.io.File

class MainActivity : FragmentActivity() {

    private val viewModel: FileManagerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val snackbarHostState = remember { SnackbarHostState() }
            var currentTab by remember { mutableStateOf(NavigationDestination.HOME) }
            var activeToolRoute by remember { mutableStateOf<ToolRoute?>(null) }
            var batchInitialFiles by remember { mutableStateOf<List<FileItem>>(emptyList()) }
            var showGlobalSearch by remember { mutableStateOf(false) }
            var smartAnalyzeTarget by remember { mutableStateOf<FileItem?>(null) }
            var pendingVaultImport by remember { mutableStateOf<FileItem?>(null) }

            val audioPlayerManager = remember { AudioPlayerManager(context.applicationContext) }
            val audioState by audioPlayerManager.state.collectAsStateWithLifecycle()

            DisposableEffect(Unit) {
                audioPlayerManager.connect()
                onDispose { audioPlayerManager.release() }
            }

            // Runtime permissions (media permissions + optional Android 13+ notification for Media3 playback)
            val permissionsToRequest = remember {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_AUDIO,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                } else {
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { perms ->
                val storageGranted = perms.entries
                    .filter { it.key != Manifest.permission.POST_NOTIFICATIONS }
                    .all { it.value }
                viewModel.setStoragePermissionsGranted(storageGranted)
            }

            LaunchedEffect(Unit) {
                val allGranted = permissionsToRequest.all {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                }
                val storageOnlyGranted = permissionsToRequest
                    .filter { it != Manifest.permission.POST_NOTIFICATIONS }
                    .all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
                viewModel.setStoragePermissionsGranted(storageOnlyGranted)
                if (!allGranted) {
                    permissionLauncher.launch(permissionsToRequest)
                }
            }

            LaunchedEffect(uiState.userMessage) {
                uiState.userMessage?.let { msg ->
                    snackbarHostState.showSnackbar(msg)
                    viewModel.dismissUserMessage()
                }
            }

            BackHandler(enabled = true) {
                when {
                    smartAnalyzeTarget != null -> smartAnalyzeTarget = null
                    uiState.activeViewerFile != null -> viewModel.closeViewer()
                    showGlobalSearch -> showGlobalSearch = false
                    activeToolRoute != null -> activeToolRoute = null
                    uiState.isMultiSelecting -> viewModel.clearSelection()
                    currentTab == NavigationDestination.BROWSE && viewModel.navigateBackInBrowse() -> {}
                    currentTab != NavigationDestination.HOME -> currentTab = NavigationDestination.HOME
                    else -> finish()
                }
            }

            val openByPolicy: (FileItem, List<FileItem>) -> Unit = { file, ctxList ->
                when (uiState.fileOpenMode) {
                    FileOpenMode.UNIVERSAL_VIEWER -> viewModel.openFileInViewer(file, ctxList)
                    FileOpenMode.SMART_ASSISTANT -> smartAnalyzeTarget = file
                    FileOpenMode.EXTERNAL_APP -> {
                        if (!openExternally(context, file)) {
                            viewModel.openFileInViewer(file, ctxList)
                        }
                    }
                }
            }

            val triggerMoveToVault: (FileItem) -> Unit = { file ->
                if (file.isDirectory) {
                    viewModel.showUserMessage("Only files can be encrypted inside Private Vault")
                } else {
                    pendingVaultImport = file
                    activeToolRoute = ToolRoute.PRIVATE_VAULT
                }
            }

            val appStrings = remember(uiState.appLanguage) { stringsForLanguage(uiState.appLanguage) }

            CompositionLocalProvider(LocalAppStrings provides appStrings) {
                UniversalFileManagerTheme(
                    themeMode = uiState.themeMode,
                    dynamicColor = uiState.dynamicColor,
                    accentColor = uiState.accentColor
                ) {
                    val activeViewerFile = uiState.activeViewerFile
                    when {
                        activeViewerFile != null -> {
                            UniversalViewerHost(
                                fileItem = activeViewerFile,
                                galleryOrQueueContext = uiState.activeViewerContext,
                                localRepo = viewModel.localRepo,
                                audioPlayerManager = audioPlayerManager,
                                onBack = { viewModel.closeViewer() },
                                onShare = { item -> shareFile(context, item) },
                                onMessage = { msg -> viewModel.showUserMessage(msg) },
                                readerTheme = uiState.readerTheme,
                                readerFont = uiState.readerFont,
                                keepAwakeDefault = uiState.readerKeepAwake,
                                playbackResumeEnabled = uiState.playbackResumeEnabled,
                                onSmartAnalyze = { item -> smartAnalyzeTarget = item }
                            )
                        }

                        showGlobalSearch -> {
                            GlobalSearchScreen(
                                initialQuery = uiState.searchQuery,
                                localRepo = viewModel.localRepo,
                                onOpenFile = { item -> openByPolicy(item, emptyList()) },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onShowDetails = viewModel::showFileDetails,
                                onRenameFile = viewModel::setActiveRenameItem,
                                onDeleteFile = viewModel::deleteFile,
                                onShareFile = { item -> shareFile(context, item) },
                                onBack = { showGlobalSearch = false }
                            )
                        }

                        activeToolRoute != null -> {
                            when (activeToolRoute!!) {
                                ToolRoute.STORAGE_ANALYZER -> StorageAnalyzerScreen(
                                    storageStats = uiState.storageStats,
                                    breakdown = uiState.storageBreakdown,
                                    currentTreeUri = uiState.currentTreeUri,
                                    localRepo = viewModel.localRepo,
                                    onOpenFile = { item -> openByPolicy(item, emptyList()) },
                                    onCategoryClick = { cat ->
                                        activeToolRoute = null
                                        viewModel.loadCategoryFiles(cat)
                                        currentTab = NavigationDestination.CATEGORIES
                                    },
                                    onMessage = viewModel::showUserMessage,
                                    onBack = {
                                        activeToolRoute = null
                                        viewModel.refreshStorageStats()
                                    }
                                )
                                ToolRoute.FILE_CONVERTER -> FileConverterScreen(
                                    conversionEngine = viewModel.conversionEngine,
                                    localRepo = viewModel.localRepo,
                                    onOpenFile = { item -> viewModel.openFileInViewer(item) },
                                    onMessage = viewModel::showUserMessage,
                                    onBack = { activeToolRoute = null }
                                )
                                ToolRoute.QR_STUDIO -> QrScannerAndGeneratorScreen(
                                    onMessage = viewModel::showUserMessage,
                                    onBack = { activeToolRoute = null }
                                )
                                ToolRoute.OCR_TEXT_TOOLS -> OcrAndTextToolsScreen(
                                    initialSection = 0,
                                    onMessage = viewModel::showUserMessage,
                                    onBack = { activeToolRoute = null }
                                )
                                ToolRoute.DIFF_TOOLS -> OcrAndTextToolsScreen(
                                    initialSection = 1,
                                    onMessage = viewModel::showUserMessage,
                                    onBack = { activeToolRoute = null }
                                )
                                ToolRoute.SPLIT_MERGE -> OcrAndTextToolsScreen(
                                    initialSection = 3,
                                    onMessage = viewModel::showUserMessage,
                                    onBack = { activeToolRoute = null }
                                )
                                ToolRoute.BATCH_TOOLS -> BatchOperationsScreen(
                                    initialFiles = batchInitialFiles,
                                    conversionEngine = viewModel.conversionEngine,
                                    onMessage = viewModel::showUserMessage,
                                    onRefreshBrowse = viewModel::refreshCurrentFolder,
                                    onBack = {
                                        batchInitialFiles = emptyList()
                                        activeToolRoute = null
                                    }
                                )
                                ToolRoute.INSTALLED_APPS -> InstalledAppsScreen(
                                    onInspectApk = { apkItem -> viewModel.openFileInViewer(apkItem) },
                                    onBack = { activeToolRoute = null }
                                )
                                ToolRoute.PRIVATE_VAULT -> PrivateVaultAndSecurityScreen(
                                    activity = this@MainActivity,
                                    localRepo = viewModel.localRepo,
                                    vaultPinHash = uiState.vaultPinHash,
                                    vaultPinSalt = uiState.vaultPinSalt,
                                    biometricEnabled = uiState.vaultBiometricEnabled,
                                    autoLockMinutes = uiState.vaultAutoLockMinutes,
                                    failedAttempts = uiState.vaultFailedAttempts,
                                    lockoutUntilMs = uiState.vaultLockoutUntilMs,
                                    isVaultUnlocked = uiState.isVaultUnlocked,
                                    pendingImportFile = pendingVaultImport,
                                    onUnlockVault = viewModel::unlockVault,
                                    onLockVault = viewModel::lockVault,
                                    onTouchVaultActivity = viewModel::touchVaultActivity,
                                    onSaveNewPin = viewModel::saveVaultPin,
                                    onRecordFailedAttempt = viewModel::recordVaultFailedAttempt,
                                    onClearPendingImport = { pendingVaultImport = null },
                                    onOpenFileInViewer = { item -> viewModel.openFileInViewer(item) },
                                    onMessage = viewModel::showUserMessage,
                                    onBack = {
                                        pendingVaultImport = null
                                        activeToolRoute = null
                                    }
                                )
                                ToolRoute.ADVANCED_MANAGEMENT -> FileManagementToolsScreen(
                                    localRepo = viewModel.localRepo,
                                    initialTab = 0,
                                    onOpenFile = { item -> viewModel.openFileInViewer(item) },
                                    onMessage = viewModel::showUserMessage,
                                    onBack = { activeToolRoute = null }
                                )
                            }
                        }

                        else -> {
                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val isWideLayout = maxWidth >= 600.dp

                                Row(modifier = Modifier.fillMaxSize()) {
                                    if (isWideLayout) {
                                        AppNavigationRail(
                                            currentDestination = currentTab,
                                            onNavigateToDestination = { dest -> currentTab = dest }
                                        )
                                    }

                                    Scaffold(
                                        modifier = Modifier.weight(1f),
                                        contentWindowInsets = WindowInsets.navigationBars,
                                        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                                        bottomBar = {
                                            Column {
                                                if (audioState.currentUri != null) {
                                                    MiniAudioPlayerBar(
                                                        title = audioState.currentTitle,
                                                        isPlaying = audioState.isPlaying,
                                                        onTogglePlay = { audioPlayerManager.togglePlayPause() },
                                                        onNext = { audioPlayerManager.seekNext() },
                                                        onClick = {
                                                            val uri = audioState.currentUri ?: return@MiniAudioPlayerBar
                                                            val item = audioState.queue.getOrNull(audioState.currentIndex)
                                                                ?: FileItem(
                                                                    uri = uri,
                                                                    name = audioState.currentTitle.ifBlank { "Audio" },
                                                                    path = uri.toString(),
                                                                    mimeType = "audio/*"
                                                                )
                                                            viewModel.openFileInViewer(item, audioState.queue)
                                                        }
                                                    )
                                                }
                                                if (!isWideLayout) {
                                                    AppBottomNavigationBar(
                                                        currentDestination = currentTab,
                                                        onNavigateToDestination = { dest -> currentTab = dest }
                                                    )
                                                }
                                            }
                                        }
                                    ) { innerPadding ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(innerPadding)
                                        ) {
                                            when (currentTab) {
                                                NavigationDestination.HOME -> {
                                                    HomeScreen(
                                                        uiState = uiState,
                                                        onOpenGlobalSearch = { showGlobalSearch = true },
                                                        onCategoryClick = { cat ->
                                                            viewModel.loadCategoryFiles(cat)
                                                            currentTab = NavigationDestination.CATEGORIES
                                                        },
                                                        onNavigateToBrowse = { currentTab = NavigationDestination.BROWSE },
                                                        onNavigateToTools = { currentTab = NavigationDestination.TOOLS },
                                                        onOpenTool = { route -> activeToolRoute = route },
                                                        onNavigateToHashTool = { currentTab = NavigationDestination.TOOLS },
                                                        onFileClick = { file -> openByPolicy(file, uiState.recentFiles) },
                                                        onFileLongClick = { file ->
                                                            currentTab = NavigationDestination.BROWSE
                                                            viewModel.toggleSelection(file.uri)
                                                        },
                                                        onToggleFavorite = viewModel::toggleFavorite,
                                                        onShowFileDetails = viewModel::showFileDetails,
                                                        onRenameFile = viewModel::setActiveRenameItem,
                                                        onDeleteFile = viewModel::deleteFile,
                                                        onShareFile = { file -> shareFile(context, file) },
                                                        onSmartAnalyze = { file -> smartAnalyzeTarget = file },
                                                        onMoveToVault = triggerMoveToVault
                                                    )
                                                }

                                                NavigationDestination.BROWSE -> {
                                                    BrowseScreen(
                                                        uiState = uiState,
                                                        onRootFolderSelected = viewModel::onRootFolderSelected,
                                                        onNavigateIntoFolder = viewModel::navigateIntoFolder,
                                                        onBreadcrumbClick = viewModel::navigateBreadcrumb,
                                                        onRefreshFolder = viewModel::refreshCurrentFolder,
                                                        onToggleSelect = viewModel::toggleSelection,
                                                        onClearSelection = viewModel::clearSelection,
                                                        onSelectAll = viewModel::selectAll,
                                                        onDeleteSelected = viewModel::deleteSelectedFiles,
                                                        onCopySelected = { viewModel.setClipboard(viewModel.getSelectedFileItems(), isMove = false) },
                                                        onMoveSelected = { viewModel.setClipboard(viewModel.getSelectedFileItems(), isMove = true) },
                                                        onBatchSelected = {
                                                            batchInitialFiles = viewModel.getSelectedFileItems().filter { !it.isDirectory }
                                                            viewModel.clearSelection()
                                                            activeToolRoute = ToolRoute.BATCH_TOOLS
                                                        },
                                                        onShareSelected = {
                                                            viewModel.shareMultipleFiles(viewModel.getSelectedFileItems())
                                                        },
                                                        onCopyFile = { file -> viewModel.setClipboard(listOf(file), isMove = false) },
                                                        onMoveFile = { file -> viewModel.setClipboard(listOf(file), isMove = true) },
                                                        onMoveToTrash = viewModel::moveToTrash,
                                                        onPasteClipboard = viewModel::pasteClipboardHere,
                                                        onClearClipboard = viewModel::clearClipboard,
                                                        onDeleteFile = viewModel::deleteFile,
                                                        onRenameFile = viewModel::setActiveRenameItem,
                                                        onToggleFavorite = viewModel::toggleFavorite,
                                                        onShowFileDetails = viewModel::showFileDetails,
                                                        onShareFile = { file -> shareFile(context, file) },
                                                        onSmartAnalyze = { file -> smartAnalyzeTarget = file },
                                                        onMoveToVault = triggerMoveToVault,
                                                        onFileClick = { file -> openByPolicy(file, uiState.filteredBrowseFiles) },
                                                        onCreateFolderClick = { viewModel.setShowCreateFolderDialog(true) },
                                                        onCreateFileClick = { viewModel.setShowCreateFileDialog(true) },
                                                        onToggleViewMode = viewModel::setViewMode,
                                                        onUpdateSortConfig = viewModel::setSortConfig,
                                                        onSearchQueryChange = viewModel::setSearchQuery
                                                    )
                                                }

                                                NavigationDestination.CATEGORIES -> {
                                                    CategoriesScreen(
                                                        uiState = uiState,
                                                        onSelectCategory = viewModel::loadCategoryFiles,
                                                        onRefreshCategory = { viewModel.loadCategoryFiles(uiState.selectedCategory) },
                                                        onFileClick = { file -> openByPolicy(file, uiState.filteredCategoryFiles) },
                                                        onToggleFavorite = viewModel::toggleFavorite,
                                                        onShowFileDetails = viewModel::showFileDetails,
                                                        onRenameFile = viewModel::setActiveRenameItem,
                                                        onDeleteFile = viewModel::deleteFile,
                                                        onShareFile = { file -> shareFile(context, file) },
                                                        onToggleViewMode = viewModel::setViewMode,
                                                        onUpdateSortConfig = viewModel::setSortConfig,
                                                        onSearchQueryChange = viewModel::setSearchQuery,
                                                        onAddCustomCategory = viewModel::addCustomCategory,
                                                        onDeleteCustomCategory = viewModel::deleteCustomCategory,
                                                        onSmartAnalyze = { file -> smartAnalyzeTarget = file },
                                                        onMoveToVault = triggerMoveToVault
                                                    )
                                                }

                                                NavigationDestination.TOOLS -> {
                                                    ToolsScreen(
                                                        uiState = uiState,
                                                        onOpenTool = { route ->
                                                            batchInitialFiles = emptyList()
                                                            activeToolRoute = route
                                                        },
                                                        onOpenFileInViewer = { file -> viewModel.openFileInViewer(file) },
                                                        onSmartAnalyzeFile = { file -> smartAnalyzeTarget = file }
                                                    )
                                                }

                                                NavigationDestination.SETTINGS -> {
                                                    SettingsScreen(
                                                        uiState = uiState,
                                                        onSetThemeMode = viewModel::setThemeMode,
                                                        onSetDynamicColor = viewModel::setDynamicColor,
                                                        onSetAccentColor = viewModel::setAccentColor,
                                                        onSetViewMode = viewModel::setViewMode,
                                                        onSetSortConfig = viewModel::setSortConfig,
                                                        onSetShowHiddenFiles = viewModel::setShowHiddenFiles,
                                                        onSetThumbnailsEnabled = viewModel::setThumbnailsEnabled,
                                                        onSetFileOpenMode = viewModel::setFileOpenMode,
                                                        onSetAppLanguage = viewModel::setAppLanguage,
                                                        onSetReaderTheme = viewModel::setReaderTheme,
                                                        onSetReaderFont = viewModel::setReaderFont,
                                                        onSetReaderKeepAwake = viewModel::setReaderKeepAwake,
                                                        onSetPlaybackResumeEnabled = viewModel::setPlaybackResumeEnabled,
                                                        onSetVaultBiometricEnabled = viewModel::setVaultBiometricEnabled,
                                                        onSetVaultAutoLockMinutes = viewModel::setVaultAutoLockMinutes,
                                                        onSetTrashRetentionDays = viewModel::setTrashRetentionDays,
                                                        onSetLogFileActivity = viewModel::setLogFileActivity,
                                                        onOpenVault = { activeToolRoute = ToolRoute.PRIVATE_VAULT },
                                                        onMessage = viewModel::showUserMessage
                                                    )
                                                }
                                            }

                                            uiState.activeDetailItem?.let { item ->
                                                FileDetailBottomSheet(
                                                    fileItem = item,
                                                    hashResult = uiState.hashResult,
                                                    isHashing = uiState.isHashing,
                                                    onCalculateHash = { algo -> viewModel.calculateHash(item, algo) },
                                                    onDismiss = { viewModel.showFileDetails(null) }
                                                )
                                            }

                                            if (uiState.showCreateFolderDialog) {
                                                CreateFolderDialog(
                                                    onDismiss = { viewModel.setShowCreateFolderDialog(false) },
                                                    onCreateFolder = viewModel::createFolder
                                                )
                                            }

                                            if (uiState.showCreateFileDialog) {
                                                CreateFileDialog(
                                                    onDismiss = { viewModel.setShowCreateFileDialog(false) },
                                                    onCreateFile = viewModel::createFile
                                                )
                                            }

                                            uiState.activeRenameItem?.let { item ->
                                                RenameDialog(
                                                    fileItem = item,
                                                    onDismiss = { viewModel.setActiveRenameItem(null) },
                                                    onRename = { newName -> viewModel.renameFile(item, newName) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    smartAnalyzeTarget?.let { target ->
                        SmartAssistantBottomSheet(
                            fileItem = target,
                            onDismiss = { smartAnalyzeTarget = null },
                            onOpenInViewer = { item ->
                                smartAnalyzeTarget = null
                                viewModel.openFileInViewer(item)
                            },
                            onMoveToVault = { item ->
                                smartAnalyzeTarget = null
                                triggerMoveToVault(item)
                            },
                            onOpenConverter = {
                                smartAnalyzeTarget = null
                                activeToolRoute = ToolRoute.FILE_CONVERTER
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkVaultAutoLock()
    }

    override fun onStop() {
        super.onStop()
        viewModel.touchVaultActivity()
        VaultManager(this, viewModel.localRepo).clearSensitiveTempFiles()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val name = resolveDisplayName(uri)
        val mime = intent.type ?: contentResolver.getType(uri) ?: "*/*"
        viewModel.openFileInViewer(
            FileItem(
                uri = uri,
                name = name,
                path = uri.toString(),
                extension = name.substringAfterLast('.', ""),
                mimeType = mime
            )
        )
    }

    private fun resolveDisplayName(uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getString(0)
                    if (!n.isNullOrBlank()) return n
                }
            }
        } catch (_: Exception) {}
        return uri.lastPathSegment?.substringAfterLast('/') ?: "document"
    }

    private fun openExternally(context: Context, fileItem: FileItem): Boolean {
        return try {
            val viewUri = if (fileItem.uri.scheme == "file") {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    File(fileItem.uri.path ?: "")
                )
            } else {
                fileItem.uri
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(viewUri, fileItem.mimeType.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Open with"))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun shareFile(context: Context, fileItem: FileItem) {
        try {
            val shareUri = if (fileItem.uri.scheme == "file") {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    File(fileItem.uri.path ?: "")
                )
            } else {
                fileItem.uri
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = fileItem.mimeType
                putExtra(Intent.EXTRA_STREAM, shareUri)
                putExtra(Intent.EXTRA_SUBJECT, fileItem.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share file"))
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to share this file", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun MiniAudioPlayerBar(
    title: String,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AudioFile, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = title.ifBlank { "Playing Audio" },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onTogglePlay) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or Pause")
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Default.SkipNext, "Next track")
            }
        }
    }
}
