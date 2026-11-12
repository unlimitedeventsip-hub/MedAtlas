@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package ro.medatlas.study

import android.content.Intent
import android.graphics.Bitmap
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import ro.medatlas.study.pdf.AnnotatedPdfExporter
import ro.medatlas.study.ai.AiClient
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
    var started by rememberSaveable { mutableStateOf(false) }
    var showIntro by remember { mutableStateOf(!started) }
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
        SplashScreen(onContinue = { showIntro = false; started = true })
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
                val options = listOf(Triple("Bibliotecă", Icons.Rounded.FolderOpen, 0), Triple("Notițe", Icons.Rounded.EditNote, 1), Triple("Studiu", Icons.Rounded.School, 2), Triple("Setări", Icons.Rounded.Settings, 3))
                options.forEach { (name, icon, number) ->
                    NavigationBarItem(selected = tab == number, onClick = { tab = number }, icon = { Icon(icon, name) }, label = { Text(name, fontSize = 11.sp) })
                }
            }
        },
        topBar = {
            Surface(color = Ivory, modifier = Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Favorite, null, tint = Rose, modifier = Modifier.size(26.dp))
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
                1 -> NotesScreen(store, docs)
                2 -> CardsScreen(store, docs, onOpen = { selected = it })
                else -> SettingsScreen()
            }
        }
    }
    if (importError != null) AlertDialog(onDismissRequest = { importError = null }, title = { Text("Import imposibil") }, text = { Text(importError ?: "") }, confirmButton = { TextButton(onClick = { importError = null }) { Text("Închide") } })
}

