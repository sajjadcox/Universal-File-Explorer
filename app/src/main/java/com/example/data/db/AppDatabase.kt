package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ReadingHistoryEntity::class,
        BookmarkEntity::class,
        SearchHistoryEntity::class,
        TrashEntryEntity::class,
        ConversionJobEntity::class,
        VaultEntryEntity::class,
        FileActivityEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        MediaHistoryEntity::class,
        CustomCategoryEntity::class,
        OrganizationRuleEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun readingHistoryDao(): ReadingHistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun trashDao(): TrashDao
    abstract fun conversionJobDao(): ConversionJobDao

    // Phase 2 DAOs
    abstract fun vaultDao(): VaultDao
    abstract fun fileActivityDao(): FileActivityDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun mediaHistoryDao(): MediaHistoryDao
    abstract fun customCategoryDao(): CustomCategoryDao
    abstract fun organizationRuleDao(): OrganizationRuleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `vault_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `vaultFileName` TEXT NOT NULL,
                        `originalName` TEXT NOT NULL,
                        `originalUri` TEXT NOT NULL,
                        `folderName` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `sha256Original` TEXT NOT NULL,
                        `isHidden` INTEGER NOT NULL,
                        `keyMode` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        `lastAccessedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_vault_entries_vaultFileName` ON `vault_entries` (`vaultFileName`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `file_activity` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `action` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `fileUri` TEXT NOT NULL,
                        `details` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlists` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_playlists_name` ON `playlists` (`name`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlist_tracks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `playlistId` INTEGER NOT NULL,
                        `trackUri` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `addedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_playlist_tracks_playlistId_trackUri` ON `playlist_tracks` (`playlistId`, `trackUri`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `media_history` (
                        `uri` TEXT NOT NULL PRIMARY KEY,
                        `title` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `isVideo` INTEGER NOT NULL,
                        `positionMs` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `playCount` INTEGER NOT NULL,
                        `lastPlayedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_categories` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `extensionsCsv` TEXT NOT NULL,
                        `accentHex` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_custom_categories_name` ON `custom_categories` (`name`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `organization_rules` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `extensionsCsv` TEXT NOT NULL,
                        `targetSubfolder` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `lastRunAt` INTEGER NOT NULL,
                        `filesMovedCount` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_organization_rules_name` ON `organization_rules` (`name`)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "universal_file_explorer.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
