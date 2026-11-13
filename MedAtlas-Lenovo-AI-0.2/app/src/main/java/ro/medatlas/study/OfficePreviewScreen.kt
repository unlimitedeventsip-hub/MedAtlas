package ro.medatlas.study

import android.webkit.WebView
import android.webkit.WebViewClient
import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ro.medatlas.study.data.LibraryStore
import ro.medatlas.study.data.StudyDocument
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Node
import org.w3c.dom.Element
import android.util.Base64
import java.util.Locale

internal data class OfficePreview(val pages: List<String>, val warning: String, val slides: Boolean)

/**
 * Offline in-app preview of original OOXML files; NOT a PDF conversion.
 * Supports slide text/shape/image positioning and Word paragraphs/tables/images.
 * Some Office-specific features (animations, SmartArt, master layouts, charts) are not rendered.
 */
internal object OfficeHtml {
    private const val HEADER = "<!doctype html><html><head><meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=5.0'/>" +
        "<style>body{margin:0;background:#e8eef7;font-family:Arial,sans-serif;color:#26394f}" +
        "article{background:white;max-width:900px;margin:20px auto;padding:52px;box-shadow:0 5px 30px #152d5025;box-sizing:border-box}" +
        "p{line-height:1.5;white-space:pre-wrap;overflow-wrap:break-word;margin:0 0 11px}table{width:100%;border-collapse:collapse;margin:12px 0}" +
        "td{border:1px solid #d0d8e2;padding:8px;vertical-align:top}img{max-width:100%;height:auto}" +
        ".slide{width:calc(100% - 24px);position:relative;margin:12px;box-shadow:0 8px 22px #18345230;overflow:hidden;border-radius:7px}" +
        "@media(max-width:680px){article{margin:0;padding:22px}}</style></head><body>"
    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    private fun esc(text: String): String = buildString {
        text.forEach { c -> append(when(c) {
            '&' -> "&amp;"; '<' -> "&lt;"; '>' -> "&gt;"; '"' -> "&quot;"; '\'' -> "&#39;"; else -> c.toString()
        }) }
    }
    private fun elements(node: Node?, name: String): List<Element> {
        val found = (node as? Element)?.getElementsByTagNameNS("*", name) ?: return emptyList()
        return (0 until found.length).mapNotNull { found.item(it) as? Element }
    }
    private fun first(node: Node?, name: String) = elements(node, name).firstOrNull()
    private fun kids(node: Node?): List<Element> {
        val children = node?.childNodes ?: return emptyList()
        return (0 until children.length).mapNotNull { children.item(it) as? Element }
    }
    private fun kid(node: Node?, name: String) = kids(node).firstOrNull { it.localName == name }
    private fun attr(node: Element?, name: String): String {
        if (node == null) return ""
        if (node.hasAttribute(name)) return node.getAttribute(name)
        val all = node.attributes
        for (index in 0 until all.length) {
            if (all.item(index).localName == name) return all.item(index).nodeValue.orEmpty()
        }
        return ""
    }
    private fun xml(zip: ZipFile, part: String): org.w3c.dom.Document? {
        val entry = zip.getEntry(part) ?: return null
        require(entry.size >= 0 && entry.size <= 12_000_000) { "Document XML prea mare" }
        // Android's bundled DOM implementation differs between API levels. Some
        // parsers reject optional factory features; safely reject DTDs ourselves,
        // then request every supported XXE safeguard independently.
        val bytes = zip.getInputStream(entry).use { it.readBytes() }
        require(bytes.size <= 12_000_000) { "Document XML prea mare" }
        val source = String(bytes, Charsets.UTF_8)
        require(!source.contains("<!DOCTYPE", true) && !source.contains("<!ENTITY", true)) {
            "Documentul conține declarații XML nepermise."
        }
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { factory.setExpandEntityReferences(false) }
        runCatching { factory.isXIncludeAware = false }
        return java.io.ByteArrayInputStream(bytes).use { factory.newDocumentBuilder().parse(it) }
    }
    private fun normalize(base: String, relative: String): String? {
        if (relative.contains("://") || relative.startsWith("//")) return null
        val raw = if (relative.startsWith("/")) relative.substring(1)
            else base.substringBeforeLast('/') + "/" + relative
        val normalized = mutableListOf<String>()
        for (part in raw.replace('\\', '/').split('/')) {
            if (part.isEmpty() || part == ".") continue
            if (part == "..") {
                if (normalized.isEmpty()) return null
                normalized.removeAt(normalized.lastIndex)
            } else normalized.add(part)
        }
        return normalized.joinToString("/")
    }
    private fun rels(zip: ZipFile, part: String): Map<String, String> {
        val relFile = part.substringBeforeLast('/') + "/_rels/" + part.substringAfterLast('/') + ".rels"
        return elements(xml(zip, relFile)?.documentElement, "Relationship").mapNotNull { node ->
            val id = attr(node, "Id")
            if (attr(node, "TargetMode") == "External" || id.isBlank()) null
            else normalize(part, attr(node, "Target"))?.let { id to it }
        }.toMap()
    }
    private fun embedded(zip: ZipFile, part: String?): String? {
        if (part == null || !(part.startsWith("ppt/media/") || part.startsWith("word/media/"))) return null
        val entry = zip.getEntry(part) ?: return null
        if (entry.size < 0 || entry.size > 5_000_000) return null
        val mime = when (part.substringAfterLast('.').lowercase(Locale.ROOT)) {
            "png" -> "image/png"; "jpeg", "jpg" -> "image/jpeg"
            "gif" -> "image/gif"; "webp" -> "image/webp"; else -> return null
        }
        val bytes = zip.getInputStream(entry).use { it.readBytes() }
        return if (bytes.size > 5_000_000) null
        else "data:" + mime + ";base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
    private fun col(node: Element?, default: String = "#21334B"): String {
        val hex = attr(first(node, "srgbClr"), "val")
        if (hex.matches(Regex("[a-fA-F0-9]{6}"))) return "#" + hex
        return when(attr(first(node, "schemeClr"), "val")) {
            "lt1", "bg1" -> "#FFFFFF"; "dk1", "tx1" -> "#17263D"
            "accent1" -> "#357EC8"; "accent2" -> "#BE685A"
            else -> default
        }
    }
    private fun pct(coordinate: Double, total: Double): String =
        String.format(Locale.US, "%.3f", coordinate * 100.0 / total) + "%"
    private fun fallback(text: String, slides: Boolean): OfficePreview =
        OfficePreview(listOf(HEADER + "<article><h2>Previzualizare limitată</h2><p>" + esc(text) +
            "</p></article></body></html>"), text, slides)

    fun load(doc: StudyDocument, store: LibraryStore): OfficePreview {
        if (doc.kind == "PPT" || doc.kind == "DOC") {
            return fallback("Fișier binar " + doc.kind + " păstrat în bibliotecă. " +
                "Pentru previzualizare cu formatare în MedAtlas, importă varianta " +
                (if (doc.kind == "PPT") "PPTX." else "DOCX."), doc.kind == "PPT")
        }
        return runCatching {
            ZipFile(store.fileOf(doc)).use { zip ->
                require(zip.size() < 2600) { "Prea multe elemente în document." }
                when (doc.kind) {
                    "PPTX" -> powerpoint(zip)
                    "DOCX" -> word(zip)
                    else -> fallback("Tipul de document nu este suportat.", false)
                }
            }
        }.getOrElse {
            fallback("Documentul nu poate fi citit: " +
                (it.localizedMessage ?: "structură incompatibilă"), doc.kind == "PPTX")
        }
    }

    private fun powerpoint(zip: ZipFile): OfficePreview {
        val pres = xml(zip, "ppt/presentation.xml")
        val size = first(pres?.documentElement, "sldSz")
        val slideW = attr(size, "cx").toDoubleOrNull()?.takeIf { it > 0 } ?: 12192000.0
        val slideH = attr(size, "cy").toDoubleOrNull()?.takeIf { it > 0 } ?: 6858000.0
        val presentationRels = rels(zip, "ppt/presentation.xml")
        val ordered = elements(pres?.documentElement, "sldId").mapNotNull { node ->
            val relId = node.getAttributeNS(REL_NS, "id").ifBlank { attr(node, "id") }
            presentationRels[relId]
        }.filter { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
        val fallback = zip.entries().asSequence().map { it.name }.filter {
            Regex("ppt/slides/slide[0-9]+\\.xml").matches(it)
        }.sortedBy { it.substringAfter("slide").substringBefore('.').toIntOrNull() ?: 0 }.toList()
        val slideParts = ordered.ifEmpty { fallback }.distinct().take(300)
        require(slideParts.isNotEmpty()) { "Nu există diapozitive." }
        val pages = slideParts.map { part ->
            val root = xml(zip, part)?.documentElement
            val relations = rels(zip, part)
            val shapeTree = first(root, "spTree")
            val bg = kid(first(root, "bg"), "bgPr")?.let { col(it, "#FFFFFF") } ?: "#FFFFFF"
            val shapeHtml = StringBuilder()
            for (shape in kids(shapeTree)) {
                val xfrm = first(shape, "xfrm")
                val off = kid(xfrm, "off")
                val ext = kid(xfrm, "ext")
                val x = attr(off, "x").toDoubleOrNull()
                val y = attr(off, "y").toDoubleOrNull()
                val w = attr(ext, "cx").toDoubleOrNull()
                val h = attr(ext, "cy").toDoubleOrNull()
                if (x == null || y == null || w == null || h == null || w <= 0 || h <= 0) continue
                val pos = "position:absolute;left:" + pct(x, slideW) + ";top:" + pct(y, slideH) +
                    ";width:" + pct(w, slideW) + ";height:" + pct(h, slideH) + ";box-sizing:border-box;"
                if (shape.localName == "pic") {
                    val blip = first(shape, "blip")
                    val ref = blip?.getAttributeNS(REL_NS, "embed")?.ifEmpty { attr(blip, "embed") }
                    val src = embedded(zip, relations[ref])
                    if (src != null) shapeHtml.append("<img alt='Imagine prezentare' style='")
                        .append(pos).append("object-fit:contain' src='").append(src).append("'/>")
                } else if (shape.localName == "sp") {
                    val spPr = kid(shape, "spPr")
                    val fill = kid(spPr, "solidFill")?.let { col(it) } ?: "transparent"
                    val border = kid(spPr, "ln")?.let { "border:1px solid " + col(it, "#A8B0C0") + ";" } ?: ""
                    val ellipse = attr(first(spPr, "prstGeom"), "prst") == "ellipse"
                    val paragraphs = elements(first(shape, "txBody"), "p")
                    val text = paragraphs.joinToString("<br/>") { paragraph ->
                        elements(paragraph, "t").joinToString("") { esc(it.textContent) }
                    }
                    val props = first(first(shape, "txBody"), "rPr") ?: first(first(shape, "txBody"), "defRPr")
                    val sizePt = attr(props, "sz").toDoubleOrNull()?.div(100.0) ?: 20.0
                    val fontSize = (sizePt / 26.0 * 2.1).coerceIn(.6, 6.0)
                    val font = String.format(Locale.US, "%.2f", fontSize) + "vw"
                    val fontCol = if (props != null) col(props) else "#25344D"
                    shapeHtml.append("<div style='").append(pos).append("overflow:hidden;padding:.25%;")
                        .append("font-size:").append(font).append(";line-height:1.13;color:")
                        .append(fontCol).append(";background:").append(fill).append(';')
                        .append(if (ellipse) "border-radius:50%;" else "")
                        .append(border).append("'>").append(text).append("</div>")
                }
            }
            HEADER + "<div class='slide' style='aspect-ratio:" + slideW + "/" + slideH + ";background:" +
                bg + "'>" + shapeHtml + "</div></body></html>"
        }
        return OfficePreview(pages,
            "PPTX afișat intern. Textul, imaginile și formele simple sunt redate; animațiile, " +
                "graficele, SmartArt și unele elemente de șablon pot diferi.", true)
    }

    private fun word(zip: ZipFile): OfficePreview {
        val root = xml(zip, "word/document.xml")?.documentElement
            ?: error("Lipsește documentul Word.")
        val relations = rels(zip, "word/document.xml")
        val body = first(root, "body") ?: error("Nu există text Word.")
        val html = StringBuilder(HEADER).append("<article>")
        var pictures = 0
        for (block in kids(body).take(6000)) {
            when (block.localName) {
                "p" -> {
                    val p = paragraph(block, zip, relations, pictures)
                    pictures += p.second
                    html.append(p.first)
                }
                "tbl" -> {
                    html.append("<table>")
                    for (row in kids(block).filter { it.localName == "tr" }) {
                        html.append("<tr>")
                        for (cell in kids(row).filter { it.localName == "tc" }) {
                            html.append("<td>")
                            for (p in kids(cell).filter { it.localName == "p" }) {
                                val result = paragraph(p, zip, relations, pictures)
                                pictures += result.second
                                html.append(result.first)
                            }
                            html.append("</td>")
                        }
                        html.append("</tr>")
                    }
                    html.append("</table>")
                }
            }
            if (html.length > 12_000_000) {
                html.append("<p>Conținut suplimentar omis pentru protecția memoriei.</p>")
                break
            }
        }
        html.append("</article></body></html>")
        return OfficePreview(listOf(html.toString()),
            "DOCX afișat în MedAtlas. Stilurile uzuale, tabelele și imaginile sunt păstrate; " +
                "antetele, paginarea exactă și elementele complexe pot diferi.", false)
    }
    private fun paragraph(p: Element, zip: ZipFile, rel: Map<String, String>, previousImages: Int): Pair<String, Int> {
        val ppr = kid(p, "pPr")
        val style = attr(kid(ppr, "pStyle"), "val")
        val level = Regex("Heading([1-3])", RegexOption.IGNORE_CASE)
            .find(style)?.groupValues?.getOrNull(1)
        val tag = if (level != null) "h" + level else "p"
        val align = when (attr(kid(ppr, "jc"), "val")) {
            "center" -> "center"; "right" -> "right"; "both" -> "justify"; else -> "left"
        }
        val result = StringBuilder("<" + tag + " style='text-align:" + align + "'>")
        if (kid(ppr, "numPr") != null) result.append("• ")
        var pictures = 0
        val runs = kids(p).flatMap { if (it.localName == "hyperlink") kids(it) else listOf(it) }
        for (run in runs.filter { it.localName == "r" }) {
            val rpr = kid(run, "rPr")
            val css = StringBuilder()
            if (kid(rpr, "b") != null && attr(kid(rpr, "b"), "val") != "0") css.append("font-weight:bold;")
            if (kid(rpr, "i") != null) css.append("font-style:italic;")
            if (kid(rpr, "u") != null) css.append("text-decoration:underline;")
            val sz = attr(kid(rpr, "sz"), "val").toDoubleOrNull()
            if (sz != null) css.append("font-size:").append((sz / 2).coerceIn(8.0, 50.0)).append("pt;")
            val rgb = attr(kid(rpr, "color"), "val")
            if (Regex("[a-fA-F0-9]{6}").matches(rgb)) css.append("color:#").append(rgb).append(';')
            for (fragment in kids(run)) {
                when (fragment.localName) {
                    "t" -> result.append("<span style='").append(css).append("'>")
                        .append(esc(fragment.textContent)).append("</span>")
                    "tab" -> result.append("&emsp;")
                    "br", "cr" -> result.append("<br/>")
                    "drawing", "pict" -> if (previousImages + pictures < 60) {
                        val blip = first(fragment, "blip")
                        val ref = blip?.getAttributeNS(REL_NS, "embed")?.ifEmpty { attr(blip, "embed") }
                        val src = embedded(zip, rel[ref])
                        if (src != null) {
                            result.append("<img alt='Imagine Word' src='").append(src).append("'/>")
                            pictures++
                        }
                    }
                }
            }
        }
        result.append("</").append(tag).append(">")
        return result.toString() to pictures
    }
}

@Composable
internal fun OfficePreviewScreen(
    document: StudyDocument,
    store: LibraryStore,
    onBack: () -> Unit,
    onTextMode: () -> Unit
) {
    BackHandler(onBack = onBack)
    val preview by produceState<OfficePreview?>(null, document.id) {
        value = withContext(Dispatchers.IO) { OfficeHtml.load(document, store) }
    }
    var page by remember(document.id) { mutableIntStateOf(0) }
    val current = preview?.pages?.getOrNull(page)
    Column(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(
        Color(0xFF09182E), Color(0xFF193B60), Color(0xFF0B1930)
    )))) {
        Surface(shape = RoundedCornerShape(22.dp), color = Color(0xDD183653),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .18f)),
            modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "Înapoi", tint = Color.White)
                    }
                    Icon(if (preview?.slides == true) Icons.Rounded.Slideshow
                        else Icons.Rounded.Description, null, tint = Color(0xFFB2D9FF))
                    Spacer(Modifier.width(10.dp))
                    Text(document.originalName, color = Color.White,
                        fontSize = 17.sp, maxLines = 1, fontWeight = FontWeight.Bold,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    TextButton(onClick = onTextMode) {
                        Text("Text / Nadia AI", color = Color(0xFFC5E4FF), fontSize = 12.sp)
                    }
                }
                preview?.warning?.let {
                    Text(it, color = Color(0xFFC4D4EB), fontSize = 10.sp,
                        lineHeight = 14.sp, modifier = Modifier.padding(8.dp))
                }
            }
        }
        if (current == null) Box(Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF64A6FD))
        } else {
            // The WebView must not reload on every Compose recomposition:
            // that would lose the reading position, zoom and selection in Word.
            // Only navigating to a different slide/document changes the key.
            val previewKey = document.id + ":" + page
            AndroidView(
                factory = { context ->
                    @SuppressLint("SetJavaScriptEnabled")
                    WebView(context).apply {
                        settings.javaScriptEnabled = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.blockNetworkLoads = true
                        settings.domStorageEnabled = false
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        webViewClient = WebViewClient()
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                onReset = null,
                onRelease = { view ->
                    view.stopLoading()
                    view.loadUrl("about:blank")
                    view.destroy()
                },
                update = { view ->
                    if (view.tag != previewKey) {
                        view.tag = previewKey
                        view.loadDataWithBaseURL(null, current, "text/html", "UTF-8", null)
                    }
                }
            )
        }
        if (preview?.slides == true && (preview?.pages?.size ?: 0) > 1) {
            Row(Modifier.fillMaxWidth().padding(10.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { page = (page - 1).coerceAtLeast(0) }, enabled = page > 0) {
                    Icon(Icons.Rounded.ChevronLeft, null); Text("Înapoi")
                }
                Spacer(Modifier.width(12.dp))
                Text((page + 1).toString() + " / " + (preview?.pages?.size ?: 1),
                    color = Color.White)
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = {
                    page = (page + 1).coerceAtMost((preview?.pages?.size ?: 1) - 1)
                }, enabled = page + 1 < (preview?.pages?.size ?: 1)) {
                    Text("Următor"); Icon(Icons.Rounded.ChevronRight, null)
                }
            }
        }
    }
}
