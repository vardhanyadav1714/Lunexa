package com.twango.lunexa.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = LunexaGreenDark,
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF0B5A39),
    onPrimaryContainer = Color(0xFFDDF8E8),
    secondary = Color(0xFFB9C5FF),
    onSecondary = Color(0xFF10215D),
    secondaryContainer = Color(0xFF253D83),
    onSecondaryContainer = Color(0xFFE8ECFF),
    tertiary = Color(0xFFFFB69C),
    onTertiary = Color(0xFF5B1A05),
    tertiaryContainer = Color(0xFF8B3519),
    onTertiaryContainer = Color(0xFFFFE8DE),
    background = Color(0xFF0D1511),
    onBackground = Color(0xFFE8F2EA),
    surface = LunexaDarkSurface,
    onSurface = Color(0xFFE8F2EA),
    surfaceVariant = Color(0xFF25322B),
    onSurfaceVariant = Color(0xFFC3D0C7),
    outline = Color(0xFF8B9A91),
    outlineVariant = Color(0xFF3A4B42),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = LunexaGreen,
    onPrimary = Color.White,
    primaryContainer = LunexaMint,
    onPrimaryContainer = Color(0xFF083820),
    secondary = LunexaIndigo,
    onSecondary = Color.White,
    secondaryContainer = LunexaIndigoSoft,
    onSecondaryContainer = Color(0xFF15205A),
    tertiary = LunexaCoral,
    onTertiary = Color.White,
    tertiaryContainer = LunexaCoralSoft,
    onTertiaryContainer = Color(0xFF541D08),
    background = LunexaPaper,
    onBackground = LunexaInk,
    surface = LunexaCard,
    onSurface = LunexaInk,
    surfaceVariant = Color(0xFFE9F0EA),
    onSurfaceVariant = LunexaSlate,
    outline = Color(0xFF718178),
    outlineVariant = Color(0xFFD5DED7),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun LunexaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
