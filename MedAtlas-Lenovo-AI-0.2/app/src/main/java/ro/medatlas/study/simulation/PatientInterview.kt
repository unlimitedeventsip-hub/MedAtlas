package ro.medatlas.study.simulation

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ro.medatlas.study.ai.AiClient

@Composable internal fun PatientInterview(case: ClinicalSimulatorActivity.Case, speak: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember(case.id) { mutableStateOf(false) }
    var draft by remember(case.id) { mutableStateOf("") }
    var transcript by remember(case.id) { mutableStateOf(listOf<String>()) }
    var busy by remember(case.id) { mutableStateOf(false) }
    var error by remember(case.id) { mutableStateOf("") }
    val dictate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { answer -> draft = answer }
        }
    }
    OutlinedButton(onClick = { expanded = !expanded }) { Text("Dialog și feedback Nadia AI") }
    if (!expanded) return
    Column {
        if (!AiClient.configured(context)) {
            Text("Conectează serviciul Nadia AI în Setări pentru dialog liber. Întrebările de anamneză ale cazului funcționează offline.")
            return@Column
        }
        Text("Trimite doar întrebări despre acest pacient fictiv. Dictarea folosește serviciul vocal al tabletei.")
        transcript.takeLast(6).forEach { Text(it) }
        OutlinedTextField(draft,{draft=it},Modifier.fillMaxWidth(),label={Text("Întreabă pacientul")})
        TextButton(onClick = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ro-RO")
            runCatching { dictate.launch(intent) }.onFailure { error="Dictarea este indisponibilă. Poți introduce întrebarea de la tastatură." }
        }) { Text("Dictează") }
        fun submit(feedback: Boolean) {
            val question = draft.trim()
            val facts = listOf(case.patient,case.complaint,case.history,case.vitals,
                case.questions.joinToString("\n") { it.first + " — " + it.second }).joinToString("\n")
            val prefs = context.getSharedPreferences("clinical-workbook",0)
            val stage = prefs.getInt(case.id+".visit",0)
            val notes = listOf("Diagnostic","Argumente și alternative","Tratament și monitorizare").joinToString("\n") {
                it + " · " + prefs.getString(case.id+"."+stage+"."+it,"").orEmpty()
            }
            val input = if (feedback) facts+"\nVizita "+stage+"\n"+notes+"\nBarem: "+case.explanation+" "+case.therapy
                else facts+"\n"+transcript.takeLast(6).joinToString("\n")+"\nÎntrebare: "+question
            busy=true
            error=""
            scope.launch {
                try {
                    val reply = AiClient.ask(context, if (feedback) "case_feedback" else "patient", input,null,"Spital virtual")
                    transcript = (transcript + listOf(if (feedback) "Evaluarea fișei" else "Student · "+question, reply)).takeLast(12)
                    if (!feedback) { draft=""; speak(reply) }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error=e.localizedMessage ?: "Serviciul nu a răspuns." }
                finally { busy=false }
            }
        }
        Button(enabled=!busy && draft.isNotBlank(),onClick={submit(false)}) { Text("Trimite întrebarea") }
        OutlinedButton(enabled=!busy,onClick={submit(true)}) { Text("Trimite fișa pentru feedback") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error.isNotEmpty()) Text(error)
    }
}
