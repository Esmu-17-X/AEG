package com.example.homeworkbuddy

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import androidx.core.app.NotificationCompat

/** Plays alarm sound, vibrates, blinks the flashlight and opens the alarm screen over everything. */
class AlarmService : Service() {
    companion object {
        const val ACTION_STOP = "com.example.homeworkbuddy.STOP_ALARM"

        /** True while an alarm is ringing. The alarm screen watches this to close itself. */
        @Volatile
        var active = false
    }

    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var ringing = false
    private var phase = false
    private var camId: String? = null

    private val blink = object : Runnable {
        override fun run() {
            phase = !phase
            setTorch(phase)
            handler.postDelayed(this, 350)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        if (i?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (ringing) return START_NOT_STICKY
        ringing = true
        active = true

        val id = i?.getIntExtra("id", 0) ?: 0
        val test = i?.getBooleanExtra("test", false) ?: false
        val hw = Store.all(this).find { it.id == id }
        Notif.ensure(this)

        val pmFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val alarmIntent = Intent(this, AlarmActivity::class.java)
            .putExtra("id", id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val full = PendingIntent.getActivity(this, id, alarmIntent, pmFlags)
        val stopPi = PendingIntent.getService(
            this, 99,
            Intent(this, AlarmService::class.java).setAction(ACTION_STOP),
            pmFlags
        )
        val title = if (test) "Test alarm" else "Homework time: ${hw?.subject ?: ""}"
        val body = hw?.note?.ifBlank { "Time to do your homework!" } ?: "Time to do your homework!"
        val n = NotificationCompat.Builder(this, Notif.ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .addAction(0, "Stop", stopPi)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 29)
            startForeground(3, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(3, n)

        // keep the CPU awake so the flashlight keeps blinking with the screen off
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "homework:alarm")
            wakeLock?.acquire(3 * 60 * 1000L)
            // turn the screen on
            @Suppress("DEPRECATION")
            screenLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "homework:screen"
            )
            screenLock?.acquire(15_000L)
        } catch (_: Exception) {}

        // open the alarm screen on top of whatever is running
        // (works directly when "Display over other apps" is allowed;
        //  otherwise the full-screen notification above does it)
        try { startActivity(alarmIntent) } catch (_: Exception) {}

        startSound()
        startVibration()
        startFlash()
        handler.postDelayed({ stopSelf() }, if (test) 10_000L else 120_000L)
        return START_NOT_STICKY
    }

    private fun startSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {}
    }

    private fun startVibration() {
        try {
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
        } catch (_: Exception) {}
    }

    private fun startFlash() {
        try {
            val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            // check every camera separately so one odd camera can't break the search
            val withFlash = cm.cameraIdList.filter {
                try {
                    cm.getCameraCharacteristics(it)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } catch (_: Exception) { false }
            }
            camId = withFlash.firstOrNull {
                try {
                    cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                        CameraCharacteristics.LENS_FACING_BACK
                } catch (_: Exception) { false }
            } ?: withFlash.firstOrNull()
            if (camId != null) handler.post(blink)
        } catch (_: Exception) {}
    }

    private fun setTorch(on: Boolean) {
        val id = camId ?: return
        try {
            (getSystemService(Context.CAMERA_SERVICE) as CameraManager).setTorchMode(id, on)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        active = false
        handler.removeCallbacksAndMessages(null)
        setTorch(false)
        try { player?.stop(); player?.release() } catch (_: Exception) {}
        player = null
        try { (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).cancel() } catch (_: Exception) {}
        try { wakeLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        try { screenLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        ringing = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
