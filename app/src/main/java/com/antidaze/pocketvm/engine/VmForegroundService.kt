package com.antidaze.pocketvm.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.antidaze.pocketvm.R
import com.antidaze.pocketvm.data.VmRepository
import com.antidaze.pocketvm.ui.ConsoleActivity

/**
 * Keeps the QEMU process alive while the user is outside the console
 * (the VPhoneOS-style "VM keeps running in background" behaviour).
 */
class VmForegroundService : Service() {

    private val binder = LocalBinder()
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    inner class LocalBinder : Binder() {
        val service: VmForegroundService get() = this@VmForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val vmId = intent?.getStringExtra(EXTRA_VM_ID) ?: return START_NOT_STICKY
        val vmName = intent.getStringExtra(EXTRA_VM_NAME) ?: vmId

        startForeground(NOTIFY_ID, buildNotification(vmName))
        acquireLocks()

        val engine = VmManager.get(vmId)
        if (engine != null && !engine.exitWatched) {
            engine.exitWatched = true
            engine.onExited = { code ->
                Log.i(TAG, "VM $vmId exited with $code")
                VmManager.remove(vmId)
                VmManager.dispatchExit(vmId, code)
                stopSelf()
            }
        }
        return START_STICKY
    }

    fun stopEngine() {
        VmManager.current()?.stop()
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }

    private fun acquireLocks() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pocketvm:vm").apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L)
        }
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "pocketvm:net").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseLocks() {
        try { wakeLock?.release() } catch (e: Exception) { }
        try { wifiLock?.release() } catch (e: Exception) { }
        wakeLock = null; wifiLock = null
    }

    private fun buildNotification(vmName: String): Notification {
        val tap = PendingIntent.getActivity(
            this, 0,
            Intent(this, ConsoleActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.notif_title, vmName))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(tap)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Virtual machine", NotificationManager.IMPORTANCE_LOW)
        )
    }

    companion object {
        private const val TAG = "VmService"
        private const val CHANNEL_ID = "vm"
        private const val NOTIFY_ID = 42
        const val EXTRA_VM_ID = "vm_id"
        const val EXTRA_VM_NAME = "vm_name"

        fun start(context: Context, vmId: String, vmName: String) {
            context.startForegroundService(
                Intent(context, VmForegroundService::class.java)
                    .putExtra(EXTRA_VM_ID, vmId)
                    .putExtra(EXTRA_VM_NAME, vmName)
            )
        }
    }
}

/** Registry of running engines, shared between activities and the service. */
object VmManager {
    private val engines = LinkedHashMap<String, QemuEngine>()
    private val exitListeners = mutableListOf<(vmId: String, code: Int) -> Unit>()

    @Synchronized
    fun put(id: String, engine: QemuEngine) { engines[id] = engine }

    @Synchronized
    fun get(id: String): QemuEngine? = engines[id]

    @Synchronized
    fun remove(id: String) { engines.remove(id) }

    @Synchronized
    fun current(): QemuEngine? = engines.values.firstOrNull()

    @Synchronized
    fun anyRunning(): Boolean = engines.values.any { it.running }

    @Synchronized
    fun addExitListener(l: (vmId: String, code: Int) -> Unit) { exitListeners.add(l) }

    @Synchronized
    fun removeExitListener(l: (vmId: String, code: Int) -> Unit) { exitListeners.remove(l) }

    @Synchronized
    fun dispatchExit(vmId: String, code: Int) {
        exitListeners.toList().forEach { try { it(vmId, code) } catch (e: Exception) { } }
    }
}
