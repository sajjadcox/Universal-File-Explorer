package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingHistoryDao {
    @Query("SELECT * FROM reading_history ORDER BY lastOpenedAt DESC LIMIT 50")
    fun getRecentHistory(): Flow<List<ReadingHistoryEntity>>

    @Query("SELECT * FROM reading_history WHERE uri = :uri LIMIT 1")
    suspend fun getHistoryForUri(uri: String): ReadingHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entry: ReadingHistoryEntity)

    @Query("DELETE FROM reading_history WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("DELETE FROM reading_history")
    suspend fun clearAll()
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE uri = :uri ORDER BY position ASC")
    fun getBookmarksForUri(uri: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE uri = :uri AND position = :position LIMIT 1")
    suspend fun findExact(uri: String, position: Long): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: Long)

    @Query("DELETE FROM bookmarks WHERE uri = :uri AND position = :position")
    suspend fun deleteBookmarkAt(uri: String, position: Long)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 20")
    fun getSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE `query` = :query")
    suspend fun deleteSearch(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()
}

@Dao
interface TrashDao {
    @Query("SELECT * FROM trash_entries ORDER BY deletedAt DESC")
    fun getTrashEntries(): Flow<List<TrashEntryEntity>>

    @Query("SELECT * FROM trash_entries WHERE deletedAt < :cutoffMs")
    suspend fun getEntriesOlderThan(cutoffMs: Long): List<TrashEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrashEntry(entry: TrashEntryEntity): Long

    @Query("DELETE FROM trash_entries WHERE id = :id")
    suspend fun deleteTrashEntry(id: Long)

    @Query("DELETE FROM trash_entries")
    suspend fun clearAllTrash()
}

@Dao
interface ConversionJobDao {
    @Query("SELECT * FROM conversion_jobs ORDER BY createdAt DESC LIMIT 50")
    fun getConversionJobs(): Flow<List<ConversionJobEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: ConversionJobEntity): Long

    @Query("UPDATE conversion_jobs SET status = :status, progress = :progress, outputUri = :outputUri, outputPath = :outputPath, errorMessage = :error WHERE id = :id")
    suspend fun updateJobStatus(
        id: Long,
        status: String,
        progress: Int,
        outputUri: String? = null,
        outputPath: String? = null,
        error: String? = null
    )

    @Query("DELETE FROM conversion_jobs WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    suspend fun clearFinishedJobs()
}

// ==================== PHASE 2 DAOS ====================

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_entries ORDER BY addedAt DESC")
    fun getAllVaultEntries(): Flow<List<VaultEntryEntity>>

    @Query("SELECT * FROM vault_entries ORDER BY addedAt DESC")
    suspend fun getAllVaultEntriesSnapshot(): List<VaultEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaultEntry(entry: VaultEntryEntity): Long

    @Query("UPDATE vault_entries SET isHidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Query("UPDATE vault_entries SET folderName = :folderName WHERE id = :id")
    suspend fun moveToFolder(id: Long, folderName: String)

    @Query("UPDATE vault_entries SET lastAccessedAt = :ts WHERE id = :id")
    suspend fun touchAccessed(id: Long, ts: Long = System.currentTimeMillis())

    @Query("DELETE FROM vault_entries WHERE id = :id")
    suspend fun deleteVaultEntry(id: Long)
}

@Dao
interface FileActivityDao {
    @Query("SELECT * FROM file_activity ORDER BY timestamp DESC LIMIT 200")
    fun getRecentActivity(): Flow<List<FileActivityEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(entry: FileActivityEntity): Long

    @Query("DELETE FROM file_activity")
    suspend fun clearActivity()
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY sortOrder ASC, addedAt ASC")
    fun getTracksForPlaylist(playlistId: Long): Flow<List<PlaylistTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: PlaylistTrackEntity): Long

    @Query("DELETE FROM playlist_tracks WHERE id = :trackId")
    suspend fun deleteTrack(trackId: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: Long)
}

@Dao
interface MediaHistoryDao {
    @Query("SELECT * FROM media_history ORDER BY lastPlayedAt DESC LIMIT 50")
    fun getMediaHistory(): Flow<List<MediaHistoryEntity>>

    @Query("SELECT * FROM media_history WHERE uri = :uri LIMIT 1")
    suspend fun getMediaHistoryForUri(uri: String): MediaHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMediaHistory(entry: MediaHistoryEntity)

    @Query("DELETE FROM media_history")
    suspend fun clearMediaHistory()
}

@Dao
interface CustomCategoryDao {
    @Query("SELECT * FROM custom_categories ORDER BY createdAt ASC")
    fun getCustomCategories(): Flow<List<CustomCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomCategory(category: CustomCategoryEntity): Long

    @Query("DELETE FROM custom_categories WHERE id = :id")
    suspend fun deleteCustomCategory(id: Long)
}

@Dao
interface OrganizationRuleDao {
    @Query("SELECT * FROM organization_rules ORDER BY id ASC")
    fun getRules(): Flow<List<OrganizationRuleEntity>>

    @Query("SELECT * FROM organization_rules ORDER BY id ASC")
    suspend fun getRulesSnapshot(): List<OrganizationRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: OrganizationRuleEntity): Long

    @Query("UPDATE organization_rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE organization_rules SET lastRunAt = :ts, filesMovedCount = filesMovedCount + :moved WHERE id = :id")
    suspend fun recordRuleExecution(id: Long, moved: Int, ts: Long = System.currentTimeMillis())

    @Query("DELETE FROM organization_rules WHERE id = :id")
    suspend fun deleteRule(id: Long)
}
