package com.example.homeworkbuddy

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class Stats(
    val total: Int,
    val completed: Int,
    val pending: Int,
    val overdue: Int,
    val tracked: Int,
    val onTime: Int,
    val score: Int,            // -1 = not enough data
    val avgDelayMin: Long,     // minutes after (+) or before (-) the reminder
    val snoozes: Int,
    val streak: Int,
    val subjectRates: Map<String, Int>,
    val overdueSubjects: List<String>
)

/** Offline homework assistant: understands simple commands and reports progress. */
object Copilot {
    /** Finished within 3 hours of the reminder = on time. */
    const val GRACE_MS = 3 * 60 * 60 * 1000L

    private val fmt = SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault())

    private val ALIASES = mapOf(
        "english" to "English",
        "math" to "Math", "maths" to "Math", "mathematics" to "Math",
        "biology" to "Biology", "bio" to "Biology",
        "physics" to "Physics",
        "chemistry" to "Chemistry", "chem" to "Chemistry",
        "ict" to "IT", "computer" to "IT",
        "agriculture" to "Agriculture", "agric" to "Agriculture", "agri" to "Agriculture",
        "cte" to "CTE"
    )

    private val DAYS = mapOf(
        "sunday" to 1, "sun" to 1, "monday" to 2, "mon" to 2,
        "tuesday" to 3, "tue" to 3, "tues" to 3, "wednesday" to 4, "wed" to 4,
        "thursday" to 5, "thu" to 5, "thur" to 5, "thurs" to 5,
        "friday" to 6, "fri" to 6, "saturday" to 7, "sat" to 7
    )

    // ------------------------------------------------------------ stats
    fun stats(c: Context): Stats {
        val now = System.currentTimeMillis()
        val all = Store.all(c)
        val doneList = all.filter { it.done }
        val pendingList = all.filter { !it.done }
        val overdueList = pendingList.filter { it.dueAt < now }
        val tracked = doneList.filter { it.doneAt > 0 }
        val onTime = tracked.count { it.doneAt <= it.dueAt + GRACE_MS }
        val score = if (tracked.isEmpty()) -1 else onTime * 100 / tracked.size
        val avg = if (tracked.isEmpty()) 0L
        else tracked.map { (it.doneAt - it.dueAt) / 60000 }.average().toLong()
        val streak = tracked.sortedByDescending { it.doneAt }
            .takeWhile { it.doneAt <= it.dueAt + GRACE_MS }.size
        val rates = tracked.groupBy { it.subject }.mapValues { e ->
            e.value.count { it.doneAt <= it.dueAt + GRACE_MS } * 100 / e.value.size
        }
        return Stats(
            all.size, doneList.size, pendingList.size, overdueList.size,
            tracked.size, onTime, score, avg, all.sumOf { it.snoozes }, streak,
            rates, overdueList.map { it.subject }.distinct()
        )
    }

    /** One short line for the card on the main screen. */
    fun headline(c: Context): String {
        val s = stats(c)
        return when {
            s.total == 0 -> "No homework yet. Tap to chat with me."
            s.overdue > 0 -> "⚠️ ${s.overdue} overdue. Tap to see what to do first."
            s.score >= 0 -> "Punctuality ${s.score}% · ${s.pending} pending"
            else -> "${s.pending} pending. Tap to ask me anything."
        }
    }

    // ------------------------------------------------------------ helpers
    private fun words(t: String): Set<String> =
        t.split(Regex("[^a-z0-9']+")).filter { it.isNotEmpty() }.toSet()

    private fun has(t: String, w: Set<String>, vararg keys: String): Boolean =
        keys.any { if (it.contains(' ') || it.contains('-')) t.contains(it) else it in w }

    private fun span(min: Long): String {
        val m = abs(min)
        return if (m < 60) "$m min" else "${m / 60} h ${m % 60} min"
    }

    private fun clock(h: Int, m: Int): String {
        val ap = if (h < 12) "AM" else "PM"
        val hh = if (h % 12 == 0) 12 else h % 12
        return "%d:%02d %s".format(hh, m, ap)
    }

    private fun label(score: Int) = when {
        score >= 85 -> "🌟 Excellent"
        score >= 70 -> "👍 Good"
        score >= 50 -> "🙂 Fair"
        else -> "💪 Needs work"
    }

    private fun subjectIn(raw: String): String? {
        val ws = raw.lowercase(Locale.ROOT).split(Regex("[^a-z0-9]+"))
        ws.firstNotNullOfOrNull { ALIASES[it] }?.let { return it }
        return if (Regex("""\bIT\b""").containsMatchIn(raw)) "IT" else null
    }

    private fun parseTime(t: String): Pair<Int, Int>? {
        Regex("""\b(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b""").find(t)?.let { m ->
            var h = m.groupValues[1].toInt()
            val mi = m.groupValues[2].ifEmpty { "0" }.toInt()
            val ap = m.groupValues[3]
            if (h !in 1..12 || mi > 59) return null
            if (ap == "pm" && h < 12) h += 12
            if (ap == "am" && h == 12) h = 0
            return h to mi
        }
        Regex("""\b([01]?\d|2[0-3]):([0-5]\d)\b""").find(t)?.let { m ->
            return m.groupValues[1].toInt() to m.groupValues[2].toInt()
        }
        if ("noon" in t) return 12 to 0
        return null
    }

    private fun resolveDue(t: String): Long? {
        val now = System.currentTimeMillis()
        Regex("""\bin (\d{1,3})\s*(min|mins|minute|minutes|hour|hours|hr|hrs)\b""").find(t)?.let { m ->
            val n = m.groupValues[1].toLong()
            val mult = if (m.groupValues[2].startsWith("h")) 60L else 1L
            return now + n * mult * 60_000L
        }
        val tm = parseTime(t) ?: (if ("tonight" in t) 19 to 0 else null) ?: return null
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, tm.first)
            set(Calendar.MINUTE, tm.second)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val w = words(t)
        var weekdayGiven = false
        var offset: Int? = null
        when {
            "day after tomorrow" in t -> offset = 2
            "tomorrow" in w -> offset = 1
            "today" in w || "tonight" in w -> offset = 0
            else -> {
                val d = w.firstNotNullOfOrNull { DAYS[it] }
                if (d != null) {
                    offset = (d - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
                    weekdayGiven = true
                }
            }
        }
        if (offset != null) cal.add(Calendar.DAY_OF_YEAR, offset)
        if (cal.timeInMillis <= now) {
            if (offset == null) cal.add(Calendar.DAY_OF_YEAR, 1)
            else if (weekdayGiven) cal.add(Calendar.DAY_OF_YEAR, 7)
            else return null
        }
        return cal.timeInMillis
    }

    // ------------------------------------------------------------ main entry
    fun reply(c: Context, raw: String): String {
        val t = raw.lowercase(Locale.ROOT).trim()
        val w = words(t)
        if (t.isEmpty()) return HELP
        return when {
            t == "help" || t == "?" || has(t, w, "commands", "what can you do", "how do i use") -> HELP
            t.length <= 20 && has(t, w, "hello", "hi", "hey", "salam", "yo", "good morning", "good afternoon", "good evening") ->
                "Hi there! 👋 ${headline(c)}"
            has(t, w, "thanks", "thank you", "thx") -> "You're welcome! 😊 Keep it up."
            has(t, w, "clear") && has(t, w, "done", "finished", "completed") -> clearDone(c)
            has(t, w, "report", "progress", "punctual", "punctuality", "stats", "performance", "how am i doing", "how am i") -> report(c)
            has(t, w, "tip", "tips", "advice", "suggest", "improve") -> tips(c)
            has(t, w, "add", "remind me", "new homework", "schedule") -> add(c, raw)
            has(t, w, "delete", "remove", "cancel") -> delete(c, raw)
            has(t, w, "done", "finished", "finish", "completed", "complete") -> markDone(c, raw)
            "morning" in w && has(t, w, "reminder", "remind", "time", "set", "change") -> setTime(c, t, true)
            has(t, w, "after school", "after-school", "afterschool") -> setTime(c, t, false)
            t.contains("today") && has(t, w, "subject", "subjects", "class", "classes", "school") -> today(c)
            has(t, w, "due", "pending", "homework", "assignment", "assignments", "todo", "list", "next", "upcoming", "overdue") -> due(c)
            else -> "I didn't quite get that 🤔\nTry: \"report\", \"what's due\", \"add math tomorrow at 5pm\" or \"finished physics\".\nSay \"help\" to see everything."
        }
    }

    // ------------------------------------------------------------ actions
    private fun add(c: Context, raw: String): String {
        val parts = raw.split(Regex("(?i)\\s+about\\s+"), 2)
        val head = parts[0]
        val note = parts.getOrNull(1)?.trim().orEmpty()
        val subject = subjectIn(head)
            ?: return "Which subject? Try: \"add math tomorrow at 5pm\""
        val due = resolveDue(head.lowercase(Locale.ROOT))
            ?: return "I need a future time. Try \"add $subject tomorrow at 5pm\" or \"add $subject in 2 hours\"."
        val hw = Homework(
            (System.currentTimeMillis() and 0x7fffffff).toInt(),
            subject, note, due, false
        )
        Store.add(c, hw)
        Scheduler.scheduleHomework(c, hw)
        return "✅ Added $subject homework.\n⏰ I'll ring you on ${fmt.format(Date(due))}" +
            if (note.isNotBlank()) "\n📝 $note" else ""
    }

    private fun markDone(c: Context, raw: String): String {
        val s = subjectIn(raw) ?: return "Which subject did you finish? Try: \"finished math\""
        val hw = Store.all(c).filter { !it.done && it.subject == s }.minByOrNull { it.dueAt }
            ?: return "You have no pending $s homework. 👍"
        Store.setDone(c, hw.id, true)
        Scheduler.cancelHomework(c, hw.id)
        val onTime = System.currentTimeMillis() <= hw.dueAt + GRACE_MS
        return if (onTime) "🎉 Nice! $s is done, right on time."
        else "✅ $s is done. A bit late this time, but finished is what counts."
    }

    private fun delete(c: Context, raw: String): String {
        val s = subjectIn(raw) ?: return "Which subject should I delete? Try: \"delete math\""
        val hw = Store.all(c).filter { !it.done && it.subject == s }.minByOrNull { it.dueAt }
            ?: return "You have no pending $s homework to delete."
        Scheduler.cancelHomework(c, hw.id)
        Store.delete(c, hw.id)
        return "🗑️ Deleted $s homework (${fmt.format(Date(hw.dueAt))})."
    }

    private fun clearDone(c: Context): String {
        val d = Store.all(c).filter { it.done }
        if (d.isEmpty()) return "Nothing to clear. No finished homework yet."
        d.forEach { Store.delete(c, it.id) }
        return "🧹 Cleared ${d.size} finished homework."
    }

    private fun setTime(c: Context, t: String, morning: Boolean): String {
        val tm = parseTime(t)
            ?: return "Tell me the time with am/pm, e.g. \"set morning reminder to 6:30 am\"."
        return if (morning) {
            Store.setMorning(c, tm.first, tm.second)
            Scheduler.scheduleMorning(c)
            "🌅 Morning reminder is now at ${clock(tm.first, tm.second)}."
        } else {
            Store.setAfterSchool(c, tm.first, tm.second)
            Scheduler.scheduleAfterSchool(c)
            "🏫 After-school question is now at ${clock(tm.first, tm.second)}."
        }
    }

    private fun today(c: Context): String {
        val s = Store.todaySubjects(c).sortedBy { Store.SUBJECTS.indexOf(it) }
        return if (s.isEmpty()) "You haven't picked today's subjects yet. Tap them on the main screen or set your weekly timetable."
        else "🎒 Today you have: ${s.joinToString(", ")}."
    }

    private fun due(c: Context): String {
        val now = System.currentTimeMillis()
        val p = Store.all(c).filter { !it.done }.sortedBy { it.dueAt }
        if (p.isEmpty()) return "🎉 You have no pending homework!"
        val sb = StringBuilder("📋 Pending homework (${p.size}):\n")
        p.take(8).forEach {
            val mark = if (it.dueAt < now) "⚠️ overdue" else "⏰"
            sb.append("• ${it.subject} — $mark ${fmt.format(Date(it.dueAt))}\n")
            if (it.note.isNotBlank()) sb.append("   📝 ${it.note}\n")
        }
        if (p.size > 8) sb.append("…and ${p.size - 8} more")
        return sb.toString().trimEnd()
    }

    // ------------------------------------------------------------ reports
    private fun insights(s: Stats): List<String> {
        val l = mutableListOf<String>()
        if (s.overdue > 0)
            l += "You have ${s.overdue} overdue (${s.overdueSubjects.joinToString(", ")}). Finish those first."
        if (s.total > 0 && s.snoozes.toDouble() / s.total > 1.0)
            l += "You snooze a lot. Start right when the alarm rings, because the first 5 minutes are the hardest."
        if (s.score in 0..59)
            l += "Your punctuality is low. Set reminders for times when you're really free, like right after school."
        if (s.score >= 90)
            l += "Amazing consistency! Keep the streak going. 🔥"
        s.subjectRates.filter { it.value < 100 }.minByOrNull { it.value }?.let {
            l += "${it.key} is your weakest subject for punctuality (${it.value}% on time). Give it an earlier slot."
        }
        if (l.isEmpty()) l += "Add each homework as soon as you get it so your reminders are always ready."
        return l
    }

    fun report(c: Context): String {
        val s = stats(c)
        if (s.total == 0)
            return "📊 No homework recorded yet. Add some and I'll start tracking how punctual you are!"
        val sb = StringBuilder("📊 Your progress report\n\n")
        if (s.score >= 0) {
            sb.append("Punctuality score: ${s.score}%  ${label(s.score)}\n")
            sb.append("(on time = finished within 3 hours of the reminder)\n\n")
        } else {
            sb.append("Punctuality score: not enough data yet. Finish a homework and I'll calculate it.\n\n")
        }
        sb.append("✅ Finished: ${s.completed}\n")
        sb.append("⏳ Pending: ${s.pending}\n")
        if (s.overdue > 0) sb.append("⚠️ Overdue: ${s.overdue}\n")
        if (s.tracked > 0) {
            val dir = if (s.avgDelayMin >= 0) "after" else "before"
            sb.append("🕒 You usually finish ${span(s.avgDelayMin)} $dir the reminder\n")
            sb.append("🔥 On-time streak: ${s.streak}\n")
        }
        sb.append("😴 Snoozes: ${s.snoozes}\n")
        s.subjectRates.maxByOrNull { it.value }?.let {
            sb.append("\n🏆 Best subject: ${it.key} (${it.value}% on time)")
        }
        sb.append("\n\n💡 ").append(insights(s).take(2).joinToString("\n💡 "))
        return sb.toString()
    }

    private fun tips(c: Context): String {
        val s = stats(c)
        return "💡 My tips for you:\n\n" + insights(s).joinToString("\n\n") { "• $it" }
    }

    private const val HELP = """Here's what I can do 🤖

📊 "report": punctuality & progress
📋 "what's due": pending homework
➕ "add math tomorrow at 5pm about chapter 3"
➕ "add physics in 2 hours"
✅ "finished chemistry"
🗑️ "delete english"
🎒 "today's subjects"
⏰ "set morning reminder to 6:30 am"
🏫 "set after school to 3:45 pm"
🧹 "clear finished"
💡 "tips""""
}
