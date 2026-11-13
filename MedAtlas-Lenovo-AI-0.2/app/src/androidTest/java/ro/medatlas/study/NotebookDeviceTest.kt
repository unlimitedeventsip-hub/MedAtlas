package ro.medatlas.study

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ro.medatlas.study.data.LibraryStore
import ro.medatlas.study.data.InkPoint
import ro.medatlas.study.data.Tool
import ro.medatlas.study.ui.PenGestures
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NotebookDeviceTest {
    @get:Rule val ui = createEmptyComposeRule()

    /**
     * Regression for the actual deterministic input path. The ML Kit Romanian
     * model is downloaded on first real use; emulators in CI may have no
     * internet/model service and cannot reliably exercise that remote step.
     */
    @Test fun convertsVectorStrokesIntoDigitalInkOffline() {
        fun stroke(vararg xy: Pair<Float, Float>) = ro.medatlas.study.data.Stroke(points = xy.map {
            ro.medatlas.study.data.InkPoint(it.first, it.second)
        })
        val sample = listOf(
            stroke(.10f to .50f, .17f to .20f, .25f to .50f),
            stroke(.13f to .39f, .21f to .39f),
            stroke(.28f to .50f, .28f to .34f, .29f to .42f, .32f to .35f, .36f to .36f, .37f to .50f),
            stroke(.50f to .36f, .46f to .34f, .42f to .39f, .42f to .48f, .46f to .50f, .50f to .44f, .50f to .34f, .51f to .50f)
        )
        val ink = createDigitalInk(sample, 800, 1000)
        assertEquals(4, ink.strokes.size)
        val first = ink.strokes[0].pointsInGlobalCoordinates
        assertEquals(3, first.size)
        assertEquals(80f, first[0].x)
        assertEquals(500f, first[0].y)
        assertTrue(ink.strokes.all { it.pointsInGlobalCoordinates.isNotEmpty() })
    }

    @Test fun penGesturesRecognizeClosedShapesAndOptInScratch() {
        val ellipse = (0..40).map { i ->
            val angle = 2.0 * Math.PI * i / 40
            InkPoint(.5f + (.17 * kotlin.math.cos(angle)).toFloat(),
                .47f + (.11 * kotlin.math.sin(angle)).toFloat())
        }
        assertEquals(Tool.CIRCLE, PenGestures.perfectShape(ellipse))

        val corners = listOf(.25f to .26f, .75f to .26f, .75f to .75f,
            .25f to .75f, .25f to .26f)
        val rectangle = corners.zipWithNext().flatMap { (start, end) ->
            (0..10).map { step ->
                val progress = step / 10f
                InkPoint(start.first + (end.first - start.first) * progress,
                    start.second + (end.second - start.second) * progress)
            }
        }
        assertEquals(Tool.RECTANGLE, PenGestures.perfectShape(rectangle))

        val scratch = (0..6).flatMap { sweep ->
            (0..8).map { step ->
                val t = step / 8f
                InkPoint(if (sweep % 2 == 0) .30f + .4f * t else .70f - .4f * t,
                    .45f + sweep * .006f)
            }
        }
        assertTrue(PenGestures.isScratch(scratch))
        assertTrue(!PenGestures.isScratch(ellipse))
    }

    @Test fun exportIncludesEveryPageAndContinuesLongText() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = LibraryStore(context)
        val note = ro.medatlas.study.data.StudyNote(documentId = "export", page = 0, heading = "Atlas", body = "Text de curs\n".repeat(100))
        val file = java.io.File.createTempFile("caiet", ".pdf", context.cacheDir)
        try {
            file.outputStream().use { stream ->
                kotlinx.coroutines.runBlocking { exportNotebook(context, store, listOf(note, note.copy(body = "")), stream) }
            }
            android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                android.graphics.pdf.PdfRenderer(fd).use { pdf -> org.junit.Assert.assertTrue(pdf.pageCount >= 4) }
            }
        } finally { file.delete() }
    }

    @Test fun boardInkSurvivesPanAndReopen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("medatlas_editor", 0).edit().putBoolean("show_premium_intro", false).commit()
        val title = "Schema-" + UUID.randomUUID().toString().take(8)
        ActivityScenario.launch(MainActivity::class.java).use {
            ui.onNodeWithText("Notițe & planșe").performClick()
            ui.onNodeWithText("+ Planșă").performClick()
            ui.onNodeWithText("Numele planșei").performTextInput(title)
            ui.onNodeWithText("Creează planșa").performClick()
            ui.onNodeWithTag("board-surface").performTouchInput { swipe(centerLeft, centerRight, 200) }
            ui.onNodeWithText("Deplasează").performClick()
            ui.onNodeWithTag("board-surface").performTouchInput { swipe(centerLeft, centerRight, 200) }
            ui.onNodeWithText("‹ Bibliotecă").performClick()
            ui.onNodeWithText("Caută în bibliotecă").performTextInput(title)
            ui.onNode(hasText(title) and hasClickAction() and !hasSetTextAction()).performClick()
            ui.onNodeWithText("Încadrează tot").performClick()
            val store = LibraryStore(context)
            val board = store.boards().single { it.heading == title }
            assertEquals(1, store.ink(inkId(board.id), 0).size)
        }
    }

    @Test fun createWriteReopenAndSearchFromAnotherBook() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("medatlas_editor", 0).edit().putBoolean("show_premium_intro", false).commit()
        val book = "Fiziologie-" + UUID.randomUUID().toString().take(8)
        val phrase = "Potențial de repaus " + book
        ActivityScenario.launch(MainActivity::class.java).use {
            ui.onNodeWithText("Notițe & planșe").performClick()
            ui.onNodeWithText("+ Caiet").performClick()
            ui.onNodeWithText("Numele caietului").performTextInput(book)
            ui.onNodeWithText("Creează caietul").performClick()
            // Rare page layout controls are now behind the simplified toolbar.
            ui.onNodeWithText("Mai multe opțiuni").performClick()
            ui.onNodeWithText("Linii").performClick()
            ui.onNodeWithText("Caroiaj").performClick()
            ui.onNodeWithTag("notebook-ink").performTouchInput { swipe(centerLeft, centerRight, 300) }
            ui.onNodeWithText("Transcrie").performClick()
            ui.onNodeWithText("Verifică transcrierea").performTextInput(phrase)
            ui.onNodeWithText("Adaugă în pagină").performClick()
            ui.onNodeWithText("‹ Pagini").performClick()
            ui.onNodeWithText("+ Pagină").performClick()
            ui.onNodeWithText("‹ Pagini").performClick()
            ui.onNodeWithText("‹ Caiete").performClick()
            ui.onNodeWithText("Caută în bibliotecă").performTextInput(phrase)
            ui.onNodeWithText("Pagina 1").performClick()
            ui.onNodeWithText(phrase).assertExists()
            val store = LibraryStore(context)
            val pages = store.notes().filter { n -> n.course == book }
            assertEquals(2, pages.size)
            val first = pages.single { page -> page.body == phrase }
            assertEquals("GRID", first.paper)
            val key = "notebook-" + UUID.nameUUIDFromBytes(first.id.toByteArray(Charsets.UTF_8))
            assertEquals(1, store.ink(key, 0).size)
        }
    }
}
