@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package ro.medatlas.study

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ro.medatlas.study.data.*
import ro.medatlas.study.study.StudyEngine
import ro.medatlas.study.ui.StudyCanvasView
import ro.medatlas.study.ui.HandwritingPadView
import ro.medatlas.study.ui.PremiumIntro
import ro.medatlas.study.pdf.AnnotatedPdfExporter
import ro.medatlas.study.ai.AiClient
import ro.medatlas.study.classroom.ClassroomActivity
import ro.medatlas.study.simulation.ClinicalSimulatorActivity
import ro.medatlas.study.simulation.ExamSimulatorActivity
import ro.medatlas.study.anatomy.Anatomy3DActivity
import ro.medatlas.study.anatomy.OrganAtlasActivity
import ro.medatlas.study.reference.MedicalReferenceActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Navy = Color(0xFF182535)
private val DeepNavy = Color(0xFF0E1928)
private val Rose = Color(0xFFD58EA2)
private val Ivory = Color(0xFFFAF8F4)
private val Slate = Color(0xFF718092)
private val Teal = Color(0xFF38786E)
private val PaleRose = Color(0xFFFFEFF2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(250, 248, 244)
        window.navigationBarColor = android.graphics.Color.rgb(250, 248, 244)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Teal, secondary = Rose, surface = Ivory, background = Ivory, onBackground = Navy, onSurface = Navy)) {
                MedAtlasApp()
            }
        }
    }
}

@Composable
private fun MedAtlasApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { LibraryStore(context) }
    val docs = remember { mutableStateListOf<StudyDocument>().apply { addAll(store.documents()) } }
    var selected by remember { mutableStateOf<StudyDocument?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val introPrefs = remember { context.getSharedPreferences("medatlas_editor", android.content.Context.MODE_PRIVATE) }
    var showIntro by rememberSaveable {
        mutableStateOf(introPrefs.getBoolean("show_premium_intro", true))
    }
    var search by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch {
            val outcomes = withContext(Dispatchers.IO) {
                uris.map { uri -> runCatching { store.import(uri) } }
            }
            docs.clear(); docs.addAll(store.documents())
            val failures = outcomes.mapNotNull { it.exceptionOrNull()?.localizedMessage }
            if (failures.isNotEmpty()) importError = failures.first()
        }
    }
    if (showIntro) {
        PremiumIntro(onFinish = { showIntro = false })
        return
    }
    selected?.let { doc ->
        ReaderScreen(doc, store, onBack = { selected = null }, onRemove = {
            store.removeDocument(doc.id); docs.removeAll { it.id == doc.id }; selected = null
        })
        return
    }
    Scaffold(
        containerColor = Ivory,
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 5.dp) {
                val options = listOf(Triple("Bibliotecă", Icons.Rounded.FolderOpen, 0), Triple("Notițe", Icons.Rounded.EditNote, 1), Triple("Studiu", Icons.Rounded.School, 2), Triple("Classroom", Icons.Rounded.Class, 4), Triple("Setări", Icons.Rounded.Settings, 3))
                options.forEach { (name, icon, number) ->
                    NavigationBarItem(selected = tab == number, onClick = {
                        if (number == 4) context.startActivity(Intent(context, ClassroomActivity::class.java))
                        else tab = number
                    }, icon = { Icon(icon, name) }, label = { Text(name, fontSize = 11.sp) })
                }
            }
        },
        topBar = {
            Surface(color = Ivory, modifier = Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MenuBook, null, tint = Teal, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text("MedAtlas", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Navy)
                        Text("Nadia's Medical Workspace", fontSize = 11.sp, color = Slate)
                    }
                    if (tab == 0) {
                        FilledTonalButton(onClick = { picker.launch(arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.openxmlformats-officedocument.presentationml.presentation")) }) {
                            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(5.dp)); Text("Importă")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> LibraryScreen(docs, search, onSearch = { search = it }, onOpen = { selected = it }, onCourse = { doc, course ->
                    val changed = doc.copy(course = course.trim().ifBlank { "Fără materie" })
                    store.saveDocuments(store.documents().map { if (it.id == doc.id) changed else it })
                    val i = docs.indexOfFirst { it.id == doc.id }; if (i >= 0) docs[i] = changed
                }, onImport = { picker.launch(arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.openxmlformats-officedocument.presentationml.presentation")) })
                1 -> NotesScreen(store, docs, onOpen = { selected = it })
                2 -> CardsScreen(store, docs, onOpen = { selected = it })
                else -> SettingsScreen(onReplayIntro = { showIntro = true })
            }
        }
    }
    if (importError != null) AlertDialog(onDismissRequest = { importError = null }, title = { Text("Import imposibil") }, text = { Text(importError ?: "") }, confirmButton = { TextButton(onClick = { importError = null }) { Text("Închide") } })
}

@Composable
private fun LibraryScreen(docs: List<StudyDocument>, search: String, onSearch: (String) -> Unit, onOpen: (StudyDocument) -> Unit, onCourse: (StudyDocument, String) -> Unit, onImport: () -> Unit) {
    var editDoc by remember { mutableStateOf<StudyDocument?>(null) }
    var courseInput by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Surface(shape = RoundedCornerShape(24.dp), color = Navy, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(19.dp)) {
                Text("Bună, Nadia ♡", color = Color(0xFFF4BAC7), fontSize = 14.sp)
                Text("Ce învățăm astăzi?", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(14.dp))
                Text("Cursurile, notițele și adnotările sunt păstrate local pe tabletă.", color = Color(0xFFB9C6D5), fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(search, onSearch, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Caută după titlu sau materie") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, shape = RoundedCornerShape(15.dp))
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Biblioteca mea", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${docs.size} materiale", color = Slate, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        val filtered = docs.filter { it.title.contains(search, ignoreCase = true) || it.course.contains(search, ignoreCase = true) }
        if (filtered.isEmpty()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.MenuBook, null, tint = Rose, modifier = Modifier.size(60.dp))
                Spacer(Modifier.height(12.dp))
                Text(if (docs.isEmpty()) "Biblioteca este pregătită" else "Nu există rezultate", fontWeight = FontWeight.Bold)
                Text(if (docs.isEmpty()) "Importă primul PDF, Word sau PowerPoint" else "Încearcă alt cuvânt-cheie", color = Slate)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onImport) { Text("Importă un curs") }
            }
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            val groups = filtered.groupBy { it.course }
            groups.forEach { (course, items) ->
                item { Text(course, color = Teal, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp)) }
                items(items, key = { it.id }) { doc ->
                    Surface(Modifier.fillMaxWidth().combinedClickable(onClick = { onOpen(doc) }, onLongClick = { editDoc = doc; courseInput = doc.course }), shape = RoundedCornerShape(15.dp), color = Color.White, tonalElevation = 1.dp) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Description, null, tint = if (doc.kind == "PDF") Rose else Teal, modifier = Modifier.size(29.dp))
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text(doc.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                Text("${doc.kind} • ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(doc.importedAt))}", fontSize = 11.sp, color = Slate)
                            }
                            Icon(Icons.Rounded.ChevronRight, null, tint = Slate)
                        }
                    }
                }
            }
        }
    }
    if (editDoc != null) AlertDialog(onDismissRequest = { editDoc = null }, title = { Text("Organizează materia") }, text = {
        Column {
            Text("${editDoc?.title} • Ține apăsat pe un fișier pentru a schimba materia.", color = Slate, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = courseInput, onValueChange = { courseInput = it }, singleLine = true, label = { Text("Materia / folder") })
        }
    }, confirmButton = { Button(onClick = { editDoc?.let { onCourse(it, courseInput) }; editDoc = null }) { Text("Salvează") } }, dismissButton = { TextButton(onClick = { editDoc = null }) { Text("Anulează") } })
}

