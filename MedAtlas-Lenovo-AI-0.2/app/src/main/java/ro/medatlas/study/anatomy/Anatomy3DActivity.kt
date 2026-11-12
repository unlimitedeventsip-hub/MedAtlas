package ro.medatlas.study.anatomy

import ro.medatlas.study.GlassTokens
import ro.medatlas.study.MedAtlasGlassTheme

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * External educational anatomy atlases displayed in a dedicated browser surface.
 * Anatomy meshes and provider content are NOT packaged into MedAtlas.
 */
class Anatomy3DActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val providers = listOf(
                "OpenDissect · 3D / ECG / CT" to "https://opendissect.com/en/",
                "Ana3D · anatomie în straturi" to "https://ana3d.app/en/",
                "Cogni Anatomy · structuri 3D" to "https://cogni.cards/anatomy/"
            )
            var selected by remember { mutableIntStateOf(0) }
            var viewer by remember { mutableStateOf<WebView?>(null) }
            MedAtlasGlassTheme {
                Column(Modifier.fillMaxSize().background(GlassTokens.LightPage).statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(6.dp)) {
                        IconButton(onClick = { finish() }) { Icon(Icons.Rounded.ArrowBack, "Înapoi") }
                        Column(Modifier.weight(1f)) {
                            Text("Anatomie 3D", style = MaterialTheme.typography.titleLarge)
                            Text("Atlase externe · model educațional, nu instrument diagnostic", fontSize = 10.sp)
                        }
                        IconButton(onClick = {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(providers[selected].second)))
                        }) { Icon(Icons.Rounded.OpenInNew, "Deschide în browser") }
                    }
                    ScrollableTabRow(selectedTabIndex = selected, edgePadding = 4.dp) {
                        providers.forEachIndexed { i, provider ->
                            Tab(selected = i == selected, onClick = { selected = i },
                                text = { Text(provider.first, fontSize = 11.sp) })
                        }
                    }
                    key(selected) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.allowFileAccess = false
                                    settings.allowContentAccess = false
                                    settings.setSupportZoom(true)
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?, request: android.webkit.WebResourceRequest?
                                        ): Boolean {
                                            val target = request?.url ?: return false
                                            val host = target.host.orEmpty()
                                            if (host in listOf("opendissect.com", "ana3d.app",
                                                    "cogni.cards", "www.opendissect.com")) return false
                                            startActivity(Intent(Intent.ACTION_VIEW, target))
                                            return true
                                        }
                                    }
                                    loadUrl(providers[selected].second)
                                    viewer = this
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            update = { viewer = it }
                        )
                    }
                }
            }
        }
    }
    override fun onDestroy() { super.onDestroy() }
}
