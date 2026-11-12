package ro.medatlas.study.simulation

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** Educational fictional scenarios. Does not replace supervised medical training. */
class ClinicalSimulatorActivity : ComponentActivity() {
    private var speaker: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speaker = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) speaker?.language = Locale("ro", "RO")
        }
        setContent { ClinicalSimulationUI() }
    }
    override fun onDestroy() { speaker?.stop(); speaker?.shutdown(); super.onDestroy() }

    data class Case(
        val id: String, val title: String, val difficulty: String, val patient: String,
        val complaint: String, val vitals: String, val history: String,
        val questions: List<Pair<String, String>>, val tests: List<Pair<String, String>>,
        val diagnoses: List<String>, val correct: Int, val therapy: String,
        val explanation: String
    )

    private val cases = listOf(
        Case("asthma", "Dispnee episodică", "Începător", "Femeie, 24 ani",
            "Mă sufoc uneori și îmi șuieră pieptul, mai ales noaptea.",
            "TA 118/75 • Puls 98 • SpO₂ 96% • FR 22/min",
            "Antecedente de alergii, simptome variabile.",
            listOf("Când apar simptomele?" to "Noaptea, la efort sau la contactul cu pisicile.",
                "Ai fumat?" to "Nu.", "Ai folosit inhalatoare?" to "Am primit odată un bronhodilatator și m-am simțit mai bine."),
            listOf("Spirometrie cu test bronhodilatator" to "Obstrucție cu reversibilitate semnificativă.",
                "Auscultație" to "Wheezing expirator difuz."),
            listOf("Astm bronșic", "BPOC", "Pneumotorax", "Fibroză pulmonară"), 0,
            "Evaluează severitatea și controlul; discută tratamentul inhalator antiinflamator conform ghidurilor actuale și tehnica inhalatorului.",
            "Variabilitatea simptomelor și reversibilitatea obstrucției susțin astmul."),
        Case("pneumonia", "Febră și tuse", "Începător", "Bărbat, 57 ani",
            "Am febră și mă doare în piept când respir adânc.",
            "T 39°C • Puls 112 • FR 26/min • SpO₂ 92%",
            "Debut în urmă cu 4 zile, tuse productivă.",
            listOf("Cum este sputa?" to "Galben-verzuie.", "Ai durere pleuritică?" to "Da, în dreapta.",
                "Ai alte boli?" to "Hipertensiune arterială."),
            listOf("Radiografie toracică" to "Opacitate de condensare în lobul inferior drept.",
                "Hemoleucogramă / CRP" to "Markeri inflamatori crescuți."),
            listOf("Astm", "Pneumonie comunitară", "Embolie pulmonară", "Edem pulmonar"), 1,
            "Evaluează gravitatea, indicația de spitalizare, oxigenoterapia după necesități și antibioterapia conform ghidurilor și contextului local.",
            "Febra, tusea productivă, durerea pleuritică și condensarea radiologică sugerează pneumonie."),
        Case("acs", "Durere toracică", "Avansat", "Bărbat, 62 ani",
            "Mă apasă pieptul de o jumătate de oră și transpir.",
            "TA 95/60 • Puls 105 • SpO₂ 95%",
            "Diabet, fumător, dislipidemie.",
            listOf("Durerea iradiază?" to "În brațul stâng și mandibulă.",
                "Când a început?" to "Acum 30 de minute, în repaus.",
                "Ai greață?" to "Da, foarte puternică."),
            listOf("ECG cu 12 derivații" to "Supradenivelare ST în DII, DIII, aVF.",
                "Troponină" to "Creștere dinamică a troponinei."),
            listOf("Angină stabilă", "Pericardită", "STEMI inferior", "Reflux gastroesofagian"), 2,
            "Activează circuitul de urgență și reperfuzie, cu evaluare hemodinamică și tratament conform protocolului local pentru sindrom coronarian acut.",
            "Durerea tipică și supradenivelarea ST în derivațiile inferioare indică STEMI inferior."),
        Case("appendix", "Durere abdominală", "Intermediar", "Femeie, 19 ani",
            "Mă doare burta, întâi în jurul buricului și acum în dreapta jos.",
            "T 37.9°C • Puls 102 • TA 110/70",
            "Greață, inapetență, fără intervenții chirurgicale anterioare.",
            listOf("Unde doare acum?" to "În fosa iliacă dreaptă.",
                "Ai vărsat?" to "O dată.",
                "Există posibilitate de sarcină?" to "Trebuie verificat."),
            listOf("Examen abdominal" to "Sensibilitate locală cu semne de iritație peritoneală.",
                "Ecografie abdominală" to "Apendice necompresibil, dilatat."),
            listOf("Pancreatită", "Apendicită acută", "Gastrită", "Colică renală"), 1,
            "Solicită consult chirurgical, evaluare de urgență, investigații potrivite și management conform protocolului local; exclude urgențele ginecologice.",
            "Migrarea durerii periumbilicale către fosa iliacă dreaptă este sugestivă, dar diagnosticul diferențial rămâne esențial."),
        Case("pe", "Dispnee bruscă", "Avansat", "Femeie, 44 ani",
            "Dintr-odată nu mai pot respira normal și mă doare pieptul.",
            "Puls 125 • FR 30 • SpO₂ 89% • TA 105/70",
            "Imobilizare după operație ortopedică recentă.",
            listOf("Ai avut o intervenție recentă?" to "Da, la genunchi.",
                "Durerea e la inspir?" to "Da.",
                "Ai un picior umflat?" to "Gamba stângă este umflată."),
            listOf("Evaluare probabilitate clinică" to "Probabilitate mare pentru tromboembolism venos.",
                "Angio-CT pulmonar" to "Defecte de umplere în arterele pulmonare."),
            listOf("Embolie pulmonară", "Laringită", "Astm ușor", "Pleurită virală"), 0,
            "Evaluează imediat stabilitatea hemodinamică, necesarul de oxigen și strategia antitrombotică sau de reperfuzie conform ghidurilor.",
            "Debutul brusc, hipoxemia și factorul de risc postoperator susțin embolia pulmonară."),
        Case("stroke", "Deficit neurologic acut", "Avansat", "Bărbat, 70 ani",
            "Nu pot ridica bine mâna dreaptă și vorbesc greu.",
            "TA 175/95 • Puls 92 • Glicemie 118 mg/dL",
            "Debut brusc observat acum 40 de minute.",
            listOf("Când ai fost văzut ultima dată normal?" to "Acum aproximativ 40 de minute.",
                "Ai luat anticoagulante?" to "Nu știu, medicul trebuie să verifice.",
                "A apărut brusc?" to "Da."),
            listOf("Examen FAST/NIHSS" to "Asimetrie facială, pareză braț drept și disfazie.",
                "CT cerebral fără contrast" to "Fără hemoragie intracraniană evidentă."),
            listOf("Hipoglicemie", "AVC ischemic acut", "Migrenă", "Paralizie periferică"), 1,
            "Activează codul AVC, documentează ultima oră cunoscută fără simptome și evaluează eligibilitatea pentru terapii de reperfuzie conform protocolului.",
            "Deficitul neurologic focal brusc impune evaluare de urgență pentru AVC; CT exclude inițial hemoragia majoră.")
    )

    @Composable
    private fun ClinicalSimulationUI() {
        var current by remember { mutableStateOf<Case?>(null) }
        var revealed by remember(current?.id) { mutableStateOf(setOf<Int>()) }
        var investigations by remember(current?.id) { mutableStateOf(setOf<Int>()) }
        var diagnosis by remember(current?.id) { mutableIntStateOf(-1) }
        var therapyShown by remember(current?.id) { mutableStateOf(false) }
        val dark = Color(0xFF182535)
        val tint = Color(0xFFD58EA2)
        MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF38786E))) {
            Column(Modifier.fillMaxSize().background(Color(0xFFFAF8F4)).statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (current == null) finish() else current = null }) {
                        Icon(Icons.Rounded.ArrowBack, "Înapoi")
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Spital Virtual", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = dark)
                        Text("Pacienți fictivi · simulare educațională", fontSize = 11.sp, color = Color.Gray)
                    }
                    Icon(Icons.Rounded.MedicalServices, null, tint = tint)
                }
                if (current == null) {
                    LazyColumn(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        item { Text("Alege cazul. Obține anamneza, investighează și stabilește diagnosticul.",
                            color = Color.Gray, fontSize = 12.sp) }
                        items(cases, key = { it.id }) { c ->
                            Card(Modifier.fillMaxWidth().clickable { current = c }) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Person, null)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(c.title, fontWeight = FontWeight.SemiBold)
                                        Text(c.difficulty + " • " + c.patient, fontSize = 11.sp)
                                    }
                                    Icon(Icons.Rounded.ChevronRight, null)
                                }
                            }
                        }
                    }
                } else {
                    val c = current!!
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 30.dp)) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEFF2))) {
                                Column(Modifier.fillMaxWidth().padding(15.dp)) {
                                    Text(c.patient, fontWeight = FontWeight.Bold)
                                    Text("Motivul prezentării:", fontSize = 11.sp, color = Color.Gray)
                                    Text(c.complaint)
                                    TextButton(onClick = {
                                        speaker?.speak(c.complaint, TextToSpeech.QUEUE_FLUSH, null, "patient")
                                    }) { Icon(Icons.Rounded.VolumeUp, null); Text("Ascultă pacientul") }
                                }
                            }
                        }
                        item {
                            Text("Fișa clinică", fontWeight = FontWeight.Bold)
                            Text(c.vitals, fontSize = 13.sp)
                            Text(c.history, fontSize = 13.sp)
                            HorizontalDivider()
                            Text("1. Anamneză prin mesaje", fontWeight = FontWeight.Bold)
                        }
                        items(c.questions.indices.toList()) { index ->
                            val pair = c.questions[index]
                            OutlinedButton(onClick = { revealed = revealed + index },
                                modifier = Modifier.fillMaxWidth()) { Text("Întreabă: " + pair.first) }
                            if (index in revealed) {
                                Text("Pacient: „" + pair.second + "”", modifier = Modifier.padding(start = 15.dp),
                                    color = Color(0xFF38786E))
                                TextButton(onClick = {
                                    speaker?.speak(pair.second, TextToSpeech.QUEUE_ADD, null, "answer" + index)
                                }) { Icon(Icons.Rounded.VolumeUp, null); Text("Audio") }
                            }
                        }
                        item { Text("2. Investigații", fontWeight = FontWeight.Bold) }
                        items(c.tests.indices.toList()) { index ->
                            val item = c.tests[index]
                            OutlinedButton(onClick = { investigations = investigations + index },
                                modifier = Modifier.fillMaxWidth()) { Text(item.first) }
                            if (index in investigations) Text(item.second, color = Color(0xFF38786E),
                                modifier = Modifier.padding(start = 15.dp))
                        }
                        item {
                            HorizontalDivider()
                            Text("3. Diagnostic probabil", fontWeight = FontWeight.Bold)
                            c.diagnoses.forEachIndexed { i, option ->
                                Row(Modifier.fillMaxWidth().clickable { diagnosis = i }.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = diagnosis == i, onClick = { diagnosis = i })
                                    Text(option)
                                }
                            }
                            Button(onClick = { therapyShown = true }, enabled = diagnosis >= 0,
                                modifier = Modifier.fillMaxWidth()) { Text("Verifică diagnosticul și tratamentul") }
                            if (therapyShown) {
                                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(if (diagnosis == c.correct) "Diagnostic corect!" else
                                            "Revezi cazul. Diagnosticul de referință: " + c.diagnoses[c.correct],
                                            fontWeight = FontWeight.Bold, color = Color(0xFF38786E))
                                        Text(c.explanation)
                                        Text("Conduită educațională:", fontWeight = FontWeight.SemiBold)
                                        Text(c.therapy)
                                        Text("Simulare fictivă; nu folosi aceste instrucțiuni ca protocol pentru un pacient real.",
                                            fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
