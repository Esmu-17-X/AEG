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

/** Runs after restart / app update / time change: puts every alarm back. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        // put every pending alarm back
        Scheduler.rescheduleAll(c)

        // after a restart, tell the user about reminders that were missed while the phone was off
        val isBoot = i.action == Intent.ACTION_BOOT_COMPLETED ||
            i.action == "android.intent.action.QUICKBOOT_POWERON"
        if (!isBoot) return

        val now = System.currentTimeMillis()
        val missed = Store.all(c).filter {
            !it.done && it.dueAt <= now && it.dueAt > now - 24 * 60 * 60 * 1000L
        }
        if (missed.isEmpty()) return

        Notif.ensure(c)
        val open = PendingIntent.getActivity(c, 13, Intent(c, MainActivity::class.java), FLAGS)
        val text = "Your phone was off. You missed: " +
            missed.joinToString(", ") { it.subject } + ". Tap to open."
        val n = NotificationCompat.Builder(c, Notif.ASK)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("Missed homework reminder")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        c.getSystemService(NotificationManager::class.java).notify(5, n)
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
