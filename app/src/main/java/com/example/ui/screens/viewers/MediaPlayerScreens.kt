package com.example.ui.screens.viewers

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Sync
import com.example.data.db.LocalDataRepository
import com.example.data.storage.Phase2FileOperations
import com.example.data.storage.SubtitleCue
import android.graphics.BitmapFactory
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.storage.AdvancedStorageOperations
import com.example.domain.analyzer.ApkAndFileAnalyzer
import com.example.domain.analyzer.MediaDetailsInfo
import com.example.domain.converter.ConversionEngine
import com.example.domain.model.FileItem
import com.example.domain.tools.ProductivityEngine
import com.example.media.AudioPlayerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@Composable
fun AudioPlayerScreen(
    fileItem: FileItem,
    playlist: List<FileItem>,
    audioPlayerManager: AudioPlayerManager,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    localRepo: LocalDataRepository? = null,
    resumeEnabled: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val analyzer = remember { ApkAndFileAnalyzer(context) }
    val storageOps = remember { AdvancedStorageOperations(context) }
    val audioState by audioPlayerManager.state.collectAsStateWithLifecycle()

    var mediaInfo by remember { mutableStateOf<MediaDetailsInfo?>(null) }
    var showQueueDialog by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showTrimDialog by remember { mutableStateOf(false) }
    var showLibrarySheet by remember { mutableStateOf(false) }
    var showEqualizerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(fileItem.uri) {
        val items = if (playlist.any { it.uri == fileItem.uri }) playlist else listOf(fileItem)
        val idx = items.indexOfFirst { it.uri == fileItem.uri }.coerceAtLeast(0)
        if (audioState.currentUri != fileItem.uri) {
            audioPlayerManager.playPlaylist(items, idx)
        }
        mediaInfo = analyzer.extractMediaMetadata(fileItem.uri)
    }

    LaunchedEffect(audioState.currentUri) {
        audioState.currentUri?.let { u ->
            mediaInfo = analyzer.extractMediaMetadata(u)
            localRepo?.upsertMediaHistory(
                uri = u.toString(),
                title = audioState.currentTitle.ifBlank { fileItem.name },
                mediaType = "AUDIO",
                positionMs = if (resumeEnabled) audioState.positionMs else 0L,
                durationMs = audioState.durationMs
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("audio_player_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Album Art Card
        Card(
            modifier = Modifier.size(220.dp),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (mediaInfo?.albumArt != null) {
                    Image(
                        bitmap = mediaInfo!!.albumArt!!.asImageBitmap(),
                        contentDescription = "Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Audiotrack,
                        contentDescription = "Audio Track",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(88.dp)
                    )
                }
            }
        }

        // Title & Metadata
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = mediaInfo?.title?.ifBlank { audioState.currentTitle } ?: fileItem.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(
                    mediaInfo?.artist?.ifBlank { null },
                    mediaInfo?.album?.ifBlank { null },
                    mediaInfo?.bitrate?.takeIf { it != "Unknown" }
                ).joinToString(" • ").ifBlank { "${fileItem.extension.uppercase()} Audio" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            audioState.sleepTimerRemainingMinutes?.let { mins ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Text(
                        text = "Sleep timer: $mins min remaining",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Seek Bar
        val dur = audioState.durationMs.coerceAtLeast(1L)
        val pos = audioState.positionMs.coerceIn(0L, dur)
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = pos.toFloat(),
                onValueChange = { audioPlayerManager.seekTo(it.toLong()) },
                valueRange = 0f..dur.toFloat()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDurationMs(pos), style = MaterialTheme.typography.labelSmall)
                Text(formatDurationMs(dur), style = MaterialTheme.typography.labelSmall)
            }
        }

        // Primary Transport Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { audioPlayerManager.toggleShuffle() }) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (audioState.shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { audioPlayerManager.seekPrevious() }) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(34.dp))
            }
            Surface(
                modifier = Modifier.size(64.dp).clip(CircleShape).clickable { audioPlayerManager.togglePlayPause() },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (audioState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (audioState.isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
            IconButton(onClick = { audioPlayerManager.seekNext() }) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(34.dp))
            }
            IconButton(onClick = { audioPlayerManager.cycleRepeatMode() }) {
                Icon(
                    imageVector = if (audioState.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = "Repeat Mode",
                    tint = if (audioState.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Speed & Extra Tools Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(onClick = {
                val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                val next = speeds[(speeds.indexOf(audioState.playbackSpeed) + 1) % speeds.size]
                audioPlayerManager.setPlaybackSpeed(next)
            }) {
                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("${audioState.playbackSpeed}x")
            }
            OutlinedButton(onClick = { showQueueDialog = true }) {
                Icon(Icons.Default.QueueMusic, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Queue (${audioState.queue.size})")
            }
            OutlinedButton(onClick = { showSleepDialog = true }) {
                Icon(Icons.Default.Bedtime, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Sleep")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (localRepo != null) {
                OutlinedButton(
                    onClick = { showLibrarySheet = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Library & Playlists")
                }
            }
            OutlinedButton(
                onClick = { showEqualizerDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Equalizer, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Equalizer")
            }
        }

        OutlinedButton(
            onClick = { showTrimDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Trim Audio Clip (AAC/M4A)")
        }
    }

    if (showLibrarySheet && localRepo != null) {
        AudioLibraryAndPlaylistsSheet(
            currentTrack = fileItem,
            localRepo = localRepo,
            audioPlayerManager = audioPlayerManager,
            onDismiss = { showLibrarySheet = false },
            onMessage = onMessage
        )
    }

    if (showEqualizerDialog) {
        EqualizerDialog(
            audioSessionId = 0,
            onDismiss = { showEqualizerDialog = false }
        )
    }

    if (showQueueDialog) {
        AlertDialog(
            onDismissRequest = { showQueueDialog = false },
            title = { Text("Playback Queue", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.height(280.dp)) {
                    itemsIndexed(audioState.queue) { idx, qItem ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { audioPlayerManager.skipToIndex(idx) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${idx + 1}. ${qItem.name}",
                                fontWeight = if (idx == audioState.currentIndex) FontWeight.Bold else FontWeight.Normal,
                                color = if (idx == audioState.currentIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { audioPlayerManager.removeFromQueue(idx) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove from queue", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showQueueDialog = false }) { Text("Close") } }
        )
    }

    if (showSleepDialog) {
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = { Text("Sleep Timer", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(null to "Off", 5 to "5 minutes", 15 to "15 minutes", 30 to "30 minutes", 60 to "60 minutes").forEach { (mins, label) ->
                        OutlinedButton(
                            onClick = {
                                audioPlayerManager.setSleepTimer(mins)
                                showSleepDialog = false
                                onMessage(if (mins == null) "Sleep timer cleared" else "Sleep timer set for $mins min")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(label) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSleepDialog = false }) { Text("Cancel") } }
        )
    }

    if (showTrimDialog) {
        val totalMs = audioState.durationMs.coerceAtLeast(1000L)
        var range by remember(totalMs) { mutableStateOf(0f..totalMs.toFloat()) }
        AlertDialog(
            onDismissRequest = { showTrimDialog = false },
            title = { Text("Trim Audio Clip", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Start: ${formatDurationMs(range.start.toLong())}  •  End: ${formatDurationMs(range.endInclusive.toLong())}")
                    RangeSlider(
                        value = range,
                        onValueChange = { range = it },
                        valueRange = 0f..totalMs.toFloat()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val src = storageOps.copyUriToTempCache(audioState.currentUri ?: fileItem.uri, fileItem.extension)
                        if (src != null) {
                            val outDir = File(context.filesDir, "Trimmed_Audio").apply { mkdirs() }
                            val out = File(outDir, "${fileItem.name.substringBeforeLast('.')}_trim_${System.currentTimeMillis()}.m4a")
                            ProductivityEngine.trimAudioToM4a(src, out, range.start.toLong(), range.endInclusive.toLong())
                                .onSuccess { onMessage("Saved trimmed clip: ${it.name}") }
                                .onFailure { onMessage("Trim error: ${it.message}") }
                        }
                    }
                    showTrimDialog = false
                }) { Text("Trim & Save") }
            },
            dismissButton = { TextButton(onClick = { showTrimDialog = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    fileItem: FileItem,
    initialPositionMs: Long = 0L,
    onPositionChanged: (Long, Long) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val analyzer = remember { ApkAndFileAnalyzer(context) }
    val storageOps = remember { AdvancedStorageOperations(context) }

    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var subtitleUri by remember { mutableStateOf<Uri?>(null) }
    var subtitleCues by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var subtitleOffsetMs by remember { mutableLongStateOf(0L) }
    var currentPosMs by remember { mutableLongStateOf(initialPositionMs) }
    var showAudioTracksDialog by remember { mutableStateOf(false) }
    var showSubSyncDialog by remember { mutableStateOf(false) }
    var showScreenshotsDialog by remember { mutableStateOf(false) }
    var videoInfo by remember { mutableStateOf<MediaDetailsInfo?>(null) }
    var gestureHudText by remember { mutableStateOf<String?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }

    val exoPlayer = remember(fileItem.uri) {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(fileItem.uri)
            setMediaItem(mediaItem)
            if (initialPositionMs > 0) seekTo(initialPositionMs)
            prepare()
            playWhenReady = true
        }
    }

    val subtitlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            subtitleUri = uri
            val ext = uri.toString().substringAfterLast('.', "").lowercase()
            val subMime = when (ext) {
                "vtt" -> MimeTypes.TEXT_VTT
                "ass", "ssa" -> MimeTypes.TEXT_SSA
                else -> MimeTypes.APPLICATION_SUBRIP
            }
            val subConfig = MediaItem.SubtitleConfiguration.Builder(uri)
                .setMimeType(subMime)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            val pos = exoPlayer.currentPosition
            val updatedItem = MediaItem.Builder()
                .setUri(fileItem.uri)
                .setSubtitleConfigurations(listOf(subConfig))
                .build()
            exoPlayer.setMediaItem(updatedItem, pos)
            exoPlayer.prepare()
            val parsedRaw = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            }.getOrDefault("")
            subtitleCues = Phase2FileOperations.parseSrtOrVtt(parsedRaw)
            subtitleOffsetMs = 0L
            onMessage("Loaded subtitle (${subtitleCues.size} cues)")
        }
    }

    LaunchedEffect(fileItem.uri) {
        videoInfo = analyzer.extractMediaMetadata(fileItem.uri)
        while (true) {
            delay(250)
            currentPosMs = exoPlayer.currentPosition
            if (exoPlayer.duration > 0) {
                onPositionChanged(exoPlayer.currentPosition, exoPlayer.duration)
            }
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            if (exoPlayer.duration > 0) {
                onPositionChanged(exoPlayer.currentPosition, exoPlayer.duration)
            }
            exoPlayer.release()
        }
    }

    Column(modifier = modifier.fillMaxSize().background(Color.Black).testTag("video_player_screen")) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { gestureHudText = null },
                        onDragCancel = { gestureHudText = null }
                    ) { change, dragAmount ->
                        change.consume()
                        val isLeftSide = change.position.x < size.width / 2f
                        if (isLeftSide) {
                            // Brightness Gesture
                            val act = context as? Activity
                            val lp = act?.window?.attributes
                            if (lp != null) {
                                val current = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                                val next = (current - dragAmount / 600f).coerceIn(0.05f, 1.0f)
                                lp.screenBrightness = next
                                act.window.attributes = lp
                                gestureHudText = "Brightness: ${(next * 100).toInt()}%"
                            }
                        } else {
                            // Volume Gesture
                            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val delta = if (dragAmount < -12f) 1 else if (dragAmount > 12f) -1 else 0
                            if (delta != 0) {
                                val nextVol = (curVol + delta).coerceIn(0, maxVol)
                                am.setStreamVolume(AudioManager.STREAM_MUSIC, nextVol, 0)
                                gestureHudText = "Volume: ${(nextVol * 100 / maxVol.coerceAtLeast(1))}%"
                            }
                        }
                    }
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        setShowSubtitleButton(true)
                    }
                },
                update = { pv ->
                    pv.resizeMode = resizeMode
                },
                modifier = Modifier.fillMaxSize()
            )

            val activeSub = remember(subtitleCues, currentPosMs, subtitleOffsetMs) {
                if (subtitleCues.isEmpty()) null
                else Phase2FileOperations.activeSubtitleForPosition(subtitleCues, currentPosMs, subtitleOffsetMs)
            }
            if (!activeSub.isNullOrBlank()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 20.dp, vertical = 48.dp)
                ) {
                    Text(
                        text = activeSub,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            if (isFullscreen) {
                IconButton(
                    onClick = { isFullscreen = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                ) {
                    Icon(Icons.Default.FullscreenExit, contentDescription = "Exit Fullscreen", tint = Color.White)
                }
            }

            gestureHudText?.let { hud ->
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Text(
                        text = hud,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }

        // Video Advanced Controls Strip
        if (!isFullscreen) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        exoPlayer.pause()
                        exoPlayer.seekTo((exoPlayer.currentPosition - 40L).coerceAtLeast(0L))
                    }) {
                        Icon(Icons.Default.Replay10, contentDescription = "Step back")
                    }
                    IconButton(onClick = {
                        exoPlayer.pause()
                        exoPlayer.seekTo(exoPlayer.currentPosition + 40L)
                    }) {
                        Icon(Icons.Default.Forward10, contentDescription = "Step forward")
                    }
                    IconButton(onClick = {
                        val speeds = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f)
                        playbackSpeed = speeds[(speeds.indexOf(playbackSpeed) + 1) % speeds.size]
                        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                        onMessage("Speed: ${playbackSpeed}x")
                    }) {
                        Icon(Icons.Default.Speed, contentDescription = "Playback speed")
                    }
                    IconButton(onClick = {
                        resizeMode = when (resizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    }) {
                        Icon(Icons.Default.AspectRatio, contentDescription = "Aspect ratio")
                    }
                    IconButton(onClick = { subtitlePicker.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.ClosedCaption, contentDescription = "Load Subtitle (SRT/VTT/ASS)")
                    }
                    if (subtitleCues.isNotEmpty()) {
                        IconButton(onClick = { showSubSyncDialog = true }) {
                            Icon(Icons.Default.Sync, contentDescription = "Subtitle Sync Offset", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = { showAudioTracksDialog = true }) {
                        Icon(Icons.Default.Audiotrack, contentDescription = "Audio Track")
                    }
                    IconButton(onClick = {
                        val curMs = exoPlayer.currentPosition
                        scope.launch {
                            val bmp = analyzer.extractVideoThumbnail(fileItem.uri, curMs * 1000L)
                            if (bmp != null) {
                                val outDir = File(context.filesDir, "Screenshots").apply { mkdirs() }
                                val outFile = File(outDir, "${fileItem.name.substringBeforeLast('.')}_${curMs}ms.png")
                                FileOutputStream(outFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
                                bmp.recycle()
                                onMessage("Screenshot saved: ${outFile.name}")
                            } else {
                                onMessage("Unable to capture frame from this stream")
                            }
                        }
                    }) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Capture Screenshot")
                    }
                    IconButton(onClick = { showScreenshotsDialog = true }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Screenshots Gallery")
                    }
                    IconButton(onClick = { isFullscreen = true }) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen")
                    }
                    IconButton(onClick = {
                        val act = context as? Activity
                        if (act != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val params = PictureInPictureParams.Builder()
                                .setAspectRatio(Rational(16, 9))
                                .build()
                            act.enterPictureInPictureMode(params)
                        }
                    }) {
                        Icon(Icons.Default.PictureInPictureAlt, contentDescription = "Picture-in-Picture")
                    }
                }
            }
        }
    }

    if (showSubSyncDialog) {
        AlertDialog(
            onDismissRequest = { showSubSyncDialog = false },
            title = { Text("Subtitle Timing & Sync", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${subtitleCues.size} cues loaded")
                    Text("Sync Offset: ${"%.1f".format(subtitleOffsetMs / 1000f)}s")
                    Slider(
                        value = subtitleOffsetMs.toFloat(),
                        onValueChange = { subtitleOffsetMs = it.toLong() },
                        valueRange = -5000f..5000f
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showSubSyncDialog = false }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = {
                    subtitleCues = emptyList()
                    subtitleOffsetMs = 0L
                    showSubSyncDialog = false
                }) { Text("Clear Subtitles") }
            }
        )
    }

    if (showScreenshotsDialog) {
        val shotsDir = remember { File(context.filesDir, "Screenshots").apply { mkdirs() } }
        var shots by remember { mutableStateOf(shotsDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()) }
        AlertDialog(
            onDismissRequest = { showScreenshotsDialog = false },
            title = { Text("Saved Video Screenshots (${shots.size})", fontWeight = FontWeight.Bold) },
            text = {
                if (shots.isEmpty()) {
                    Text("No screenshots captured yet.")
                } else {
                    LazyColumn(modifier = Modifier.height(280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(shots) { _, file ->
                            val bmp = remember(file.absolutePath) { BitmapFactory.decodeFile(file.absolutePath) }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = file.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(64.dp, 40.dp).clip(RoundedCornerShape(6.dp))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(file.name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                IconButton(onClick = {
                                    file.delete()
                                    shots = shotsDir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
                                }) {
                                    Icon(Icons.Default.Delete, "Delete screenshot", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showScreenshotsDialog = false }) { Text("Close") } }
        )
    }

    if (showAudioTracksDialog) {
        val groups = exoPlayer.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        AlertDialog(
            onDismissRequest = { showAudioTracksDialog = false },
            title = { Text("Audio Tracks & Video Info", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    videoInfo?.let { info ->
                        Text("Resolution: ${info.width}×${info.height} • Bitrate: ${info.bitrate}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (groups.isEmpty()) {
                        Text("Default audio track active.")
                    } else {
                        groups.forEachIndexed { idx, group ->
                            val lang = group.getTrackFormat(0).language ?: "Track ${idx + 1}"
                            OutlinedButton(
                                onClick = {
                                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                        .buildUpon()
                                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                                        .build()
                                    showAudioTracksDialog = false
                                    onMessage("Selected audio track: $lang")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Track ${idx + 1}: $lang")
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAudioTracksDialog = false }) { Text("Close") } }
        )
    }
}

fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60
    val secs = totalSec % 60
    return "%02d:%02d".format(mins, secs)
}
