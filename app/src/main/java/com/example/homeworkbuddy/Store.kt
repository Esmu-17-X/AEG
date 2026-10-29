package com.example.homeworkbuddy

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Homework(
    val id: Int,
    val subject: String,
    val note: String,
    val dueAt: Long,
    val done: Boolean
)

object Store {
    val SUBJECTS = listOf(
        "English", "Math", "Biology", "Physics",
        "Chemistry", "IT", "Agriculture", "CTE"
    )

    private fun prefs(c: Context) = c.getSharedPreferences("hw", Context.MODE_PRIVATE)

    // ---- today's subjects (the boxes you tick in the morning) ----
    fun todaySubjects(c: Context): Set<String> =
        prefs(c).getStringSet("today", emptySet()) ?: emptySet()

    fun setTodaySubjects(c: Context, s: Set<String>) =
        prefs(c).edit().putStringSet("today", s).apply()

    // ---- weekly timetable (Calendar.SUNDAY=1 ... SATURDAY=7) ----
    fun timetable(c: Context, dow: Int): Set<String> =
        prefs(c).getStringSet("tt_$dow", emptySet()) ?: emptySet()

    fun setTimetable(c: Context, dow: Int, s: Set<String>) =
        prefs(c).edit().putStringSet("tt_$dow", s).apply()

    /** Once per day, tick today's boxes from the timetable (if one is saved). */
    fun autoFill(c: Context) {
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        if (prefs(c).getString("fill", "") == today) return
        val tt = timetable(c, Calendar.getInstance().get(Calendar.DAY_OF_WEEK))
        if (tt.isNotEmpty()) setTodaySubjects(c, tt)
        prefs(c).edit().putString("fill", today).apply()
    }

    // ---- reminder times ----
    fun afterSchool(c: Context): Pair<Int, Int> =
        prefs(c).getInt("asH", 15) to prefs(c).getInt("asM", 30)

    fun setAfterSchool(c: Context, h: Int, m: Int) =
        prefs(c).edit().putInt("asH", h).putInt("asM", m).apply()

    fun morning(c: Context): Pair<Int, Int> =
        prefs(c).getInt("mH", 6) to prefs(c).getInt("mM", 30)

    fun setMorning(c: Context, h: Int, m: Int) =
        prefs(c).edit().putInt("mH", h).putInt("mM", m).apply()

    // ---- homework list / tracker ----
    fun all(c: Context): List<Homework> {
        val arr = JSONArray(prefs(c).getString("list", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Homework(
                o.getInt("id"), o.getString("subject"), o.getString("note"),
                o.getLong("due"), o.getBoolean("done")
            )
        }
    }

    private fun saveAll(c: Context, list: List<Homework>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("subject", it.subject)
                    .put("note", it.note).put("due", it.dueAt).put("done", it.done)
            )
        }
        prefs(c).edit().putString("list", arr.toString()).apply()
    }

    fun add(c: Context, hw: Homework) = saveAll(c, all(c) + hw)
    fun setDone(c: Context, id: Int, done: Boolean) =
        saveAll(c, all(c).map { if (it.id == id) it.copy(done = done) else it })
    fun setDue(c: Context, id: Int, due: Long) =
        saveAll(c, all(c).map { if (it.id == id) it.copy(dueAt = due) else it })
    fun delete(c: Context, id: Int) = saveAll(c, all(c).filter { it.id != id })
}
