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

/** Plays alarm sound, vibrates and blinks the flashlight until stopped. */
class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var torchOn = false
    private var camId: String? = null

    private val blink = object : Runnable {
        override fun run() {
            setTorch(!torchOn)
            handler.postDelayed(this, 400)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        if (player != null) return START_NOT_STICKY // already ringing
        val id = i?.getIntExtra("id", 0) ?: 0
        val hw = Store.all(this).find { it.id == id }
        Notif.ensure(this)

        val full = PendingIntent.getActivity(
            this, id,
            Intent(this, AlarmActivity::class.java).putExtra("id", id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, Notif.ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Homework time: ${hw?.subject ?: ""}")
            .setContentText(hw?.note?.ifBlank { "Time to do your homework!" } ?: "Time to do your homework!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 29)
            startForeground(3, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(3, n)

        startSound()
        startVibration()
        startFlash()
        handler.postDelayed({ stopSelf() }, 2 * 60 * 1000L) // auto-stop after 2 min
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
        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
    }

    private fun startFlash() {
        try {
            val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            camId = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (camId != null) handler.post(blink)
        } catch (_: Exception) {}
    }

    private fun setTorch(on: Boolean) {
        try {
            val id = camId ?: return
            (getSystemService(Context.CAMERA_SERVICE) as CameraManager).setTorchMode(id, on)
            torchOn = on
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        setTorch(false)
        try { player?.stop(); player?.release() } catch (_: Exception) {}
        player = null
        (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
