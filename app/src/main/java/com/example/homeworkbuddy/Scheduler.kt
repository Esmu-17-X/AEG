package com.example.homeworkbuddy

import android.app.*
import android.content.*
import android.os.Build
import java.util.Calendar

object Notif {
    const val ALARM = "alarm"
    const val ASK = "ask"

    fun ensure(c: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(ALARM, "Homework alarm", NotificationManager.IMPORTANCE_HIGH)
                    .apply { setSound(null, null) }
            )
            nm.createNotificationChannel(
                NotificationChannel(ASK, "Daily reminders", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }
}

object Scheduler {
    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    const val SNOOZE_MIN = 10

    private fun am(c: Context) = c.getSystemService(AlarmManager::class.java)

    private fun hwIntent(c: Context, id: Int) = PendingIntent.getBroadcast(
        c, id, Intent(c, AlarmReceiver::class.java).putExtra("id", id), FLAGS
    )

    // Exact alarm that works in Doze and needs no special permission
    fun scheduleHomework(c: Context, hw: Homework) {
        val show = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), FLAGS)
        am(c).setAlarmClock(AlarmManager.AlarmClockInfo(hw.dueAt, show), hwIntent(c, hw.id))
    }

    fun cancelHomework(c: Context, id: Int) = am(c).cancel(hwIntent(c, id))

    fun snooze(c: Context, id: Int) {
        val due = System.currentTimeMillis() + SNOOZE_MIN * 60_000L
        Store.setDue(c, id, due)
        Store.all(c).find { it.id == id }?.let { scheduleHomework(c, it) }
    }

    private fun nextAt(h: Int, m: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis

    // Daily "did you have homework?" notification
    fun scheduleAfterSchool(c: Context) {
        val (h, m) = Store.afterSchool(c)
        val pi = PendingIntent.getBroadcast(c, 1, Intent(c, AfterSchoolReceiver::class.java), FLAGS)
        am(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAt(h, m), pi)
    }

    // Daily morning "tick your subjects" notification
    fun scheduleMorning(c: Context) {
        val (h, m) = Store.morning(c)
        val pi = PendingIntent.getBroadcast(c, 2, Intent(c, MorningReceiver::class.java), FLAGS)
        am(c).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAt(h, m), pi)
    }

    fun rescheduleAll(c: Context) {
        scheduleAfterSchool(c)
        scheduleMorning(c)
        Store.all(c).filter { !it.done && it.dueAt > System.currentTimeMillis() }
            .forEach { scheduleHomework(c, it) }
    }
}
