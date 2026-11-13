package ro.medatlas.study

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ro.medatlas.study.data.LibraryStore
import ro.medatlas.study.data.PortableNotes
import ro.medatlas.study.data.StudyNote
import ro.medatlas.study.data.StudyDocument
import ro.medatlas.study.data.Stroke
import ro.medatlas.study.data.InkPoint
import ro.medatlas.study.data.Tool
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CrossDeviceDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = LibraryStore(context)

    @Test fun phoneOffersReaderAndNotesWithoutDuplicatingDataModel() {
        assertEquals(WorkspaceProfile.PHONE, DeviceExperience.profile(360))
        assertEquals(WorkspaceProfile.PHONE, DeviceExperience.profile(599))
        assertEquals(WorkspaceProfile.TABLET, DeviceExperience.profile(600))
        assertEquals(setOf(0, 1), DeviceExperience.allowedTabs(WorkspaceProfile.PHONE))
        assertTrue(DeviceExperience.allowedTabs(WorkspaceProfile.TABLET).containsAll(
            listOf(0, 1, 2, 3, 4)))
        assertFalse(DeviceExperience.allowsDocumentEditing(WorkspaceProfile.PHONE))
        assertTrue(DeviceExperience.allowsDocumentEditing(WorkspaceProfile.TABLET))
    }

    @Test fun smartScanLayoutIsPortableAndNormalized() {
        val scan = SmartScanReport(
            text = "Plămâni, frecvența respiratorie",
            lines = listOf(ScanTextLine(
                "Plămâni", ScanRegion(.1f, .15f, .6f, .2f),
                "ro", .88f, 0f
            )),
            codes = listOf(ScanCode("QR", "https://example.org/article",
                ScanRegion(.7f, .4f, .9f, .6f)))
        )
        val parsed = SmartScanReport.parse(JSONObject(scan.json().toString()))
        assertEquals(scan.text, parsed.text)
        assertEquals(scan.lines, parsed.lines)
        assertEquals(scan.codes, parsed.codes)
    }

    @Test fun importingConflictingNoteCreatesCopyAndPreservesOriginal() {
        val before = store.notes()
        val id = UUID.randomUUID().toString()
        val local = StudyNote(
            id = id, documentId = "medical-notebook", page = 0,
            heading = "Fiziologie", body = "Versiunea locală"
        )
        store.saveNotes(before + local)
        val file = File.createTempFile("medatlas-transfer-", ".json", context.cacheDir)
        file.writeText(JSONArray().put(local.copy(body = "Versiunea laptop").json()).toString())
        try {
            val result = PortableNotes.import(context, store, Uri.fromFile(file))
            assertEquals(1, result.imported)
            assertEquals(1, result.copiedConflicts)
            assertEquals(0, result.ignored)
            val notes = store.notes()
            assertEquals("Versiunea locală", notes.single { it.id == id }.body)
            assertEquals(1, notes.count { it.body == "Versiunea laptop" })
        } finally {
            store.saveNotes(before)
            file.delete()
        }
    }

    @Test fun vectorHandwritingTransfersWithoutOverwritingExistingInk() {
        val before = store.notes()
        val id = UUID.randomUUID().toString()
        val local = StudyNote(
            id = id, documentId = "medical-notebook", page = 0,
            heading = "Anatomie", body = "Versiune tabletă"
        )
        val ink = Stroke(
            tool = Tool.PENCIL, width = 2f,
            points = listOf(InkPoint(.1f, .2f, .4f), InkPoint(.9f, .8f, .8f))
        )
        store.saveNotes(before + local)
        val document = local.copy(body = "Versiune laptop").json().apply {
            put("strokes", JSONArray().put(ink.json()))
        }
        val file = File.createTempFile("medatlas-ink-transfer-", ".json", context.cacheDir)
        file.writeText(JSONArray().put(document).toString())
        var duplicateId: String? = null
        try {
            val result = PortableNotes.import(context, store, Uri.fromFile(file))
            assertEquals(1, result.copiedConflicts)
            assertEquals("Versiune tabletă", store.notes().single { it.id == id }.body)
            val copy = store.notes().single { it.body == "Versiune laptop" }
            duplicateId = copy.id
            val key = "notebook-" + UUID.nameUUIDFromBytes(
                copy.id.toByteArray(Charsets.UTF_8))
            assertEquals(listOf(ink), store.ink(key, 0))
        } finally {
            store.saveNotes(before)
            duplicateId?.let { newId ->
                val key = "notebook-" + UUID.nameUUIDFromBytes(
                    newId.toByteArray(Charsets.UTF_8))
                store.saveInk(key, 0, emptyList())
            }
            file.delete()
        }
    }

    @Test fun scannedQrCanBeSearchedWithItsOriginalPdfPage() {
        val id = UUID.randomUUID().toString()
        val doc = StudyDocument(
            id = id,
            title = "Curs pneumologie",
            originalName = "curs.pdf",
            kind = "PDF",
            localName = "$id.pdf",
            importedAt = System.currentTimeMillis()
        )
        val qrValue = "https://example.org/medatlas/ref/$id"
        val report = SmartScanReport(
            text = "Materiale respiratorii",
            lines = emptyList(),
            codes = listOf(ScanCode("QR", qrValue, null))
        )
        val index = File(context.filesDir, "medatlas/ocr/${id}_2.layout.json")
        try {
            SmartDocumentScanner.save(context, id, 2, report)
            assertTrue(store.containsIndexedText(doc, qrValue))
            val evidence = store.searchEvidenceHit(doc, qrValue)
            assertEquals(2, evidence?.page)
            assertTrue(evidence?.excerpt?.contains(qrValue) == true)
        } finally { index.delete() }
    }

    @Test fun invalidPortablePayloadCannotChangeExistingNotes() {
        val before = store.notes()
        val file = File.createTempFile("medatlas-invalid-", ".json", context.cacheDir)
        file.writeText(JSONArray().put(
            JSONObject().put("id", "valid-id").put("body", 42)
        ).toString())
        try {
            assertTrue(runCatching {
                PortableNotes.import(context, store, Uri.fromFile(file))
            }.isFailure)
            assertEquals(before, store.notes())
        } finally { file.delete() }
    }
}
