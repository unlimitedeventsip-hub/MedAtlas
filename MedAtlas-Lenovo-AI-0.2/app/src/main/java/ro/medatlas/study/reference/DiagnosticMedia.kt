package ro.medatlas.study.reference

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable internal fun DiagnosticMedia(type: String) {
    val context = LocalContext.current
    if (type == "ecg") {
        val traces = remember { context.assets.open("ecg.tsv").bufferedReader().useLines {
            it.drop(1).map { line -> line.split('\t') }.toList()
        } }
        var record by remember { mutableIntStateOf(0) }
        var zoom by remember { mutableStateOf(false) }
        var doubleGain by remember { mutableStateOf(false) }
        val row = traces[record]
        val samples = remember(record) { row[3].split(',').map { it.toFloat() / row[2].toFloat() } }
        Text("ECG real · MIT-BIH " + row[0] + " · derivația " + row[1])
        val seconds = if (zoom) 3 else 6
        val halfRange = if (doubleGain) 1.5f else 3f
        Canvas(Modifier.fillMaxWidth().height(170.dp).background(Color(0xFFFFF4F1))) {
            val trace = Path()
            val n = seconds*360
            for (tick in 0..seconds*5) {
                val x = tick*size.width/(seconds*5)
                drawLine(Color(0xFFE6C4C0),androidx.compose.ui.geometry.Offset(x,0f),androidx.compose.ui.geometry.Offset(x,size.height))
            }
            for (i in 0 until n) {
                val x = i*size.width/(n-1)
                val y = (size.height*(.5f - samples[i]/(2*halfRange))).coerceIn(0f,size.height)
                if (i==0) trace.moveTo(x,y) else trace.lineTo(x,y)
            }
            drawPath(trace,Color(0xFF225951),style=Stroke(1.4.dp.toPx()))
        }
        Text("Fereastră · $seconds s     Amplitudine · ±$halfRange mV     Grilă · 0,2 s")
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            FilterChip(zoom,{ zoom=!zoom },{ Text("3 secunde") })
            FilterChip(doubleGain,{ doubleGain=!doubleGain },{ Text("Amplificare ×2") })
        }
        Row {
            TextButton(enabled=record>0,onClick={record--}) { Text("Anterior") }
            TextButton(enabled=record<traces.lastIndex,onClick={record++}) { Text("Următorul ECG") }
        }
        Text("Moody și Mark · MIT-BIH Arrhythmia Database 1.0.0 · PhysioNet · ODC-By 1.0")
    } else if (type == "xray" || type == "ct") {
        var slice by remember { mutableIntStateOf(1) }
        val axial = listOf(2,14,24,32)

        val uri = androidx.compose.ui.platform.LocalUriHandler.current
        val label = if (type == "ct") "CT axial · secțiunea " + axial[slice] else "Radiografie toracică PA"
        val source = if (type == "ct") "CT_of_a_normal_brain,_axial_" + axial[slice] + ".png" else "Chest_Xray_PA_3-8-2010.png"
        OutlinedButton(onClick = { uri.openUri("https://commons.wikimedia.org/wiki/File:" + source) }) {
            Text("Deschide imaginea publicată · online")
        }
        Text(label)
        if (type == "ct") {
            Slider(slice.toFloat(),{slice=it.roundToInt()},valueRange=0f..3f,steps=2)
            Text("Parcurge cele patru niveluri și compară forma structurilor de pe linia mediană.")
        }
        Text(if (type=="ct") "Mikael Häggström · CT of a normal brain · CC0" else "Stillwaterising · Chest Xray PA 3-8-2010 · CC0")
    }
}
