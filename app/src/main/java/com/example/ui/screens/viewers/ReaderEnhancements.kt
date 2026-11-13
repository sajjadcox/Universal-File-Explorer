package com.example.ui.screens.viewers

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.domain.model.ReaderFontChoice
import com.example.domain.model.ReaderTheme
import java.util.Locale

class ReaderTtsHelper(context: Context) {
    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                isReady = true
            }
        }
    }

    fun speak(text: String): Boolean {
        if (!isReady || text.isBlank()) return false
        val chunk = if (text.length > 3500) text.take(3500) else text
        tts?.speak(chunk, TextToSpeech.QUEUE_FLUSH, null, "ufe_reader_${System.currentTimeMillis()}")
        return true
    }

    fun stop() {
        runCatching { tts?.stop() }
    }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
    }
}

@Composable
fun KeepScreenAwakeEffect(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val window = (context as? Activity)?.window
        if (enabled) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
fun ReaderOrientationEffect(landscapeLocked: Boolean) {
    val context = LocalContext.current
    DisposableEffect(landscapeLocked) {
        val activity = context as? Activity
        if (landscapeLocked && activity != null) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else if (activity != null) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

fun readerSurfaceColors(theme: ReaderTheme, defaultBg: Color, defaultFg: Color): Pair<Color, Color> = when (theme) {
    ReaderTheme.SYSTEM -> defaultBg to defaultFg
    ReaderTheme.LIGHT -> Color(0xFFFFFFFF) to Color(0xFF1A1C1E)
    ReaderTheme.SEPIA -> Color(0xFFF6EFE0) to Color(0xFF3E2F1C)
    ReaderTheme.DARK -> Color(0xFF121417) to Color(0xFFE2E6EC)
}

fun ReaderFontChoice.toComposeFontFamily(): FontFamily = when (this) {
    ReaderFontChoice.SANS -> FontFamily.SansSerif
    ReaderFontChoice.SERIF -> FontFamily.Serif
    ReaderFontChoice.MONO -> FontFamily.Monospace
}

@Composable
fun ReaderEnhancementBar(
    readerTheme: ReaderTheme,
    onCycleTheme: () -> Unit,
    readerFont: ReaderFontChoice,
    onCycleFont: () -> Unit,
    autoScrollEnabled: Boolean,
    onToggleAutoScroll: () -> Unit,
    keepAwake: Boolean,
    onToggleKeepAwake: () -> Unit,
    landscapeMode: Boolean,
    onToggleLandscape: () -> Unit,
    isSpeaking: Boolean,
    onToggleSpeak: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = false,
                onClick = onCycleTheme,
                label = { Text(readerTheme.label) },
                leadingIcon = { Icon(Icons.Default.LightMode, null, modifier = Modifier.size(16.dp)) }
            )
            FilterChip(
                selected = false,
                onClick = onCycleFont,
                label = { Text(readerFont.label) },
                leadingIcon = { Icon(Icons.Default.FontDownload, null, modifier = Modifier.size(16.dp)) }
            )
            FilterChip(
                selected = isSpeaking,
                onClick = onToggleSpeak,
                label = { Text(if (isSpeaking) "Stop TTS" else "Read Aloud") },
                leadingIcon = { Icon(Icons.Default.RecordVoiceOver, null, modifier = Modifier.size(16.dp)) }
            )
            FilterChip(
                selected = autoScrollEnabled,
                onClick = onToggleAutoScroll,
                label = { Text("Auto-Scroll") },
                leadingIcon = {
                    Icon(
                        if (autoScrollEnabled) Icons.Default.StopCircle else Icons.Default.PlayCircle,
                        null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
            FilterChip(
                selected = keepAwake,
                onClick = onToggleKeepAwake,
                label = { Text("Awake") },
                leadingIcon = { Icon(Icons.Default.BrightnessHigh, null, modifier = Modifier.size(16.dp)) }
            )
            FilterChip(
                selected = landscapeMode,
                onClick = onToggleLandscape,
                label = { Text("Rotate") },
                leadingIcon = { Icon(Icons.Default.ScreenRotation, null, modifier = Modifier.size(16.dp)) }
            )
        }
    }
}
