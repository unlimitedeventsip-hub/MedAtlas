package ro.medatlas.study.anatomy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ro.medatlas.study.R

private data class Plate(
    val drawable: Int, val caption: String, val prompt: String, val legend: String, val file: String
)

private val heart = Plate(R.drawable.gray_heart, "Inima și vasele mari · vedere anterioară",
    "Identifică vasul roșu curbat și vasul albastru anterior de el.",
    "Crosa aortei continuă aorta ascendentă. Trunchiul pulmonar pornește din ventriculul drept și se bifurcă sub crosă.",
    "Gray505.png")
private val lung = Plate(R.drawable.gray_lung, "Lobul pulmonar · secțiune schematică",
    "Urmărește traseul aerului de la bronhiola respiratorie până la sacii alveolari.",
    "r.b. — bronhiolă respiratorie · al.d. — duct alveolar · a.s. — sac alveolar. Vasele însoțesc regiunea de schimb gazos.",
    "Gray975.png")
private val kidney = Plate(R.drawable.gray_kidney, "Rinichiul · secțiune longitudinală",
    "Identifică reperele 2, 5 și 6 înainte de a deschide legenda.",
    "1 Suprarenală · 2 Piramidă renală · 3 Hil renal · 4 Papilă · 5 Bazinet · 6 Cortex · 7 Ureter",
    "Kidney-numbered.png")

@Composable
internal fun AtlasPlate(organ: String) {
    val plate = when (organ) {
        "Inimă", "Aortă", "Arteră pulmonară", "Ventricul drept", "Artere coronare" -> heart
        "Plămân drept", "Plămân stâng", "Alveole", "Bronhiole" -> lung
        "Rinichi drept", "Rinichi stâng", "Cortex renal", "Medulă renală", "Bazinet", "Suprarenale" -> kidney
        else -> return
    }
    var showLegend by rememberSaveable(organ) { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(plate.caption, style = MaterialTheme.typography.titleMedium)
            Image(painterResource(plate.drawable), plate.caption, Modifier.fillMaxWidth().height(350.dp))
            Text(plate.prompt)
            OutlinedButton(onClick = { showLegend = !showLegend }) {
                Text(if (showLegend) "Ascunde legenda" else "Verifică reperele")
            }
            AnimatedVisibility(showLegend) { Text(plate.legend) }
            TextButton(onClick = { uri.openUri("https://commons.wikimedia.org/wiki/File:" + plate.file) }) {
                Text(if (plate == kidney) "Gray · planșă numerotată de Daedalus · sursă"
                    else "Gray · Anatomy of the Human Body (1918) · sursă")
            }
        }
    }
}
