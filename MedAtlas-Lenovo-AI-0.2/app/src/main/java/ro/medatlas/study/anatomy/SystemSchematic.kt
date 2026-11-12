package ro.medatlas.study.anatomy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Small original vector schematics. They are decorative study cues, NOT
 * detailed anatomical diagrams or substitutes for a validated medical atlas.
 * Unlike remote pictures, these are always available offline.
 */
@Composable
internal fun SystemSchematic(system: String, modifier: Modifier = Modifier) {
    val rose = Color(0xFFD58EA2)
    val teal = Color(0xFF38786E)
    val ivory = Color(0xFFFAF8F4)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(175.dp)) {
            val w = size.width
            val h = size.height
            fun x(f: Float) = f * w
            fun y(f: Float) = f * h
            fun line(a: Float, b: Float, c: Float, d: Float, color: Color = teal, width: Float = 3f) =
                drawLine(color, Offset(x(a), y(b)), Offset(x(c), y(d)), strokeWidth = width.dp.toPx())
            fun circle(a: Float, b: Float, radius: Float, color: Color = rose) =
                drawCircle(color, radius = radius * h, center = Offset(x(a), y(b)))
            drawRoundRect(ivory, size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx()))
            drawCircle(teal.copy(alpha = .055f), radius = h * .38f,
                center = Offset(x(.5f), y(.52f)))
            when (system) {
                "Cardiovascular" -> {
                    val heart = Path().apply {
                        moveTo(x(.50f), y(.79f))
                        cubicTo(x(.38f),y(.67f),x(.30f),y(.47f),x(.32f),y(.35f))
                        cubicTo(x(.34f),y(.18f),x(.45f),y(.20f),x(.50f),y(.34f))
                        cubicTo(x(.55f),y(.20f),x(.66f),y(.18f),x(.68f),y(.35f))
                        cubicTo(x(.70f),y(.47f),x(.62f),y(.67f),x(.50f),y(.79f))
                        close()
                    }
                    drawPath(heart, rose)
                    line(.49f,.14f,.49f,.32f, teal, 5f)
                    line(.52f,.15f,.52f,.33f, teal.copy(alpha=.65f), 3f)
                }
                "Respirator" -> {
                    line(.50f,.13f,.50f,.38f,teal,7f)
                    line(.50f,.38f,.37f,.48f,teal,4f)
                    line(.50f,.38f,.63f,.48f,teal,4f)
                    drawOval(rose.copy(alpha=.8f),
                        topLeft=Offset(x(.28f),y(.34f)),size=Size(x(.19f),y(.48f)))
                    drawOval(rose.copy(alpha=.8f),
                        topLeft=Offset(x(.53f),y(.34f)),size=Size(x(.19f),y(.48f)))
                    line(.50f,.12f,.50f,.38f,Color.White,1f)
                }
                "Digestiv" -> {
                    line(.49f,.15f,.49f,.35f,teal,7f)
                    drawOval(rose, Offset(x(.51f),y(.34f)),Size(x(.20f),y(.24f)))
                    drawOval(teal.copy(alpha=.45f),Offset(x(.31f),y(.34f)),Size(x(.18f),y(.17f)))
                    val gut = Path().apply {
                        moveTo(x(.43f),y(.58f))
                        cubicTo(x(.28f),y(.59f),x(.30f),y(.73f),x(.45f),y(.72f))
                        cubicTo(x(.64f),y(.69f),x(.62f),y(.57f),x(.44f),y(.66f))
                        cubicTo(x(.29f),y(.73f),x(.42f),y(.84f),x(.55f),y(.80f))
                    }
                    drawPath(gut,teal,style=Stroke(width=5.dp.toPx()))
                }
                "Urinar" -> {
                    drawOval(rose,Offset(x(.30f),y(.28f)),Size(x(.18f),y(.24f)))
                    drawOval(rose,Offset(x(.52f),y(.28f)),Size(x(.18f),y(.24f)))
                    line(.39f,.50f,.46f,.72f,teal,3f)
                    line(.61f,.50f,.54f,.72f,teal,3f)
                    circle(.50f,.76f,.085f,rose)
                    line(.50f,.83f,.50f,.90f,teal,3f)
                }
                "Sistem nervos" -> {
                    circle(.50f,.27f,.17f,rose)
                    line(.50f,.44f,.50f,.82f,teal,6f)
                    for (i in 0..5) {
                        val yy=.49f+i*.058f
                        line(.50f,yy,.35f,yy+.04f,teal.copy(alpha=.75f),2f)
                        line(.50f,yy,.65f,yy+.04f,teal.copy(alpha=.75f),2f)
                    }
                }
                "Endocrin" -> {
                    circle(.50f,.16f,.055f,teal)
                    circle(.50f,.34f,.060f,rose)
                    circle(.40f,.52f,.048f,rose)
                    circle(.60f,.52f,.048f,rose)
                    drawOval(rose,Offset(x(.43f),y(.63f)),Size(x(.17f),y(.09f)))
                    circle(.40f,.82f,.052f,teal.copy(alpha=.7f))
                    circle(.60f,.82f,.052f,teal.copy(alpha=.7f))
                    line(.50f,.20f,.50f,.30f,teal.copy(alpha=.45f),1f)
                }
                "Musculoscheletal" -> {
                    circle(.50f,.19f,.11f,ivory)
                    drawCircle(teal, radius=.11f*h, center=Offset(x(.50f),y(.19f)),
                        style=Stroke(width=3.dp.toPx()))
                    line(.50f,.30f,.50f,.68f,teal,7f)
                    line(.36f,.38f,.64f,.38f,teal,4f)
                    line(.36f,.38f,.28f,.66f,rose,8f)
                    line(.64f,.38f,.72f,.66f,rose,8f)
                    line(.50f,.68f,.40f,.90f,rose,9f)
                    line(.50f,.68f,.60f,.90f,rose,9f)
                }
                "Tegumentar" -> {
                    drawRect(rose.copy(alpha=.5f),Offset(x(.27f),y(.26f)),Size(x(.46f),y(.17f)))
                    drawRect(teal.copy(alpha=.24f),Offset(x(.27f),y(.43f)),Size(x(.46f),y(.20f)))
                    drawRect(rose.copy(alpha=.25f),Offset(x(.27f),y(.63f)),Size(x(.46f),y(.18f)))
                    line(.27f,.26f,.73f,.26f,teal,3f)
                    line(.43f,.27f,.39f,.57f,teal,3f)
                    line(.43f,.27f,.46f,.14f,teal,2f)
                }
                "Limfatic și imun" -> {
                    line(.5f,.17f,.5f,.79f,teal,3f)
                    line(.5f,.36f,.35f,.49f,teal,3f)
                    line(.5f,.36f,.65f,.49f,teal,3f)
                    circle(.5f,.20f,.05f,rose)
                    circle(.5f,.39f,.07f,rose)
                    circle(.35f,.49f,.055f,rose)
                    circle(.65f,.49f,.055f,rose)
                    circle(.5f,.77f,.08f,rose)
                }
                "Reproducător" -> {
                    val uter = Path().apply {
                        moveTo(x(.38f),y(.37f)); cubicTo(x(.38f),y(.66f),x(.62f),y(.66f),x(.62f),y(.37f))
                        cubicTo(x(.57f),y(.44f),x(.43f),y(.44f),x(.38f),y(.37f)); close()
                    }
                    drawPath(uter,rose)
                    line(.38f,.40f,.25f,.32f,teal,3f)
                    line(.62f,.40f,.75f,.32f,teal,3f)
                    circle(.25f,.32f,.055f,rose)
                    circle(.75f,.32f,.055f,rose)
                    line(.50f,.60f,.50f,.83f,teal,4f)
                }
                else -> {
                    drawOval(rose,Offset(x(.31f),y(.35f)),Size(x(.38f),y(.28f)))
                    circle(.50f,.49f,.087f,teal)
                    circle(.50f,.49f,.044f,Color.White)
                    line(.19f,.49f,.31f,.49f,teal.copy(alpha=.5f),2f)
                    line(.69f,.49f,.81f,.49f,teal.copy(alpha=.5f),2f)
                }
            }
        }
        Text("Schemă stilizată originală • nu înlocuiește o ilustrație anatomică validată",
            fontSize = 10.sp, color = Color(0xFF77828A),
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
