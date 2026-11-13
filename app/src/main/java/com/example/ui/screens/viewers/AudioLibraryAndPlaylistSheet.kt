package com.example.ui.screens.viewers

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.LocalDataRepository
import com.example.data.db.PlaylistEntity
import com.example.data.storage.AudioLibraryTrack
import com.example.data.storage.Phase2FileOperations
import com.example.domain.model.FileItem
import com.example.media.AudioPlayerManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioLibraryAndPlaylistsSheet(
    currentTrack: FileItem,
    localRepo: LocalDataRepository,
    audioPlayerManager: AudioPlayerManager,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) } // 0 = Songs, 1 = Albums/Artists, 2 = Playlists, 3 = History
    var tracks by remember { mutableStateOf<List<AudioLibraryTrack>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }

    val playlists by localRepo.playlists.collectAsState(initial = emptyList())
    val mediaHistory by localRepo.recentMediaHistory("AUDIO", 30).collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        isLoading = true
        tracks = Phase2FileOperations.queryDeviceAudioLibrary(context)
        isLoading = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Audio Library & Playlists", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Songs", "Albums", "Playlists", "History").forEachIndexed { idx, label ->
                    FilterChip(selected = tab == idx, onClick = { tab = idx }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(10.dp))

            when (tab) {
                0 -> {
                    if (isLoading) {
                        Text("Scanning device audio library...", style = MaterialTheme.typography.bodySmall)
                    } else if (tracks.isEmpty()) {
                        Text("No music tracks indexed in MediaStore.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        LazyColumn(modifier = Modifier.height(340.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(tracks, key = { it.id }) { t ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        audioPlayerManager.playPlaylist(tracks.map { it.fileItem }, tracks.indexOf(t))
                                        onDismiss()
                                    }
                                ) {
                                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Audiotrack, null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(t.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${t.artist} • ${t.album}", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                        }
                                        Text(formatDurationMs(t.durationMs), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    val grouped = remember(tracks) { tracks.groupBy { it.album } }
                    LazyColumn(modifier = Modifier.height(340.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(grouped.entries.toList(), key = { it.key }) { (album, albumTracks) ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    audioPlayerManager.playPlaylist(albumTracks.map { it.fileItem }, 0)
                                    onDismiss()
                                }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(album, fontWeight = FontWeight.Bold)
                                    Text("${albumTracks.firstOrNull()?.artist ?: ""} • ${albumTracks.size} track(s)", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                2 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showCreatePlaylist = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Create New Playlist")
                        }
                        LazyColumn(modifier = Modifier.height(300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(playlists, key = { it.id }) { pl ->
                                val plTracks by localRepo.playlistTracks(pl.id).collectAsState(initial = emptyList())
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        val items = plTracks.map {
                                            FileItem(Uri.parse(it.trackUri), it.title, it.trackUri, it.title.substringAfterLast('.', "mp3"))
                                        }
                                        if (items.isNotEmpty()) {
                                            audioPlayerManager.playPlaylist(items, 0)
                                            onDismiss()
                                        } else onMessage("Playlist is empty; tap + to add current song")
                                    }
                                ) {
                                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(pl.name, fontWeight = FontWeight.Bold)
                                            Text("${plTracks.size} track(s)", style = MaterialTheme.typography.labelSmall)
                                        }
                                        IconButton(onClick = {
                                            scope.launch {
                                                localRepo.addTrackToPlaylist(pl.id, currentTrack.uri.toString(), currentTrack.name)
                                                onMessage("Added '${currentTrack.name}' to '${pl.name}'")
                                            }
                                        }) { Icon(Icons.Default.PlaylistAdd, "Add current track") }
                                        IconButton(onClick = { scope.launch { localRepo.deletePlaylist(pl.id) } }) {
                                            Icon(Icons.Default.Delete, "Delete playlist", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> {
                    LazyColumn(modifier = Modifier.height(340.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(mediaHistory, key = { it.uri }) { mh ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    val item = FileItem(Uri.parse(mh.uri), mh.title, mh.uri, mh.title.substringAfterLast('.', "mp3"))
                                    audioPlayerManager.playPlaylist(listOf(item), 0)
                                    onDismiss()
                                }
                            ) {
                                Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(mh.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text("Play ${mh.playCount}×", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreatePlaylist) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylist = false },
            title = { Text("New Playlist") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Playlist Name") }, singleLine = true) },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        scope.launch { localRepo.createPlaylist(name.trim()) }
                    }
                    showCreatePlaylist = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreatePlaylist = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun EqualizerDialog(
    audioSessionId: Int,
    onDismiss: () -> Unit
) {
    val eqSupported = remember { mutableStateOf(true) }
    val equalizer = remember(audioSessionId) {
        runCatching { Equalizer(0, audioSessionId.coerceAtLeast(0)) }.getOrNull()
    }
    val bassBoost = remember(audioSessionId) {
        runCatching { BassBoost(0, audioSessionId.coerceAtLeast(0)) }.getOrNull()
    }
    var enabled by remember { mutableStateOf(equalizer?.enabled ?: false) }
    var bassStrength by remember { mutableStateOf(0f) }
    val bands = remember(equalizer) {
        mutableStateListOf<Float>().apply {
            val count = equalizer?.numberOfBands?.toInt() ?: 0
            for (i in 0 until count) {
                val lvl = equalizer?.getBandLevel(i.toShort())?.toFloat() ?: 0f
                add(lvl)
            }
        }
    }

    LaunchedEffect(equalizer) {
        eqSupported.value = equalizer != null && (equalizer.numberOfBands > 0)
    }

    DisposableEffect(equalizer, bassBoost) {
        onDispose {
            runCatching { equalizer?.release() }
            runCatching { bassBoost?.release() }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Equalizer, null)
                Spacer(Modifier.width(8.dp))
                Text("Audio Equalizer", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            if (!eqSupported.value || equalizer == null) {
                Text("Hardware AudioEffect Equalizer is unavailable on this device/emulator output session.")
            } else {
                val range = equalizer.bandLevelRange
                val minLvl = range.getOrNull(0)?.toFloat() ?: -1500f
                val maxLvl = range.getOrNull(1)?.toFloat() ?: 1500f
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Enable Equalizer", fontWeight = FontWeight.SemiBold)
                        Switch(checked = enabled, onCheckedChange = {
                            enabled = it
                            runCatching {
                                equalizer.enabled = it
                                bassBoost?.enabled = it
                            }
                        })
                    }
                    bands.forEachIndexed { idx, value ->
                        val hz = equalizer.getCenterFreq(idx.toShort()) / 1000
                        Text("${hz} Hz (${(value / 100).toInt()} dB)", style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = value,
                            onValueChange = { next ->
                                bands[idx] = next
                                runCatching { equalizer.setBandLevel(idx.toShort(), next.toInt().toShort()) }
                            },
                            valueRange = minLvl..maxLvl
                        )
                    }
                    if (bassBoost?.strengthSupported == true) {
                        Text("Bass Boost (${(bassStrength / 10).toInt()}%)", style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = bassStrength,
                            onValueChange = {
                                bassStrength = it
                                runCatching { bassBoost.setStrength(it.toInt().toShort()) }
                            },
                            valueRange = 0f..1000f
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
