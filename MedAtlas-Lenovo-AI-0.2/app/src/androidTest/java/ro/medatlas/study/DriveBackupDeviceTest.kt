package ro.medatlas.study

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ro.medatlas.study.data.LibraryStore
import ro.medatlas.study.data.StudyNote
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DriveBackupDeviceTest {
    /** Drive uses Android SAF, so test the exact ZIP export/restore used by the picker. */
    @Test
    fun backupRestoresNotesAndFolderHierarchy() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = LibraryStore(context)
        val folderName = "DriveTest-" + UUID.randomUUID().toString().take(8)
        val folder = LibraryOrganization(context, store).create(folderName, null)
        val note = StudyNote(documentId = "medical-notebook",
            heading = "Restaurare manuală Drive", page = 0, body = "Test sincronizare",
            course = folderName, paper = "BLANK")
        val zipFile = File.createTempFile("medatlas-drive-", ".zip", context.cacheDir)
        try {
            store.saveNotes(store.notes() + note)
            LibraryOrganization(context, store).moveNote(note.id, folder.id)
            store.exportBackup(Uri.fromFile(zipFile))

            store.saveNotes(store.notes().filterNot { it.id == note.id })
            LibraryOrganization(context, store).remove(folder.id)
            store.importBackup(Uri.fromFile(zipFile))

            assertTrue(store.notes().any { it.id == note.id && it.body == "Test sincronizare" })
            val restoredOrganization = LibraryOrganization(context, store)
            assertTrue(restoredOrganization.allFolders().any { it.id == folder.id && it.name == folderName })
            assertTrue(restoredOrganization.noteFolder(note) == folder.id)
        } finally {
            store.saveNotes(store.notes().filterNot { it.id == note.id })
            LibraryOrganization(context, store).remove(folder.id)
            zipFile.delete()
        }
    }
}
