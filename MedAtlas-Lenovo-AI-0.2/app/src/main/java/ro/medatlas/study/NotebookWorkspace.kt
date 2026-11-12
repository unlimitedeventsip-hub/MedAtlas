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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ro.medatlas.study.data.*
import ro.medatlas.study.ui.StudyCanvasView
import java.util.UUID

private val patterns = mapOf("LINES" to "Linii", "GRID" to "Caroiaj", "DOTS" to "Puncte")
private val covers = listOf(Color(0xFF315C60), Color(0xFF6C4856), Color(0xFF465775), Color(0xFF796133))
private fun cover(name: String) = covers[Math.floorMod(name.hashCode(), covers.size)]
private fun inkId(id: String) = "notebook-" + UUID.nameUUIDFromBytes(id.toByteArray(Charsets.UTF_8))

private fun paperBitmap(note: StudyNote, legacy: Bitmap?, width: Int): Bitmap {
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
private fun Choice(label: String, options: List<String>, select: (Int) -> Unit) {
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
internal fun NotebookLibrary(store: LibraryStore, docs: List<StudyDocument>, onOpen: (StudyDocument) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("medatlas_editor", 0) }
    var classic by rememberSaveable { mutableStateOf(false) }
    var book by rememberSaveable { mutableStateOf<String?>(null) }
    var pageId by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var naming by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
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
    val active = notes.find { it.id == pageId }
    if (active != null) {
        key(active.id) {
            NotebookPage(active, store, prefs, onBack = { back() },
                onNext = {
                    notes = store.notes()
                    val pages = notes.filter { course(it) == course(active) }
                    val index = pages.indexOfFirst { it.id == active.id }
                    pages.getOrNull(index + 1)?.let { pageId = it.id } ?: addPage(course(active))
                }, onDelete = {
                    store.saveNotes(store.notes().filterNot { it.id == active.id })
                    store.saveInk(inkId(active.id), 0, emptyList())
                    back()
                }, document = docs.find { it.id == active.documentId }, onOpen = onOpen)
        }
        return
    }
    val groups = notes.groupBy(::course)
    val matching = if (search.isNotBlank()) notes.filter {
        listOf(course(it), it.heading, it.body).any { field -> field.contains(search.trim(), ignoreCase = true) }
    } else groups[book].orEmpty()
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (book != null) TextButton(onClick = { book = null; search = "" }) { Text("‹ Caiete") }
            Text(book ?: "Caietele mele", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { classic = true }) { Text("Carnet clasic") }
            Button(onClick = { val folder = book; if (folder == null) { name = ""; naming = true } else addPage(folder) }) {
                Text(if (book == null) "+ Caiet" else "+ Pagină")
            }
        }
        OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Caută în toate caietele") })
        if (search.isBlank() && book == null) {
            if (groups.isEmpty()) Text("Creează un caiet pentru prima ta pagină.")
            LazyVerticalGrid(GridCells.Adaptive(190.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(groups.keys.sorted(), key = { it }) { title ->
                    Column(Modifier.fillMaxWidth().background(cover(title), RoundedCornerShape(12.dp))
                        .clickable { book = title }.padding(start = 12.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("MEDATLAS   /   NADIA", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(32.dp))
                            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("${groups[title]?.size} pagini", color = Color.White)
                        }
                    }
                }
            }
        } else {
            if (matching.isEmpty()) Text("Nicio pagină găsită.")
            LazyVerticalGrid(GridCells.Adaptive(185.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(matching, key = { it.id }) { note ->
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
    }
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
        val view = StudyCanvasView(context)
        view.bind(paper, strokes, Tool.PEN, 0, 2f, false)
        view.layout(0, 0, 650, 450)
        val thumb = Bitmap.createBitmap(260, 180, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(thumb)
        canvas.scale(.4f, .4f)
        view.draw(canvas)
        image = thumb
    }
    Row(Modifier.fillMaxWidth().height(90.dp).background(Color(0xffe9eeea)), verticalAlignment = Alignment.CenterVertically) {
        image?.let { Image(it.asImageBitmap(), "Previzualizare manuscris", Modifier.weight(1f).fillMaxHeight()) }
        if (note.body.isNotBlank()) Text(note.body, Modifier.weight(1f).padding(6.dp), maxLines = 4,
            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun NotebookPage(original: StudyNote, store: LibraryStore, prefs: SharedPreferences,
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
            TextButton(onClick = { flush(); onNext() }) { Text("Următoarea ›") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
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
            document?.let { TextButton(onClick = { flush(); onOpen(it) }) { Text("Curs asociat") } }
            TextButton(onClick = { delete = true }) { Text("Șterge pagina") }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val ink: @Composable (Modifier) -> Unit = { modifier ->
                AndroidView(factory = { ctx -> StudyCanvasView(ctx).also { view = it } }, modifier = modifier, update = {
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
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Ștergi pagina?") },
        text = { Text(title) }, confirmButton = { TextButton(onClick = { delete = false; onDelete() }) { Text("Șterge") } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text("Păstrează") } })
}
