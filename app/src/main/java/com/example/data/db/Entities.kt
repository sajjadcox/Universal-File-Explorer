package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "reading_history")
data class ReadingHistoryEntity(
    @PrimaryKey val uri: String,
    val name: String,
    val mimeType: String,
    val viewerType: String,
    val size: Long,
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val lastPosition: Long = 0L, // page index or playback ms
    val totalLength: Long = 0L   // total pages or duration ms
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val fileName: String,
    val label: String,
    val position: Long = 0L, // page number or chapter index
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "trash_entries")
data class TrashEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalName: String,
    val originalUri: String,
    val trashFilePath: String,
    val mimeType: String,
    val size: Long,
    val deletedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "conversion_jobs")
data class ConversionJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceName: String,
    val sourceUri: String,
    val targetFormat: String,
    val outputUri: String? = null,
    val outputPath: String? = null,
    val status: String, // PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
    val progress: Int = 0,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

// ==================== PHASE 2 ENTITIES ====================

@Entity(
    tableName = "vault_entries",
    indices = [Index(value = ["vaultFileName"], unique = true)]
)
data class VaultEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vaultFileName: String,
    val originalName: String,
    val originalUri: String,
    val folderName: String = "General",
    val mimeType: String,
    val sizeBytes: Long,
    val sha256Original: String,
    val isHidden: Boolean = false,
    val keyMode: String = "KEYSTORE", // KEYSTORE or PASSWORD
    val addedAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "file_activity")
data class FileActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val action: String, // OPENED, ENCRYPTED, DECRYPTED, VAULT_IMPORT, VAULT_EXPORT, COPIED, MOVED, RENAMED, TRASHED, RESTORED, DELETED, SHARED, HASH_CHECKED, ORGANIZED
    val fileName: String,
    val fileUri: String,
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlists",
    indices = [Index(value = ["name"], unique = true)]
)
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_tracks",
    indices = [Index(value = ["playlistId", "trackUri"], unique = true)]
)
data class PlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val trackUri: String,
    val title: String,
    val mimeType: String = "audio/*",
    val sizeBytes: Long = 0L,
    val sortOrder: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "media_history")
data class MediaHistoryEntity(
    @PrimaryKey val uri: String,
    val title: String,
    val mimeType: String,
    val isVideo: Boolean,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playCount: Int = 1,
    val lastPlayedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "custom_categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class CustomCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val extensionsCsv: String,
    val accentHex: Long = 0xFF0061A4,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "organization_rules",
    indices = [Index(value = ["name"], unique = true)]
)
data class OrganizationRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val extensionsCsv: String,
    val targetSubfolder: String,
    val enabled: Boolean = true,
    val lastRunAt: Long = 0L,
    val filesMovedCount: Int = 0
)
