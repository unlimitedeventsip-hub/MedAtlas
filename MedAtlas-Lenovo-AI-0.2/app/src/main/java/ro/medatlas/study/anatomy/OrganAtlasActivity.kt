package ro.medatlas.study.anatomy

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

/**
 * Organ-level atlas index and study guide, not a certified clinical reference.
 * OpenStax links point to the original open-access textbook; MedAtlas does not
 * reproduce its figures and does not claim generated pictures are validated.
 */
class OrganAtlasActivity : ComponentActivity() {
    data class Organ(
        val system: String,
        val name: String,
        val role: String = "",
        val focus: String = ""
    )
    data class SystemGroup(
        val name: String, val overview: String, val url: String,
        val organs: List<Organ>
    )

    private fun group(name: String, overview: String, url: String,
                      entries: String): SystemGroup = SystemGroup(name, overview, url,
        entries.split(";").mapNotNull { raw ->
            val fields = raw.split("|")
            val title = fields.first().trim()
            if (title.isBlank()) null else Organ(name, title,
                fields.getOrElse(1) { "" }.trim(), fields.getOrElse(2) { "" }.trim())
        })

    private val groups = listOf(
        group("Sistem nervos", "Integrarea informației, coordonarea funcțiilor și control autonom.",
            "https://openstax.org/books/anatomy-and-physiology-2e/pages/13-introduction",
            "Creier|Procesarea și integrarea informațiilor|CT/RMN; Cortex cerebral; Lob frontal; Lob parietal; Lob temporal; Lob occipital; Cerebel|Coordonarea mișcărilor|Examen neurologic; Trunchi cerebral|Funcții vitale și căi nervoase; Mezencefal; Punte; Bulb rahidian; Măduva spinării|Transmiterea impulsurilor și reflexe|RMN, reflexe; Meninge; Nervi cranieni; Nervi spinali; Ganglioni nervoși; Plex brahial; Plex lombosacrat; Nerv sciatic; Sistem nervos simpatic; Sistem nervos parasimpatic"),
        group("Cardiovascular", "Transport sanguin, perfuzie tisulară și reglarea circulației.",
            "https://openstax.org/books/anatomy-and-physiology-2e/pages/19-introduction",
            "Inimă|Pomparea sângelui|ECG, ecocardiografie; Atriu drept; Atriu stâng; Ventricul drept; Ventricul stâng; Sept interventricular; Valvă mitrală; Valvă tricuspidă; Valvă aortică; Valvă pulmonară; Nod sinusal; Nod atrioventricular; Aortă|Conducerea sângelui sistemic; Artere coronare|Irigarea miocardului|Angiografie; Arteră carotidă; Arteră pulmonară; Venă pulmonară; Vene cave; Capilare; Vene periferice; Artere periferice; Sistem de conducere cardiacă"),
        group("Respirator", "Ventilație, schimburi de gaze și echilibru acido-bazic.",
            "https://openstax.org/books/anatomy-and-physiology-2e/pages/22-introduction",
            "Nas; Cavitate nazală; Sinusuri paranazale; Faringe; Laringe; Epiglotă; Trahee|Conducerea aerului|Bronhoscopie; Bronhii principale; Bronhii lobare; Bronhiole; Alveole|Schimb de gaze|Gazometrie; Plămân drept|Schimburi gazoase|Radiografie, CT, spirometrie; Plămân stâng|Schimburi gazoase|Radiografie, CT, spirometrie; Pleura|Membrane seroase toracice|Ecografie pleurală; Diafragmă|Principal mușchi inspirator|Examen dinamic; Mușchi intercostali; Hil pulmonar"),
        group("Digestiv", "Digestie, absorbție și metabolismul nutrienților.",
            "https://openstax.org/books/anatomy-and-physiology-2e/pages/23-introduction",
            "Cavitate bucală; Limbă; Dinți; Glande salivare; Faringe; Esofag|Transportul bolului alimentar|Endoscopie; Stomac|Amestecare și digestie gastrică|Endoscopie; Duoden; Jejun|Absorbția nutrienților|Endoscopie/capsulă; Ileon; Cec; Apendice vermiform; Colon ascendent; Colon transvers; Colon descendent; Colon sigmoid; Rect; Canal anal; Ficat|Metabolism și producerea bilei|Analize hepatice, ecografie; Vezică biliară|Depozitarea bilei|Ecografie; Căi biliare; Pancreas|Enzime digestive și hormoni|Lipază, CT, ecografie; Peritoneu; Mezenter"),
        group("Urinar", "Filtrarea sângelui, homeostazie și eliminarea urinei.",
            "https://openstax.org/books/anatomy-and-physiology-2e/pages/25-introduction",
            "Rinichi drept|Filtrare și reglare hidroelectrolitică|Creatinină, sumar urină, ecografie; Rinichi stâng|Filtrare și reglare hidroelectrolitică|Creatinină, sumar urină, ecografie; Cortex renal; Medulă renală; Nefron|Unitate funcțională renală; Glomerul; Tub proximal; Ansa Henle; Tub distal; Canal colector; Calice renale; Bazinet; Ureter drept; Ureter stâng; Vezică urinară|Stocarea urinei|Ecografie, sumar urină; Uretră; Sfincter urinar"),
        group("Endocrin", "Hormoni și reglarea metabolică, reproductivă și a stresului.",
            "https://openstax.org/books/anatomy-and-physiology-2e/pages/17-introduction",
            "Hipotalamus|Coordonare neuroendocrină; Hipofiză|Reglarea altor glande|Analize hormonale, RMN; Epifiză; Tiroidă|Hormoni tiroidieni|TSH, FT4, ecografie; Paratiroide|Reglarea calciului|PTH, calcemie; Suprarenale|Hormoni steroidieni și catecolamine|Cortizol, CT; Cortex suprarenalian; Medulă suprarenaliană; Insulele pancreatice|Hormoni glicemici|Glicemie, HbA1c; Celule beta pancreatice; Timus; Ovare|Hormoni reproductivi; Testicule|Hormoni reproductivi"),
        group("Reproducător", "Reproducere, funcție gonadală și dezvoltare.",
            "https://openstax.org/books/anatomy-and-physiology-2e/pages/27-introduction",
            "Ovar; Trompă uterină; Uter|Gestație și ciclu uterin|Ecografie; Endometru; Col uterin|Legătura uter-vagin|Screening cervical; Vagin; Vulvă; Clitoris; Glandă mamară|Lactație|Ecografie/mamografie; Testicul|Spermatogeneză și hormoni|Examen, ecografie; Epididim; Canal deferent; Vezicule seminale; Prostată|Componentă a lichidului seminal|PSA interpretat clinic; Penis; Uretră masculină; Scrot"),
        group("Musculoscheletal", "Susținere, mișcare, protecție și metabolism osos.",
            "https://openstax.org/books/anatomy-and-physiology-2e/pages/6-introduction",
            "Craniu|Protecția encefalului|CT osos; Mandibulă; Coloană cervicală; Coloană toracică; Coloană lombară; Sacru; Stern; Coaste; Claviculă; Scapulă; Humerus; Radius; Ulna; Carpiene; Metacarpiene; Falange mână; Pelvis; Femur|Suport și locomotie|Radiografie; Patelă; Tibie; Fibulă; Tarsiene; Metatarsiene; Falange picior; Mușchi deltoid; Mușchi pectoral mare; Biceps brahial; Triceps brahial; Diafragmă; Mușchi abdominali; Cvadriceps; Ischiogambieri; Gastrocnemian; Tendonul lui Ahile; Articulația șoldului; Genunchi; Articulația umărului"),
        group("Tegumentar", "Barieră, protecție, termoreglare și sensibilitate.",
            "https://openstax.org/books/anatomy-and-physiology-2e/pages/5-introduction",
            "Piele|Barieră protectoare|Examen dermatologic; Epiderm; Derm|Vase, nervi și anexe; Hipoderm; Folicul pilos; Glande sudoripare; Glande sebacee; Unghii; Receptori tactili; Melanocite; Keratinocite"),
        group("Limfatic și imun", "Apărare imună și circulația limfei.",
            "https://openstax.org/books/anatomy-and-physiology-2e/pages/21-introduction",
            "Ganglioni limfatici|Filtrarea limfei|Examen clinic, ecografie; Vase limfatice; Splină|Filtrare sanguină și funcții imune|Ecografie; Timus|Maturarea limfocitelor T; Măduvă osoasă|Hematopoieză|Hemoleucogramă; Amigdale palatine; Țesut limfoid asociat mucoaselor; Duct toracic; Limfocite"),
        group("Organe de simț", "Recepția stimulilor vizuali, auditivi, olfactivi și gustativi.",
            "https://openstax.org/books/anatomy-and-physiology-2e/pages/14-introduction",
            "Glob ocular|Vedere|Acuitate vizuală, fund de ochi; Cornee; Iris; Cristalin; Retină|Fototransducție|Fund de ochi; Nerv optic; Ureche externă; Membrană timpanică; Ureche medie; Oscioare auditive; Cohlee|Auz|Audiometrie; Aparat vestibular|Echilibru|Teste vestibulare; Mucoasă olfactivă; Papile gustative")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AtlasScreen() }
    }

    @Composable
    private fun AtlasScreen() {
        var query by remember { mutableStateOf("") }
        var activeSystem by remember { mutableStateOf("Toate") }
        var selected by remember { mutableStateOf<Organ?>(null) }
        val total = groups.sumOf { it.organs.size }
        MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF38786E))) {
            Column(Modifier.fillMaxSize().background(Color(0xFFFAF8F4))
                .statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (selected != null) selected = null else finish() }) {
                        Icon(Icons.Rounded.ArrowBack, "Înapoi")
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Atlasul organelor", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(total.toString() + " structuri • 11 sisteme • surse OpenStax",
                            color = Color.Gray, fontSize = 11.sp)
                    }
                    Icon(Icons.Rounded.Book, null, tint = Color(0xFFD58EA2))
                }
                if (selected == null) {
                    OutlinedTextField(value = query, onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                        singleLine = true, label = { Text("Caută organ, regiune sau funcție") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) })
                    Row(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())
                        .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (listOf("Toate") + groups.map { it.name }).forEach { system ->
                            FilterChip(selected = activeSystem == system,
                                onClick = { activeSystem = system },
                                label = { Text(system) })
                        }
                    }
                    val matching = groups.flatMap { it.organs }.filter { organ ->
                        (activeSystem == "Toate" || organ.system == activeSystem) &&
                            (query.isBlank() || listOf(organ.name, organ.system, organ.role)
                                .any { it.contains(query, ignoreCase = true) })
                    }
                    Text(matching.size.toString() + " rezultate",
                        color = Color.Gray, fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 16.dp))
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        contentPadding = PaddingValues(bottom = 25.dp)) {
                        items(matching, key = { it.system + it.name }) { organ ->
                            Card(Modifier.fillMaxWidth().clickable { selected = organ },
                                colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Row(Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Biotech, null, tint = Color(0xFF38786E))
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(organ.name, fontWeight = FontWeight.SemiBold)
                                        Text(organ.system + (if (organ.role.isNotBlank())
                                            " • " + organ.role else ""),
                                            fontSize = 11.sp, color = Color.Gray)
                                    }
                                    Icon(Icons.Rounded.ChevronRight, null)
                                }
                            }
                        }
                    }
                } else {
                    val organ = selected!!
                    val system = groups.first { it.name == organ.system }
                    LazyColumn(Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(11.dp),
                        contentPadding = PaddingValues(bottom = 22.dp)) {
                        item {
                            Text(organ.name, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                            Text(organ.system, color = Color(0xFF38786E))
                            if (organ.role.isNotBlank()) {
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(15.dp)) {
                                        Text("Rol / funcție", fontWeight = FontWeight.Bold)
                                        Text(organ.role)
                                    }
                                }
                            }
                            if (organ.focus.isNotBlank()) {
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(15.dp)) {
                                        Text("Examinare și investigații — exemple",
                                            fontWeight = FontWeight.Bold)
                                        Text(organ.focus)
                                    }
                                }
                            }
                            Text("Contextul sistemului", fontWeight = FontWeight.Bold)
                            Text(system.overview)
                            Text("Fișă de aprofundare", fontWeight = FontWeight.Bold)
                            Text("De completat din surse verificate: localizare, raporturi anatomice, "
                                + "histologie, vascularizație, inervație, fiziologie, patologii, "
                                + "diagnostic diferențial, investigații, tratament și exemple clinice.")
                            OutlinedButton(onClick = {
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(system.url)))
                            }) {
                                Icon(Icons.Rounded.OpenInNew, null)
                                Spacer(Modifier.width(7.dp))
                                Text("Studiază sistemul în OpenStax")
                            }
                            Text("Index educațional, nu atlas clinic validat. Nu utiliza fișe incomplete "
                                + "sau ilustrații generate AI pentru decizii medicale.",
                                fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}
