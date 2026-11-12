package ro.medatlas.study

import android.content.Context

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ro.medatlas.study.anatomy.OrganAtlasActivity
import ro.medatlas.study.reference.MedicalReferenceActivity
import ro.medatlas.study.simulation.ClinicalSimulatorActivity
import ro.medatlas.study.simulation.ExamSimulatorActivity

@RunWith(AndroidJUnit4::class)
class WorkbookDeviceTest {
    @get:Rule val ui = createEmptyComposeRule()

    private fun listTo(label: String) {
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label))
    }

    private fun tap(label: String) {
        listTo(label)
        ui.onNodeWithText(label).performScrollTo().performClick()
    }

    private fun enter(label: String, value: String) {
        listTo(label)
        ui.onNodeWithText(label).performScrollTo().performTextInput(value)
    }

    @Test fun resumesIndividualPatientAtFollowUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("clinical-workbook", Context.MODE_PRIVATE).edit().clear().commit()
        val patient = "Nu mai pot duce coșul de rufe pe scări."
        ActivityScenario.launch(ClinicalSimulatorActivity::class.java).use {
            listTo(patient)
            ui.onNodeWithText(patient).performClick()
            tap("Feritină")
            ui.onNodeWithText("7 µg/L", substring = true).assertExists()
            enter("Diagnostic", "Anemie cu microcitoză")
            enter("Argumente și alternative", "Feritină scăzută")
            enter("Tratament și monitorizare", "Verific pierderile și urmăresc hemograma")
            tap("Evaluez semnele de alarmă, explic planul și organizez urmărirea")
            tap("Continuă · Ziua 7")
            listTo("Ziua 7")
            ui.onNodeWithText("Diagnostic · Anemie cu microcitoză").assertExists()
            enter("Diagnostic", "În urmărire")
            enter("Argumente și alternative", "Verific toleranța")
            enter("Tratament și monitorizare", "Reevaluare")
            tap("Reevaluez simptomele, investigațiile și toleranța planului")
            tap("Continuă · Luna 3")
        }
        ActivityScenario.launch(ClinicalSimulatorActivity::class.java).use {
            listTo(patient)
            ui.onNodeWithText(patient).performClick()
            listTo("Luna 3")
            ui.onNodeWithText("Diagnostic · În urmărire").assertExists()
            ui.onNodeWithText("Ecografia ginecologică", substring = true).assertExists()
            enter("Diagnostic", "Anemie în ameliorare")
            enter("Argumente și alternative", "Hemoglobina a crescut")
            enter("Tratament și monitorizare", "Reevaluare ginecologică")
            tap("Compar evoluția, funcția și riscurile; stabilesc un plan documentat")
            tap("S-au refăcut depozitele și a fost tratată cauza?")
            ui.onNodeWithText("Corect!").assertExists()
            tap("Anemie feriprivă")
            tap("Verifică diagnosticul și tratamentul")
            ui.onNodeWithText("Diagnostic corect!").assertExists()
        }
    }

    @Test fun opensAnatomyDetailAndGradesLab() {
        ActivityScenario.launch(OrganAtlasActivity::class.java).use {
            ui.onNodeWithText("Caută organ, regiune sau funcție").performTextInput("Glomer")
            ui.onNodeWithText("Glomerul").performClick()
            ui.onNodeWithText("Ghem capilar", substring = true).assertExists()
        }
        ActivityScenario.launch(MedicalReferenceActivity::class.java).use {
            listTo("Feritină și microcitoză")
            ui.onNodeWithText("Feritină și microcitoză").performClick()
            listTo("Verifică ce ai învățat")
            ui.onAllNodes(isSelectable())[0].performClick()
            ui.onNodeWithText("Corect!").assertExists()
        }
    }
    @Test fun startsOfflineExamAndNavigatesQuestions() {
        ActivityScenario.launch(ExamSimulatorActivity::class.java).use {
            ui.onNodeWithText("Simulare examen").assertExists()
            ui.onNodeWithText("Începe examenul").performClick()
            ui.onNodeWithText("Întrebarea 1 / 20").assertExists()
            ui.onNodeWithText("Următoarea").performClick()
            ui.onNodeWithText("Întrebarea 2 / 20").assertExists()
        }
    }

    @Test fun workbookBackupRoundTripAndOldZip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ro.medatlas.study.data.LibraryStore(context)
        val prefs = context.getSharedPreferences("clinical-workbook", Context.MODE_PRIVATE)
        prefs.edit().clear().putInt("f01.visit", 1).putBoolean("f01.opened", true)
            .putString("f01.0.Diagnostic", "Test cu diacritice")
            .putStringSet("f01.tests", setOf("0","1")).commit()
        val zip = java.io.File.createTempFile("workbook-", ".zip", context.cacheDir)
        val uri = android.net.Uri.fromFile(zip)
        try {
            store.exportBackup(uri)
            prefs.edit().clear().commit()
            store.importBackup(uri)
            org.junit.Assert.assertEquals(1, prefs.getInt("f01.visit", 0))
            org.junit.Assert.assertEquals("Test cu diacritice",prefs.getString("f01.0.Diagnostic",""))
            org.junit.Assert.assertEquals(setOf("0","1"),prefs.getStringSet("f01.tests",emptySet()))
            fun writeArchive(clinical: String?) {
                java.util.zip.ZipOutputStream(zip.outputStream()).use { output ->
                    output.putNextEntry(java.util.zip.ZipEntry("notes.json"))
                    output.write("[]".toByteArray())
                    output.closeEntry()
                    if (clinical != null) {
                        output.putNextEntry(java.util.zip.ZipEntry("clinical-workbook-v1.json"))
                        output.write(clinical.toByteArray())
                        output.closeEntry()
                    }
                }
            }
            val note = ro.medatlas.study.data.StudyNote(documentId = "test", page = 0, heading = "Test", body = "Păstrează")
            store.saveNotes(listOf(note))
            writeArchive("""{"version":1,"fields":{"f01.visit":99}}""")
            org.junit.Assert.assertTrue(runCatching { store.importBackup(uri) }.isFailure)
            org.junit.Assert.assertEquals(note.id, store.notes().single().id)
            org.junit.Assert.assertEquals(1, prefs.getInt("f01.visit", 0))
            writeArchive(null)
            store.importBackup(uri)
            org.junit.Assert.assertTrue(prefs.getBoolean("f01.opened", false))
        } finally { zip.delete() }
    }

    @Test fun allThirtyAtlasStickersCanBeInsertedAndReopened() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ro.medatlas.study.data.LibraryStore(context)
        val models = store.stickerCatalog().filter { it.first.startsWith("FMA") }
        org.junit.Assert.assertEquals(30,models.size)
        val stickers = models.map { (id,_) ->
            val name = store.builtInSticker(id)
            val bmp = store.stickerBitmap(name)!!
            org.junit.Assert.assertEquals(512,bmp.width)
            org.junit.Assert.assertTrue(bmp.hasAlpha())
            bmp.recycle()
            ro.medatlas.study.data.PageSticker(imageName = name)
        }
        store.saveStickers("device-test",0,stickers)
        org.junit.Assert.assertEquals(stickers,store.stickers("device-test",0))
    }

    @Test fun openLinkedLabAndReturnToSamePatient() {
        ActivityScenario.launch(ClinicalSimulatorActivity::class.java).use {
            val patient = "Nu mai pot duce coșul de rufe pe scări."
            listTo(patient)
            ui.onNodeWithText(patient).performClick()
            tap("Deschide laboratorul")
            ui.onNodeWithText("Feritină și microcitoză").assertExists()
            ui.onNodeWithContentDescription("Înapoi").performClick()
            tap("Consultă atlasul")
            ui.onNodeWithText("Măduvă osoasă", substring=false).assertExists()
            ui.onNodeWithContentDescription("Înapoi").performClick()
            listTo("Fișa clinică")
            ui.onNodeWithText("Fișa clinică").assertExists()
        }
    }

    @Test fun examResumesAndShowsStatistics() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("exam-session-v1",0).edit().clear().commit()
        ActivityScenario.launch(ro.medatlas.study.study.ExamActivity::class.java).use {
            ui.onNodeWithText("Începe un examen").performClick()
            ui.onNodeWithText("Următor").performScrollTo().performClick()
        }
        ActivityScenario.launch(ro.medatlas.study.study.ExamActivity::class.java).use {
            ui.onNodeWithText("Întrebarea 2 din 20",substring=true).assertExists()
            repeat(18) { ui.onNodeWithText("Următor").performScrollTo().performClick() }
            ui.onNodeWithText("Încheie examenul").performScrollTo().performClick()
            ui.onNodeWithText("Rezultat · 0 / 20").assertExists()
            ui.onNodeWithText("Anatomie · 0 / 10").assertExists()
        }
    }

    @Test fun nativeBodyDrawsAndTogglesAnatomicalLayers() {
        ActivityScenario.launch(ro.medatlas.study.anatomy.Anatomy3DActivity::class.java).use {
            val initialDevice = androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
            org.junit.Assert.assertTrue(initialDevice.wait(androidx.test.uiautomator.Until.hasObject(
                androidx.test.uiautomator.By.desc("Corp 3D · 4 straturi vizibile")), 45_000))
            ui.onNodeWithText("Schelet").performClick()
            val device = androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
            org.junit.Assert.assertTrue(device.wait(androidx.test.uiautomator.Until.hasObject(
                androidx.test.uiautomator.By.desc("Corp 3D · 3 straturi vizibile")), 15_000))
        }
    }

}
