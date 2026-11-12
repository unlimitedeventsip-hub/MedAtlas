package ro.medatlas.study.reference

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable internal fun BloodPressureStation() {
    val steps = listOf(
        Triple("Pacientul tocmai a urcat scările. Pregătește măsurarea în repaus.", "Repaus în șezut cel puțin 5 minute", "Măsurare imediată în picioare"),
        Triple("Poziționează membrul superior.", "Braț sprijinit la nivelul inimii", "Braț lăsat să atârne"),
        Triple("Alege montarea manșetei potrivite circumferinței.", "Pe brațul dezgolit", "Peste mâneca groasă"),
        Triple("Prima citire fictivă este 128/78 mmHg.", "Repetă după un minut de repaus", "Înregistrează doar valoarea preferată")
    )
    var stage by rememberSaveable { mutableIntStateOf(0) }
    var errors by rememberSaveable { mutableIntStateOf(0) }
    var hint by rememberSaveable { mutableStateOf("") }
    Text("Stație practică · măsurarea tensiunii arteriale",style=MaterialTheme.typography.titleMedium)
    if (stage in steps.indices) {
        val (prompt,appropriate,other) = steps[stage]
        Text(prompt)
        val options = if (stage % 2 == 0) listOf(other,appropriate) else listOf(appropriate,other)
        options.forEach { option ->
            OutlinedButton(onClick={
                if (option == appropriate) { stage++; hint="" }
                else { errors++; hint=listOf("Efortul recent poate modifica valoarea.", "Poziția brațului influențează citirea.",
                    "Îmbrăcămintea împiedică aplicarea corectă.", "Două citiri separate ajută la estimarea variabilității.")[stage] }
            }) { Text(option) }
        }
        if (hint.isNotEmpty()) Text(hint)
    } else {
        Text("Buletin fictiv · 128/78 și 124/76 mmHg. Media este 126/77 mmHg.")
        Text("Patru etape încheiate · $errors decizii corectate")
        TextButton(onClick={ stage=0;errors=0;hint="" }) { Text("Reia procedura") }
    }
    val link=androidx.compose.ui.platform.LocalUriHandler.current
    TextButton(onClick={link.openUri("https://www.heart.org/en/health-topics/high-blood-pressure/blood-pressure-explained/")}) {
        Text("Metoda de măsurare · American Heart Association")
    }
}
