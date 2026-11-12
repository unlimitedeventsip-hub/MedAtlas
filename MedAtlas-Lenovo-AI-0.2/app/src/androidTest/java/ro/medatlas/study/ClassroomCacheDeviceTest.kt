package ro.medatlas.study

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ro.medatlas.study.classroom.ClassroomSync

@RunWith(AndroidJUnit4::class)
class ClassroomCacheDeviceTest {
    @Test fun displaysPreviouslySynchronizedCoursesOffline() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("classroom_sync", Context.MODE_PRIVATE)
        val keys = listOf("cached_courses", "cached_materials", "last_sync_ms",
            "last_sync_error", "connected")
        val saved = keys.associateWith { key -> prefs.all[key] }
        try {
            val course = JSONObject().put("id", "101").put("courseId", "")
                .put("name", "Pneumologie").put("detail", "Anul III")
                .put("link", "").put("kind", "Curs").put("driveId", "")
            val material = JSONObject().put("id", "101_material_2").put("courseId", "101")
                .put("name", "BPOC și astm").put("detail", "Curs pentru examen")
                .put("link", "").put("kind", "Material").put("driveId", "")
            prefs.edit().putString("cached_courses", JSONArray().put(course).toString())
                .putString("cached_materials", JSONArray().put(material).toString())
                .putLong("last_sync_ms", 1712345678000L).putBoolean("connected", true)
                .remove("last_sync_error").commit()

            val snapshot = ClassroomSync.cached(context)
            assertEquals("Pneumologie", snapshot.courses.single().name)
            assertEquals("101", snapshot.materials.single().courseId)
            assertEquals("BPOC și astm", snapshot.materials.single().name)
            assertEquals(1712345678000L, snapshot.syncedAt)
            assertTrue(snapshot.connected)
            assertTrue(snapshot.error.isBlank())
        } finally {
            val editor = prefs.edit()
            saved.forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Long -> editor.putLong(key, value)
                    is String -> editor.putString(key, value)
                    is Int -> editor.putInt(key, value)
                    else -> editor.remove(key)
                }
            }
            editor.commit()
        }
    }

    @Test fun damagedCacheNeverCrashesClassroomScreen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("classroom_sync", Context.MODE_PRIVATE)
        val original = prefs.getString("cached_courses", null)
        try {
            prefs.edit().putString("cached_courses", "not-json").commit()
            assertFalse(ClassroomSync.cached(context).courses.isNotEmpty())
        } finally {
            prefs.edit().apply {
                if (original == null) remove("cached_courses")
                else putString("cached_courses", original)
            }.commit()
        }
    }
}
