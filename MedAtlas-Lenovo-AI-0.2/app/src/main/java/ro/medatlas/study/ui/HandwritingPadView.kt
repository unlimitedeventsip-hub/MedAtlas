package ro.medatlas.study.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View

/** Simple pen input surface. OCR is performed explicitly by the user after writing. */
class HandwritingPadView(context: Context) : View(context) {
    private val strokes = mutableListOf<Path>()
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(27, 60, 62)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 4f * resources.displayMetrics.density
    }
    private var active = Path()
    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)
        val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(230, 235, 236)
            strokeWidth = resources.displayMetrics.density
        }
        val spacing = 36f * resources.displayMetrics.density
        var y = spacing
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, grid)
            y += spacing
        }
        strokes.forEach { canvas.drawPath(it, pen) }
        canvas.drawPath(active, pen)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                active = Path().apply { moveTo(event.x, event.y) }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.historySize) {
                    active.lineTo(event.getHistoricalX(i), event.getHistoricalY(i))
                }
                active.lineTo(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                active.lineTo(event.x, event.y)
                strokes.add(active)
                active = Path()
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                active = Path()
                invalidate()
                return true
            }
        }
        return true
    }
    fun clearInk() {
        strokes.clear()
        active = Path()
        invalidate()
    }
    fun snapshot(): Bitmap {
        val bmp = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp))
        return bmp
    }
}
