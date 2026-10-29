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
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private val fmt = SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault())
    private val days = arrayOf(
        "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
    )

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        title = "Homework Buddy"
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        Notif.ensure(this)
        Store.autoFill(this)
        Scheduler.scheduleAfterSchool(this)
        Scheduler.scheduleMorning(this)

        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        scroll.addView(root)
        setContentView(scroll)
        render()
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) { Store.autoFill(this); render() }
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

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun header(t: String) = TextView(this).apply {
        text = t; textSize = 20f; setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(20), 0, dp(6))
    }

    private fun hm(h: Int, m: Int) = "%02d:%02d".format(h, m)

    private fun render() {
        root.removeAllViews()

        // ---- 1. Today's subjects ----
        root.addView(header("📚 Today's subjects"))
        val selected = Store.todaySubjects(this).toMutableSet()
        Store.SUBJECTS.forEach { s ->
            root.addView(CheckBox(this).apply {
                text = s; textSize = 17f; isChecked = s in selected
                setOnCheckedChangeListener { _, on ->
                    if (on) selected.add(s) else selected.remove(s)
                    Store.setTodaySubjects(this@MainActivity, selected.toSet())
                }
            })
        }

        // ---- 2. Reminder settings ----
        root.addView(header("⏰ Settings"))
        val (mh, mm) = Store.morning(this)
        root.addView(Button(this).apply {
            text = "Morning reminder: ${hm(mh, mm)} (tap to change)"
            setOnClickListener {
                TimePickerDialog(this@MainActivity, { _, nh, nm ->
                    Store.setMorning(this@MainActivity, nh, nm)
                    Scheduler.scheduleMorning(this@MainActivity)
                    render()
                }, mh, mm, false).show()
            }
        })
        val (h, m) = Store.afterSchool(this)
        root.addView(Button(this).apply {
            text = "After-school question: ${hm(h, m)} (tap to change)"
            setOnClickListener {
                TimePickerDialog(this@MainActivity, { _, nh, nm ->
                    Store.setAfterSchool(this@MainActivity, nh, nm)
                    Scheduler.scheduleAfterSchool(this@MainActivity)
                    render()
                }, h, m, false).show()
            }
        })
        root.addView(Button(this).apply {
            text = "🗓 Weekly timetable (auto-tick subjects)"
            setOnClickListener { timetableDialog() }
        })
        root.addView(Button(this).apply {
            text = "➕ Add homework now"
            setOnClickListener { addDialog() }
        })

        // ---- 3. Tracker ----
        val now = System.currentTimeMillis()
        val list = Store.all(this).sortedWith(compareBy({ it.done }, { it.dueAt }))
        val pending = list.count { !it.done }
        val overdue = list.count { !it.done && it.dueAt < now }
        val done = list.size - pending
        root.addView(header("✅ Tracker"))
        root.addView(TextView(this).apply {
            val pct = if (list.isEmpty()) 0 else done * 100 / list.size
            text = "Pending: $pending   Overdue: $overdue   Done: $done  ($pct%)"
            textSize = 15f
        })
        if (list.isNotEmpty()) root.addView(ProgressBar(
            this, null, android.R.attr.progressBarStyleHorizontal
        ).apply { max = list.size; progress = done })
        if (list.isEmpty()) root.addView(TextView(this).apply { text = "No homework yet 🎉" })

        list.forEach { hw ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val cb = CheckBox(this).apply {
                isChecked = hw.done
                setOnCheckedChangeListener { _, on ->
                    Store.setDone(this@MainActivity, hw.id, on)
                    if (on) Scheduler.cancelHomework(this@MainActivity, hw.id)
                    else if (hw.dueAt > System.currentTimeMillis())
                        Scheduler.scheduleHomework(this@MainActivity, hw)
                    render()
                }
            }
            val info = TextView(this).apply {
                text = "${hw.subject}\n${hw.note.ifBlank { "(no details)" }}\n⏰ ${fmt.format(Date(hw.dueAt))}"
                if (hw.done) setTextColor(Color.GRAY)
                else if (hw.dueAt < now) setTextColor(Color.RED)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val del = Button(this).apply {
                text = "🗑"
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Delete this homework?")
                        .setPositiveButton("Delete") { _, _ ->
                            Scheduler.cancelHomework(this@MainActivity, hw.id)
                            Store.delete(this@MainActivity, hw.id)
                            render()
                        }
                        .setNegativeButton("Cancel", null).show()
                }
            }
            row.addView(cb); row.addView(info); row.addView(del)
            root.addView(row)
        }

        if (done > 0) root.addView(Button(this).apply {
            text = "Clear finished homework"
            setOnClickListener {
                Store.all(this@MainActivity).filter { it.done }
                    .forEach { Store.delete(this@MainActivity, it.id) }
                render()
            }
        })
    }

    // ---------- Weekly timetable ----------
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

    // ---------- Add homework: subject + note -> date -> time ----------
    private fun addDialog() {
        val subs = Store.todaySubjects(this).ifEmpty { Store.SUBJECTS.toSet() }
            .toList().sortedBy { Store.SUBJECTS.indexOf(it) }
        val sp = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity, android.R.layout.simple_spinner_dropdown_item, subs
            )
        }
        val note = EditText(this).apply { hint = "What is the homework? (optional)" }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), 0)
            addView(sp); addView(note)
        }
        AlertDialog.Builder(this)
            .setTitle("New homework")
            .setView(box)
            .setPositiveButton("Next: pick date") { _, _ ->
                pickDateTime(sp.selectedItem.toString(), note.text.toString().trim())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickDateTime(subject: String, note: String) {
        val c = Calendar.getInstance()
        val dp = DatePickerDialog(this, { _, y, mo, d ->
            TimePickerDialog(this, { _, hh, mi ->
                val cal = Calendar.getInstance().apply {
                    set(y, mo, d, hh, mi, 0); set(Calendar.MILLISECOND, 0)
                }
                if (cal.timeInMillis <= System.currentTimeMillis()) {
                    Toast.makeText(this, "That time has already passed — pick a later time.", Toast.LENGTH_LONG).show()
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
        dp.datePicker.minDate = System.currentTimeMillis() - 1000
        dp.show()
    }
}
