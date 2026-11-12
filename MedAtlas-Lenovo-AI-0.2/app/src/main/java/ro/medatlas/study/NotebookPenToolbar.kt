package ro.medatlas.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.medatlas.study.data.Tool

/**
 * Persistent, directly accessible pen controls above the freehand page.
 * All choices bind to the existing StudyCanvasView; no duplicate ink storage.
 */
@Composable
internal fun NotebookPenToolbar(
    activeTool: Tool,
    onTool: (Tool) -> Unit,
    selectedColor: String,
    onColor: (String) -> Unit,
    colors: Map<String, Color>,
    width: Int,
    onWidth: (Int) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit
) {
    val tools: List<Triple<Tool, String, ImageVector>> = listOf(
        Triple(Tool.PEN, "Pix", Icons.Rounded.Edit),
        Triple(Tool.PENCIL, "Creion", Icons.Rounded.Create),
        Triple(Tool.BRUSH, "Pensulă", Icons.Rounded.Brush),
        Triple(Tool.HIGHLIGHT, "Marker", Icons.Rounded.BorderColor),
        Triple(Tool.ERASER, "Radieră", Icons.Rounded.AutoFixOff),
        Triple(Tool.LASSO, "Lasou", Icons.Rounded.Gesture),
        Triple(Tool.PAN, "Deplasare", Icons.Rounded.PanTool)
    )
    Surface(
        color = Color(0xED153252),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .19f)),
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                tools.forEach { (tool, label, icon) ->
                    val active = activeTool == tool
                    Surface(
                        modifier = Modifier.clickable {
                            onTool(tool)
                            if (tool == Tool.PENCIL) onWidth(2)
                            else if (tool == Tool.HIGHLIGHT) onWidth(6)
                            else if (tool == Tool.BRUSH) onWidth(4)
                            else if (tool == Tool.PEN) onWidth(2)
                        },
                        color = if (active) Color(0xFF338DFA) else Color.White.copy(alpha = .075f),
                        shape = RoundedCornerShape(13.dp),
                        border = BorderStroke(1.dp,
                            if (active) Color(0xFF9DD2FF) else Color.White.copy(alpha = .12f))
                    ) {
                        Row(Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, label, tint = Color.White, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(label, color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
                IconButton(onClick = onUndo) {
                    Icon(Icons.Rounded.Undo, "Anulează ultima trasare", tint = Color(0xFFDAE9FF))
                }
                IconButton(onClick = onRedo) {
                    Icon(Icons.Rounded.Redo, "Refă ultima trasare", tint = Color(0xFFDAE9FF))
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    colors.forEach { (name, shade) ->
                        val chosen = selectedColor == name
                        Surface(
                            color = shade,
                            shape = CircleShape,
                            border = BorderStroke(if (chosen) 3.dp else 1.dp,
                                if (chosen) Color.White else Color.White.copy(alpha = .35f)),
                            modifier = Modifier.size(if (chosen) 31.dp else 26.dp)
                                .clickable { onColor(name) }
                        ) {
                            if (chosen) Box(contentAlignment = Alignment.Center) {
                                Text("✓", fontSize = 13.sp, color =
                                    if (name == "Galben" || name == "Fildeș") Color.Black else Color.White)
                            }
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.width(126.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Grosime  " + width + " px", fontSize = 11.sp,
                        color = Color(0xFFD5E8FF))
                    Slider(
                        value = width.toFloat(),
                        onValueChange = { onWidth(it.toInt().coerceIn(1, 12)) },
                        valueRange = 1f..12f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF8AC2FF), activeTrackColor = Color(0xFF60A9FF),
                            inactiveTrackColor = Color(0xFF6A819E)
                        ),
                        modifier = Modifier.height(26.dp)
                    )
                }
            }
        }
    }
}
