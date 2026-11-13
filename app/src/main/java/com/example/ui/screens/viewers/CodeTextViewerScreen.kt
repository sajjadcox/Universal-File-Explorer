package com.example.ui.screens.viewers

import android.net.Uri
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.storage.AdvancedStorageOperations
import com.example.domain.model.FileItem
import com.example.domain.tools.ProductivityEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class CodeViewMode {
    SOURCE, PREVIEW, EDIT
}

@Composable
fun CodeTextViewerScreen(
    fileItem: FileItem,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storageOps = remember { AdvancedStorageOperations(context) }
    val ext = remember(fileItem) { fileItem.extension.lowercase() }

    var isLoading by remember { mutableStateOf(true) }
    var rawText by remember { mutableStateOf("") }
    var editedText by remember { mutableStateOf("") }
    var totalLoadedBytes by remember { mutableIntStateOf(512 * 1024) }
    var isTruncated by remember { mutableStateOf(false) }

    var showLineNumbers by remember { mutableStateOf(true) }
    var wordWrap by remember { mutableStateOf(true) }
    var viewMode by remember { mutableStateOf(CodeViewMode.SOURCE) }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var validationBanner by remember { mutableStateOf<String?>(null) }
    var showOverwriteConfirm by remember { mutableStateOf(false) }

    val supportsPreview = ext in setOf("md", "markdown", "html", "htm")
    val supportsJsonFormat = ext == "json"
    val supportsXmlFormat = ext in setOf("xml", "svg", "xhtml")

    val saveCopyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { destUri: Uri? ->
        if (destUri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(destUri)?.bufferedWriter()?.use {
                        it.write(editedText)
                    }
                    withContext(Dispatchers.Main) {
                        rawText = editedText
                        onMessage("Saved copy successfully")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onMessage("Save failed: ${e.message}")
                    }
                }
            }
        }
    }

    LaunchedEffect(fileItem.uri, totalLoadedBytes) {
        isLoading = true
        val loaded = withContext(Dispatchers.IO) {
            storageOps.readTextPreview(fileItem.uri, totalLoadedBytes)
        }
        rawText = loaded
        if (editedText.isEmpty() || !isTruncated) {
            editedText = loaded
        }
        isTruncated = fileItem.size > loaded.toByteArray(Charsets.UTF_8).size
        isLoading = false
    }

    val lines = remember(rawText) { rawText.lines() }
    val matchingLineIndices = remember(lines, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else lines.mapIndexedNotNull { index, line ->
            if (line.contains(searchQuery, ignoreCase = true)) index else null
        }
    }
    var activeMatchCursor by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("code_text_viewer")
    ) {
        // Toolbar strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = viewMode == CodeViewMode.SOURCE,
                onClick = { viewMode = CodeViewMode.SOURCE },
                label = { Text("Source") },
                leadingIcon = { Icon(Icons.Default.Code, null, modifier = Modifier.size(16.dp)) }
            )
            if (supportsPreview) {
                FilterChip(
                    selected = viewMode == CodeViewMode.PREVIEW,
                    onClick = { viewMode = CodeViewMode.PREVIEW },
                    label = { Text(if (ext in setOf("html", "htm")) "Safe HTML" else "Markdown") },
                    leadingIcon = { Icon(Icons.Default.Preview, null, modifier = Modifier.size(16.dp)) }
                )
            }
            FilterChip(
                selected = viewMode == CodeViewMode.EDIT,
                onClick = {
                    editedText = rawText
                    viewMode = CodeViewMode.EDIT
                },
                label = { Text("Edit") },
                leadingIcon = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp)) }
            )
            FilterChip(
                selected = showLineNumbers,
                onClick = { showLineNumbers = !showLineNumbers },
                label = { Text("Lines") },
                leadingIcon = { Icon(Icons.Default.FormatListNumbered, null, modifier = Modifier.size(16.dp)) }
            )
            FilterChip(
                selected = wordWrap,
                onClick = { wordWrap = !wordWrap },
                label = { Text("Wrap") },
                leadingIcon = { Icon(Icons.Default.WrapText, null, modifier = Modifier.size(16.dp)) }
            )
            FilterChip(
                selected = showSearchBar,
                onClick = { showSearchBar = !showSearchBar },
                label = { Text("Find") },
                leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp)) }
            )
            if (supportsJsonFormat) {
                OutlinedButton(
                    onClick = {
                        ProductivityEngine.formatAndValidateJson(rawText).fold(
                            onSuccess = { pretty ->
                                rawText = pretty
                                editedText = pretty
                                validationBanner = "Valid JSON • Formatted cleanly"
                            },
                            onFailure = { err ->
                                validationBanner = "Invalid JSON: ${err.message}"
                            }
                        )
                    }
                ) {
                    Icon(Icons.Default.FormatIndentIncrease, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Validate & Format JSON")
                }
            }
            if (supportsXmlFormat) {
                OutlinedButton(
                    onClick = {
                        ProductivityEngine.formatAndValidateXml(rawText).fold(
                            onSuccess = { pretty ->
                                rawText = pretty
                                editedText = pretty
                                validationBanner = "Valid XML • Pretty printed"
                            },
                            onFailure = { err ->
                                validationBanner = "Invalid XML: ${err.message}"
                            }
                        )
                    }
                ) {
                    Icon(Icons.Default.FormatIndentIncrease, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Validate & Format XML")
                }
            }
        }

        if (validationBanner != null) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(validationBanner!!, style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(onClick = { validationBanner = null }) { Text("Dismiss") }
                }
            }
        }

        if (showSearchBar) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        activeMatchCursor = 0
                    },
                    placeholder = { Text("Search text...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (matchingLineIndices.isEmpty()) "0" else "${activeMatchCursor + 1}/${matchingLineIndices.size}",
                    style = MaterialTheme.typography.labelSmall
                )
                IconButton(
                    onClick = {
                        if (matchingLineIndices.isNotEmpty()) {
                            activeMatchCursor = (activeMatchCursor - 1 + matchingLineIndices.size) % matchingLineIndices.size
                        }
                    }
                ) { Icon(Icons.Default.KeyboardArrowUp, "Previous match") }
                IconButton(
                    onClick = {
                        if (matchingLineIndices.isNotEmpty()) {
                            activeMatchCursor = (activeMatchCursor + 1) % matchingLineIndices.size
                        }
                    }
                ) { Icon(Icons.Default.KeyboardArrowDown, "Next match") }
            }
        }

        if (isTruncated) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Showing first ${totalLoadedBytes / 1024} KB (streaming mode)",
                        style = MaterialTheme.typography.labelSmall
                    )
                    TextButton(onClick = { totalLoadedBytes += 512 * 1024 }) {
                        Text("Load +512 KB")
                    }
                }
            }
        }

        HorizontalDivider()

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            when (viewMode) {
                CodeViewMode.SOURCE -> {
                    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                    val activeHighlightLine = matchingLineIndices.getOrNull(activeMatchCursor)
                    val codeModifier = if (wordWrap) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .fillMaxSize()
                            .horizontalScroll(rememberScrollState())
                    }

                    SelectionContainer {
                        LazyColumn(
                            modifier = codeModifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            itemsIndexed(lines) { idx, line ->
                                val isMatchLine = idx in matchingLineIndices
                                val isActiveLine = idx == activeHighlightLine
                                val bgColor = when {
                                    isActiveLine -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                    isMatchLine -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                    else -> Color.Transparent
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(bgColor)
                                        .padding(vertical = 1.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    if (showLineNumbers) {
                                        Text(
                                            text = "%4d ".format(idx + 1),
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            ),
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                    }
                                    Text(
                                        text = highlightCodeLine(line, ext, isDark, searchQuery),
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            lineHeight = 19.sp
                                        ),
                                        softWrap = wordWrap
                                    )
                                }
                            }
                        }
                    }
                }

                CodeViewMode.PREVIEW -> {
                    if (ext in setOf("html", "htm")) {
                        // Strict sandboxed HTML preview (no JavaScript, no file access)
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = false
                                    settings.allowFileAccess = false
                                    settings.allowContentAccess = false
                                    settings.loadsImagesAutomatically = false
                                }
                            },
                            update = { wv ->
                                wv.loadDataWithBaseURL(null, rawText, "text/html", "utf-8", null)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        MarkdownPreviewView(
                            markdown = rawText,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                CodeViewMode.EDIT -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    saveCopyLauncher.launch("edited_${fileItem.name}")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save Copy")
                            }
                            if (fileItem.canWrite) {
                                OutlinedButton(
                                    onClick = { showOverwriteConfirm = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Overwrite Original")
                                }
                            }
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ) {
                            BasicTextField(
                                value = editedText,
                                onValueChange = { editedText = it },
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showOverwriteConfirm) {
        AlertDialog(
            onDismissRequest = { showOverwriteConfirm = false },
            title = { Text("Overwrite Original File?", fontWeight = FontWeight.Bold) },
            text = { Text("This will replace the original contents of '${fileItem.name}'. Consider using Save Copy instead if you want to preserve the original.") },
            confirmButton = {
                Button(onClick = {
                    showOverwriteConfirm = false
                    scope.launch(Dispatchers.IO) {
                        val ok = try {
                            if (fileItem.uri.scheme == "file") {
                                File(fileItem.uri.path ?: "").writeText(editedText)
                                true
                            } else {
                                context.contentResolver.openOutputStream(fileItem.uri, "wt")?.bufferedWriter()?.use {
                                    it.write(editedText)
                                }
                                true
                            }
                        } catch (_: Exception) {
                            false
                        }
                        withContext(Dispatchers.Main) {
                            if (ok) {
                                rawText = editedText
                                onMessage("Original file updated")
                            } else {
                                onMessage("Unable to overwrite (read-only URI); use Save Copy instead")
                            }
                        }
                    }
                }) { Text("Overwrite") }
            },
            dismissButton = {
                TextButton(onClick = { showOverwriteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MarkdownPreviewView(markdown: String, modifier: Modifier = Modifier) {
    SelectionContainer {
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            var inCodeBlock = false
            val codeBuffer = mutableListOf<String>()

            markdown.lines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("```")) {
                    if (inCodeBlock) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = codeBuffer.joinToString("\n"),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        codeBuffer.clear()
                        inCodeBlock = false
                    } else {
                        inCodeBlock = true
                    }
                    return@forEach
                }

                if (inCodeBlock) {
                    codeBuffer.add(line)
                    return@forEach
                }

                when {
                    trimmed.startsWith("# ") -> Text(
                        text = trimmed.removePrefix("# "),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    trimmed.startsWith("## ") -> Text(
                        text = trimmed.removePrefix("## "),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    trimmed.startsWith("### ") -> Text(
                        text = trimmed.removePrefix("### "),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    trimmed.startsWith("- ") || trimmed.startsWith("* ") -> Text(
                        text = "• ${trimmed.drop(2)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    trimmed.startsWith("> ") -> Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = trimmed.removePrefix("> "),
                            fontStyle = FontStyle.Italic,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    trimmed == "---" || trimmed == "***" -> HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    trimmed.isNotEmpty() -> Text(
                        text = renderInlineMarkdown(line),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    else -> Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

private fun renderInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            if (text.startsWith("**", i)) {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            } else if (text[i] == '`') {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22888888))) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                    continue
                }
            }
            append(text[i])
            i++
        }
    }
}

private val CODE_KEYWORDS = setOf(
    "fun", "val", "var", "class", "interface", "object", "enum", "sealed", "data", "package", "import",
    "return", "if", "else", "when", "for", "while", "do", "break", "continue", "try", "catch", "finally",
    "throw", "suspend", "private", "public", "internal", "protected", "override", "open", "abstract",
    "def", "lambda", "async", "await", "yield", "from", "as", "with", "pass", "None", "True", "False",
    "function", "const", "let", "export", "default", "switch", "case", "new", "this", "super", "extends",
    "implements", "void", "int", "long", "float", "double", "boolean", "bool", "char", "string", "struct",
    "select", "SELECT", "from", "FROM", "where", "WHERE", "insert", "INSERT", "into", "INTO", "update",
    "UPDATE", "delete", "DELETE", "create", "CREATE", "table", "TABLE", "join", "JOIN", "true", "false", "null"
)

private fun highlightCodeLine(
    line: String,
    ext: String,
    isDark: Boolean,
    searchQuery: String
): AnnotatedString {
    val keywordColor = if (isDark) Color(0xFFCC7832) else Color(0xFF0033B3)
    val stringColor = if (isDark) Color(0xFF6A8759) else Color(0xFF067D17)
    val commentColor = if (isDark) Color(0xFF808080) else Color(0xFF7A7E85)
    val numberColor = if (isDark) Color(0xFF6897BB) else Color(0xFF1750EB)
    val tagColor = if (isDark) Color(0xFFE8BF6A) else Color(0xFF0033B3)
    val defaultColor = if (isDark) Color(0xFFE6E1E5) else Color(0xFF1C1B1F)

    return buildAnnotatedString {
        val trimmed = line.trimStart()
        val isFullComment = trimmed.startsWith("//") ||
            (ext in setOf("py", "sh", "yaml", "yml", "toml", "ini", "cfg") && trimmed.startsWith("#")) ||
            (ext == "sql" && trimmed.startsWith("--"))

        if (isFullComment) {
            withStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)) {
                append(line)
            }
            return@buildAnnotatedString
        }

        val tokenRegex = Regex("""("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|</?[A-Za-z0-9_:-]+>?|\b\d+(?:\.\d+)?\b|\b[A-Za-z_][A-Za-z0-9_]*\b)""")
        var lastIndex = 0
        for (match in tokenRegex.findAll(line)) {
            if (match.range.first > lastIndex) {
                withStyle(SpanStyle(color = defaultColor)) {
                    append(line.substring(lastIndex, match.range.first))
                }
            }
            val token = match.value
            val color = when {
                token.startsWith("\"") || token.startsWith("'") -> stringColor
                token.startsWith("<") -> tagColor
                token.first().isDigit() -> numberColor
                token in CODE_KEYWORDS -> keywordColor
                else -> defaultColor
            }
            val highlightBg = if (searchQuery.isNotBlank() && token.contains(searchQuery, ignoreCase = true)) {
                Color(0x55FFEB3B)
            } else {
                Color.Transparent
            }
            withStyle(SpanStyle(color = color, background = highlightBg)) {
                append(token)
            }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < line.length) {
            withStyle(SpanStyle(color = defaultColor)) {
                append(line.substring(lastIndex))
            }
        }
    }
}

private fun Color.luminance(): Float {
    return 0.299f * red + 0.587f * green + 0.114f * blue
}
