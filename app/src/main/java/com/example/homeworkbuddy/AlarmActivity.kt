package com.example.homeworkbuddy

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Full-screen alarm screen. Shows over the lock screen and over any other app. */
class AlarmActivity : Activity() {
    private var pulse: ObjectAnimator? = null
    private val handler = Handler(Looper.getMainLooper())

    // closes this screen automatically when the alarm stops (notification Stop button, timeout…)
    private val watcher = object : Runnable {
        override fun run() {
            if (!AlarmService.active) finish() else handler.postDelayed(this, 700)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)

        // show over the lock screen + turn the screen on
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
        )

        val ctx = this
        val id = intent.getIntExtra("id", 0)
        val hw = Store.all(ctx).find { it.id == id }

        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setPadding(Ui.dp(ctx, 28), Ui.dp(ctx, 28), Ui.dp(ctx, 28), Ui.dp(ctx, 28))
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#1A237E"), Color.parseColor("#4A148C"))
        )

        val icon = Ui.text(ctx, "⏰", 80f)
        icon.gravity = Gravity.CENTER
        pulse = ObjectAnimator.ofPropertyValuesHolder(
            icon,
            PropertyValuesHolder.ofFloat("scaleX", 1f, 1.2f),
            PropertyValuesHolder.ofFloat("scaleY", 1f, 1.2f)
        ).apply {
            duration = 500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
        root.addView(icon)

        val small = Ui.text(ctx, "HOMEWORK TIME", 14f, Color.parseColor("#B3FFFFFF"), true)
        small.gravity = Gravity.CENTER
        small.letterSpacing = 0.2f
        small.setPadding(0, Ui.dp(ctx, 16), 0, 0)
        root.addView(small)

        val subject = Ui.text(ctx, hw?.subject ?: "Test alarm", 36f, Color.WHITE, true)
        subject.gravity = Gravity.CENTER
        root.addView(subject)

        val note = Ui.text(ctx, hw?.note ?: "", 18f, Color.parseColor("#CCFFFFFF"))
        note.gravity = Gravity.CENTER
        note.setPadding(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 32))
        root.addView(note)

        root.addView(Ui.button(ctx, "✓  I finished it", Ui.GREEN) {
            Store.setDone(ctx, id, true)
            stopAlarm(); finish()
        })
        root.addView(Ui.button(ctx, "😴  Snooze ${Scheduler.SNOOZE_MIN} min", Ui.ACCENT) {
            Scheduler.snooze(ctx, id)
            Toast.makeText(ctx, "Snoozed ${Scheduler.SNOOZE_MIN} minutes", Toast.LENGTH_SHORT).show()
            stopAlarm(); finish()
        })
        root.addView(Ui.button(ctx, "Stop alarm", Color.parseColor("#33FFFFFF")) {
            stopAlarm(); finish()
        })
        setContentView(root)

        handler.postDelayed(watcher, 1500)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        pulse?.cancel()
        super.onDestroy()
    }

    private fun stopAlarm() = stopService(Intent(this, AlarmService::class.java))
}
