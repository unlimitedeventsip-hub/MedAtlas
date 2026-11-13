package ro.medatlas.study

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ro.medatlas.study.data.LibraryStore
import ro.medatlas.study.data.StudyDocument
import ro.medatlas.study.data.StudyNote
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val glassWhite = Color(0xFFEAF3FF)
private val glassMuted = Color(0xFFAABFD9)
private val glassBlue = Color(0xFF338DFA)
private val glassBorder = Color.White.copy(alpha = .16f)
private val glassSurface = Color(0xFF163255).copy(alpha = .75f)
private val glassShape = RoundedCornerShape(24.dp)

internal data class LibraryFolder(val id: String, val name: String, val parentId: String?)

/** Folder metadata is separate from imported files; existing PDFs, ink and AI indexes are untouched. */
internal class LibraryOrganization(private val context: Context, private val store: LibraryStore) {
    private val file = File(context.filesDir, "medatlas/organization.json")
    private val folders = mutableListOf<LibraryFolder>()
    private val documents = mutableMapOf<String, String>()
    private val notes = mutableMapOf<String, String>()

    init {
        if (file.exists()) load() else migrateCourses()
    }

    private fun load() {
        runCatching {
            val obj = JSONObject(file.readText())
            val list = obj.optJSONArray("folders") ?: JSONArray()
            for (i in 0 until list.length()) {
                val f = list.getJSONObject(i)
                folders.add(LibraryFolder(f.getString("id"), f.getString("name"),
                    f.optString("parentId").takeIf { it.isNotBlank() }))
            }
            for ((key, target) in listOf("documents" to documents, "notes" to notes)) {
                val entries = obj.optJSONObject(key) ?: JSONObject()
                for (id in entries.keys()) target[id] = entries.getString(id)
            }
        }
    }

    private fun migrateCourses() {
        val names = (store.documents().map { it.course } + store.notes().map { it.course })
            .map { it.trim() }.filter { it.isNotEmpty() && it != "Fără materie" && it != "General" }
            .distinct().sorted()
        names.forEach { name ->
            folders.add(LibraryFolder(UUID.randomUUID().toString(), name, null))
        }
        store.documents().forEach { doc ->
            folders.firstOrNull { it.name == doc.course }?.let { documents[doc.id] = it.id }
        }
        store.notes().forEach { note ->
            folders.firstOrNull { it.name == note.course }?.let { notes[note.id] = it.id }
        }
        save()
    }

    private fun save() {
        file.parentFile?.mkdirs()
        val objectJson = JSONObject().apply {
            put("folders", JSONArray().apply {
                folders.forEach { f -> put(JSONObject().apply {
                    put("id", f.id)
                    put("name", f.name)
                    put("parentId", f.parentId ?: "")
                }) }
            })
            put("documents", JSONObject().apply { documents.forEach { (id, folder) -> put(id, folder) } })
            put("notes", JSONObject().apply { notes.forEach { (id, folder) -> put(id, folder) } })
        }
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(objectJson.toString())
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
    }

    fun allFolders(): List<LibraryFolder> = folders.toList()
    fun name(id: String?): String = folders.firstOrNull { it.id == id }?.name.orEmpty()
    fun documentFolder(doc: StudyDocument): String? =
        documents[doc.id] ?: folders.firstOrNull { it.name == doc.course }?.id
    fun noteFolder(note: StudyNote): String? =
        notes[note.id] ?: folders.firstOrNull { it.name == note.course }?.id

    fun create(name: String, parent: String?): LibraryFolder {
        val clean = name.trim()
        require(clean.isNotEmpty() && clean.length <= 80) { "Numele folderului trebuie să aibă între 1 și 80 de caractere." }
        require(parent == null || folders.any { it.id == parent }) { "Folder părinte inexistent." }
        require(folders.none { it.parentId == parent && it.name.equals(clean, true) }) { "Folderul există deja aici." }
        return LibraryFolder(UUID.randomUUID().toString(), clean, parent).also { folders.add(it); save() }
    }

    fun rename(id: String, name: String) {
        val old = folders.firstOrNull { it.id == id } ?: return
        val clean = name.trim()
        require(clean.isNotEmpty() && clean.length <= 80) { "Nume invalid." }
        require(folders.none { it.id != id && it.parentId == old.parentId && it.name.equals(clean, true) }) { "Numele este deja folosit." }
        folders[folders.indexOf(old)] = old.copy(name = clean)
        save()
    }

