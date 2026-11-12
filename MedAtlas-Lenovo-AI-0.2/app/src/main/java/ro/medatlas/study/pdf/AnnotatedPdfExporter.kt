package ro.medatlas.study.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import ro.medatlas.study.data.*
import java.io.File
import kotlin.math.*

/** Flattened PDF export. Original source document is never modified. */
object AnnotatedPdfExporter {
    fun export(context: Context, document: StudyDocument, store: LibraryStore): File {
        val result = File(context.cacheDir, "exports").apply { mkdirs() }
        val filename = document.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(60).ifBlank { "curs" }
        val dest = File(result, "${filename}_MedAtlas.pdf")
        val pdf = PdfDocument()
        try {
            DocumentPager(document, store).use { pager ->
                repeat(pager.count) { index ->
                    val bitmap = pager.render(index)
                    try {
                        val info = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                        val page = pdf.startPage(info)
                        page.canvas.drawColor(android.graphics.Color.WHITE)
                        page.canvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                        store.stickers(document.id, index).forEach { sticker ->
                            val source = store.stickerBitmap(sticker.imageName) ?: return@forEach
                            val l = sticker.x * bitmap.width
                            val t = sticker.y * bitmap.height
                            val w = sticker.size * bitmap.width
                            val h = w * source.height.toFloat() / max(1f, source.width.toFloat())
                            page.canvas.drawBitmap(source, null, RectF(l, t, l + w, t + h), Paint(Paint.FILTER_BITMAP_FLAG))
                            source.recycle()
                        }
                        store.ink(document.id, index).forEach { stroke -> paintStroke(page.canvas, stroke, bitmap.width.toFloat(), bitmap.height.toFloat()) }
                        pdf.finishPage(page)
                    } finally { bitmap.recycle() }
                }
            }
            dest.outputStream().use { pdf.writeTo(it) }
        } finally { pdf.close() }
        return dest
    }

    private fun paintStroke(canvas: Canvas, stroke: Stroke, w: Float, h: Float) {
        val pts = stroke.points
        if (pts.isEmpty()) return
        val first = pts.first()
        val last = pts.last()
        val x: (InkPoint) -> Float = { it.x * w }
        val y: (InkPoint) -> Float = { it.y * h }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = stroke.color
            alpha = if (stroke.tool == Tool.HIGHLIGHT) 95 else 255
            strokeWidth = stroke.width * (w / 800f) * if (stroke.tool == Tool.HIGHLIGHT) 4f else 1f
        }
        when (stroke.tool) {
            Tool.HIGHLIGHT -> {
                val l = min(x(first), x(last))
                val r = max(x(first), x(last))
                val centerY = (y(first) + y(last)) / 2f
                val height = max(13f * w / 800f, stroke.width * 6.5f * w / 800f)
                paint.style = Paint.Style.FILL
                paint.alpha = 78
                canvas.drawRoundRect(RectF(l, centerY - height / 2f, r, centerY + height / 2f),
                    5f * w / 800f, 5f * w / 800f, paint)
            }
            Tool.CIRCLE -> canvas.drawOval(RectF(min(x(first), x(last)), min(y(first), y(last)), max(x(first), x(last)), max(y(first), y(last))), paint)
            Tool.RECTANGLE -> canvas.drawRect(RectF(min(x(first), x(last)), min(y(first), y(last)), max(x(first), x(last)), max(y(first), y(last))), paint)
            Tool.UNDERLINE -> canvas.drawLine(x(first), y(last), x(last), y(last), paint)
            Tool.WAVE -> {
                val path = Path().apply { moveTo(x(first), y(last)) }
                val dist = abs(x(last) - x(first))
                for (i in 1..80) {
                    val t = i / 80f
                    path.lineTo(x(first) + (x(last) - x(first)) * t, y(last) + sin(t * dist / 17f) * 4f * w / 800f)
                }
                canvas.drawPath(path, paint)
            }
            Tool.ARROW -> {
                val angle = atan2(y(last) - y(first), x(last) - x(first))
                val head = 16f * w / 800f
                canvas.drawLine(x(first), y(first), x(last), y(last), paint)
                canvas.drawLine(x(last), y(last), x(last) - head * cos(angle - .5f), y(last) - head * sin(angle - .5f), paint)
                canvas.drawLine(x(last), y(last), x(last) - head * cos(angle + .5f), y(last) - head * sin(angle + .5f), paint)
            }
            Tool.ERASER, Tool.LASSO, Tool.ANALYZE, Tool.PAN, Tool.STICKER, Tool.COMMENT -> Unit
            else -> {
                val path = Path().apply { moveTo(x(first), y(first)) }
                for (i in 1 until pts.size) path.lineTo(x(pts[i]), y(pts[i]))
                if (pts.size == 1) path.lineTo(x(first) + .1f, y(first) + .1f)
                canvas.drawPath(path, paint)
            }
        }
    }
}
