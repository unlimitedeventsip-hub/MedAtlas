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
}
