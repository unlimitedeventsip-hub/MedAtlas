package ro.medatlas.study

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ro.medatlas.study.data.LibraryStore
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NotebookDeviceTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun createWriteReopenAndSearchFromAnotherBook() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("medatlas_editor", 0).edit().putBoolean("show_premium_intro", false).commit()
        val book = "Fiziologie-" + UUID.randomUUID().toString().take(8)
        val phrase = "Potențial de repaus " + book
        ActivityScenario.launch(MainActivity::class.java).use {
            ui.onNodeWithText("Notițe").performClick()
            ui.onNodeWithText("+ Caiet").performClick()
            ui.onNodeWithText("Numele caietului").performTextInput(book)
            ui.onNodeWithText("Creează caietul").performClick()
            ui.onNodeWithText("Linii").performClick()
            ui.onNodeWithText("Caroiaj").performClick()
            ui.onNodeWithTag("notebook-ink").performTouchInput { swipe(centerLeft, centerRight, 300) }
            ui.onNodeWithText("Textul paginii").performTextInput(phrase)
            ui.onNodeWithText("‹ Pagini").performClick()
            ui.onNodeWithText("+ Pagină").performClick()
            ui.onNodeWithText("‹ Pagini").performClick()
            ui.onNodeWithText("‹ Caiete").performClick()
            ui.onNodeWithText("Caută în toate caietele").performTextInput(phrase)
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
