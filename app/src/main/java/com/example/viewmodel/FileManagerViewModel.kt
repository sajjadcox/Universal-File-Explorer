package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.UniversalFileExplorerApp
import com.example.data.db.AppDatabase
import com.example.data.db.BookmarkEntity
import com.example.data.db.CustomCategoryEntity
import com.example.data.db.LocalDataRepository
import com.example.data.db.ReadingHistoryEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.storage.AdvancedStorageOperations
import com.example.data.storage.Phase2FileOperations
import com.example.data.storage.StorageRepository
import com.example.domain.converter.ConversionEngine
import com.example.domain.model.AccentColor
import com.example.domain.model.FileCategory
import com.example.domain.model.FileItem
import com.example.domain.model.FileOpenMode
import com.example.domain.model.ReaderFontChoice
import com.example.domain.model.ReaderTheme
import com.example.domain.model.SortConfig
import com.example.domain.model.SortField
import com.example.domain.model.SortOrder
import com.example.domain.model.StorageStats
import com.example.domain.model.ThemeMode
import com.example.domain.model.ViewMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FileManagerUiState(
    val storageStats: List<StorageStats> = emptyList(),
    val storageBreakdown: Map<FileCategory, Long> = emptyMap(),
    val recentFiles: List<FileItem> = emptyList(),
    val favoriteUris: Set<String> = emptySet(),
    val favoriteFiles: List<FileItem> = emptyList(),
    val readingHistory: List<ReadingHistoryEntity> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val trashCount: Int = 0,
    val currentTreeUri: Uri? = null,
    val currentDirectoryName: String = "App Storage",
    val browseBreadcrumbs: List<Pair<Uri?, String>> = listOf(null to "App Storage"),
    val browseFiles: List<FileItem> = emptyList(),
    val isLoadingBrowse: Boolean = false,
    val selectedCategory: FileCategory = FileCategory.DOCUMENTS,
    val categoryFiles: List<FileItem> = emptyList(),
    val isLoadingCategory: Boolean = false,
    val searchQuery: String = "",
    val viewMode: ViewMode = ViewMode.LIST,
    val sortConfig: SortConfig = SortConfig(),
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val dynamicColor: Boolean = true,
    val accentColor: AccentColor = AccentColor.BLUE,
    val showHiddenFiles: Boolean = false,
    val appLanguage: String = "en",
    val fileOpenMode: FileOpenMode = FileOpenMode.UNIVERSAL_VIEWER,
    val thumbnailsEnabled: Boolean = true,
    val trashRetentionDays: Int = 30,
    val vaultPinHash: String = "",
    val vaultPinSalt: String = "",
    val vaultBiometricEnabled: Boolean = true,
    val vaultAutoLockMinutes: Int = 5,
    val vaultFailedAttempts: Int = 0,
    val vaultLockoutUntilMs: Long = 0L,
    val isVaultUnlocked: Boolean = false,
    val vaultLastInteractionMs: Long = 0L,
    val readerTheme: ReaderTheme = ReaderTheme.SYSTEM,
    val readerFont: ReaderFontChoice = ReaderFontChoice.SANS,
    val readerKeepAwake: Boolean = false,
    val playbackResumeEnabled: Boolean = true,
    val logFileActivity: Boolean = true,
    val customCategories: List<CustomCategoryEntity> = emptyList(),
    val selectedUris: Set<Uri> = emptySet(),
    val clipboardItems: List<FileItem> = emptyList(),
    val clipboardIsMove: Boolean = false,
    val activeDetailItem: FileItem? = null,
    val showCreateFolderDialog: Boolean = false,
    val showCreateFileDialog: Boolean = false,
    val activeRenameItem: FileItem? = null,
    val activeViewerFile: FileItem? = null,
    val activeViewerContext: List<FileItem> = emptyList(),
    val hashResult: String? = null,
    val isHashing: Boolean = false,
    val userMessage: String? = null,
    val storagePermissionsGranted: Boolean = false
) {
    val isMultiSelecting: Boolean
        get() = selectedUris.isNotEmpty()

    val filteredBrowseFiles: List<FileItem>
        get() = applySearchAndSort(browseFiles, searchQuery, sortConfig, showHiddenFiles)

    val filteredCategoryFiles: List<FileItem>
        get() = applySearchAndSort(categoryFiles, searchQuery, sortConfig, showHiddenFiles)

    companion object {
        fun applySearchAndSort(
            list: List<FileItem>,
            query: String,
            sortConfig: SortConfig,
            showHidden: Boolean
        ): List<FileItem> {
            var result = list
            if (!showHidden) {
                result = result.filter { !it.name.startsWith(".") }
            }
            if (query.isNotBlank()) {
                val q = query.trim().lowercase()
                result = result.filter { it.name.lowercase().contains(q) || it.extension.lowercase().contains(q) }
            }
            val sorted = when (sortConfig.field) {
                SortField.NAME -> {
                    if (sortConfig.order == SortOrder.ASCENDING)
                        result.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                    else
                        result.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.name.lowercase() })
                }
                SortField.DATE -> {
                    if (sortConfig.order == SortOrder.ASCENDING)
                        result.sortedWith(compareBy({ !it.isDirectory }, { it.lastModified }))
                    else
                        result.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.lastModified })
                }
                SortField.SIZE -> {
                    if (sortConfig.order == SortOrder.ASCENDING)
                        result.sortedWith(compareBy({ !it.isDirectory }, { it.size }))
                    else
                        result.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.size })
                }
                SortField.TYPE -> {
                    if (sortConfig.order == SortOrder.ASCENDING)
                        result.sortedWith(compareBy({ !it.isDirectory }, { it.extension.lowercase() }, { it.name.lowercase() }))
                    else
                        result.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenByDescending { it.extension.lowercase() }.thenBy { it.name.lowercase() })
                }
            }
            return sorted
        }
    }
}

class FileManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val storageRepo = StorageRepository(application)
    private val prefsRepo = UserPreferencesRepository(application)
    val storageOps = AdvancedStorageOperations(application)
    val localRepo: LocalDataRepository = (application as? UniversalFileExplorerApp)?.localDataRepository
        ?: LocalDataRepository(AppDatabase.getInstance(application))
    val conversionEngine = ConversionEngine(application, localRepo, storageOps)

    private val _uiState = MutableStateFlow(FileManagerUiState())
    val uiState: StateFlow<FileManagerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prefsRepo.themeModeFlow.collectLatest { theme ->
                _uiState.update { it.copy(themeMode = theme) }
            }
        }
        viewModelScope.launch {
            prefsRepo.dynamicColorFlow.collectLatest { dyn ->
                _uiState.update { it.copy(dynamicColor = dyn) }
            }
        }
        viewModelScope.launch {
            prefsRepo.viewModeFlow.collectLatest { view ->
                _uiState.update { it.copy(viewMode = view) }
            }
        }
        viewModelScope.launch {
            prefsRepo.sortConfigFlow.collectLatest { sort ->
                _uiState.update { it.copy(sortConfig = sort) }
            }
        }
        viewModelScope.launch {
            prefsRepo.showHiddenFilesFlow.collectLatest { show ->
                _uiState.update { it.copy(showHiddenFiles = show) }
            }
        }
        viewModelScope.launch {
            prefsRepo.favoriteUrisFlow.collectLatest { favs ->
                _uiState.update { current ->
                    current.copy(
                        favoriteUris = favs,
                        favoriteFiles = current.recentFiles.filter { favs.contains(it.uri.toString()) }
                    )
                }
            }
        }
        viewModelScope.launch {
            prefsRepo.appLanguageFlow.collectLatest { lang ->
                _uiState.update { it.copy(appLanguage = lang) }
            }
        }
        viewModelScope.launch {
            localRepo.readingHistory.collectLatest { history ->
                _uiState.update { it.copy(readingHistory = history) }
            }
        }
        viewModelScope.launch {
            localRepo.allBookmarks.collectLatest { bms ->
                _uiState.update { it.copy(bookmarks = bms) }
            }
        }
        viewModelScope.launch {
            localRepo.trashEntries.collectLatest { trash ->
                _uiState.update { it.copy(trashCount = trash.size) }
            }
        }
        viewModelScope.launch {
            prefsRepo.accentColorFlow.collectLatest { accent ->
                _uiState.update { it.copy(accentColor = accent) }
            }
        }
        viewModelScope.launch {
            prefsRepo.fileOpenModeFlow.collectLatest { mode ->
                _uiState.update { it.copy(fileOpenMode = mode) }
            }
        }
        viewModelScope.launch {
            prefsRepo.thumbnailsEnabledFlow.collectLatest { enabled ->
                _uiState.update { it.copy(thumbnailsEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            prefsRepo.trashRetentionDaysFlow.collectLatest { days ->
                _uiState.update { it.copy(trashRetentionDays = days) }
            }
        }
        viewModelScope.launch {
            prefsRepo.vaultPinHashFlow.collectLatest { hash ->
                _uiState.update { it.copy(vaultPinHash = hash.orEmpty()) }
            }
        }
        viewModelScope.launch {
            prefsRepo.vaultPinSaltFlow.collectLatest { salt ->
                _uiState.update { it.copy(vaultPinSalt = salt.orEmpty()) }
            }
        }
        viewModelScope.launch {
            prefsRepo.vaultBiometricEnabledFlow.collectLatest { bio ->
                _uiState.update { it.copy(vaultBiometricEnabled = bio) }
            }
        }
        viewModelScope.launch {
            prefsRepo.vaultAutoLockMinutesFlow.collectLatest { mins ->
                _uiState.update { it.copy(vaultAutoLockMinutes = mins) }
            }
        }
        viewModelScope.launch {
            prefsRepo.vaultFailedAttemptsFlow.collectLatest { attempts ->
                _uiState.update { it.copy(vaultFailedAttempts = attempts) }
            }
        }
        viewModelScope.launch {
            prefsRepo.vaultLockoutUntilMsFlow.collectLatest { until ->
                _uiState.update { it.copy(vaultLockoutUntilMs = until) }
            }
        }
        viewModelScope.launch {
            prefsRepo.readerThemeFlow.collectLatest { rt ->
                _uiState.update { it.copy(readerTheme = rt) }
            }
        }
        viewModelScope.launch {
            prefsRepo.readerFontFlow.collectLatest { rf ->
                _uiState.update { it.copy(readerFont = rf) }
            }
        }
        viewModelScope.launch {
            prefsRepo.readerKeepAwakeFlow.collectLatest { awake ->
                _uiState.update { it.copy(readerKeepAwake = awake) }
            }
        }
        viewModelScope.launch {
            prefsRepo.playbackResumeEnabledFlow.collectLatest { resume ->
                _uiState.update { it.copy(playbackResumeEnabled = resume) }
            }
        }
        viewModelScope.launch {
            prefsRepo.logFileActivityFlow.collectLatest { logEnabled ->
                _uiState.update { it.copy(logFileActivity = logEnabled) }
            }
        }
        viewModelScope.launch {
            localRepo.customCategories.collectLatest { cats ->
                _uiState.update { it.copy(customCategories = cats) }
            }
        }

        // Startup maintenance: Trash retention auto-cleanup, seed default organization rules, verify bookmark URIs
        viewModelScope.launch {
            runCatching {
                val retention = prefsRepo.trashRetentionDaysFlow.first()
                if (retention > 0) {
                    val expired = localRepo.getExpiredTrashEntries(retention)
                    expired.forEach { entry ->
                        storageOps.deleteTrashFilePermanently(entry)
                        localRepo.removeTrashEntry(entry.id)
                    }
                }
                localRepo.seedDefaultRulesIfEmpty()
                localRepo.verifyBookmarksIntegrity()
            }
        }

        refreshStorageStats()
        loadRecentFiles()
        loadCategoryFiles(FileCategory.DOCUMENTS)
        loadInitialBrowseFiles()
    }

    fun openFileInViewer(fileItem: FileItem, contextList: List<FileItem> = emptyList()) {
        _uiState.update {
            it.copy(
                activeViewerFile = fileItem,
                activeViewerContext = contextList
            )
        }
    }

    fun closeViewer() {
        _uiState.update { it.copy(activeViewerFile = null, activeViewerContext = emptyList()) }
        refreshCurrentFolder()
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            val baseStats = storageRepo.getStorageStats()
            val volumeStats = storageOps.getAvailableStorageVolumes()
            val merged = if (volumeStats.isNotEmpty()) {
                val paths = baseStats.map { it.path }.toSet()
                baseStats + volumeStats.filter { it.path !in paths && it.isRemovable }
            } else baseStats
            val breakdown = storageRepo.getStorageCategoryBreakdown()
            _uiState.update { it.copy(storageStats = merged, storageBreakdown = breakdown) }
        }
    }

    fun loadRecentFiles() {
        viewModelScope.launch {
            val recents = storageRepo.getRecentFiles(25)
            _uiState.update { current ->
                val favFiles = recents.filter { current.favoriteUris.contains(it.uri.toString()) }
                current.copy(recentFiles = recents, favoriteFiles = favFiles)
            }
        }
    }

    fun loadCategoryFiles(category: FileCategory) {
        viewModelScope.launch {
            _uiState.update { it.copy(selectedCategory = category, isLoadingCategory = true) }
            val files = storageRepo.getFilesForCategory(category)
            _uiState.update { it.copy(categoryFiles = files, isLoadingCategory = false) }
        }
    }

    private fun loadInitialBrowseFiles() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBrowse = true) }
            val internalFiles = storageRepo.listInternalAppFiles()
            _uiState.update {
                it.copy(
                    browseFiles = internalFiles,
                    isLoadingBrowse = false,
                    currentTreeUri = null,
                    currentDirectoryName = "App Storage",
                    browseBreadcrumbs = listOf(null to "App Storage")
                )
            }
        }
    }

    fun onRootFolderSelected(treeUri: Uri, folderName: String) {
        viewModelScope.launch {
            storageRepo.takePersistableUriPermission(treeUri)
            _uiState.update {
                it.copy(
                    isLoadingBrowse = true,
                    currentTreeUri = treeUri,
                    currentDirectoryName = folderName,
                    browseBreadcrumbs = listOf(treeUri to folderName),
                    selectedUris = emptySet()
                )
            }
            val files = storageRepo.listDocumentTree(treeUri)
            _uiState.update { it.copy(browseFiles = files, isLoadingBrowse = false) }
        }
    }

    fun navigateIntoFolder(item: FileItem) {
        if (!item.isDirectory) return
        viewModelScope.launch {
            _uiState.update { current ->
                val newBreadcrumbs = current.browseBreadcrumbs + (item.uri to item.name)
                current.copy(
                    isLoadingBrowse = true,
                    currentDirectoryName = item.name,
                    browseBreadcrumbs = newBreadcrumbs,
                    selectedUris = emptySet()
                )
            }
            val files = if (item.uri.scheme == "file") {
                val dir = java.io.File(item.uri.path ?: "")
                dir.listFiles()?.map { f ->
                    FileItem(
                        uri = Uri.fromFile(f),
                        name = f.name,
                        path = f.absolutePath,
                        extension = f.extension,
                        size = if (f.isDirectory) 0L else f.length(),
                        lastModified = f.lastModified(),
                        isDirectory = f.isDirectory,
                        childrenCount = if (f.isDirectory) f.listFiles()?.size else null,
                        canWrite = f.canWrite(),
                        canDelete = f.canWrite()
                    )
                } ?: emptyList()
            } else {
                storageRepo.listDirectoryDocument(item.uri)
            }
            _uiState.update { it.copy(browseFiles = files, isLoadingBrowse = false) }
        }
    }

    fun navigateBreadcrumb(index: Int) {
        val currentBreadcrumbs = _uiState.value.browseBreadcrumbs
        if (index < 0 || index >= currentBreadcrumbs.size) return
        val target = currentBreadcrumbs[index]
        val targetUri = target.first

        viewModelScope.launch {
            _uiState.update { current ->
                val slicedBreadcrumbs = current.browseBreadcrumbs.take(index + 1)
                current.copy(
                    isLoadingBrowse = true,
                    currentDirectoryName = target.second,
                    browseBreadcrumbs = slicedBreadcrumbs,
                    selectedUris = emptySet()
                )
            }
            val files = when {
                targetUri == null -> storageRepo.listInternalAppFiles()
                targetUri.scheme == "file" -> {
                    val dir = java.io.File(targetUri.path ?: "")
                    dir.listFiles()?.map { f ->
                        FileItem(
                            uri = Uri.fromFile(f),
                            name = f.name,
                            path = f.absolutePath,
                            extension = f.extension,
                            size = if (f.isDirectory) 0L else f.length(),
                            lastModified = f.lastModified(),
                            isDirectory = f.isDirectory,
                            childrenCount = if (f.isDirectory) f.listFiles()?.size else null
                        )
                    } ?: emptyList()
                }
                else -> storageRepo.listDirectoryDocument(targetUri)
            }
            _uiState.update { it.copy(browseFiles = files, isLoadingBrowse = false) }
        }
    }

    fun navigateBackInBrowse(): Boolean {
        val breadcrumbs = _uiState.value.browseBreadcrumbs
        return if (breadcrumbs.size > 1) {
            navigateBreadcrumb(breadcrumbs.size - 2)
            true
        } else {
            false
        }
    }

    fun refreshCurrentFolder() {
        val currentBreadcrumbs = _uiState.value.browseBreadcrumbs
        val currentTarget = currentBreadcrumbs.lastOrNull() ?: return
        val targetUri = currentTarget.first

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBrowse = true) }
            val files = when {
                targetUri == null -> storageRepo.listInternalAppFiles()
                targetUri.scheme == "file" -> {
                    val dir = java.io.File(targetUri.path ?: "")
                    dir.listFiles()?.map { f ->
                        FileItem(
                            uri = Uri.fromFile(f),
                            name = f.name,
                            path = f.absolutePath,
                            extension = f.extension,
                            size = if (f.isDirectory) 0L else f.length(),
                            lastModified = f.lastModified(),
                            isDirectory = f.isDirectory,
                            childrenCount = if (f.isDirectory) f.listFiles()?.size else null
                        )
                    } ?: emptyList()
                }
                else -> storageRepo.listDirectoryDocument(targetUri)
            }
            _uiState.update { it.copy(browseFiles = files, isLoadingBrowse = false) }
        }
    }

    fun toggleSelection(uri: Uri) {
        _uiState.update { current ->
            val set = current.selectedUris.toMutableSet()
            if (set.contains(uri)) set.remove(uri) else set.add(uri)
            current.copy(selectedUris = set)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedUris = emptySet()) }
    }

    fun selectAll() {
        _uiState.update { current ->
            val allUris = current.filteredBrowseFiles.map { it.uri }.toSet()
            current.copy(selectedUris = allUris)
        }
    }

    fun setClipboard(items: List<FileItem>, isMove: Boolean) {
        val filesOnly = items.filter { !it.isDirectory }
        if (filesOnly.isEmpty()) {
            showUserMessage("Select one or more files to ${if (isMove) "move" else "copy"}")
            return
        }
        _uiState.update {
            it.copy(
                clipboardItems = filesOnly,
                clipboardIsMove = isMove,
                selectedUris = emptySet(),
                userMessage = "${filesOnly.size} file(s) ready to ${if (isMove) "move" else "copy"}. Navigate to destination and tap Paste."
            )
        }
    }

    fun clearClipboard() {
        _uiState.update { it.copy(clipboardItems = emptyList()) }
    }

    fun pasteClipboardHere() {
        val items = _uiState.value.clipboardItems
        val isMove = _uiState.value.clipboardIsMove
        val destTreeUri = _uiState.value.browseBreadcrumbs.lastOrNull()?.first
        if (items.isEmpty()) return

        viewModelScope.launch {
            var count = 0
            for (item in items) {
                val ok = if (isMove) {
                    storageOps.moveDocument(item, destTreeUri)
                } else {
                    storageOps.copyDocument(item.uri, destTreeUri, item.name, item.mimeType)
                }
                if (ok) {
                    count++
                    logIfEnabled(if (isMove) "MOVED" else "COPIED", item.name, item.uri.toString(), "Destination: ${destTreeUri ?: "App Storage"}")
                }
            }
            _uiState.update {
                it.copy(
                    clipboardItems = emptyList(),
                    userMessage = "${if (isMove) "Moved" else "Copied"} $count of ${items.size} file(s)"
                )
            }
            refreshCurrentFolder()
            refreshStorageStats()
        }
    }

    fun getSelectedFileItems(): List<FileItem> {
        val selected = _uiState.value.selectedUris
        return _uiState.value.browseFiles.filter { it.uri in selected }
    }

    fun shareMultipleFiles(items: List<FileItem>) {
        val ok = Phase2FileOperations.shareMultipleFiles(getApplication(), items)
        if (ok) {
            items.filter { !it.isDirectory }.forEach {
                logIfEnabled("SHARED", it.name, it.uri.toString(), "Shared via multi-share")
            }
            clearSelection()
        } else {
            showUserMessage("Select at least one non-folder file to share")
        }
    }

    fun deleteSelectedFiles() {
        val toDelete = _uiState.value.selectedUris.toList()
        val selectedNames = _uiState.value.browseFiles.associate { it.uri to it.name }
        if (toDelete.isEmpty()) return

        viewModelScope.launch {
            var successCount = 0
            for (uri in toDelete) {
                if (storageRepo.deleteDocument(uri)) {
                    successCount++
                    logIfEnabled("DELETED", selectedNames[uri] ?: uri.lastPathSegment.orEmpty(), uri.toString(), "Batch delete")
                }
            }
            clearSelection()
            refreshCurrentFolder()
            loadRecentFiles()
            refreshStorageStats()
            _uiState.update { it.copy(userMessage = "Deleted $successCount file(s)") }
        }
    }

    fun moveToTrash(fileItem: FileItem) {
        viewModelScope.launch {
            val entry = storageOps.moveToTrash(fileItem)
            if (entry != null) {
                localRepo.addTrashEntry(entry)
                logIfEnabled("TRASHED", fileItem.name, fileItem.uri.toString(), "Moved to App Trash")
                refreshCurrentFolder()
                loadRecentFiles()
                refreshStorageStats()
                showUserMessage("Moved '${fileItem.name}' to App Trash")
            } else {
                showUserMessage("Unable to move '${fileItem.name}' to trash (read-only)")
            }
        }
    }

    fun deleteFile(fileItem: FileItem) {
        viewModelScope.launch {
            val success = storageRepo.deleteDocument(fileItem.uri)
            if (success) {
                logIfEnabled("DELETED", fileItem.name, fileItem.uri.toString(), "Permanent delete")
                refreshCurrentFolder()
                loadRecentFiles()
                loadCategoryFiles(_uiState.value.selectedCategory)
                refreshStorageStats()
                _uiState.update { it.copy(userMessage = "Deleted ${fileItem.name}") }
            } else {
                _uiState.update { it.copy(userMessage = "Unable to delete ${fileItem.name} (read-only or restricted)") }
            }
        }
    }

    fun createFolder(name: String) {
        val currentTarget = _uiState.value.browseBreadcrumbs.lastOrNull()
        val targetUri = currentTarget?.first

        viewModelScope.launch {
            val success = if (targetUri == null) {
                val f = java.io.File(getApplication<Application>().filesDir, name)
                f.mkdirs()
            } else {
                storageRepo.createFolder(targetUri, name)
            }
            _uiState.update { it.copy(showCreateFolderDialog = false) }
            if (success) {
                logIfEnabled("CREATED_FOLDER", name, targetUri?.toString() ?: "AppStorage", "Created directory")
                refreshCurrentFolder()
                _uiState.update { it.copy(userMessage = "Created folder \"$name\"") }
            } else {
                _uiState.update { it.copy(userMessage = "Failed to create folder (permission denied)") }
            }
        }
    }

    fun createFile(name: String) {
        val targetUri = _uiState.value.browseBreadcrumbs.lastOrNull()?.first
        viewModelScope.launch {
            val created = storageOps.createFile(targetUri, name)
            _uiState.update { it.copy(showCreateFileDialog = false) }
            if (created != null) {
                logIfEnabled("CREATED_FILE", name, created.toString(), "Created empty file")
                refreshCurrentFolder()
                showUserMessage("Created '$name'")
            } else {
                showUserMessage("Failed to create file '$name'")
            }
        }
    }

    fun renameFile(fileItem: FileItem, newName: String) {
        viewModelScope.launch {
            val success = if (fileItem.uri.scheme == "file") {
                val f = java.io.File(fileItem.uri.path ?: "")
                f.renameTo(java.io.File(f.parentFile, newName))
            } else {
                storageRepo.renameDocument(fileItem.uri, newName)
            }
            _uiState.update { it.copy(activeRenameItem = null) }
            if (success) {
                logIfEnabled("RENAMED", newName, fileItem.uri.toString(), "From '${fileItem.name}'")
                refreshCurrentFolder()
                loadRecentFiles()
                _uiState.update { it.copy(userMessage = "Renamed to $newName") }
            } else {
                _uiState.update { it.copy(userMessage = "Rename failed or not permitted") }
            }
        }
    }

    fun toggleFavorite(item: FileItem) {
        viewModelScope.launch {
            prefsRepo.toggleFavorite(item.uri.toString())
        }
    }

    fun calculateHash(fileItem: FileItem, algorithm: String = "SHA-256") {
        viewModelScope.launch {
            _uiState.update { it.copy(isHashing = true, hashResult = null) }
            val hash = storageRepo.calculateFileHash(fileItem.uri, algorithm)
            _uiState.update { it.copy(isHashing = false, hashResult = hash) }
        }
    }

    fun clearHashResult() {
        _uiState.update { it.copy(hashResult = null, isHashing = false) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setViewMode(viewMode: ViewMode) {
        viewModelScope.launch { prefsRepo.setViewMode(viewMode) }
    }

    fun setSortConfig(sortConfig: SortConfig) {
        viewModelScope.launch { prefsRepo.setSortConfig(sortConfig) }
    }

    fun setThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch { prefsRepo.setThemeMode(themeMode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setDynamicColor(enabled) }
    }

    fun setShowHiddenFiles(show: Boolean) {
        viewModelScope.launch { prefsRepo.setShowHiddenFiles(show) }
    }

    fun setAppLanguage(lang: String) {
        viewModelScope.launch { prefsRepo.setAppLanguage(lang) }
    }

    fun setAccentColor(accentColor: AccentColor) {
        viewModelScope.launch { prefsRepo.setAccentColor(accentColor) }
    }

    fun setFileOpenMode(mode: FileOpenMode) {
        viewModelScope.launch { prefsRepo.setFileOpenMode(mode) }
    }

    fun setThumbnailsEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setThumbnailsEnabled(enabled) }
    }

    fun setTrashRetentionDays(days: Int) {
        viewModelScope.launch { prefsRepo.setTrashRetentionDays(days) }
    }

    fun setReaderTheme(theme: ReaderTheme) {
        viewModelScope.launch { prefsRepo.setReaderTheme(theme) }
    }

    fun setReaderFont(font: ReaderFontChoice) {
        viewModelScope.launch { prefsRepo.setReaderFont(font) }
    }

    fun setReaderKeepAwake(keepAwake: Boolean) {
        viewModelScope.launch { prefsRepo.setReaderKeepAwake(keepAwake) }
    }

    fun setPlaybackResumeEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setPlaybackResumeEnabled(enabled) }
    }

    fun setVaultBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setVaultBiometricEnabled(enabled) }
    }

    fun setVaultAutoLockMinutes(minutes: Int) {
        viewModelScope.launch { prefsRepo.setVaultAutoLockMinutes(minutes) }
    }

    fun setLogFileActivity(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setLogFileActivity(enabled) }
    }

    fun unlockVault() {
        val now = System.currentTimeMillis()
        _uiState.update { it.copy(isVaultUnlocked = true, vaultLastInteractionMs = now) }
        viewModelScope.launch { prefsRepo.resetVaultLockout() }
    }

    fun lockVault() {
        _uiState.update { it.copy(isVaultUnlocked = false) }
    }

    fun touchVaultActivity() {
        _uiState.update { it.copy(vaultLastInteractionMs = System.currentTimeMillis()) }
    }

    fun checkVaultAutoLock() {
        val state = _uiState.value
        if (!state.isVaultUnlocked) return
        val timeoutMins = state.vaultAutoLockMinutes
        if (timeoutMins == 0) {
            lockVault()
            return
        }
        val elapsedMs = System.currentTimeMillis() - state.vaultLastInteractionMs
        if (state.vaultLastInteractionMs > 0L && elapsedMs >= timeoutMins * 60_000L) {
            lockVault()
        }
    }

    fun saveVaultPin(hash: String, salt: String) {
        viewModelScope.launch {
            prefsRepo.saveVaultPinCredentials(hash, salt)
            _uiState.update { it.copy(isVaultUnlocked = true, vaultLastInteractionMs = System.currentTimeMillis()) }
        }
    }

    fun recordVaultFailedAttempt(attempts: Int, untilMs: Long) {
        viewModelScope.launch {
            prefsRepo.recordVaultFailedAttempt(attempts, untilMs)
        }
    }

    fun addCustomCategory(name: String, extensionsCsv: String) {
        if (name.isBlank() || extensionsCsv.isBlank()) return
        viewModelScope.launch {
            localRepo.saveCustomCategory(name.trim(), extensionsCsv.trim())
        }
    }

    fun deleteCustomCategory(id: Long) {
        viewModelScope.launch {
            localRepo.deleteCustomCategory(id)
        }
    }

    private fun logIfEnabled(action: String, fileName: String, fileUri: String, details: String) {
        if (!_uiState.value.logFileActivity) return
        viewModelScope.launch {
            localRepo.logActivity(action, fileName, fileUri, details)
        }
    }

    fun showFileDetails(item: FileItem?) {
        _uiState.update { it.copy(activeDetailItem = item, hashResult = null) }
    }

    fun setShowCreateFolderDialog(show: Boolean) {
        _uiState.update { it.copy(showCreateFolderDialog = show) }
    }

    fun setShowCreateFileDialog(show: Boolean) {
        _uiState.update { it.copy(showCreateFileDialog = show) }
    }

    fun setActiveRenameItem(item: FileItem?) {
        _uiState.update { it.copy(activeRenameItem = item) }
    }

    fun setStoragePermissionsGranted(granted: Boolean) {
        _uiState.update { it.copy(storagePermissionsGranted = granted) }
        if (granted) {
            refreshStorageStats()
            loadRecentFiles()
            loadCategoryFiles(_uiState.value.selectedCategory)
        }
    }

    fun showUserMessage(msg: String) {
        _uiState.update { it.copy(userMessage = msg) }
    }

    fun dismissUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
