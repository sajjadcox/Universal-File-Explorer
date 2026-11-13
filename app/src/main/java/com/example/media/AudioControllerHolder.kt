package com.example.media

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.domain.model.FileItem
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AudioPlayerUiState(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val currentTitle: String = "",
    val currentArtist: String = "",
    val currentUri: Uri? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val playbackSpeed: Float = 1.0f,
    val queue: List<FileItem> = emptyList(),
    val currentIndex: Int = -1,
    val sleepTimerRemainingMinutes: Int? = null
)

class AudioPlayerManager(private val context: Context) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null

    private val _state = MutableStateFlow(AudioPlayerUiState())
    val state: StateFlow<AudioPlayerUiState> = _state.asStateFlow()

    fun connect(onReady: (() -> Unit)? = null) {
        if (controller != null) {
            onReady?.invoke()
            return
        }
        val token = SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync()
        controllerFuture?.addListener({
            try {
                val ctrl = controllerFuture?.get() ?: return@addListener
                controller = ctrl
                ctrl.addListener(playerListener)
                _state.update { it.copy(isConnected = true) }
                syncFromController()
                startProgressLoop()
                onReady?.invoke()
            } catch (_: Exception) {
            }
        }, MoreExecutors.directExecutor())
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncFromController()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncFromController()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _state.update { it.copy(shuffleEnabled = shuffleModeEnabled) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _state.update { it.copy(repeatMode = repeatMode) }
        }
    }

    private fun syncFromController() {
        val ctrl = controller ?: return
        val curItem = ctrl.currentMediaItem
        _state.update {
            it.copy(
                isPlaying = ctrl.isPlaying,
                currentTitle = curItem?.mediaMetadata?.title?.toString()
                    ?: it.queue.getOrNull(ctrl.currentMediaItemIndex)?.name
                    ?: "",
                currentArtist = curItem?.mediaMetadata?.artist?.toString() ?: "Local Audio",
                currentUri = curItem?.localConfiguration?.uri,
                positionMs = ctrl.currentPosition.coerceAtLeast(0L),
                durationMs = ctrl.duration.coerceAtLeast(0L),
                shuffleEnabled = ctrl.shuffleModeEnabled,
                repeatMode = ctrl.repeatMode,
                currentIndex = ctrl.currentMediaItemIndex
            )
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                controller?.let { ctrl ->
                    _state.update {
                        it.copy(
                            positionMs = ctrl.currentPosition.coerceAtLeast(0L),
                            durationMs = ctrl.duration.coerceAtLeast(0L),
                            isPlaying = ctrl.isPlaying
                        )
                    }
                }
                delay(500)
            }
        }
    }

    fun playPlaylist(items: List<FileItem>, startIndex: Int = 0) {
        if (items.isEmpty()) return
        connect {
            val ctrl = controller ?: return@connect
            val mediaItems = items.map { file ->
                MediaItem.Builder()
                    .setUri(file.uri)
                    .setMediaId(file.uri.toString())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(file.name.substringBeforeLast('.'))
                            .setArtist(file.extension.uppercase())
                            .build()
                    )
                    .build()
            }
            _state.update { it.copy(queue = items, currentIndex = startIndex) }
            ctrl.setMediaItems(mediaItems, startIndex.coerceIn(0, mediaItems.lastIndex), 0L)
            ctrl.prepare()
            ctrl.play()
        }
    }

    fun addToQueue(item: FileItem) {
        connect {
            val ctrl = controller ?: return@connect
            val mediaItem = MediaItem.Builder()
                .setUri(item.uri)
                .setMediaId(item.uri.toString())
                .setMediaMetadata(MediaMetadata.Builder().setTitle(item.name.substringBeforeLast('.')).build())
                .build()
            ctrl.addMediaItem(mediaItem)
            _state.update { it.copy(queue = it.queue + item) }
        }
    }

    fun removeFromQueue(index: Int) {
        val ctrl = controller ?: return
        if (index in 0 until ctrl.mediaItemCount) {
            ctrl.removeMediaItem(index)
            _state.update {
                val mutable = it.queue.toMutableList()
                if (index in mutable.indices) mutable.removeAt(index)
                it.copy(queue = mutable)
            }
        }
    }

    fun togglePlayPause() {
        val ctrl = controller ?: return
        if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms.coerceAtLeast(0L))
    }

    fun seekNext() {
        controller?.seekToNextMediaItem()
    }

    fun seekPrevious() {
        controller?.seekToPreviousMediaItem()
    }

    fun skipToIndex(index: Int) {
        controller?.seekToDefaultPosition(index)
        controller?.play()
    }

    fun toggleShuffle() {
        val ctrl = controller ?: return
        ctrl.shuffleModeEnabled = !ctrl.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        val ctrl = controller ?: return
        ctrl.repeatMode = when (ctrl.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val ctrl = controller ?: return
        ctrl.playbackParameters = PlaybackParameters(speed)
        _state.update { it.copy(playbackSpeed = speed) }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _state.update { it.copy(sleepTimerRemainingMinutes = null) }
            return
        }
        _state.update { it.copy(sleepTimerRemainingMinutes = minutes) }
        sleepTimerJob = scope.launch {
            var remaining = minutes
            while (remaining > 0) {
                delay(60_000L)
                remaining--
                _state.update { it.copy(sleepTimerRemainingMinutes = if (remaining > 0) remaining else null) }
            }
            controller?.pause()
        }
    }

    fun release() {
        progressJob?.cancel()
        sleepTimerJob?.cancel()
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
    }
}
