package ro.medatlas.study.simulation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** A short decorative signature on entering the hospital, independent of navigation. */
@Composable
internal fun HospitalHeaderTrace() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    val points = remember { listOf(
        Offset(0f, .59f), Offset(.16f, .59f), Offset(.21f, .48f), Offset(.26f, .59f),
        Offset(.34f, .59f), Offset(.39f, .68f), Offset(.45f, .09f), Offset(.51f, .88f),
        Offset(.57f, .59f), Offset(.65f, .59f), Offset(.74f, .40f), Offset(.83f, .59f), Offset(1f, .59f)
    ) }
    Canvas(Modifier.size(66.dp, 36.dp)) {
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = point.x * size.width
            val y = point.y * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(path, Color(0xFFE2D9D9), style = stroke)
        val measure = PathMeasure().apply { setPath(path, false) }
        val trace = Path()
        measure.getSegment(0f, measure.length * progress.value, trace, true)
        drawPath(trace, Color(0xFF9D4965), style = stroke)
    }
}
