package it.ge360.vintedscanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF087E8B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8F3F5),
    onPrimaryContainer = Color(0xFF002F33),
    secondary = Color(0xFF475D61),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E6E8),
    onSecondaryContainer = Color(0xFF162B2E),
    background = Color(0xFFF7F9F9),
    onBackground = Color(0xFF171D1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171D1E),
    surfaceVariant = Color(0xFFE8EEEE),
    onSurfaceVariant = Color(0xFF414A4B),
    outline = Color(0xFF717A7B),
    outlineVariant = Color(0xFFC1C8C9),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF65D4DF),
    onPrimary = Color(0xFF00363B),
    primaryContainer = Color(0xFF00505A),
    onPrimaryContainer = Color(0xFFA3EEF3),
    secondary = Color(0xFFB7CBCD),
    onSecondary = Color(0xFF223437),
    secondaryContainer = Color(0xFF394B4E),
    onSecondaryContainer = Color(0xFFD3E7E9),
    background = Color(0xFF0F1415),
    onBackground = Color(0xFFDEE4E4),
    surface = Color(0xFF151B1C),
    onSurface = Color(0xFFDEE4E4),
    surfaceVariant = Color(0xFF273032),
    onSurfaceVariant = Color(0xFFC0C8C9),
    outline = Color(0xFF8A9495),
    outlineVariant = Color(0xFF3F494A),
    error = Color(0xFFFFB4AB)
)

@Composable
fun VintedScannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
