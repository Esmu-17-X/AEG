package com.example.homeworkbuddy

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private val fmt = SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault())
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private val days = arrayOf(
        "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
    )

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = Ui.PRIMARY
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        Notif.ensure(this)
        Store.autoFill(this)
        Scheduler.scheduleAfterSchool(this)
        Scheduler.scheduleMorning(this)

        val frame = FrameLayout(this)
        frame.setBackgroundColor(Ui.BG)

        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        scroll.addView(root)
        frame.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))

        val fab = Ui.button(this, "＋  Add homework", Ui.ACCENT, radius = 28) { addDialog() }
        fab.elevation = 8f * resources.displayMetrics.density
        val flp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.END)
        flp.setMargins(0, 0, dp(16), dp(20))
        fab.layoutParams = flp
        frame.addView(fab)

        setContentView(frame)
        render()
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) {
            Store.autoFill(this)
            Scheduler.rescheduleAll(this) // restores alarms if a restart wiped them
            render()
        }
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        setIntent(i)
        handleIntent(i)
    }

    private fun handleIntent(i: Intent) {
        if (i.getBooleanExtra("add", false)) {
            i.removeExtra("add")
            getSystemService(NotificationManager::class.java).cancel(2)
            addDialog()
        }
    }

    private fun dp(v: Int) = Ui.dp(this, v)

    private fun hm(h: Int, m: Int): String {
        val ap = if (h < 12) "AM" else "PM"
        val hh = if (h % 12 == 0) 12 else h % 12
        return "%d:%02d %s".format(hh, m, ap)
    }

    // ---------------- Header ----------------
    private fun headerView(pending: Int, overdue: Int, done: Int): LinearLayout {
        val ctx = this
        val h = LinearLayout(ctx)
        h.orientation = LinearLayout.VERTICAL
        h.setPadding(dp(20), dp(16), dp(20), dp(24))
        val g = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Ui.PRIMARY, Color.parseColor("#7E57C2"))
        )
        val r = dp(28).toFloat()
        g.cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
        h.background = g

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greet = when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
        h.addView(Ui.text(ctx, "$greet 👋", 14f, Color.parseColor("#CCFFFFFF")))
        h.addView(Ui.text(ctx, "Homework Buddy", 28f, Color.WHITE, true))

        val stats = LinearLayout(ctx)
        stats.orientation = LinearLayout.HORIZONTAL
        val slp = LinearLayout.LayoutParams(MATCH, WRAP)
        slp.setMargins(0, dp(16), 0, 0)
        stats.layoutParams = slp

        fun stat(n: Int, label: String, numColor: Int) {
            val box = LinearLayout(ctx)
            box.orientation = LinearLayout.VERTICAL
            box.gravity = Gravity.CENTER
            box.setPadding(dp(8), dp(12), dp(8), dp(12))
            box.background = Ui.rounded(ctx, Color.parseColor("#33FFFFFF"), 16)
            val lp = LinearLayout.LayoutParams(0, WRAP, 1f)
            lp.setMargins(dp(4), 0, dp(4), 0)
            box.layoutParams = lp
            val num = Ui.text(ctx, n.toString(), 26f, numColor, true)
            num.gravity = Gravity.CENTER
            val lab = Ui.text(ctx, label, 12f, Color.parseColor("#CCFFFFFF"))
            lab.gravity = Gravity.CENTER
            box.addView(num)
            box.addView(lab)
            stats.addView(box)
        }
        stat(pending, "Pending", Color.WHITE)
        stat(overdue, "Overdue", if (overdue > 0) Color.parseColor("#FF8A80") else Color.WHITE)
        stat(done, "Done", Color.parseColor("#B9F6CA"))
        h.addView(stats)
        return h
    }

    // ---------------- Main screen ----------------
    private fun render() {
        val ctx = this
        root.removeAllViews()

        val now = System.currentTimeMillis()
        val list = Store.all(ctx).sortedWith(compareBy({ it.done }, { it.dueAt }))
        val pending = list.count { !it.done }
        val overdue = list.count { !it.done && it.dueAt < now }
        val done = list.size - pending

        root.addView(headerView(pending, overdue, done))

        val body = LinearLayout(ctx)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(dp(16), dp(16), dp(16), dp(110))
        root.addView(body)

        // ---- Today's subjects ----
        val selected = Store.todaySubjects(ctx).toMutableSet()
        val c1 = Ui.card(ctx)
        c1.addView(Ui.text(ctx, "Today's subjects", 18f, Ui.TEXT, true))
        val hint = Ui.text(ctx, "Tap the subjects you have at school today", 13f, Ui.MUTED)
        hint.setPadding(0, 0, 0, dp(8))
        c1.addView(hint)
        c1.addView(Ui.chipGrid(ctx, Store.SUBJECTS, { it in selected }) { s, tv ->
            if (s in selected) selected.remove(s) else selected.add(s)
            Store.setTodaySubjects(ctx, selected.toSet())
            Ui.styleChip(ctx, tv, s in selected, Ui.subjectColor(s))
        })
        body.addView(c1)

        // ---- Settings ----
        val (mh, mm) = Store.morning(ctx)
        val (ah, am) = Store.afterSchool(ctx)
        val c2 = Ui.card(ctx)
        c2.setPadding(dp(16), dp(8), dp(16), dp(8))
        c2.addView(Ui.row(ctx, "🌅", "Morning reminder", hm(mh, mm)) {
            TimePickerDialog(ctx, { _, nh, nm ->
                Store.setMorning(ctx, nh, nm)
                Scheduler.scheduleMorning(ctx)
                render()
            }, mh, mm, false).show()
        })
        c2.addView(Ui.divider(ctx))
        c2.addView(Ui.row(ctx, "🏫", "After-school question", hm(ah, am)) {
            TimePickerDialog(ctx, { _, nh, nm ->
                Store.setAfterSchool(ctx, nh, nm)
                Scheduler.scheduleAfterSchool(ctx)
                render()
            }, ah, am, false).show()
        })
        c2.addView(Ui.divider(ctx))
        c2.addView(Ui.row(ctx, "🗓️", "Weekly timetable", null) { timetableDialog() })
        c2.addView(Ui.divider(ctx))
        c2.addView(Ui.row(ctx, "🔦", "Test alarm & flashlight", null) { testAlarm() })
        body.addView(c2)

        // ---- Tracker ----
        val title = Ui.text(ctx, "Tracker", 20f, Ui.TEXT, true)
        title.setPadding(dp(4), dp(8), 0, dp(8))
        body.addView(title)

        if (list.isNotEmpty()) {
            val pct = done * 100 / list.size
            val c3 = Ui.card(ctx)
            c3.addView(Ui.text(ctx, "$pct% finished  ($done of ${list.size})", 14f, Ui.MUTED))
            val bar = LinearLayout(ctx)
            bar.orientation = LinearLayout.HORIZONTAL
            bar.weightSum = 100f
            bar.background = Ui.rounded(ctx, Ui.CHIP_OFF, 6)
            val blp = LinearLayout.LayoutParams(MATCH, dp(12))
            blp.setMargins(0, dp(8), 0, 0)
            bar.layoutParams = blp
            val fill = View(ctx)
            fill.background = Ui.rounded(ctx, Ui.GREEN, 6)
            fill.layoutParams = LinearLayout.LayoutParams(0, MATCH, pct.toFloat())
            bar.addView(fill)
            c3.addView(bar)
            body.addView(c3)
        }

        if (list.isEmpty()) {
            val empty = Ui.card(ctx)
            empty.gravity = Gravity.CENTER
            val e1 = Ui.text(ctx, "🎉", 40f)
            e1.gravity = Gravity.CENTER
            val e2 = Ui.text(ctx, "No homework yet", 17f, Ui.TEXT, true)
            e2.gravity = Gravity.CENTER
            val e3 = Ui.text(ctx, "Tap  ＋ Add homework  to create one", 14f, Ui.MUTED)
            e3.gravity = Gravity.CENTER
            empty.addView(e1); empty.addView(e2); empty.addView(e3)
            body.addView(empty)
        }

        list.forEach { body.addView(hwCard(it, now)) }

        if (done > 0) {
            body.addView(Ui.button(ctx, "Clear finished homework", Ui.CHIP_OFF, Ui.PRIMARY) {
                Store.all(ctx).filter { it.done }.forEach { Store.delete(ctx, it.id) }
                render()
            })
        }
    }

    private fun hwCard(hw: Homework, now: Long): LinearLayout {
        val ctx = this
        val card = Ui.card(ctx)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.setPadding(dp(14), dp(12), dp(10), dp(12))

        val strip = View(ctx)
        strip.background = Ui.rounded(ctx, Ui.subjectColor(hw.subject), 3)
        val stlp = LinearLayout.LayoutParams(dp(5), dp(56))
        stlp.setMargins(0, 0, dp(12), 0)
        strip.layoutParams = stlp

        val check = TextView(ctx)
        check.gravity = Gravity.CENTER
        check.textSize = 18f
        val clp = LinearLayout.LayoutParams(dp(36), dp(36))
        clp.setMargins(0, 0, dp(12), 0)
        check.layoutParams = clp
        if (hw.done) {
            check.text = "✓"
            check.setTextColor(Color.WHITE)
            check.background = Ui.rounded(ctx, Ui.GREEN, 18)
        } else {
            check.text = ""
            check.background = Ui.rounded(ctx, Color.WHITE, 18, Color.parseColor("#C5CAE9"))
        }
        check.setOnClickListener {
            val nowDone = !hw.done
            Store.setDone(ctx, hw.id, nowDone)
            if (nowDone) Scheduler.cancelHomework(ctx, hw.id)
            else if (hw.dueAt > System.currentTimeMillis()) Scheduler.scheduleHomework(ctx, hw)
            render()
        }

        val info = LinearLayout(ctx)
        info.orientation = LinearLayout.VERTICAL
        info.layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)

        val subject = Ui.text(ctx, hw.subject, 17f, if (hw.done) Ui.MUTED else Ui.TEXT, true)
        if (hw.done) subject.paintFlags = subject.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        info.addView(subject)
        if (hw.note.isNotBlank()) info.addView(Ui.text(ctx, hw.note, 14f, Ui.MUTED))

        val isOver = !hw.done && hw.dueAt < now
        val dueText = when {
            hw.done -> "Done ✓"
            isOver -> "⏰ ${fmt.format(Date(hw.dueAt))} · overdue"
            else -> "⏰ ${fmt.format(Date(hw.dueAt))}"
        }
        val dueColor = when {
            hw.done -> Ui.GREEN
            isOver -> Ui.RED
            else -> Ui.PRIMARY
        }
        val due = Ui.text(ctx, dueText, 13f, dueColor, true)
        due.setPadding(0, dp(4), 0, 0)
        info.addView(due)

        val del = Ui.text(ctx, "✕", 18f, Ui.MUTED)
        del.setPadding(dp(10), dp(10), dp(10), dp(10))
        del.setOnClickListener {
            AlertDialog.Builder(ctx)
                .setTitle("Delete this homework?")
                .setPositiveButton("Delete") { _, _ ->
                    Scheduler.cancelHomework(ctx, hw.id)
                    Store.delete(ctx, hw.id)
                    render()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        card.addView(strip)
        card.addView(check)
        card.addView(info)
        card.addView(del)
        return card
    }

    // ---------------- Test alarm ----------------
    private fun testAlarm() {
        startForegroundService(
            Intent(this, AlarmService::class.java).putExtra("id", 0).putExtra("test", true)
        )
        Toast.makeText(
            this, "Test running for 10 seconds. The flashlight should blink.", Toast.LENGTH_LONG
        ).show()
    }

    // ---------------- Weekly timetable ----------------
    private fun timetableDialog() {
        val items = Array(7) { i ->
            val s = Store.timetable(this, i + 1)
            "${days[i]}  (${s.size} subjects)"
        }
        AlertDialog.Builder(this)
            .setTitle("Pick a day")
            .setItems(items) { _, which -> subjectsDialog(which + 1) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun subjectsDialog(dow: Int) {
        val subs = Store.SUBJECTS.toTypedArray()
        val cur = Store.timetable(this, dow)
        val checked = BooleanArray(subs.size) { subs[it] in cur }
        AlertDialog.Builder(this)
            .setTitle(days[dow - 1])
            .setMultiChoiceItems(subs, checked) { _, i, on -> checked[i] = on }
            .setPositiveButton("Save") { _, _ ->
                val chosen = subs.filterIndexed { i, _ -> checked[i] }.toSet()
                Store.setTimetable(this, dow, chosen)
                if (dow == Calendar.getInstance().get(Calendar.DAY_OF_WEEK))
                    Store.setTodaySubjects(this, chosen)
                render()
                timetableDialog()
            }
            .setNegativeButton("Back") { _, _ -> timetableDialog() }
            .show()
    }

    // ---------------- Add homework: subject + note -> date -> time ----------------
    private fun addDialog() {
        val ctx = this
        val subs = Store.todaySubjects(ctx).ifEmpty { Store.SUBJECTS.toSet() }
            .toList().sortedBy { Store.SUBJECTS.indexOf(it) }
        var chosen = subs.first()
        lateinit var grid: LinearLayout
        grid = Ui.chipGrid(ctx, subs, { it == chosen }) { s, _ ->
            chosen = s
            for (i in 0 until grid.childCount) {
                val r = grid.getChildAt(i) as LinearLayout
                for (j in 0 until r.childCount) {
                    val v = r.getChildAt(j)
                    if (v is TextView) {
                        val key = v.tag as String
                        Ui.styleChip(ctx, v, key == chosen, Ui.subjectColor(key))
                    }
                }
            }
        }

        val note = EditText(ctx)
        note.hint = "What is the homework? (optional)"
        note.background = Ui.rounded(ctx, Ui.CHIP_OFF, 14)
        note.setPadding(dp(14), dp(12), dp(14), dp(12))
        val nlp = LinearLayout.LayoutParams(MATCH, WRAP)
        nlp.setMargins(dp(4), dp(12), dp(4), 0)
        note.layoutParams = nlp

        val box = LinearLayout(ctx)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(12), dp(16), 0)
        box.addView(grid)
        box.addView(note)

        AlertDialog.Builder(ctx)
            .setTitle("New homework")
            .setView(box)
            .setPositiveButton("Next: pick date") { _, _ ->
                pickDateTime(chosen, note.text.toString().trim())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickDateTime(subject: String, note: String) {
        val c = Calendar.getInstance()
        val picker = DatePickerDialog(this, { _, y, mo, d ->
            TimePickerDialog(this, { _, hh, mi ->
                val cal = Calendar.getInstance().apply {
                    set(y, mo, d, hh, mi, 0); set(Calendar.MILLISECOND, 0)
                }
                if (cal.timeInMillis <= System.currentTimeMillis()) {
                    Toast.makeText(this, "That time has already passed. Pick a later time.", Toast.LENGTH_LONG).show()
                    pickDateTime(subject, note)
                    return@TimePickerDialog
                }
                val hw = Homework(
                    (System.currentTimeMillis() and 0x7fffffff).toInt(),
                    subject, note, cal.timeInMillis, false
                )
                Store.add(this, hw)
                Scheduler.scheduleHomework(this, hw)
                Toast.makeText(this, "Alarm set for ${fmt.format(cal.time)}", Toast.LENGTH_LONG).show()
                render()
            }, 17, 0, false).show()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
        picker.datePicker.minDate = System.currentTimeMillis() - 1000
        picker.show()
    }
}
