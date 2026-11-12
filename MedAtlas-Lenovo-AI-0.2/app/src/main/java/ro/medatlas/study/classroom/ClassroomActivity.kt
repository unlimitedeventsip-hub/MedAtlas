package ro.medatlas.study.classroom

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

/* Independent Classroom window. Requires Android OAuth app registration in Google Cloud. */
class ClassroomActivity : ComponentActivity() {
    data class ClassroomEntry(val id: String, val courseId: String, val name: String,
        val detail: String, val link: String, val kind: String)
    private val authorization by lazy { Identity.getAuthorizationClient(this) }
    private val scopes = listOf(
        Scope("https://www.googleapis.com/auth/classroom.courses.readonly"),
        Scope("https://www.googleapis.com/auth/classroom.courseworkmaterials.readonly"),
        Scope("https://www.googleapis.com/auth/classroom.coursework.me.readonly")
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ClassroomScreen() }
    }

    @Composable
    private fun ClassroomScreen() {
        var status by remember { mutableStateOf("Conectează Google Classroom pentru a vedea cursurile și temele.") }
        var busy by remember { mutableStateOf(false) }
        var courses by remember { mutableStateOf<List<ClassroomEntry>>(emptyList()) }
        var materials by remember { mutableStateOf<List<ClassroomEntry>>(emptyList()) }
        var selected by remember { mutableStateOf<String?>(null) }
        val navy = Color(0xFF182535)
        fun sync(token: String) {
            busy = true
            status = "Sincronizare în curs..."
            lifecycleScope.launch {
                runCatching { withContext(Dispatchers.IO) { ClassroomApi.load(token) } }
                    .onSuccess {
                        courses = it.first; materials = it.second
                        status = "Sincronizate " + courses.size + " cursuri și " + materials.size + " materiale / teme."
                    }.onFailure { status = "Sincronizare nereușită: " + (it.localizedMessage ?: "Eroare Google") }
                busy = false
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
        MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF38786E))) {
            Column(Modifier.fillMaxSize().background(Color(0xFFFAF8F4))
                .statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { finish() }) { Icon(Icons.Rounded.ArrowBack, "Înapoi") }
                    Column(Modifier.weight(1f)) {
                        Text("Google Classroom", fontWeight = FontWeight.Bold, fontSize = 21.sp, color = navy)
                        Text("Fereastră separată • numai citire", fontSize = 11.sp, color = Color.Gray)
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(status, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { connect() }, enabled = !busy) { Text("Conectează / Actualizează") }
                            OutlinedButton(onClick = {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://classroom.google.com")))
                            }) { Text("Web") }
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
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link)))
                                }) { Text("Deschide material") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private object ClassroomApi {
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
                    if (a != null && a.length() > 0) {
                        val first = a.optJSONObject(0)
                        val drive = first?.optJSONObject("driveFile")?.optJSONObject("driveFile")
                            ?.optString("alternateLink").orEmpty()
                        val link = first?.optJSONObject("link")?.optString("url").orEmpty()
                        url = drive.ifBlank { link }.ifBlank { url }
                    }
                    materials.add(ClassroomActivity.ClassroomEntry(
                        id + "_" + type + "_" + item.optString("id"), id,
                        item.optString("title"), item.optString("description"), url, type))
                }
            }
        }
        return courses to materials
    }
}