    /** Delete a folder without deleting study files or notes; its children and contents move up. */
    fun remove(id: String) {
        val folder = folders.firstOrNull { it.id == id } ?: return
        for (i in folders.indices) {
            if (folders[i].parentId == id) folders[i] = folders[i].copy(parentId = folder.parentId)
        }
        folders.removeAll { it.id == id }
        documents.entries.toList().filter { it.value == id }.forEach {
            if (folder.parentId == null) documents.remove(it.key) else documents[it.key] = folder.parentId
        }
        notes.entries.toList().filter { it.value == id }.forEach {
            if (folder.parentId == null) notes.remove(it.key) else notes[it.key] = folder.parentId
        }
        save()
    }

    fun moveDocument(id: String, target: String?) {
        require(target == null || folders.any { it.id == target })
        if (target == null) documents.remove(id) else documents[id] = target
        save()
    }

    fun moveNote(id: String, target: String?) {
        require(target == null || folders.any { it.id == target })
        if (target == null) notes.remove(id) else notes[id] = target
        save()
    }

    fun depth(id: String): Int {
        var pointer = folders.firstOrNull { it.id == id }?.parentId
        val visited = mutableSetOf(id)
        var depth = 0
        while (pointer != null && visited.add(pointer) && depth < 6) {
            depth++
            pointer = folders.firstOrNull { it.id == pointer }?.parentId
        }
        return depth
    }
}

