package ro.medatlas.study.reference

import ro.medatlas.study.GlassTokens
import ro.medatlas.study.MedAtlasGlassTheme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

/** Device and interpretation training reference, not a diagnostic device. */
class MedicalReferenceActivity : ComponentActivity() {
    data class Topic(
        val title: String, val icon: String, val steps: List<String>,
        val question: String, val answers: List<String>, val correct: Int, val explanation: String
    )
    private val topics by lazy { listOf(
        Topic("ECG · ordinea interpretării", "ecg",
            listOf("1. Verifică identitatea, viteza și calibrarea trasării.",
                "2. Calculează frecvența și regularitatea ritmului.",
                "3. Caută undele P și relația lor cu QRS.",
                "4. Măsoară PR, durata QRS și QT/QTc.",
                "5. Analizează axa, progresia R, modificările ST, undele T și Q patologice.",
                "6. Compară cu ECG-uri anterioare și contextul clinic."),
            "Care este prima verificare înainte de interpretarea ECG?",
            listOf("Doza de tratament", "Identitatea și calibrarea ECG", "Radiografia", "Grupa sanguină"),
            1, "Calibrarea și identificarea sunt esențiale înaintea interpretării ritmului."),
        Topic("Radiografie toracică · checklist", "xray",
            listOf("A — Airway: trahee, bronhii principale și devieri.",
                "B — Breathing: câmpuri pulmonare, simetrie, pleură.",
                "C — Cardiac: contururi cardiomediastinale și vase.",
                "D — Diaphragm: cupole, unghiuri costofrenice, aer subdiafragmatic.",
                "E — Everything else: oase, țesuturi moi, dispozitive.",
                "Verifică întotdeauna proiecția, rotația și gradul de inspirație."),
            "Ce poate altera aparent dimensiunea siluetei cardiace?",
            listOf("Proiecția AP", "Vârsta pacientului singură", "Grupa ABO", "Culoarea imaginii"),
            0, "Proiecția AP poate mări aparent silueta cardiacă; contează și tehnica."),
        Topic("CT · orientare", "ct",
            listOf("Identifică pacientul, regiunea și prezența contrastului.",
                "Verifică planurile axial, coronal și sagital.",
                "Parcurge imaginile sistematic și folosește ferestrele potrivite.",
                "Compară anatomia dreapta–stânga și caută leziuni focale.",
                "Corelează cu indicația clinică și raportul radiologic."),
            "Ce ferestre pot fi utile în examinarea CT toracică?",
            listOf("Doar ferestra osoasă", "Pulmonară și mediastinală", "Doar ferestra craniană", "Niciuna"),
            1, "Ferestrele pulmonară și mediastinală evidențiază structuri diferite."),
        Topic("Monitor pacient · citirea parametrilor", "monitor",
            listOf("HR / FC: frecvența cardiacă și sursa de semnal.",
                "SpO₂: saturația periferică, interpretată cu forma undei și perfuzia.",
                "RR / FR: frecvența respiratorie.",
                "NIBP / TA: tensiune arterială non-invazivă, unități mmHg.",
                "TEMP: temperatura; verifică localizarea senzorului.",
                "Nu ignora artefactele, electrozii slăbiți și discrepanțele față de pacient."),
            "Ce verifici când SpO₂ este neașteptat de mică?",
            listOf("Doar meniul afișajului", "Pacientul, senzorul și calitatea semnalului", "Volumul televizorului", "Nimic"),
            1, "Prioritatea este evaluarea clinică a pacientului și a semnalului."),
        Topic("Instrumentar · categorii", "tools",
            listOf("Stetoscop: auscultație cardiopulmonară și abdominală.",
                "Tensiometru: măsurarea tensiunii arteriale cu manșetă adecvată.",
                "Pulsoximetru: estimarea SpO₂ și pulsului.",
                "Otoscope / oftalmoscop: inspecție ORL și fund de ochi.",
                "Laringoscop: instrument de vizualizare a glotei, utilizat de personal instruit.",
                "Electrocauter și instrumentar chirurgical: necesită formare și protocoale specifice."),
            "Ce instrument folosești pentru auscultație?",
            listOf("Stetoscop", "Otoscope", "Tensiometru", "Glucometru"),
            0, "Stetoscopul este instrumentul de bază pentru auscultație."),
        Topic("Farmacologie · organizarea studiului", "meds",
            listOf("Învață medicamentele pe clase, mecanism de acțiune și indicații.",
                "Separă contraindicațiile, efectele adverse și interacțiunile.",
                "Verifică forma, calea de administrare și ajustările în insuficiența renală/hepatică.",
                "Înțelege diferența dintre tratamentul de urgență, inițial și de întreținere.",
                "Pentru doze reale, folosește surse actualizate și protocoale aprobate."),
            "Ce element trebuie verificat înainte de administrarea unei medicații?",
            listOf("Doar ambalajul", "Indicația, contraindicațiile și identitatea pacientului",
                "Culoarea tabletei", "Doar ora zilei"),
            1, "Siguranța administrării implică verificări multiple, nu o singură proprietate.")
    ) + ro.medatlas.study.simulation.readClinicalTable(this, "laboratory").map { row ->
        Topic(row.getValue("title"), "lab", listOf("Buletin fictiv", row.getValue("report")),
            row.getValue("question"), listOf("a", "b", "c").map(row::getValue),
            row.getValue("correct").toInt(), row.getValue("explanation"))
    } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MedicalReferenceScreen() }
    }
    @Composable
    private fun MedicalReferenceScreen() {
        val target = intent.getStringExtra("topic")
        var selected by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(topics.indexOfFirst { it.title == target }) }
        fun back() { if (target != null || selected < 0) finish() else selected = -1 }
        androidx.activity.compose.BackHandler(selected >= 0) { back() }
        var answer by remember(selected) { mutableIntStateOf(-1) }
        MedAtlasGlassTheme {
            Column(Modifier.fillMaxSize().background(GlassTokens.LightPage).statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { back() }) {
                        Icon(Icons.Rounded.ArrowBack, "Înapoi")
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Laborator medical", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text("ECG • imagistică • aparatură • farmacologie", fontSize = 11.sp, color = Color.Gray)
                    }
                }
                if (selected < 0) {
                    LazyColumn(Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            val uri = androidx.compose.ui.platform.LocalUriHandler.current
                            OutlinedButton(onClick = {
                                uri.openUri("https://www.usmle.org/exam-resources/step-1-materials/step-1-sample-test-questions")
                            }) { Text("Grile oficiale USMLE · online, în engleză") }
                        }
                        items(topics.indices.toList()) { index ->
                            Card(onClick = { selected = index }, modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.MonitorHeart, null)
                                    Spacer(Modifier.width(12.dp))
                                    Text(topics[index].title, modifier = Modifier.weight(1f))
                                    Icon(Icons.Rounded.ChevronRight, null)
                                }
                            }
                        }
                    }
                } else {
                    val topic = topics[selected]
                    LazyColumn(Modifier.fillMaxSize().padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 25.dp)) {
                        item {
                            Text(topic.title, style = MaterialTheme.typography.titleLarge)
                            DiagnosticMedia(topic.icon)
                            if (topic.icon == "monitor") BloodPressureStation()
                            topic.steps.forEach { statement ->
                                Card(Modifier.fillMaxWidth()) {
                                    Text(statement, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
                                }
                            }
                        }
                        item {
                            HorizontalDivider()
                            Text("Verifică ce ai învățat", fontWeight = FontWeight.Bold)
                            Text(topic.question)
                            topic.answers.forEachIndexed { i, label ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = answer == i, onClick = { answer = i })
                                    Text(label, modifier = Modifier.weight(1f))
                                }
                            }
                            if (answer >= 0) {
                                Text(if (answer == topic.correct) "Corect!" else
                                    "Răspuns corect: " + topic.answers[topic.correct],
                                    fontWeight = FontWeight.Bold, color = Color(0xFF38786E))
                                Text(topic.explanation)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Exclusiv material educațional. Pentru decizii clinice folosește protocoale actualizate și supraveghere profesională.",
                                fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}
