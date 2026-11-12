package ro.medatlas.study

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import android.content.Context
import ro.medatlas.study.data.LibraryStore
import ro.medatlas.study.data.StudyDocument
import ro.medatlas.study.data.Stroke
import ro.medatlas.study.data.Tool
import ro.medatlas.study.data.InkPoint
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class OfficePreviewDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = LibraryStore(context)

    private fun sample(kind: String, files: Map<String, String>): Pair<File, StudyDocument> {
        val id = UUID.randomUUID().toString()
        val name = id + "." + kind.lowercase()
        val doc = StudyDocument(id, "Test", name, kind, name, System.currentTimeMillis())
        val file = store.fileOf(doc)
        file.parentFile?.mkdirs()
        ZipOutputStream(file.outputStream()).use { zip ->
            files.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return file to doc
    }

    @Test fun rendersDocxTextAndTablesInternally() {
        val source = mapOf("word/document.xml" to
            """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Test medicină Word</w:t></w:r></w:p>
                    <w:tbl><w:tr><w:tc><w:p><w:r><w:t>VEMS</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
                </w:body>
              </w:document>""")
        val (file, doc) = sample("DOCX", source)
        try {
            val preview = OfficeHtml.load(doc, store)
            assertEquals(1, preview.pages.size)
            assertTrue(preview.warning, preview.pages[0].contains("Test medicină Word"))
            assertTrue(preview.pages[0].contains("<table>"))
            assertTrue(preview.pages[0].contains("VEMS"))
            assertTrue(!preview.slides)
        } finally { file.delete() }
    }

    @Test fun rendersPptxSlideInsideAppWithoutPdfConversion() {
        val presentation =
            """<p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
               xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <p:sldIdLst><p:sldId id="256" r:id="rId1"/></p:sldIdLst>
                <p:sldSz cx="12192000" cy="6858000"/>
            </p:presentation>"""
        val relations =
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="test" Target="slides/slide1.xml"/>
            </Relationships>"""
        val slide =
            """<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                <p:cSld><p:spTree><p:sp>
                  <p:spPr><a:xfrm><a:off x="1000000" y="800000"/><a:ext cx="8000000" cy="3000000"/></a:xfrm></p:spPr>
                  <p:txBody><a:p><a:r><a:rPr sz="3000"/><a:t>Prezentare pneumologie</a:t></a:r></a:p></p:txBody>
                </p:sp></p:spTree></p:cSld>
            </p:sld>"""
        val (file, doc) = sample("PPTX", mapOf(
            "ppt/presentation.xml" to presentation,
            "ppt/_rels/presentation.xml.rels" to relations,
            "ppt/slides/slide1.xml" to slide
        ))
        try {
            val preview = OfficeHtml.load(doc, store)
            assertTrue(preview.slides)
            assertEquals(1, preview.pages.size)
            assertTrue(preview.warning, preview.pages[0].contains("Prezentare pneumologie"))
            assertTrue(preview.pages[0].contains("position:absolute"))
            assertTrue(!preview.pages[0].contains("Previzualizare limitată"))
        } finally { file.delete() }
    }

    @Test fun pencilStrokeSurvivesJsonRoundTrip() {
        val original = Stroke(tool = Tool.PENCIL, width = 2f,
            points = listOf(InkPoint(.2f, .4f, .3f), InkPoint(.7f, .8f, .9f)))
        val parsed = Stroke.parse(original.json())
        assertEquals(Tool.PENCIL, parsed.tool)
        assertEquals(2f, parsed.width)
        assertEquals(original.points, parsed.points)
    }
}
