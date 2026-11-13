package com.example.ui.screens.viewers

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.data.db.LocalDataRepository
import com.example.domain.engine.FileInspectionResult
import com.example.domain.engine.FileSignatureDetector
import com.example.domain.engine.ViewerType
import com.example.domain.model.FileItem
import com.example.media.AudioPlayerManager
import kotlinx.coroutines.launch

import com.example.domain.model.ReaderFontChoice
import com.example.domain.model.ReaderTheme
import com.example.ui.components.SmartAssistantBottomSheet

@Composable
fun UniversalViewerHost(
    fileItem: FileItem,
    galleryOrQueueContext: List<FileItem>,
    localRepo: LocalDataRepository,
    audioPlayerManager: AudioPlayerManager,
    onBack: () -> Unit,
    onShare: (FileItem) -> Unit,
    onMessage: (String) -> Unit,
    readerTheme: ReaderTheme = ReaderTheme.LIGHT,
    readerFont: ReaderFontChoice = ReaderFontChoice.SERIF,
    keepAwakeDefault: Boolean = false,
    playbackResumeEnabled: Boolean = true,
    onSmartAnalyze: ((FileItem) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    BackHandler(enabled = true) { onBack() }

    var inspection by remember(fileItem.uri) { mutableStateOf<FileInspectionResult?>(null) }
    var isHexMode by remember(fileItem.uri) { mutableStateOf(false) }
    var initialPosition by remember(fileItem.uri) { mutableLongStateOf(0L) }
    var isInspecting by remember(fileItem.uri) { mutableStateOf(true) }
    var showLocalAssistant by remember(fileItem.uri) { mutableStateOf(false) }

    val bookmarks by localRepo.getBookmarksForUri(fileItem.uri.toString())
        .collectAsState(initial = emptyList())
    val isBookmarked = bookmarks.isNotEmpty()

    LaunchedEffect(fileItem.uri) {
        isInspecting = true
        val res = FileSignatureDetector.inspectFile(context, fileItem)
        inspection = res
        val hist = localRepo.getHistoryForUri(fileItem.uri.toString())
        initialPosition = hist?.lastPosition ?: 0L
        localRepo.recordFileOpened(
            uri = fileItem.uri.toString(),
            name = fileItem.name,
            mimeType = res.detectedMimeType,
            viewerType = res.viewerType.name,
            size = fileItem.size
        )
        isInspecting = false
    }

    if (showLocalAssistant) {
        SmartAssistantBottomSheet(
            fileItem = fileItem,
            onDismiss = { showLocalAssistant = false },
            onOpenInViewer = { showLocalAssistant = false }
        )
    }

    ViewerScaffold(
        fileItem = fileItem,
        inspection = inspection,
        isBookmarked = isBookmarked,
        isHexMode = isHexMode,
        onBack = onBack,
        onToggleBookmark = {
            scope.launch {
                if (isBookmarked) {
                    bookmarks.forEach { localRepo.removeBookmark(it.id) }
                    onMessage("Bookmark removed")
                } else {
                    localRepo.addBookmark(
                        uri = fileItem.uri.toString(),
                        fileName = fileItem.name,
                        label = "Saved bookmark",
                        position = initialPosition
                    )
                    onMessage("Bookmarked ${fileItem.name}")
                }
            }
        },
        onToggleHexMode = { isHexMode = !isHexMode },
        onShare = { onShare(fileItem) },
        onSmartAnalyze = {
            if (onSmartAnalyze != null) onSmartAnalyze(fileItem) else showLocalAssistant = true
        }
    ) { contentModifier ->
        if (isInspecting) {
            Box(contentModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@ViewerScaffold
        }

        if (isHexMode) {
            HexBinaryViewerScreen(fileItem = fileItem, modifier = contentModifier)
            return@ViewerScaffold
        }

        val res = inspection
        when (res?.viewerType) {
            ViewerType.PDF -> PdfViewerScreen(
                fileItem = fileItem,
                initialPage = initialPosition.toInt().coerceAtLeast(0),
                onPageChanged = { page, total ->
                    initialPosition = page.toLong()
                    scope.launch {
                        localRepo.updateReadingPosition(fileItem.uri.toString(), page.toLong(), total.toLong())
                    }
                },
                onMessage = onMessage,
                modifier = contentModifier,
                keepAwakeDefault = keepAwakeDefault
            )
            ViewerType.OFFICE_WORD -> WordDocumentViewer(
                fileItem = fileItem,
                onMessage = onMessage,
                modifier = contentModifier,
                initialReaderTheme = readerTheme,
                initialReaderFont = readerFont,
                keepAwakeDefault = keepAwakeDefault
            )
            ViewerType.OFFICE_SHEET -> SpreadsheetViewer(
                fileItem = fileItem,
                onMessage = onMessage,
                modifier = contentModifier
            )
            ViewerType.OFFICE_SLIDE -> PresentationViewer(
                fileItem = fileItem,
                onMessage = onMessage,
                modifier = contentModifier
            )
            ViewerType.EBOOK -> EbookViewer(
                fileItem = fileItem,
                initialChapter = initialPosition.toInt().coerceAtLeast(0),
                onChapterChanged = { chap, total ->
                    initialPosition = chap.toLong()
                    scope.launch {
                        localRepo.updateReadingPosition(fileItem.uri.toString(), chap.toLong(), total.toLong())
                    }
                },
                modifier = contentModifier,
                initialReaderTheme = readerTheme,
                initialReaderFont = readerFont,
                keepAwakeDefault = keepAwakeDefault
            )
            ViewerType.EMAIL -> EmailViewer(
                fileItem = fileItem,
                modifier = contentModifier
            )
            ViewerType.IMAGE -> ImageViewerScreen(
                fileItem = fileItem,
                galleryList = galleryOrQueueContext.filter { !it.isDirectory },
                onMessage = onMessage,
                modifier = contentModifier
            )
            ViewerType.AUDIO -> AudioPlayerScreen(
                fileItem = fileItem,
                playlist = galleryOrQueueContext.filter { !it.isDirectory },
                audioPlayerManager = audioPlayerManager,
                onMessage = onMessage,
                modifier = contentModifier,
                localRepo = localRepo,
                resumeEnabled = playbackResumeEnabled
            )
            ViewerType.VIDEO -> VideoPlayerScreen(
                fileItem = fileItem,
                initialPositionMs = if (playbackResumeEnabled) initialPosition else 0L,
                onPositionChanged = { pos, dur ->
                    initialPosition = pos
                    scope.launch {
                        localRepo.updateReadingPosition(fileItem.uri.toString(), pos, dur)
                    }
                },
                onMessage = onMessage,
                modifier = contentModifier
            )
            ViewerType.ARCHIVE -> ArchiveViewerScreen(
                fileItem = fileItem,
                onMessage = onMessage,
                modifier = contentModifier
            )
            ViewerType.CODE_TEXT -> CodeTextViewerScreen(
                fileItem = fileItem,
                onMessage = onMessage,
                modifier = contentModifier
            )
            ViewerType.APK -> ApkViewerScreen(
                fileItem = fileItem,
                onMessage = onMessage,
                modifier = contentModifier
            )
            ViewerType.SQLITE_DB -> SqliteViewerScreen(
                fileItem = fileItem,
                modifier = contentModifier
            )
            ViewerType.FONT -> FontViewerScreen(
                fileItem = fileItem,
                modifier = contentModifier
            )
            ViewerType.HEX_BINARY -> HexBinaryViewerScreen(
                fileItem = fileItem,
                modifier = contentModifier
            )
            ViewerType.UNSUPPORTED, null -> UnsupportedFallbackScreen(
                fileItem = fileItem,
                inspection = res,
                onOpenHex = { isHexMode = true },
                onShare = { onShare(fileItem) },
                modifier = contentModifier
            )
        }
    }
}
