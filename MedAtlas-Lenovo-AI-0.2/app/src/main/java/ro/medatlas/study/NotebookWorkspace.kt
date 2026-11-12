package ro.medatlas.study

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.withContext
import ro.medatlas.study.data.*
import ro.medatlas.study.ui.StudyCanvasView
import java.util.UUID

private val patterns = mapOf("LINES" to "Linii", "GRID" to "Caroiaj", "DOTS" to "Puncte")
private val covers = listOf(Color(0xFF315C60), Color(0xFF6C4856), Color(0xFF465775), Color(0xFF796133),
    Color(0xFF536A4D), Color(0xFF79617E), Color(0xFF5B6772), Color(0xFF9B5C5F))
private fun cover(name: String) = covers[Math.floorMod(name.hashCode(), covers.size)]
internal fun inkId(id: String) = "notebook-" + UUID.nameUUIDFromBytes(id.toByteArray(Charsets.UTF_8))

internal fun paperBitmap(note: StudyNote, legacy: Bitmap?, width: Int): Bitmap {
    val height = if (legacy == null) width * 5 / 4 else
        (width * legacy.height.toFloat() / legacy.width).toInt().coerceIn(1, width * 4)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    if (legacy != null) {
        canvas.drawBitmap(legacy, null, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
        return bitmap
    }
    canvas.drawColor(if (note.darkPaper) 0xff223039.toInt() else 0xfffffcf5.toInt())
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (note.darkPaper) 0xff4b6068.toInt() else 0xffcbd8d4.toInt()
        strokeWidth = width / 720f
    }
    val step = (width / 24).coerceAtLeast(1)
    for (y in step until height step step) {
        if (note.paper == "DOTS") {
            for (x in step until width step step) canvas.drawCircle(x.toFloat(), y.toFloat(), width / 400f, paint)
        } else canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), paint)
    }
    if (note.paper == "GRID") for (x in step until width step step)
        canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), paint)
    return bitmap
}

@Composable
internal fun Choice(label: String, options: List<String>, select: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(label, maxLines = 1) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { i, item ->
                DropdownMenuItem(text = { Text(item) }, onClick = { expanded = false; select(i) })
            }
        }
    }
}

@Composable
internal fun NotebookSettings(prefs: SharedPreferences) {
    var paper by remember { mutableStateOf(prefs.getString("notebook_paper", "LINES")) }
    var dark by remember { mutableStateOf(prefs.getBoolean("notebook_dark", false)) }
    var finger by remember { mutableStateOf(prefs.getBoolean("notebook_finger", true)) }
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Caiete · pagini noi", fontWeight = FontWeight.Bold)
            Choice(patterns[paper] ?: "Linii", patterns.values.toList()) {
                paper = patterns.keys.elementAt(it)
                prefs.edit().putString("notebook_paper", paper).apply()
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hârtie închisă", Modifier.weight(1f))
                Switch(dark, { dark = it; prefs.edit().putBoolean("notebook_dark", it).apply() })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Desenează și cu degetul", Modifier.weight(1f))
                Switch(finger, { finger = it; prefs.edit().putBoolean("notebook_finger", it).apply() })
            }
        }
    }
}

