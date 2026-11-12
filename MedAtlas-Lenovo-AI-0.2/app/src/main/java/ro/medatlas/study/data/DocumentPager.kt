package ro.medatlas.study.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.Closeable
import kotlin.math.max
import kotlin.math.min

/** PDF pages retain original visuals; Office files are shown in extracted-text reading mode. */
class DocumentPager(doc: StudyDocument, private val store: LibraryStore) : Closeable {
    private val descriptor: ParcelFileDescriptor? = if (doc.kind == "PDF")
        ParcelFileDescriptor.open(store.fileOf(doc), ParcelFileDescriptor.MODE_READ_ONLY) else null
    private val renderer: PdfRenderer? = descriptor?.let { PdfRenderer(it) }
    private val pages: List<List<String>> = if (renderer == null) {
        val lines = mutableListOf<String>()
        store.extractedText(doc.id).lines().forEach { paragraph ->
            if (paragraph.isEmpty()) lines.add("")
            else paragraph.chunked(88).forEach { lines.add(it) }
        }
        if (lines.isEmpty()) lines.add("Document gol")
        lines.chunked(38)
    } else emptyList()
    val count: Int get() = renderer?.pageCount ?: pages.size

    fun render(position: Int): Bitmap {
        val index = position.coerceIn(0, max(0, count - 1))
        val pdf = renderer
        if (pdf != null) {
            pdf.openPage(index).use { page ->
                val w = min(1800, max(800, page.width * 2))
                val h = max(1, (w * page.height.toFloat() / max(1, page.width)).toInt())
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bitmap
            }
        }
        val bitmap = Bitmap.createBitmap(1200, 1670, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val header = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(27, 90, 75); textSize = 44f; typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(39, 51, 52); textSize = 25f; typeface = android.graphics.Typeface.DEFAULT
        }
        canvas.drawText("MEDATLAS  •  READER", 85f, 100f, header)
        canvas.drawLine(85f, 122f, 1115f, 122f, Paint().apply { color = Color.rgb(205, 224, 218); strokeWidth = 2f })
        var y = 186f
        pages.getOrNull(index).orEmpty().forEach { line ->
            canvas.drawText(line, 85f, y, linePaint)
            y += 35f
        }
        linePaint.textSize = 20f
        linePaint.color = Color.GRAY
        canvas.drawText("Pagina ${index + 1} / $count  •  DOCX/PPTX: mod text, fără formatarea originală", 85f, 1590f, linePaint)
        return bitmap
    }

    override fun close() { renderer?.close(); descriptor?.close() }
}
