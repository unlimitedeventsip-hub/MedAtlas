package ro.medatlas.study.data

import android.util.AtomicFile
import java.io.File
import java.io.OutputStream

/**
 * A single source of truth for local writes that must survive an app/process
 * interruption. AtomicFile keeps the previous complete version until the new
 * version is committed, and openRead() recovers an interrupted transaction.
 *
 * All LibraryStore instances share the same lock. This prevents two concurrent
 * WorkManager/UI writes from interleaving the .new / .bak transactions.
 */
internal object AtomicLocalFiles {
    private val lock = Any()

    fun readText(file: File): String = synchronized(lock) {
        AtomicFile(file).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    fun writeText(file: File, text: String) {
        write(file) { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
        }
    }

    fun write(file: File, body: (OutputStream) -> Unit) = synchronized(lock) {
        check(file.parentFile?.isDirectory == true || file.parentFile?.mkdirs() == true) {
            "Nu se poate crea folderul pentru salvare."
        }
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            body(output)
            atomic.finishWrite(output)
        } catch (failure: Exception) {
            atomic.failWrite(output)
            throw failure
        }
    }
}
