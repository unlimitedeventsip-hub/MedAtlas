package ro.medatlas.study.classroom

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ro.medatlas.study.data.LibraryStore
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One Classroom integration shared by app launch and the Classroom page.
 * Cached metadata is displayed offline immediately; OAuth tokens are NEVER stored.
 * Startup asks Google only for an already-granted permission. If an interactive
 * consent dialog is required, the user authorizes once from the Classroom page.
 */
internal object ClassroomSync {
    private const val PREF = "classroom_sync"
    private const val COURSES = "cached_courses"
    private const val MATERIALS = "cached_materials"
    private const val LAST_SYNC = "last_sync_ms"
    private const val ERROR = "last_sync_error"
    private const val CONNECTED = "connected"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startupInProgress = AtomicBoolean(false)
    private val networkMutex = Mutex()

    val requiredScopes: List<Scope> = listOf(
        Scope("https://www.googleapis.com/auth/classroom.courses.readonly"),
        Scope("https://www.googleapis.com/auth/classroom.courseworkmaterials.readonly"),
        Scope("https://www.googleapis.com/auth/classroom.coursework.me.readonly"),
        Scope("https://www.googleapis.com/auth/drive.readonly")
    )

    data class Snapshot(
        val courses: List<ClassroomActivity.ClassroomEntry> = emptyList(),
        val materials: List<ClassroomActivity.ClassroomEntry> = emptyList(),
        val syncedAt: Long = 0,
        val error: String = "",
        val connected: Boolean = false
    )

    private val _revision = MutableStateFlow(0)
    val revision = _revision.asStateFlow()
    private val _syncing = MutableStateFlow(false)
    val syncing = _syncing.asStateFlow()

    fun cached(context: Context): Snapshot {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        fun unpack(key: String): List<ClassroomActivity.ClassroomEntry> = runCatching {
            val arr = JSONArray(prefs.getString(key, "[]") ?: "[]")
            (0 until arr.length()).mapNotNull { index ->
                val entry = arr.optJSONObject(index) ?: return@mapNotNull null
                ClassroomActivity.ClassroomEntry(
                    id = entry.optString("id"), courseId = entry.optString("courseId"),
                    name = entry.optString("name"), detail = entry.optString("detail"),
                    link = entry.optString("link"), kind = entry.optString("kind"),
                    driveId = entry.optString("driveId")
                )
            }
        }.getOrDefault(emptyList())
        return Snapshot(
            courses = unpack(COURSES),
            materials = unpack(MATERIALS),
            syncedAt = prefs.getLong(LAST_SYNC, 0L),
            error = prefs.getString(ERROR, "").orEmpty(),
            connected = prefs.getBoolean(CONNECTED, false)
        )
    }

    private fun save(context: Context,
        courses: List<ClassroomActivity.ClassroomEntry>,
        materials: List<ClassroomActivity.ClassroomEntry>
    ) {
        fun pack(values: List<ClassroomActivity.ClassroomEntry>): String {
            val array = JSONArray()
            values.forEach { item ->
                array.put(JSONObject().apply {
                    put("id", item.id)
                    put("courseId", item.courseId)
                    put("name", item.name.take(250))
                    put("detail", item.detail.take(1800))
                    put("link", item.link.take(1500))
                    put("kind", item.kind)
                    put("driveId", item.driveId)
                })
            }
            return array.toString()
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(COURSES, pack(courses.take(100)))
            .putString(MATERIALS, pack(materials.take(1500)))
            .putLong(LAST_SYNC, System.currentTimeMillis())
            .putBoolean(CONNECTED, true)
            .remove(ERROR)
            .apply()
        _revision.value = _revision.value + 1
    }

    private fun noteError(context: Context, error: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(ERROR, error.take(240)).apply()
        _revision.value = _revision.value + 1
    }

    /**
     * Call once from MainActivity.onCreate. Does not show OAuth UI and does not
     * slow down the library. No automatic connection is possible before consent.
     */
    fun refreshOnAppOpen(context: Context) {
        val app = context.applicationContext
        if (!startupInProgress.compareAndSet(false, true)) return
        _syncing.value = true
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(requiredScopes).build()
        runCatching { Identity.getAuthorizationClient(app).authorize(request) }
            .onSuccess { task ->
                task.addOnSuccessListener { authorization ->
                    val token = if (authorization.hasResolution()) null else authorization.accessToken
                    if (token.isNullOrBlank()) {
                        // The user has never connected (or must re-consent):
                        // do not interrupt a study session with an OAuth dialog.
                        _syncing.value = false
                        startupInProgress.set(false)
                    } else scope.launch {
                        try { synchronize(app, token) }
                        catch (e: Exception) {
                            noteError(app, e.localizedMessage ?: "Nu s-au putut actualiza cursurile.")
                        } finally {
                            startupInProgress.set(false)
                        }
                    }
                }.addOnFailureListener { error ->
                    if (cached(app).connected) {
                        noteError(app, error.localizedMessage ?: "Sesiunea Google trebuie reconectată.")
                    }
                    _syncing.value = false
                    startupInProgress.set(false)
                }
            }.onFailure {
                if (cached(app).connected)
                    noteError(app, it.localizedMessage ?: "Google Classroom indisponibil.")
                _syncing.value = false
                startupInProgress.set(false)
            }
    }

    /** Used both by the startup sync and the explicit refresh button. */
    suspend fun synchronize(context: Context, token: String): Snapshot =
        networkMutex.withLock {
            _syncing.value = true
            try {
                val (courses, materials) = withContext(Dispatchers.IO) {
                    ClassroomApi.load(token)
                }
                save(context, courses, materials)
                // Attachment downloads require the existing explicit opt-in.
                if (context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                        .getBoolean("auto_import", false)) {
                    withContext(Dispatchers.IO) {
                        importFiles(context, token, materials, courses)
                    }
                    // A second notification ensures downloaded documents appear
                    // in the open MedAtlas library, without an app restart.
                    _revision.value = _revision.value + 1
                }
                cached(context)
            } catch (e: Exception) {
                noteError(context, e.localizedMessage ?: "Sincronizarea a eșuat.")
                throw e
            } finally {
                _syncing.value = false
            }
        }

    /** Avoid replacing handwritten notes or annotated originals on an automatic import. */
    private fun importFiles(context: Context, token: String,
        materials: List<ClassroomActivity.ClassroomEntry>,
        courses: List<ClassroomActivity.ClassroomEntry>
    ) {
        val library = LibraryStore(context)
        val destination = File(context.cacheDir, "classroom")
        for (item in materials.filter { it.driveId.isNotBlank() }.take(25)) {
            runCatching {
                val courseName = courses.firstOrNull { it.id == item.courseId }
                    ?.name.orEmpty().ifBlank { "Classroom" }
                val downloaded = ClassroomApi.download(token, item.driveId, destination)
                try {
                    val exists = library.documents().any {
                        it.originalName == downloaded.name && it.course == courseName
                    }
                    if (!exists) {
                        val uri: Uri = FileProvider.getUriForFile(
                            context, context.packageName + ".files", downloaded)
                        library.import(uri, courseName)
                    }
                } finally { downloaded.delete() }
            }
        }
    }
}
