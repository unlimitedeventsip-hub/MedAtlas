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
import org.json.JSONArray
import java.io.File
import java.util.UUID
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import ro.medatlas.study.data.PageSticker
import ro.medatlas.study.data.PageComment
import ro.medatlas.study.data.PageTextBox
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
    var onCommentCreate: (InkPoint, String) -> Unit = { _, _ -> }
    var onCommentOpen: (PageComment) -> Unit = {}
    var onTextCreate: (InkPoint) -> Unit = {}
    var onTextOpen: (PageTextBox) -> Unit = {}
    var onTextsSave: (List<PageTextBox>) -> Unit = {}
    var textBoxes: List<PageTextBox> = emptyList()
        set(value) { field = value; invalidate() }
    private var draggingTextId: String? = null
    var comments: List<PageComment> = emptyList()
        set(value) { field = value; invalidate() }
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
    var smartUnderline = true
    var smartMarker = true
    var fingerInk = false
    /** In continuous PDF mode let finger gestures reach the outer page scroller. */
    var scrollThroughFingers = false
    private var pending = mutableListOf<InkPoint>()
    private var ocrBitmap: Bitmap? = null
    private var wordRegions: List<Pair<RectF, String>> = emptyList()

    private fun prepareTextRegions(bitmap: Bitmap?) {
        if (bitmap == null || ocrBitmap === bitmap) return
        ocrBitmap = bitmap
        wordRegions = emptyList()
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        client.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                if (ocrBitmap !== bitmap) return@addOnSuccessListener
                wordRegions = result.textBlocks.flatMap { it.lines }
                    .flatMap { it.elements }.mapNotNull { element ->
                        val rect = element.boundingBox ?: return@mapNotNull null
                        RectF(rect.left / bitmap.width.toFloat(), rect.top / bitmap.height.toFloat(),
                            rect.right / bitmap.width.toFloat(), rect.bottom / bitmap.height.toFloat()) to element.text
                    }
            }.addOnCompleteListener { client.close() }
    }

    private fun alignWithRecognizedText(tool: Tool, points: List<InkPoint>): List<InkPoint>? {
        if (points.size < 2 || wordRegions.isEmpty()) return null
        val left = points.minOf { it.x }
        val right = points.maxOf { it.x }
        val meanY = points.map { it.y }.average().toFloat()
        val matched = wordRegions.filter { (rect, _) ->
            val xOverlap = min(right, rect.right) - max(left, rect.left)
            val verticalDistance = if (tool == Tool.UNDERLINE)
                kotlin.math.abs(meanY - rect.bottom) else kotlin.math.abs(meanY - rect.centerY())
            xOverlap >= -.012f && verticalDistance <= .025f
        }
        if (matched.isEmpty()) return null
        val regionLeft = matched.minOf { it.first.left }.coerceIn(0f, 1f)
        val regionRight = matched.maxOf { it.first.right }.coerceIn(0f, 1f)
        val y = if (tool == Tool.UNDERLINE)
            (matched.maxOf { it.first.bottom } + .004f).coerceAtMost(1f)
        else (matched.minOf { it.first.top } + matched.maxOf { it.first.bottom }) / 2f
        return listOf(InkPoint(regionLeft, y), InkPoint(regionRight, y))
    }

    private fun closestText(point: InkPoint): String {
        return wordRegions.minByOrNull { (rect, _) ->
            kotlin.math.abs(rect.centerX() - point.x) + kotlin.math.abs(rect.centerY() - point.y)
        }?.takeIf { (rect, _) ->
            kotlin.math.abs(rect.centerX() - point.x) < .065f &&
                kotlin.math.abs(rect.centerY() - point.y) < .045f
        }?.second.orEmpty()
    }
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
        if (selectedTool == Tool.UNDERLINE || selectedTool == Tool.HIGHLIGHT || selectedTool == Tool.COMMENT) prepareTextRegions(bitmap)
        marks = strokes
        tool = selectedTool
        val editorPrefs = context.getSharedPreferences("medatlas_editor", Context.MODE_PRIVATE)
        smartUnderline = editorPrefs.getBoolean("smart_underline", true)
        smartMarker = editorPrefs.getBoolean("smart_marker", true)
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
    fun selectedInk(): List<Stroke> = if (selectedIds.isEmpty()) marks else marks.filter { it.id in selectedIds }
    fun clearSelection() { selectedIds = emptySet(); invalidate() }
    fun eraseSelection() {
        if (selectedIds.isNotEmpty()) { marks = marks.filterNot { it.id in selectedIds }; selectedIds = emptySet(); undone.clear(); onSave(marks) }
    }

    /** Clipboard for selected vector ink: private to this app, available across notebook pages. */
    private fun inkClipboard() = File(context.filesDir, "medatlas/ink_clipboard.json")

    fun copySelection(): Boolean {
        val selected = marks.filter { it.id in selectedIds }
        if (selected.isEmpty()) return false
        return runCatching {
            val file = inkClipboard()
            file.parentFile?.mkdirs()
            val contents = JSONArray().apply { selected.forEach { put(it.json()) } }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(contents.toString())
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
            true
        }.getOrDefault(false)
    }

    fun pasteSelection(): Boolean {
        val copied = runCatching {
            val file = inkClipboard()
            val array = JSONArray(file.readText())
            (0 until array.length()).map { Stroke.parse(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        if (copied.isEmpty()) return false
        val allPoints = copied.flatMap { it.points }
        if (allPoints.isEmpty()) return false
        val dx = if (allPoints.maxOf { it.x } < .95f) .035f else -.035f
        val dy = if (allPoints.maxOf { it.y } < .95f) .035f else -.035f
        val pasted = copied.map { stroke ->
            stroke.copy(id = UUID.randomUUID().toString(),
                points = stroke.points.map { point ->
                    point.copy(x = (point.x + dx).coerceIn(0f, 1f),
                        y = (point.y + dy).coerceIn(0f, 1f))
                })
        }
        marks = marks + pasted
        selectedIds = pasted.map { it.id }.toSet()
        undone.clear()
        onSave(marks)
        invalidate()
        return true
    }

    /** Resize selected ink proportionally about its center. Does not alter other page content. */
    fun resizeSelection(factor: Float): Boolean {
        if (!factor.isFinite() || factor <= 0f) return false
        val selection = marks.filter { it.id in selectedIds }
        val allPoints = selection.flatMap { it.points }
        if (allPoints.isEmpty()) return false
        val cx = (allPoints.minOf { it.x } + allPoints.maxOf { it.x }) / 2f
        val cy = (allPoints.minOf { it.y } + allPoints.maxOf { it.y }) / 2f
        marks = marks.map { stroke ->
            if (stroke.id in selectedIds) stroke.copy(points = stroke.points.map { point ->
                point.copy(x = (cx + (point.x - cx) * factor).coerceIn(0f, 1f),
                    y = (cy + (point.y - cy) * factor).coerceIn(0f, 1f))
            }) else stroke
        }
        undone.clear()
        onSave(marks)
        invalidate()
        return true
    }

    private fun insideLasso(point: InkPoint, polygon: List<InkPoint>): Boolean {
        var inside = false
        var previous = polygon.last()
        for (next in polygon) {
            if ((previous.y > point.y) != (next.y > point.y)) {
                val crossing = previous.x + (point.y - previous.y) * (next.x - previous.x) / (next.y - previous.y)
                if (point.x < crossing) inside = !inside
            }
            previous = next
        }
        return inside
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
        textBoxes.forEach { box ->
            val x = pageRect.left + box.x * pageRect.width()
            val y = pageRect.top + box.y * pageRect.height()
            val fontSize = box.sizeSp * resources.displayMetrics.scaledDensity
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = box.color
                textSize = fontSize
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            }
            box.content.lines().take(18).forEachIndexed { row, line ->
                canvas.drawText(line.take(180), x, y + (row + 1) * fontSize * 1.22f, paint)
            }
            if (tool == Tool.TEXT) {
                val widthText = box.content.lines().maxOfOrNull { paint.measureText(it.take(180)) } ?: 20f
                val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(58, 128, 123); style = Paint.Style.STROKE; strokeWidth = 1.8f
                }
                canvas.drawRoundRect(RectF(x-4f, y, x+widthText+4f,
                    y + box.content.lines().size.coerceAtMost(18) * fontSize * 1.22f + 4f),
                    4f, 4f, highlight)
            }
        }
        val commentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(210, 131, 156); style = Paint.Style.FILL
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; typeface = android.graphics.Typeface.DEFAULT_BOLD
            textSize = 12f * resources.displayMetrics.density
            textAlign = Paint.Align.CENTER
        }
        comments.forEachIndexed { index, comment ->
            val cx = pageRect.left + comment.x * pageRect.width()
            val cy = pageRect.top + comment.y * pageRect.height()
            val radius = 13f * resources.displayMetrics.density
            canvas.drawCircle(cx, cy, radius, commentPaint)
            canvas.drawText("${index + 1}", cx, cy + radius * 0.33f, labelPaint)
        }
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
            Tool.HIGHLIGHT -> {
                if (points.size >= 2 && smartMarker) {
                    val left = min(x(first), x(last)); val right = max(x(first), x(last))
                    val midY = (y(first) + y(last)) * .5f
                    val lineHeight = max(10f * resources.displayMetrics.density,
                        stroke.width * 6.5f * resources.displayMetrics.density)
                    p.style = Paint.Style.FILL
                    val oldColor = p.color
                    p.alpha = 78
                    canvas.drawRoundRect(RectF(left, midY - lineHeight / 2f,
                        right, midY + lineHeight / 2f), 4f, 4f, p)
                    p.color = oldColor; p.style = Paint.Style.STROKE; p.alpha = 255
                    return
                }
            }
            Tool.BRUSH -> {
                for (index in 1 until points.size) {
                    val a = points[index - 1]
                    val b = points[index]
                    p.strokeWidth = stroke.width * resources.displayMetrics.density *
                        ((a.pressure + b.pressure) / 2).coerceIn(.25f, 2f)
                    canvas.drawLine(x(a), y(a), x(b), y(b), p)
                }
                return
            }
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
        if (scrollThroughFingers && !fingerInk && event.actionMasked == MotionEvent.ACTION_DOWN
            && event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS
            && event.getToolType(0) != MotionEvent.TOOL_TYPE_ERASER) {
            // Finger = scroll the whole PDF. Stylus = annotate the selected page.
            return false
        }
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
            MotionEvent.ACTION_CANCEL -> { parent?.requestDisallowInterceptTouchEvent(false); pending.clear(); drawing = false; multiTouch = false; invalidate(); return true }
            MotionEvent.ACTION_DOWN -> {
                multiTouch = false
                dragged = false
                val isStylus = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS || event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER
                if (isStylus) parent?.requestDisallowInterceptTouchEvent(true)
                fingerNavigating = !fingerInk && !isStylus
                if (tool == Tool.PAN || fingerNavigating) { priorMidX = event.x; priorMidY = event.y; return true }
                val pt = pos(event.x, event.y, event.pressure)
                if (tool == Tool.TEXT) {
                    val hit = textBoxes.lastOrNull { box ->
                        val boxX = box.x
                        val boxY = box.y
                        pt.x >= boxX - .018f && pt.x <= (boxX + .40f).coerceAtMost(1f) &&
                            pt.y >= boxY - .025f && pt.y <= (boxY + .09f + box.content.lines().size * .028f)
                    }
                    if (hit != null) {
                        draggingTextId = hit.id
                        priorMidX = event.x; priorMidY = event.y
                    } else onTextCreate(pt)
                    return true
                }
                if (tool == Tool.COMMENT) {
                    val radius = 0.028f
                    val hit = comments.lastOrNull {
                        kotlin.math.abs(it.x - pt.x) < radius && kotlin.math.abs(it.y - pt.y) < radius
                    }
                    if (hit != null) onCommentOpen(hit) else onCommentCreate(pt, closestText(pt))
                    return true
                }
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
                if (tool == Tool.TEXT && draggingTextId != null) {
                    val start = pos(priorMidX, priorMidY, 1f)
                    val end = pos(event.x, event.y, 1f)
                    textBoxes = textBoxes.map { item ->
                        if (item.id == draggingTextId) item.copy(
                            x = (item.x + end.x - start.x).coerceIn(0f, .96f),
                            y = (item.y + end.y - start.y).coerceIn(0f, .96f)
                        ) else item
                    }
                    priorMidX = event.x; priorMidY = event.y
                    dragged = true
                    return true
                }
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
                parent?.requestDisallowInterceptTouchEvent(false)
                if (tool == Tool.TEXT && draggingTextId != null) {
                    val id = draggingTextId
                    draggingTextId = null
                    if (!dragged) textBoxes.firstOrNull { it.id == id }?.let(onTextOpen)
                    onTextsSave(textBoxes)
                    return true
                }
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
                        selectedIds = marks.filter { stroke ->
                            stroke.points.any { region.contains(it.x, it.y) && insideLasso(it, completed) }
                        }.map { it.id }.toSet()
                    }
                    Tool.ERASER, Tool.PAN -> Unit
                    else -> {
                        val snapped = if ((tool == Tool.UNDERLINE && smartUnderline) ||
                            (tool == Tool.HIGHLIGHT && smartMarker))
                            alignWithRecognizedText(tool, completed) else null
                        val cleaned = snapped ?: when {
                            tool == Tool.UNDERLINE && smartUnderline && completed.size >= 2 -> {
                                val minX = completed.minOf { it.x }
                                val maxX = completed.maxOf { it.x }
                                // Slightly below the user's path; horizontal and elegant.
                                val baseY = (completed.map { it.y }.average().toFloat() + .004f).coerceIn(0f, 1f)
                                listOf(InkPoint(minX, baseY), InkPoint(maxX, baseY))
                            }
                            tool == Tool.HIGHLIGHT && smartMarker && completed.size >= 2 -> {
                                // Store a normalized, horizontal rectangle as a two-point stroke.
                                val minX = completed.minOf { it.x }
                                val maxX = completed.maxOf { it.x }
                                val centerY = completed.map { it.y }.average().toFloat().coerceIn(0f, 1f)
                                listOf(InkPoint(minX, centerY), InkPoint(maxX, centerY))
                            }
                            else -> completed
                        }
                        undone.clear()
                        marks = marks + Stroke(tool = tool, color = inkColor, width = penWidth, points = cleaned)
                        onSave(marks)
                    }
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
