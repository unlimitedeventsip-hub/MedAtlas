package ro.medatlas.study.simulation

import ro.medatlas.study.GlassTokens
import ro.medatlas.study.MedAtlasGlassTheme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Exam built from original, locally stored question banks, not copied from
 * copyrighted exams. Questions are illustrative teaching exercises.
 */
class ExamSimulatorActivity : ComponentActivity() {
    data class Question(
        val id: String, val category: String, val stem: String,
        val choices: List<String>, val answer: Int, val explanation: String
    )

    private val bank: List<Question> by lazy {
        val labs = readClinicalTable(this, "laboratory").map { row ->
            Question("lab." + row.getValue("id"), "Laborator",
                row.getValue("title") + "\n" + row.getValue("report") +
                    "\n\n" + row.getValue("question"),
                listOf(row.getValue("a"), row.getValue("b"), row.getValue("c")),
                row.getValue("correct").toInt(), row.getValue("explanation"))
        }
        val followups = readClinicalTable(this, "reassessment").map { row ->
            Question("follow." + row.getValue("id"), "Cazuri clinice",
                row.getValue("question"),
                listOf(row.getValue("a"), row.getValue("b"), row.getValue("c")),
                row.getValue("correct").toInt(), row.getValue("explanation"))
        }
        require((labs + followups).map { it.id }.distinct().size == (labs + followups).size)
        require((labs + followups).all { it.answer in 0..2 })
        labs + followups
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ExamScreen() }
    }

    @Composable
    private fun ExamScreen() {
        var mode by rememberSaveable { mutableStateOf("Toate") }
        var count by rememberSaveable { mutableIntStateOf(20) }
        var examStarted by rememberSaveable { mutableStateOf(false) }
        var finished by rememberSaveable { mutableStateOf(false) }
        var seed by rememberSaveable { mutableIntStateOf(1) }
        var current by rememberSaveable { mutableIntStateOf(0) }
        val pool = remember(mode) {
            bank.filter { mode == "Toate" || it.category == mode }
        }
        val questions = remember(mode, count, seed) {
            pool.shuffled(kotlin.random.Random(seed)).take(count.coerceAtMost(pool.size))
        }
        val responses = remember(mode, count, seed) {
            mutableStateMapOf<String, Int>()
        }
        val correctCount = questions.count { responses[it.id] == it.answer }
        val dark = Color(0xFF182535)
        BackHandler(examStarted) {
            if (finished) {
                examStarted = false
                finished = false
            } else examStarted = false
        }

        MedAtlasGlassTheme {
            Column(Modifier.fillMaxSize().background(GlassTokens.LightPage)
                .statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(9.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        if (examStarted) { examStarted = false; finished = false }
                        else finish()
                    }) { Icon(Icons.Rounded.ArrowBack, "Înapoi") }
                    Column(Modifier.weight(1f)) {
                        Text("Simulare examen", fontSize = 22.sp,
                            fontWeight = FontWeight.Bold, color = dark)
                        Text("Grile originale • offline • punctaj și explicații",
                            fontSize = 11.sp, color = Color.Gray)
                    }
                    Icon(Icons.Rounded.School, null, tint = Color(0xFFD58EA2))
                }
                if (!examStarted) {
                    Column(Modifier.fillMaxSize().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Alege materia", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Toate", "Laborator", "Cazuri clinice").forEach { option ->
                                FilterChip(selected = mode == option, onClick = { mode = option },
                                    label = { Text(option) })
                            }
                        }
                        Text("Întrebări: " + pool.size + " disponibile", color = Color.Gray)
                        Text("Durata este liberă. Fiecare întrebare valorează 1 punct.")
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf(10, 20, 30).forEach { value ->
                                FilterChip(selected = count == value,
                                    onClick = { count = value },
                                    label = { Text(value.toString()) })
                            }
                        }
                        Button(onClick = {
                            seed += 1; current = 0; finished = false
                            examStarted = true
                        }, modifier = Modifier.fillMaxWidth(),
                            enabled = pool.isNotEmpty()) {
                            Text("Începe examenul")
                        }
                        Text("Grilele sunt exerciții educaționale locale, nu subiecte "
                            + "oficiale UMFCD sau USMLE.", fontSize = 11.sp, color = Color.Gray)
                    }
                } else if (!finished) {
                    val question = questions[current]
                    Column(Modifier.fillMaxSize().padding(17.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Întrebarea " + (current + 1) + " / " + questions.size,
                            fontWeight = FontWeight.SemiBold)
                        LinearProgressIndicator(progress = {
                            (current + 1).toFloat() / questions.size.coerceAtLeast(1)
                        }, modifier = Modifier.fillMaxWidth())
                        Text(question.category, color = Color(0xFF38786E), fontSize = 12.sp)
                        Column(Modifier.weight(1f)) {
                            Text(question.stem, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                            Spacer(Modifier.height(10.dp))
                            question.choices.forEachIndexed { index, choice ->
                                Card(onClick = { responses[question.id] = index },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = responses[question.id] == index,
                                            onClick = { responses[question.id] = index })
                                        Spacer(Modifier.width(5.dp))
                                        Text(choice, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { current-- },
                                enabled = current > 0) { Text("Înapoi") }
                            Spacer(Modifier.weight(1f))
                            if (current < questions.lastIndex) {
                                Button(onClick = { current++ }) { Text("Următoarea") }
                            } else Button(onClick = { finished = true }) {
                                Text("Corectează examenul")
                            }
                        }
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                        contentPadding = PaddingValues(bottom = 30.dp)) {
                        item {
                            Text("Rezultatul examenului", fontSize = 22.sp,
                                fontWeight = FontWeight.Bold)
                            Text(correctCount.toString() + " / " + questions.size +
                                " puncte • " + (correctCount * 100 / questions.size.coerceAtLeast(1)) +
                                "%", fontSize = 20.sp, color = Color(0xFF38786E))
                            Text("Nerăspunse: " +
                                questions.count { !responses.containsKey(it.id) }, color = Color.Gray)
                            Button(onClick = { examStarted = false; finished = false },
                                modifier = Modifier.fillMaxWidth()) {
                                Text("Alt examen")
                            }
                        }
                        items(questions) { question ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(13.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(question.stem, fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp)
                                    val choice = responses[question.id]
                                    val right = choice == question.answer
                                    Text(if (right) "Corect" else "Greșit sau fără răspuns",
                                        color = if (right) Color(0xFF38786E) else Color(0xFFB15B5B))
                                    if (choice != null)
                                        Text("Ai ales: " + question.choices[choice], fontSize = 12.sp)
                                    Text("Răspuns: " + question.choices[question.answer],
                                        fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    Text(question.explanation, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
