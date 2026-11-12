package ro.medatlas.study

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ro.medatlas.study.data.*
import kotlin.math.min

/** Board coordinates are density-independent and independent of the visible window. */
private class BoardSurface(ctx: Context, boardId: String) : View(ctx) {
    var marks: List<Stroke> = emptyList()
    var commit: (List<Stroke>) -> Unit = {}
    var navigation = false
    var color = 0xff284952.toInt()
    private val prefs = ctx.getSharedPreferences("board-viewports", Context.MODE_PRIVATE)
    private val key = boardId
    private val density = resources.displayMetrics.density
    private var zoom = prefs.getFloat("$key-z", 1f)
    private var cx = prefs.getFloat("$key-x", 0f)
    private var cy = prefs.getFloat("$key-y", 0f)
    private var lastX = 0f
    private var lastY = 0f
    private var multi = false
    private var pending = mutableListOf<InkPoint>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val scale get() = density * zoom
    private fun world(x: Float, y: Float) = InkPoint(cx + (x-width/2f)/scale, cy + (y-height/2f)/scale)
    private val detector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val anchor = world(d.focusX, d.focusY)
            zoom = (zoom*d.scaleFactor).coerceIn(.1f, 12f)
            cx = anchor.x - (d.focusX-width/2f)/scale
            cy = anchor.y - (d.focusY-height/2f)/scale
            invalidate()
            return true
        }
    })
    private fun saveViewport() {
        prefs.edit().putFloat("$key-z", zoom).putFloat("$key-x", cx).putFloat("$key-y", cy).apply()
    }
    fun fit() {
        val points = marks.flatMap { it.points }
        if (points.isEmpty()) { cx = 0f; cy = 0f; zoom = 1f } else {
            val left = points.minOf { it.x }; val right = points.maxOf { it.x }
            val top = points.minOf { it.y }; val bottom = points.maxOf { it.y }
            cx = (left + right) / 2; cy = (top + bottom) / 2
            zoom = min(width / ((right-left+56f)*density), height / ((bottom-top+56f)*density)).coerceIn(.1f, 4f)
        }
        saveViewport(); invalidate()
    }
    override fun onDraw(c: Canvas) {
        c.drawColor(0xfffffcf5.toInt())
        var step = 28f * scale
        while (step < 16f * density) step *= 2
        paint.color = 0xffccd7d2.toInt()
        var x = ((width/2f - cx*scale) % step + step) % step
        while (x < width) {
            var y = ((height/2f - cy*scale) % step + step) % step
            while (y < height) { c.drawCircle(x,y,density,paint); y += step }
            x += step
        }
        c.save()
        c.translate(width/2f, height/2f); c.scale(scale, scale); c.translate(-cx,-cy)
        fun render(points: List<InkPoint>, ink: Int, size: Float) {
            paint.color = ink; paint.strokeWidth = size
            if (points.size == 1) c.drawPoint(points[0].x, points[0].y, paint)
            for (i in 1 until points.size) c.drawLine(points[i-1].x,points[i-1].y,points[i].x,points[i].y,paint)
        }
        marks.forEach { render(it.points, it.color, it.width) }
        render(pending, color, 2.8f)
        c.restore()
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        detector.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                multi = false; lastX = e.x; lastY = e.y; pending.clear()
                if (!navigation) pending.add(world(e.x,e.y))
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                multi = true; pending.clear()
                lastX = (e.getX(0)+e.getX(1))/2; lastY = (e.getY(0)+e.getY(1))/2
            }
            MotionEvent.ACTION_MOVE -> {
                if (e.pointerCount >= 2 || navigation && !multi) {
                    val x = if (e.pointerCount >= 2) (e.getX(0)+e.getX(1))/2 else e.x
                    val y = if (e.pointerCount >= 2) (e.getY(0)+e.getY(1))/2 else e.y
                    cx -= (x-lastX)/scale; cy -= (y-lastY)/scale; lastX=x; lastY=y
                } else if (!multi) {
                    for (i in 0 until e.historySize) pending.add(world(e.getHistoricalX(i),e.getHistoricalY(i)))
                    pending.add(world(e.x,e.y))
                }
            }
            MotionEvent.ACTION_UP -> {
                if (pending.isNotEmpty() && !navigation && !multi) {
                    pending.add(world(e.x,e.y))
                    marks = marks + Stroke(color = color, width = 2.8f, points = pending.toList())
                    commit(marks)
                }
                pending.clear(); saveViewport(); parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> { pending.clear(); parent?.requestDisallowInterceptTouchEvent(false) }
        }
        invalidate(); return true
    }
}

@Composable
internal fun BoardEditor(board: StudyNote, store: LibraryStore, close: () -> Unit) {
    val id = inkId(board.id)
    var title by rememberSaveable { mutableStateOf(board.heading) }
    var navigation by rememberSaveable { mutableStateOf(false) }
    var color by rememberSaveable { mutableIntStateOf(0xff284952.toInt()) }
    var marks by remember { mutableStateOf(store.ink(id, 0)) }
    val redo = remember { mutableStateListOf<Stroke>() }
    var surface by remember { mutableStateOf<BoardSurface?>(null) }
    var deleting by remember { mutableStateOf(false) }
    fun saveTitle() {
        val latest = store.boards()
        val normalized = title.trim().ifBlank { "Planșă fără titlu" }
        if (latest.any { it.id == board.id && it.heading != normalized })
            store.saveBoards(latest.map { if (it.id == board.id) it.copy(heading = normalized) else it })
    }
    DisposableEffect(Unit) { onDispose { saveTitle() } }
    androidx.activity.compose.BackHandler { saveTitle(); close() }
    fun save(items: List<Stroke>) { store.saveInk(id,0,items); marks = items }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick = { saveTitle(); close() }) { Text("‹ Bibliotecă") }
            OutlinedTextField(title, { title = it }, Modifier.weight(1f), singleLine = true, label = { Text("Titlul planșei") })
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            FilterChip(!navigation, { navigation = false }, { Text("Desenează") })
            FilterChip(navigation, { navigation = true }, { Text("Deplasează") })
            Choice("Cerneală", listOf("Grafit","Petrol","Trandafiriu")) { color = listOf(0xff284952.toInt(),0xff138b80.toInt(),0xffbe527b.toInt())[it] }
            TextButton(onClick = { surface?.fit() }) { Text("Încadrează tot") }
            TextButton(enabled = marks.isNotEmpty(), onClick = { redo.add(marks.last()); save(marks.dropLast(1)) }) { Text("Anulează") }
            TextButton(enabled = redo.isNotEmpty(), onClick = { save(marks + redo.removeAt(redo.lastIndex)) }) { Text("Refă") }
            TextButton(onClick = { deleting = true }) { Text("Șterge planșa") }
        }
        AndroidView(factory = { BoardSurface(it, board.id).also { v -> surface = v } },
            modifier = Modifier.fillMaxSize().testTag("board-surface"), update = {
                it.marks = marks; it.navigation = navigation; it.color = color
                it.commit = { next -> redo.clear(); save(next) }
                it.invalidate()
            })
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Ștergi planșa?") },
        text = { Text(title) }, confirmButton = { TextButton(onClick = {
            store.saveBoards(store.boards().filterNot { it.id == board.id }); store.saveInk(id,0,emptyList()); close()
        }) { Text("Șterge") } }, dismissButton = { TextButton(onClick = { deleting = false }) { Text("Păstrează") } })
}
