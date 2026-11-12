package ro.medatlas.study.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class Tool { PEN, BRUSH, HIGHLIGHT, UNDERLINE, WAVE, CIRCLE, RECTANGLE, ARROW, LASSO, ERASER, ANALYZE, PAN, STICKER, COMMENT, TEXT }

data class InkPoint(val x: Float, val y: Float, val pressure: Float = 1f) {
    fun json() = JSONArray().put(x.toDouble()).put(y.toDouble()).put(pressure.toDouble())
    companion object {
        fun parse(json: JSONArray) = InkPoint(json.getDouble(0).toFloat(), json.getDouble(1).toFloat(), json.optDouble(2, 1.0).toFloat())
    }
}

data class Stroke(
    val id: String = UUID.randomUUID().toString(),
    val tool: Tool = Tool.PEN,
    val color: Int = android.graphics.Color.rgb(31, 64, 60),
    val width: Float = 3f,
    val points: List<InkPoint> = emptyList()
) {
    fun json() = JSONObject().apply {
        put("id", id); put("tool", tool.name); put("color", color)
        put("width", width.toDouble())
        put("points", JSONArray().apply { points.forEach { put(it.json()) } })
    }
    companion object {
        fun parse(o: JSONObject) = Stroke(
            id = o.optString("id", UUID.randomUUID().toString()),
            tool = runCatching { Tool.valueOf(o.optString("tool", "PEN")) }.getOrDefault(Tool.PEN),
            color = o.optInt("color", android.graphics.Color.BLACK),
            width = o.optDouble("width", 3.0).toFloat(),
            points = o.optJSONArray("points")?.let { arr -> (0 until arr.length()).map { InkPoint.parse(arr.getJSONArray(it)) } }.orEmpty()
        )
    }
}

data class StudyDocument(
    val id: String,
    val title: String,
    val originalName: String,
    val kind: String,
    val localName: String,
    val importedAt: Long,
    val course: String = "Fără materie",
    val favorite: Boolean = false
) {
    fun json() = JSONObject().apply {
        put("id", id); put("title", title); put("originalName", originalName)
        put("kind", kind); put("localName", localName); put("importedAt", importedAt)
        put("course", course); put("favorite", favorite)
    }
    companion object {
        fun parse(o: JSONObject) = StudyDocument(
            o.getString("id"), o.optString("title"), o.optString("originalName"),
            o.optString("kind"), o.optString("localName"), o.optLong("importedAt"),
            o.optString("course", "Fără materie"), o.optBoolean("favorite")
        )
    }
}

data class StudyNote(
    val id: String = UUID.randomUUID().toString(), val documentId: String,
    val page: Int, val heading: String, val body: String, val createdAt: Long = System.currentTimeMillis(),
    val course: String = "", val handwritingFile: String = "", val updatedAt: Long = createdAt,
    val paper: String = "LINES", val darkPaper: Boolean = false
) {
    fun json() = JSONObject().apply { put("id", id); put("documentId", documentId); put("page", page); put("heading", heading); put("body", body); put("createdAt", createdAt); put("course", course); put("handwritingFile", handwritingFile); put("updatedAt", updatedAt); put("paper", paper); put("darkPaper", darkPaper) }
    companion object {
        fun parse(o: JSONObject) = StudyNote(o.getString("id"), o.getString("documentId"), o.optInt("page"), o.optString("heading"), o.optString("body"), o.optLong("createdAt"),
        o.optString("course"), o.optString("handwritingFile"), o.optLong("updatedAt", o.optLong("createdAt")), o.optString("paper", "LINES"), o.optBoolean("darkPaper"))
    }
}

data class Flashcard(
    val id: String = UUID.randomUUID().toString(), val documentId: String, val page: Int,
    val question: String, val answer: String, val dueAt: Long = 0L, val intervalDays: Int = 0
) {
    fun json() = JSONObject().apply { put("id", id); put("documentId", documentId); put("page", page); put("question", question); put("answer", answer); put("dueAt", dueAt); put("intervalDays", intervalDays) }
    companion object {
        fun parse(o: JSONObject) = Flashcard(o.getString("id"), o.getString("documentId"), o.optInt("page"), o.optString("question"), o.optString("answer"), o.optLong("dueAt"), o.optInt("intervalDays"))
    }
}


data class PageSticker(
    val id: String = UUID.randomUUID().toString(),
    val imageName: String,
    val sourceUrl: String = "",
    val attribution: String = "",
    val license: String = "",
    val x: Float = 0.25f,
    val y: Float = 0.25f,
    val size: Float = 0.32f
) {
    fun json() = JSONObject().apply {
        put("id", id); put("imageName", imageName); put("sourceUrl", sourceUrl); put("attribution", attribution); put("license", license); put("x", x.toDouble());
        put("y", y.toDouble()); put("size", size.toDouble())
    }
    companion object {
        fun parse(o: JSONObject) = PageSticker(
            id = o.optString("id", UUID.randomUUID().toString()),
            imageName = o.getString("imageName"),
            sourceUrl = o.optString("sourceUrl"),
            attribution = o.optString("attribution"),
            license = o.optString("license"),
            x = o.optDouble("x", 0.25).toFloat(),
            y = o.optDouble("y", 0.25).toFloat(),
            size = o.optDouble("size", 0.32).toFloat()
        )
    }
}

/** A margin comment anchored to normalized PDF coordinates, independent of zoom. */
data class PageComment(
    val id: String = UUID.randomUUID().toString(),
    val documentId: String,
    val page: Int,
    val x: Float,
    val y: Float,
    val body: String,
    val quote: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun json() = JSONObject().apply {
        put("id", id); put("documentId", documentId); put("page", page)
        put("x", x.toDouble()); put("y", y.toDouble())
        put("body", body); put("quote", quote); put("createdAt", createdAt)
    }
    companion object {
        fun parse(o: JSONObject) = PageComment(
            id = o.getString("id"), documentId = o.getString("documentId"),
            page = o.optInt("page"), x = o.optDouble("x").toFloat(),
            y = o.optDouble("y").toFloat(), body = o.optString("body"),
            quote = o.optString("quote"), createdAt = o.optLong("createdAt")
        )
    }
}

/** Editable text overlay anchored to normalized PDF coordinates. */
data class PageTextBox(
    val id: String = UUID.randomUUID().toString(),
    val documentId: String,
    val page: Int,
    val x: Float,
    val y: Float,
    val content: String,
    val sizeSp: Float = 17f,
    val color: Int = android.graphics.Color.rgb(24, 37, 53)
) {
    fun json() = JSONObject().apply {
        put("id", id); put("documentId", documentId); put("page", page)
        put("x", x.toDouble()); put("y", y.toDouble()); put("content", content)
        put("sizeSp", sizeSp.toDouble()); put("color", color)
    }
    companion object {
        fun parse(o: JSONObject) = PageTextBox(
            id = o.getString("id"), documentId = o.getString("documentId"),
            page = o.optInt("page"), x = o.optDouble("x").toFloat(),
            y = o.optDouble("y").toFloat(), content = o.optString("content"),
            sizeSp = o.optDouble("sizeSp", 17.0).toFloat(),
            color = o.optInt("color", android.graphics.Color.rgb(24, 37, 53))
        )
    }
}
