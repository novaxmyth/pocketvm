package com.pocketvm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pocketvm.R
import com.pocketvm.ui.MainActivity

/**
 * Foreground service that keeps VM processes alive and shows a persistent
 * notification with the VM name and status.
 */
class VmForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "pocketvm_vm_channel"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_VM_ID = "extra_vm_id"
        const val EXTRA_VM_NAME = "extra_vm_name"
        const val ACTION_STOP = "com.pocketvm.action.STOP_VM"

        fun start(context: Context, vmId: String, vmName: String) {
            val intent = Intent(context, VmForegroundService::class.java).apply {
                putExtra(EXTRA_VM_ID, vmId)
                putExtra(EXTRA_VM_NAME, vmName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VmForegroundService::class.java))
        }
    }

    private var vmId: String = ""
    private var vmName: String = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        vmId = intent?.getStringExtra(EXTRA_VM_ID) ?: "unknown"
        vmName = intent?.getStringExtra(EXTRA_VM_NAME) ?: "Virtual Machine"

        val notification = buildNotification("Running")
        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PocketVM Instance",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when a virtual machine instance is running"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, VmForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PocketVM — $vmName")
            .setContentText("Status: $status")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(0, "Stop", stopPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun updateStatus(status: String) {
        val notification = buildNotification(status)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
}