@Composable
private fun SplashScreen(onContinue: () -> Unit) {
    LaunchedEffect(Unit) { delay(3200); onContinue() }
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "ecg")
    val progress by transition.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(2800), androidx.compose.animation.core.RepeatMode.Restart), label = "draw")
    val beat by transition.animateFloat(1f, 1.07f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(650), androidx.compose.animation.core.RepeatMode.Reverse), label = "beat")
    Box(Modifier.fillMaxSize().background(DeepNavy).clickable { onContinue() }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(.9f)) {
            Canvas(Modifier.fillMaxWidth().height(145.dp)) {
                val w = size.width; val h = size.height
                val path = android.graphics.Path().apply {
                    moveTo(w * .04f, h * .54f); lineTo(w * .23f, h * .54f)
                    lineTo(w * .27f, h * .45f); lineTo(w * .30f, h * .64f)
                    lineTo(w * .34f, h * .12f); lineTo(w * .39f, h * .88f)
                    lineTo(w * .43f, h * .54f); lineTo(w * .45f, h * .54f)
                    cubicTo(w * .39f, h * .23f, w * .47f, h * .14f, w * .50f, h * .33f)
                    cubicTo(w * .58f, h * .10f, w * .66f, h * .34f, w * .50f, h * .79f)
                    cubicTo(w * .47f, h * .71f, w * .44f, h * .65f, w * .45f, h * .54f)
                    lineTo(w * .95f, h * .54f)
                }
                val measure = android.graphics.PathMeasure(path, false)
                val part = android.graphics.Path()
                measure.getSegment(0f, measure.length * progress, part, true)
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    style = android.graphics.Paint.Style.STROKE
                    color = android.graphics.Color.rgb(246, 177, 192)
                    strokeWidth = 4f * beat
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                }
                drawContext.canvas.nativeCanvas.drawPath(part, p)
            }
            Spacer(Modifier.height(5.dp))
            Text("Nadia, te iubesc", color = Color(0xFFFCE6EC), fontSize = 30.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(15.dp))
            Text("M E D A T L A S", color = Color(0xFFA8B6C8), fontSize = 13.sp)
            Spacer(Modifier.height(42.dp))
            Text("Atinge pentru a continua", fontSize = 11.sp, color = Color(0xFF758699))
        }
    }
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
    var expanded by remember { mutableStateOf(isWide) }
    var crop by remember { mutableStateOf<Bitmap?>(null) }
    var aiAnswer by remember { mutableStateOf<String?>(null) }
    var aiTitle by remember { mutableStateOf("Nadia AI") }
    var aiBusy by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var aiIllustration by remember { mutableStateOf<Bitmap?>(null) }
    fun askSelection(mode: String) {
        val current = crop ?: return
        if (!AiClient.configured(context)) { aiError = "Configurează backend-ul AI în Setări. Până atunci, poți folosi butonul ChatGPT Plus pentru a partaja selecția."; return }
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
        if (!AiClient.configured(context)) { aiError = "Configurează backend-ul AI în Setări pentru generarea desenelor."; return }
        aiBusy = true
        scope.launch {
            runCatching { AiClient.draw(context, "Ilustrează anatomia și mecanismul explicat în selecția medicală. Document: ${doc.title}, pagina ${page + 1}.", current) }
                .onSuccess { aiIllustration = it; crop = null }
                .onFailure { aiError = it.localizedMessage ?: "Nu s-a generat desenul." }
            aiBusy = false
        }
    }
    var highlightSuggestions by remember { mutableStateOf<List<Stroke>>(emptyList()) }
    var showNote by remember { mutableStateOf(false) }
    var showAtlas by remember { mutableStateOf(false) }
    var showRemove by remember { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }
    var textVersion by remember { mutableIntStateOf(0) }
    var marks by remember(doc.id, page) { mutableStateOf(store.ink(doc.id, page)) }
    var stickers by remember(doc.id, page) { mutableStateOf(store.stickers(doc.id, page)) }
    val stickerCache = remember(doc.id) { mutableMapOf<String, Bitmap>() }
    var canvasView by remember { mutableStateOf<StudyCanvasView?>(null) }
    var exporting by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            runCatching { store.importSticker(it) }.onSuccess { imageName ->
                stickers = stickers + PageSticker(imageName = imageName)
                store.saveStickers(doc.id, page, stickers)
                tool = Tool.STICKER
            }.onFailure { Toast.makeText(context, it.localizedMessage ?: "Nu s-a importat imaginea", Toast.LENGTH_LONG).show() }
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
                Toast.makeText(context, "Textul paginii a fost extras și salvat", Toast.LENGTH_SHORT).show()
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
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Bibliotecă") }
            Column(Modifier.weight(1f)) {
                Text(doc.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("${doc.kind} • Pagina ${page + 1}/${pager.count}", fontSize = 11.sp, color = Slate)
            }
            IconButton(onClick = { expanded = !expanded }) { Icon(Icons.Rounded.AutoAwesome, "Studio de învățare", tint = Rose) }
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
        ToolBar(tool = tool, onTool = { tool = it }, color = color, onColor = { color = it }, width = width, onWidth = { width = it }, onUndo = { canvasView?.undo() }, onRedo = { canvasView?.redo() }, onImportSticker = { imagePicker.launch("image/*") }, onAtlas = { showAtlas = true }, onClearSelection = { canvasView?.eraseSelection() }, onStickerResize = { canvasView?.increaseStickerSize(it) }, onStickerRemove = { canvasView?.deleteSelectedSticker() })
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 9.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (page > 0) page-- }, enabled = page > 0) { Icon(Icons.Rounded.ChevronLeft, "Pagina precedentă") }
            Text("${page + 1} / ${pager.count}", fontSize = 12.sp, color = Slate)
            IconButton(onClick = { if (page + 1 < pager.count) page++ }, enabled = page + 1 < pager.count) { Icon(Icons.Rounded.ChevronRight, "Pagina următoare") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { canvasView?.resetView() }) { Text("Zoom 100%") }
            TextButton(onClick = { fingerInk = !fingerInk }) { Text(if (fingerInk) "Deget: desen" else "Deget: navigare") }
            TextButton(onClick = { runOcr(bitmap) }, enabled = !processing && bitmap != null) {
                Text(if (processing) "OCR…" else "OCR pagină")
            }
        }
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (bitmap != null) AndroidView(
                    factory = { c -> StudyCanvasView(c).also { canvasView = it } },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        view.onSave = { strokes -> marks = strokes; store.saveInk(doc.id, page, strokes) }
                        view.onAnalyze = { crop = it }
                        view.stickerResolver = { name -> stickerCache.getOrPut(name) { store.stickerBitmap(name) ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) } }
                        view.onStickersSave = { list -> stickers = list; store.saveStickers(doc.id, page, list) }
                        view.stickers = stickers
                        view.bind(bitmap, marks, tool, color, width, fingerInk)
                    }
                ) else if (loadError != null) Text(loadError.orEmpty(), modifier = Modifier.align(Alignment.Center))
                else CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            if (isWide && expanded) {
                VerticalDivider()
                Box(Modifier.width(335.dp).fillMaxHeight()) {
                    StudyPanel(doc, store, page, textVersion, bitmap, onOcr = { runOcr(bitmap) }, onBatchOcr = { runBatchOcr() }, onSuggest = { suggestImportantLines() }, onAiSuggest = { suggestAiHighlights() }, onNote = { showNote = true }, onClose = { expanded = false })
                }
            }
        }
    }
    if (!isWide && expanded) {
        ModalBottomSheet(onDismissRequest = { expanded = false }) {
            Box(Modifier.fillMaxWidth().heightIn(min = 280.dp, max = 590.dp)) {
                StudyPanel(doc, store, page, textVersion, bitmap, onOcr = { runOcr(bitmap) }, onBatchOcr = { runBatchOcr() }, onSuggest = { suggestImportantLines() }, onAiSuggest = { suggestAiHighlights() }, onNote = { showNote = true }, onClose = { expanded = false })
            }
        }
    }
    if (showAtlas) AlertDialog(onDismissRequest = { showAtlas = false }, title = { Text("Stickers medicale") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Scheme originale pentru notițe. Nu sunt atlas anatomic validat clinic.", color = Slate, fontSize = 12.sp)
            listOf("lungs" to "Plămâni", "heart" to "Inimă", "neuron" to "Neuron").forEach { (key, name) ->
                OutlinedButton(onClick = {
                    runCatching { store.builtInSticker(key) }.onSuccess { fileName ->
                        stickers = stickers + PageSticker(imageName = fileName)
                        store.saveStickers(doc.id, page, stickers)
                        tool = Tool.STICKER
                    }.onFailure { Toast.makeText(context, "Nu s-a inserat: ${it.localizedMessage}", Toast.LENGTH_LONG).show() }
                    showAtlas = false
                }, modifier = Modifier.fillMaxWidth()) { Text(name) }
            }
        }
    }, confirmButton = { TextButton(onClick = { showAtlas = false }) { Text("Închide") } })
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
    if (showNote) NoteDialog(store, doc, page, onDismiss = { showNote = false })
    if (showRemove) AlertDialog(onDismissRequest = { showRemove = false }, title = { Text("Gestionare document") }, text = { Text("Ștergerea va elimina și notițele, flashcardurile și adnotările asociate. Originalul din Downloads nu este afectat.") }, confirmButton = { TextButton(onClick = { showRemove = false; onRemove() }) { Text("Șterge", color = Color.Red) } }, dismissButton = { TextButton(onClick = { showRemove = false }) { Text("Anulează") } })
}

