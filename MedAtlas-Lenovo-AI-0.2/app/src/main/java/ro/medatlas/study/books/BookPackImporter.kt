package ro.medatlas.study.books

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.ZipInputStream
import ro.medatlas.study.data.LibraryStore

/** Import personal, legally obtained medical books from a user-selected ZIP.
 * Never publish personal PDFs to a public Git repository.
 *
 * The caller must run this on Dispatchers.IO. Streams each entry to disk,
 * avoids ZipSlip, caps individual and total expanded sizes, and deletes temps.
 */
internal object BookPackImporter {
    data class Result(val imported: Int, val duplicates: Int, val unsupported: Int,
                      val failed: Int, val booksProcessed: Int)
    fun import(context: Context, zipUri: Uri, store: LibraryStore,
               progress: (Int) -> Unit): Result {
        val temporary = File(context.cacheDir, "book_zip").apply { mkdirs() }
        var imported = 0
        var duplicates = 0
        var unsupported = 0
        var failed = 0
        var processed = 0
        var expandedTotal = 0L
        val supported = setOf("pdf", "docx", "pptx")
        val individualLimit = 750L * 1024 * 1024
        val totalLimit = 12L * 1024 * 1024 * 1024
        context.contentResolver.openInputStream(zipUri).use { source ->
            require(source != null) { "Arhiva nu poate fi deschisă" }
            ZipInputStream(source.buffered()).use { archive ->
                while (true) {
                    val entry = archive.nextEntry ?: break
                    if (entry.isDirectory) { archive.closeEntry(); continue }
                    if (processed >= 300) error("Limita de 300 documente a fost atinsă")
                    processed++
                    val raw = entry.name.replace('\\', '/')
                    val filename = raw.substringAfterLast('/')
                    if (filename.isBlank() || filename.startsWith(".") ||
                        filename != raw || filename.contains("..")) {
                        unsupported++
                        archive.closeEntry()
                        continue
                    }
                    val extension = filename.substringAfterLast('.', "").lowercase()
                    if (extension !in supported) {
                        unsupported++
                        archive.closeEntry()
                        continue
                    }
                    val sanitized = filename.take(140)
                    if (store.documents().any { it.originalName == sanitized }) {
                        duplicates++
                        archive.closeEntry()
                        progress(processed)
                        continue
                    }
                    val single = File(temporary, "entry_" + processed).apply { mkdirs() }
                    val target = File(single, sanitized)
                    try {
                        target.outputStream().buffered().use { output ->
                            val buffer = ByteArray(1024 * 1024)
                            var size = 0L
                            while (true) {
                                val bytes = archive.read(buffer)
                                if (bytes == -1) break
                                size += bytes
                                expandedTotal += bytes
                                if (size > individualLimit || expandedTotal > totalLimit)
                                    error("Arhiva este prea mare pentru importul sigur")
                                output.write(buffer, 0, bytes)
                            }
                        }
                        require(target.length() > 0) { "Document gol" }
                        val uri = FileProvider.getUriForFile(context,
                            context.packageName + ".files", target)
                        store.import(uri, "Cărți medicale")
                        imported++
                    } catch (e: Exception) {
                        failed++
                        if (expandedTotal > totalLimit) throw e
                    } finally {
                        target.delete()
                        single.delete()
                        archive.closeEntry()
                        progress(processed)
                    }
                }
            }
        }
        return Result(imported, duplicates, unsupported, failed, processed)
    }
}
