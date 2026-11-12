package ro.medatlas.study.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** The API key stays on the owner-managed backend, never in the Android application. */
object AiClient {
    private const val PREFS = "medatlas_ai"
    const val DEFAULT_ENDPOINT = "https://medatlas-nadia-ai.unlimited-events-ip.workers.dev"
    fun endpoint(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString("endpoint", DEFAULT_ENDPOINT).orEmpty().ifBlank { DEFAULT_ENDPOINT }
    fun token(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("token", "").orEmpty()
    fun configure(context: Context, endpoint: String, token: String) {
        val base = endpoint.trim().trimEnd('/').ifBlank { DEFAULT_ENDPOINT }
        val uri = runCatching { java.net.URI(base) }.getOrNull()
        require(uri != null && uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
            uri.query == null && uri.fragment == null) { "Introdu o adresă HTTPS validă." }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("endpoint", base)
            .putString("token", token.trim()).apply()
    }
    fun configured(context: Context) = endpoint(context).startsWith("https://") && token(context).isNotBlank()

    suspend fun ask(context: Context, mode: String, text: String, page: Int?, document: String, image: Bitmap? = null): String = withContext(Dispatchers.IO) {
        val request = baseRequest(context, mode, text, page, document, image)
        val response = perform(context, request)
        response.optString("answer").ifBlank { throw IllegalStateException("Nu am primit explicația AI.") }
    }

    suspend fun draw(context: Context, prompt: String, image: Bitmap? = null): Bitmap = withContext(Dispatchers.IO) {
        val response = perform(context, baseRequest(context, "sticker", prompt, null, "Desen medical", image))
        val encoded = response.optString("image_b64")
        require(encoded.isNotBlank()) { "Nu a fost returnată ilustrația." }
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("Formatul imaginii nu este acceptat.")
    }

    private fun baseRequest(context: Context, mode: String, text: String, page: Int?, document: String, image: Bitmap?): JSONObject = JSONObject().apply {
        put("mode", mode)
        put("document", document.take(160))
        put("page", page ?: JSONObject.NULL)
        put("text", text.take(60000))
        if (image != null) {
            val scaled = if (image.width > 1200 || image.height > 1200) {
                val ratio = 1200f / maxOf(image.width, image.height)
                Bitmap.createScaledBitmap(image, (image.width * ratio).toInt().coerceAtLeast(1), (image.height * ratio).toInt().coerceAtLeast(1), true)
            } else image
            val stream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 78, stream)
            put("image_b64", Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP))
            if (scaled !== image) scaled.recycle()
        }
    }

    private fun perform(context: Context, request: JSONObject): JSONObject {
        val address = endpoint(context)
        require(address.startsWith("https://") && token(context).isNotBlank()) { "Configurează mai întâi backend-ul AI din Setări." }
        val connection = URL("$address/ai").openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.requestMethod = "POST"
        connection.connectTimeout = 15000
        connection.readTimeout = if (request.optString("mode") == "sticker") 180000 else 90000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Authorization", "Bearer ${token(context)}")
        try {
            connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val payload = (if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val result = runCatching { JSONObject(payload) }.getOrElse { JSONObject().put("error", "Răspuns invalid de la server ($status)") }
            if (status !in 200..299) throw IllegalStateException(result.optString("error", "Cerere AI nereușită ($status)"))
            return result
        } finally { connection.disconnect() }
    }
}
