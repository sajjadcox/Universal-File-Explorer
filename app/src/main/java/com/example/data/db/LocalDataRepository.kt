package com.example.data.db

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

class LocalDataRepository(private val db: AppDatabase) {
    val readingHistory: Flow<List<ReadingHistoryEntity>> = db.readingHistoryDao().getRecentHistory()
    val allBookmarks: Flow<List<BookmarkEntity>> = db.bookmarkDao().getAllBookmarks()
    val searchHistory: Flow<List<SearchHistoryEntity>> = db.searchHistoryDao().getSearchHistory()
    val trashEntries: Flow<List<TrashEntryEntity>> = db.trashDao().getTrashEntries()
    val conversionJobs: Flow<List<ConversionJobEntity>> = db.conversionJobDao().getConversionJobs()

    // Phase 2 Flows
    val vaultEntries: Flow<List<VaultEntryEntity>> = db.vaultDao().getAllVaultEntries()
    val fileActivity: Flow<List<FileActivityEntity>> = db.fileActivityDao().getRecentActivity()
    val recentActivity: Flow<List<FileActivityEntity>> get() = fileActivity
    val playlists: Flow<List<PlaylistEntity>> = db.playlistDao().getPlaylists()
    val mediaHistory: Flow<List<MediaHistoryEntity>> = db.mediaHistoryDao().getMediaHistory()
    val customCategories: Flow<List<CustomCategoryEntity>> = db.customCategoryDao().getCustomCategories()
    val organizationRules: Flow<List<OrganizationRuleEntity>> = db.organizationRuleDao().getRules()

    fun getBookmarksForUri(uri: String): Flow<List<BookmarkEntity>> =
        db.bookmarkDao().getBookmarksForUri(uri)

    suspend fun getHistoryForUri(uri: String): ReadingHistoryEntity? =
        db.readingHistoryDao().getHistoryForUri(uri)

    suspend fun recordFileOpened(
        uri: String,
        name: String,
        mimeType: String,
        viewerType: String,
        size: Long,
        position: Long = 0L,
        totalLength: Long = 0L
    ) {
        val existing = db.readingHistoryDao().getHistoryForUri(uri)
        db.readingHistoryDao().upsertHistory(
            ReadingHistoryEntity(
                uri = uri,
                name = name,
                mimeType = mimeType,
                viewerType = viewerType,
                size = size,
                lastOpenedAt = System.currentTimeMillis(),
                lastPosition = if (position > 0) position else (existing?.lastPosition ?: 0L),
                totalLength = if (totalLength > 0) totalLength else (existing?.totalLength ?: 0L)
            )
        )
    }

    suspend fun updateReadingPosition(uri: String, position: Long, totalLength: Long) {
        val existing = db.readingHistoryDao().getHistoryForUri(uri) ?: return
        db.readingHistoryDao().upsertHistory(
            existing.copy(
                lastOpenedAt = System.currentTimeMillis(),
                lastPosition = position,
                totalLength = totalLength
            )
        )
    }

    suspend fun removeMissingReadingHistoryEntry(uri: String) {
        db.readingHistoryDao().deleteByUri(uri)
    }

    suspend fun addBookmark(uri: String, fileName: String, label: String, position: Long = 0L): Long {
        val existing = db.bookmarkDao().findExact(uri, position)
        if (existing != null) return existing.id
        return db.bookmarkDao().insertBookmark(
            BookmarkEntity(uri = uri, fileName = fileName, label = label, position = position)
        )
    }

    suspend fun removeBookmark(id: Long) = db.bookmarkDao().deleteBookmarkById(id)

    suspend fun removeBookmarkAt(uri: String, position: Long) =
        db.bookmarkDao().deleteBookmarkAt(uri, position)

