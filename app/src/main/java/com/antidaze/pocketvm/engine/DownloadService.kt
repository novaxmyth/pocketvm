package com.antidaze.pocketvm.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.antidaze.pocketvm.R
import com.antidaze.pocketvm.data.VmRepository
import com.antidaze.pocketvm.guest.GuestImages
import java.io.File
import kotlin.concurrent.thread

/**
 * Foreground-service downloader so image downloads survive the user leaving
 * the app (process deaths killed the old coroutine-based downloads).
 * Handles both the Alpine ISO and the Android guest bundle, including
 * unpacking, and finalizes the VM config when done.
 */
class DownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        val vmId = intent.getStringExtra(EXTRA_VM_ID) ?: return START_NOT_STICKY
        val kind = intent.getStringExtra(EXTRA_KIND) ?: KIND_ISO
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "download"
        val url = intent.getStringExtra(EXTRA_URL) ?: ""

        val notifyId = NOTIFY_ID_BASE + (vmId.hashCode().mod(1000))
        createChannel()
        startForeground(notifyId, progressNotification(label, 0, 0, true))

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pocketvm:download").apply {
            setReferenceCounted(false)
            acquire(3 * 60 * 60 * 1000L)
        }

        thread(name = "download-$vmId") {
            val repo = VmRepository(this@DownloadService)
            try {
                val realUrl = if (kind == KIND_ANDROID) {
                    GuestImages.latestAndroidAsset()?.first
                        ?: throw IllegalStateException("Android guest image is not available yet")
                } else url

                val dest = when (kind) {
                    KIND_ANDROID -> "guest.zip"
                    else -> "alpine.iso"
                }
                repo.downloadImage(vmId, realUrl, dest) { read, total ->
                    val pct = if (total > 0) (read * 100 / total).toInt() else 0
                    notify(notifyId, progressNotification(label, pct, total, false))
                }

                val vmDir = repo.vmDir(vmId)
                val cfg = repo.listVms().firstOrNull { it.id == vmId }
                    ?: throw IllegalStateException("VM vanished")
                if (kind == KIND_ANDROID) {
                    GuestImages.unzip(File(vmDir, "guest.zip"), vmDir)
                    val gz = File(vmDir, "rootfs.img.gz")
                    val raw = File(vmDir, "rootfs.img")
                    GuestImages.gunzip(gz, raw)
                    notify(notifyId, progressNotification(label, 100, 0, true))
                    gz.delete()
                    File(vmDir, "guest.zip").delete()
                    cfg.guest = "android"
                    cfg.systemImagePath = raw.absolutePath
                    cfg.systemIsCdrom = false
                    cfg.kernelPath = File(vmDir, "vmlinuz").absolutePath
                    cfg.kernelCmdline = "console=ttyAMA0 root=/dev/vda1 rw init=/lib/systemd/systemd"
                } else {
                    cfg.systemImagePath = File(vmDir, "alpine.iso").absolutePath
                    cfg.systemIsCdrom = true
                }
                cfg.preparing = false
                cfg.statusNote = ""
                repo.saveConfig(cfg)
                notify(notifyId, resultNotification(label, true, null))
            } catch (e: Exception) {
                try {
                    val cfg = repo.listVms().firstOrNull { it.id == vmId }
                    if (cfg != null) {
                        cfg.preparing = false
                        cfg.statusNote = "download failed: ${e.message ?: "error"}"
                        repo.saveConfig(cfg)
                    }
                } catch (x: Exception) { }
                notify(notifyId, resultNotification(label, false, e.message ?: "error"))
            } finally {
                try { wakeLock.release() } catch (x: Exception) { }
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun nm(): NotificationManager =
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    private fun createChannel() {
        nm().createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun baseBuilder(label: String): Notification.Builder =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.notif_dl_title, label))
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, com.antidaze.pocketvm.ui.MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

    private fun progressNotification(label: String, pct: Int, total: Long, indeterminate: Boolean): Notification =
        baseBuilder(label)
            .setProgress(100, pct, indeterminate || total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun resultNotification(label: String, ok: Boolean, err: String?): Notification {
        val b = baseBuilder(label)
            .setSmallIcon(if (ok) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setOngoing(false)
            .setAutoCancel(true)
        if (ok) b.setContentText(getString(R.string.notif_dl_done, label))
        else b.setContentText(getString(R.string.notif_dl_failed, err ?: ""))
        return b.build()
    }

    private fun notify(id: Int, n: Notification) {
        try { nm().notify(id, n) } catch (e: Exception) { }
    }

    companion object {
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFY_ID_BASE = 1000
        const val EXTRA_VM_ID = "vm_id"
        const val EXTRA_KIND = "kind"
        const val EXTRA_URL = "url"
        const val EXTRA_LABEL = "label"
        const val KIND_ISO = "iso"
        const val KIND_ANDROID = "android"

        fun start(context: Context, vmId: String, kind: String, url: String, label: String) {
            val i = Intent(context, DownloadService::class.java)
                .putExtra(EXTRA_VM_ID, vmId)
                .putExtra(EXTRA_KIND, kind)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_LABEL, label)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }
    }
}
