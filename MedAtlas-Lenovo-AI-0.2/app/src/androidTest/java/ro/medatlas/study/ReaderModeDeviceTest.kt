package ro.medatlas.study

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ro.medatlas.study.data.InkPoint
import ro.medatlas.study.data.Stroke
import ro.medatlas.study.data.Tool
import ro.medatlas.study.ui.StudyCanvasView

@RunWith(AndroidJUnit4::class)
class ReaderModeDeviceTest {
    /**
     * Regression: users may swipe or tap a PDF with a stylus while in Citire.
     * Neither the ink model nor its persistence callback may be changed.
     */
    @Test fun readonlyReaderRejectsEditingAndAllowsNavigation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = Bitmap.createBitmap(800, 1100, Bitmap.Config.ARGB_8888)
        val original = listOf(Stroke(tool = Tool.PEN, points = listOf(
            InkPoint(.2f, .3f), InkPoint(.7f, .3f))))
        val reader = StudyCanvasView(context)
        reader.layout(0, 0, 900, 1200)
        reader.pageBitmap = bitmap
        reader.marks = original
        reader.tool = Tool.ERASER // A previously selected editing tool must not matter.
        var saved = 0
        reader.onSave = { saved++ }
        reader.readOnly = true
        reader.draw(Canvas(Bitmap.createBitmap(900, 1200, Bitmap.Config.ARGB_8888)))

        fun event(action: Int, time: Long, x: Float, y: Float): MotionEvent =
            MotionEvent.obtain(0L, time, action, x, y, 0)

        val down = event(MotionEvent.ACTION_DOWN, 10L, 420f, 460f)
        val move = event(MotionEvent.ACTION_MOVE, 40L, 510f, 500f)
        val up = event(MotionEvent.ACTION_UP, 70L, 510f, 500f)
        try {
            assertTrue(reader.onTouchEvent(down))
            assertTrue(reader.onTouchEvent(move))
            assertTrue(reader.onTouchEvent(up))
        } finally {
            down.recycle()
            move.recycle()
            up.recycle()
        }

        reader.undo()
        reader.redo()
        reader.eraseSelection()
        assertFalse(reader.pasteSelection())
        assertFalse(reader.resizeSelection(1.5f))
        assertEquals(original, reader.marks)
        assertEquals(0, saved)

        reader.scrollThroughFingers = true
        val scrollingTouch = event(MotionEvent.ACTION_DOWN, 100L, 490f, 600f)
        try {
            assertFalse(reader.onTouchEvent(scrollingTouch))
        } finally {
            scrollingTouch.recycle()
            bitmap.recycle()
        }
        assertEquals(original, reader.marks)
        assertEquals(0, saved)
    }
}
