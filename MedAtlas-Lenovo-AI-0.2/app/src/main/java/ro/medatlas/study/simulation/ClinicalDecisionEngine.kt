package ro.medatlas.study.simulation

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Explicit scenario branches; these are educational consequences, not a
 * physiological simulation and not patient-specific treatment guidance.
 */
internal object ClinicalDecisionEngine {
    val decisionLabels = listOf(
        listOf(
            "Evaluez semnele de alarmă, explic planul și organizez urmărirea",
            "Mă opresc la prima impresie, fără verificarea semnelor de alarmă",
            "Presupun că problema se rezolvă singură și nu organizez urmărirea"
        ),
        listOf(
            "Reevaluez simptomele, investigațiile și toleranța planului",
            "Continui planul fără să întreb despre răspuns sau reacții adverse",
            "Ignor schimbările clinice și amân reevaluarea"
        ),
        listOf(
            "Compar evoluția, funcția și riscurile; stabilesc un plan documentat",
            "Interpretez un singur rezultat izolat ca vindecare completă",
            "Închid cazul fără evaluarea simptomelor persistente"
        )
    )
    fun outcome(stage: Int, choice: Int, case: ClinicalSimulatorActivity.Case): String {
        if (choice == 0) return when (stage) {
            0 -> "Ai documentat o evaluare inițială și un plan de urmărire. La control: " + case.day7
            1 -> "Ai reevaluat răspunsul și siguranța conduitei. La trei luni: " + case.month3
            else -> "Ai urmărit evoluția și riscurile. Cazul se încheie ca exercițiu; " +
                "continuarea îngrijirii depinde de evaluarea clinică reală."
        }
        val issue = if (choice == 1)
            "O decizie insuficient argumentată poate întârzia diagnosticul sau detectarea efectelor adverse."
            else "Omiterea urmăririi poate ascunde agravarea sau complicațiile."
        return when (stage) {
            0 -> "SCENARIU ALTERNATIV: $issue Pacientul revine pentru reevaluare; " +
                "în locul unui prognostic inventat, verifică simptomele, semnele vitale și investigațiile."
            1 -> "SCENARIU ALTERNATIV: $issue La vizita următoare trebuie reluată anamneza și " +
                "verificată situația clinică. Nu presupune o ameliorare automată."
            else -> "SCENARIU ALTERNATIV: $issue Ai nevoie de un plan de reevaluare și " +
                "documentarea motivului deciziei. Acesta nu este un rezultat clinic prezis."
        }
    }
}

@Composable
internal fun ClinicalDecisionPanel(
    context: Context,
    case: ClinicalSimulatorActivity.Case,
    stage: Int
) {
    val prefs = remember(context) {
        context.getSharedPreferences("clinical-workbook", Context.MODE_PRIVATE)
    }
    val key = case.id + "." + stage + ".decision"
    var selected by remember(key) { mutableIntStateOf(prefs.getInt(key, -1)) }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("Decizie clinică și consecințe", fontWeight = FontWeight.SemiBold)
        Text("Alege conduita. Cazul va afișa consecințe educaționale diferite " +
            "în funcție de decizia salvată.", color = Color.Gray)
        ClinicalDecisionEngine.decisionLabels[stage].forEachIndexed { idx, label ->
            OutlinedButton(
                onClick = {
                    selected = idx
                    prefs.edit().putInt(key, idx).apply()
                }, modifier = Modifier.fillMaxWidth()
            ) {
                RadioButton(selected = selected == idx, onClick = null)
                Spacer(Modifier.width(6.dp))
                Text(label)
            }
        }
        if (selected >= 0) {
            val explanation = ClinicalDecisionEngine.outcome(stage, selected, case)
            Text(if (selected == 0) "Conduită orientată spre siguranță"
                else "Atenție: abordare incompletă / nesigură",
                color = if (selected == 0) Color(0xFF38786E) else Color(0xFFAE6545),
                fontWeight = FontWeight.Medium)
            Text(explanation)
        }
    }
}
