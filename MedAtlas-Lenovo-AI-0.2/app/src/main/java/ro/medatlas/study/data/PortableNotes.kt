package ro.medatlas.study.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
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

    /**
     * Use a stable ID for an imported conflicting version. Importing the same
     * laptop file again cannot create endless "(copie importată)" duplicates.
     * Changes to text, paper or vector ink produce a DIFFERENT conflict ID.
     */
    private fun conflictId(candidate: StudyNote, strokes: List<Stroke>, hasInk: Boolean): String {
        val text = buildString {
            append(candidate.id).append('\u0000')
            append(candidate.heading).append('\u0000')
            append(candidate.body).append('\u0000')
            append(candidate.course).append('\u0000')
            append(candidate.paper).append('\u0000')
            append(candidate.darkPaper).append('\u0000')
            append(hasInk).append('\u0000')
            strokes.forEach { append(it.json().toString()).append('\u0000') }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
        return "conflict-" + digest.take(16).joinToString("") { "%02x".format(it) }
    }

    private fun sameFields(a: StudyNote, b: StudyNote): Boolean =
        a.heading == b.heading && a.body == b.body &&
            a.course == b.course && a.paper == b.paper &&
            a.darkPaper == b.darkPaper

    /** Serialize and check size BEFORE opening a destination file. */
    fun export(context: Context, store: LibraryStore, uri: Uri): Int {
        val notes = store.notes()
        require(notes.size <= MAX_NOTES) { "Sunt prea multe notițe pentru transferul JSON." }
        val payload = JSONArray().apply {
            notes.forEach { note ->
                // Raster pictures use the full ZIP backup; vector ink is portable.
                val entry = note.copy(handwritingFile = "").json()
                val strokes = store.ink(inkId(note.id), 0)
                entry.put("strokes", JSONArray().apply {
                    strokes.forEach { put(it.json()) }
                })
                put(entry)
            }
        }.toString().toByteArray(Charsets.UTF_8)
        require(payload.size <= MAX_IMPORT_BYTES) {
            "Notițele depășesc 12 MB. Folosește backupul ZIP complet."
        }
        val stream = context.contentResolver.openOutputStream(uri)
            ?: error("Nu pot crea fișierul de transfer.")
        stream.use { it.write(payload) }
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
            val inkProvided = json.has("strokes")
            require(!inkProvided || json.optJSONArray("strokes") != null) {
                "Notița ${index + 1} are trasee manuscrise invalide."
            }
            val importedStrokes = json.optJSONArray("strokes")?.let { list ->
                require(list.length() <= MAX_STROKES_PER_NOTE) {
                    "Notița ${index + 1} are prea multe trasee."
                }
                (0 until list.length()).map { n ->
                    val row = list.getJSONObject(n)
                    require((row.optJSONArray("points")?.length() ?: 0) <= MAX_POINTS_PER_STROKE) {
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
            Triple(candidate, importedStrokes, inkProvided)
        }

        val local = store.notes().toMutableList()
        val existing = local.associateBy { it.id }.toMutableMap()
        var imported = 0
        var conflicts = 0
        var ignored = 0
        val newInk = mutableListOf<Pair<String, List<Stroke>>>()
        parsed.forEach { (candidate, strokes, hasInk) ->
            val previous = existing[candidate.id]
            val identical = previous != null &&
                sameFields(previous, candidate) &&
                (!hasInk || store.ink(inkId(candidate.id), 0) == strokes)
            if (previous == null) {
                local += candidate
                existing[candidate.id] = candidate
                newInk += candidate.id to strokes
                imported++
            } else if (identical) {
                ignored++
            } else {
                val stableId = conflictId(candidate, strokes, hasInk)
                val existingCopy = existing[stableId]
                val sameCopy = existingCopy != null &&
                    sameFields(
                        existingCopy,
                        candidate.copy(heading = (
                            candidate.heading.take(154) + " (copie importată)"
                        ))
                    ) &&
                    (!hasInk || store.ink(inkId(stableId), 0) == strokes)
                if (sameCopy) {
                    ignored++
                } else {
                    // A collision is highly unlikely, but must never overwrite
                    // an unrelated local note.
                    val copyId = if (existingCopy == null) stableId
                        else UUID.randomUUID().toString()
                    val duplicate = candidate.copy(
                        id = copyId,
                        heading = candidate.heading.take(154) + " (copie importată)",
                        updatedAt = System.currentTimeMillis()
                    )
                    local += duplicate
                    existing[duplicate.id] = duplicate
                    newInk += duplicate.id to strokes
                    imported++
                    conflicts++
                }
            }
        }
        if (imported > 0) {
            // Save vector data FIRST: if an I/O error occurs, no new note appears
            // in the visible index with a missing handwritten drawing.
            newInk.forEach { (id, strokes) ->
                if (strokes.isNotEmpty()) store.saveInk(inkId(id), 0, strokes)
            }
            store.saveNotes(local)
        }
        return ImportResult(imported, conflicts, ignored)
    }
}
