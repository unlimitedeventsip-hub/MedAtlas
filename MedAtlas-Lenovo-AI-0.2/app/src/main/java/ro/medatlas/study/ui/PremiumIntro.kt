package ro.medatlas.study.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Native, resolution-independent, light premium splash.
 * Deliberately avoids the old jagged ECG or anatomically incorrect heart shape.
 * Can be disabled from Settings or skipped with a tap.
 */
@Composable
fun PremiumIntro(onFinish: () -> Unit) {
    val fade = remember { Animatable(0f) }
    val rise = remember { Animatable(.90f) }
    val ring = remember { Animatable(.25f) }
    var finished by remember { mutableStateOf(false) }
    val pulse = rememberInfiniteTransition(label = "soft-pulse")
    val heartBeat by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "heart-scale"
    )
    fun complete() {
        if (!finished) { finished = true; onFinish() }
    }
    LaunchedEffect(Unit) {
        fade.animateTo(1f, tween(850, easing = FastOutSlowInEasing))
        rise.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
        ring.animateTo(1f, tween(550))
        delay(900)
        complete()
    }
    val background = Color(0xFFFAF8F4)
    val blush = Color(0xFFD58EA2)
    val deep = Color(0xFF182535)
    Box(Modifier.fillMaxSize().background(background).clickable { complete() },
        contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth(.92f).alpha(fade.value),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.minDimension * .435f
                    drawCircle(blush.copy(alpha = .075f * ring.value),
                        radius = radius, center = center)
                    drawCircle(blush.copy(alpha = .16f),
                        radius = radius * .78f, center = center,
                        style = Stroke(width = 1.5.dp.toPx()))
                    drawCircle(deep.copy(alpha = .08f),
                        radius = radius * .96f, center = center,
                        style = Stroke(width = .75.dp.toPx()))
                }
                Canvas(Modifier.size(102.dp).scale(rise.value * heartBeat)) {
                    val w = size.width
                    val h = size.height
                    val shape = Path().apply {
                        moveTo(.50f * w, .85f * h)
                        cubicTo(.42f * w, .74f * h, .10f * w, .52f * h,
                            .10f * w, .29f * h)
                        cubicTo(.10f * w, .07f * h, .36f * w, .02f * h,
                            .50f * w, .23f * h)
                        cubicTo(.64f * w, .02f * h, .90f * w, .07f * h,
                            .90f * w, .29f * h)
                        cubicTo(.90f * w, .52f * h, .58f * w, .74f * h,
                            .50f * w, .85f * h)
                        close()
                    }
                    drawPath(shape, color = blush)
                    drawPath(shape, color = Color(0xFFBD768B).copy(alpha = .6f),
                        style = Stroke(width = 1.1.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Nadia, te iubesc", color = deep,
                fontSize = 33.sp, letterSpacing = .1.sp,
                fontWeight = FontWeight.Light, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text("MEDATLAS", color = Color(0xFF38786E), fontSize = 13.sp,
                letterSpacing = 3.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(38.dp))
            Text("Un univers de medicină, creat pentru tine.",
                fontSize = 12.sp, color = Color(0xFF77828A), textAlign = TextAlign.Center)
        }
        Text("Atinge pentru a continua", color = Color(0xFFA5A8AB),
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 30.dp))
    }
}
