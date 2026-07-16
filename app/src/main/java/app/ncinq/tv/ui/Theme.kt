package app.ncinq.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val AppBackground = Color(0xFF08090B)
val Panel = Color(0xFF17191E)
val PanelRaised = Color(0xFF252830)
val Brand = Color(0xFFE23D4F)
val BrandBright = Color(0xFFFF5968)
val Success = Color(0xFF2DD4A8)
val Warning = Color(0xFFF4C95D)
val TextPrimary = Color(0xFFF7F7F8)
val TextSecondary = Color(0xFFADB1BA)

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
