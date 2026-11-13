package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AccentColor
import com.example.domain.model.FileOpenMode
import com.example.domain.model.ReaderFontChoice
import com.example.domain.model.ReaderTheme
import com.example.domain.model.SortConfig
import com.example.domain.model.SortField
import com.example.domain.model.SortOrder
import com.example.domain.model.ThemeMode
import com.example.domain.model.ViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val VIEW_MODE = stringPreferencesKey("view_mode")
        val SORT_FIELD = stringPreferencesKey("sort_field")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val SHOW_HIDDEN = booleanPreferencesKey("show_hidden")
        val FAVORITE_URIS = stringSetPreferencesKey("favorite_uris")
        val RECENT_URIS = stringPreferencesKey("recent_uris_csv")
        val APP_LANGUAGE = stringPreferencesKey("app_language")

        // Phase 2 Settings & Security Preferences
        val FILE_OPEN_MODE = stringPreferencesKey("file_open_mode")
        val THUMBNAILS_ENABLED = booleanPreferencesKey("thumbnails_enabled")
        val TRASH_RETENTION_DAYS = intPreferencesKey("trash_retention_days")
        val VAULT_PIN_HASH = stringPreferencesKey("vault_pin_hash")
        val VAULT_PIN_SALT = stringPreferencesKey("vault_pin_salt")
        val VAULT_BIOMETRIC_ENABLED = booleanPreferencesKey("vault_biometric_enabled")
        val VAULT_AUTO_LOCK_MINUTES = intPreferencesKey("vault_auto_lock_minutes")
        val VAULT_FAILED_ATTEMPTS = intPreferencesKey("vault_failed_attempts")
        val VAULT_LOCKOUT_UNTIL_MS = longPreferencesKey("vault_lockout_until_ms")
        val READER_THEME = stringPreferencesKey("reader_theme")
        val READER_FONT = stringPreferencesKey("reader_font")
        val READER_FONT_SIZE_SP = intPreferencesKey("reader_font_size_sp")
        val READER_KEEP_AWAKE = booleanPreferencesKey("reader_keep_awake")
        val PLAYBACK_RESUME_ENABLED = booleanPreferencesKey("playback_resume_enabled")
        val LOG_FILE_ACTIVITY = booleanPreferencesKey("log_file_activity")
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        when (preferences[PreferencesKeys.THEME_MODE]) {
            ThemeMode.DARK.name -> ThemeMode.DARK
            ThemeMode.AMOLED.name -> ThemeMode.AMOLED
            ThemeMode.SYSTEM.name -> ThemeMode.SYSTEM
            else -> ThemeMode.LIGHT
        }
    }

    val dynamicColorFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DYNAMIC_COLOR] ?: true
    }

    val accentColorFlow: Flow<AccentColor> = context.dataStore.data.map { preferences ->
        val raw = preferences[PreferencesKeys.ACCENT_COLOR]
        AccentColor.entries.firstOrNull { it.name == raw } ?: AccentColor.BLUE
    }

    val viewModeFlow: Flow<ViewMode> = context.dataStore.data.map { preferences ->
        when (preferences[PreferencesKeys.VIEW_MODE]) {
            ViewMode.GRID.name -> ViewMode.GRID
            else -> ViewMode.LIST
        }
    }

    val sortConfigFlow: Flow<SortConfig> = context.dataStore.data.map { preferences ->
        val field = when (preferences[PreferencesKeys.SORT_FIELD]) {
            SortField.DATE.name -> SortField.DATE
            SortField.SIZE.name -> SortField.SIZE
            SortField.TYPE.name -> SortField.TYPE
            else -> SortField.NAME
        }
        val order = when (preferences[PreferencesKeys.SORT_ORDER]) {
            SortOrder.DESCENDING.name -> SortOrder.DESCENDING
            else -> SortOrder.ASCENDING
        }
        SortConfig(field = field, order = order)
    }

    val showHiddenFilesFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SHOW_HIDDEN] ?: false
    }

    val favoriteUrisFlow: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.FAVORITE_URIS] ?: emptySet()
    }

    val recentUrisFlow: Flow<List<String>> = context.dataStore.data.map { preferences ->
        val raw = preferences[PreferencesKeys.RECENT_URIS] ?: ""
        if (raw.isBlank()) emptyList() else raw.split("|").filter { it.isNotBlank() }
    }

    val appLanguageFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.APP_LANGUAGE] ?: "en"
    }

    val fileOpenModeFlow: Flow<FileOpenMode> = context.dataStore.data.map { prefs ->
        FileOpenMode.entries.firstOrNull { it.name == prefs[PreferencesKeys.FILE_OPEN_MODE] }
            ?: FileOpenMode.UNIVERSAL_VIEWER
    }

    val thumbnailsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.THUMBNAILS_ENABLED] ?: true
    }

    val trashRetentionDaysFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.TRASH_RETENTION_DAYS] ?: 30
    }

    val vaultPinHashFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.VAULT_PIN_HASH]
    }

    val vaultPinSaltFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.VAULT_PIN_SALT]
    }

    val vaultBiometricEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.VAULT_BIOMETRIC_ENABLED] ?: true
    }

    val vaultAutoLockMinutesFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.VAULT_AUTO_LOCK_MINUTES] ?: 5
    }

    val vaultFailedAttemptsFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.VAULT_FAILED_ATTEMPTS] ?: 0
    }

    val vaultLockoutUntilMsFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.VAULT_LOCKOUT_UNTIL_MS] ?: 0L
    }

    val readerThemeFlow: Flow<ReaderTheme> = context.dataStore.data.map { prefs ->
        ReaderTheme.entries.firstOrNull { it.name == prefs[PreferencesKeys.READER_THEME] }
            ?: ReaderTheme.SYSTEM
    }

    val readerFontFlow: Flow<ReaderFontChoice> = context.dataStore.data.map { prefs ->
        ReaderFontChoice.entries.firstOrNull { it.name == prefs[PreferencesKeys.READER_FONT] }
            ?: ReaderFontChoice.SANS
    }

    val readerFontSizeSpFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.READER_FONT_SIZE_SP] ?: 16
    }

    val readerKeepAwakeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.READER_KEEP_AWAKE] ?: false
    }

    val playbackResumeEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.PLAYBACK_RESUME_ENABLED] ?: true
    }

    val logFileActivityFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.LOG_FILE_ACTIVITY] ?: true
    }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { it[PreferencesKeys.THEME_MODE] = themeMode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setAccentColor(accentColor: AccentColor) {
        context.dataStore.edit { it[PreferencesKeys.ACCENT_COLOR] = accentColor.name }
    }

    suspend fun setViewMode(viewMode: ViewMode) {
        context.dataStore.edit { it[PreferencesKeys.VIEW_MODE] = viewMode.name }
    }

    suspend fun setSortConfig(sortConfig: SortConfig) {
        context.dataStore.edit {
            it[PreferencesKeys.SORT_FIELD] = sortConfig.field.name
            it[PreferencesKeys.SORT_ORDER] = sortConfig.order.name
        }
    }

    suspend fun setShowHiddenFiles(show: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SHOW_HIDDEN] = show }
    }

    suspend fun toggleFavorite(uriString: String) {
        context.dataStore.edit { preferences ->
            val current = (preferences[PreferencesKeys.FAVORITE_URIS] ?: emptySet()).toMutableSet()
            if (current.contains(uriString)) current.remove(uriString) else current.add(uriString)
            preferences[PreferencesKeys.FAVORITE_URIS] = current
        }
    }

    suspend fun addRecentFile(uriString: String) {
        context.dataStore.edit { preferences ->
            val raw = preferences[PreferencesKeys.RECENT_URIS] ?: ""
            val list = raw.split("|").filter { it.isNotBlank() }.toMutableList()
            list.remove(uriString)
            list.add(0, uriString)
            preferences[PreferencesKeys.RECENT_URIS] = list.take(30).joinToString("|")
        }
    }

    suspend fun setAppLanguage(lang: String) {
        context.dataStore.edit { it[PreferencesKeys.APP_LANGUAGE] = lang }
    }

    suspend fun setFileOpenMode(mode: FileOpenMode) {
        context.dataStore.edit { it[PreferencesKeys.FILE_OPEN_MODE] = mode.name }
    }

    suspend fun setThumbnailsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.THUMBNAILS_ENABLED] = enabled }
    }

    suspend fun setTrashRetentionDays(days: Int) {
        context.dataStore.edit { it[PreferencesKeys.TRASH_RETENTION_DAYS] = days }
    }

    suspend fun saveVaultPinCredentials(hash: String, salt: String) {
        context.dataStore.edit {
            it[PreferencesKeys.VAULT_PIN_HASH] = hash
            it[PreferencesKeys.VAULT_PIN_SALT] = salt
            it[PreferencesKeys.VAULT_FAILED_ATTEMPTS] = 0
            it[PreferencesKeys.VAULT_LOCKOUT_UNTIL_MS] = 0L
        }
    }

    suspend fun clearVaultPinCredentials() {
        context.dataStore.edit {
            it.remove(PreferencesKeys.VAULT_PIN_HASH)
            it.remove(PreferencesKeys.VAULT_PIN_SALT)
            it[PreferencesKeys.VAULT_FAILED_ATTEMPTS] = 0
            it[PreferencesKeys.VAULT_LOCKOUT_UNTIL_MS] = 0L
        }
    }

    suspend fun setVaultBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.VAULT_BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setVaultAutoLockMinutes(minutes: Int) {
        context.dataStore.edit { it[PreferencesKeys.VAULT_AUTO_LOCK_MINUTES] = minutes }
    }

    suspend fun recordVaultFailedAttempt(attempts: Int, lockoutUntilMs: Long) {
        context.dataStore.edit {
            it[PreferencesKeys.VAULT_FAILED_ATTEMPTS] = attempts
            it[PreferencesKeys.VAULT_LOCKOUT_UNTIL_MS] = lockoutUntilMs
        }
    }

    suspend fun resetVaultLockout() {
        context.dataStore.edit {
            it[PreferencesKeys.VAULT_FAILED_ATTEMPTS] = 0
            it[PreferencesKeys.VAULT_LOCKOUT_UNTIL_MS] = 0L
        }
    }

    suspend fun setReaderTheme(theme: ReaderTheme) {
        context.dataStore.edit { it[PreferencesKeys.READER_THEME] = theme.name }
    }

    suspend fun setReaderFont(font: ReaderFontChoice) {
        context.dataStore.edit { it[PreferencesKeys.READER_FONT] = font.name }
    }

    suspend fun setReaderFontSizeSp(sizeSp: Int) {
        context.dataStore.edit { it[PreferencesKeys.READER_FONT_SIZE_SP] = sizeSp }
    }

    suspend fun setReaderKeepAwake(keepAwake: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.READER_KEEP_AWAKE] = keepAwake }
    }

    suspend fun setPlaybackResumeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.PLAYBACK_RESUME_ENABLED] = enabled }
    }

    suspend fun setLogFileActivity(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.LOG_FILE_ACTIVITY] = enabled }
    }
}
