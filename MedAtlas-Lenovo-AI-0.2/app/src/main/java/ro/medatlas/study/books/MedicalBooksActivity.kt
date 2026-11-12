package ro.medatlas.study.books

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.FileProvider
import org.json.JSONArray
import java.io.File
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ro.medatlas.study.data.LibraryStore

/* An honest bookshelf: publisher links are not silently presented as installed textbooks. */
class MedicalBooksActivity : ComponentActivity() {
    data class Book(val title: String, val author: String, val subject: String,
                    val url: String, val license: String)
    private val catalog = listOf(
        Book("Anatomy and Physiology 2e", "OpenStax", "Anatomie", "https://openstax.org/details/books/anatomy-and-physiology-2e", "Verifică licența ediției"),
        Book("Microbiology", "OpenStax", "Microbiologie", "https://openstax.org/books/microbiology/pages/1-introduction", "Verifică licența ediției"),
        Book("Pharmacology for Nurses", "OpenStax", "Farmacologie", "https://openstax.org/books/pharmacology/pages/1-introduction", "CC BY-NC-SA 4.0"),
        Book("Medical-Surgical Nursing", "OpenStax", "Clinic", "https://openstax.org/books/medical-surgical-nursing/pages/1-introduction", "CC BY-NC-SA 4.0"),
        Book("Fundamentals of Nursing", "OpenStax", "Clinic", "https://openstax.org/books/fundamentals-nursing/pages/1-introduction", "CC BY-NC-SA 4.0"),
        Book("Clinical Nursing Skills", "OpenStax", "Proceduri", "https://openstax.org/books/clinical-nursing-skills/pages/1-introduction", "Verifică licența ediției"),
        Book("Nutrition for Nurses", "OpenStax", "Nutriție", "https://openstax.org/details/books/nutrition-for-nurses", "Verifică licența ediției"),
        Book("Biology 2e", "OpenStax", "Biologie", "https://openstax.org/details/books/biology-2e", "Verifică licența ediției"),
        Book("Chemistry 2e", "OpenStax", "Biochimie", "https://openstax.org/details/books/chemistry-2e", "Verifică licența ediției"),
        Book("Introduction to Behavioral Neuroscience", "OpenStax", "Neurologie", "https://openstax.org/details/books/introduction-behavioral-neuroscience", "Verifică licența ediției"),
        Book("Nursing Pharmacology", "Open RN", "Farmacologie", "https://wtcs.pressbooks.pub/pharmacology/", "Verifică excepțiile capitolelor"),
        Book("Nursing Fundamentals", "Open RN", "Clinic", "https://wtcs.pressbooks.pub/nursingfundamentals/", "Verifică excepțiile capitolelor"),
        Book("Nursing Skills", "Open RN", "Proceduri", "https://wtcs.pressbooks.pub/nursingskills/", "Verifică excepțiile capitolelor")
    )

