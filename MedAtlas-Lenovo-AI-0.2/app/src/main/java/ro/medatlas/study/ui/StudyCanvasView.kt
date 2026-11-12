package ro.medatlas.study.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import ro.medatlas.study.data.PageSticker
import ro.medatlas.study.data.InkPoint
import ro.medatlas.study.data.Stroke
import ro.medatlas.study.data.Tool
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Document-independent ink, stored as coordinates normalized to the source page. */
class StudyCanvasView(context: Context) : View(context) {
    var onSave: (List<Stroke>) -> Unit = {}
    var onAnalyze: (Bitmap) -> Unit = {}
    var onStickersSave: (List<PageSticker>) -> Unit = {}
    var stickerResolver: (String) -> Bitmap? = { null }
    var stickers: List<PageSticker> = emptyList()
        set(value) { field = value; invalidate() }
    private var currentStickerId: String? = null
    private var fingerNavigating = false
    var pageBitmap: Bitmap? = null
        set(value) { field = value; invalidate() }
    var marks: List<Stroke> = emptyList()
        set(value) { field = value; invalidate() }
    var tool: Tool = Tool.PEN
    var inkColor: Int = Color.rgb(36, 75, 67)
    var penWidth: Float = 3.0f
    var fingerInk = false
    private var pending = mutableListOf<InkPoint>()
    private var drawing = false
    private var multiTouch = false
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var priorMidX = 0f
    private var priorMidY = 0f
    private var selectedIds: Set<String> = emptySet()
    private var dragged = false
    private val undone = mutableListOf<Stroke>()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val pageRect = RectF()
    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoom = (zoom * detector.scaleFactor).coerceIn(1f, 5f)
            invalidate()
            return true
        }
    })

    fun bind(bitmap: Bitmap?, strokes: List<Stroke>, selectedTool: Tool, color: Int, size: Float, allowFinger: Boolean) {
        pageBitmap = bitmap
        marks = strokes
        tool = selectedTool
        inkColor = color
        penWidth = size
        fingerInk = allowFinger
    }

    fun increaseStickerSize(delta: Float) {
        val id = currentStickerId ?: return
        stickers = stickers.map { if (it.id == id) it.copy(size = (it.size + delta).coerceIn(0.08f, 0.95f)) else it }
        onStickersSave(stickers)
    }
    fun deleteSelectedSticker() {
        val id = currentStickerId ?: return
        stickers = stickers.filterNot { it.id == id }
        currentStickerId = null
        onStickersSave(stickers)
    }
    fun resetView() { zoom = 1f; panX = 0f; panY = 0f; selectedIds = emptySet(); invalidate() }
    fun undo() {
        if (marks.isNotEmpty()) {
            undone.add(marks.last()); marks = marks.dropLast(1); onSave(marks)
        }
    }
    fun redo() {
        if (undone.isNotEmpty()) { marks = marks + undone.removeAt(undone.lastIndex); onSave(marks) }
    }
    fun clearSelection() { selectedIds = emptySet(); invalidate() }
    fun eraseSelection() {
        if (selectedIds.isNotEmpty()) { marks = marks.filterNot { it.id in selectedIds }; selectedIds = emptySet(); undone.clear(); onSave(marks) }
    }

    private fun fitRect(): RectF {
        val b = pageBitmap ?: return RectF()
        val margin = 16f * resources.displayMetrics.density
        val availableW = max(1f, width - 2 * margin)
        val availableH = max(1f, height - 2 * margin)
        val ratio = min(availableW / b.width, availableH / b.height)
        val w = b.width * ratio
        val h = b.height * ratio
        return RectF((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
    }
    private fun pos(x: Float, y: Float, pressure: Float): InkPoint {
        val originalX = (x - panX - width / 2f) / zoom + width / 2f
        val originalY = (y - panY - height / 2f) / zoom + height / 2f
        return InkPoint(((originalX - pageRect.left) / pageRect.width()).coerceIn(0f, 1f),
            ((originalY - pageRect.top) / pageRect.height()).coerceIn(0f, 1f), pressure.coerceIn(0.2f, 2.5f))
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(233, 238, 234))
        val b = pageBitmap ?: return
        pageRect.set(fitRect())
        canvas.save()
        canvas.translate(panX, panY)
        canvas.scale(zoom, zoom, width / 2f, height / 2f)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; setShadowLayer(8f, 0f, 2f, 0x33000000) }
        canvas.drawRoundRect(pageRect, 5f, 5f, bg)
        canvas.drawBitmap(b, null, pageRect, bitmapPaint)
        for (sticker in stickers) {
            val source = stickerResolver(sticker.imageName) ?: continue
            val left = pageRect.left + sticker.x * pageRect.width()
            val top = pageRect.top + sticker.y * pageRect.height()
            val sw = sticker.size * pageRect.width()
            val sh = sw * source.height.toFloat() / max(1f, source.width.toFloat())
            val rectangle = RectF(left, top, left + sw, top + sh)
            canvas.drawBitmap(source, null, rectangle, bitmapPaint)
            if (sticker.id == currentStickerId && tool == Tool.STICKER) {
                val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE; color = Color.rgb(0, 140, 132); strokeWidth = 3f
                }
                canvas.drawRect(rectangle, line)
            }
        }
        for (stroke in marks) renderStroke(canvas, stroke, stroke.id in selectedIds)
        if (pending.isNotEmpty()) renderStroke(canvas, Stroke(tool = tool, color = inkColor, width = penWidth, points = pending), false)
        canvas.restore()
    }

    private fun renderStroke(canvas: Canvas, stroke: Stroke, selected: Boolean) {
        val points = stroke.points
        if (points.isEmpty()) return
        val p = inkPaint
        p.style = Paint.Style.STROKE
        p.color = if (selected) Color.rgb(11, 147, 183) else stroke.color
        p.alpha = if (stroke.tool == Tool.HIGHLIGHT) 94 else 255
        p.strokeWidth = stroke.width * resources.displayMetrics.density * (if (stroke.tool == Tool.HIGHLIGHT) 4f else 1f)
        val x: (InkPoint) -> Float = { pageRect.left + it.x * pageRect.width() }
        val y: (InkPoint) -> Float = { pageRect.top + it.y * pageRect.height() }
        val first = points.first()
        val last = points.last()
        val path = Path()
        when (stroke.tool) {
            Tool.CIRCLE -> { canvas.drawOval(RectF(min(x(first), x(last)), min(y(first), y(last)), max(x(first), x(last)), max(y(first), y(last))), p); return }
            Tool.RECTANGLE -> { canvas.drawRect(RectF(min(x(first), x(last)), min(y(first), y(last)), max(x(first), x(last)), max(y(first), y(last))), p); return }
            Tool.UNDERLINE -> { canvas.drawLine(x(first), y(last), x(last), y(last), p); return }
            Tool.WAVE -> {
                val sx = x(first); val ex = x(last); val yy = y(last)
                path.moveTo(sx, yy)
                val length = abs(ex - sx)
                for (i in 1..80) {
                    val t = i / 80f
                    path.lineTo(sx + (ex - sx) * t, yy + sin(t * length / 9f) * 4f * resources.displayMetrics.density)
                }
                canvas.drawPath(path, p); return
            }
            Tool.ARROW -> {
                val ex = x(last); val ey = y(last)
                val angle = atan2(ey - y(first), ex - x(first))
                val head = 16f * resources.displayMetrics.density
                canvas.drawLine(x(first), y(first), ex, ey, p)
                canvas.drawLine(ex, ey, ex - head * cos(angle - 0.5f), ey - head * sin(angle - 0.5f), p)
                canvas.drawLine(ex, ey, ex - head * cos(angle + 0.5f), ey - head * sin(angle + 0.5f), p)
                return
            }
            Tool.LASSO, Tool.ANALYZE -> {
                p.color = if (stroke.tool == Tool.ANALYZE) Color.rgb(14, 136, 169) else Color.rgb(47, 123, 103)
                p.strokeWidth = 2.5f * resources.displayMetrics.density
            }
            else -> Unit
        }
        path.moveTo(x(first), y(first))
        if (points.size == 1) path.lineTo(x(first) + 0.1f, y(first) + 0.1f)
        for (i in 1 until points.size) {
            val pt = points[i]
            path.lineTo(x(pt), y(pt))
        }
        if (stroke.tool == Tool.ANALYZE || stroke.tool == Tool.LASSO) path.close()
        canvas.drawPath(path, p)
        p.alpha = 255
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (pageBitmap == null || pageRect.isEmpty) return false
        scaler.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                multiTouch = true
                drawing = false
                pending.clear()
                if (event.pointerCount >= 2) {
                    priorMidX = (event.getX(0) + event.getX(1)) / 2f
                    priorMidY = (event.getY(0) + event.getY(1)) / 2f
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> return true
            MotionEvent.ACTION_CANCEL -> { pending.clear(); drawing = false; multiTouch = false; invalidate(); return true }
            MotionEvent.ACTION_DOWN -> {
                multiTouch = false
                dragged = false
                val isStylus = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS || event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER
                fingerNavigating = !fingerInk && !isStylus
                if (tool == Tool.PAN || fingerNavigating) { priorMidX = event.x; priorMidY = event.y; return true }
                val pt = pos(event.x, event.y, event.pressure)
                if (tool == Tool.STICKER) {
                    currentStickerId = stickers.lastOrNull { sticker ->
                        val bmp = stickerResolver(sticker.imageName)
                        val w = sticker.size
                        val h = w * (bmp?.height?.toFloat() ?: 1f) / max(1f, bmp?.width?.toFloat() ?: 1f) * pageRect.width() / pageRect.height()
                        pt.x in sticker.x..(sticker.x + w) && pt.y in sticker.y..(sticker.y + h)
                    }?.id
                    priorMidX = event.x; priorMidY = event.y
                    invalidate()
                    return true
                }
                if (tool == Tool.ERASER) { drawing = true; eraseNear(pt); return true }
                if (tool == Tool.LASSO && selectedIds.isNotEmpty() && selectedRect().contains(pt.x, pt.y)) {
                    drawing = true; pending.add(pt); dragged = true; return true
                }
                drawing = true; pending.clear(); pending.add(pt); invalidate(); return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2) {
                    val mx = (event.getX(0) + event.getX(1)) / 2f
                    val my = (event.getY(0) + event.getY(1)) / 2f
                    panX += mx - priorMidX; panY += my - priorMidY
                    priorMidX = mx; priorMidY = my; invalidate(); return true
                }
                if (multiTouch) return true
                if (tool == Tool.PAN || fingerNavigating) { panX += event.x - priorMidX; panY += event.y - priorMidY; priorMidX = event.x; priorMidY = event.y; invalidate(); return true }
                if (tool == Tool.STICKER && currentStickerId != null) {
                    val start = pos(priorMidX, priorMidY, 1f)
                    val end = pos(event.x, event.y, 1f)
                    stickers = stickers.map { if (it.id == currentStickerId) it.copy(
                        x = (it.x + end.x - start.x).coerceIn(0f, 1f),
                        y = (it.y + end.y - start.y).coerceIn(0f, 1f)
                    ) else it }
                    priorMidX = event.x; priorMidY = event.y
                    invalidate(); return true
                }
                if (!drawing) return true
                val pt = pos(event.x, event.y, event.pressure)
                if (dragged && pending.isNotEmpty()) {
                    val old = pending.last(); val dx = pt.x - old.x; val dy = pt.y - old.y
                    marks = marks.map { s -> if (s.id in selectedIds) s.copy(points = s.points.map { ip -> ip.copy(x = (ip.x + dx).coerceIn(0f, 1f), y = (ip.y + dy).coerceIn(0f, 1f)) }) else s }
                    pending.add(pt)
                } else if (tool == Tool.ERASER) eraseNear(pt)
                else pending.add(pt)
                invalidate(); return true
            }
            MotionEvent.ACTION_UP -> {
                if (tool == Tool.STICKER && currentStickerId != null) { onStickersSave(stickers); return true }
                if (multiTouch) { multiTouch = false; return true }
                if (!drawing) return true
                drawing = false
                pending.add(pos(event.x, event.y, event.pressure))
                val completed = pending.toList()
                pending.clear()
                when (tool) {
                    Tool.ANALYZE -> cropFromSelection(completed)?.let(onAnalyze)
                    Tool.LASSO -> if (dragged) { onSave(marks) } else {
                        val region = bounds(completed)
                        selectedIds = marks.filter { s -> s.points.any { region.contains(it.x, it.y) } }.map { it.id }.toSet()
                    }
                    Tool.ERASER, Tool.PAN -> Unit
                    else -> { undone.clear(); marks = marks + Stroke(tool = tool, color = inkColor, width = penWidth, points = completed); onSave(marks) }
                }
                invalidate(); return true
            }
        }
        return true
    }

    private fun bounds(points: List<InkPoint>): RectF {
        if (points.isEmpty()) return RectF()
        return RectF(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
    }
    private fun selectedRect(): RectF = bounds(marks.filter { it.id in selectedIds }.flatMap { it.points }).apply { inset(-0.02f, -0.02f) }
    private fun eraseNear(point: InkPoint) {
        val hit = marks.lastOrNull { s -> s.points.any { p -> hypot((p.x - point.x).toDouble(), (p.y - point.y).toDouble()) < 0.025 } }
        if (hit != null) { marks = marks.filterNot { it.id == hit.id }; undone.clear(); onSave(marks) }
    }
    private fun cropFromSelection(points: List<InkPoint>): Bitmap? {
        val original = pageBitmap ?: return null
        if (points.size < 3) return null
        val r = bounds(points)
        if (r.width() < 0.01f || r.height() < 0.01f) return null
        val margin = 0.025f
        val l = ((r.left - margin).coerceIn(0f, 1f) * original.width).toInt()
        val t = ((r.top - margin).coerceIn(0f, 1f) * original.height).toInt()
        val right = ((r.right + margin).coerceIn(0f, 1f) * original.width).toInt()
        val bottom = ((r.bottom + margin).coerceIn(0f, 1f) * original.height).toInt()
        if (right <= l || bottom <= t) return null
        return Bitmap.createBitmap(original, l, t, right - l, bottom - t)
    }
}