@Composable
private fun ToolBar(tool: Tool, onTool: (Tool) -> Unit, color: Int, onColor: (Int) -> Unit, width: Float, onWidth: (Float) -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, onImportSticker: () -> Unit, onAtlas: () -> Unit, onClearSelection: () -> Unit, onStickerResize: (Float) -> Unit, onStickerRemove: () -> Unit) {
    val tools = listOf(Tool.PEN to "Pix", Tool.HIGHLIGHT to "Marker", Tool.UNDERLINE to "Subliniază", Tool.WAVE to "Valuri", Tool.CIRCLE to "Cerc", Tool.RECTANGLE to "Dreptunghi", Tool.ARROW to "Săgeată", Tool.LASSO to "Lasso", Tool.ERASER to "Radieră", Tool.ANALYZE to "Încercuiește AI", Tool.PAN to "Mută", Tool.STICKER to "Sticker")
    Column(Modifier.background(Color(0xFFF6F1F0))) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            tools.forEach { (item, label) ->
                val icon = when (item) {
                    Tool.PEN -> Icons.Rounded.Edit; Tool.HIGHLIGHT -> Icons.Rounded.Brush; Tool.UNDERLINE -> Icons.Rounded.FormatUnderlined
                    Tool.WAVE -> Icons.Rounded.Waves; Tool.CIRCLE -> Icons.Rounded.Circle; Tool.RECTANGLE -> Icons.Rounded.CropSquare
                    Tool.ARROW -> Icons.Rounded.ArrowForward; Tool.LASSO -> Icons.Rounded.Gesture; Tool.ERASER -> Icons.Rounded.AutoFixOff
                    Tool.ANALYZE -> Icons.Rounded.AutoAwesome; Tool.PAN -> Icons.Rounded.PanTool; Tool.STICKER -> Icons.Rounded.Image
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
        if (!AiClient.configured(context)) { aiError = "Configurează Nadia AI în Setări sau trimite fragmentul către ChatGPT Plus."; return }
        aiBusy = true
        scope.launch {
            runCatching { AiClient.ask(context, type, fragment, page, doc.title) }
                .onSuccess { aiAnswer = it }
                .onFailure { aiError = it.localizedMessage ?: "AI indisponibil" }
            aiBusy = false
        }
    }
    Column(Modifier.fillMaxSize().background(Color.White).padding(14.dp)) {
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
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
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
                    OutlinedTextField(value = freeAnswer, onValueChange = { freeAnswer = it; freeFeedback = false }, modifier = Modifier.fillMaxWidth().weight(1f), placeholder = { Text("Scrie răspunsul tău...") }, minLines = 4)
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
    }, confirmButton = { Button(onClick = { store.saveNotes(store.notes() + StudyNote(documentId = doc.id, page = page, heading = heading.ifBlank { "Notiță" }, body = body)); onDismiss() }, enabled = body.isNotBlank()) { Text("Salvează") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Anulează") } })
}

@Composable
private fun NotesScreen(store: LibraryStore, docs: List<StudyDocument>) {
    var refresh by remember { mutableIntStateOf(0) }
    val notes = remember(refresh) { store.notes().sortedByDescending { it.createdAt } }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Notițele mele", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(13.dp))
        if (notes.isEmpty()) Text("Când adaugi notițe dintr-un curs, vor apărea aici.", color = Slate)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(notes, key = { it.id }) { note ->
                Surface(color = Color.White, shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(15.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(note.heading, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { store.saveNotes(store.notes().filterNot { it.id == note.id }); refresh++ }) { Icon(Icons.Rounded.DeleteOutline, "Șterge") }
                        }
                        Text("${docs.firstOrNull { it.id == note.documentId }?.title ?: "Document"} • p. ${note.page + 1}", fontSize = 11.sp, color = Slate)
                        Spacer(Modifier.height(6.dp)); Text(note.body)
                    }
                }
            }
        }
    }
}

