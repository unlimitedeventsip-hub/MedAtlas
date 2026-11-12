package ro.medatlas.study.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

class LibraryStore(private val context: Context) {
    private val root = File(context.filesDir, "medatlas").apply { mkdirs() }
    private val docsDir = File(root, "documents").apply { mkdirs() }
    private val inkDir = File(root, "ink").apply { mkdirs() }
    private val stickersDir = File(root, "stickers").apply { mkdirs() }
    private val ocrDir = File(root, "ocr").apply { mkdirs() }
    private val commentsDir = File(root, "comments").apply { mkdirs() }
    private val textBoxesDir = File(root, "text_boxes").apply { mkdirs() }
    private val notebookPagesDir = File(root, "notebook_pages").apply { mkdirs() }
    fun saveNotebookDrawing(bitmap: android.graphics.Bitmap, existing: String = ""): String {
        val safe = existing.takeIf { it.matches(Regex("[0-9a-f-]{36}\\.png")) }
            ?: (UUID.randomUUID().toString() + ".png")
        val destination = File(notebookPagesDir, safe)
        val temp = File(notebookPagesDir, safe + ".tmp")
        temp.outputStream().use { out ->
            require(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)) {
                "Nu am putut salva pagina desenată"
            }
        }
        if (!temp.renameTo(destination)) { temp.copyTo(destination, overwrite = true); temp.delete() }
        return safe
    }
    fun notebookDrawing(name: String): android.graphics.Bitmap? {
        if (!name.matches(Regex("[0-9a-f-]{36}\\.png"))) return null
        return android.graphics.BitmapFactory.decodeFile(File(notebookPagesDir, name).absolutePath)
    }

    fun textBoxes(documentId: String, page: Int): List<PageTextBox> {
        val file = File(textBoxesDir, "${documentId}_${page}.json")
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { PageTextBox.parse(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }
    fun saveTextBoxes(documentId: String, page: Int, boxes: List<PageTextBox>) {
        val target = File(textBoxesDir, "${documentId}_${page}.json")
        val temp = File(textBoxesDir, "${documentId}_${page}.tmp")
        temp.writeText(JSONArray().apply { boxes.forEach { put(it.json()) } }.toString())
        if (!temp.renameTo(target)) { temp.copyTo(target, overwrite = true); temp.delete() }
    }

    fun comments(documentId: String, page: Int): List<PageComment> {
        val file = File(commentsDir, "${documentId}_${page}.json")
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { PageComment.parse(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }
    fun saveComments(documentId: String, page: Int, comments: List<PageComment>) {
        val file = File(commentsDir, "${documentId}_${page}.json")
        val temp = File(commentsDir, "${documentId}_${page}.tmp")
        temp.writeText(JSONArray().apply { comments.forEach { put(it.json()) } }.toString())
        if (!temp.renameTo(file)) { temp.copyTo(file, overwrite = true); temp.delete() }
    }


    private fun <T> loadArray(name: String, parse: (JSONObject) -> T): List<T> = runCatching {
        val arr = JSONArray(File(root, name).readText())
        (0 until arr.length()).map { parse(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun saveArray(name: String, objects: List<JSONObject>) {
        val target = File(root, name)
        val temp = File(root, "$name.tmp")
        temp.writeText(JSONArray().apply { objects.forEach { put(it) } }.toString())
        if (!temp.renameTo(target)) { temp.copyTo(target, overwrite = true); temp.delete() }
    }

    /** Full local backup for migrations and reinstallations; exported only by explicit user action. */
    fun exportBackup(uri: Uri) {
        val output = context.contentResolver.openOutputStream(uri) ?: error("Nu se poate deschide destinația.")
        output.use { stream ->
            java.util.zip.ZipOutputStream(stream).use { zip ->
                root.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.forEach { file ->
                    val relative = file.relativeTo(root).invariantSeparatorsPath
                    zip.putNextEntry(java.util.zip.ZipEntry(relative))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** Restore only paths inside the app's private MedAtlas directory. */
    fun importBackup(uri: Uri) {
        val input = context.contentResolver.openInputStream(uri) ?: error("Arhiva nu poate fi citită.")
        input.use { source ->
            java.util.zip.ZipInputStream(source).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) { zip.closeEntry(); continue }
                    val dest = File(root, entry.name)
                    require(dest.canonicalPath.startsWith(root.canonicalPath + File.separator)) {
                        "Arhiva conține o cale invalidă."
                    }
                    dest.parentFile?.mkdirs()
                    dest.outputStream().use { zip.copyTo(it) }
                    zip.closeEntry()
                }
            }
        }
    }

    fun documents(): List<StudyDocument> = loadArray("documents.json", StudyDocument::parse).sortedByDescending { it.importedAt }
    fun boards(): List<StudyNote> = loadArray("boards.json", StudyNote::parse)
    fun saveBoards(items: List<StudyNote>) = saveArray("boards.json", items.map { it.json() })
    fun notes(): List<StudyNote> = loadArray("notes.json", StudyNote::parse)
    fun cards(): List<Flashcard> = loadArray("cards.json", Flashcard::parse)
    fun saveNotes(items: List<StudyNote>) = saveArray("notes.json", items.map { it.json() })
    fun saveCards(items: List<Flashcard>) = saveArray("cards.json", items.map { it.json() })
    fun saveDocuments(items: List<StudyDocument>) = saveArray("documents.json", items.map { it.json() })
    fun fileOf(doc: StudyDocument) = File(docsDir, doc.localName)

    fun ink(documentId: String, page: Int): List<Stroke> {
        val f = File(inkDir, "${documentId}_$page.json")
        return runCatching {
            val array = JSONArray(f.readText())
            (0 until array.length()).map { Stroke.parse(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }
    fun saveInk(documentId: String, page: Int, strokes: List<Stroke>) {
        val target = File(inkDir, "${documentId}_$page.json")
        val temp = File(inkDir, "${documentId}_$page.tmp")
        temp.writeText(JSONArray().apply { strokes.forEach { put(it.json()) } }.toString())
        if (!temp.renameTo(target)) { temp.copyTo(target, overwrite = true); temp.delete() }
    }

    fun stickers(documentId: String, page: Int): List<PageSticker> {
        val file = File(stickersDir, "${documentId}_$page.json")
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { PageSticker.parse(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    /** The source list is generated from sticker metadata, not from guessed web origins. */
    fun imageBibliography(): List<PageSticker> = stickersDir.listFiles().orEmpty()
        .filter { it.name.endsWith(".json") }
        .flatMap { file ->
            runCatching {
                val a = JSONArray(file.readText())
                (0 until a.length()).map { PageSticker.parse(a.getJSONObject(it)) }
            }.getOrDefault(emptyList())
        }.filter { it.sourceUrl.isNotBlank() || it.attribution.isNotBlank() }
        .distinctBy { it.sourceUrl.ifBlank { it.imageName } }

    fun saveStickers(documentId: String, page: Int, items: List<PageSticker>) {
        val file = File(stickersDir, "${documentId}_$page.json")
        val temp = File(stickersDir, "${documentId}_$page.tmp")
        temp.writeText(JSONArray().apply { items.forEach { put(it.json()) } }.toString())
        if (!temp.renameTo(file)) { temp.copyTo(file, overwrite = true); temp.delete() }
    }

    fun importSticker(uri: Uri): String {
        val fileName = UUID.randomUUID().toString() + ".png"
        val output = File(stickersDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            output.outputStream().use { input.copyTo(it) }
        } ?: error("Imaginea nu a putut fi citită")
        return fileName
    }

    fun saveGeneratedSticker(bitmap: android.graphics.Bitmap): String {
        val filename = UUID.randomUUID().toString() + ".png"
        File(stickersDir, filename).outputStream().use { output ->
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)) { "Stickerul AI nu a putut fi salvat" }
        }
        return filename
    }

    fun builtInSticker(name: String): String {
        require(name in listOf("lungs", "heart", "neuron")) { "Sticker necunoscut" }
        val filename = UUID.randomUUID().toString() + ".png"
        val dest = File(stickersDir, filename)
        context.assets.open("stickers/$name.png").use { input -> dest.outputStream().use { input.copyTo(it) } }
        return filename
    }

    fun stickerBitmap(name: String): android.graphics.Bitmap? = runCatching {
        android.graphics.BitmapFactory.decodeFile(File(stickersDir, name).absolutePath)
    }.getOrNull()

    fun removeDocument(documentId: String) {
        val doc = documents().firstOrNull { it.id == documentId } ?: return
        fileOf(doc).delete()
        File(docsDir, "${doc.id}.txt").delete()
        inkDir.listFiles()?.filter { it.name.startsWith("${doc.id}_") }?.forEach { it.delete() }
        ocrDir.listFiles()?.filter { it.name.startsWith("${doc.id}_") }?.forEach { it.delete() }
        commentsDir.listFiles()?.filter { it.name.startsWith("${doc.id}_") }?.forEach { it.delete() }
        textBoxesDir.listFiles()?.filter { it.name.startsWith("${doc.id}_") }?.forEach { it.delete() }
        stickersDir.listFiles()?.filter { it.name.startsWith("${doc.id}_") }?.forEach { it.delete() }
        saveDocuments(documents().filterNot { it.id == documentId })
        saveNotes(notes().filterNot { it.documentId == documentId })
        saveCards(cards().filterNot { it.documentId == documentId })
    }

    fun import(uri: Uri, course: String = "Fără materie"): StudyDocument {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment ?: "Material"
        val ext = name.substringAfterLast('.', "").lowercase()
        require(ext in listOf("pdf", "docx", "ppt", "pptx")) {
            "Sunt acceptate PDF, DOCX, PPT și PPTX."
        }
        val id = UUID.randomUUID().toString()
        val filename = "$id.$ext"
        val destination = File(docsDir, filename)
        context.contentResolver.openInputStream(uri)?.use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
            ?: error("Nu se poate citi fișierul.")
        try {
            if (ext != "pdf") {
                val text = when (ext) {
                    "docx" -> extractDocx(destination)
                    "pptx" -> extractPptx(destination)
                    "ppt" -> "Fișier PowerPoint (.ppt) păstrat în formatul original. " +
                        "Deschide prezentarea într-o aplicație Office compatibilă pentru a vedea diapozitivele."
                    else -> ""
                }
                File(docsDir, "$id.txt").writeText(text)
            }
            val doc = StudyDocument(id, name.substringBeforeLast('.'), name, ext.uppercase(), filename, System.currentTimeMillis(), course)
            saveDocuments(documents() + doc)
            return doc
        } catch (e: Exception) {
            destination.delete()
            throw e
        }
    }

    fun extractedText(documentId: String): String = runCatching { File(docsDir, "$documentId.txt").readText() }.getOrDefault("")
    fun saveExtractedText(documentId: String, text: String) {
        val file = File(docsDir, "$documentId.txt")
        val temp = File(docsDir, "$documentId.txt.tmp")
        temp.writeText(text)
        if (!temp.renameTo(file)) { temp.copyTo(file, overwrite = true); temp.delete() }
    }

    fun savePageText(documentId: String, page: Int, text: String) {
        val dest = File(ocrDir, "${documentId}_${page}.txt")
        val tmp = File(ocrDir, "${documentId}_${page}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(dest)) { tmp.copyTo(dest, overwrite = true); tmp.delete() }
    }
    fun pageText(documentId: String, page: Int): String = runCatching {
        File(ocrDir, "${documentId}_${page}.txt").readText()
    }.getOrDefault("")
    /**
     * Search OCR page text without concatenating an entire medical textbook into memory.
     * Called on Dispatchers.IO by the library UI, not on the main thread.
     */
    fun containsIndexedText(doc: StudyDocument, phrase: String): Boolean {
        val keyword = phrase.trim()
        if (keyword.isBlank()) return false
        return if (doc.kind != "PDF") {
            val source = File(docsDir, "${doc.id}.txt")
            source.isFile && runCatching {
                source.bufferedReader().useLines { lines ->
                    lines.any { it.contains(keyword, ignoreCase = true) }
                }
            }.getOrDefault(false)
        } else {
            ocrDir.listFiles { file ->
                file.isFile && file.name.startsWith("${doc.id}_") && file.name.endsWith(".txt")
            }.orEmpty().any { file ->
                runCatching {
                    file.bufferedReader().useLines { lines ->
                        lines.any { it.contains(keyword, ignoreCase = true) }
                    }
                }.getOrDefault(false)
            }
        }
    }

    /**
     * Small source-grounded excerpts for Nadia search. Never upload entire books.
     */
    fun searchEvidence(doc: StudyDocument, phrase: String, maxChars: Int = 900): String? {
        val term = phrase.trim()
        if (term.isBlank()) return null
        val sources = if (doc.kind == "PDF") {
            ocrDir.listFiles { f -> f.isFile && f.name.startsWith("${doc.id}_") && f.name.endsWith(".txt") }
                .orEmpty().sortedBy { it.name.substringAfter("${doc.id}_").substringBefore('.').toIntOrNull() ?: 0 }
        } else listOf(File(docsDir, "${doc.id}.txt"))
        for (source in sources) {
            if (!source.isFile) continue
            val snippet = runCatching {
                source.bufferedReader().use { reader ->
                    var previous = ""
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.contains(term, ignoreCase = true)) {
                            val next = reader.readLine().orEmpty()
                            return@use listOf(previous, line, next)
                                .filter { it.isNotBlank() }.joinToString(" ").take(maxChars.coerceIn(100, 1400))
                        }
                        previous = line
                    }
                    null
                }
            }.getOrNull()
            if (snippet != null) {
                val page = if (doc.kind == "PDF") {
                    source.name.removePrefix("${doc.id}_").substringBefore('.').toIntOrNull()?.plus(1)
                } else null
                return if (page != null) "Pagina $page: $snippet" else snippet
            }
        }
        return null
    }

    fun studyText(doc: StudyDocument): String = if (doc.kind != "PDF") extractedText(doc.id) else
        ocrDir.listFiles().orEmpty().filter { it.name.startsWith("${doc.id}_") && it.name.endsWith(".txt") }
            .sortedBy { it.name.substringAfter("${doc.id}_").substringBefore('.').toIntOrNull() ?: 0 }
            .joinToString("\n\n") { it.readText() }


    private fun extractDocx(file: File): String {
        ZipInputStream(file.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") return readOfficeXml(zip, "p", "t")
            }
        }
        return "Nu s-a putut extrage textul din acest document."
    }

    private fun extractPptx(file: File): String {
        val slides = mutableMapOf<Int, String>()
        ZipInputStream(file.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val match = Regex("ppt/slides/slide(\\d+)\\.xml").matchEntire(entry.name)
                if (match != null) {
                    slides[match.groupValues[1].toInt()] = readOfficeXml(zip, "p", "t")
                }
            }
        }
        return slides.toSortedMap().entries.joinToString("\n\n") { (num, text) -> "SLIDE $num\n$text" }
    }

    private fun readOfficeXml(stream: InputStream, paragraphTag: String, textTag: String): String {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(stream, "UTF-8")
        val result = StringBuilder()
        var inText = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name?.substringAfter(':')
            when (parser.eventType) {
                XmlPullParser.START_TAG -> if (tag == textTag) inText = true
                XmlPullParser.TEXT -> if (inText) result.append(parser.text)
                XmlPullParser.END_TAG -> {
                    if (tag == textTag) inText = false
                    if (tag == paragraphTag) result.append('\n')
                }
            }
            parser.next()
        }
        return result.toString().trim()
    }
}
