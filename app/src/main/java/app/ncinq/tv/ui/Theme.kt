package app.ncinq.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val AppBackground = Color(0xFF06070A)
val Panel = Color(0xFF111827)
val PanelRaised = Color(0xFF1C2940)
val Brand = Color(0xFF6366F1)
val BrandBright = Color(0xFF818CF8)
val Success = Color(0xFF34D399)
val Warning = Color(0xFFFBBF24)
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFFA8B3C7)

@Composable
fun NCinqTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Brand,
            secondary = BrandBright,
            background = AppBackground,
            surface = Panel,
            onPrimary = Color.White,
            onBackground = TextPrimary,
            onSurface = TextPrimary,
        ),
        content = content,
    )
}
