package com.example.domain.model

enum class SortField {
    NAME,
    DATE,
    SIZE,
    TYPE
}

enum class SortOrder {
    ASCENDING,
    DESCENDING
}

data class SortConfig(
    val field: SortField = SortField.NAME,
    val order: SortOrder = SortOrder.ASCENDING
)

enum class ViewMode {
    LIST,
    GRID
}

enum class ThemeMode {
    LIGHT,
    DARK,
    AMOLED,
    SYSTEM
}

enum class AccentColor(val label: String, val primaryHex: Long) {
    BLUE("Ocean Blue", 0xFF0061A4),
    EMERALD("Emerald", 0xFF006D40),
    VIOLET("Royal Violet", 0xFF6750A4),
    TEAL("Cyber Teal", 0xFF006876),
    AMBER("Warm Amber", 0xFF8C5000),
    CRIMSON("Crimson", 0xFFB3261E)
}

enum class FileOpenMode(val label: String) {
    UNIVERSAL_VIEWER("Built-in Universal Viewer"),
    SMART_ASSISTANT("Analyze First (Smart Assistant)"),
    EXTERNAL_APP("Android External App")
}

enum class ReaderTheme(val label: String) {
    SYSTEM("App Theme"),
    LIGHT("Paper Light"),
    SEPIA("Sepia"),
    DARK("Night Ink")
}

enum class ReaderFontChoice(val label: String) {
    SANS("Sans-Serif"),
    SERIF("Classic Serif"),
    MONO("Monospace")
}