@Composable
private fun ReaderScreen(doc: StudyDocument, store: LibraryStore, onBack: () -> Unit, onRemove: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config = LocalConfiguration.current
    val isWide = config.orientation == Configuration.ORIENTATION_LANDSCAPE && config.screenWidthDp >= 700
    val pager = remember(doc.id) { DocumentPager(doc, store) }
    DisposableEffect(pager) { onDispose { pager.close() } }
    var page by rememberSaveable(doc.id) { mutableIntStateOf(0) }
    var bitmap by remember(doc.id) { mutableStateOf<Bitmap?>(null) }
    var loadError by remember(doc.id) { mutableStateOf<String?>(null) }
    var tool by remember { mutableStateOf(Tool.PEN) }
    var color by remember { mutableIntStateOf(AndroidColor.rgb(41, 93, 82)) }
    var width by remember { mutableFloatStateOf(3f) }
    var fingerInk by remember { mutableStateOf(false) }
    // AI stays hidden until the reader explicitly requests it.
    var expanded by rememberSaveable(doc.id) { mutableStateOf(false) }
    var continuous by rememberSaveable(doc.id) { mutableStateOf(true) }
    var documentScale by rememberSaveable(doc.id) { mutableFloatStateOf(1f) }
    val scrollState = rememberLazyListState(initialFirstVisibleItemIndex = page)
    LaunchedEffect(continuous) {
        if (continuous) {
            snapshotFlow { scrollState.firstVisibleItemIndex }.collect { visible ->
                if (page != visible) page = visible
            }
        }
    }

    var crop by remember { mutableStateOf<Bitmap?>(null) }
    var aiAnswer by remember { mutableStateOf<String?>(null) }
    var aiTitle by remember { mutableStateOf("Nadia AI") }
    var aiBusy by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var aiIllustration by remember { mutableStateOf<Bitmap?>(null) }
    fun askSelection(mode: String) {
        val current = crop ?: return
        if (!AiClient.configured(context)) {
            shareImage(context, current, "Explică în limba română această selecție medicală, cu mecanisme, diagnostic și informațiile importante pentru examen.")
            crop = null
            return
        }
        aiBusy = true; aiTitle = if (mode == "explain") "Explicație medicală" else if (mode == "web") "Explicație și surse online" else "Nadia AI"
        scope.launch {
            runCatching { AiClient.ask(context, mode, "Explică informația încercuită din curs.", page, doc.title, current) }
                .onSuccess { aiAnswer = it; crop = null }
                .onFailure { aiError = it.localizedMessage ?: "Nu a fost posibilă conexiunea AI." }
            aiBusy = false
        }
    }
    fun drawSelection() {
        val current = crop ?: return
        if (!AiClient.configured(context)) {
            shareImage(context, current, "Realizează un desen medical explicativ cu etichete, pe baza imaginii; spune ce elemente anatomice trebuie verificate.")
            crop = null
            return
        }
        aiBusy = true
        scope.launch {
            runCatching { AiClient.draw(context, "Ilustrează anatomia și mecanismul explicat în selecția medicală. Document: ${doc.title}, pagina ${page + 1}.", current) }
                .onSuccess { aiIllustration = it; crop = null }
                .onFailure { aiError = it.localizedMessage ?: "Nu s-a generat desenul." }
            aiBusy = false
        }
    }
    var highlightSuggestions by remember { mutableStateOf<List<Stroke>>(emptyList()) }
    var commentAnchor by remember { mutableStateOf<Triple<Int, InkPoint, String>?>(null) }
    var selectedComment by remember { mutableStateOf<PageComment?>(null) }
    var textAnchor by remember { mutableStateOf<Pair<Int, InkPoint>?>(null) }
    var selectedText by remember { mutableStateOf<PageTextBox?>(null) }
    var textBoxesVersion by remember { mutableIntStateOf(0) }
    var commentsVersion by remember { mutableIntStateOf(0) }
    var showNote by remember { mutableStateOf(false) }
    var showAtlas by remember { mutableStateOf(false) }
    var showRemove by remember { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }
    var textVersion by remember { mutableIntStateOf(0) }
    var showOcrLibrary by remember { mutableStateOf(false) }
    var marks by remember(doc.id, page) { mutableStateOf(store.ink(doc.id, page)) }
    var stickers by remember(doc.id, page) { mutableStateOf(store.stickers(doc.id, page)) }
    val stickerCache = remember(doc.id) { mutableMapOf<String, Bitmap>() }
    var canvasView by remember { mutableStateOf<StudyCanvasView?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var imageToCrop by remember { mutableStateOf<Bitmap?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { picked ->
            runCatching {
                context.contentResolver.openInputStream(picked)?.use { input ->
                    BitmapFactory.decodeStream(input)
                } ?: error("Nu am putut citi imaginea")
            }.onSuccess { imageToCrop = it }
             .onFailure { Toast.makeText(context, it.localizedMessage ?: "Import imposibil", Toast.LENGTH_LONG).show() }
        }
    }
    LaunchedEffect(doc.id, page) {
        bitmap = null; loadError = null
        runCatching { withContext(Dispatchers.IO) { pager.render(page) } }
            .onSuccess { bitmap = it }
            .onFailure { loadError = it.localizedMessage ?: "Pagina nu poate fi deschisă" }
    }
    fun runOcr(source: Bitmap?) {
        val b = source ?: return
        processing = true
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        client.process(InputImage.fromBitmap(b, 0))
            .addOnSuccessListener { result ->
                store.savePageText(doc.id, page, result.text)
                textVersion++
                Toast.makeText(context, "OCR salvat. Deschide Text OCR pentru a-l citi și corecta.", Toast.LENGTH_LONG).show()
                showOcrLibrary = true
            }
            .addOnFailureListener { Toast.makeText(context, it.localizedMessage ?: "OCR nereușit", Toast.LENGTH_LONG).show() }
            .addOnCompleteListener { processing = false; client.close() }
    }
    fun suggestImportantLines() {
        val source = bitmap ?: return
        if (processing) return
        processing = true
        val requestedPage = page
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        client.process(InputImage.fromBitmap(source, 0))
            .addOnSuccessListener { result ->
                if (requestedPage != page) return@addOnSuccessListener
                store.savePageText(doc.id, page, result.text)
                textVersion++
                highlightSuggestions = result.textBlocks.flatMap { it.lines }
                    .filter { it.text.length > 18 && it.boundingBox != null }
                    .sortedByDescending { StudyEngine.importance(it.text) }
                    .take(8)
                    .filter { StudyEngine.importance(it.text) >= 2 }
                    .mapNotNull { line ->
                        val r = line.boundingBox ?: return@mapNotNull null
                        Stroke(tool = Tool.UNDERLINE, color = AndroidColor.rgb(211, 122, 147), width = 3.0f,
                            points = listOf(
                                InkPoint((r.left.toFloat() / source.width).coerceIn(0f, 1f), ((r.bottom + 3f) / source.height).coerceIn(0f, 1f)),
                                InkPoint((r.right.toFloat() / source.width).coerceIn(0f, 1f), ((r.bottom + 3f) / source.height).coerceIn(0f, 1f))
                            ))
                    }
                if (highlightSuggestions.isEmpty()) Toast.makeText(context, "Nu au fost identificate sublinieri relevante pe această pagină.", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { Toast.makeText(context, it.localizedMessage ?: "Analiza OCR a eșuat", Toast.LENGTH_LONG).show() }
            .addOnCompleteListener { processing = false; client.close() }
    }
    fun suggestAiHighlights() {
        val source = bitmap ?: return
        if (processing || aiBusy) return
        if (!AiClient.configured(context)) {
            aiError = "Configurează Nadia AI în Setări. Poți folosi până atunci «Propune sublinieri importante» offline."
            return
        }
        processing = true
        scope.launch {
            val result = runCatching {
                val ocr = withContext(Dispatchers.IO) {
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    try { Tasks.await(recognizer.process(InputImage.fromBitmap(source, 0))) }
                    finally { recognizer.close() }
                }
                val lines = ocr.textBlocks.flatMap { it.lines }.filter { it.text.length > 12 && it.boundingBox != null }
                if (lines.isEmpty()) error("Nu s-au detectat rânduri pentru subliniere. Încearcă un PDF cu text lizibil.")
                store.savePageText(doc.id, page, ocr.text)
                val aiText = AiClient.ask(context, "highlights", lines.joinToString("\n") { it.text }.take(12000), page, doc.title)
                val chosen = aiText.lineSequence().map { it.trim() }.filter { it.startsWith("MARK:", ignoreCase = true) }
                    .map { it.substringAfter(':').trim().trim('"', '“', '”') }.filter { it.length >= 12 }.take(8).toList()
                fun normalize(t: String): String = t.lowercase(Locale.ROOT).replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
                lines.filter { ln ->
                    val lineNorm = normalize(ln.text)
                    chosen.any { quote ->
                        val key = normalize(quote)
                        key == lineNorm || (key.length >= 18 && lineNorm.contains(key)) || (lineNorm.length >= 18 && key.contains(lineNorm))
                    }
                }.take(8).mapNotNull { ln ->
                    val r = ln.boundingBox ?: return@mapNotNull null
                    Stroke(tool = Tool.UNDERLINE, color = AndroidColor.rgb(211, 122, 147), width = 3f,
                        points = listOf(
                            InkPoint((r.left.toFloat() / source.width).coerceIn(0f, 1f), ((r.bottom + 3f) / source.height).coerceIn(0f, 1f)),
                            InkPoint((r.right.toFloat() / source.width).coerceIn(0f, 1f), ((r.bottom + 3f) / source.height).coerceIn(0f, 1f))
                        ))
                }
            }
            result.onSuccess { lines ->
                textVersion++
                if (lines.isEmpty()) aiError = "AI-ul a răspuns, dar rândurile alese nu coincid exact cu OCR-ul. Încearcă sublinierea offline sau OCR mai clar."
                else highlightSuggestions = lines
            }.onFailure { aiError = it.localizedMessage ?: "Nu s-au putut genera subliniere AI." }
            processing = false
        }
    }
    fun runBatchOcr() {
        if (doc.kind != "PDF" || processing) return
        processing = true
        scope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    DocumentPager(doc, store).use { workerPager ->
                        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                        try {
                            val last = minOf(workerPager.count, page + 10)
                            var completed = 0
                            for (position in page until last) {
                                val image = workerPager.render(position)
                                try {
                                    val recognized = Tasks.await(recognizer.process(InputImage.fromBitmap(image, 0)))
                                    store.savePageText(doc.id, position, recognized.text)
                                    completed++
                                } finally { image.recycle() }
                            }
                            completed
                        } finally { recognizer.close() }
                    }
                }
            }
            outcome.onSuccess { completed ->
                textVersion++
                Toast.makeText(context, "Indexate $completed pagini. Poți continua din pagina următoare.", Toast.LENGTH_LONG).show()
            }.onFailure { Toast.makeText(context, "Indexarea nu a reușit: ${it.localizedMessage}", Toast.LENGTH_LONG).show() }
            processing = false
        }
    }
    Column(Modifier.fillMaxSize().background(Ivory).statusBarsPadding().navigationBarsPadding()) {
        // Always visible, independent from the PDF scrolling surface.
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Bibliotecă") }
            Column(Modifier.weight(1f)) {
                Text(doc.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("${doc.kind} • pagina ${page + 1}/${pager.count}", fontSize = 11.sp, color = Slate)
            }
            TextButton(onClick = { expanded = !expanded }) {
                Icon(Icons.Rounded.AutoAwesome, "Nadia AI", tint = Rose)
                Spacer(Modifier.width(4.dp))
                Text(if (expanded) "Ascunde AI" else "Nadia AI", fontSize = 12.sp, color = Navy)
            }
            IconButton(enabled = !exporting, onClick = {
                exporting = true
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { AnnotatedPdfExporter.export(context, doc, store) } }
                        .onSuccess { file -> sharePdf(context, file) }
                        .onFailure { Toast.makeText(context, it.localizedMessage ?: "Export eșuat", Toast.LENGTH_LONG).show() }
                    exporting = false
                }
            }) { Icon(Icons.Rounded.IosShare, "Export PDF") }
            IconButton(onClick = { showRemove = true }) { Icon(Icons.Rounded.MoreVert, "Mai multe") }
        }
        ToolBar(tool = tool, onTool = { tool = it }, color = color, onColor = { color = it },
            width = width, onWidth = { width = it }, onUndo = { canvasView?.undo() },
            onRedo = { canvasView?.redo() }, onImportSticker = { imagePicker.launch("image/*") },
            onAtlas = { showAtlas = true }, onClearSelection = { canvasView?.eraseSelection() },
            onStickerResize = { canvasView?.increaseStickerSize(it) },
            onStickerRemove = { canvasView?.deleteSelectedSticker() })

        Row(Modifier.fillMaxWidth().weight(1f)) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (continuous) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val requestedWidth = (minOf(maxWidth.value - 20f, 960f) * documentScale).coerceAtLeast(120f).dp
                        LazyColumn(
                            state = scrollState,
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            contentPadding = PaddingValues(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(pager.count, key = { it }) { index ->
                                var pageBitmap by remember(doc.id, index) { mutableStateOf<Bitmap?>(null) }
                                var pageMarks by remember(doc.id, index) { mutableStateOf(store.ink(doc.id, index)) }
                                var pageStickers by remember(doc.id, index) { mutableStateOf(store.stickers(doc.id, index)) }
                                var itemError by remember(doc.id, index) { mutableStateOf<String?>(null) }
                                LaunchedEffect(doc.id, index, pager) {
                                    runCatching { withContext(Dispatchers.IO) { synchronized(pager) { pager.render(index) } } }
                                        .onSuccess { pageBitmap = it }
                                        .onFailure { itemError = it.localizedMessage ?: "Pagina nu poate fi citită" }
                                }
                                Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    contentAlignment = Alignment.Center) {
                                    Column(Modifier.width(requestedWidth)) {
                                        Text("Pagina ${index + 1} / ${pager.count}",
                                            fontSize = 11.sp, color = Slate,
                                            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp))
                                        Box(Modifier.fillMaxWidth().aspectRatio(
                                            pageBitmap?.let { it.width.toFloat() / it.height.toFloat() } ?: 0.707f
                                        )) {
                                            if (pageBitmap != null) {
                                                AndroidView(
                                                    factory = { c -> StudyCanvasView(c).apply { scrollThroughFingers = true } },
                                                    modifier = Modifier.fillMaxSize(),
                                                    update = { view ->
                                                        if (page == index) canvasView = view
                                                        view.scrollThroughFingers = true
                                                        view.onSave = { strokes ->
                                                            pageMarks = strokes
                                                            store.saveInk(doc.id, index, strokes)
                                                            if (page == index) marks = strokes
                                                        }
                                                        view.onAnalyze = { selectedImage -> page = index; crop = selectedImage }
                                                        view.comments = store.comments(doc.id, index).also { commentsVersion }
                                                        view.textBoxes = store.textBoxes(doc.id, index).also { textBoxesVersion }
                                                        view.onTextCreate = { point -> textAnchor = index to point }
                                                        view.onTextOpen = { box -> selectedText = box }
                                                        view.onTextsSave = { list ->
                                                            store.saveTextBoxes(doc.id, index, list)
                                                            textBoxesVersion++
                                                        }
                                                        view.onCommentCreate = { point, word -> commentAnchor = Triple(index, point, word) }
                                                        view.onCommentOpen = { comment -> selectedComment = comment }
                                                        view.stickerResolver = { name -> stickerCache.getOrPut(name) {
                                                            store.stickerBitmap(name) ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                                                        } }
                                                        view.onStickersSave = { list ->
                                                            pageStickers = list
                                                            store.saveStickers(doc.id, index, list)
                                                            if (page == index) stickers = list
                                                        }
                                                        view.stickers = if (page == index) stickers else pageStickers
                                                        view.bind(pageBitmap, if (page == index) marks else pageMarks,
                                                            tool, color, width, fingerInk)
                                                    }
                                                )
                                            } else if (itemError != null) {
                                                Text(itemError.orEmpty(), Modifier.align(Alignment.Center))
                                            } else {
                                                CircularProgressIndicator(Modifier.align(Alignment.Center))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (bitmap != null) AndroidView(
                        factory = { c -> StudyCanvasView(c).also { canvasView = it } },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            canvasView = view
                            view.scrollThroughFingers = false
                            view.onSave = { strokes -> marks = strokes; store.saveInk(doc.id, page, strokes) }
                            view.onAnalyze = { crop = it }
                            view.comments = store.comments(doc.id, page).also { commentsVersion }
                            view.textBoxes = store.textBoxes(doc.id, page).also { textBoxesVersion }
                            view.onTextCreate = { point -> textAnchor = page to point }
                            view.onTextOpen = { box -> selectedText = box }
                            view.onTextsSave = { list ->
                                store.saveTextBoxes(doc.id, page, list); textBoxesVersion++
                            }
                            view.onCommentCreate = { point, word -> commentAnchor = Triple(page, point, word) }
                            view.onCommentOpen = { comment -> selectedComment = comment }
                            view.stickerResolver = { name -> stickerCache.getOrPut(name) {
                                store.stickerBitmap(name) ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                            } }
                            view.onStickersSave = { list -> stickers = list; store.saveStickers(doc.id, page, list) }
                            view.stickers = stickers
                            view.bind(bitmap, marks, tool, color, width, fingerInk)
                        }
                    ) else if (loadError != null) Text(loadError.orEmpty(), Modifier.align(Alignment.Center))
                    else CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
            if (isWide && expanded) {
                VerticalDivider()
                Box(Modifier.width(340.dp).fillMaxHeight()) {
                    StudyPanel(doc, store, page, textVersion, bitmap,
                        onOcr = { runOcr(bitmap) }, onBatchOcr = { runBatchOcr() },
                        onSuggest = { suggestImportantLines() }, onAiSuggest = { suggestAiHighlights() },
                        onNote = { showNote = true }, onClose = { expanded = false })
                }
            }
        }

        // Fixed reader controls. No longer hidden below an oversized PDF or quiz panel.
        Row(Modifier.fillMaxWidth().background(Color.White).horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { continuous = !continuous }) {
                Icon(if (continuous) Icons.Rounded.ViewDay else Icons.Rounded.Description, null)
                Spacer(Modifier.width(4.dp))
                Text(if (continuous) "Pagini continue" else "O pagină", fontSize = 12.sp)
            }
            IconButton(onClick = {
                val target = (page - 1).coerceAtLeast(0)
                page = target
                if (continuous) scope.launch { scrollState.scrollToItem(target) }
            }, enabled = page > 0) { Icon(Icons.Rounded.ChevronLeft, "Pagina precedentă") }
            Text("${page + 1} / ${pager.count}", fontSize = 12.sp, color = Navy)
            IconButton(onClick = {
                val target = (page + 1).coerceAtMost(pager.count - 1)
                page = target
                if (continuous) scope.launch { scrollState.scrollToItem(target) }
            }, enabled = page < pager.count - 1) { Icon(Icons.Rounded.ChevronRight, "Pagina următoare") }
            if (continuous) {
                IconButton(onClick = { documentScale = (documentScale - 0.15f).coerceAtLeast(0.6f) }) {
                    Icon(Icons.Rounded.ZoomOut, "Micșorează")
                }
                Text("${(documentScale * 100).toInt()}%", fontSize = 11.sp, color = Slate)
                IconButton(onClick = { documentScale = (documentScale + 0.15f).coerceAtMost(2f) }) {
                    Icon(Icons.Rounded.ZoomIn, "Mărește")
                }
            } else TextButton(onClick = { canvasView?.resetView() }) { Text("Reset zoom") }
            TextButton(onClick = { fingerInk = !fingerInk }) {
                Text(if (fingerInk) "Deget: desen" else "Deget: scroll", fontSize = 11.sp)
            }
            TextButton(onClick = { runOcr(bitmap) }, enabled = !processing && bitmap != null) {
                Text(if (processing) "OCR…" else "OCR pagină", fontSize = 11.sp)
            }
            TextButton(onClick = { showOcrLibrary = true }) {
                Icon(Icons.Rounded.TextSnippet, null)
                Spacer(Modifier.width(4.dp))
                Text("Text OCR", fontSize = 11.sp)
            }
        }
    }
    if (!isWide && expanded) {
        ModalBottomSheet(onDismissRequest = { expanded = false }) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(.85f)) {
                StudyPanel(doc, store, page, textVersion, bitmap, onOcr = { runOcr(bitmap) },
                    onBatchOcr = { runBatchOcr() }, onSuggest = { suggestImportantLines() },
                    onAiSuggest = { suggestAiHighlights() }, onNote = { showNote = true },
                    onClose = { expanded = false })
            }
        }
    }
    if (showOcrLibrary) {
        var ocrPage by remember { mutableIntStateOf(page) }
        var ocrText by remember(ocrPage, textVersion) {
            mutableStateOf(if (doc.kind == "PDF") store.pageText(doc.id, ocrPage) else store.extractedText(doc.id))
        }
        AlertDialog(onDismissRequest = { showOcrLibrary = false },
            title = { Text("Text extras OCR · " + doc.title) },
            text = {
                Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { ocrPage = (ocrPage - 1).coerceAtLeast(0) }) {
                            Icon(Icons.Rounded.ChevronLeft, "Pagina precedentă")
                        }
                        Text("Pagina " + (ocrPage + 1) + " din " + pager.count,
                            modifier = Modifier.weight(1f), color = Navy, fontSize = 13.sp)
                        IconButton(onClick = { ocrPage = (ocrPage + 1).coerceAtMost(pager.count - 1) }) {
                            Icon(Icons.Rounded.ChevronRight, "Pagina următoare")
                        }
                    }
                    if (ocrText.isBlank()) Text("Nu există încă text OCR pentru această pagină. Închide și apasă OCR pagină.",
                        color = Slate, fontSize = 12.sp)
                    OutlinedTextField(value = ocrText, onValueChange = { ocrText = it },
                        label = { Text("Text recunoscut — poți corecta erorile") },
                        modifier = Modifier.fillMaxWidth(), minLines = 7, maxLines = 18)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("MedAtlas OCR", ocrText))
                            Toast.makeText(context, "Text copiat", Toast.LENGTH_SHORT).show()
                        }) { Text("Copiază") }
                        TextButton(onClick = {
                            shareText(context, "Text OCR din " + doc.title + ", pagina " + (ocrPage + 1) + ":\n\n" + ocrText)
                        }) { Text("ChatGPT") }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (doc.kind == "PDF") store.savePageText(doc.id, ocrPage, ocrText)
                    else store.saveExtractedText(doc.id, ocrText)
                    textVersion++
                    showOcrLibrary = false
                    Toast.makeText(context, "Text OCR salvat", Toast.LENGTH_SHORT).show()
                }) { Text("Salvează modificările") }
            },
            dismissButton = { TextButton(onClick = { showOcrLibrary = false }) { Text("Închide") } }
        )
    }
    if (imageToCrop != null) {
        val image = imageToCrop!!
        var left by remember(image) { mutableFloatStateOf(0f) }
        var right by remember(image) { mutableFloatStateOf(1f) }
        var top by remember(image) { mutableFloatStateOf(0f) }
        var bottom by remember(image) { mutableFloatStateOf(1f) }
        var imageSource by remember(image) { mutableStateOf("") }
        var imageAuthor by remember(image) { mutableStateOf("") }
        var imageLicense by remember(image) { mutableStateOf("") }
        val cx = (left * image.width).toInt().coerceIn(0, image.width - 1)
        val cy = (top * image.height).toInt().coerceIn(0, image.height - 1)
        val cw = ((right - left) * image.width).toInt().coerceIn(1, image.width - cx)
        val ch = ((bottom - top) * image.height).toInt().coerceIn(1, image.height - cy)
        val preview = remember(image, cx, cy, cw, ch) {
            Bitmap.createBitmap(image, cx, cy, cw, ch)
        }
        AlertDialog(
            onDismissRequest = { imageToCrop = null },
            title = { Text("Decupează imaginea") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    androidx.compose.foundation.Image(preview.asImageBitmap(),
                        contentDescription = "Previzualizare decupare",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 210.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                    Text("Stânga", fontSize = 11.sp)
                    Slider(value = left, onValueChange = { left = it.coerceAtMost(right - .05f) },
                        valueRange = 0f..1f)
                    Text("Dreapta", fontSize = 11.sp)
                    Slider(value = right, onValueChange = { right = it.coerceAtLeast(left + .05f) },
                        valueRange = 0f..1f)
                    Text("Sus", fontSize = 11.sp)
                    Slider(value = top, onValueChange = { top = it.coerceAtMost(bottom - .05f) },
                        valueRange = 0f..1f)
                    Text("Jos", fontSize = 11.sp)
                    Slider(value = bottom, onValueChange = { bottom = it.coerceAtLeast(top + .05f) },
                        valueRange = 0f..1f)
                    OutlinedTextField(value = imageSource, onValueChange = { imageSource = it },
                        label = { Text("Linkul original al imaginii") },
                        placeholder = { Text("https://...") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = imageAuthor, onValueChange = { imageAuthor = it },
                        label = { Text("Autor / sursă") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = imageLicense, onValueChange = { imageLicense = it },
                        label = { Text("Licența (de ex. CC BY 4.0)") }, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { left = 0f; right = 1f; top = 0f; bottom = 1f }) {
                        Text("Resetează decuparea")
                    }
                }
            },
            confirmButton = { Button(onClick = {
                runCatching {
                    val cropped = Bitmap.createBitmap(image, cx, cy, cw, ch)
                    val name = store.saveGeneratedSticker(cropped)
                    stickers = stickers + PageSticker(
                        imageName = name, sourceUrl = imageSource.trim(),
                        attribution = imageAuthor.trim(), license = imageLicense.trim()
                    )
                    store.saveStickers(doc.id, page, stickers)
                    tool = Tool.STICKER
                    imageToCrop = null
                }.onFailure { Toast.makeText(context, it.localizedMessage ?: "Nu s-a decupat imaginea", Toast.LENGTH_LONG).show() }
            }) { Text("Adaugă sticker") } },
            dismissButton = { TextButton(onClick = { imageToCrop = null }) { Text("Renunță") } }
        )
    }
    if (showAtlas) {
        var atlasQuery by remember { mutableStateOf("") }
        val categories = listOf(
            "Cap și gât", "Creier și neuroni", "Ochi", "Ureche și ORL",
            "Plămâni și căi aeriene", "Inimă și vase", "Abdomen și digestie",
            "Ficat și pancreas", "Rinichi și sistem urinar", "Aparat reproducător",
            "Oase și articulații", "Mușchi", "Sistem nervos periferic", "Piele",
            "Sistem endocrin", "Sistem limfatic", "Embriologie", "Patologie",
            "Proceduri medicale", "Histologie"
        )
        AlertDialog(onDismissRequest = { showAtlas = false },
            title = { Text("Atlas MedAtlas · imagini medicale") },
            text = {
                Column(Modifier.heightIn(max = 510.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = atlasQuery, onValueChange = { atlasQuery = it },
                        label = { Text("Caută organ / structură") }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true)
                    Text("Bibliotecă locală — schițe de exemplu", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    listOf("lungs" to "Plămâni", "heart" to "Inimă", "neuron" to "Neuron").forEach { (key, name) ->
                        if (atlasQuery.isBlank() || name.contains(atlasQuery, true)) {
                            OutlinedButton(onClick = {
                                runCatching { store.builtInSticker(key) }.onSuccess { imageName ->
                                    stickers = stickers + PageSticker(imageName = imageName)
                                    store.saveStickers(doc.id, page, stickers)
                                    tool = Tool.STICKER
                                }
                                showAtlas = false
                            }, modifier = Modifier.fillMaxWidth()) { Text("Adaugă $name") }
                        }
                    }
                    HorizontalDivider()
                    Text("Caută în Servier Medical Art (3000+ imagini, CC BY 4.0)",
                        fontWeight = FontWeight.Medium, fontSize = 12.sp)
                    OutlinedButton(onClick = {
                        browse(context, "site:smart.servier.com medical illustration " +
                            atlasQuery.ifBlank { "anatomy" })
                    }, modifier = Modifier.fillMaxWidth()) { Text("Servier · caută imagini") }
                    OutlinedButton(onClick = {
                        browse(context, "site:commons.wikimedia.org anatomy " +
                            atlasQuery.ifBlank { "medical anatomy" })
                    }, modifier = Modifier.fillMaxWidth()) { Text("Wikimedia Commons · caută") }
                    OutlinedButton(onClick = {
                        imagePicker.launch("image/*")
                        showAtlas = false
                    }, modifier = Modifier.fillMaxWidth()) { Text("Importă / decupează imaginea salvată") }
                    Text("Categorii anatomice", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    categories.filter { atlasQuery.isBlank() || it.contains(atlasQuery, true) }.forEach { name ->
                        TextButton(onClick = {
                            browse(context, "site:smart.servier.com " + name + " medical art")
                        }, modifier = Modifier.fillMaxWidth()) { Text(name) }
                    }
                    Text("Imaginile externe se descarcă din browser și se importă manual. Verifică licența și atribuirea.",
                        fontSize = 11.sp, color = Slate)
                }
            }, confirmButton = {
                TextButton(onClick = { showAtlas = false }) { Text("Închide") }
            })
    }
    if (highlightSuggestions.isNotEmpty()) {
        AlertDialog(onDismissRequest = { highlightSuggestions = emptyList() },
            title = { Text("Sublinieri propuse") },
            text = { Text("Am găsit ${highlightSuggestions.size} rânduri potențial importante pe pagina ${page + 1}. Acestea sunt sugestii bazate pe cuvinte-cheie, nu o evaluare clinică. Adaugi sublinierile roz?") },
            confirmButton = { Button(onClick = {
                marks = marks + highlightSuggestions
                store.saveInk(doc.id, page, marks)
                highlightSuggestions = emptyList()
            }) { Text("Adaugă") } }, dismissButton = { TextButton(onClick = { highlightSuggestions = emptyList() }) { Text("Renunță") } })
    }
    if (crop != null) {
        AlertDialog(onDismissRequest = { crop = null }, icon = { Icon(Icons.Rounded.AutoAwesome, null, tint = Rose) }, title = { Text("Selecție cu pen-ul") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Zona încercuită este pregătită. Alege cum vrei să o studiezi.")
                Button(onClick = { shareImage(context, crop!!, "Explică în română informația medicală din imaginea selectată; oferă mecanism, puncte importante pentru examen și precizează când nu ești sigur."); crop = null }, modifier = Modifier.fillMaxWidth()) { Text("Întreabă ChatGPT / Partajează") }
                OutlinedButton(onClick = { askSelection("explain") }, modifier = Modifier.fillMaxWidth(), enabled = !aiBusy) { Text("Explică aici cu Nadia AI") }
                OutlinedButton(onClick = { askSelection("web") }, modifier = Modifier.fillMaxWidth(), enabled = !aiBusy) { Text("Explică + verifică online") }
                OutlinedButton(onClick = { drawSelection() }, modifier = Modifier.fillMaxWidth(), enabled = !aiBusy) { Text("Generează desen-sticker AI") }
                OutlinedButton(onClick = { shareImage(context, crop!!, "Explică acest concept medical printr-o schemă sau un desen cu etichete anatomice corecte."); crop = null }, modifier = Modifier.fillMaxWidth()) { Text("Desen în ChatGPT Plus") }
                OutlinedButton(onClick = {
                    val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    client.process(InputImage.fromBitmap(crop!!, 0)).addOnSuccessListener { result ->
                        browse(context, result.text.ifBlank { doc.title })
                    }.addOnFailureListener { browse(context, doc.title) }.addOnCompleteListener { client.close() }
                    crop = null
                }, modifier = Modifier.fillMaxWidth()) { Text("Caută selecția pe internet") }
                OutlinedButton(onClick = { crop = null; showNote = true }, modifier = Modifier.fillMaxWidth()) { Text("Creează notiță") }
            }
        }, confirmButton = { TextButton(onClick = { crop = null }) { Text("Închide") } })
    }
    if (aiBusy) AlertDialog(onDismissRequest = {}, title = { Text("Nadia AI analizează...") }, text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(26.dp)); Spacer(Modifier.width(12.dp)); Text("Datele selectate sunt trimise doar după confirmarea ta.") } }, confirmButton = {})
    if (aiError != null) AlertDialog(onDismissRequest = { aiError = null }, title = { Text("Nadia AI") }, text = { Text(aiError.orEmpty()) }, confirmButton = { TextButton(onClick = { aiError = null }) { Text("Închide") } })
    if (aiAnswer != null) AlertDialog(onDismissRequest = { aiAnswer = null }, title = { Text(aiTitle) }, text = {
        Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())) {
            Text("${doc.title} • pagina ${page + 1}", color = Slate, fontSize = 11.sp)
            Spacer(Modifier.height(8.dp))
            Text(aiAnswer.orEmpty(), fontSize = 13.sp)
        }
    }, confirmButton = { Button(onClick = {
        store.saveNotes(store.notes() + StudyNote(documentId = doc.id, page = page, heading = aiTitle, body = aiAnswer.orEmpty()))
        aiAnswer = null
        Toast.makeText(context, "Explicație salvată în notițe", Toast.LENGTH_SHORT).show()
    }) { Text("Salvează notiță") } }, dismissButton = { TextButton(onClick = { aiAnswer = null }) { Text("Închide") } })
    if (aiIllustration != null) AlertDialog(onDismissRequest = { aiIllustration = null }, title = { Text("Desen medical AI") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Ilustrație generată automat. Verifică exactitatea anatomică înainte de utilizare.", fontSize = 12.sp, color = Slate)
            val image = aiIllustration
            if (image != null) androidx.compose.foundation.Image(image.asImageBitmap(), null, Modifier.fillMaxWidth().height(220.dp))
        }
    }, confirmButton = { Button(onClick = {
        val generated = aiIllustration ?: return@Button
        runCatching {
            val name = store.saveGeneratedSticker(generated)
            stickers = stickers + PageSticker(imageName = name)
            store.saveStickers(doc.id, page, stickers)
            tool = Tool.STICKER
        }.onSuccess { aiIllustration = null }.onFailure { aiError = it.localizedMessage }
    }) { Text("Adaugă pe pagină") } }, dismissButton = { TextButton(onClick = { aiIllustration = null }) { Text("Renunță") } })
    val textEditing = selectedText
    val textAdding = textAnchor
    if (textEditing != null || textAdding != null) {
        val targetPage = textEditing?.page ?: textAdding!!.first
        var typed by remember(textEditing?.id, textAdding) { mutableStateOf(textEditing?.content.orEmpty()) }
        var fontSize by remember(textEditing?.id, textAdding) { mutableFloatStateOf(textEditing?.sizeSp ?: 17f) }
        var fontColor by remember(textEditing?.id, textAdding) { mutableIntStateOf(textEditing?.color ?: AndroidColor.rgb(24,37,53)) }
        AlertDialog(
            onDismissRequest = { selectedText = null; textAnchor = null },
            title = { Text(if (textEditing == null) "Adaugă text pe pagină" else "Editează textul") },
            text = {
                Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Pagina ${targetPage + 1} • text poziționat pe PDF",
                        fontSize = 12.sp, color = Slate)
                    OutlinedTextField(typed, { typed = it }, modifier = Modifier.fillMaxWidth(),
                        minLines = 3, maxLines = 8, label = { Text("Scrie textul aici") })
                    Text("Dimensiune: ${fontSize.toInt()} sp", fontSize = 12.sp)
                    Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 10f..38f)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(0xFF182535, 0xFFD58EA2, 0xFF38786E, 0xFFAC6B1E).forEach { c ->
                            val actual = c.toInt()
                            Box(Modifier.size(if (fontColor == actual) 29.dp else 24.dp)
                                .clip(CircleShape).background(Color(actual))
                                .clickable { fontColor = actual })
                        }
                    }
                    Text("Pentru mutare: selectează unealta Text și trage textul cu pen-ul.",
                        color = Slate, fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val current = store.textBoxes(doc.id, targetPage)
                    val obj = textEditing?.copy(content = typed, sizeSp = fontSize, color = fontColor)
                        ?: PageTextBox(documentId = doc.id, page = targetPage,
                            x = textAdding!!.second.x, y = textAdding.second.y,
                            content = typed, sizeSp = fontSize, color = fontColor)
                    store.saveTextBoxes(doc.id, targetPage,
                        if (textEditing == null) current + obj
                        else current.map { if (it.id == textEditing.id) obj else it })
                    textBoxesVersion++
                    textAnchor = null; selectedText = null
                }, enabled = typed.isNotBlank()) { Text("Salvează textul") }
            },
            dismissButton = {
                Row {
                    if (textEditing != null) TextButton(onClick = {
                        store.saveTextBoxes(doc.id, targetPage,
                            store.textBoxes(doc.id, targetPage).filterNot { it.id == textEditing.id })
                        textBoxesVersion++
                        textAnchor = null; selectedText = null
                    }) { Text("Șterge", color = Color.Red) }
                    TextButton(onClick = { textAnchor = null; selectedText = null }) { Text("Anulează") }
                }
            }
        )
    }
    val commentEditing = selectedComment
    val commentAdding = commentAnchor
    if (commentEditing != null || commentAdding != null) {
        val commentPage = commentEditing?.page ?: commentAdding!!.first
        val coords = commentAdding?.second
        val existing = commentEditing
        var commentText by remember(existing?.id, commentPage, coords) {
            mutableStateOf(existing?.body.orEmpty())
        }
        AlertDialog(
            onDismissRequest = { selectedComment = null; commentAnchor = null },
            title = { Text(if (existing == null) "Comentariu la text" else "Comentariu ancorat") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Pagina ${commentPage + 1} • note marginale", color = Slate, fontSize = 12.sp)
                    val quote = existing?.quote ?: commentAdding?.third.orEmpty()
                    if (quote.isNotBlank()) Text("Cuvânt selectat: „$quote”", fontWeight = FontWeight.Medium, color = Teal)
                    OutlinedTextField(value = commentText, onValueChange = { commentText = it },
                        label = { Text("Explicația / ideea ta") }, modifier = Modifier.fillMaxWidth(),
                        minLines = 3, maxLines = 9)
                    Text("Simbolul roz numerotat rămâne la această poziție când mărești pagina.",
                        fontSize = 11.sp, color = Slate)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val old = store.comments(doc.id, commentPage)
                    val comment = existing?.copy(body = commentText) ?: PageComment(
                        documentId = doc.id, page = commentPage,
                        x = coords!!.x, y = coords.y, body = commentText, quote = commentAdding?.third.orEmpty()
                    )
                    store.saveComments(doc.id, commentPage,
                        if (existing == null) old + comment else old.map { if (it.id == existing.id) comment else it })
                    commentsVersion++
                    selectedComment = null; commentAnchor = null
                }, enabled = commentText.isNotBlank()) { Text("Salvează") }
            },
            dismissButton = {
                Row {
                    if (existing != null) TextButton(onClick = {
                        store.saveComments(doc.id, commentPage,
                            store.comments(doc.id, commentPage).filterNot { it.id == existing.id })
                        commentsVersion++
                        selectedComment = null; commentAnchor = null
                    }) { Text("Șterge", color = Color.Red) }
                    TextButton(onClick = { selectedComment = null; commentAnchor = null }) { Text("Anulează") }
                }
            }
        )
    }
    if (showNote) NoteDialog(store, doc, page, onDismiss = { showNote = false })
    if (showRemove) AlertDialog(onDismissRequest = { showRemove = false }, title = { Text("Gestionare document") }, text = { Text("Ștergerea va elimina și notițele, flashcardurile și adnotările asociate. Originalul din Downloads nu este afectat.") }, confirmButton = { TextButton(onClick = { showRemove = false; onRemove() }) { Text("Șterge", color = Color.Red) } }, dismissButton = { TextButton(onClick = { showRemove = false }) { Text("Anulează") } })
}