    suspend fun recordSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            db.searchHistoryDao().insertSearch(SearchHistoryEntity(query = trimmed))
        }
    }

    suspend fun deleteSearch(query: String) = db.searchHistoryDao().deleteSearch(query)

    suspend fun clearSearchHistory() = db.searchHistoryDao().clearSearchHistory()

    suspend fun addTrashEntry(entry: TrashEntryEntity): Long = db.trashDao().insertTrashEntry(entry)

    suspend fun removeTrashEntry(id: Long) = db.trashDao().deleteTrashEntry(id)

    suspend fun getExpiredTrashEntries(retentionDays: Int): List<TrashEntryEntity> {
        if (retentionDays <= 0) return emptyList()
        val cutoff = System.currentTimeMillis() - retentionDays * 24L * 60L * 60L * 1000L
        return db.trashDao().getEntriesOlderThan(cutoff)
    }

    suspend fun clearAllTrash() = db.trashDao().clearAllTrash()

    suspend fun createConversionJob(sourceName: String, sourceUri: String, targetFormat: String): Long =
        db.conversionJobDao().insertJob(
            ConversionJobEntity(
                sourceName = sourceName,
                sourceUri = sourceUri,
                targetFormat = targetFormat,
                status = "PENDING",
                progress = 0
            )
        )

    suspend fun updateConversionJob(
        id: Long,
        status: String,
        progress: Int,
        outputUri: String? = null,
        outputPath: String? = null,
        error: String? = null
    ) = db.conversionJobDao().updateJobStatus(id, status, progress, outputUri, outputPath, error)

    suspend fun clearFinishedConversionJobs() = db.conversionJobDao().clearFinishedJobs()

    // ==================== PHASE 2 REPOSITORY METHODS ====================

    suspend fun addVaultEntry(entry: VaultEntryEntity): Long = db.vaultDao().insertVaultEntry(entry)
    suspend fun getVaultEntriesSnapshot(): List<VaultEntryEntity> = db.vaultDao().getAllVaultEntriesSnapshot()
    suspend fun setVaultEntryHidden(id: Long, hidden: Boolean) = db.vaultDao().setHidden(id, hidden)
    suspend fun moveVaultEntryFolder(id: Long, folder: String) = db.vaultDao().moveToFolder(id, folder)
    suspend fun touchVaultEntryAccessed(id: Long) = db.vaultDao().touchAccessed(id)
    suspend fun removeVaultEntry(id: Long) = db.vaultDao().deleteVaultEntry(id)

    suspend fun logActivity(action: String, fileName: String, fileUri: String, details: String = "") {
        db.fileActivityDao().insertActivity(
            FileActivityEntity(
                action = action,
                fileName = fileName,
                fileUri = fileUri,
                details = details
            )
        )
    }

    suspend fun clearActivityLog() = db.fileActivityDao().clearActivity()

    fun getPlaylistTracks(playlistId: Long): Flow<List<PlaylistTrackEntity>> =
        db.playlistDao().getTracksForPlaylist(playlistId)

    fun playlistTracks(playlistId: Long): Flow<List<PlaylistTrackEntity>> =
        getPlaylistTracks(playlistId)

    suspend fun createPlaylist(name: String): Long =
        db.playlistDao().insertPlaylist(PlaylistEntity(name = name.trim()))

    suspend fun deletePlaylist(playlistId: Long) {
        db.playlistDao().clearPlaylistTracks(playlistId)
        db.playlistDao().deletePlaylist(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, uri: String, title: String, sizeBytes: Long = 0L) {
        db.playlistDao().insertTrack(
            PlaylistTrackEntity(
                playlistId = playlistId,
                trackUri = uri,
                title = title,
                sizeBytes = sizeBytes
            )
        )
    }

    suspend fun removeTrackFromPlaylist(trackId: Long) = db.playlistDao().deleteTrack(trackId)

    fun recentMediaHistory(type: String, limit: Int): Flow<List<MediaHistoryEntity>> =
        mediaHistory.map { list ->
            list.filter { entry ->
                when {
                    type.equals("AUDIO", ignoreCase = true) -> !entry.isVideo
                    type.equals("VIDEO", ignoreCase = true) -> entry.isVideo
                    else -> true
                }
            }.take(limit)
        }

    suspend fun upsertMediaHistory(
        uri: String,
        title: String,
        mediaType: String,
        positionMs: Long,
        durationMs: Long
    ) {
        val isVideo = mediaType.equals("VIDEO", ignoreCase = true)
        recordMediaPlayback(
            uri = uri,
            title = title,
            mimeType = if (isVideo) "video/*" else "audio/*",
            isVideo = isVideo,
            positionMs = positionMs,
            durationMs = durationMs
        )
    }

    suspend fun recordMediaPlayback(
        uri: String,
        title: String,
        mimeType: String,
        isVideo: Boolean,
        positionMs: Long,
        durationMs: Long
    ) {
        val existing = db.mediaHistoryDao().getMediaHistoryForUri(uri)
        val count = if (existing == null) 1 else existing.playCount + if (positionMs < 5000L) 1 else 0
        db.mediaHistoryDao().upsertMediaHistory(
            MediaHistoryEntity(
                uri = uri,
                title = title,
                mimeType = mimeType,
                isVideo = isVideo,
                positionMs = positionMs,
                durationMs = durationMs,
                playCount = count,
                lastPlayedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getMediaResumePositionMs(uri: String): Long =
        db.mediaHistoryDao().getMediaHistoryForUri(uri)?.positionMs ?: 0L

    suspend fun clearMediaHistory() = db.mediaHistoryDao().clearMediaHistory()

    suspend fun saveCustomCategory(name: String, extensionsCsv: String, accentHex: Long = 0xFF0061A4): Long =
        db.customCategoryDao().upsertCustomCategory(
            CustomCategoryEntity(name = name.trim(), extensionsCsv = extensionsCsv.trim(), accentHex = accentHex)
        )

    suspend fun deleteCustomCategory(id: Long) = db.customCategoryDao().deleteCustomCategory(id)

    suspend fun saveOrganizationRule(name: String, extensionsCsv: String, targetSubfolder: String): Long =
        db.organizationRuleDao().upsertRule(
            OrganizationRuleEntity(
                name = name.trim(),
                extensionsCsv = extensionsCsv.trim(),
                targetSubfolder = targetSubfolder.trim()
            )
        )

    suspend fun seedDefaultRulesIfEmpty() {
        if (db.organizationRuleDao().getRulesSnapshot().isNotEmpty()) return
        saveOrganizationRule("Sort Documents", "pdf,doc,docx,odt,txt,csv,xlsx,pptx", "Organized/Documents")
        saveOrganizationRule("Sort Images", "jpg,jpeg,png,webp,gif,heic,svg", "Organized/Images")
        saveOrganizationRule("Sort Archives", "zip,7z,tar,gz,bz2,xz,rar", "Organized/Archives")
        saveOrganizationRule("Sort Installers", "apk,xapk,apks", "Organized/APKs")
    }

    suspend fun toggleRuleEnabled(id: Long, enabled: Boolean) = db.organizationRuleDao().setEnabled(id, enabled)
    suspend fun setOrganizationRuleEnabled(id: Long, enabled: Boolean) = toggleRuleEnabled(id, enabled)
    suspend fun recordRuleMoved(id: Long, moved: Int) = db.organizationRuleDao().recordRuleExecution(id, moved)
    suspend fun deleteOrganizationRule(id: Long) = db.organizationRuleDao().deleteRule(id)
    suspend fun removeOrganizationRule(id: Long) = deleteOrganizationRule(id)

    suspend fun verifyBookmarksIntegrity() {
        val bookmarks = allBookmarks.first()
        bookmarks.forEach { bm ->
            if (!isLocalFileUriAlive(bm.uri)) {
                removeBookmark(bm.id)
            }
        }
    }

    fun isLocalFileUriAlive(uriString: String): Boolean {
        val parsed = Uri.parse(uriString)
        return if (parsed.scheme == "file") {
            val p = parsed.path
            p != null && File(p).exists()
        } else true
    }
}
