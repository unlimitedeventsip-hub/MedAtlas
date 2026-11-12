package ro.medatlas.study

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun UmfMaterialsDialog(onDismiss: () -> Unit, onImport: () -> Unit) {
    val context = LocalContext.current
    var error by remember { mutableStateOf("") }
    val links = listOf(
        "Cursuri pentru toți anii · Google Drive" to "https://drive.google.com/drive/folders/0B0wNSnyeLwgKUUpZOWVsTjJYZ0k?resourcekey=0-l7Veo4QPTLnZt9nVOtnuPw&usp=sharing",
        "Rezidențiat și cărți · linkuri utile" to "https://umfcursuri.weebly.com/linkuri-interesante-si-utile.html",
        "Pagina principală UMF Cursuri" to "https://umfcursuri.weebly.com/"
    )
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Materiale UMF") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                links.forEach { (label, url) ->
                    OutlinedButton(onClick = {
                        error = ""
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: ActivityNotFoundException) {
                            error = "Pentru deschiderea linkului este necesar un browser sau Google Drive."
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                }
                if (error.isNotEmpty()) Text(error)
                Text("Pentru studiu offline, importă un PDF, Word sau PowerPoint din Descărcări ori din Google Drive. Fișierul se adaugă în folderul curent al bibliotecii.")
            }
        }, confirmButton = { TextButton(onClick = onImport) { Text("Importă material") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Închide") } })
}
