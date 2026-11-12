package ro.medatlas.study.simulation

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal fun readClinicalTable(context: Context, file: String): List<Map<String, String>> {
    val rows = context.assets.open("clinical/$file.tsv").bufferedReader(Charsets.UTF_8).use { it.readLines() }
    val headers = rows.first().split('\t')
    return rows.drop(1).filter { it.isNotBlank() }.map { line ->
        val cells = line.split('\t')
        require(cells.size == headers.size && cells.none { it.isBlank() }) { "Fișă incompletă în $file" }
        headers.zip(cells).toMap()
    }
}

internal fun loadPatientCases(context: Context): List<ClinicalSimulatorActivity.Case> {
    val profiles = readClinicalTable(context, "profiles").associateBy { it.getValue("id") }
    return readClinicalTable(context, "patients").map { patient ->
        val profile = profiles.getValue(patient.getValue("profile"))
        val diagnosis = profile.getValue("diagnosis")
        val options = mutableListOf(profile.getValue("alternative1"), profile.getValue("alternative2"))
        val correct = Math.floorMod(patient.getValue("id").hashCode(), 3)
        options.add(correct, diagnosis)
        ClinicalSimulatorActivity.Case(
            patient.getValue("id"), patient.getValue("complaint"), "Ambulatoriu", patient.getValue("patient"),
            patient.getValue("complaint"), "Consultație programată", patient.getValue("history"),
            listOf(profile.getValue("question") to profile.getValue("answer")),
            listOf(profile.getValue("test") to profile.getValue("result"), profile.getValue("secondTest") to profile.getValue("secondResult")),
            options, correct, profile.getValue("plan"),
            listOf(profile.getValue("result"), profile.getValue("secondResult")).joinToString(" "),
            profile.getValue("day7"), profile.getValue("month3") + "\n\n" + patient.getValue("evolution"), profile.getValue("id")
        )
    }
}

@Composable
internal fun VisitWorkbook(context: Context, case: ClinicalSimulatorActivity.Case) {
    val prefs = remember(context) { context.getSharedPreferences("clinical-workbook", Context.MODE_PRIVATE) }
    val review = remember(case.profile) { readClinicalTable(context, "reassessment").first { it["id"] == case.profile } }
    val days = listOf("Consultație inițială", "Ziua 7", "Luna 3")
    var stage by remember(case.id) { mutableIntStateOf(prefs.getInt(case.id + ".visit", 0)) }
    val visitKey = case.id + "." + stage
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(days[stage], fontWeight = FontWeight.Bold)
        if (stage > 0) {
            Text(if (stage == 1) case.day7 else case.month3)
            val priorKey = case.id + "." + (stage - 1)
            Text("La vizita precedentă ai scris")
            listOf("Diagnostic", "Argumente și alternative", "Tratament și monitorizare").forEach { field ->
                Text(field + " · " + prefs.getString(priorKey + "." + field, "—"))
            }
        }
        val fields = listOf("Diagnostic", "Argumente și alternative", "Tratament și monitorizare")
        val notes = remember(visitKey) { mutableStateListOf<String>().apply {
            addAll(fields.map { prefs.getString(visitKey + "." + it, "").orEmpty() })
        } }
        fields.forEachIndexed { index, field ->
            OutlinedTextField(value = notes[index], onValueChange = {
                notes[index] = it
                prefs.edit().putString(visitKey + "." + field, it).apply()
            }, modifier = Modifier.fillMaxWidth(), label = { Text(field) }, minLines = 2)
        }
        Text("Fișa se salvează automat pe această tabletă.")
        if (stage > 0) OutlinedButton(onClick = {
            stage = stage - 1
            prefs.edit().putInt(case.id + ".visit", stage).apply()
        }) { Text("Recitește vizita precedentă") }
        if (stage < 2) Button(enabled = notes.all { it.isNotBlank() }, onClick = {
            stage = stage + 1
            prefs.edit().putInt(case.id + ".visit", stage).apply()
        }) { Text("Continuă · " + days[stage + 1]) }
        else if (notes.all { it.isNotBlank() }) {
            Text("Cele trei vizite sunt documentate.", fontWeight = FontWeight.Bold)
            Text(review.getValue("question"))
            val key = case.id + ".reassessment"
            var selected by remember(case.id) { mutableIntStateOf(prefs.getInt(key, -1)) }
            val options = listOf("a", "b", "c").map(review::getValue)
            options.forEachIndexed { index, answer ->
                OutlinedButton(onClick = {
                    selected = index
                    prefs.edit().putInt(key, index).apply()
                }) { Text(answer) }
            }
            if (selected >= 0) {
                val correct = review.getValue("correct").toInt()
                Text(if (selected == correct) "Corect!" else "Răspuns de referință · " + options[correct])
                Text(review.getValue("explanation"))
            }
        }
    }
}
