package ro.medatlas.study.ui

import ro.medatlas.study.data.InkPoint
import ro.medatlas.study.data.Tool
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Local geometric pen gestures. No handwriting recognition model or network needed.
 * Hold the pen at the end of a closed loop to turn it into a perfect shape.
 * Scratch-to-erase is opt-in to protect legitimate handwritten strokes.
 */
internal object PenGestures {
    fun perfectShape(points: List<InkPoint>): Tool? {
        if (points.size < 14) return null
        val left = points.minOf { it.x }
        val right = points.maxOf { it.x }
        val top = points.minOf { it.y }
        val bottom = points.maxOf { it.y }
        val w = right - left
        val h = bottom - top
        if (w < .035f || h < .035f || w / h !in .22f..4.5f) return null
        val diagonal = hypot(w.toDouble(), h.toDouble()).toFloat()
        val first = points.first()
        val last = points.last()
        if (hypot((first.x - last.x).toDouble(),
                (first.y - last.y).toDouble()) > diagonal * .25f) return null
        val sampled = points.filterIndexed { index, _ -> index % 2 == 0 }
        if (sampled.size < 7) return null

        // A rectangle lies near the four edges of its bounding box.
        val nearEdge = sampled.count { p ->
            val nx = ((p.x - left) / w).coerceIn(0f, 1f)
            val ny = ((p.y - top) / h).coerceIn(0f, 1f)
            min(min(nx, 1f - nx), min(ny, 1f - ny)) <= .11f
        }.toFloat() / sampled.size
        // A circle also passes near each bounding-box edge. Require the
        // actual four corners to distinguish a rectangle from an ellipse.
        val cornersSeen = sampled.mapNotNull { p ->
            val nx = ((p.x - left) / w).coerceIn(0f, 1f)
            val ny = ((p.y - top) / h).coerceIn(0f, 1f)
            when {
                nx <= .13f && ny <= .13f -> 0
                nx >= .87f && ny <= .13f -> 1
                nx >= .87f && ny >= .87f -> 2
                nx <= .13f && ny >= .87f -> 3
                else -> null
            }
        }.toSet()
        if (nearEdge >= .75f && cornersSeen.size == 4) return Tool.RECTANGLE

        // Ellipse: normalized radius of points about its center is near one.
        val onEllipse = sampled.count { p ->
            val nx = 2f * (p.x - (left + right) / 2f) / w
            val ny = 2f * (p.y - (top + bottom) / 2f) / h
            val r = hypot(nx.toDouble(), ny.toDouble())
            r in .67..1.33
        }.toFloat() / sampled.size
        return if (onEllipse >= .72f) Tool.CIRCLE else null
    }

    fun isScratch(points: List<InkPoint>): Boolean {
        if (points.size < 18) return false
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val w = maxX - minX
        val h = maxY - minY
        if (w < .055f || h > .10f || h > w * .65f) return false
        var lastDirection = 0
        var reversals = 0
        var travelled = 0f
        for (i in 1 until points.size) {
            val dx = points[i].x - points[i-1].x
            travelled += abs(dx)
            if (abs(dx) < w * .055f) continue
            val direction = if (dx > 0f) 1 else -1
            if (lastDirection != 0 && lastDirection != direction) reversals++
            lastDirection = direction
        }
        return reversals >= 4 && travelled >= 3.4f * w
    }
}
