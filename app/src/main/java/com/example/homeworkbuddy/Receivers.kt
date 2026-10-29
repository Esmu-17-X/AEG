package com.example.homeworkbuddy

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.*
import androidx.core.app.NotificationCompat

private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

/** Fires at the homework time -> starts the loud alarm service. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        c.startForegroundService(
            Intent(c, AlarmService::class.java).putExtra("id", i.getIntExtra("id", 0))
        )
    }
}

/** Fires every day after school -> asks "Did you have homework?" */
class AfterSchoolReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Notif.ensure(c)
        val yes = PendingIntent.getActivity(
            c, 10,
            Intent(c, MainActivity::class.java).putExtra("add", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            FLAGS
        )
        val no = PendingIntent.getBroadcast(c, 11, Intent(c, DismissReceiver::class.java), FLAGS)

        val n = NotificationCompat.Builder(c, Notif.ASK)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("Welcome back from school!")
            .setContentText("Did you get any homework or assignment today?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(yes)
            .setAutoCancel(true)
            .addAction(0, "Yes", yes)
            .addAction(0, "No", no)
            .build()
        c.getSystemService(NotificationManager::class.java).notify(2, n)

        Scheduler.scheduleAfterSchool(c) // schedule tomorrow
    }
}

class DismissReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        c.getSystemService(NotificationManager::class.java).cancel(2)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action == Intent.ACTION_BOOT_COMPLETED) Scheduler.rescheduleAll(c)
    }
}

/** Every morning: fills today's subjects from the timetable and reminds you to check your bag. */
class MorningReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Notif.ensure(c)
        Store.autoFill(c)
        val subs = Store.todaySubjects(c).sortedBy { Store.SUBJECTS.indexOf(it) }
        val open = PendingIntent.getActivity(c, 12, Intent(c, MainActivity::class.java), FLAGS)
        val text = if (subs.isEmpty()) "Tap to choose today's subjects."
        else "Today: ${subs.joinToString(", ")}. Tap to check them."
        val n = NotificationCompat.Builder(c, Notif.ASK)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("Good morning! Ready for school?")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        c.getSystemService(NotificationManager::class.java).notify(4, n)
        Scheduler.scheduleMorning(c)
    }
}
