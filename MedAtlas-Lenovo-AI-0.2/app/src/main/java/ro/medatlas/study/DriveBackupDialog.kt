package ro.medatlas.study

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ro.medatlas.study.data.LibraryStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manual cloud workflow through the Android system document provider.
 * Google Drive appears in the picker when its app/provider is configured.
 * No Google account tokens, API keys, or background sync permissions are used.
 */
@Composable
internal fun DriveBackupDialog(
    store: LibraryStore,
    onDismiss: () -> Unit,
    onImported: () -> Unit,
    onRestored: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var finished by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = true; error = null; finished = null
            try {
                withContext(Dispatchers.IO) { store.exportBackup(uri) }
                finished = "Arhiva MedAtlas a fost salvată în locația selectată."
            } catch (e: Exception) {
                error = "Backup nereușit: " + (e.localizedMessage ?: "Nu pot salva arhiva.")
            } finally { busy = false }
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true; error = null; finished = null
            try {
                withContext(Dispatchers.IO) { store.importBackup(uri) }
                finished = "Arhiva a fost restaurată. Biblioteca a fost reîncărcată."
                onRestored()
            } catch (e: Exception) {
                error = "Restaurare nereușită: " + (e.localizedMessage ?: "Arhiva nu poate fi citită.")
            } finally { busy = false }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Google Drive · Documente și backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "MedAtlas folosește selectorul Android. Alege Google Drive în selectorul de fișiere " +
                        "dacă aplicația Drive este instalată și contul este conectat.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Poți importa cursuri din Drive sau salva o arhivă completă cu PDF-uri, " +
                        "notițe, desene și organizarea folderelor. Aceasta este o copie manuală, " +
                        "nu sincronizare automată.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    onClick = { onImported(); onDismiss() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.FileOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Importă documente din Drive")
                }
                OutlinedButton(
                    onClick = {
                        val filename = "MedAtlas-backup-" +
                            SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date()) + ".zip"
                        exporter.launch(filename)
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.CloudUpload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Salvează backup în Drive")
                }
                OutlinedButton(
                    onClick = { confirmRestore = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Rounded.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Restaurează backup din Drive")
                }
                if (busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Se transferă arhiva. Nu închide MedAtlas.",
                        style = MaterialTheme.typography.bodySmall)
                }
                if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
                if (finished != null) Text(finished.orEmpty(),
                    style = MaterialTheme.typography.bodySmall)
                Text(
                    "Fișierele salvate în Google Drive respectă spațiul și setările contului tău. " +
                        "MedAtlas nu primește acces automat la întregul cont Google.",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Închide") }
        }
    )

    if (confirmRestore) AlertDialog(
        onDismissRequest = { confirmRestore = false },
        icon = { Icon(Icons.Rounded.Security, null) },
        title = { Text("Restaurezi biblioteca MedAtlas?") },
        text = {
            Text("Fișierele din arhiva aleasă pot suprascrie fișierele locale cu același nume. " +
                "Salvează mai întâi un backup al bibliotecii actuale dacă ai modificări importante.")
        },
        confirmButton = {
            Button(onClick = {
                confirmRestore = false
                importer.launch(arrayOf("application/zip", "application/octet-stream"))
            }) { Text("Alege arhiva") }
        },
        dismissButton = {
            TextButton(onClick = { confirmRestore = false }) { Text("Anulează") }
        }
    )
}