@Composable
private fun ToolBar(tool: Tool, onTool: (Tool) -> Unit, color: Int, onColor: (Int) -> Unit, width: Float, onWidth: (Float) -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onImportSticker: () -> Unit, onAtlas: () -> Unit, onClearSelection: () -> Unit, onStickerResize: (Float) -> Unit, onStickerRemove: () -> Unit) {
    val tools = listOf(Tool.PEN to "Pix", Tool.HIGHLIGHT to "Marker", Tool.UNDERLINE to "Subliniază", Tool.WAVE to "Valuri", Tool.CIRCLE to "Cerc", Tool.RECTANGLE to "Dreptunghi", Tool.ARROW to "Săgeată", Tool.LASSO to "Lasso", Tool.ERASER to "Radieră", Tool.ANALYZE to "Încercuiește AI", Tool.PAN to "Mută", Tool.STICKER to "Sticker", Tool.COMMENT to "Comentariu", Tool.TEXT to "Text")
    Column(Modifier.background(Color(0xFFF6F1F0))) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            tools.forEach { (item, label) ->
                val icon = when (item) {
                    Tool.PEN -> Icons.Rounded.Edit; Tool.HIGHLIGHT -> Icons.Rounded.Brush; Tool.UNDERLINE -> Icons.Rounded.FormatUnderlined
                    Tool.WAVE -> Icons.Rounded.Waves; Tool.CIRCLE -> Icons.Rounded.Circle; Tool.RECTANGLE -> Icons.Rounded.CropSquare
                    Tool.ARROW -> Icons.Rounded.ArrowForward; Tool.LASSO -> Icons.Rounded.Gesture; Tool.ERASER -> Icons.Rounded.AutoFixOff
                    Tool.ANALYZE -> Icons.Rounded.AutoAwesome; Tool.PAN -> Icons.Rounded.PanTool; Tool.STICKER -> Icons.Rounded.Image
                    Tool.COMMENT -> Icons.Rounded.Comment
                    Tool.TEXT -> Icons.Rounded.TextFields
                }
                Surface(shape = RoundedCornerShape(10.dp), color = if (tool == item) Color(0xFFF4DDE4) else Color.Transparent, modifier = Modifier.padding(end = 3.dp).clickable { onTool(item) }) {
                    Column(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, label, tint = if (tool == item) Color(0xFF9E5272) else Navy, modifier = Modifier.size(20.dp))
                        Text(label, fontSize = 10.sp, color = Navy)
                    }
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 10.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(0xFF315F56, 0xFFD58EA2, 0xFFDDB44B, 0xFF83A4D1, 0xFF243248, 0xFF6F9B79).forEach { c ->
                val argb = c.toInt()
                Box(Modifier.padding(end = 7.dp).size(if (color == argb) 26.dp else 23.dp).clip(CircleShape).background(Color(argb)).clickable { onColor(argb) })
            }
            Text("${width.toInt()} px", fontSize = 10.sp, color = Slate)
            Slider(value = width, onValueChange = onWidth, valueRange = 1f..12f, modifier = Modifier.width(95.dp).height(29.dp))
            IconButton(onClick = onUndo, modifier = Modifier.size(38.dp)) { Icon(Icons.Rounded.Undo, "Undo") }
            IconButton(onClick = onRedo, modifier = Modifier.size(38.dp)) { Icon(Icons.Rounded.Redo, "Redo") }
            if (tool == Tool.LASSO) TextButton(onClick = onClearSelection) { Text("Șterge selecția") }
            if (tool == Tool.STICKER) {
                TextButton(onClick = onImportSticker) { Text("+ Imagine") }
                TextButton(onClick = onAtlas) { Text("Atlas") }
                TextButton(onClick = { onStickerResize(.08f) }) { Text("Mărește") }
                TextButton(onClick = { onStickerResize(-.08f) }) { Text("Micșorează") }
                IconButton(onClick = onStickerRemove) { Icon(Icons.Rounded.Delete, "Șterge sticker") }
            }
        }
    }
}

