from pathlib import Path
p=Path('/mnt/data/medatlas-lenovo/app/src/main/java/ro/medatlas/study/MainActivity.kt')
s=p.read_text()
s=s.replace('import ro.medatlas.study.pdf.AnnotatedPdfExporter','import ro.medatlas.study.pdf.AnnotatedPdfExporter\nimport ro.medatlas.study.ai.AiClient')
s=s.replace('    var crop by remember { mutableStateOf<Bitmap?>(null) }','''    var crop by remember { mutableStateOf<Bitmap?>(null) }
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
    }''')
s=s.replace('''                OutlinedButton(onClick = { shareImage(context, crop!!, "Explică acest concept medical printr-o schemă sau un desen cu etichete anatomice corecte."); crop = null }, modifier = Modifier.fillMaxWidth()) { Text("Explică prin desen AI") }''','''                OutlinedButton(onClick = { askSelection("explain") }, modifier = Modifier.fillMaxWidth(), enabled = !aiBusy) { Text("Explică aici cu Nadia AI") }
                OutlinedButton(onClick = { askSelection("web") }, modifier = Modifier.fillMaxWidth(), enabled = !aiBusy) { Text("Explică + verifică online") }
                OutlinedButton(onClick = { drawSelection() }, modifier = Modifier.fillMaxWidth(), enabled = !aiBusy) { Text("Generează desen-sticker AI") }
                OutlinedButton(onClick = { shareImage(context, crop!!, "Explică acest concept medical printr-o schemă sau un desen cu etichete anatomice corecte."); crop = null }, modifier = Modifier.fillMaxWidth()) { Text("Desen în ChatGPT Plus") }''')
s=s.replace('''    if (showNote) NoteDialog(store, doc, page, onDismiss = { showNote = false })''','''    if (aiBusy) AlertDialog(onDismissRequest = {}, title = { Text("Nadia AI analizează...") }, text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(26.dp)); Spacer(Modifier.width(12.dp)); Text("Datele selectate sunt trimise doar după confirmarea ta.") } }, confirmButton = {})
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
            val file = File(context.cacheDir, "medatlas-ai-sticker-${System.currentTimeMillis()}.png")
            file.outputStream().use { generated.compress(Bitmap.CompressFormat.PNG, 100, it) }
            // Copy to private sticker storage through the app's existing importer.
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val name = store.importSticker(uri)
            stickers = stickers + PageSticker(imageName = name)
            store.saveStickers(doc.id, page, stickers)
            tool = Tool.STICKER
        }.onSuccess { aiIllustration = null }.onFailure { aiError = it.localizedMessage }
    }) { Text("Adaugă pe pagină") } }, dismissButton = { TextButton(onClick = { aiIllustration = null }) { Text("Renunță") } })
    if (showNote) NoteDialog(store, doc, page, onDismiss = { showNote = false })''')
s=s.replace('import androidx.compose.ui.graphics.nativeCanvas','import androidx.compose.ui.graphics.nativeCanvas\nimport androidx.compose.ui.graphics.asImageBitmap')
s=s.replace('''    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(Color.White).padding(14.dp)) {''','''    val context = LocalContext.current
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
    Column(Modifier.fillMaxSize().background(Color.White).padding(14.dp)) {''')
s=s.replace('''        Spacer(Modifier.height(9.dp))
        Text("Sursa:''','''        Spacer(Modifier.height(9.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedButton(onClick = { runAi("summary") }, enabled = !aiBusy) { Text("Rezumat AI", fontSize = 10.sp) }
            OutlinedButton(onClick = { runAi("quiz") }, enabled = !aiBusy) { Text("Grile AI", fontSize = 10.sp) }
            OutlinedButton(onClick = { runAi("free") }, enabled = !aiBusy) { Text("Scriere liberă AI", fontSize = 10.sp) }
            OutlinedButton(onClick = { runAi("web") }, enabled = !aiBusy) { Text("AI + web", fontSize = 10.sp) }
            OutlinedButton(onClick = { shareText(context, "Fă-mi un material de studiu bazat numai pe acest text din ${doc.title}, pagina ${page + 1}:\\n\\n" + (if (allText.isNotBlank()) allText else text).take(12000)) }, enabled = allText.isNotBlank() || text.isNotBlank()) { Text("ChatGPT Plus", fontSize = 10.sp) }
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
        Text("Sursa:''')
s=s.replace('''private fun SettingsScreen() {
    Column''','''private fun SettingsScreen() {
    val context = LocalContext.current
    var endpoint by remember { mutableStateOf(AiClient.endpoint(context)) }
    var token by remember { mutableStateOf(AiClient.token(context)) }
    var message by remember { mutableStateOf("") }
    Column''')
s=s.replace('''        SettingLine("AI și web", "În această versiune, selecțiile sunt partajate explicit prin sistemul Android. Integrarea unui API AI securizat va urma.")''','''        SettingLine("ChatGPT Plus", "Poți partaja textul sau selecția către ChatGPT fără API. Revii în MedAtlas după studiu.")
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
        }''')
s=s.replace('''        Text("MedAtlas • MVP 0.1 • construit pentru Nadia ♡",''','''        Text("MedAtlas • MVP 0.2 • construit pentru Nadia ♡",''')
p.write_text(s)
mp=Path('/mnt/data/medatlas-lenovo/app/src/main/AndroidManifest.xml')
m=mp.read_text().replace('<application','<uses-permission android:name="android.permission.INTERNET" />\n    <application')
mp.write_text(m)
bp=Path('/mnt/data/medatlas-lenovo/app/build.gradle.kts')
b=bp.read_text().replace('versionCode = 1','versionCode = 2').replace('versionName = "0.1.0"','versionName = "0.2.0"')
bp.write_text(b)
