package ro.medatlas.study

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ro.medatlas.study.data.Stroke
import java.util.concurrent.TimeUnit

internal fun recognizeLine(strokes: List<Stroke>, width: Int, height: Int): String {
    val identifier = requireNotNull(DigitalInkRecognitionModelIdentifier.fromLanguageTag("ro-RO"))
    val model = DigitalInkRecognitionModel.builder(identifier).build()
    val manager = RemoteModelManager.getInstance()
    if (!Tasks.await(manager.isModelDownloaded(model), 15, TimeUnit.SECONDS))
        Tasks.await(manager.download(model, DownloadConditions.Builder().build()), 90, TimeUnit.SECONDS)
    return DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build()).use {
        Tasks.await(it.recognize(createDigitalInk(strokes, width, height)), 25, TimeUnit.SECONDS)
            .candidates.firstOrNull()?.text.orEmpty()
    }
}

/** Pure vector-to-ML-Kit conversion: testable offline without downloading any ML model. */
internal fun createDigitalInk(strokes: List<Stroke>, width: Int, height: Int): Ink {
    require(width > 0 && height > 0) { "Invalid canvas size" }
    val ink = Ink.builder()
    strokes.filter { it.points.isNotEmpty() }.forEach { stroke ->
        val line = Ink.Stroke.builder()
        stroke.points.forEach { point ->
            val x = point.x * width
            val y = point.y * height
            if (x.isFinite() && y.isFinite()) line.addPoint(Ink.Point.create(x, y))
        }
        ink.addStroke(line.build())
    }
    return ink.build()
}

@Composable
internal fun TranscriptionDialog(strokes: List<Stroke>, paper: Bitmap, onAdd: (String) -> Unit, close: () -> Unit) {
    var proposal by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = close, title = { Text("Transcriere în română") }, text = {
        Column {
            Text(if (strokes.isEmpty()) "Recunoaște textul din imaginea veche a paginii."
                else "Pentru un singur rând, selectează-l întâi cu lasoul. La prima folosire se descarcă modelul românesc.")
            TextButton(enabled = !busy, onClick = {
                busy = true; message = ""
                scope.launch {
                    try {
                        proposal = withContext(Dispatchers.IO) {
                            if (strokes.isNotEmpty()) recognizeLine(strokes, paper.width, paper.height)
                            else TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).use {
                                Tasks.await(it.process(InputImage.fromBitmap(paper, 0)), 25, TimeUnit.SECONDS).text
                            }
                        }
                        if (proposal.isBlank()) message = "Nu am identificat text. Poți completa transcrierea manual."
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { message = "Transcriere indisponibilă. Verifică descărcarea modelului sau completează textul manual." }
                    finally { busy = false }
                }
            }) { Text(if (busy) "Se procesează…" else "Recunoaște textul") }
            OutlinedTextField(proposal, { proposal = it }, enabled = !busy, label = { Text("Verifică transcrierea") })
            if (message.isNotEmpty()) Text(message)
        }
    }, confirmButton = { Button(enabled = !busy && proposal.isNotBlank(), onClick = { onAdd(proposal.trim()) }) { Text("Adaugă în pagină") } },
        dismissButton = { TextButton(onClick = close) { Text("Închide") } })
}
