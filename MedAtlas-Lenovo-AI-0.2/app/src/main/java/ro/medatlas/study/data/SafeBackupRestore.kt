package ro.medatlas.study.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Validates a complete backup before changing the user's library.
 *
 * Uses separate staging and rollback directories; no file from an invalid ZIP
 * can overwrite a saved note, drawing or imported document. When a filesystem
 * error occurs while copying the restored files, overwritten entries are
 * restored from the rollback directory. Existing unrelated files are retained.
 *
 * This protects against ordinary restore failures, not unexpected process death
 * during multi-file commits. Back up local changes before a cross-device restore.
 */
internal object SafeBackupRestore {
    private const val MAX_FILES = 60_000
    private const val MAX_FILE_BYTES = 3L * 1024 * 1024 * 1024
    private const val MAX_ARCHIVE_BYTES = 32L * 1024 * 1024 * 1024
    private val restoreLock = Any()

    fun restore(context: Context, root: File, uri: Uri) = synchronized(restoreLock) {
        val id = UUID.randomUUID().toString()
        val staging = File(context.filesDir, ".medatlas-stage-$id")
        val rollback = File(context.filesDir, ".medatlas-rollback-$id")
        val archive = File(context.filesDir, ".medatlas-archive-$id.zip")
        check(staging.mkdirs()) { "Nu se poate crea zona de restaurare." }
        if (!rollback.mkdirs()) {
            staging.deleteRecursively()
            error("Nu se poate crea zona de recuperare.")
        }
        try {
            // Content providers expose a stream, not necessarily a seekable file.
            // Retain a temporary compressed copy to verify the ZIP central
            // directory before unpacking. ZipInputStream alone would accept a
            // truncated archive containing only its first local entries.
            val input = context.contentResolver.openInputStream(uri)
                ?: error("Arhiva de backup nu poate fi deschisă.")
            input.use { source ->
                archive.outputStream().use { destination ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val n = source.read(buffer)
                        if (n < 0) break
                        copied += n
                        require(copied <= MAX_ARCHIVE_BYTES) {
                            "Arhiva ZIP este prea mare pentru verificare."
                        }
                        destination.write(buffer, 0, n)
                    }
                }
            }
            ZipFile(archive).use { verified ->
                require(verified.size() in 1..MAX_FILES) {
                    "Arhiva ZIP nu are un index valid de fișiere."
                }
            }
            val names = hashSetOf<String>()
            var entries = 0
            var total = 0L
            archive.inputStream().use { source ->
                ZipInputStream(source).use { zip ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = entry.name
                        // Paths are always relative and must not escape staging.
                        require(name.isNotBlank() &&
                            !name.startsWith("/") &&
                            '\\' !in name &&
                            '\u0000' !in name &&
                            name.split('/').none { it == ".." || it == "." } &&
                            !name.endsWith(".tmp")) {
                            "Arhiva conține o cale nepermisă."
                        }
                        if (entry.isDirectory) {
                            zip.closeEntry()
                            continue
                        }
                        require(names.add(name)) { "Arhiva conține fișiere duplicate." }
                        entries++
                        require(entries <= MAX_FILES) { "Arhiva conține prea multe fișiere." }
                        val output = File(staging, name)
                        require(output.canonicalPath.startsWith(
                            staging.canonicalPath + File.separator)) {
                            "Cale de backup nepermisă."
                        }
                        output.parentFile?.mkdirs()
                        var length = 0L
                        output.outputStream().use { saved ->
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                length += read
                                total += read
                                require(length <= MAX_FILE_BYTES && total <= MAX_ARCHIVE_BYTES) {
                                    "Backupul depășește limitele de siguranță."
                                }
                                saved.write(buffer, 0, read)
                            }
                        }
                        zip.closeEntry() // ZIP CRC is verified while reading.
                    }
                }
            }
            require(entries > 0 && names.any {
                it in setOf("documents.json", "notes.json", "boards.json",
                    "cards.json", "organization.json", "clinical-workbook-v1.json")
            }) { "Acest fișier nu este o arhivă MedAtlas validă." }

            // Corrupted core metadata must not poison the app's local library.
            for (filename in listOf("documents.json", "notes.json", "boards.json", "cards.json")) {
                val file = File(staging, filename)
                if (file.exists()) JSONArray(file.readText())
            }
            val organization = File(staging, "organization.json")
            if (organization.exists()) {
                val metadata = JSONObject(organization.readText())
                require(metadata.has("folders")) {
                    "Structura folderelor din arhivă este incompletă."
                }
            }


            val workbookFile = File(staging, "clinical-workbook-v1.json")
            val workbook = ClinicalBackup(context)
            val clinicalUpdate = if (workbookFile.exists()) {
                require(workbookFile.length() <= 32L * 1024 * 1024) { "Fișa clinică depășește limita de citire." }
                workbook.prepare(workbookFile.readText())
            } else null
            val previousClinical = clinicalUpdate?.let { workbook.encode() }
            var clinicalCommitStarted = false
            val staged = staging.walkTopDown().filter { it.isFile && it != workbookFile }.toList()
            val prior = hashSetOf<String>()

            // Save every file that could be replaced BEFORE any changes.
            staged.forEach { entry ->
                val relative = entry.relativeTo(staging).invariantSeparatorsPath
                val existing = File(root, relative)
                require(existing.canonicalPath.startsWith(root.canonicalPath + File.separator)) {
                    "Cale invalidă la restaurare."
                }
                if (existing.exists()) {
                    require(existing.isFile) { "Backupul suprapune un folder local." }
                    val saved = File(rollback, relative)
                    saved.parentFile?.mkdirs()
                    existing.copyTo(saved, overwrite = false)
                    prior.add(relative)
                }
            }

            val touched = mutableListOf<String>()
            try {
                staged.forEach { sourceFile ->
                    val relative = sourceFile.relativeTo(staging).invariantSeparatorsPath
                    val target = File(root, relative)
                    target.parentFile?.mkdirs()
                    val pending = File(target.parentFile, ".restore-$id-${target.name}.tmp")
                    touched.add(relative)
                    try {
                        sourceFile.copyTo(pending, overwrite = true)
                        if (!pending.renameTo(target)) {
                            pending.copyTo(target, overwrite = true)
                        }
                    } finally { pending.delete() }
                }
                if (clinicalUpdate != null) {
                    clinicalCommitStarted = true
                    check(clinicalUpdate.commit()) { "Restaurarea fișelor clinice a eșuat." }
                }
            } catch (error: Exception) {
                val failures = mutableListOf<Throwable>()
                if (clinicalCommitStarted && previousClinical != null) {
                    runCatching { check(workbook.prepare(previousClinical).commit()) }
                        .onFailure { failures.add(it) }
                }
                touched.asReversed().forEach { relative ->
                    val target = File(root, relative)
                    try {
                        if (relative in prior) {
                            val saved = File(rollback, relative)
                            saved.copyTo(target, overwrite = true)
                        } else {
                            if (target.exists() && !target.delete()) {
                                throw IOException("Nu se poate elimina fișierul incomplet: $relative")
                            }
                        }
                    } catch (rollbackError: Exception) { failures.add(rollbackError) }
                }
                failures.forEach(error::addSuppressed)
                throw error
            }
        } finally {
            staging.deleteRecursively()
            rollback.deleteRecursively()
            archive.delete()
        }
    }
}
