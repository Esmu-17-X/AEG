package com.example.homeworkbuddy

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

/** Permissions that let the alarm screen appear over other apps and the lock screen. */
object Perms {
    private var asked = false

    fun overlayOk(c: Context) = Settings.canDrawOverlays(c)

    fun fullScreenOk(c: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 34)
            c.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        else true

    fun allOk(c: Context) = overlayOk(c) && fullScreenOk(c)

    /** Opens the settings page for whichever permission is still missing. */
    fun fix(a: Activity) {
        try {
            if (!overlayOk(a)) {
                a.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${a.packageName}"))
                )
            } else if (!fullScreenOk(a) && Build.VERSION.SDK_INT >= 34) {
                a.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${a.packageName}"))
                )
            } else {
                Toast.makeText(a, "Alarm screen is fully enabled ✓", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(a, "Open Settings → Apps → Homework Buddy and allow the permissions.", Toast.LENGTH_LONG).show()
        }
    }

    /** Asks once per app launch if something is missing. */
    fun prompt(a: Activity) {
        if (allOk(a) || asked) return
        asked = true
        AlertDialog.Builder(a)
            .setTitle("Show alarm over everything")
            .setMessage(
                "To make the homework alarm appear on top of other apps and on the lock screen, " +
                    "please allow:\n\n• Display over other apps\n• Full-screen notifications (Android 14+)\n\n" +
                    "Tap Allow, switch the permission on, then press back."
            )
            .setPositiveButton("Allow") { _, _ -> fix(a) }
            .setNegativeButton("Later", null)
            .show()
    }
}