    data class BundledBook(val title: String, val subject: String,
        val file: String, val publisher: String, val rights: String, val bytes: Long)
    private fun bundled(): List<BundledBook> = runCatching {
        val entries = JSONArray(assets.open("open_books/catalog.json")
            .bufferedReader().use { it.readText() })
        (0 until entries.length()).map { i ->
            val record = entries.getJSONObject(i)
            BundledBook(record.getString("title"), record.getString("subject"),
                record.getString("file"), record.getString("publisher"),
                record.getString("rights"), record.optLong("bytes"))
        }
    }.getOrDefault(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BooksScreen() }
    }

    @Composable
    private fun BooksScreen() {
        val library = remember { LibraryStore(this@MedicalBooksActivity) }
        var imports by remember { mutableIntStateOf(library.documents().size) }
        var status by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        val packaged = remember { bundled() }
        var confirmBulk by remember { mutableStateOf(false) }
        var packageProgress by remember { mutableIntStateOf(0) }
        suspend fun installBooks(books: List<BundledBook>) {
            busy = true
            packageProgress = 0
            var copied = 0
            var already = 0
            var failed = 0
            for (book in books) {
                val name = book.file
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        require(name.matches(Regex("[A-Za-z0-9_.-]{5,120}"))) {
                            "Invalid bundled filename"
                        }
                        if (library.documents().any { it.originalName == name }) {
                            false
                        } else {
                            val folder = File(cacheDir, "bundled_books").apply { mkdirs() }
                            val target = File(folder, name)
                            try {
                                assets.open("open_books/" + name).use { source ->
                                    target.outputStream().use { dest -> source.copyTo(dest, 1024 * 1024) }
                                }
                                require(target.length() > 100_000L) { "PDF incomplete" }
                                val uri = FileProvider.getUriForFile(
                                    this@MedicalBooksActivity, packageName + ".files", target)
                                library.import(uri, "Cărți medicale")
                                true
                            } finally { target.delete() }
                        }
                    }
                }
                result.onSuccess { if (it) copied++ else already++ }.onFailure { failed++ }
                packageProgress++
                imports = library.documents().size
                status = "Instalare: " + packageProgress + "/" + books.size
            }
            status = "Cărți adăugate: " + copied + "; existente: " + already +
                "; erori: " + failed + ". Deschide Bibliotecă pentru a citi."
            busy = false
        }
        var search by remember { mutableStateOf("") }
        var subject by remember { mutableStateOf("Toate") }

        val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                busy = true
                packageProgress = 0
                status = "Se importă pachetul de cărți..."
                lifecycleScope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            BookPackImporter.import(this@MedicalBooksActivity, uri, library) {
                                progress -> runOnUiThread { packageProgress = progress }
                            }
                        }
                    }.onSuccess { report ->
                        imports = library.documents().size
                        status = "ZIP: " + report.imported + " cărți importate, " +
                            report.duplicates + " duplicate, " +
                            report.unsupported + " fișiere neacceptate, " +
                            report.failed + " erori. Deschide Bibliotecă."
                    }.onFailure { e ->
                        status = "Import ZIP nereușit: " +
                            (e.localizedMessage ?: "arhivă invalidă")
                    }
                    busy = false
                }
            }
        }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                busy = true
                lifecycleScope.launch {
                    val results = withContext(Dispatchers.IO) {
                        uris.map { uri -> runCatching { library.import(uri, "Cărți medicale") } }
                    }
                    imports = library.documents().size
                    status = results.count { it.isSuccess }.toString() + " cărți importate; " +
                        results.count { it.isFailure } + " cu erori."
                    busy = false
                }
            }
        }
        val filtered = catalog.filter {
            (subject == "Toate" || subject == it.subject) &&
                (search.isBlank() || it.title.contains(search, true) ||
                    it.subject.contains(search, true))
        }
        MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF38786E))) {
            Column(Modifier.fillMaxSize().background(Color(0xFFFAF8F4))
                .statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { finish() }) { Icon(Icons.Rounded.ArrowBack, "Înapoi") }
                    Column(Modifier.weight(1f)) {
                        Text("Cărți medicale", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Bibliografie și import offline", fontSize = 11.sp, color = Color.Gray)
                    }
                    Icon(Icons.Rounded.MenuBook, null)
                }
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(imports.toString() + " documente instalate în biblioteca locală",
                            fontWeight = FontWeight.SemiBold)
                        Text("PDF-uri complete incluse offline: " + packaged.size +
                            ". Referințe suplimentare cu link la editor: " + catalog.size + ".",
                            fontSize = 11.sp, color = Color.Gray)
                        if (packaged.isNotEmpty()) {
                            Button(onClick = { confirmBulk = true },
                                enabled = !busy) {
                                Icon(Icons.Rounded.FileDownload, null)
                                Spacer(Modifier.width(5.dp))
                                Text("Adaugă cărțile incluse în Bibliotecă")
                            }
                        }
                        Text("Spațiu aproximativ pentru cărțile incluse: " +
                            (packaged.sumOf { it.bytes } / (1024L * 1024L)).toString() + " MiB; " +
                            "copierea în Bibliotecă va necesita spațiu suplimentar.",
                            fontSize = 11.sp, color = Color.Gray)
                        Text("Cărțile complete sunt în APK, cu atribuirea editorului " +
                            "în PDF-urile originale; nu sunt 100 de cărți încă.",
                            fontSize = 11.sp, color = Color.Gray)
                        Button(enabled = !busy, onClick = {
                            picker.launch(arrayOf("application/pdf",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/vnd.openxmlformats-officedocument.presentationml.presentation"))
                        }) { Icon(Icons.Rounded.Add, null); Text("Importă cărți PDF în lot") }
                        OutlinedButton(enabled = !busy, onClick = {
                            zipPicker.launch(arrayOf("application/zip",
                                "application/x-zip-compressed", "application/octet-stream"))
                        }) {
                            Icon(Icons.Rounded.FolderZip, null)
                            Spacer(Modifier.width(5.dp))
                            Text("Importă pachet ZIP cu până la 300 de cărți")
                        }
                        Text("Poți importa și o arhivă ZIP cu peste 100 de cărți " +
                            "deținute legal. Nu publica PDF-uri personale în GitHub.",
                            fontSize = 11.sp, color = Color.Gray)

                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (status.isNotBlank()) Text(status, fontSize = 12.sp)
                    }
                }
                if (packaged.isNotEmpty()) {
                    Text("Manuale complete incluse în aplicație",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(
                        androidx.compose.foundation.rememberScrollState())
                        .padding(horizontal = 13.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        packaged.forEach { book ->
                            Card(Modifier.width(210.dp)) {
                                Column(Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(book.title, fontWeight = FontWeight.SemiBold,
                                        maxLines = 2)
                                    Text(book.subject, fontSize = 11.sp, color = Color.Gray)
                                    Text("OpenStax • carte completă offline",
                                        fontSize = 11.sp, color = Color(0xFF38786E))
                                    Button(onClick = {
                                        lifecycleScope.launch { installBooks(listOf(book)) }
                                    }, enabled = !busy) { Text("Adaugă în bibliotecă") }
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(search, { search = it }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    label = { Text("Caută carte / materie") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) })
                Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    (listOf("Toate") + catalog.map { it.subject }.distinct()).forEach { tag ->
                        FilterChip(selected = subject == tag, onClick = { subject = tag },
                            label = { Text(tag) })
                    }
                }
                if (confirmBulk) AlertDialog(
                    onDismissRequest = { confirmBulk = false },
                    title = { Text("Adaugi toate manualele?") },
                    text = { Text("Vor fi copiate în biblioteca MedAtlas " +
                        packaged.size + " PDF-uri (aproximativ " +
                        (packaged.sumOf { it.bytes } / (1024L * 1024L)) +
                        " MiB). Asigură-te că există spațiu disponibil. " +
                        "Manualele incluse pot fi citite offline.") },
                    confirmButton = { Button(onClick = {
                        confirmBulk = false
                        lifecycleScope.launch { installBooks(packaged) }
                    }) { Text("Importă") } },
                    dismissButton = { TextButton(onClick = { confirmBulk = false }) {
                        Text("Anulează")
                    } }
                )
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)) {
                    items(filtered) { book ->
                        Card(Modifier.fillMaxWidth().clickable {
                            runCatching {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(book.url)))
                            }.onFailure { status = it.localizedMessage ?: "Link indisponibil" }
                        }) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Book, null, tint = Color(0xFF38786E))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(book.title, fontWeight = FontWeight.SemiBold)
                                    Text(book.author + " · " + book.subject, fontSize = 11.sp)
                                    Text(book.license, fontSize = 10.sp, color = Color.Gray)
                                    Text("Deschide sursa", fontSize = 11.sp,
                                        color = Color(0xFF38786E))
                                }
                                Icon(Icons.Rounded.OpenInNew, null)
                            }
                        }
                    }
                }
            }
        }
    }
}
