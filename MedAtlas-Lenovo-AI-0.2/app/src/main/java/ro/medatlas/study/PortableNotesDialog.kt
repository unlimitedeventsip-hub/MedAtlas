package ro.medatlas.study

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ro.medatlas.study.data.LibraryStore
import ro.medatlas.study.data.PortableNotes

/** Companion transfer: text only, no destructive merge or silent cloud calls. */
@Composable
internal fun PortableNotesDialog(
    store: LibraryStore,
    onDismiss: () -> Unit,
    onImported: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { destination ->
        if (destination != null) scope.launch {
            busy = true
            error = null
            try {
                val count = withContext(Dispatchers.IO) {
                    PortableNotes.export(context, store, destination)
                }
                result = "$count notițe exportate. Transferul conține doar textul."
            } catch (exception: Exception) {
                error = exception.localizedMessage ?: "Nu am putut exporta notițele."
            } finally { busy = false }
        }
    }
    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { source ->
        if (source != null) scope.launch {
            busy = true
            error = null
            try {
                val details = withContext(Dispatchers.IO) {
                    PortableNotes.import(context, store, source)
                }
                result = "${details.imported} notițe adăugate, " +
                    "${details.copiedConflicts} copii pentru conflicte, " +
                    "${details.ignored} deja existente."
                onImported()
            } catch (exception: Exception) {
                error = exception.localizedMessage ?: "Fișierul JSON nu a putut fi importat."
                Toast.makeText(context, "Import nereușit", Toast.LENGTH_SHORT).show()
            } finally { busy = false }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Notițe · telefon și laptop") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Transferă notițele text între MedAtlas Android și Companion " +
                        "pe laptop. Nu este sincronizare automată.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(
                    enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    onClick = { exporter.launch("MedAtlas-notes.json") }
                ) {
                    Icon(Icons.Rounded.FileDownload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Exportă notițele JSON")
                }
                OutlinedButton(
                    enabled = !busy, modifier = Modifier.fillMaxWidth(),
                    onClick = { importer.launch(arrayOf("application/json", "text/plain")) }
                ) {
                    Icon(Icons.Rounded.FileUpload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Importă notițele JSON")
                }
                Text(
                    "Dacă o notiță diferă de cea existentă, se creează o copie. " +
                        "Desenele și stickerele se transferă numai prin backupul complet.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (result.isNotBlank()) Text(result, color = MaterialTheme.colorScheme.primary)
                if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Gata") }
        }
    )
}
