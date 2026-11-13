package ro.medatlas.study.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Cross-device interchange in the same StudyNote and Stroke JSON schemas used
 * by MedAtlas Android. Vector pen strokes and text transfer together.
 * Embedded pictures, stickering and legacy raster paper require a full ZIP backup.
 *
 * Import never overwrites different existing notes, regardless of timestamps.
 * Conflicts are copied, with a new UUID, so no local edit is discarded.
 */
internal object PortableNotes {
    private const val MAX_IMPORT_BYTES = 12 * 1024 * 1024
    private const val MAX_NOTES = 10_000
    private const val MAX_BODY = 300_000
    private const val MAX_STROKES_PER_NOTE = 3_000
    private const val MAX_POINTS_PER_STROKE = 10_000
    private const val MAX_POINTS_PER_NOTE = 150_000

    private fun inkId(id: String): String =
        "notebook-" + UUID.nameUUIDFromBytes(id.toByteArray(Charsets.UTF_8))

    data class ImportResult(val imported: Int, val copiedConflicts: Int, val ignored: Int)

    fun export(context: Context, store: LibraryStore, uri: Uri): Int {
        val notes = store.notes()
        val payload = JSONArray().apply {
            notes.forEach { note ->
                // A drawing's path is only meaningful inside the originating
                // tablet's private storage, so don't export a broken reference.
                val entry = note.copy(handwritingFile = "").json()
                val strokes = store.ink(inkId(note.id), 0)
                entry.put("strokes", JSONArray().apply {
                    strokes.forEach { put(it.json()) }
                })
                put(entry)
            }
        }.toString(2)
        val stream = context.contentResolver.openOutputStream(uri)
            ?: error("Nu pot crea fișierul de transfer.")
        stream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        return notes.size
    }

    private fun readLimited(context: Context, uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri)
            ?: error("Nu pot deschide fișierul JSON.")
        val bytes = stream.use { input ->
            val buffer = ByteArray(16 * 1024)
            val output = ByteArrayOutputStream()
            var total = 0
            while (true) {
                val size = input.read(buffer)
                if (size < 0) break
                total += size
                require(total <= MAX_IMPORT_BYTES) {
                    "Fișierul cu notițe depășește limita de 12 MB."
                }
                output.write(buffer, 0, size)
            }
            output.toByteArray()
        }
        return String(bytes, Charsets.UTF_8)
    }

    fun import(context: Context, store: LibraryStore, uri: Uri): ImportResult {
        val source = JSONArray(readLimited(context, uri))
        require(source.length() <= MAX_NOTES) {
            "Fișierul conține prea multe notițe."
        }
        // Parse and validate the entire payload BEFORE touching local storage.
        val parsed = (0 until source.length()).map { index ->
            val json = source.getJSONObject(index)
            require(json.has("body") && json.opt("body") is String) {
                "Notița ${index + 1} are text invalid."
            }
            val body = json.getString("body")
            require(body.length <= MAX_BODY) {
                "Notița ${index + 1} este prea lungă."
            }
            val originalId = json.optString("id")
            val id = if (runCatching { SafeStorageNames.id(originalId) }.isSuccess)
                originalId else UUID.randomUUID().toString()
            val heading = json.optString("heading").take(180)
                .ifBlank { "Notiță importată" }
            val importedStrokes = json.optJSONArray("strokes")?.let { list ->
                require(list.length() <= MAX_STROKES_PER_NOTE) {
                    "Notița ${index + 1} are prea multe trasee."
                }
                (0 until list.length()).map { n ->
                    val row = list.getJSONObject(n)
                    require(row.optJSONArray("points")?.length() ?: 0 <= MAX_POINTS_PER_STROKE) {
                        "Traseu manuscris prea lung."
                    }
                    Stroke.parse(row).also { stroke ->
                        require(stroke.width.isFinite() && stroke.width in .1f..100f &&
                            stroke.points.all { point ->
                                point.x.isFinite() && point.y.isFinite() &&
                                point.pressure.isFinite() &&
                                point.x in -.01f..1.01f && point.y in -.01f..1.01f &&
                                point.pressure in 0f..4f
                            }) { "Date de scriere manuală invalide." }
                    }
                }
            }.orEmpty()
            require(importedStrokes.sumOf { it.points.size } <= MAX_POINTS_PER_NOTE) {
                "Notița ${index + 1} are prea multe puncte manuscrise."
            }
            val candidate = StudyNote(
                id = id,
                documentId = "medical-notebook",
                page = 0,
                heading = heading,
                body = body,
                course = json.optString("course", "General").take(80),
                paper = json.optString("paper", "BLANK")
                    .takeIf { it in setOf("BLANK", "LINES", "GRID", "DOTS", "MUSIC") }
                    ?: "BLANK",
                darkPaper = json.optBoolean("darkPaper"),
                handwritingFile = "",
                createdAt = json.optLong("createdAt").takeIf { it > 0 }
                    ?: System.currentTimeMillis(),
                updatedAt = json.optLong("updatedAt").takeIf { it > 0 }
                    ?: System.currentTimeMillis()
            )
            candidate to importedStrokes
        }

        val local = store.notes().toMutableList()
        val existing = local.associateBy { it.id }.toMutableMap()
        var imported = 0
        var conflicts = 0
        var ignored = 0
        val newInk = mutableListOf<Pair<String, List<Stroke>>>()
        parsed.forEach { (candidate, strokes) ->
            val previous = existing[candidate.id]
            if (previous == null) {
                local += candidate
                existing[candidate.id] = candidate
                newInk += candidate.id to strokes
                imported++
            } else if (previous.body == candidate.body &&
                previous.heading == candidate.heading &&
                (strokes.isEmpty() || store.ink(inkId(candidate.id), 0) == strokes)) {
                ignored++
            } else {
                val duplicate = candidate.copy(
                    id = UUID.randomUUID().toString(),
                    heading = (candidate.heading.take(154) + " (copie importată)"),
                    updatedAt = System.currentTimeMillis()
                )
                local += duplicate
                existing[duplicate.id] = duplicate
                newInk += duplicate.id to strokes
                imported++
                conflicts++
            }
        }
        if (imported > 0) {
            store.saveNotes(local)
            newInk.forEach { (id, strokes) ->
                if (strokes.isNotEmpty()) store.saveInk(inkId(id), 0, strokes)
            }
        }
        return ImportResult(imported, conflicts, ignored)
    }
}
