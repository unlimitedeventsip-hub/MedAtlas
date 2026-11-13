package ro.medatlas.study

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ro.medatlas.study.data.AtomicLocalFiles
import ro.medatlas.study.data.SafeStorageNames
import java.io.File
import java.util.concurrent.TimeUnit

internal data class ScanRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun json() = JSONArray().put(left.toDouble()).put(top.toDouble())
        .put(right.toDouble()).put(bottom.toDouble())

    companion object {
        fun parse(value: JSONArray) = ScanRegion(
            value.getDouble(0).toFloat(), value.getDouble(1).toFloat(),
            value.getDouble(2).toFloat(), value.getDouble(3).toFloat()
        )
    }
}

internal data class ScanTextLine(
    val text: String,
    val area: ScanRegion,
    val language: String,
    val confidence: Float,
    val angle: Float
) {
    fun json() = JSONObject().apply {
        put("text", text); put("area", area.json())
        put("language", language); put("confidence", confidence.toDouble())
        put("angle", angle.toDouble())
    }
    companion object {
        fun parse(value: JSONObject) = ScanTextLine(
            text = value.optString("text"),
            area = ScanRegion.parse(value.getJSONArray("area")),
            language = value.optString("language", "und"),
            confidence = value.optDouble("confidence", 0.0).toFloat(),
            angle = value.optDouble("angle", 0.0).toFloat()
        )
    }
}

internal data class ScanCode(
    val type: String,
    val value: String,
    val area: ScanRegion?
) {
    fun json() = JSONObject().apply {
        put("type", type); put("value", value)
        if (area != null) put("area", area.json())
    }
    companion object {
        fun parse(value: JSONObject) = ScanCode(
            type = value.optString("type"),
            value = value.optString("value"),
            area = value.optJSONArray("area")?.let(ScanRegion::parse)
        )
    }
}

internal data class SmartScanReport(
    val text: String,
    val lines: List<ScanTextLine>,
    val codes: List<ScanCode>,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun json() = JSONObject().apply {
        put("schema", 1)
        put("text", text)
        put("createdAt", createdAt)
        put("lines", JSONArray().apply { lines.forEach { put(it.json()) } })
        put("codes", JSONArray().apply { codes.forEach { put(it.json()) } })
    }
    companion object {
        fun parse(value: JSONObject): SmartScanReport {
            require(value.optInt("schema") == 1) { "Index OCR necunoscut." }
            val textLines = value.optJSONArray("lines") ?: JSONArray()
            val codeList = value.optJSONArray("codes") ?: JSONArray()
            return SmartScanReport(
                text = value.optString("text"),
                lines = (0 until textLines.length()).map {
                    ScanTextLine.parse(textLines.getJSONObject(it))
                },
                codes = (0 until codeList.length()).map {
                    ScanCode.parse(codeList.getJSONObject(it))
                },
                createdAt = value.optLong("createdAt")
            )
        }
    }
}

/**
 * All inference is local on the device. Neither camera access nor an account
 * is needed: the user explicitly scans a previously opened document page.
 * Never automatically open a URL encoded in a QR code.
 */
internal object SmartDocumentScanner {
    private fun region(
        left: Int, top: Int, right: Int, bottom: Int, width: Int, height: Int
    ) = ScanRegion(
        left.toFloat().div(width).coerceIn(0f, 1f),
        top.toFloat().div(height).coerceIn(0f, 1f),
        right.toFloat().div(width).coerceIn(0f, 1f),
        bottom.toFloat().div(height).coerceIn(0f, 1f)
    )

    suspend fun scan(bitmap: Bitmap): SmartScanReport = withContext(Dispatchers.Default) {
        require(bitmap.width > 0 && bitmap.height > 0 && !bitmap.isRecycled) {
            "Imaginea de scanat nu este disponibilă."
        }
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val options = BarcodeScannerOptions.Builder().setBarcodeFormats(
            Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX,
            Barcode.FORMAT_CODE_128, Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_PDF417
        ).build()
        val scanner = BarcodeScanning.getClient(options)
        try {
            val textResult = Tasks.await(recognizer.process(image), 60, TimeUnit.SECONDS)
            val codesResult = Tasks.await(scanner.process(image), 45, TimeUnit.SECONDS)
            val lines = textResult.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                val bounds = line.boundingBox ?: return@mapNotNull null
                ScanTextLine(
                    text = line.text,
                    area = region(bounds.left, bounds.top, bounds.right, bounds.bottom,
                        bitmap.width, bitmap.height),
                    language = line.recognizedLanguage,
                    confidence = line.confidence.coerceIn(0f, 1f),
                    angle = line.angle
                )
            }
            val codes = codesResult.mapNotNull { barcode ->
                val raw = barcode.rawValue?.trim()?.take(2000)
                if (raw.isNullOrBlank()) return@mapNotNull null
                val bounds = barcode.boundingBox
                ScanCode(
                    type = when (barcode.format) {
                        Barcode.FORMAT_QR_CODE -> "QR"
                        Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
                        Barcode.FORMAT_CODE_128 -> "Code 128"
                        Barcode.FORMAT_EAN_13 -> "EAN-13"
                        Barcode.FORMAT_PDF417 -> "PDF417"
                        else -> "Cod"
                    },
                    value = raw,
                    area = bounds?.let { region(it.left, it.top, it.right, it.bottom,
                        bitmap.width, bitmap.height) }
                )
            }
            SmartScanReport(textResult.text, lines, codes)
        } finally {
            recognizer.close()
            scanner.close()
        }
    }

    fun save(context: Context, documentId: String, page: Int, report: SmartScanReport) {
        require(page >= 0)
        val safeId = SafeStorageNames.id(documentId)
        val destination = File(context.filesDir,
            "medatlas/ocr/${safeId}_${page}.layout.json")
        AtomicLocalFiles.writeText(destination, report.json().toString())
    }

    fun cached(context: Context, documentId: String, page: Int): SmartScanReport? {
        if (page < 0) return null
        val safeId = SafeStorageNames.id(documentId)
        val destination = File(context.filesDir, "medatlas/ocr/${safeId}_${page}.layout.json")
        return runCatching {
            SmartScanReport.parse(JSONObject(AtomicLocalFiles.readText(destination)))
        }.getOrNull()
    }
}
