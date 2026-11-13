package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.domain.model.AccentColor
import com.example.domain.model.ThemeMode

private fun buildLightScheme(accent: AccentColor): ColorScheme {
    val primary = Color(accent.primaryHex)
    val primaryContainer = when (accent) {
        AccentColor.BLUE -> BrandBlueContainerLight
        AccentColor.EMERALD -> Color(0xFFB7F3D1)
        AccentColor.VIOLET -> Color(0xFFEADDFF)
        AccentColor.TEAL -> Color(0xFF9EF0FF)
        AccentColor.AMBER -> Color(0xFFFFDCBE)
        AccentColor.CRIMSON -> Color(0xFFFFDAD6)
    }
    val onPrimaryContainer = when (accent) {
        AccentColor.BLUE -> BrandBlueOnContainerLight
        AccentColor.EMERALD -> Color(0xFF002111)
        AccentColor.VIOLET -> Color(0xFF21005D)
        AccentColor.TEAL -> Color(0xFF001F25)
        AccentColor.AMBER -> Color(0xFF2D1600)
        AccentColor.CRIMSON -> Color(0xFF410002)
    }
    return lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = CyanSecondaryLight,
        onSecondary = Color.White,
        background = Color(0xFFF8FAFC),
        onBackground = Color(0xFF0F172A),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF0F172A),
        surfaceVariant = Color(0xFFF1F5F9),
        onSurfaceVariant = Color(0xFF475569),
        outline = Color(0xFFCBD5E1),
        outlineVariant = Color(0xFFE2E8F0)
    )
}

private fun buildDarkScheme(accent: AccentColor): ColorScheme {
    val primary = when (accent) {
        AccentColor.BLUE -> BrandBluePrimaryDark
        AccentColor.EMERALD -> Color(0xFF7ADCA7)
        AccentColor.VIOLET -> Color(0xFFD0BCFF)
        AccentColor.TEAL -> Color(0xFF83D2E3)
        AccentColor.AMBER -> Color(0xFFFFB870)
        AccentColor.CRIMSON -> Color(0xFFFFB4AB)
    }
    val onPrimary = Color(0xFF00243D)
    val primaryContainer = Color(accent.primaryHex).copy(alpha = 0.35f)
    return darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = Color(0xFFF1F5F9),
        secondary = CyanSecondaryDark,
        onSecondary = Color(0xFF00363E),
        background = Color(0xFF0F172A),
        onBackground = Color(0xFFF8FAFC),
        surface = Color(0xFF1E293B),
        onSurface = Color(0xFFF8FAFC),
        surfaceVariant = Color(0xFF334155),
        onSurfaceVariant = Color(0xFFCBD5E1),
        outline = Color(0xFF475569),
        outlineVariant = Color(0xFF334155)
    )
}

private fun buildAmoledScheme(accent: AccentColor): ColorScheme {
    val dark = buildDarkScheme(accent)
    return dark.copy(
        background = AmoledBackground,
        onBackground = Color(0xFFEDEDED),
        surface = AmoledSurface,
        onSurface = Color(0xFFEDEDED),
        surfaceVariant = AmoledSurfaceVariant,
        onSurfaceVariant = Color(0xFFB0B0B0),
        outline = AmoledCardBorder,
        outlineVariant = Color(0xFF1F1F1F)
    )
}

@Composable
fun UniversalFileManagerTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    dynamicColor: Boolean = true,
    accentColor: AccentColor = AccentColor.BLUE,
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val context = LocalContext.current

    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme: ColorScheme = when (themeMode) {
        ThemeMode.LIGHT -> if (useDynamic) dynamicLightColorScheme(context) else buildLightScheme(accentColor)
        ThemeMode.DARK -> if (useDynamic) dynamicDarkColorScheme(context) else buildDarkScheme(accentColor)
        ThemeMode.AMOLED -> buildAmoledScheme(accentColor)
        ThemeMode.SYSTEM -> {
            if (systemInDark) {
                if (useDynamic) dynamicDarkColorScheme(context) else buildDarkScheme(accentColor)
            } else {
                if (useDynamic) dynamicLightColorScheme(context) else buildLightScheme(accentColor)
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
