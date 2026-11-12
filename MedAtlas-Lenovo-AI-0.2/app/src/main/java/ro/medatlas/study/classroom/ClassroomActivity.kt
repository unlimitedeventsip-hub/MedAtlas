package ro.medatlas.study.classroom

import ro.medatlas.study.classroom.ClassroomActivity.ClassroomEntry

import ro.medatlas.study.GlassTokens
import ro.medatlas.study.MedAtlasGlassTheme

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.core.content.FileProvider
import ro.medatlas.study.data.LibraryStore
import java.io.File
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Compatibility entry point for existing shortcuts; normal navigation stays in-app. */
class ClassroomActivity : ComponentActivity() {
    data class ClassroomEntry(val id: String, val courseId: String, val name: String,
        val detail: String, val link: String, val kind: String, val driveId: String = "")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ClassroomWorkspace(onBack = { finish() }) }
    }
}

@Composable
internal fun ClassroomWorkspace(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val authorization = remember(context) { Identity.getAuthorizationClient(context) }
    val scopes = ClassroomSync.requiredScopes
    val scope = rememberCoroutineScope()

        val cached = remember { ClassroomSync.cached(context) }
        var status by remember { mutableStateOf(
            if (cached.syncedAt > 0) "Cursurile sunt disponibile offline; actualizarea rulează automat la pornire."
            else "Conectează Google Classroom o singură dată pentru sincronizare automată."
        ) }
        var busy by remember { mutableStateOf(false) }
        val revision by ClassroomSync.revision.collectAsState()
        val backgroundBusy by ClassroomSync.syncing.collectAsState()
        var courses by remember { mutableStateOf(cached.courses) }
        var materials by remember { mutableStateOf(cached.materials) }
        LaunchedEffect(revision) {
            val refreshed = ClassroomSync.cached(context)
            courses = refreshed.courses
            materials = refreshed.materials
            status = if (refreshed.error.isNotBlank())
                "Ultima actualizare a eșuat: " + refreshed.error +
                    if (refreshed.syncedAt > 0) " · cursurile salvate rămân disponibile." else ""
            else if (refreshed.syncedAt > 0)
                "Sincronizate " + courses.size + " cursuri și " + materials.size +
                    " materiale. Disponibile și fără internet."
            else "Conectează Google Classroom o singură dată pentru sincronizare automată."
        }
        var selected by remember { mutableStateOf<String?>(null) }
        val navy = Color(0xFF182535)
        val library = remember { LibraryStore(context) }
        val preferences = remember { context.getSharedPreferences("classroom_sync", android.content.Context.MODE_PRIVATE) }
        var autoImport by remember { mutableStateOf(preferences.getBoolean("auto_import", false)) }
        var currentToken by remember { mutableStateOf<String?>(null) }
        fun importAttachments(token: String, source: List<ClassroomEntry>, classrooms: List<ClassroomEntry>) {
            busy = true
            status = "Import Classroom în biblioteca locală..."
            scope.launch {
                val counts = withContext(Dispatchers.IO) {
                    var copied = 0
                    var existing = 0
                    var failed = 0
                    for (attachment in source.filter { it.driveId.isNotBlank() }.take(25)) {
                        val courseName = classrooms.firstOrNull { it.id == attachment.courseId }
                            ?.name ?: "Classroom"
                        runCatching {
                            val downloaded = ClassroomApi.download(token, attachment.driveId,
                                File(context.cacheDir, "classroom"))
                            try {
                                if (library.documents().any {
                                    it.originalName == downloaded.name && it.course == courseName
                                }) existing++
                                else {
                                    val uri = FileProvider.getUriForFile(
                                        context, context.packageName + ".files", downloaded)
                                    library.import(uri, courseName)
                                    copied++
                                }
                            } finally { downloaded.delete() }
                        }.onFailure { failed++ }
                    }
                    Triple(copied, existing, failed)
                }
                status = "Fișiere importate: " + counts.first + "; deja existente: " +
                    counts.second + "; indisponibile: " + counts.third
                busy = false
            }
        }
        fun sync(token: String) {
            busy = true
            currentToken = token // transient: never persisted
            status = "Actualizez cursurile și materialele..."
            scope.launch {
                try {
                    val updated = ClassroomSync.synchronize(context, token)
                    courses = updated.courses
                    materials = updated.materials
                    status = "Actualizate " + courses.size + " cursuri și " +
                        materials.size + " materiale / teme."
                } catch (error: Exception) {
                    status = "Nu pot actualiza acum: " +
                        (error.localizedMessage ?: "eroare de conexiune") +
                        ". Datele salvate rămân disponibile."
                } finally {
                    busy = false
                }
            }
        }
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { response ->
            if (response.resultCode == Activity.RESULT_OK && response.data != null) {
                runCatching { authorization.getAuthorizationResultFromIntent(response.data!!).accessToken }
                    .onSuccess { if (!it.isNullOrBlank()) sync(it) else status = "Token Google absent" }
                    .onFailure { status = it.localizedMessage ?: "Autorizare eșuată" }
            } else status = "Conectarea a fost anulată."
        }
        fun connect() {
            busy = true
            val request = AuthorizationRequest.builder().setRequestedScopes(scopes).build()
            authorization.authorize(request).addOnSuccessListener { result ->
                busy = false
                if (result.hasResolution()) {
                    val pending = result.pendingIntent
                    if (pending != null) launcher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                    else status = "Autorizarea Google nu poate afișa dialogul."
                } else result.accessToken?.let { sync(it) }
                    ?: run { status = "Nu a fost returnat tokenul." }
            }.addOnFailureListener {
                busy = false
                status = "OAuth: " + (it.localizedMessage ?: "Eroare") +
                    ". Verifică în Google Cloud OAuth, pachetul Android și SHA-1."
            }
        }
        // App launch has already requested a silent refresh. No surprise OAuth popup here.
        MedAtlasGlassTheme {
            Column(Modifier.fillMaxSize().background(GlassTokens.LightPage)
                .statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Înapoi") }
                    Column(Modifier.weight(1f)) {
                        Text("Google Classroom", fontWeight = FontWeight.Bold, fontSize = 21.sp, color = navy)
                        Text("Sincronizare automată · acces offline", fontSize = 11.sp, color = Color.Gray)
                    }
                }
                if (busy || backgroundBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(status, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = autoImport, onCheckedChange = {
                                autoImport = it
                                preferences.edit().putBoolean("auto_import", it).apply()
                            })
                            Text("Importă automat și fișierele PDF, Word și PowerPoint",
                                modifier = Modifier.weight(1f), fontSize = 11.sp)
                        }
                        Text("Cursurile și temele se actualizează automat la pornirea MedAtlas, " +
                            "după prima conectare. Opțiunea de mai sus descarcă și fișierele în " +
                            "bibliotecă (maximum 25 per sincronizare, 30 MB fiecare).",
                            fontSize = 10.sp, color = Color.Gray)
                        if (!autoImport && currentToken != null &&
                            materials.any { it.driveId.isNotBlank() }) {
                            OutlinedButton(onClick = {
                                importAttachments(currentToken!!, materials, courses)
                            }, enabled = !busy) { Text("Importă fișiere în MedAtlas") }
                        }
                        Button(onClick = { connect() }, enabled = !busy && !backgroundBusy) {
                            Icon(if (cached.connected || courses.isNotEmpty())
                                Icons.Rounded.Refresh else Icons.Rounded.AccountCircle, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (cached.connected || courses.isNotEmpty()) "Actualizează acum"
                                else "Conectează Google Classroom")
                        }
                    }
                }
                if (selected != null) TextButton(onClick = { selected = null }) {
                    Icon(Icons.Rounded.ArrowBack, null); Text("Toate cursurile")
                }
                LazyColumn(Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selected == null) items(courses, key = { it.id }) { course ->
                        Card(Modifier.fillMaxWidth().clickable { selected = course.id }) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.MenuBook, null)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(course.name, fontWeight = FontWeight.SemiBold)
                                    Text(course.detail, fontSize = 11.sp, color = Color.Gray)
                                }
                                Icon(Icons.Rounded.ChevronRight, null)
                            }
                        }
                    } else items(materials.filter { it.courseId == selected }, key = { it.id }) { item ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text(item.kind, color = Color(0xFF38786E), fontSize = 11.sp)
                                Text(item.name, fontWeight = FontWeight.SemiBold)
                                if (item.detail.isNotBlank()) Text(item.detail, fontSize = 12.sp)
                                if (item.link.isNotBlank()) TextButton(onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link)))
                                }) { Text("Deschide material") }
                            }
                        }
                    }
                }
            }
        }
    }

