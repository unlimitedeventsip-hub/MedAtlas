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

    @Test fun resumesIndividualPatientAtFollowUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("clinical-workbook", Context.MODE_PRIVATE).edit().clear().commit()
        val patient = "Nu mai pot duce coșul de rufe pe scări."
        ActivityScenario.launch(ClinicalSimulatorActivity::class.java).use {
            listTo(patient)
            ui.onNodeWithText(patient).performClick()
            listTo("Verificare prin grilă")
            ui.onNodeWithText("Diagnostic").performTextInput("Anemie cu microcitoză")
            ui.onNodeWithText("Argumente și alternative").performTextInput("Feritină scăzută")
            ui.onNodeWithText("Tratament și monitorizare").performTextInput("Verific pierderile și urmăresc hemograma")
            ui.onNodeWithText("Anemie feriprivă").performClick()
            ui.onNodeWithText("Verifică diagnosticul și tratamentul").performClick()
            ui.onNodeWithText("Continuă · Ziua 7").performClick()
            ui.onNodeWithText("Ziua 7").assertExists()
            ui.onNodeWithText("Diagnostic · Anemie cu microcitoză").assertExists()
            ui.onNodeWithText("Diagnostic").performTextInput("În urmărire")
            ui.onNodeWithText("Argumente și alternative").performTextInput("Verific toleranța")
            ui.onNodeWithText("Tratament și monitorizare").performTextInput("Reevaluare")
            ui.onNodeWithText("Continuă · Luna 3").performClick()
        }
        ActivityScenario.launch(ClinicalSimulatorActivity::class.java).use {
            listTo(patient)
            ui.onNodeWithText(patient).performClick()
            listTo("Luna 3")
            ui.onNodeWithText("Diagnostic · În urmărire").assertExists()
            ui.onNodeWithText("Ecografia ginecologică", substring = true).assertExists()
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
