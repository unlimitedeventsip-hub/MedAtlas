package ro.medatlas.study

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ro.medatlas.study.data.LibraryStore
import ro.medatlas.study.data.StudyDocument
import ro.medatlas.study.data.StudyNote
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class StorageSafetyDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = LibraryStore(context)

    @Test fun incompleteAtomicSaveKeepsPreviousNotes() {
        val before = store.notes()
        val marker = StudyNote(
            id = UUID.randomUUID().toString(),
            documentId = "medical-notebook", page = 0,
            heading = "Păstrează originalul",
            body = "Conținutul trebuie să supraviețuiască unei întreruperi"
        )
        try {
            store.saveNotes(before + marker)
            val notesFile = File(context.filesDir, "medatlas/notes.json")
            val pending = AtomicFile(notesFile).startWrite()
            // Simulate power loss while writing a new JSON file: no finishWrite.
            pending.write("[INVALID-INCOMPLETE".toByteArray())
            pending.close()
            val readBack = store.notes().single { it.id == marker.id }
            assertEquals(marker.body, readBack.body)
            store.saveNotes(store.notes())
            assertTrue(File(context.filesDir, "medatlas/notes.json").isFile)
        } finally {
            store.saveNotes(before)
        }
    }

    @Test fun maliciousDocumentMetadataCannotEscapeLocalLibrary() {
        val before = store.notes()
        val marker = StudyNote(
            id = UUID.randomUUID().toString(),
            documentId = "medical-notebook", page = 0,
            heading = "Date personale protejate", body = "Nu suprascrie"
        )
        store.saveNotes(before + marker)
        val archive = File.createTempFile("medatlas-unsafe-metadata-", ".zip", context.cacheDir)
        try {
            ZipOutputStream(archive.outputStream()).use { zip ->
                val files = linkedMapOf(
                    "notes.json" to "[]",
                    "documents.json" to
                        """[{"id":"safe-doc","title":"Fals","originalName":"f.pdf","kind":"PDF","localName":"../../shared_prefs/google.xml","importedAt":1}]"""
                )
                files.forEach { (name, text) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(text.toByteArray())
                    zip.closeEntry()
                }
            }
            assertTrue(runCatching { store.importBackup(Uri.fromFile(archive)) }.isFailure)
            assertEquals("Nu suprascrie", store.notes().single { it.id == marker.id }.body)
        } finally {
            store.saveNotes(before)
            archive.delete()
        }
    }

    @Test fun externalDocumentPathAndStickerPathAreRejected() {
        val doc = StudyDocument(
            id = "safe-id", title = "Fals", originalName = "Fals.pdf",
            kind = "PDF", localName = "../../shared_prefs/secret.xml",
            importedAt = System.currentTimeMillis()
        )
        assertTrue(runCatching { store.fileOf(doc) }.isFailure)
        assertTrue(store.stickerBitmap("../../shared_prefs/secret.xml") == null)
    }
}
