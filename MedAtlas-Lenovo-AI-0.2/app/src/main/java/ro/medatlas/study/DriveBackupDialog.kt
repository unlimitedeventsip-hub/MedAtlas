package ro.medatlas.study

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    var autoBackupEnabled by remember { mutableStateOf(DriveAutoBackup.enabled(context)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var finished by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }

    val autoFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching { DriveAutoBackup.enable(context, uri) }
                .onSuccess {
                    autoBackupEnabled = true
                    error = null
                    finished = "Backup automat activat. Prima arhivă este programată acum, apoi zilnic."
                }
                .onFailure {
                    error = "Nu am putut activa backupul: " +
                        (it.localizedMessage ?: "Folderul nu acceptă acces persistent.")
                }
        }
    }

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
                        "notițe, desene și organizarea folderelor. Poți folosi backup manual " +
                        "sau programa copii automate într-un folder ales de tine.",
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
                HorizontalDivider()
                Text("Backup automat (la fiecare 24 de ore)",
                    style = MaterialTheme.typography.titleSmall)
                Text(
                    if (autoBackupEnabled)
                        "Activ. MedAtlas salvează arhive noi și păstrează ultimele 8 copii " +
                            "în folderul ales. Nu modifică fișierele din bibliotecă."
                    else
                        "Alege un folder din Google Drive (sau alt provider Android compatibil). " +
                            "MedAtlas va salva acolo câte o arhivă nouă în fiecare zi.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (!autoBackupEnabled) {
                    OutlinedButton(
                        onClick = { autoFolderPicker.launch(null) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Activează backup automat")
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            runCatching { DriveAutoBackup.backupNow(context) }
                                .onSuccess {
                                    finished = "O copie nouă este programată pentru salvare."
                                    error = null
                                }
                                .onFailure { error = it.localizedMessage ?: "Cerere nereușită." }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("Salvează o copie acum") }
                    TextButton(
                        onClick = {
                            DriveAutoBackup.disable(context)
                            autoBackupEnabled = false
                            finished = "Backupul automat a fost oprit. Copiile existente rămân în Drive."
                        },
                        enabled = !busy
                    ) { Text("Oprește backup automat") }
                    val last = DriveAutoBackup.lastSuccess(context)
                    if (last > 0L) Text(
                        "Ultima copie: " + SimpleDateFormat(
                            "dd.MM.yyyy HH:mm", Locale.getDefault()
                        ).format(Date(last)) + " · " + DriveAutoBackup.lastArchive(context),
                        style = MaterialTheme.typography.labelSmall
                    )
                    val previousError = DriveAutoBackup.lastError(context)
                    if (previousError.isNotBlank()) Text(
                        "Ultima eroare: " + previousError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
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
                        "MedAtlas are acces doar la fișierele și folderul pe care le alegi. " +
                        "Backupul automat nu este sincronizare în două sensuri.",
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
