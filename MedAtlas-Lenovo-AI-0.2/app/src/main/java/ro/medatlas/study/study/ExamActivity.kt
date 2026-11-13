package ro.medatlas.study.study

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONObject
import ro.medatlas.study.simulation.readClinicalTable
import kotlin.random.Random

class ExamActivity : ComponentActivity() {
    private data class Question(val section: String, val prompt: String, val answers: List<String>, val solution: String, val explanation: String)
    private val prefs by lazy { getSharedPreferences("exam-session-v1", MODE_PRIVATE) }

    private fun paper(seed: Int): List<Question> {
        val rng = Random(seed)
        val anatomy = assets.open("atlas-details.tsv").bufferedReader().use { it.readLines().drop(1).map { line -> line.split('\t') } }
        val anatomyItems = anatomy.map { organ ->
            val options = (anatomy.filter { it != organ }.shuffled(rng).take(3).map { it[0] } + organ[0]).shuffled(rng)
            Question("Anatomie", "Identifică structura după funcție. " + organ[2], options, organ[0], organ[1])
        }
        fun questions(file: String, section: String) = readClinicalTable(this, file).map {
            val choices = listOf(it.getValue("a"), it.getValue("b"), it.getValue("c"))
            Question(section, it.getValue("question").let { q -> it["report"]?.let { report -> report + "\n\n" + q } ?: q },
                choices.shuffled(rng), choices[it.getValue("correct").toInt()], it.getValue("explanation"))
        }
        return (anatomyItems.shuffled(rng).take(10) + questions("laboratory", "Laborator").shuffled(rng).take(5) +
            questions("reassessment", "Clinică").shuffled(rng).take(5)).shuffled(rng)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Exam() } }
    }

    @Composable private fun Exam() {
        var seed by remember { mutableStateOf(if (prefs.contains("seed")) prefs.getInt("seed", 0) else null) }
        val questions = remember(seed) { seed?.let(::paper).orEmpty() }
        var index by remember(seed) { mutableIntStateOf(prefs.getInt("index", 0)) }
        var finished by remember(seed) { mutableStateOf(prefs.getBoolean("finished", false)) }
        var answers by remember(seed) { mutableStateOf(JSONObject(prefs.getString("answers", "{}")!!)) }
        var left by remember(seed) { mutableLongStateOf(0L) }
        fun finishPaper() { finished = true; prefs.edit().putBoolean("finished", true).apply() }
        LaunchedEffect(seed, finished) {
            if (seed != null && !finished) while (true) {
                left = (prefs.getLong("deadline", 0L) - System.currentTimeMillis()).coerceAtLeast(0L) / 1000
                if (left == 0L) { finishPaper(); break }
                delay(500)
            }
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { finish() }) { Text("Înapoi la studiu") }
            Text("Examen de antrenament", style = MaterialTheme.typography.headlineSmall)
            if (seed == null || finished) {
                if (finished && questions.isNotEmpty()) {
                    val correct = questions.indices.filter { answers.optString(it.toString()) == questions[it].solution }
                    Text("Rezultat · ${correct.size} / ${questions.size}", style = MaterialTheme.typography.titleLarge)
                    questions.groupBy { it.section }.forEach { (section, items) ->
                        Text(section + " · " + correct.count { questions[it].section == section } + " / " + items.size)
                    }
                }
                Text("20 de întrebări · 20 de minute · anatomie, laborator și clinică")
                Button(onClick = {
                    val newSeed = Random.nextInt()
                    prefs.edit().clear().putInt("seed", newSeed)
                        .putLong("deadline", System.currentTimeMillis() + 20 * 60_000).apply()
                    seed = newSeed
                }) { Text("Începe un examen") }
                if (finished) questions.forEachIndexed { i, q ->
                    val response = answers.optString(i.toString(), "Fără răspuns")
                    if (response != q.solution) {
                        Text(q.prompt, style = MaterialTheme.typography.titleSmall)
                        Text("Ai ales · " + response)
                        Text("Răspuns · " + q.solution + "\n" + q.explanation)
                    }
                }
            } else {
                val q = questions[index]
                Text("Întrebarea ${index + 1} din 20 · ${left / 60} min ${left % 60} s")
                Text(q.prompt, style = MaterialTheme.typography.titleMedium)
                q.answers.forEach { option ->
                    FilterChip(selected = answers.optString(index.toString()) == option, onClick = {
                        answers = JSONObject(answers.toString()).put(index.toString(), option)
                        prefs.edit().putString("answers", answers.toString()).apply()
                    }, label = { Text(option) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = index > 0, onClick = { index--; prefs.edit().putInt("index", index).apply() }) { Text("Anterior") }
                    Button(onClick = {
                        if (index == questions.lastIndex) finishPaper()
                        else { index++; prefs.edit().putInt("index", index).apply() }
                    }) { Text(if (index == questions.lastIndex) "Încheie examenul" else "Următor") }
                }
            }
        }
    }
}