@Composable
private fun StudyPanel(doc: StudyDocument, store: LibraryStore, page: Int, textVersion: Int, bitmap: Bitmap?, onOcr: () -> Unit, onBatchOcr: () -> Unit, onSuggest: () -> Unit, onAiSuggest: () -> Unit, onNote: () -> Unit, onClose: () -> Unit) {
    var mode by remember(doc.id) { mutableIntStateOf(0) }
    val text = remember(doc.id, page, textVersion) { if (doc.kind == "PDF") store.pageText(doc.id, page) else store.extractedText(doc.id) }
    val allText = remember(doc.id, textVersion) { store.studyText(doc) }
    var summary by remember(doc.id, page) { mutableStateOf(emptyList<String>()) }
    var quiz by remember(doc.id, page) { mutableStateOf(emptyList<StudyEngine.Question>()) }
    var quizIndex by remember(doc.id, page) { mutableIntStateOf(0) }
    var selected by remember(doc.id, page) { mutableIntStateOf(-1) }
    var freeAnswer by remember(doc.id, page) { mutableStateOf("") }
    var freeFeedback by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var aiAnswer by remember(doc.id, page) { mutableStateOf<String?>(null) }
    var aiBusy by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    fun runAi(type: String) {
        val fragment = (if (allText.isNotBlank()) allText else text).take(60000)
        if (fragment.isBlank()) { aiError = "Extrage mai întâi textul prin OCR."; return }
        if (!AiClient.configured(context)) {
            // ChatGPT Plus fallback is explicitly user-triggered, without a paid API backend.
            val task = when (type) {
                "summary" -> "Creează un rezumat complet și logic în română, cu tabele și concepte esențiale."
                "quiz" -> "Creează 10 grile medicale de examen cu barem, feedback și explicații."
                "free" -> "Creează întrebări de examen cu răspuns liber și barem de evaluare."
                "web" -> "Explică materialul și compară-l cu ghidurile medicale actuale disponibile online, menționând diferențele și sursele."
                else -> "Explică acest fragment medical."
            }
            shareText(context, task + "\n\nDin cursul " + doc.title + ", pagina " + (page + 1) + ":\n\n" + fragment.take(12000))
            return
        }
        aiBusy = true
        scope.launch {
            runCatching { AiClient.ask(context, type, fragment, page, doc.title) }
                .onSuccess { aiAnswer = it }
                .onFailure { aiError = it.localizedMessage ?: "AI indisponibil" }
            aiBusy = false
        }
    }
    Column(Modifier.fillMaxSize().background(Color.White).verticalScroll(rememberScrollState()).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Rose)
            Spacer(Modifier.width(8.dp))
            Text("Nadia Study Studio", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Închide") }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Rezumat", "Quiz", "Răspuns liber").forEachIndexed { index, name ->
                SegmentedButton(selected = mode == index, onClick = { mode = index }, shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(name, fontSize = 11.sp) }
            }
        }
        Spacer(Modifier.height(9.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedButton(onClick = { runAi("summary") }, enabled = !aiBusy) { Text("Rezumat AI", fontSize = 10.sp) }
            OutlinedButton(onClick = { runAi("quiz") }, enabled = !aiBusy) { Text("Grile AI", fontSize = 10.sp) }
            OutlinedButton(onClick = { runAi("free") }, enabled = !aiBusy) { Text("Scriere liberă AI", fontSize = 10.sp) }
            OutlinedButton(onClick = { runAi("web") }, enabled = !aiBusy) { Text("AI + web", fontSize = 10.sp) }
            OutlinedButton(onClick = { shareText(context, "Fă-mi un material de studiu bazat numai pe acest text din ${doc.title}, pagina ${page + 1}:\n\n" + (if (allText.isNotBlank()) allText else text).take(12000)) }, enabled = allText.isNotBlank() || text.isNotBlank()) { Text("ChatGPT Plus", fontSize = 10.sp) }
        }
        if (aiBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (aiError != null) TextButton(onClick = { aiError = null }) { Text("Eroare: ${aiError.orEmpty()} • Închide", fontSize = 11.sp, color = Color.Red) }
        if (aiAnswer != null) {
            Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()).background(PaleRose, RoundedCornerShape(12.dp)).padding(10.dp)) {
                Text("Nadia AI — verifică informațiile medicale", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Spacer(Modifier.height(5.dp))
                Text(aiAnswer.orEmpty(), fontSize = 12.sp)
                Row { TextButton(onClick = { store.saveNotes(store.notes() + StudyNote(documentId = doc.id, page = page, heading = "Nadia AI", body = aiAnswer.orEmpty())); Toast.makeText(context, "Salvat în Notițe", Toast.LENGTH_SHORT).show() }) { Text("Salvează") }; TextButton(onClick = { aiAnswer = null }) { Text("Închide") } }
            }
        }
        Spacer(Modifier.height(9.dp))
        Text("Sursa: ${if (doc.kind == "PDF") "OCR pagina ${page + 1}" else "text extras din ${doc.kind}"} • offline", color = Slate, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        if (doc.kind == "PDF") {
            TextButton(onClick = onSuggest, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.FormatUnderlined, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Propune sublinieri importante", fontSize = 12.sp)
            }
            OutlinedButton(onClick = onAiSuggest, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Sublinieri AI (confirmă înainte de aplicare)", fontSize = 12.sp)
            }
            OutlinedButton(onClick = onBatchOcr, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.DocumentScanner, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Indexează 10 pagini din punctul curent", fontSize = 12.sp)
            }
        }
        if (text.isBlank() && doc.kind == "PDF") {
            Text("Pentru un PDF scanat sau fără text extras, apasă OCR pentru a recunoaște textul paginii. Textul rămâne pe tabletă.", fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Button(onClick = onOcr, enabled = bitmap != null) { Icon(Icons.Rounded.DocumentScanner, null); Spacer(Modifier.width(7.dp)); Text("OCR pagina curentă") }
        }
        when (mode) {
            0 -> {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Button(onClick = { summary = StudyEngine.summary((if (text.isNotBlank()) text else allText)) }, enabled = text.isNotBlank() || allText.isNotBlank()) { Text("Rezumă") }
                    OutlinedButton(onClick = onNote) { Text("Adaugă notiță") }
                }
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (summary.isEmpty()) Text("Rezumatul offline extrage fraze importante din text; nu înlocuiește analiza AI. Pentru rezumat AI complet se va conecta ulterior un serviciu securizat.", color = Slate, fontSize = 12.sp)
                    summary.forEachIndexed { i, point -> Surface(color = if (i % 2 == 0) PaleRose else Color(0xFFF2F6F4), shape = RoundedCornerShape(10.dp)) { Text("${i + 1}. $point", modifier = Modifier.padding(11.dp), fontSize = 13.sp) } }
                    if (summary.isNotEmpty()) OutlinedButton(onClick = { shareText(context, "Te rog restructurează acest rezumat medical exclusiv pe baza fragmentelor furnizate, fără să inventezi informații:\n\n" + summary.joinToString("\n")) }) { Text("Aprofundează în ChatGPT") }
                    if (doc.kind == "PDF" && text.isNotBlank()) Text("Pentru a analiza și alte pagini, navighează la ele și folosește OCR.", color = Slate, fontSize = 11.sp)
                }
            }
            1 -> {
                Button(onClick = { quiz = StudyEngine.quiz(if (allText.isNotBlank()) allText else text); quizIndex = 0; selected = -1 }, enabled = allText.isNotBlank() || text.isNotBlank()) { Text("Generează grile offline") }
                Spacer(Modifier.height(11.dp))
                if (quiz.isEmpty()) Text("Grilele offline sunt exerciții de completare a termenilor extrași din curs. Pentru întrebări clinice complexe și distractori validați este necesară integrarea AI.", color = Slate, fontSize = 12.sp)
                else {
                    val q = quiz[quizIndex]
                    Text("Întrebarea ${quizIndex + 1} / ${quiz.size}", color = Teal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(q.prompt, fontSize = 13.sp)
                        q.options.forEachIndexed { i, label ->
                            val background = if (selected != -1 && i == q.correct) Color(0xFFDFF3E6) else if (selected == i && i != q.correct) Color(0xFFFBE3E4) else Color(0xFFF2F5F6)
                            Surface(Modifier.fillMaxWidth().clickable { selected = i }, color = background, shape = RoundedCornerShape(10.dp)) { Text("${"ABCD"[i]}. $label", modifier = Modifier.padding(11.dp)) }
                        }
                        if (selected != -1) Text(if (selected == q.correct) "Corect!" else "Răspunsul corect: ${q.options[q.correct]}", fontWeight = FontWeight.Medium, color = Teal)
                        if (selected != -1) Text("Fragment original: ${q.source}", color = Slate, fontSize = 11.sp)
                        if (selected != -1) OutlinedButton(onClick = {
                            store.saveCards(store.cards() + Flashcard(documentId = doc.id, page = page, question = q.prompt, answer = q.options[q.correct] + "\n" + q.source))
                            Toast.makeText(context, "Flashcard salvat", Toast.LENGTH_SHORT).show()
                        }) { Text("Salvează ca flashcard") }
                    }
                    Button(onClick = { quizIndex = (quizIndex + 1) % quiz.size; selected = -1 }, modifier = Modifier.fillMaxWidth()) { Text(if (quizIndex == quiz.lastIndex) "Reia testul" else "Următoarea întrebare") }
                }
            }
            else -> {
                val statement = remember(allText, text) { StudyEngine.summary(if (allText.isNotBlank()) allText else text, 1).firstOrNull() }
                if (statement == null) Text("Extrage mai întâi textul din document.", color = Slate)
                else {
                    Text("Explică în propriile cuvinte ideea din curs:", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(7.dp))
                    Text(statement, color = Teal, fontSize = 13.sp)
                    OutlinedTextField(value = freeAnswer, onValueChange = { freeAnswer = it; freeFeedback = false }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Scrie răspunsul tău...") }, minLines = 4)
                    Button(onClick = { freeFeedback = true }, enabled = freeAnswer.isNotBlank()) { Text("Autoevaluare") }
                    if (freeFeedback) Text("Autoevaluare: compară dacă ai explicat toate ideile din fragmentul original. Corectarea clinică automată este planificată pentru modul AI.", color = Slate, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun NoteDialog(store: LibraryStore, doc: StudyDocument, page: Int, onDismiss: () -> Unit) {
    var heading by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Notiță • pagina ${page + 1}") }, text = {
        Column {
            OutlinedTextField(heading, { heading = it }, label = { Text("Titlu") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(body, { body = it }, label = { Text("Conținut") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
        }
    }, confirmButton = { Button(onClick = { store.saveNotes(store.notes() + StudyNote(documentId = doc.id, page = page, heading = heading.ifBlank { "Notiță" }, body = body, course = doc.course)); onDismiss() }, enabled = body.isNotBlank()) { Text("Salvează") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Anulează") } })
}

@Composable
private fun NotesScreen(store: LibraryStore, docs: List<StudyDocument>, onOpen: (StudyDocument) -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(docs.size) {
        val original = store.notes()
        var changed = false
        val migrated = original.map { note ->
            if (note.course.isBlank()) {
                val match = docs.firstOrNull { it.id == note.documentId }
                if (match != null) {
                    changed = true
                    note.copy(course = match.course.ifBlank { "General" })
                } else note
            } else note
        }
        if (changed) {
            store.saveNotes(migrated)
            refresh++
        }
    }
    val notes = remember(refresh) { store.notes().sortedByDescending { it.updatedAt } }
    val courses = (listOf("General") + docs.map { it.course } + notes.map { it.course })
        .map { it.ifBlank { "General" } }.distinct().sorted()
    var courseFilter by remember { mutableStateOf("Toate") }
    var query by remember { mutableStateOf("") }
    var editor by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<StudyNote?>(null) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var course by remember { mutableStateOf("General") }
    var linkedDocument by remember { mutableStateOf("") }
    var drawingFile by remember { mutableStateOf("") }
    var handwriting by remember { mutableStateOf(false) }
    var handwritingView by remember { mutableStateOf<HandwritingPadView?>(null) }
    var recognizing by remember { mutableStateOf(false) }
    var addCourse by remember { mutableStateOf(false) }
    var courseName by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<StudyNote?>(null) }

    fun editNote(note: StudyNote? = null) {
        selected = note
        title = note?.heading.orEmpty()
        body = note?.body.orEmpty()
        course = note?.course?.ifBlank { "General" }
            ?: courseFilter.takeIf { it != "Toate" } ?: "General"
        linkedDocument = note?.documentId.orEmpty()
        drawingFile = note?.handwritingFile.orEmpty()
        editor = true
    }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val recognized = result.data
            ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull().orEmpty()
        if (recognized.isNotBlank()) {
            body = (body + " " + recognized).trim()
            editor = true
        }
    }
    fun dictate() {
        runCatching {
            speech.launch(Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ro-RO")
                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Dictează notițele de la curs")
            })
        }.onFailure {
            Toast.makeText(context, "Dictarea nu este disponibilă pe tabletă.", Toast.LENGTH_LONG).show()
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Carnet de curs", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("Materii, notițe tastate, pagini manuscrise și dictare", color = Slate, fontSize = 12.sp)
            }
            IconButton(onClick = { editNote() }) { Icon(Icons.Rounded.NoteAdd, "Notiță nouă") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Button(onClick = { editNote() }) { Text("Notă nouă") }
            OutlinedButton(onClick = {
                editNote(); editor = false; handwriting = true
            }) { Text("Foaie cu pen-ul") }
            OutlinedButton(onClick = { editNote(); dictate() }) { Text("Dictează") }
            OutlinedButton(onClick = { addCourse = true }) { Text("Materie nouă") }
        }
        Spacer(Modifier.height(9.dp))
        OutlinedTextField(value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Caută în carnet") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) })
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf("Toate") + courses).forEach { item ->
                FilterChip(selected = courseFilter == item, onClick = { courseFilter = item },
                    label = { Text(item, maxLines = 1) })
            }
        }
        val filtered = notes.filter {
            (courseFilter == "Toate" || it.course.ifBlank { "General" } == courseFilter) &&
            (query.isBlank() || it.heading.contains(query, true) || it.body.contains(query, true))
        }
        Text(filtered.size.toString() + " notițe", fontSize = 11.sp, color = Slate)
        if (filtered.isEmpty()) Text("Adaugă prima notiță în acest carnet.", color = Slate)
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = PaddingValues(bottom = 20.dp)) {
            items(filtered, key = { it.id }) { note ->
                Card(Modifier.fillMaxWidth().clickable { editNote(note) },
                    colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(13.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(note.heading, fontWeight = FontWeight.Bold)
                                Text(note.course.ifBlank { "General" }, fontSize = 11.sp, color = Slate)
                            }
                            IconButton(onClick = { editNote(note) }) {
                                Icon(Icons.Rounded.Edit, "Editează")
                            }
                            IconButton(onClick = { deleting = note }) {
                                Icon(Icons.Rounded.DeleteOutline, "Șterge")
                            }
                        }
                        if (note.body.isNotBlank()) Text(note.body, maxLines = 5,
                            overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                        if (note.handwritingFile.isNotBlank()) {
                            val image = remember(note.handwritingFile) {
                                store.notebookDrawing(note.handwritingFile)
                            }
                            if (image != null) androidx.compose.foundation.Image(
                                image.asImageBitmap(), contentDescription = "Pagina manuscrisă",
                                modifier = Modifier.fillMaxWidth().heightIn(max = 190.dp),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                        }
                        docs.firstOrNull { it.id == note.documentId }?.let { document ->
                            TextButton(onClick = { onOpen(document) }) {
                                Text("Deschide cursul: " + document.title.take(35))
                            }
                        }
                    }
                }
            }
        }
    }
    if (addCourse) AlertDialog(onDismissRequest = { addCourse = false },
        title = { Text("Materie nouă") },
        text = { OutlinedTextField(courseName, { courseName = it },
            label = { Text("Numele materiei") }) },
        confirmButton = { Button(onClick = {
            courseFilter = courseName.trim()
            addCourse = false
            courseName = ""
            editNote()
            course = courseFilter
        }, enabled = courseName.isNotBlank()) { Text("Creează") } },
        dismissButton = { TextButton(onClick = { addCourse = false }) { Text("Anulează") } })

    if (handwriting) AlertDialog(onDismissRequest = { if (!recognizing) handwriting = false },
        title = { Text("Carnet · scrie cu pen-ul") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Desenul original se salvează; transcrierea OCR este opțională și trebuie verificată.",
                    color = Slate, fontSize = 11.sp)
                AndroidView(factory = { c -> HandwritingPadView(c).also { handwritingView = it } },
                    modifier = Modifier.fillMaxWidth().height(300.dp))
                TextButton(onClick = { handwritingView?.clearInk() }) { Text("Șterge foaia") }
                if (recognizing) CircularProgressIndicator()
            }
        },
        confirmButton = { Button(onClick = {
            val bitmap = handwritingView?.snapshot() ?: return@Button
            drawingFile = store.saveNotebookDrawing(bitmap, drawingFile)
            handwriting = false; editor = true
        }, enabled = !recognizing) { Text("Salvează desenul") } },
        dismissButton = { TextButton(onClick = {
            val bitmap = handwritingView?.snapshot() ?: return@TextButton
            drawingFile = store.saveNotebookDrawing(bitmap, drawingFile)
            recognizing = true
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener {
                    body = (body + "\n" + it.text).trim()
                    handwriting = false; editor = true
                    Toast.makeText(context, "Corectează textul OCR înainte de salvare.",
                        Toast.LENGTH_LONG).show()
                }.addOnFailureListener {
                    handwriting = false; editor = true
                    Toast.makeText(context, "Desen salvat, OCR nereușit.", Toast.LENGTH_LONG).show()
                }.addOnCompleteListener { recognizing = false; recognizer.close() }
        }, enabled = !recognizing) { Text("Desen + OCR") } })

    if (editor) AlertDialog(onDismissRequest = { editor = false },
        title = { Text(if (selected == null) "Notă de curs" else "Editează nota") },
        text = { Column(Modifier.heightIn(max = 530.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Titlul lecției") },
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(course, { course = it }, label = { Text("Materie") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (docs.isNotEmpty()) {
                Text("Asociază un document (opțional)", color = Slate, fontSize = 11.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = linkedDocument.isBlank(),
                        onClick = { linkedDocument = "" }, label = { Text("Niciunul") })
                    docs.forEach { doc ->
                        FilterChip(selected = linkedDocument == doc.id,
                            onClick = { linkedDocument = doc.id; course = doc.course },
                            label = { Text(doc.title.take(20)) })
                    }
                }
            }
            OutlinedTextField(body, { body = it }, label = { Text("Textul notiței") },
                modifier = Modifier.fillMaxWidth(), minLines = 7, maxLines = 16)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedButton(onClick = { dictate() }) { Text("Dictează") }
                OutlinedButton(onClick = { editor = false; handwriting = true }) { Text("Scrie cu pen-ul") }
            }
            if (drawingFile.isNotBlank()) {
                val image = remember(drawingFile) { store.notebookDrawing(drawingFile) }
                if (image != null) androidx.compose.foundation.Image(image.asImageBitmap(),
                    contentDescription = "Foaia manuscrisă atașată",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            }
            Text("Nu introduce date identificabile despre pacienți.",
                color = Slate, fontSize = 11.sp)
        }},
        confirmButton = { Button(onClick = {
            val old = store.notes()
            val original = selected
            val note = if (original == null) StudyNote(
                documentId = linkedDocument.ifBlank { "medical-notebook" }, page = 0,
                heading = title.ifBlank { "Notă fără titlu" }, body = body,
                course = course.ifBlank { "General" }, handwritingFile = drawingFile)
            else original.copy(documentId = linkedDocument.ifBlank { "medical-notebook" },
                heading = title.ifBlank { "Notă fără titlu" }, body = body,
                course = course.ifBlank { "General" }, handwritingFile = drawingFile,
                updatedAt = System.currentTimeMillis())
            store.saveNotes(if (original == null) old + note
                else old.map { if (it.id == original.id) note else it })
            refresh++; editor = false
        }, enabled = body.isNotBlank() || drawingFile.isNotBlank()) { Text("Salvează") } },
        dismissButton = { TextButton(onClick = { editor = false }) { Text("Anulează") } })

    deleting?.let { note -> AlertDialog(onDismissRequest = { deleting = null },
        title = { Text("Ștergi notița?") },
        text = { Text("Ștergerea elimină nota din carnet.") },
        confirmButton = { Button(onClick = {
            store.saveNotes(store.notes().filterNot { it.id == note.id })
            deleting = null; refresh++
        }) { Text("Șterge") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Anulează") } })
    }
}

@Composable
private fun CardsScreen(store: LibraryStore, docs: List<StudyDocument>, onOpen: (StudyDocument) -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    val cards = remember(refresh) { store.cards() }
    var reveal by remember { mutableStateOf<String?>(null) }
    var dueOnly by remember { mutableStateOf(false) }
    val today = System.currentTimeMillis()
    val dueCount = cards.count { it.dueAt <= today }
    val context = LocalContext.current
    fun review(card: Flashcard, grade: Int) {
        val days = when (grade) {
            0 -> 0
            1 -> (card.intervalDays.coerceAtLeast(1) + 1)
            else -> if (card.intervalDays == 0) 3 else (card.intervalDays * 2).coerceAtMost(365)
        }
        val next = card.copy(intervalDays = days,
            dueAt = if (days == 0) System.currentTimeMillis() else System.currentTimeMillis() + days * 86400000L)
        store.saveCards(store.cards().map { if (it.id == card.id) next else it })
        reveal = null
        refresh++
    }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Pregătire pentru examen", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("Flashcarduri și materiale generate din cursuri", color = Slate, fontSize = 13.sp)
        Button(onClick = {
            context.startActivity(Intent(context, ClinicalSimulatorActivity::class.java))
        }) {
            Icon(Icons.Rounded.MedicalServices, null)
            Spacer(Modifier.width(6.dp))
            Text("Spital Virtual · cazuri clinice")
        }
        OutlinedButton(onClick = {
            context.startActivity(Intent(context, ExamSimulatorActivity::class.java))
        }) {
            Icon(Icons.Rounded.Quiz, null)
            Spacer(Modifier.width(6.dp))
            Text("Examen · grile, punctaj și explicații")
        }
        OutlinedButton(onClick = {
            context.startActivity(Intent(context, Anatomy3DActivity::class.java))
        }) {
            Icon(Icons.Rounded.AccessibilityNew, null)
            Spacer(Modifier.width(6.dp))
            Text("Anatomie 3D · organe, straturi, ECG și CT")
        }
        OutlinedButton(onClick = {
            context.startActivity(Intent(context, OrganAtlasActivity::class.java))
        }) {
            Icon(Icons.Rounded.Biotech, null)
            Spacer(Modifier.width(6.dp))
            Text("Enciclopedia organelor · anatomie, funcții, surse")
        }
        OutlinedButton(onClick = {
            context.startActivity(Intent(context, MedicalReferenceActivity::class.java))
        }) {
            Icon(Icons.Rounded.MonitorHeart, null)
            Spacer(Modifier.width(6.dp))
            Text("Laborator medical · ECG, RX, monitoare, medicamente")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("De repetat acum: " + dueCount + " / " + cards.size, color = Slate,
                fontSize = 12.sp, modifier = Modifier.weight(1f))
            Checkbox(checked = dueOnly, onCheckedChange = { dueOnly = it })
            Text("Doar de repetat", fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))
        if (cards.isEmpty()) Text("Deschide un curs și folosește Studio de învățare pentru rezumate și grile. Flashcardurile personalizate vor fi afișate aici.", color = Slate)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(cards.filter { !dueOnly || it.dueAt <= today }, key = { it.id }) { card ->
                Surface(Modifier.fillMaxWidth().clickable { reveal = if (reveal == card.id) null else card.id }, color = Color.White, shape = RoundedCornerShape(15.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(card.question, fontWeight = FontWeight.Medium)
                        if (reveal == card.id) {
                            Spacer(Modifier.height(8.dp))
                            Text(card.answer, color = Teal)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                OutlinedButton(onClick = { review(card, 0) }) { Text("Din nou", fontSize = 11.sp) }
                                OutlinedButton(onClick = { review(card, 1) }) { Text("Greu", fontSize = 11.sp) }
                                Button(onClick = { review(card, 2) }) { Text("Ușor", fontSize = 11.sp) }
                            }
                        }
                        else Text("Atinge pentru răspuns", color = Slate, fontSize = 11.sp)
                        val source = docs.firstOrNull { it.id == card.documentId }
                        if (source != null) TextButton(onClick = { onOpen(source) }) { Text("Deschide cursul") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(onReplayIntro: () -> Unit) {
    val context = LocalContext.current
    var endpoint by remember { mutableStateOf(AiClient.endpoint(context)) }
    var token by remember { mutableStateOf(AiClient.token(context)) }
    var message by remember { mutableStateOf("") }
    val editPrefs = remember { context.getSharedPreferences("medatlas_editor", android.content.Context.MODE_PRIVATE) }
    var smartUnderline by remember { mutableStateOf(editPrefs.getBoolean("smart_underline", true)) }
    var smartMarker by remember { mutableStateOf(editPrefs.getBoolean("smart_marker", true)) }
    var introEnabled by remember { mutableStateOf(editPrefs.getBoolean("show_premium_intro", true)) }
    var showBibliography by remember { mutableStateOf(false) }
    val backupStore = remember { LibraryStore(context) }
    val backupSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) runCatching { backupStore.exportBackup(uri) }
            .onSuccess { message = "Backup creat. Păstrează ZIP-ul în siguranță." }
            .onFailure { message = it.localizedMessage ?: "Backup nereușit" }
    }
    val backupRestorer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { backupStore.importBackup(uri) }
            .onSuccess { message = "Restaurat. Închide și redeschide MedAtlas pentru a reîncărca biblioteca." }
            .onFailure { message = it.localizedMessage ?: "Restaurare nereușită" }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Setări și confidențialitate", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        SettingLine("Offline-first", "Documentele, notițele și desenele se păstrează pe dispozitiv.")
        SettingLine("Fără reclame", "Nu există publicitate, trackere publicitare sau cont obligatoriu.")
        Surface(color = Color.White, shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.fillMaxWidth().padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Intro pentru Nadia", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Arată intro-ul elegant la deschiderea aplicației",
                        modifier = Modifier.weight(1f), fontSize = 12.sp)
                    Switch(checked = introEnabled, onCheckedChange = {
                        introEnabled = it
                        editPrefs.edit().putBoolean("show_premium_intro", it).apply()
                    })
                }
                OutlinedButton(onClick = onReplayIntro,
                    modifier = Modifier.fillMaxWidth()) { Text("Revezi intro-ul") }
            }
        }
        SettingLine("Portret / peisaj", "Interfață adaptabilă automat la orientarea tabletei.")
        Surface(color = Color.White, shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.fillMaxWidth().padding(15.dp)) {
                Text("Instrumente de adnotare", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Subliniere inteligentă la text OCR", modifier = Modifier.weight(1f), fontSize = 13.sp)
                    Switch(checked = smartUnderline, onCheckedChange = {
                        smartUnderline = it
                        editPrefs.edit().putBoolean("smart_underline", it).apply()
                    })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Marker drept și aliniat la text", modifier = Modifier.weight(1f), fontSize = 13.sp)
                    Switch(checked = smartMarker, onCheckedChange = {
                        smartMarker = it
                        editPrefs.edit().putBoolean("smart_marker", it).apply()
                    })
                }
                Text("OCR este salvat local. În fiecare document, apasă Text OCR pentru vizualizare și corectare.",
                    color = Slate, fontSize = 12.sp)
            }
        }
        Surface(color = Color.White, shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.fillMaxWidth().padding(15.dp)) {
                Text("Bibliografia imaginilor", fontWeight = FontWeight.SemiBold)
                Text("Lista linkurilor și licențelor introduse la importul stickerelor.",
                    color = Slate, fontSize = 12.sp)
                OutlinedButton(onClick = { showBibliography = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Deschide bibliografia imaginilor")
                }
            }
        }

        SettingLine("ChatGPT Plus", "Poți partaja textul sau selecția către ChatGPT fără API. Revii în MedAtlas după studiu.")
        Surface(color = Color.White, shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Nadia AI • conectare backend", fontWeight = FontWeight.SemiBold)
                Text("Pentru răspunsuri direct în aplicație este necesar un Cloudflare Worker cu OpenAI API. Plus nu include facturarea API. Cheia OpenAI rămâne DOAR pe server.", fontSize = 12.sp, color = Slate)
                OutlinedTextField(endpoint, { endpoint = it }, label = { Text("URL backend HTTPS") }, placeholder = { Text("https://medatlas-nadia-ai...workers.dev") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(token, { token = it }, label = { Text("Token privat MedAtlas") }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                Button(onClick = {
                    runCatching { AiClient.configure(context, endpoint, token) }.onSuccess { message = "Setările Nadia AI au fost salvate local." }.onFailure { message = it.localizedMessage ?: "Date invalide." }
                }, modifier = Modifier.fillMaxWidth()) { Text("Salvează configurarea AI") }
                if (message.isNotBlank()) Text(message, color = Teal, fontSize = 12.sp)
            }
        }
        Surface(color = Color.White, shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.fillMaxWidth().padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Siguranța cursurilor · Backup complet", fontWeight = FontWeight.SemiBold)
                Text("Salvează documentele, adnotările, comentariile și stickerele înainte să reinstalezi APK-ul.",
                    color = Slate, fontSize = 12.sp)
                Button(onClick = { backupSaver.launch("MedAtlas-Nadia-backup.zip") },
                    modifier = Modifier.fillMaxWidth()) { Text("Exportă backup ZIP") }
                OutlinedButton(onClick = { backupRestorer.launch(arrayOf("application/zip", "application/octet-stream")) },
                    modifier = Modifier.fillMaxWidth()) { Text("Restabilește din backup ZIP") }
            }
        }
        Surface(color = Color.White, shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Google Classroom · import manual sigur", fontWeight = FontWeight.SemiBold)
                Text("Deschide cursurile Classroom, descarcă fișierul PDF/DOCX/PPTX și revino în Bibliotecă → Importă. Sincronizarea Google OAuth nu este configurată.",
                    color = Slate, fontSize = 12.sp)
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://classroom.google.com/")))
                }, modifier = Modifier.fillMaxWidth()) { Text("Deschide Google Classroom") }
            }
        }
                SettingLine("Word și PowerPoint", "DOCX/PPTX se citesc ca text extras, fără păstrarea layoutului original. Documentele originale sunt păstrate.")
        SettingLine("Rezumate și quiz", "Algoritmi offline simpli; nu sunt încă evaluări AI clinice.")
        SettingLine("Backup", "Poți exporta și restaura arhiva completă ZIP în Setări.")
        Spacer(Modifier.height(14.dp))
        Text("MedAtlas • Beta 0.8 • construit pentru Nadia ♡", color = Slate, fontSize = 12.sp)
    }
    if (showBibliography) {
        val bibliography = remember(showBibliography) { backupStore.imageBibliography() }
        AlertDialog(onDismissRequest = { showBibliography = false },
            title = { Text("Bibliografie · imagini folosite") },
            text = {
                Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (bibliography.isEmpty()) Text(
                        "Nu există încă imagini cu linkuri înregistrate. La import poți completa autorul, sursa și licența.",
                        fontSize = 12.sp, color = Slate)
                    bibliography.forEachIndexed { index, item ->
                        Surface(color = Ivory, shape = RoundedCornerShape(10.dp)) {
                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                Text("${index + 1}. " + item.attribution.ifBlank { "Imagine importată" },
                                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                if (item.license.isNotBlank()) Text("Licență: " + item.license, fontSize = 11.sp)
                                if (item.sourceUrl.isNotBlank()) TextButton(onClick = {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.sourceUrl))) }
                                }) { Text("Sursă: " + item.sourceUrl.take(70), fontSize = 11.sp) }
                            }
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { showBibliography = false }) { Text("Închide") } })
    }
}

@Composable
private fun SettingLine(title: String, detail: String) {
    Surface(color = Color.White, shape = RoundedCornerShape(13.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(detail, color = Slate, fontSize = 12.sp)
        }
    }
}

private fun shareImage(context: android.content.Context, image: Bitmap, prompt: String) {
    runCatching {
        val file = File(context.cacheDir, "exports/medatlas_selectie.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_TEXT, prompt)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Alege ChatGPT sau altă aplicație"))
    }.onFailure { Toast.makeText(context, it.localizedMessage ?: "Nu s-a putut partaja", Toast.LENGTH_LONG).show() }
}

private fun shareText(context: android.content.Context, prompt: String) {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, prompt) }, "Trimite către ChatGPT"))
}

private fun browse(context: android.content.Context, query: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query.take(240))))) }
        .onFailure { Toast.makeText(context, "Nu s-a găsit browserul", Toast.LENGTH_LONG).show() }
}

private fun sharePdf(context: android.content.Context, pdf: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", pdf)
    val intent = Intent(Intent.ACTION_SEND).apply { type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    context.startActivity(Intent.createChooser(intent, "Exportă PDF adnotat"))
}
