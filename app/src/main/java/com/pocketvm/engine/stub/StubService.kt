package com.pocketvm.engine.stub

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Service proxy that hosts guest Service instances inside PocketVM containers.
 * Reads the real target Service class from intent extras, loads it via the
 * guest's DexClassLoader, and delegates lifecycle methods.
 *
 * 20 numbered subclasses (StubSvc0–StubSvc19) are declared for the manifest pool.
 */
open class StubService : Service() {

    companion object {
        private const val TAG = "StubService"
        const val EXTRA_VM_ID = "pocketvm.extra.VM_ID"
        const val EXTRA_TARGET_PACKAGE = "pocketvm.extra.TARGET_PACKAGE"
        const val EXTRA_TARGET_SERVICE = "pocketvm.extra.TARGET_SERVICE"

        private val usedSlots = ConcurrentHashMap<Int, Boolean>()
        private const val SLOT_COUNT = 20

        fun allocateSlot(): Int {
            for (i in 0 until SLOT_COUNT) {
                if (usedSlots.putIfAbsent(i, true) == null) return i
            }
            return 0
        }

        fun releaseSlot(index: Int) {
            usedSlots.remove(index)
        }

        fun createStartIntent(
            context: Context,
            vmId: String,
            targetPackage: String,
            targetService: String
        ): Intent {
            val slotIndex = allocateSlot()
            val stubClassName = "com.pocketvm.engine.stub.StubService\$StubSvc$slotIndex"
            return Intent().apply {
                component = ComponentName(context.packageName, stubClassName)
                putExtra(EXTRA_VM_ID, vmId)
                putExtra(EXTRA_TARGET_PACKAGE, targetPackage)
                putExtra(EXTRA_TARGET_SERVICE, targetService)
            }
        }
    }

    private var guestService: Service? = null
    private var slotIndex: Int = -1

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        val vmId = intent.getStringExtra(EXTRA_VM_ID) ?: return START_NOT_STICKY
        val targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE) ?: return START_NOT_STICKY
        val targetService = intent.getStringExtra(EXTRA_TARGET_SERVICE) ?: return START_NOT_STICKY

        val className = javaClass.simpleName
        slotIndex = className.removePrefix("StubSvc").toIntOrNull() ?: -1

        Log.d(TAG, "StubSvc$slotIndex starting guest: $targetPackage/$targetService in VM $vmId")

        try {
            val vfs = com.pocketvm.engine.io.VirtualFileSystem.get()
            val apkDir = vfs.getApkDir(vmId, targetPackage)
            val apkFile = apkDir.listFiles()?.firstOrNull { it.name.endsWith(".apk") }

            if (apkFile == null || !apkFile.exists()) {
                Log.e(TAG, "APK not found for $targetPackage in VM $vmId")
                stopSelf()
                return START_NOT_STICKY
            }

            val classLoader = StubActivity.getOrCreateClassLoader(
                this, vmId, targetPackage, apkFile.absolutePath
            )
            val serviceClass = classLoader.loadClass(targetService)
            val guest = serviceClass.newInstance() as Service
            guestService = guest

            Log.d(TAG, "Guest service $targetService loaded successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start guest service", e)
            stopSelf()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Guest services that need binding would implement this
        return null
    }

    override fun onDestroy() {
        try {
            guestService?.let {
                val method = Service::class.java.getDeclaredMethod("onDestroy")
                method.isAccessible = true
                method.invoke(it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Guest onDestroy failed: ${e.message}")
        }
        guestService = null
        if (slotIndex >= 0) releaseSlot(slotIndex)
        super.onDestroy()
    }

    // ── 20 Stub subclasses ───────────────────────────────────────────────
    class StubSvc0 : StubService()
    class StubSvc1 : StubService()
    class StubSvc2 : StubService()
    class StubSvc3 : StubService()
    class StubSvc4 : StubService()
    class StubSvc5 : StubService()
    class StubSvc6 : StubService()
    class StubSvc7 : StubService()
    class StubSvc8 : StubService()
    class StubSvc9 : StubService()
    class StubSvc10 : StubService()
    class StubSvc11 : StubService()
    class StubSvc12 : StubService()
    class StubSvc13 : StubService()
    class StubSvc14 : StubService()
    class StubSvc15 : StubService()
    class StubSvc16 : StubService()
    class StubSvc17 : StubService()
    class StubSvc18 : StubService()
    class StubSvc19 : StubService()
}
