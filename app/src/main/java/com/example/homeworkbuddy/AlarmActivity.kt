package com.example.homeworkbuddy

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Full-screen alarm screen shown over the lock screen. */
class AlarmActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val id = intent.getIntExtra("id", 0)
        val hw = Store.all(this).find { it.id == id }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#B71C1C"))
            setPadding(48, 48, 48, 48)
        }
        fun tv(t: String, size: Float) = TextView(this).apply {
            text = t; textSize = size; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }
        root.addView(tv("HOMEWORK TIME!", 32f))
        root.addView(tv(hw?.subject ?: "", 26f))
        root.addView(tv(hw?.note ?: "", 18f))

        root.addView(Button(this).apply {
            text = "I finished it ✓"
            setOnClickListener {
                Store.setDone(this@AlarmActivity, id, true)
                stopAlarm(); finish()
            }
        })
        root.addView(Button(this).apply {
            text = "Snooze ${Scheduler.SNOOZE_MIN} min"
            setOnClickListener {
                Scheduler.snooze(this@AlarmActivity, id)
                Toast.makeText(
                    this@AlarmActivity, "Snoozed ${Scheduler.SNOOZE_MIN} minutes", Toast.LENGTH_SHORT
                ).show()
                stopAlarm(); finish()
            }
        })
        root.addView(Button(this).apply {
            text = "Stop alarm"
            setOnClickListener { stopAlarm(); finish() }
        })
        setContentView(root)
    }

    private fun stopAlarm() = stopService(Intent(this, AlarmService::class.java))
}
