package ro.medatlas.study.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Only student workbook data is part of this entry. Account and AI settings have separate storage. */
internal class ClinicalBackup(context: Context) {
    private val prefs = context.getSharedPreferences("clinical-workbook", Context.MODE_PRIVATE)

    fun encode(): String {
        val fields = JSONObject()
        prefs.all.forEach { (key, value) ->
            fields.put(key, if (value is Set<*>) JSONArray(value.toList()) else value)
        }
        return JSONObject().put("version", 1).put("fields", fields).toString()
    }

    fun prepare(text: String): android.content.SharedPreferences.Editor {
        val archive = JSONObject(text)
        require(archive.getInt("version") == 1)
        val fields = archive.getJSONObject("fields")
        val editor = prefs.edit().clear()
        for (key in fields.keys()) {
            val value = fields.get(key)
            when (key.substringAfterLast('.')) {
                "visit", "decision", "choice", "reassessment" -> {
                    require(value is Int && value in (if (key.endsWith(".visit")) 0..2 else -1..3))
                    editor.putInt(key, value)
                }
                "feedback", "opened" -> { require(value is Boolean); editor.putBoolean(key, value) }
                "history", "tests" -> {
                    require(value is JSONArray)
                    editor.putStringSet(key, (0 until value.length()).map { value.getString(it) }.toSet())
                }
                else -> { require(value is String); editor.putString(key, value) }
            }
        }
        return editor
    }
}