private data class LibraryRow(
    val id: String,
    val title: String,
    val type: String,
    val updated: Long,
    val document: StudyDocument? = null,
    val note: StudyNote? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UnifiedLibraryScreen(
    store: LibraryStore,
    refreshKey: Int,
    documents: List<StudyDocument>,
    onOpenDocument: (StudyDocument) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenDocumentAtPage: (StudyDocument, Int) -> Unit = { doc, _ -> onOpenDocument(doc) },
    onImport: (String?) -> Unit,
    onDocumentChanged: (StudyDocument) -> Unit,
    onDocumentDeleted: (String) -> Unit,
    onLibraryRestored: () -> Unit,
    onNavigate: (Int) -> Unit
) {
    val context = LocalContext.current
    val organization = remember(refreshKey) { LibraryOrganization(context, store) }
    var revision by remember { mutableIntStateOf(0) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var section by remember { mutableStateOf("Bibliotecă") }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Toate") }
    var showUmf by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showPlus by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var renamingFolder by remember { mutableStateOf<LibraryFolder?>(null) }
    var deletingFolder by remember { mutableStateOf<LibraryFolder?>(null) }
    var renamingRow by remember { mutableStateOf<LibraryRow?>(null) }
    var deletingRow by remember { mutableStateOf<LibraryRow?>(null) }
    var movingRow by remember { mutableStateOf<LibraryRow?>(null) }
    var nameInput by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var showDrive by remember { mutableStateOf(false) }
    var showPortableNotes by remember { mutableStateOf(false) }
    var showPhoneActions by remember { mutableStateOf(false) }
    val folders = remember(revision, refreshKey) { organization.allFolders() }
    val notes = remember(revision, refreshKey, documents.size) { store.notes() }
    val query = search.trim()
    val indexedHits by produceState(emptySet<String>(), query, documents.toList()) {
        value = emptySet()
        if (query.length >= 3) {
            delay(220)
            value = withContext(Dispatchers.IO) {
                documents.filter { store.containsIndexedText(it, query) }.map { it.id }.toSet()
            }
        }
    }
    val allRows = documents.map {
        LibraryRow(it.id, it.originalName.ifBlank { it.title }, it.kind, it.importedAt, document = it)
    } + notes.map {
        LibraryRow(it.id, it.heading.ifBlank { "Notiță fără titlu" }, "NOTIȚĂ", it.updatedAt, note = it)
    }
    val shown = allRows.filter { row ->
        val matchesFolder = query.isNotBlank() || section != "Bibliotecă" || selectedFolder == null ||
            (row.document?.let { organization.documentFolder(it) } ?: row.note?.let { organization.noteFolder(it) }) == selectedFolder
        val matchesSection = section != "Favorite" || row.document?.favorite == true
        val matchesType = when (filter) {
            "PDF" -> row.type == "PDF"
            "PPT/PPTX" -> row.type == "PPT" || row.type == "PPTX"
            "Notițe" -> row.note != null
            else -> true
        }
        val matchesSearch = query.isBlank() || row.title.contains(query, true) ||
            row.note?.body?.contains(query, true) == true || row.id in indexedHits ||
            (row.document?.course?.contains(query, true) == true)
        matchesFolder && matchesSection && matchesType && matchesSearch
    }.sortedByDescending { it.updated }
    val wide = LocalConfiguration.current.screenWidthDp >= 800
    val phoneProfile = DeviceExperience.profile(
        LocalConfiguration.current.smallestScreenWidthDp) == WorkspaceProfile.PHONE
    val compactLayout = phoneProfile || LocalConfiguration.current.screenWidthDp < 600
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    fun createNote() {
        val selectedName = organization.name(selectedFolder)
        val note = StudyNote(documentId = "medical-notebook", page = 0,
            heading = "Notiță nouă", body = "", course = selectedName.ifBlank { "General" },
            paper = "BLANK")
        store.saveNotes(store.notes() + note)
        organization.moveNote(note.id, selectedFolder)
        revision++
        onOpenNote(note.id)
    }

    fun move(row: LibraryRow, target: String?) {
        val courseName = organization.name(target).ifBlank { "Fără materie" }
        if (row.document != null) {
            organization.moveDocument(row.document.id, target)
            onDocumentChanged(row.document.copy(course = courseName))
        } else if (row.note != null) {
            organization.moveNote(row.note.id, target)
            store.saveNotes(store.notes().map {
                if (it.id == row.note.id) it.copy(course = courseName, updatedAt = System.currentTimeMillis()) else it
            })
        }
        revision++
    }

    fun openOriginal(doc: StudyDocument) {
        // Office files stay in MedAtlas. The original file remains unchanged.
        onOpenDocument(doc)
    }

    Box(Modifier.fillMaxSize().background(
        Brush.linearGradient(listOf(Color(0xFF081629), Color(0xFF102A50), Color(0xFF09172D)))
    )) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = glassBlue.copy(alpha = .27f), shape = RoundedCornerShape(15.dp),
                    border = BorderStroke(1.dp, glassBlue.copy(alpha = .6f))) {
                    Icon(Icons.Rounded.MonitorHeart, null, tint = glassWhite,
                        modifier = Modifier.padding(11.dp).size(25.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text("MedAtlas", color = glassWhite, fontSize = if (wide) 25.sp else 20.sp, fontWeight = FontWeight.Bold)
                if (wide) {
                    Spacer(Modifier.width(18.dp))
                    Text("Biblioteca ta medicală completă", color = glassMuted, fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                if (!phoneProfile) Surface(color = glassBlue.copy(alpha = .18f), shape = RoundedCornerShape(40.dp),
                    border = BorderStroke(1.dp, glassBorder), modifier = Modifier.clickable { showSearch = true }) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = Color(0xFFC8B4FF), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Nadia AI", color = glassWhite, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (phoneProfile) {
                    IconButton(onClick = { showSearch = true }) {
                        Icon(Icons.Rounded.Search, "Caută în bibliotecă", tint = glassWhite)
                    }
                    Box {
                        IconButton(onClick = { showPhoneActions = true }) {
                            Icon(Icons.Rounded.MoreVert, "Mai multe acțiuni", tint = glassWhite)
                        }
                        DropdownMenu(
                            expanded = showPhoneActions,
                            onDismissRequest = { showPhoneActions = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Google Drive și backup") },
                                leadingIcon = { Icon(Icons.Rounded.CloudUpload, null) },
                                onClick = { showPhoneActions = false; showDrive = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Transferă notițe cu laptopul") },
                                leadingIcon = { Icon(Icons.Rounded.Sync, null) },
                                onClick = { showPhoneActions = false; showPortableNotes = true }
                            )
                        }
                    }
                } else {
                    IconButton(onClick = { showDrive = true }) {
                        Icon(Icons.Rounded.CloudUpload, "Google Drive și backup", tint = glassWhite)
                    }
                    IconButton(onClick = { showPortableNotes = true }) {
                        Icon(Icons.Rounded.Sync, "Transferă notițe cu laptopul", tint = glassWhite)
                    }
                    IconButton(onClick = { showSearch = true }) {
                        Icon(Icons.Rounded.Search, "Căutare", tint = glassWhite)
                    }
                }
            }

            // On phones the tablet's 163 dp fixed sidebar would leave the
            // document list unreadable. Replace it with a compact scrollable
            // navigation/folder strip, and give the list the entire width.
            if (compactLayout) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("Bibliotecă", "Recente", "Favorite").forEach { choice ->
                        FilterChip(selected = section == choice,
                            onClick = { section = choice; selectedFolder = null },
                            label = { Text(choice, fontSize = 11.sp) })
                    }
                    OutlinedButton(onClick = { onNavigate(1) }) {
                        Icon(Icons.Rounded.EditNote, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Notițe")
                    }
                }
                if (folders.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = selectedFolder == null,
                            onClick = { selectedFolder = null; section = "Bibliotecă" },
                            label = { Text("Toate folderele") })
                        folders.forEach { folder ->
                            FilterChip(selected = selectedFolder == folder.id,
                                onClick = {
                                    selectedFolder = folder.id
                                    section = "Bibliotecă"; search = ""
                                },
                                label = { Text(folder.name, maxLines = 1) })
                        }
                        TextButton(onClick = { folderName = ""; newFolder = true }) {
                            Text("+ Folder")
                        }
                    }
                }
            }
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!compactLayout) Surface(shape = glassShape, color = glassSurface,
                    border = BorderStroke(1.dp, glassBorder),
                    modifier = Modifier.width(if (wide) 258.dp else 163.dp).fillMaxHeight()) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 16.dp)) {
                        listOf("Bibliotecă" to Icons.Rounded.FolderOpen, "Recente" to Icons.Rounded.History,
                            "Favorite" to Icons.Rounded.Star, "Notițe & planșe" to Icons.Rounded.EditNote,
                            "Studiu" to Icons.Rounded.School, "Classroom" to Icons.Rounded.Class,
                            "Setări" to Icons.Rounded.Settings).forEach { (title, icon) ->
                            val active = section == title
                            Row(Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (active) glassBlue.copy(alpha = .26f) else Color.Transparent)
                                .clickable {
                                    when (title) {
                                        "Notițe & planșe" -> onNavigate(1)
                                        "Studiu" -> onNavigate(2)
                                        "Classroom" -> onNavigate(4)
                                        "Setări" -> onNavigate(3)
                                        else -> { section = title; selectedFolder = null }
                                    }
                                }.padding(horizontal = 9.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, null, tint = if (active) glassWhite else glassMuted,
                                    modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(title, color = if (active) glassWhite else glassMuted,
                                    fontSize = 12.sp, maxLines = 1)
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Folderele mele", color = glassWhite, fontWeight = FontWeight.Bold,
                                fontSize = 12.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { folderName = ""; newFolder = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.Add, "Folder nou", tint = glassWhite)
                            }
                        }
                        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                            items(folders, key = { it.id }) { folder ->
                                var showActions by remember { mutableStateOf(false) }
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .background(if (selectedFolder == folder.id) glassBlue.copy(alpha = .25f) else Color.Transparent)
                                    .clickable { selectedFolder = folder.id; section = "Bibliotecă"; search = "" }
                                    .padding(start = (6 + organization.depth(folder.id) * 8).dp, top = 7.dp, bottom = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Folder, null, tint = Color(0xFFAF78FF), modifier = Modifier.size(21.dp))
                                    Spacer(Modifier.width(7.dp))
                                    Text(folder.name, color = glassWhite, fontSize = 11.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Box {
                                        IconButton(onClick = { showActions = true }, modifier = Modifier.size(26.dp)) {
                                            Icon(Icons.Rounded.MoreVert, "Opțiuni folder", tint = glassMuted, modifier = Modifier.size(16.dp))
                                        }
                                        DropdownMenu(showActions, onDismissRequest = { showActions = false }) {
                                            DropdownMenuItem(text = { Text("Subfolder nou") }, onClick = {
                                                showActions = false; selectedFolder = folder.id; folderName = ""; newFolder = true
                                            })
                                            DropdownMenuItem(text = { Text("Redenumește") }, onClick = {
                                                showActions = false; folderName = folder.name; renamingFolder = folder
                                            })
                                            DropdownMenuItem(text = { Text("Elimină folderul") }, onClick = {
                                                showActions = false; deletingFolder = folder
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(shape = glassShape, color = glassSurface, border = BorderStroke(1.dp, glassBorder),
                    modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.fillMaxSize().padding(if (wide) 20.dp else 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Folder, null, tint = Color(0xFFAF78FF), modifier = Modifier.size(29.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (selectedFolder == null) section else organization.name(selectedFolder),
                                    color = glassWhite, fontSize = if (wide) 23.sp else 18.sp, fontWeight = FontWeight.Bold)
                                Text(shown.size.toString() + " materiale", color = glassMuted, fontSize = 11.sp)
                            }
                            if (wide) {
                                Surface(shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = .06f),
                                    border = BorderStroke(1.dp, glassBorder)) {
                                    Text("PDF  ·  PPT/PPTX  ·  Notițe", color = glassMuted, fontSize = 11.sp,
                                        modifier = Modifier.padding(12.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        if (showSearch) {
                            OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Caută în documente și notițe", color = glassMuted) },
                                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = glassWhite) },
                                trailingIcon = { IconButton(onClick = { search = ""; showSearch = false }) {
                                    Icon(Icons.Rounded.Close, "Închide", tint = glassWhite)
                                } },
                                singleLine = true, shape = RoundedCornerShape(20.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = glassWhite,
                                    unfocusedTextColor = glassWhite, focusedBorderColor = glassBlue,
                                    unfocusedBorderColor = glassBorder, cursorColor = glassBlue))
                            Spacer(Modifier.height(8.dp))
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf("Toate", "PDF", "PPT/PPTX", "Notițe").forEach { choice ->
                                FilterChip(selected = filter == choice, onClick = { filter = choice },
                                    label = { Text(choice, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = Color.White.copy(alpha = .07f),
                                        labelColor = glassMuted,
                                        selectedContainerColor = glassBlue,
                                        selectedLabelColor = Color.White),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true, selected = filter == choice,
                                        borderColor = glassBorder, selectedBorderColor = glassBlue))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .08f),
                            RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 9.dp)) {
                            Text("Nume", Modifier.weight(1f), fontSize = 11.sp, color = glassMuted)
                            if (wide) Text("Tip", Modifier.width(85.dp), fontSize = 11.sp, color = glassMuted)
                            Text("Modificat", Modifier.width(if (wide) 100.dp else 74.dp), fontSize = 11.sp, color = glassMuted)
                            Spacer(Modifier.width(32.dp))
                        }
                        if (shown.isEmpty()) {
                            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.FolderOpen, null, tint = glassMuted, modifier = Modifier.size(46.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("Folderul este pregătit", color = glassWhite, fontWeight = FontWeight.Medium)
                                Text("Adaugă o notiță sau importă documente.", color = glassMuted, fontSize = 12.sp)
                            }
                        } else {
                            LazyColumn(Modifier.fillMaxWidth().weight(1f),
                                contentPadding = PaddingValues(bottom = 88.dp)) {
                                items(shown, key = { (if (it.document != null) "doc-" else "note-") + it.id }) { row ->
                                    var rowMenu by remember { mutableStateOf(false) }
                                    Row(Modifier.fillMaxWidth().clickable {
                                        val doc = row.document
                                        if (doc != null) {
                                            if (doc.kind == "PPTX" || doc.kind == "PPT") openOriginal(doc)
                                            else onOpenDocument(doc)
                                        } else row.note?.let { onOpenNote(it.id) }
                                    }.padding(horizontal = 8.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Surface(shape = RoundedCornerShape(11.dp),
                                            color = when (row.type) {
                                                "PDF" -> Color(0xFFCE4054)
                                                "PPTX", "PPT" -> Color(0xFFDA643D)
                                                "NOTIȚĂ" -> Color(0xFF7B52D8)
                                                else -> Color(0xFF3E7EA8)
                                            }, modifier = Modifier.size(35.dp)) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(when (row.type) {
                                                    "NOTIȚĂ" -> Icons.Rounded.EditNote
                                                    "PPTX", "PPT" -> Icons.Rounded.Slideshow
                                                    else -> Icons.Rounded.Description
                                                }, null, tint = Color.White, modifier = Modifier.size(21.dp))
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Text(row.title, color = glassWhite, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f), fontSize = 12.sp)
                                        if (wide) Text(row.type, Modifier.width(85.dp), color = glassMuted, fontSize = 10.sp)
                                        Text(dateFormat.format(Date(row.updated)),
                                            Modifier.width(if (wide) 100.dp else 74.dp), color = glassMuted, fontSize = 10.sp)
                                        Box {
                                            IconButton(onClick = { rowMenu = true }, modifier = Modifier.size(32.dp)) {
                                                Icon(Icons.Rounded.MoreVert, "Opțiuni", tint = glassMuted, modifier = Modifier.size(19.dp))
                                            }
                                            DropdownMenu(rowMenu, onDismissRequest = { rowMenu = false }) {
                                                DropdownMenuItem(text = { Text("Redenumește") }, onClick = {
                                                    rowMenu = false; renamingRow = row; nameInput = row.title
                                                })
                                                DropdownMenuItem(text = { Text("Mută în folder") }, onClick = {
                                                    rowMenu = false; movingRow = row
                                                })
                                                if (row.document != null) {
                                                    DropdownMenuItem(text = { Text(if (row.document.favorite) "Elimină din favorite" else "Adaugă la favorite") },
                                                        onClick = {
                                                            rowMenu = false
                                                            onDocumentChanged(row.document.copy(favorite = !row.document.favorite))
                                                        })
                                                    DropdownMenuItem(text = { Text("Deschide în MedAtlas") },
                                                        onClick = { rowMenu = false; openOriginal(row.document) })

                                                }
                                                DropdownMenuItem(text = { Text("Șterge") }, onClick = {
                                                    rowMenu = false; deletingRow = row
                                                })
                                            }
                                        }
                                    }
                                    HorizontalDivider(color = Color.White.copy(alpha = .07f))
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 27.dp, bottom = 28.dp)) {
            DropdownMenu(expanded = showPlus, onDismissRequest = { showPlus = false }) {
                DropdownMenuItem(text = { Text("Notiță nouă") }, leadingIcon = { Icon(Icons.Rounded.EditNote, null) },
                    onClick = { showPlus = false; createNote() })
                DropdownMenuItem(text = { Text("Importă documente") }, leadingIcon = { Icon(Icons.Rounded.FileUpload, null) },
                    onClick = { showPlus = false; onImport(selectedFolder) })
                DropdownMenuItem(text = { Text("Materiale UMF") }, leadingIcon = { Icon(Icons.Rounded.School, null) },
                    onClick = { showPlus = false; showUmf = true })
                DropdownMenuItem(text = { Text("Folder nou") }, leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, null) },
                    onClick = { showPlus = false; folderName = ""; newFolder = true })
            }
            FloatingActionButton(onClick = { showPlus = true }, shape = CircleShape,
                containerColor = glassBlue, contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd)) {
                Icon(Icons.Rounded.Add, "Adaugă")
            }
        }
    }

    if (showUmf) UmfMaterialsDialog(onDismiss = { showUmf = false }, onImport = {
        showUmf = false
        onImport(selectedFolder)
    })
    if (newFolder) AlertDialog(onDismissRequest = { newFolder = false },
        title = { Text("Folder nou") },
        text = {
            Column {
                Text("Locație: " + organization.name(selectedFolder).ifBlank { "Bibliotecă" },
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(folderName, { folderName = it }, label = { Text("Denumire") }, singleLine = true)
            }
        },
        confirmButton = { Button(enabled = folderName.isNotBlank(), onClick = {
            runCatching { organization.create(folderName, selectedFolder) }
                .onSuccess { revision++; selectedFolder = it.id; newFolder = false }
                .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
        }) { Text("Creează") } },
        dismissButton = { TextButton(onClick = { newFolder = false }) { Text("Anulează") } })

    renamingFolder?.let { folder ->
        AlertDialog(onDismissRequest = { renamingFolder = null }, title = { Text("Redenumește folderul") },
            text = { OutlinedTextField(folderName, { folderName = it }, singleLine = true) },
            confirmButton = { Button(onClick = {
                runCatching { organization.rename(folder.id, folderName) }
                    .onSuccess { revision++; renamingFolder = null }
                    .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
            }) { Text("Salvează") } },
            dismissButton = { TextButton(onClick = { renamingFolder = null }) { Text("Anulează") } })
    }
    deletingFolder?.let { folder ->
        AlertDialog(onDismissRequest = { deletingFolder = null }, title = { Text("Elimină folderul?") },
            text = { Text("Fișierele, notițele și subfolderele vor fi mutate în folderul părinte; nimic nu se șterge.") },
            confirmButton = { TextButton(onClick = {
                organization.remove(folder.id)
                if (selectedFolder == folder.id) selectedFolder = folder.parentId
                revision++; deletingFolder = null
            }) { Text("Elimină folderul") } },
            dismissButton = { TextButton(onClick = { deletingFolder = null }) { Text("Anulează") } })
    }
    renamingRow?.let { row ->
        AlertDialog(onDismissRequest = { renamingRow = null }, title = { Text("Redenumește") },
            text = { OutlinedTextField(nameInput, { nameInput = it }, label = { Text("Nume") }, singleLine = true) },
            confirmButton = { Button(enabled = nameInput.isNotBlank(), onClick = {
                val title = nameInput.trim()
                row.document?.let {
                    onDocumentChanged(it.copy(title = title, originalName = title +
                        (if (title.endsWith("." + it.kind.lowercase(), true)) "" else "." + it.kind.lowercase())))
                }
                row.note?.let {
                    store.saveNotes(store.notes().map { note ->
                        if (note.id == it.id) note.copy(heading = title, updatedAt = System.currentTimeMillis()) else note
                    })
                }
                revision++; renamingRow = null
            }) { Text("Salvează") } },
            dismissButton = { TextButton(onClick = { renamingRow = null }) { Text("Anulează") } })
    }
    movingRow?.let { row ->
        AlertDialog(onDismissRequest = { movingRow = null }, title = { Text("Mută în folder") },
            text = {
                Column(Modifier.heightIn(max = 330.dp).verticalScroll(rememberScrollState())) {
                    TextButton(onClick = { move(row, null); movingRow = null }) { Text("Bibliotecă (rădăcină)") }
                    folders.forEach { folder ->
                        TextButton(onClick = { move(row, folder.id); movingRow = null }) {
                            Icon(Icons.Rounded.Folder, null)
                            Spacer(Modifier.width(8.dp))
                            Text(folder.name)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { movingRow = null }) { Text("Închide") } })
    }
    deletingRow?.let { row ->
        AlertDialog(onDismissRequest = { deletingRow = null }, title = { Text("Ștergi materialul?") },
            text = { Text("Acțiunea șterge materialul din aplicație. Folosește backup pentru a păstra o copie.") },
            confirmButton = { TextButton(onClick = {
                row.document?.let { onDocumentDeleted(it.id) }
                row.note?.let { note ->
                    store.saveNotes(store.notes().filterNot { it.id == note.id })
                    store.saveInk(inkId(note.id), 0, emptyList())
                    store.saveStickers(inkId(note.id), 0, emptyList())
                }
                revision++; deletingRow = null
            }) { Text("Șterge") } },
            dismissButton = { TextButton(onClick = { deletingRow = null }) { Text("Anulează") } })
    }
    if (showDrive) {
        DriveBackupDialog(
            store = store,
            onDismiss = { showDrive = false },
            onImported = { onImport(null) },
            onRestored = onLibraryRestored
        )
    }
    if (showPortableNotes) {
        PortableNotesDialog(
            store = store,
            onDismiss = { showPortableNotes = false },
            onImported = { revision++; onLibraryRestored() }
        )
    }
    if (showSearch) {
        NadiaGlobalSearch(
            store = store,
            documents = documents,
            onDismiss = { showSearch = false },
            onOpenDocument = onOpenDocument,
            onOpenNote = onOpenNote,
            onOpenDocumentAtPage = onOpenDocumentAtPage
        )
    }
}