@Composable
private fun CardsScreen(store: LibraryStore, docs: List<StudyDocument>, onOpen: (StudyDocument) -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    val cards = remember(refresh) { store.cards() }
    var reveal by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Pregătire pentru examen", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("Flashcarduri și materiale generate din cursuri", color = Slate, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        if (cards.isEmpty()) Text("Deschide un curs și folosește Studio de învățare pentru rezumate și grile. Flashcardurile personalizate vor fi afișate aici.", color = Slate)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(cards) { card ->
                Surface(Modifier.fillMaxWidth().clickable { reveal = if (reveal == card.id) null else card.id }, color = Color.White, shape = RoundedCornerShape(15.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(card.question, fontWeight = FontWeight.Medium)
                        if (reveal == card.id) { Spacer(Modifier.height(8.dp)); Text(card.answer, color = Teal) }
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
private fun SettingsScreen() {
    val context = LocalContext.current
    var endpoint by remember { mutableStateOf(AiClient.endpoint(context)) }
    var token by remember { mutableStateOf(AiClient.token(context)) }
    var message by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Setări și confidențialitate", fontSize = 23.sp, fontWeight = FontWeight.Bold)
        SettingLine("Offline-first", "Documentele, notițele și desenele se păstrează pe dispozitiv.")
        SettingLine("Fără reclame", "Nu există publicitate, trackere publicitare sau cont obligatoriu.")
        SettingLine("Intro personalizat", "Nadia, te iubesc — ECG și inimă animată la pornire.")
        SettingLine("Portret / peisaj", "Interfață adaptabilă automat la orientarea tabletei.")
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
        SettingLine("Word și PowerPoint", "DOCX/PPTX se citesc ca text extras, fără păstrarea layoutului original. Documentele originale sunt păstrate.")
        SettingLine("Rezumate și quiz", "Algoritmi offline simpli; nu sunt încă evaluări AI clinice.")
        SettingLine("Backup", "Exportul PDF este disponibil. Backup-ul complet al bibliotecii va fi adăugat ulterior.")
        Spacer(Modifier.height(14.dp))
        Text("MedAtlas • MVP 0.2 • construit pentru Nadia ♡", color = Slate, fontSize = 12.sp)
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
