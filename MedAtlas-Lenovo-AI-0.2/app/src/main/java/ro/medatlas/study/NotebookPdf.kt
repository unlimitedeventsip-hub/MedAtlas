package ro.medatlas.study

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ro.medatlas.study.data.LibraryStore
import ro.medatlas.study.data.StudyNote
import ro.medatlas.study.data.Tool
import ro.medatlas.study.ui.StudyCanvasView

/** Each notebook page starts a landscape PDF sheet. Long text continues on additional sheets. */
internal suspend fun exportNotebook(context: Context, store: LibraryStore, pages: List<StudyNote>, out: OutputStream) {
    require(pages.isNotEmpty())
    withContext(Dispatchers.IO) {
        PdfDocument().use { pdf ->
            var number = 0
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 13f }
            val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xff284952.toInt(); textSize = 19f; typeface = Typeface.DEFAULT_BOLD
            }
            for (note in pages) {
                val paper = paperBitmap(note, store.notebookDrawing(note.handwritingFile), 1000)
                val strokes = store.ink(inkId(note.id), 0)
                val layout = StaticLayout.Builder.obtain(note.body, 0, note.body.length, textPaint, 345)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
                var line = 0
                do {
                    val sheet = pdf.startPage(PdfDocument.PageInfo.Builder(842,595,++number).create())
                    val canvas = sheet.canvas
                    canvas.drawColor(Color.WHITE)
                    canvas.save()
                    canvas.clipRect(24,10,780,44)
                    canvas.drawText(note.heading, 24f,34f,heading)
                    canvas.restore()
                    canvas.drawText(number.toString(), 793f,34f,textPaint)
                    if (line == 0) withContext(Dispatchers.Main.immediate) {
                        val view = StudyCanvasView(context)
                        view.bind(paper,strokes,Tool.PEN,Color.BLACK,2f,false)
                        view.smartMarker = false
                        view.layout(0,0,430,515)
                        canvas.save(); canvas.translate(24f,54f); view.draw(canvas); canvas.restore()
                    }
                    val top = layout.getLineTop(line)
                    var end = line + 1
                    while (end < layout.lineCount && layout.getLineBottom(end)-top <= 512) end++
                    canvas.save()
                    canvas.translate(472f,54f)
                    canvas.clipRect(0,0,345,layout.getLineBottom(end-1)-top)
                    canvas.translate(0f,-top.toFloat())
                    layout.draw(canvas)
                    canvas.restore()
                    pdf.finishPage(sheet)
                    line = end
                } while (line < layout.lineCount)
            }
            pdf.writeTo(out)
        }
    }
}
