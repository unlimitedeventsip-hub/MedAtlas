package ro.medatlas.study.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Text-only cross-device interchange in the same StudyNote JSON schema used by
 * MedAtlas Android. Handwritten ink, stickers and embedded images are not part
 * of this format. Those require the full ZIP backup.
 *
 * Import never overwrites different existing notes, regardless of timestamps.
 * Conflicts are copied, with a new UUID, so no local edit is discarded.
 */
internal object PortableNotes {
    private const val MAX_IMPORT_BYTES = 12 * 1024 * 1024
    private const val MAX_NOTES = 10_000
    private const val MAX_BODY = 300_000

    data class ImportResult(val imported: Int, val copiedConflicts: Int, val ignored: Int)

    fun export(context: Context, store: LibraryStore, uri: Uri): Int {
        val notes = store.notes()
        val payload = JSONArray().apply {
            notes.forEach { note ->
                // A drawing's path is only meaningful inside the originating
                // tablet's private storage, so don't export a broken reference.
                put(note.copy(handwritingFile = "").json())
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
            StudyNote(
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
        }

        val local = store.notes().toMutableList()
        val existing = local.associateBy { it.id }.toMutableMap()
        var imported = 0
        var conflicts = 0
        var ignored = 0
        parsed.forEach { candidate ->
            val previous = existing[candidate.id]
            if (previous == null) {
                local += candidate
                existing[candidate.id] = candidate
                imported++
            } else if (previous.body == candidate.body &&
                previous.heading == candidate.heading) {
                ignored++
            } else {
                val duplicate = candidate.copy(
                    id = UUID.randomUUID().toString(),
                    heading = (candidate.heading.take(154) + " (copie importată)"),
                    updatedAt = System.currentTimeMillis()
                )
                local += duplicate
                existing[duplicate.id] = duplicate
                imported++
                conflicts++
            }
        }
        if (imported > 0) store.saveNotes(local)
        return ImportResult(imported, conflicts, ignored)
    }
}