internal object ClassroomApi {
    private fun get(token: String, url: String): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.setRequestProperty("Authorization", "Bearer " + token)
            c.setRequestProperty("Accept", "application/json")
            c.connectTimeout = 15000
            c.readTimeout = 25000
            val status = c.responseCode
            val body = (if (status in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) error("Google HTTP " + status + ": " + body.take(180))
            return JSONObject(body)
        } finally { c.disconnect() }
    }
    private fun pages(token: String, base: String, field: String): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        var next = ""
        repeat(10) {
            val url = base + (if (base.contains("?")) "&" else "?") + "pageSize=100" +
                if (next.isBlank()) "" else "&pageToken=" + URLEncoder.encode(next, "UTF-8")
            val result = get(token, url)
            val array = result.optJSONArray(field)
            if (array != null) for (i in 0 until array.length()) out.add(array.getJSONObject(i))
            next = result.optString("nextPageToken")
            if (next.isBlank()) return out
        }
        return out
    }
    fun download(token: String, driveId: String, directory: File): File {
        require(driveId.matches(Regex("[A-Za-z0-9_-]{6,200}"))) { "Identificator Drive invalid" }
        val metadata = get(token, "https://www.googleapis.com/drive/v3/files/" + driveId +
            "?fields=id,name,mimeType,size")
        val mime = metadata.optString("mimeType")
        val ext: String
        val url: String
        when (mime) {
            "application/pdf" -> {
                ext = "pdf"
                url = "https://www.googleapis.com/drive/v3/files/" + driveId + "?alt=media"
            }
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> {
                ext = "docx"
                url = "https://www.googleapis.com/drive/v3/files/" + driveId + "?alt=media"
            }
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> {
                ext = "pptx"
                url = "https://www.googleapis.com/drive/v3/files/" + driveId + "?alt=media"
            }
            "application/vnd.google-apps.document" -> {
                ext = "docx"
                url = "https://www.googleapis.com/drive/v3/files/" + driveId +
                    "/export?mimeType=application%2Fvnd.openxmlformats-officedocument.wordprocessingml.document"
            }
            "application/vnd.google-apps.presentation" -> {
                ext = "pptx"
                url = "https://www.googleapis.com/drive/v3/files/" + driveId +
                    "/export?mimeType=application%2Fvnd.openxmlformats-officedocument.presentationml.presentation"
            }
            else -> error("Tipul fișierului nu poate fi importat: " + mime)
        }
        require(metadata.optLong("size", 0) <= 30L * 1024 * 1024) { "Fișier prea mare" }
        val safeName = metadata.optString("name")
            .replace(Regex("[^A-Za-z0-9._ -]"), "_").take(120)
        val filename = if (safeName.lowercase().endsWith("." + ext)) safeName
            else safeName.substringBeforeLast(".").ifBlank { driveId } + "." + ext
        directory.mkdirs()
        val file = File(directory, filename)
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.setRequestProperty("Authorization", "Bearer " + token)
            connection.connectTimeout = 15000
            connection.readTimeout = 45000
            if (connection.responseCode !in 200..299) error("Drive HTTP " + connection.responseCode)
            var total = 0L
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= 30L * 1024 * 1024) { "Limita de 30 MB depășită" }
                        output.write(buffer, 0, n)
                    }
                }
            }
            return file
        } catch (ex: Exception) { file.delete(); throw ex }
        finally { connection.disconnect() }
    }

    fun load(token: String): Pair<List<ClassroomActivity.ClassroomEntry>,
                                   List<ClassroomActivity.ClassroomEntry>> {
        val courses = pages(token,
            "https://classroom.googleapis.com/v1/courses?courseStates=ACTIVE", "courses")
            .map { item ->
                ClassroomActivity.ClassroomEntry(item.optString("id"), "",
                    item.optString("name"), item.optString("section"), "", "Curs")
            }
        val materials = mutableListOf<ClassroomActivity.ClassroomEntry>()
        for (course in courses.take(50)) {
            val id = course.id
            val lessons = runCatching {
                pages(token, "https://classroom.googleapis.com/v1/courses/" + id +
                    "/courseWorkMaterials", "courseWorkMaterial")
            }.getOrDefault(emptyList())
            val assignments = runCatching {
                pages(token, "https://classroom.googleapis.com/v1/courses/" + id +
                    "/courseWork", "courseWork")
            }.getOrDefault(emptyList())
            for ((type, group) in listOf("Material" to lessons, "Temă" to assignments)) {
                for (item in group) {
                    val a = item.optJSONArray("materials")
                    var url = item.optString("alternateLink")
                    var driveId = ""
                    if (a != null && a.length() > 0) {
                        val first = a.optJSONObject(0)
                        driveId = first?.optJSONObject("driveFile")?.optJSONObject("driveFile")?.optString("id").orEmpty()
                        val drive = first?.optJSONObject("driveFile")?.optJSONObject("driveFile")
                            ?.optString("alternateLink").orEmpty()
                        val link = first?.optJSONObject("link")?.optString("url").orEmpty()
                        url = drive.ifBlank { link }.ifBlank { url }
                    }
                    materials.add(ClassroomActivity.ClassroomEntry(
                        id + "_" + type + "_" + item.optString("id"), id,
                        item.optString("title"), item.optString("description"), url, type, driveId))
                }
            }
        }
        return courses to materials
    }
}
