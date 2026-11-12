package ro.medatlas.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Shared visual tokens across the library, notebook, reader and medical modules. */
internal object GlassTokens {
    val Midnight = Color(0xFF09192F)
    val Navy = Color(0xFF183858)
    val Blue = Color(0xFF338DFA)
    val Violet = Color(0xFF9D73E9)
    val Ink = Color(0xFF1B2E49)
    val Muted = Color(0xFF607998)
    val Frost = Color(0xF2EFF6FE)
    val FrostCard = Color(0xDFFFFFFF)
    val DarkGlass = Color(0xE6193658)
    val Stroke = Color.White.copy(alpha = .31f)
    val LightPage = Brush.linearGradient(listOf(
        Color(0xFFD8E9F8), Color(0xFFE9F2FC), Color(0xFFCFDDF1)
    ))
    val NightPage = Brush.linearGradient(listOf(
        Color(0xFF08172D), Color(0xFF1B3E62), Color(0xFF0C1A33)
    ))
}

/** Keep study content dark on pale frosted sheets for legibility. */
@Composable
internal fun MedAtlasGlassTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = GlassTokens.Blue,
            secondary = GlassTokens.Violet,
            background = Color(0xFFE6F0FC),
            surface = Color(0xFFF2F7FD),
            surfaceVariant = Color(0xFFD6E6F7),
            onBackground = GlassTokens.Ink,
            onSurface = GlassTokens.Ink,
            onPrimary = Color.White
        ),
        content = content
    )
}

/** Reusable translucent work surface for every main and sub-module. */
@Composable
internal fun GlassPageFrame(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.background(GlassTokens.NightPage)) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            color = GlassTokens.Frost,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, GlassTokens.Stroke)
        ) { content() }
    }
}
