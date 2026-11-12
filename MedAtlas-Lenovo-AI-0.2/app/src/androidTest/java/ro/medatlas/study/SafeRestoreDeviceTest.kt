package ro.medatlas.study

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import ro.medatlas.study.data.LibraryStore
import ro.medatlas.study.data.StudyNote
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class SafeRestoreDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = LibraryStore(context)

    private fun archive(entries: List<Pair<String, String>>): File {
        val file = File.createTempFile("medatlas-restore-validation-", ".zip", context.cacheDir)
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, value) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return file
    }

    /** Broken JSON may not replace an existing handwritten note. */
    @Test fun corruptMetadataCannotTouchLiveNotes() {
        val id = UUID.randomUUID().toString()
        val original = StudyNote(id = id, documentId = "safety-test",
            heading = "Notiță protejată", page = 0, body = "Nu se șterge",
            course = "Test")
        store.saveNotes(store.notes() + original)
        val before = store.notes().single { it.id == id }
        val bad = archive(listOf(
            "notes.json" to "[{\"id\": \"corrupt\", \"body\": 7}]",
            "documents.json" to "invalid-json"
        ))
        try {
            try {
                store.importBackup(Uri.fromFile(bad))
                fail("Invalid metadata must not be restored.")
            } catch (_: Exception) {
                // The restore must fail before changing any saved notes.
            }
            val after = store.notes().single { it.id == id }
            assertEquals(before.body, after.body)
            assertEquals(before.heading, after.heading)
        } finally {
            store.saveNotes(store.notes().filterNot { it.id == id })
            bad.delete()
        }
    }

    /** A ZIP cannot access the parent's private files. */
    @Test fun rejectsZipSlipAndPreservesCurrentData() {
        val id = UUID.randomUUID().toString()
        val note = StudyNote(id = id, documentId = "safety-test",
            heading = "Test traversal", page = 0, body = "Intact",
            course = "Test")
        store.saveNotes(store.notes() + note)
        // Android's ZipOutputStream rejects "../" names at construction.
        // Build a valid ZIP, then rewrite the equally sized filename bytes in
        // both local and central headers to simulate a hostile downloaded ZIP.
        val malicious = archive(listOf(
            "notes.json" to "[]",
            "aa/outside.json" to "{}"
        ))
        val zipBytes = malicious.readBytes()
        val harmless = "aa/outside.json".toByteArray(Charsets.UTF_8)
        val traversal = "../outside.json".toByteArray(Charsets.UTF_8)
        require(harmless.size == traversal.size)
        var rewritten = 0
        for (offset in 0..(zipBytes.size - harmless.size)) {
            if (harmless.indices.all { index -> zipBytes[offset + index] == harmless[index] }) {
                traversal.copyInto(zipBytes, destinationOffset = offset)
                rewritten++
            }
        }
        assertTrue("Malicious ZIP fixture was not written", rewritten >= 2)
        malicious.writeBytes(zipBytes)
        try {
            var rejected = false
            try {
                store.importBackup(Uri.fromFile(malicious))
            } catch (_: Exception) {
                // Some Android versions reject the dangerous path in ZipFile,
                // others allow us to reject it in SafeBackupRestore.
                rejected = true
            }
            assertTrue("Zip-slip must be rejected", rejected)
            assertEquals("Intact", store.notes().single { it.id == id }.body)
            assertTrue(!File(context.filesDir, "outside.json").exists())
        } finally {
            store.saveNotes(store.notes().filterNot { it.id == id })
            malicious.delete()
        }
    }

    /** Missing ZIP central directory must fail, even if the first file is intact. */
    @Test fun truncatedArchiveDoesNotChangeExistingNotes() {
        val id = UUID.randomUUID().toString()
        val note = StudyNote(id = id, documentId = "safety-test",
            heading = "Notițe originale", page = 0, body = "De păstrat", course = "Test")
        store.saveNotes(store.notes() + note)
        val zip = archive(listOf("notes.json" to "[]"))
        try {
            val valid = zip.readBytes()
            // Remove the end-of-central-directory record.
            zip.writeBytes(valid.copyOf(valid.size - 22))
            var rejected = false
            try { store.importBackup(Uri.fromFile(zip)) }
            catch (_: Exception) { rejected = true }
            assertTrue("Truncated ZIP must not be accepted", rejected)
            assertEquals("De păstrat", store.notes().single { it.id == id }.body)
        } finally {
            store.saveNotes(store.notes().filterNot { it.id == id })
            zip.delete()
        }
    }

    @Test fun rejectsArchiveWithoutMedAtlasMetadata() {
        val zip = archive(listOf("random.txt" to "Not a backup"))
        try {
            var failed = false
            try { store.importBackup(Uri.fromFile(zip)) }
            catch (_: IllegalArgumentException) { failed = true }
            assertTrue(failed)
        } finally { zip.delete() }
    }
}
