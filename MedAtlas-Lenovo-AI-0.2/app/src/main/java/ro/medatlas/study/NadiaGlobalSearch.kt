package ro.medatlas.study

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ro.medatlas.study.ai.AiClient
import ro.medatlas.study.data.LibraryStore
import ro.medatlas.study.data.StudyDocument
import ro.medatlas.study.data.StudyNote

private val aiGlassBlue = Color(0xFF338DFA)
private val aiWhite = Color(0xFFF1F6FF)
private val aiMuted = Color(0xFFB4C6DE)

/**
 * One search surface for imported PDFs (OCR pages), Office documents and handwritten
 * notebooks with text. AI is opt-in: a search never sends material to the backend.
 * Only small, explicitly selected search evidence snippets are sent on button press.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NadiaGlobalSearch(
    store: LibraryStore,
    documents: List<StudyDocument>,
    onDismiss: () -> Unit,
    onOpenDocument: (StudyDocument) -> Unit,
    onOpenNote: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    var answer by remember { mutableStateOf<String?>(null) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var aiWorking by remember { mutableStateOf(false) }
    val notes = remember { store.notes() }
    val trimmed = query.trim()
    val documentHits by produceState(initialValue = emptyList<StudyDocument>(), trimmed, documents.toList()) {
        value = emptyList()
        if (trimmed.isNotBlank()) {
            value = withContext(Dispatchers.IO) {
                documents.filter { doc ->
                    doc.title.contains(trimmed, true) ||
                    doc.originalName.contains(trimmed, true) ||
                    doc.course.contains(trimmed, true) ||
                    store.containsIndexedText(doc, trimmed)
                }
            }
        }
    }
    val noteHits = if (trimmed.isNotBlank()) notes.filter {
        it.heading.contains(trimmed, true) || it.body.contains(trimmed, true) || it.course.contains(trimmed, true)
    } else emptyList()
    val wide = LocalConfiguration.current.screenWidthDp >= 800

    fun askNadia() {
        if (trimmed.length < 2 || aiWorking) return
        if (!AiClient.configured(context)) {
            aiError = "Configurează conexiunea Nadia AI din Setări."
            return
        }
        val mode = if (tab == 2) "web" else "summary"
        aiError = null
        answer = null
        aiWorking = true
        scope.launch {
            try {
                val evidence = withContext(Dispatchers.IO) {
                    val fromDocs = documentHits.take(5).mapNotNull { doc ->
                        store.searchEvidence(doc, trimmed, 720)?.let { snippet ->
                            "DOCUMENT: ${doc.originalName}\n${snippet}"
                        }
                    }
                    val fromNotes = noteHits.take(3).mapNotNull { note ->
                        note.body.takeIf { it.contains(trimmed, true) }?.let {
                            "NOTIȚĂ: ${note.heading}\n${it.take(400)}"
                        }
                    }
                    (fromDocs + fromNotes).joinToString("\n\n---\n\n").take(5500)
                }
                if (evidence.isBlank() && mode != "web") {
                    aiError = "Nu există fragmente text indexate pentru această căutare. Pentru PDF, extrage întâi textul prin OCR sau caută online."
                } else {
                    val prompt = if (mode == "web") {
                        "Întrebare medicală: ${trimmed}. Verifică online informația și citează sursele. " +
                            (if (evidence.isBlank()) "Nu există fragmente locale indexate." else
                                "Context extras din biblioteca utilizatorului:\n${evidence}")
                    } else {
                        "Întrebare medicală: ${trimmed}. Rezumă EXCLUSIV fragmentele de mai jos. " +
                            "Menționează explicit numele documentului și pagina doar dacă acestea apar în fragment. " +
                            "Nu inventa concluzii și spune ce nu este documentat.\n\n${evidence}"
                    }
                    answer = AiClient.ask(context, mode, prompt, null, "Căutare globală MedAtlas")
                }
            } catch (error: Exception) {
                aiError = error.localizedMessage ?: "Nadia AI nu poate răspunde acum."
            } finally {
                aiWorking = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Surface(
                modifier = Modifier.fillMaxWidth(if (wide) .75f else .96f)
                    .fillMaxHeight(.94f).padding(end = 12.dp, top = 10.dp, bottom = 10.dp),
                color = Color(0xFF102744),
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 26.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = .22f))
            ) {
                Column(
                    Modifier.fillMaxSize().background(Brush.linearGradient(
                        listOf(Color(0xFF122D51), Color(0xFF0F2340), Color(0xFF10263F))
                    )).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = Color(0xFFC7B4FF))
                        Spacer(Modifier.width(9.dp))
                        Text("Căutare · Nadia AI", color = aiWhite, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, "Închide căutarea", tint = aiWhite)
                        }
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; answer = null; aiError = null },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Caută în biblioteca MedAtlas", color = aiMuted) },
                        placeholder = { Text("Ex.: pneumotorax, VEMS, tratament", color = aiMuted) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = aiWhite) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = ""; answer = null }) {
                                Icon(Icons.Rounded.Cancel, "Șterge căutarea", tint = aiMuted)
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = aiWhite, unfocusedTextColor = aiWhite,
                            focusedBorderColor = aiGlassBlue,
                            unfocusedBorderColor = Color.White.copy(alpha = .2f),
                            cursorColor = aiGlassBlue
                        )
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Toate", "Documente", "Web").forEachIndexed { i, label ->
                            FilterChip(
                                selected = tab == i,
                                onClick = { tab = i; answer = null; aiError = null },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color.White.copy(alpha = .08f),
                                    labelColor = aiMuted,
                                    selectedContainerColor = aiGlassBlue,
                                    selectedLabelColor = Color.White
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true, selected = tab == i,
                                    borderColor = Color.White.copy(alpha = .18f),
                                    selectedBorderColor = aiGlassBlue
                                )
                            )
                        }
                    }
                    if (tab != 1) {
                        Button(
                            enabled = trimmed.length >= 2 && !aiWorking,
                            onClick = { askNadia() },
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = aiGlassBlue)
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (tab == 2) "Verifică pe web cu Nadia" else "Generează rezumat din documente")
                        }
                    }
                    if (aiWorking) LinearProgressIndicator(Modifier.fillMaxWidth(), color = aiGlassBlue)
                    if (aiError != null) {
                        Text(aiError.orEmpty(), color = Color(0xFFFFC5C5), fontSize = 12.sp)
                    }
                    if (answer != null) {
                        Surface(
                            color = Color(0xFF233E60),
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = .16f))
                        ) {
                            Column(Modifier.fillMaxWidth().padding(13.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.AutoAwesome, null, tint = Color(0xFFC7B4FF),
                                        modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(7.dp))
                                    Text(if (tab == 2) "Răspuns Nadia · Web" else "Rezumat din fragmente locale",
                                        color = aiWhite, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.height(8.dp))
                                SelectionContainer {
                                    Text(answer.orEmpty().take(9000), color = aiWhite, fontSize = 12.sp, lineHeight = 18.sp)
                                }
                            }
                        }
                    }
                    Text(if (tab == 2) "Documente locale relevante" else "Rezultate în biblioteca ta",
                        color = aiWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    if (trimmed.isBlank()) {
                        Text("Introdu un termen medical pentru a găsi documente și notițe. " +
                            "Nadia AI se activează numai când apeși butonul de generare.",
                            color = aiMuted, fontSize = 12.sp)
                    } else if (documentHits.isEmpty() && noteHits.isEmpty()) {
                        Text("Nu am găsit rezultate locale. PDF-urile scanate pot necesita OCR.",
                            color = aiMuted, fontSize = 12.sp)
                    }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(documentHits, key = { "doc-${it.id}" }) { doc ->
                            Surface(
                                color = Color.White.copy(alpha = .065f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    onDismiss()
                                    onOpenDocument(doc)
                                }
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (doc.kind == "PPTX") Icons.Rounded.Slideshow else Icons.Rounded.Description,
                                        null, tint = if (doc.kind == "PDF") Color(0xFFFF808B) else Color(0xFFF6A16E))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(doc.title, color = aiWhite, fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text("${doc.kind} · ${doc.course}", color = aiMuted, fontSize = 10.sp)
                                    }
                                    Icon(Icons.Rounded.ChevronRight, null, tint = aiMuted)
                                }
                            }
                        }
                        items(noteHits, key = { "note-${it.id}" }) { note: StudyNote ->
                            Surface(color = Color.White.copy(alpha = .065f), shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().clickable { onDismiss(); onOpenNote(note.id) }) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.EditNote, null, tint = Color(0xFFC49CFA))
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(note.heading, color = aiWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("Notiță · ${note.course}", color = aiMuted, fontSize = 10.sp)
                                    }
                                    Icon(Icons.Rounded.ChevronRight, null, tint = aiMuted)
                                }
                            }
                        }
                    }
                    Text("Răspunsurile AI pot conține erori. Verifică întotdeauna cursul și pagina.",
                        color = aiMuted, fontSize = 10.sp)
                }
            }
        }
    }
}
