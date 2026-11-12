package ro.medatlas.study.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
internal fun NadiaSignature() {
    val ink = remember { Animatable(0f) }
    LaunchedEffect(Unit) { ink.animateTo(1f, tween(1450, easing = FastOutSlowInEasing)) }
    Canvas(Modifier.size(49.dp, 38.dp)) {
        val w = size.width
        val h = size.height
        val monogram = Path().apply {
            moveTo(w*.04f,h*.8f)
            cubicTo(w*.18f,h*.88f,w*.22f,h*.18f,w*.3f,h*.17f)
            cubicTo(w*.34f,h*.14f,w*.4f,h*.84f,w*.46f,h*.78f)
            cubicTo(w*.53f,h*.73f,w*.57f,h*.1f,w*.64f,h*.17f)
            cubicTo(w*.72f,h*.24f,w*.49f,h*.86f,w*.71f,h*.67f)
            lineTo(w*.77f,h*.55f)
            lineTo(w*.81f,h*.67f)
            lineTo(w*.87f,h*.36f)
            lineTo(w*.91f,h*.58f)
            lineTo(w*.99f,h*.58f)
        }
        val measured = PathMeasure().apply { setPath(monogram,false) }
        val portion = Path()
        measured.getSegment(0f, measured.length * ink.value, portion, true)
        drawPath(portion, Brush.horizontalGradient(listOf(Color(0xFF287D75),Color(0xFFAA5671))),
            style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