@Composable
internal fun NotebookLibrary(store: LibraryStore, docs: List<StudyDocument>, onOpen: (StudyDocument) -> Unit,
    unified: Boolean = false, onManage: () -> Unit = {}) {
    val context = LocalContext.current
    val exportScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("medatlas_editor", 0) }
    var boards by remember { mutableStateOf(store.boards()) }
    var boardId by rememberSaveable { mutableStateOf<String?>(null) }
    var section by rememberSaveable { mutableStateOf("Toate") }
    var newBoard by rememberSaveable { mutableStateOf(false) }
    var classic by rememberSaveable { mutableStateOf(false) }
    var book by rememberSaveable { mutableStateOf<String?>(null) }
    var pageId by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var naming by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var customizeCover by rememberSaveable { mutableStateOf<String?>(null) }
    var coverVersion by remember { mutableIntStateOf(0) }
    var notes by remember(classic) { mutableStateOf(store.notes()) }
    fun course(n: StudyNote) = n.course.ifBlank { docs.find { it.id == n.documentId }?.course.orEmpty().ifBlank { "General" } }
    fun addPage(folder: String) {
        val note = StudyNote(documentId = "medical-notebook", page = 0, heading = "Pagina " +
            (notes.count { course(it) == folder } + 1), body = "", course = folder,
            paper = prefs.getString("notebook_paper", "LINES") ?: "LINES", darkPaper = prefs.getBoolean("notebook_dark", false))
        store.saveNotes(store.notes() + note)
        notes = store.notes()
        book = folder
        search = ""
        pageId = note.id
    }
    fun back() {
        when {
            classic -> classic = false
            pageId != null -> { pageId = null; notes = store.notes() }
            search.isNotBlank() -> search = ""
            else -> book = null
        }
    }
    BackHandler(classic || pageId != null || book != null || search.isNotBlank()) { back() }
    if (classic) {
        Column {
            TextButton(onClick = { back() }) { Text("‹ Caiete") }
            NotesScreen(store, docs, onOpen)
        }
        return
    }
    boards.find { it.id == boardId }?.let { board ->
        key(board.id) { BoardEditor(board, store) { boardId = null; boards = store.boards() } }
        return
    }
    val active = notes.find { it.id == pageId }
    if (active != null) {
        key(active.id) {
            val folder = course(active)
            val pages = notes.filter { course(it) == folder }
            val index = pages.indexOfFirst { it.id == active.id }
            fun move(delta: Int) {
                val neighbor = pages.getOrNull(index + delta) ?: return
                val latest = store.notes().toMutableList()
                val from = latest.indexOfFirst { it.id == active.id }
                val to = latest.indexOfFirst { it.id == neighbor.id }
                if (from >= 0 && to >= 0) {
                    java.util.Collections.swap(latest, from, to)
                    store.saveNotes(latest)
                    notes = latest
                }
            }
            NotebookPage(active, store, prefs, onBack = { back() },
                position = index + 1, count = pages.size, move = ::move,
                onPrevious = { notes = store.notes(); pages.getOrNull(index - 1)?.let { pageId = it.id } },
                onNext = {
                    notes = store.notes()
                    pages.getOrNull(index + 1)?.let { pageId = it.id } ?: addPage(folder)
                }, onDelete = {
                    store.saveNotes(store.notes().filterNot { it.id == active.id })
                    store.saveInk(inkId(active.id), 0, emptyList())
                    back()
                }, document = docs.find { it.id == active.documentId }, onOpen = onOpen)
        }
        return
    }
    val groups = notes.groupBy(::course)
    var exportFolder by rememberSaveable { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val target = exportFolder
        if (uri != null && target != null) {
            val snapshot = store.notes().filter { course(it) == target }
            exporting = true
            exportScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        requireNotNull(context.contentResolver.openOutputStream(uri)).use {
                            exportNotebook(context, store, snapshot, it)
                        }
                    }
                    android.widget.Toast.makeText(context, "Caiet exportat", android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Export nereușit: " + e.localizedMessage, android.widget.Toast.LENGTH_LONG).show()
                } finally { exporting = false }
            }
        }
    }
    val home = unified && book == null
    val showDocs = home && section in listOf("Toate", "Documente")
    val showBoards = home && section in listOf("Toate", "Planșe")
    val showNotes = !home || section in listOf("Toate", "Caiete")
    val query = search.trim()
    val fileHits by produceState<Set<String>>(emptySet(), query, docs.toList(), showDocs) {
        value = emptySet()
        if (showDocs && query.isNotEmpty()) {
            delay(180)
            val snapshot = docs.toList()
            value = withContext(Dispatchers.IO) {
                snapshot.filter { store.studyText(it).contains(query, true) }.map { it.id }.toSet()
            }
        }
    }
    val files = if (showDocs) docs.filter { it.title.contains(query,true) || it.course.contains(query,true) ||
        it.kind.contains(query,true) || it.id in fileHits } else emptyList()
    val visibleBoards = if (showBoards) boards.filter { it.heading.contains(query,true) } else emptyList()
    val matching = if (search.isNotBlank()) notes.filter {
        listOf(course(it), it.heading, it.body).any { field -> field.contains(search.trim(), ignoreCase = true) }
    } else groups[book].orEmpty()
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (book != null) TextButton(onClick = { book = null; search = "" }) { Text("‹ Caiete") }
            Text(book ?: if (unified) "Biblioteca mea" else "Caietele mele", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            if (book != null) TextButton(enabled = !exporting, onClick = {
                exportFolder = book
                exportPicker.launch((book ?: "Caiet").replace('/', '-') + ".pdf")
            }) { Text(if (exporting) "Se exportă…" else "Export PDF") }
            if (home) TextButton(onClick = { onManage() }) { Text("Organizează fișiere") }
            if (!home) TextButton(onClick = { classic = true }) { Text("Carnet clasic") }
            Button(onClick = { val folder = book; if (folder == null) { name = ""; naming = true } else addPage(folder) }) {
                Text(if (book == null) "+ Caiet" else "+ Pagină")
            }
        }
        if (home) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Toate", "Caiete", "Planșe", "Documente").forEach { option ->
                FilterChip(section == option, { section = option }, { Text(option) })
            }
            OutlinedButton(onClick = { name = ""; newBoard = true }) { Text("+ Planșă") }
        }
        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(if (home) "Caută în bibliotecă" else "Caută în toate caietele") })
        val showCovers = showNotes && book == null && search.isBlank()
        val pages = if (showNotes && !showCovers) matching else emptyList()
        if ((!showCovers || groups.isEmpty()) && pages.isEmpty() && files.isEmpty() && visibleBoards.isEmpty())
            Text(if (query.isEmpty()) "Adaugă primul material." else "Niciun rezultat.")
        LazyVerticalGrid(GridCells.Adaptive(190.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(files, key = { "file-" + it.id }) { doc ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(doc) }) {
                    Column(Modifier.padding(14.dp)) {
                        Text(doc.kind, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(12.dp))
                        Text(doc.title, fontWeight = FontWeight.Bold)
                        Text(doc.course, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            items(visibleBoards, key = { "board-" + it.id }) { board ->
                Card(Modifier.fillMaxWidth().clickable { boardId = board.id }) {
                    Column(Modifier.padding(14.dp)) {
                        Text("PLANȘĂ", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(12.dp))
                        Text(board.heading, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (showCovers) {
                items(groups.keys.sorted(), key = { "course-" + it }) { title ->
                    val savedColor = remember(title, coverVersion) {
                        Color(prefs.getInt("notebook_cover::" + title, cover(title).toArgb()))
                    }
                    Column(Modifier.fillMaxWidth().background(savedColor, RoundedCornerShape(12.dp))
                        .clickable { book = title }.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("MEDATLAS  /  NADIA", color = Color.White.copy(alpha = .85f),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f))
                            IconButton(onClick = { customizeCover = title }) {
                                Icon(Icons.Rounded.Palette, contentDescription = "Schimbă coperta",
                                    tint = Color.White)
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(title, color = Color.White, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(5.dp))
                        Text("${groups[title]?.size} pagini", color = Color.White.copy(alpha=.9f),
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            items(pages, key = { "page-" + it.id }) { note ->
                    Card(Modifier.fillMaxWidth().clickable { pageId = note.id }) {
                        PagePreview(note, store)
                        Column(Modifier.padding(12.dp)) {
                            Text(note.heading, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(course(note), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
        }
    }
    customizeCover?.let { title ->
        AlertDialog(onDismissRequest = { customizeCover = null },
            title = { Text("Coperta caietului · " + title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Alege o culoare. Coperta este salvată local și rămâne după redeschidere.",
                        style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        covers.forEach { shade ->
                            Surface(shape = RoundedCornerShape(10.dp), color = shade,
                                modifier = Modifier.size(46.dp).clickable {
                                    prefs.edit().putInt("notebook_cover::" + title, shade.toArgb()).apply()
                                    coverVersion++
                                    customizeCover = null
                                }) {}
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { customizeCover = null }) { Text("Închide") } })
    }
    if (newBoard) AlertDialog(onDismissRequest = { newBoard = false }, title = { Text("Planșă nouă") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Numele planșei") }) },
        confirmButton = { Button(enabled = name.isNotBlank(), onClick = {
            val board = StudyNote(documentId = "board", page = 0, heading = name.trim(), body = "")
            store.saveBoards(store.boards() + board); boards = store.boards()
            boardId = board.id; newBoard = false; section = "Planșe"; search = ""
        }) { Text("Creează planșa") } }, dismissButton = { TextButton(onClick = { newBoard = false }) { Text("Anulează") } })
    if (naming) AlertDialog(onDismissRequest = { naming = false }, title = { Text("Caiet nou") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Numele caietului") }) },
        confirmButton = { Button(enabled = name.isNotBlank() && name.trim() !in groups, onClick = {
            naming = false; addPage(name.trim())
        }) { Text("Creează caietul") } }, dismissButton = { TextButton(onClick = { naming = false }) { Text("Anulează") } })
}

@Composable
private fun PagePreview(note: StudyNote, store: LibraryStore) {
    val context = LocalContext.current
    var image by remember(note) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(note) {
        val (paper, strokes) = withContext(Dispatchers.IO) {
            paperBitmap(note, store.notebookDrawing(note.handwritingFile), 180) to store.ink(inkId(note.id), 0)
        }
        image = withContext(Dispatchers.Main.immediate) {
            val view = StudyCanvasView(context)
            view.bind(paper, strokes, Tool.PEN, 0, 2f, false)
            view.smartMarker = false
            view.layout(0, 0, 650, 450)
            val thumb = Bitmap.createBitmap(260, 180, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(thumb)
            canvas.scale(.4f, .4f)
            view.draw(canvas)
            thumb
        }
    }
    Row(Modifier.fillMaxWidth().height(90.dp).background(Color(0xffe9eeea)), verticalAlignment = Alignment.CenterVertically) {
        image?.let { Image(it.asImageBitmap(), "Previzualizare manuscris", Modifier.weight(1f).fillMaxHeight()) }
        if (note.body.isNotBlank()) Text(note.body, Modifier.weight(1f).padding(6.dp), maxLines = 4,
            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun NotebookPage(original: StudyNote, store: LibraryStore, prefs: SharedPreferences,
    position: Int, count: Int, move: (Int) -> Unit, onPrevious: () -> Unit,
    onBack: () -> Unit, onNext: () -> Unit, onDelete: () -> Unit, document: StudyDocument?, onOpen: (StudyDocument) -> Unit) {
    var title by rememberSaveable { mutableStateOf(original.heading) }
    var body by rememberSaveable { mutableStateOf(original.body) }
    var paper by rememberSaveable { mutableStateOf(original.paper) }
    var dark by rememberSaveable { mutableStateOf(original.darkPaper) }
    var saved by remember { mutableStateOf(original) }
    fun flush() {
        val revised = saved.copy(heading = title.ifBlank { "Pagină fără titlu" }, body = body, paper = paper, darkPaper = dark)
        if (revised == saved) return
        val all = store.notes()
        val updated = revised.copy(updatedAt = System.currentTimeMillis())
        if (all.any { it.id == updated.id }) store.saveNotes(all.map { if (it.id == updated.id) updated else it })
        saved = updated
    }
    LaunchedEffect(title, body, paper, dark) { delay(400); flush() }
    DisposableEffect(Unit) { onDispose { flush() } }
    BackHandler { flush(); onBack() }
    var tool by rememberSaveable { mutableStateOf(Tool.PEN) }
    val toolNames = mapOf(Tool.PEN to "Fineliner", Tool.BRUSH to "Pensulă", Tool.HIGHLIGHT to "Marker",
        Tool.CIRCLE to "Elipsă", Tool.RECTANGLE to "Dreptunghi", Tool.ARROW to "Săgeată",
        Tool.LASSO to "Lasou", Tool.ERASER to "Radieră", Tool.PAN to "Deplasare")
    val colors = mapOf("Grafit" to Color(0xff263943), "Petrol" to Color(0xff258b86),
        "Trandafiriu" to Color(0xffd05b82), "Auriu" to Color(0xffe5ad43), "Fildeș" to Color(0xfffff8e8))
    var color by rememberSaveable { mutableStateOf(if (dark) "Fildeș" else "Grafit") }
    var width by rememberSaveable { mutableIntStateOf(2) }
    var delete by rememberSaveable { mutableStateOf(false) }
    var strokes by remember { mutableStateOf(store.ink(inkId(original.id), 0)) }
    var recognition by remember { mutableStateOf<List<Stroke>?>(null) }
    var view by remember { mutableStateOf<StudyCanvasView?>(null) }
    val bitmap by produceState<Bitmap?>(null, paper, dark) {
        value = withContext(Dispatchers.IO) {
            paperBitmap(original.copy(paper = paper, darkPaper = dark), store.notebookDrawing(original.handwritingFile), 840)
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { flush(); onBack() }) { Text("‹ Pagini") }
            OutlinedTextField(title, { title = it }, Modifier.weight(1f), singleLine = true, label = { Text("Titlul paginii") })
            if (position > 1) TextButton(onClick = { flush(); onPrevious() }) { Text("‹") }
            Text("$position / $count", style = MaterialTheme.typography.labelSmall)
            TextButton(onClick = { flush(); onNext() }) { Text(if (position == count) "+ Pagină" else "Următoarea ›") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            TextButton(enabled = bitmap != null && (original.handwritingFile.isNotBlank() || strokes.any { it.tool == Tool.PEN || it.tool == Tool.BRUSH }), onClick = {
                recognition = (view?.selectedInk() ?: strokes).filter { it.tool == Tool.PEN || it.tool == Tool.BRUSH }
            }) { Text("Transcrie") }
            Choice(toolNames.getValue(tool), toolNames.values.toList()) { tool = toolNames.keys.elementAt(it) }
            Choice(color, colors.keys.toList()) { color = colors.keys.elementAt(it) }
            Choice("$width · grosime", listOf("Subțire", "Medie", "Groasă")) { width = listOf(2, 4, 7)[it] }
            if (original.handwritingFile.isBlank()) {
                Choice(patterns[paper] ?: "Linii", patterns.values.toList()) { paper = patterns.keys.elementAt(it) }
                TextButton(onClick = { dark = !dark; color = if (dark) "Fildeș" else "Grafit" }) { Text(if (dark) "Hârtie deschisă" else "Hârtie închisă") }
            }
            TextButton(onClick = { view?.undo() }) { Text("Anulează trasarea") }
            TextButton(onClick = { view?.redo() }) { Text("Refă") }
            if (tool == Tool.LASSO) TextButton(onClick = { view?.eraseSelection() }) { Text("Șterge selecția") }
            if (position > 1) TextButton(onClick = { flush(); move(-1) }) { Text("Mută mai devreme") }
            if (position < count) TextButton(onClick = { flush(); move(1) }) { Text("Mută mai târziu") }
            document?.let { TextButton(onClick = { flush(); onOpen(it) }) { Text("Curs asociat") } }
            TextButton(onClick = { delete = true }) { Text("Șterge pagina") }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val ink: @Composable (Modifier) -> Unit = { modifier ->
                AndroidView(factory = { ctx -> StudyCanvasView(ctx).also { view = it } }, modifier = modifier.testTag("notebook-ink"), update = {
                    it.pageBitmap = bitmap
                    it.tool = tool
                    it.inkColor = colors.getValue(color).toArgb()
                    it.penWidth = width.toFloat()
                    it.fingerInk = prefs.getBoolean("notebook_finger", true)
                    it.smartMarker = false
                    it.marks = strokes
                    it.onSave = { marks ->
                        store.saveInk(inkId(original.id), 0, marks)
                        strokes = marks
                    }
                })
            }
            val text: @Composable (Modifier) -> Unit = { modifier ->
                OutlinedTextField(body, { body = it }, modifier, label = { Text("Textul paginii") })
            }
            if (maxWidth >= 600.dp) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ink(Modifier.weight(1.5f).fillMaxHeight()); text(Modifier.weight(1f).fillMaxHeight())
            } else Column(Modifier.fillMaxSize()) {
                ink(Modifier.weight(1.5f).fillMaxWidth()); text(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
    recognition?.let { input -> bitmap?.let { page ->
        TranscriptionDialog(input, page, onAdd = {
            body = if (body.isBlank()) it else body.trimEnd() + "\n" + it
            flush(); recognition = null
        }, close = { recognition = null })
    } }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Ștergi pagina?") },
        text = { Text(title) }, confirmButton = { TextButton(onClick = { delete = false; onDelete() }) { Text("Șterge") } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text("Păstrează") } })
}
